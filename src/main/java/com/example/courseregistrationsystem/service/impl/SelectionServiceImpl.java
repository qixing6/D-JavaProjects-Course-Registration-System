package com.example.courseregistrationsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.courseregistrationsystem.config.BloomFilterInitRunner;
import com.example.courseregistrationsystem.entity.Course;
import com.example.courseregistrationsystem.entity.Selection;
import com.example.courseregistrationsystem.mapper.CourseMapper;
import com.example.courseregistrationsystem.mapper.SelectionMapper;
import com.example.courseregistrationsystem.service.CourseService;
import com.example.courseregistrationsystem.service.SelectionService;
import com.example.courseregistrationsystem.vo.ResponseVO;
import com.example.courseregistrationsystem.vo.SelectionVO;
import com.example.oldcommonbase.exception.BusinessException;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Service
public class SelectionServiceImpl extends ServiceImpl<SelectionMapper, Selection> implements SelectionService {
    private static final Logger log = LoggerFactory.getLogger(SelectionServiceImpl.class);

    private static final String BLOOM_NAME = "course:id:bloom";
    // 依赖注入
    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final CourseService courseService; // 校验课程是否存在
    private final SelectionMapper selectionMapper;

    // 缓存常量
    private static final String SELECTION_CACHE_PREFIX = "selection:"; // 选课缓存key前缀
    private static final String STUDENT_SELECTION_KEY = SELECTION_CACHE_PREFIX + "student:{}"; // 学生选课列表key
    private static final String COURSE_SELECTION_KEY = SELECTION_CACHE_PREFIX + "course:{}"; // 课程选课列表key
    private static final int CACHE_EXPIRE_MINUTES = 15; // 选课缓存过期时间
    private static final String COURSE_BLOOM_FILTER_NAME = "courseBloom"; // 课程布隆过滤器名称
    private final CourseMapper courseMapper;

    // 构造器注入
    public SelectionServiceImpl(StringRedisTemplate redis, RedissonClient redisson,
                                CourseService courseService, SelectionMapper selectionMapper, CourseMapper courseMapper) {
        this.redis = redis;
        this.redisson = redisson;
        this.courseService = courseService;
        this.selectionMapper = selectionMapper;
        this.courseMapper = courseMapper;
    }

    // ========== 1. 学生选课（核心：唯一性校验） ==========

    @Override
    public ResponseVO<String> selectCourse(SelectionVO selectionVO) {
        // 1. 参数校验
        String studentId = selectionVO.getStudentId();
        Long courseId = selectionVO.getCourseId();
        if (StrUtil.isBlank(studentId) || courseId == null || courseId <= 0) {
            return ResponseVO.fail("学号或课程ID不能为空/非法");
        }

        // 2. 校验课程是否存在（布隆过滤器+熔断）
        boolean isCourseExist = false;
        RBloomFilter<Long> courseBloom = redisson.getBloomFilter(BLOOM_NAME);
        Course course = null;
        try {
            isCourseExist = courseBloom.contains(courseId);
            log.debug("布隆过滤器校验课程ID {}：{}", courseId, isCourseExist);
        } catch (Exception e) {
            log.error("Redis访问异常，触发缓存降级，课程ID:{}",courseId);
            throw new BusinessException(503, "系统繁忙，请稍后再试");
        }

        // 3. 【新增】查询课程详情（获取max_num和selected_num）
        if (course == null) {
            course = courseMapper.selectById(courseId);
        }
        if (course.getMaxNum() <= course.getSelectedNum()) {
            return ResponseVO.fail("课程名额已满，无法选课");
        }

        // 4. 校验是否已选（student_id+course_id唯一）
        if (checkSelected(studentId, courseId)) {
            return ResponseVO.fail("已选该课程，不可重复选课");
        }

        // 5. 【新增】加锁防止并发超卖（分布式锁，适配多实例部署）
        String lockKey = "course:lock:" + courseId;
        RLock lock = redisson.getLock(lockKey);
        try {
            // 加锁（等待10秒，持有锁30秒，防止死锁）
            boolean locked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!locked) {
                return ResponseVO.fail("选课请求过于频繁，请稍后再试");
            }

            // 二次校验名额（防止加锁期间名额被抢完）
            Course freshCourse = courseMapper.selectById(courseId);
            if (freshCourse.getMaxNum() <= freshCourse.getSelectedNum()) {
                return ResponseVO.fail("课程名额已满，无法选课");
            }

            // 6. 插入选课记录
            Selection selection = new Selection();
            selection.setStudentId(studentId);
            selection.setCourseId(courseId);
            selection.setCreateTime(LocalDateTime.now());
            selectionMapper.insert(selection);

            // 7. 【新增】更新课程已选人数（selected_num + 1）
            LambdaUpdateWrapper<Course> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Course::getId, courseId)
                    //==SQL的WHERE id=#{id}，锁定更新课程
                    .set(Course::getSelectedNum, freshCourse.getSelectedNum() + 1);
                    //==SET selection_num=#{freshCourse.selection_num}+1
            courseMapper.update(null, updateWrapper);
            //执行更新操作，第一个参数传null表示不按实体类更新，只按照updateWrapper里面条件更新

            // 8. 删除缓存
            deleteSelectionCache(studentId, courseId);
            log.info("学生{}选课程{}成功，选课时间：{}，当前课程已选人数：{}",
                    studentId, courseId, selection.getCreateTime(), freshCourse.getSelectedNum() + 1);

            return ResponseVO.success("选课成功");
        } catch (InterruptedException e) {
            log.error("获取选课锁失败", e);
            return ResponseVO.fail("选课失败，请稍后再试");
        } finally {
            // 释放锁（防止死锁）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    // ========== 2. 学生退课 ==========
    @Override
    public ResponseVO<String> dropCourse(SelectionVO selectionVO) {
        String studentId = selectionVO.getStudentId();
        Long courseId = selectionVO.getCourseId();
        if (StrUtil.isBlank(studentId) || courseId == null || courseId <= 0) {
            return ResponseVO.fail("学号或课程ID不能为空/非法");
        }

        // 1. 校验是否已选
        LambdaQueryWrapper<Selection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Selection::getStudentId, studentId)
                .eq(Selection::getCourseId, courseId);
        Selection selection = selectionMapper.selectOne(wrapper);
        if (selection == null) {
            return ResponseVO.fail("未选该课程，无法退课");
        }

        // 2. 加锁更新课程名额
        String lockKey = "course:lock:" + courseId;
        RLock lock = redisson.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!locked) {
                return ResponseVO.fail("退课请求过于频繁，请稍后再试");
            }

            // 3. 删除选课记录
            selectionMapper.deleteById(selection.getId());

            // 4. 更新课程已选人数（selected_num - 1）
            Course course = courseMapper.selectById(courseId);
            if (course.getSelectedNum() > 0) {
                LambdaUpdateWrapper<Course> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(Course::getId, courseId)
                        .set(Course::getSelectedNum, course.getSelectedNum() - 1);
                courseMapper.update(null, updateWrapper);
            }

            // 5. 删除缓存
            deleteSelectionCache(studentId, courseId);
            log.info("学生{}退课{}成功，当前课程已选人数：{}",
                    studentId, courseId, course.getSelectedNum() - 1);

            return ResponseVO.success("退课成功");
        } catch (InterruptedException e) {
            log.error("获取退课锁失败", e);
            return ResponseVO.fail("退课失败，请稍后再试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }



    // ========== 3. 查询学生已选课程列表（带缓存） ==========
    @Override
    public ResponseVO<List<Selection>> getStudentSelections(String studentId) {
        // 1. 参数校验
        if (StrUtil.isBlank(studentId)) {
            return ResponseVO.fail("学号不能为空");
        }

        String cacheKey = String.format(STUDENT_SELECTION_KEY, studentId);
        String json;

        // 2. 先查缓存
        try {
            json = redis.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(json)) {
                log.debug("学生{}选课列表缓存命中", studentId);
                List<Selection> selections = cn.hutool.json.JSONUtil.toList(json, Selection.class);
                return ResponseVO.success("查询成功", selections);
            }
        } catch (Exception e) {
            log.error("Redis查询学生选课列表失败，学号：{}", studentId, e);
            // 缓存异常不影响业务，继续查DB
        }

        // 3. 缓存未命中：查DB
        LambdaQueryWrapper<Selection> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Selection::getStudentId, studentId)
                .orderByDesc(Selection::getCreateTime); // 按选课时间倒序
        List<Selection> selections = selectionMapper.selectList(queryWrapper);

        // 4. 写入缓存（15分钟过期）
        try {
            redis.opsForValue().set(cacheKey, cn.hutool.json.JSONUtil.toJsonStr(selections),
                    CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis写入学生选课列表失败，学号：{}", studentId, e);
        }

        return ResponseVO.success("查询成功", selections);
    }

    // ========== 4. 查询课程被选学生列表（带缓存） ==========
    @Override
    public ResponseVO<List<Selection>> getCourseSelections(Long courseId) {
        // 1. 参数校验
        if (courseId == null || courseId <= 0) {
            return ResponseVO.fail("课程ID非法");
        }

        String cacheKey = String.format(COURSE_SELECTION_KEY, courseId);
        String json;

        // 2. 先查缓存
        try {
            json = redis.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(json)) {
                log.debug("课程{}选课列表缓存命中", courseId);
                List<Selection> selections = cn.hutool.json.JSONUtil.toList(json, Selection.class);
                return ResponseVO.success("查询成功", selections);
            }
        } catch (Exception e) {
            log.error("Redis查询课程选课列表失败，课程ID：{}", courseId, e);
        }

        // 3. 查DB
        LambdaQueryWrapper<Selection> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Selection::getCourseId, courseId)
                .orderByDesc(Selection::getCreateTime);
        List<Selection> selections = selectionMapper.selectList(queryWrapper);

        // 4. 写入缓存
        try {
            redis.opsForValue().set(cacheKey, cn.hutool.json.JSONUtil.toJsonStr(selections),
                    CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis写入课程选课列表失败，课程ID：{}", courseId, e);
        }

        return ResponseVO.success("查询成功", selections);
    }

    // ========== 5. 校验学生是否已选某课程（核心工具方法） ==========
    @Override
    public boolean checkSelected(String studentId, Long courseId) {
        LambdaQueryWrapper<Selection> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Selection::getStudentId, studentId)
                .eq(Selection::getCourseId, courseId);
        return selectionMapper.exists(queryWrapper);
    }

    // ========== 私有方法：删除选课缓存（同步数据） ==========
    private void deleteSelectionCache(String studentId, Long courseId) {
        try {
            // 删除学生选课列表缓存
            String studentCacheKey = String.format(STUDENT_SELECTION_KEY, studentId);
            redis.delete(studentCacheKey);
            // 删除课程选课列表缓存
            String courseCacheKey = String.format(COURSE_SELECTION_KEY, courseId);
            redis.delete(courseCacheKey);
        } catch (Exception e) {
            log.error("删除选课缓存失败，学号：{}，课程ID：{}", studentId, courseId, e);
        }
    }
}