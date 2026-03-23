package com.example.courseregistrationsystem.controller;

import com.example.courseregistrationsystem.entity.Selection;
import com.example.courseregistrationsystem.service.SelectionService;
import com.example.courseregistrationsystem.vo.ResponseVO;
import com.example.courseregistrationsystem.vo.SelectionVO;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.util.List;

@RestController
@RequestMapping("/selection")
public class SelectionController {

    @Resource
    private SelectionService selectionService;

    // 选课接口
    @PostMapping("/select")
    public ResponseVO<String> selectCourse(@RequestBody SelectionVO selectionVO) {
        return selectionService.selectCourse(selectionVO);
    }

    // 退课接口
    @PostMapping("/drop")
    public ResponseVO<String> dropCourse(@RequestBody SelectionVO selectionVO) {
        return selectionService.dropCourse(selectionVO);
    }

    // 查询学生已选课程
    @GetMapping("/student/{studentId}")
    public ResponseVO<List<Selection>> getStudentSelections(@PathVariable String studentId) {
        return selectionService.getStudentSelections(studentId);
    }

    // 查询课程被选学生
    @GetMapping("/course/{courseId}")
    public ResponseVO<List<Selection>> getCourseSelections(@PathVariable Long courseId) {
        return selectionService.getCourseSelections(courseId);
    }

    // 校验是否已选课程
    @GetMapping("/check")
    public ResponseVO<Boolean> checkSelected(@RequestParam String studentId, @RequestParam Long courseId) {
        boolean selected = selectionService.checkSelected(studentId, courseId);
        return ResponseVO.success("查询成功", selected);
    }
}