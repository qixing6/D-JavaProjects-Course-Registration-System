package com.example.courseregistrationsystem.config;

import com.example.courseregistrationsystem.entity.Course;
import com.example.courseregistrationsystem.mapper.CourseMapper;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class BloomFilterInitRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterInitRunner.class);
    private static final String BLOOM_FILTER_NAME = "course:id:bloom";
    private static final String LOCK_KEY = "lock:bloom:init";
    private static final long EXPECTED = 100000L;
    private static final double FPP = 0.001;

    @Resource
    private RedissonClient redisson;
    @Resource
    private CourseMapper courseMapper;

    @Override
    public void run(String... args) {
        RLock lock = redisson.getLock(LOCK_KEY);
        try {
            // 只允许一台机器初始化
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                log.info("其他实例正在初始化布隆，本机跳过");
                return;
            }

            RBloomFilter<Long> bloom = redisson.getBloomFilter(BLOOM_FILTER_NAME);
            if (bloom.isExists()) {
                log.info("布隆已存在，无需初始化");
                return;
            }

            bloom.tryInit(EXPECTED, FPP);
            log.info("布隆过滤器初始化完成");

            // 加载数据
            List<Course> list = courseMapper.selectList(null);
            if (list == null || list.isEmpty()) {
                log.warn("无课程数据");
                return;
            }

            list.stream()
                    .map(Course::getId)
                    .filter(Objects::nonNull)
                    .forEach(bloom::add);

            log.info("布隆过滤器加载课程ID完成，总数:{}", list.size());

        } catch (Exception e) {
            log.error("布隆初始化失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}





/* 课程ID布隆过滤器初始化器（项目启动时执行）

@Component
public class BloomFilterInitRunner implements CommandLineRunner {
    // 日志对象
    private static final Logger log = LoggerFactory.getLogger(BloomFilterInitRunner.class);

    // 布隆过滤器核心配置（全局统一）
    public static final String COURSE_BLOOM_FILTER_NAME = "course:id:bloomFilter"; // 对外暴露常量
    private static final long EXPECTED_INSERTIONS = 100000L; // 预计最大课程数
    private static final double FPP = 0.001; // 误判率（0.1%）

    // 依赖注入
    @Resource
    private RedissonClient redisson;
    @Resource
    private CourseMapper courseMapper;

    @Override
    public void run(String... args) {
        // 1. 获取布隆过滤器实例
        RBloomFilter<Long> bloomFilter = redisson.getBloomFilter(COURSE_BLOOM_FILTER_NAME);

        // 2. 初始化布隆过滤器（返回false表示已存在）
        boolean initSuccess = bloomFilter.tryInit(EXPECTED_INSERTIONS, FPP);
        if (!initSuccess) {
            log.warn("布隆过滤器已存在，清空旧数据后重新加载");
            bloomFilter.clearExpire(); // 清空旧数据（关键修复：替换clearExpire()）
        }

        // 3. 查询所有课程ID（仅查ID字段，提升性能）
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(Course::getId);
        List<Course> courseList = courseMapper.selectList(queryWrapper);

        // 4. 空值防护
        if (courseList == null || courseList.isEmpty()) {
            log.warn("未查询到任何课程数据，布隆过滤器无数据可添加");
            return;
        }

        // 5. 提取ID并过滤空值（兼容Java 8+）
        List<Long> allCourseIds = courseList.stream()
                //将courseList变成一个流(Stream)
                .map(Course::getId)
                //map是映射，将每个Course对象映射成它的ID（Long类型）
               /**修订：自增ID不可能为null,删除filter(Objects::nonNull)，避免误删有效ID
                 * .filter(Objects::nonNull) // 过滤掉ID为null的课程（安全防护，实际不应存在）
               */
                //.toList();
                //将过滤后的ID列表收集成一个新的List<Long>，命名为allCourseIds

        // 6. 批量添加ID到布隆过滤器
        //allCourseIds.forEach(bloomFilter::add);

        // 7. 打印初始化日志
       // log.info("课程ID布隆过滤器初始化完成");
        //log.info("  - 过滤器Key：{}", COURSE_BLOOM_FILTER_NAME);
        //log.info("  - 预计容量：{}，误判率：{}", EXPECTED_INSERTIONS, FPP);
        //log.info("  - 实际加载课程ID数量：{}", allCourseIds.size());
  //  }
//}