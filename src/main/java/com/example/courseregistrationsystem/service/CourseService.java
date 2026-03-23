package com.example.courseregistrationsystem.service;

import com.example.courseregistrationsystem.entity.Course;

import java.util.List;

public interface CourseService {
        /**
         * 根据课程ID查询课程（带全量缓存防御）
         */
        Course getCourseById(Long id);

        /**
         * 预热热点课程数据到缓存
         */
        void preloadHotCourseCache(Long... hotCourseIds);

        /**
         * 查询所有课程（带缓存，避免频繁查DB）
         */
        List<Course> getAllCourses();

        /**
         * 更新课程信息（同步更新缓存，避免数据不一致）
         * @param course 待更新的课程信息
         * @return 是否更新成功
         */
        boolean updateCourse(Course course);

        //增加课程
        boolean addCourse(Course course);
    }