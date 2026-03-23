package com.example.courseregistrationsystem;

import cn.hutool.core.util.StrUtil;
import com.example.courseregistrationsystem.entity.Selection;
import com.example.courseregistrationsystem.vo.ResponseVO;
import com.example.courseregistrationsystem.vo.SelectionVO;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
class CourseRegistrationSystemApplicationTests {

    @Test
    void contextLoads() {
    }

}
/*  @Override
    public ResponseVO<String> selectCourse(SelectionVO selectionVO) {
        // 1. 参数校验
        String studentId = selectionVO.getStudentId();
        Long courseId = selectionVO.getCourseId();
        if (StrUtil.isBlank(studentId) || courseId == null || courseId <= 0) {
            return ResponseVO.fail("学号或课程ID不能为空/非法");
        }

        // 2. 布隆过滤器校验课程是否存在（异常时降级到DB）
        boolean isCourseExist = false;
        RBloomFilter<Long> courseBloom = redisson.getBloomFilter(BloomFilterInitRunner.COURSE_BLOOM_FILTER_NAME);
        try {
            // 布隆过滤器快速校验（O(1)）
            isCourseExist = courseBloom.contains(courseId);
            log.debug("布隆过滤器校验课程ID {}：{}", courseId, isCourseExist);
        } catch (Exception e) {
            // 过滤器异常（未初始化/Redis连接失败），降级到DB查询
            log.error("布隆过滤器校验失败，降级到DB查询课程ID：{}", courseId, e);
            Course course = courseMapper.selectById(courseId);
            isCourseExist = (course != null);
        }

        // 3. 课程不存在则返回
        if (!isCourseExist) {
            return ResponseVO.fail("课程ID不存在");
        }

        // 4. 校验是否已选（student_id + course_id 唯一性）
        if (checkSelected(studentId, courseId)) {
            return ResponseVO.fail("已选该课程，不可重复选课");
        }

        // 5. 插入选课记录
        Selection selection = new Selection();
        selection.setStudentId(studentId);
        selection.setCourseId(courseId);
        selection.setCreateTime(LocalDateTime.now());
        selectionMapper.insert(selection);

        // 6. 删除缓存（保证数据一致性）
        deleteSelectionCache(studentId, courseId);
        log.info("学生{}选课程{}成功，选课时间：{}", studentId, courseId, selection.getCreateTime());

        return ResponseVO.success("选课成功");
    }

    // ========== 2. 学生退课 ==========
    @Override
    public ResponseVO<String> dropCourse(SelectionVO selectionVO) {
        // 1. 参数校验
        String studentId = selectionVO.getStudentId();
        Long courseId = selectionVO.getCourseId();
        if (StrUtil.isBlank(studentId) || courseId == null || courseId <= 0) {
            return ResponseVO.fail("学号或课程ID不能为空/非法");
        }

        // 2. 校验是否已选
        if (!checkSelected(studentId, courseId)) {
            return ResponseVO.fail("未选该课程，无法退课");
        }

        // 3. 删除选课记录
        LambdaQueryWrapper<Selection> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Selection::getStudentId, studentId)
                .eq(Selection::getCourseId, courseId);
        selectionMapper.delete(queryWrapper);

        // 4. 删除缓存
        deleteSelectionCache(studentId, courseId);
        log.info("学生{}退课程{}成功", studentId, courseId);

        return ResponseVO.success("退课成功");
    }

*/