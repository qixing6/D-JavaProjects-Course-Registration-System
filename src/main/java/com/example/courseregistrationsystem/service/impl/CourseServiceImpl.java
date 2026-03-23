package com.example.courseregistrationsystem.service.impl;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.courseregistrationsystem.entity.Course;
import com.example.oldcommonbase.exception.BusinessException;
import com.example.courseregistrationsystem.mapper.CourseMapper;
import com.example.courseregistrationsystem.service.CourseService;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 课程服务实现类（带全量缓存防御逻辑）
 */
@Service // 交给Spring管理
public  class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {
    private static final Logger log = LoggerFactory.getLogger(CourseServiceImpl.class);

    // 依赖注入（构造器方式，符合Spring最佳实践）
    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final CourseMapper courseMapper;

    // 缓存相关常量（抽取为常量，便于维护）
    private final Random random = new Random();
    private static final int BASE_EXPIRE_MINUTES = 30;
    private static final int RANDOM_OFFSET_MINUTES = 5;
    private static final int NULL_VALUE_EXPIRE_MINUTES = 5;
    private static final String CACHE_KEY_PREFIX = "course:";
    private static final String LOCK_KEY_PREFIX = "lock:course:";
    private static final String BLOOM_FILTER_NAME = "course:id:bloom";
    private static final String ALL_COURSES_CACHE_KEY = "course:all"; // 所有课程的缓存key
    private static final int ALL_COURSES_EXPIRE_MINUTES = 10; // 列表缓存过期时间（短于单条，避免数据不一致）
    private static final String NULL_VALUE_CACHE = "NULL_DATA"; // 统一空值缓存标识（字符串形式，避免反序列化问题）

    // 构造器注入（替代@Autowired，更规范，便于单元测试）
    public CourseServiceImpl(StringRedisTemplate redis, RedissonClient redisson, CourseMapper courseMapper) {
        this.redis = redis;
        this.redisson = redisson;
        this.courseMapper = courseMapper;
    }

    @Override
    public Course getCourseById(Long id) {
        // 1. 参数校验（前置防御，避免无效请求）
        if (id == null || id <= 0) {
            log.warn("课程ID非法：{}", id);
            return null;
        }

        String cacheKey = CACHE_KEY_PREFIX + id;
        String json;

        // ========== 1. 缓存穿透防御：布隆过滤器 ==========
            RBloomFilter<Long> bloomFilter = redisson.getBloomFilter(BLOOM_FILTER_NAME);
//       if (!bloomFilter.isExists()) {
//            log.warn("布隆过滤器未初始化，跳过校验");
//        } else if (!bloomFilter.contains(id)) {
//            log.info("课程ID{}不存在于布隆过滤器，拦截穿透请求", id);
//            throw new BusinessException(404, "课程信息不存在,ID:" + id);
//        }
        if (!bloomFilter.contains(id)) {
           log.info("课程ID{}不存在于布隆过滤器，拦截穿透请求", id);
           throw new BusinessException(404, "课程信息不存在,ID:" + id);
        }
       /*
          修订：1.布隆过滤器应该防在最前面，作为第一道防线，避免无效请求对缓存和数据库造成压力
          之前放在缓存查询后面，存在漏洞：如果Redis宕机，所有请求都会直接打到数据库，布隆过滤器就失效了，无法防止缓存穿透，导致数据库压力暴增，甚至崩溃
          2.不使用debug,改为info级别日志，布隆过滤器是核心防线，应该记录在info日志中，便于监控和排查穿透攻击；debug级别日志可能在生产环境被关闭，无法捕获重要的安全事件
          3.抛出异常而非返回null，明确告知调用方课程不存在，便于调用方区分“课程不存在”和“系统错误”两种情况；返回null可能导致调用方误以为是系统错误，增加排查难度
          4.加上异常捕获，防止布隆过滤器异常导致整个请求失败，保证系统的健壮性；如果布隆过滤器出现问题，记录错误日志，但继续执行后续逻辑，避免单点故障影响整个服务的可用性
         */

        // ========== 2. 缓存雪崩防御：Redis宕机时降级 ==========
        try {
                /**修订：捕获更广泛的异常，防止其他Redis异常导致降级失效；记录课程ID和异常信息，便于排查问题；降级策略改为熔断，不查数据库，直接返回错误提示，避免数据库压力暴增；
                 之前的降级策略虽然保证了可用性，但在Redis宕机时会导致大量请求直接打到数据库，可能引发更严重的性能问题甚至崩溃；
                  新的降级策略虽然牺牲了一部分可用性，但能有效保护数据库，保证系统整体的稳定性和安全性
                 */
            String cacheJson= redis.opsForValue().get(cacheKey);
            if(StrUtil.isNotBlank(cacheJson)){
                if(NULL_VALUE_CACHE.equals(cacheJson)){
                    log.debug("课程ID{}缓存命中空值，直接返回", id);
                    throw new BusinessException(404, "课程信息不存在,ID:" + id);
                }
                log.debug("课程ID{}缓存命中，直接返回", id);
                return JSONUtil.toBean(cacheJson, Course.class);
            }
        }catch (DataAccessException e){
            log.error("Redis访问异常，触发缓存降级，课程ID:{}", id, e);
            throw new BusinessException(503, "系统繁忙，请稍后再试");
        }

        // ========== 3. 缓存击穿防御：分布式锁 ==========
        String lockKey = LOCK_KEY_PREFIX + id;
        RLock lock = redisson.getLock(lockKey);
        Course course = null;

        try {
            // 尝试获取锁：最多等1秒，锁自动释放3秒（避免死锁）
            boolean locked = lock.tryLock(1, 3, TimeUnit.SECONDS);
            if (!locked) {
                log.debug("课程ID{}获取分布式锁失败，重试查询缓存", id);
                // 重试查缓存（大概率已被其他线程写入）
                json = redis.opsForValue().get(cacheKey);
                if (StrUtil.isNotBlank(json)) {
                    return "NULL_VALUE_CACHE".equals(json) ? null : JSONUtil.toBean(json, Course.class);
                }
                log.warn("课程ID{}重试缓存仍未命中，可能存在热点数据问题", id);
                throw new BusinessException(503, "系统繁忙，请稍后再试");
            }

            // 双重检查缓存（防止锁等待期间缓存已写入）
            json = redis.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(json)) {
                log.debug("课程ID{}双重检查缓存命中，直接返回", id);
                return "NULL_VALUE_CACHE".equals(json) ? null : JSONUtil.toBean(json, Course.class);
            }

            // ========== 4. 查数据库 ==========
            log.info("课程ID{}缓存未命中，执行数据库查询", id);
            course = courseMapper.selectById(id);

            // ========== 5. 缓存雪崩防御：过期时间加随机值 + 空值缓存 ==========
            if (course != null) {
                // 25~35分钟随机过期（避免key同时过期）
                int expireTime = BASE_EXPIRE_MINUTES + random.nextInt(2 * RANDOM_OFFSET_MINUTES) - RANDOM_OFFSET_MINUTES;
                redis.opsForValue().set(cacheKey, JSONUtil.toJsonStr(course), expireTime, TimeUnit.MINUTES);
                log.debug("课程ID{}写入缓存，过期时间：{}分钟", id, expireTime);
            } else {
                // 空值缓存：1~9分钟随机过期（保底1分钟）
                int nullExpireTime = NULL_VALUE_EXPIRE_MINUTES + random.nextInt(2 * RANDOM_OFFSET_MINUTES) - RANDOM_OFFSET_MINUTES;
                nullExpireTime = Math.max(nullExpireTime, 1);
                redis.opsForValue().set(cacheKey, "NULL_VALUE_CACHE", nullExpireTime, TimeUnit.MINUTES);
                log.debug("课程ID{}写入空值缓存，过期时间：{}分钟", id, nullExpireTime);
                throw new BusinessException(404, "课程信息不存在,ID:" + id);
            }
        } catch (InterruptedException e) {
            log.error("获取分布式锁异常，课程ID:{}", id, e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            throw new BusinessException(503, "系统繁忙，请稍后再试");
        } finally {
            // 确保锁释放（避免死锁）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("课程ID{}分布式锁已释放", id);
            }
        }

        return course;
    }

    @Override
    public void preloadHotCourseCache(Long... hotCourseIds) {
        if (hotCourseIds == null || hotCourseIds.length == 0) {
            log.warn("预热热点课程缓存：无课程ID传入");
            return;
        }

        for (Long courseId : hotCourseIds) {
            if (courseId == null || courseId <= 0) {
                log.warn("预热热点课程缓存：课程ID{}非法，跳过", courseId);
                continue;
            }

            String cacheKey = CACHE_KEY_PREFIX + courseId;
            Course course = courseMapper.selectById(courseId);
            if (course != null) {
                int expireTime = BASE_EXPIRE_MINUTES + random.nextInt(2 * RANDOM_OFFSET_MINUTES) - RANDOM_OFFSET_MINUTES;
                redis.opsForValue().set(cacheKey, JSONUtil.toJsonStr(course), expireTime, TimeUnit.MINUTES);
                log.info("预热热点课程缓存完成，课程ID:{}，过期时间:{}分钟", courseId, expireTime);
            } else {
                log.warn("预热热点课程缓存：课程ID{}不存在，跳过", courseId);
            }
        }
    }
    // ========== 新增：查询所有课程（带缓存） ==========
    @Override
    public List<Course> getAllCourses() {
        String json;

        // 1. 先查缓存
        try {
            json = redis.opsForValue().get(ALL_COURSES_CACHE_KEY);
            if (StrUtil.isNotBlank(json)) {
                log.debug("所有课程缓存命中，直接返回");
                return JSONUtil.toList(json, Course.class); // 反序列化为List<Course>
            }
        } catch (RedisConnectionFailureException e) {
            // 降级策略：熔断
            log.error("Redis访问异常，触发缓存降级", e);
            throw new BusinessException(503, "系统繁忙，请稍后再试");
        }

        // 2. 缓存未命中：查DB + 写入缓存
        List<Course> courses = courseMapper.selectList(null);
        if (courses.isEmpty()) {
            throw new BusinessException(404, "暂无课程数据");
        }

        // 写入缓存（列表缓存过期时间短一点，减少数据不一致风险）
        int expireTime = ALL_COURSES_EXPIRE_MINUTES + random.nextInt(5); // 10~14分钟随机过期
        redis.opsForValue().set(ALL_COURSES_CACHE_KEY, JSONUtil.toJsonStr(courses), expireTime, TimeUnit.MINUTES);
        log.info("所有课程写入缓存，共{}条，过期时间：{}分钟", courses.size(), expireTime);

        return courses;
    }

    // ========== 新增：更新课程（同步缓存） ==========
    @Override
    public boolean updateCourse(Course course) {
        // 1. 参数校验
        if (course == null || course.getId() == null) {
            log.warn("更新课程失败：课程对象或ID为空");
            return false;
        }

        Long courseId = course.getId();
        String courseCacheKey = CACHE_KEY_PREFIX + courseId;

        // 2. 先更新数据库（核心：先DB后缓存，避免并发脏数据）
        int updateCount = courseMapper.updateById(course);
        if (updateCount == 0) {
            log.warn("更新课程失败：课程ID{}不存在", courseId);
            return false;
        }

        // 3. 同步更新缓存（核心操作：删除旧缓存，而非直接修改）
        try {
            // 3.1 删除单条课程缓存（下次查询会重新加载最新数据）
            redis.delete(courseCacheKey);
            log.debug("课程ID{}缓存已删除", courseId);

            // 3.2 删除所有课程列表缓存（避免列表数据不一致）
            redis.delete(ALL_COURSES_CACHE_KEY);
            log.debug("所有课程列表缓存已删除");

            // 可选：更新布隆过滤器（如果课程是新增而非更新，需add；更新无需操作，布隆过滤器只判断存在性）
            // RBloomFilter<Long> bloomFilter = redisson.getBloomFilter(BLOOM_FILTER_NAME);
            // bloomFilter.add(courseId); // 仅新增课程时需要
        } catch (RedisConnectionFailureException e) {
            log.error("Redis连接失败：课程ID{}缓存删除失败（需人工核对）", courseId, e);
            // 缓存删除失败不影响DB更新结果，返回true，但记录日志便于排查
        }

        log.info("课程ID{}更新成功，缓存已同步删除", courseId);
        return true;
    }

    //补充：新增课程（可选，配套缓存）
    // 如果需要新增课程，可参考更新逻辑，新增后：1.加布隆过滤器 2.删列表缓存
    public boolean addCourse(Course course) {
        if (course == null) {
            return false;
        }
        // 1. 新增DB
        boolean saveSuccess = this.save(course);
        //save方法会自动填充course的ID（如果是自增主键），所以后续可以直接使用course.getId()获取新课程的ID
        if (!saveSuccess) {
            return false;
        }
        // 2. 加入布隆过滤器
        RBloomFilter<Long> bloomFilter = redisson.getBloomFilter(BLOOM_FILTER_NAME);
        bloomFilter.add(course.getId());
        // 3. 删除列表缓存
        redis.delete(ALL_COURSES_CACHE_KEY);
        return true;
    }
}