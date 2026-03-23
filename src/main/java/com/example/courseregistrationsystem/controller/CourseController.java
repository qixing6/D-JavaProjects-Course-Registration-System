package com.example.courseregistrationsystem.controller;


import com.example.courseregistrationsystem.entity.Course;
import com.example.courseregistrationsystem.service.CourseService;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/course")
public class CourseController {

    @Resource
    private CourseService courseService;

    // 根据ID查课程
    @GetMapping("/{id}")
    public Course getCourse(@PathVariable Long id) {
        return courseService.getCourseById(id);
    }

    // 查询所有课程
    @GetMapping("/all")
    public List<Course> getAllCourses() {
        return courseService.getAllCourses();
    }

    // 更新课程
    @PutMapping("/update")
    public String updateCourse(@RequestBody Course course) {
        boolean success = courseService.updateCourse(course);
        return success ? "课程更新成功" : "课程更新失败";
    }

    // 预热热点课程
    @GetMapping("/preload")
    public String preloadHotCourse() {
        courseService.preloadHotCourseCache(1L, 2L, 3L);
        return "热点课程缓存预热完成";
    }

    // 新增课程（可选）
    @PostMapping("/add")
    public String addCourse(@RequestBody Course course) {
        boolean success = courseService.addCourse(course);
        return success ? "课程新增成功" : "课程新增失败";
    }
}