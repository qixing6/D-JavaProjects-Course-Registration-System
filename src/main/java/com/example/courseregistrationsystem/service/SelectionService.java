package com.example.courseregistrationsystem.service;

import com.example.courseregistrationsystem.entity.Selection;
import com.example.courseregistrationsystem.vo.ResponseVO;
import com.example.courseregistrationsystem.vo.SelectionVO;

import java.util.List;

public interface SelectionService {

    /**
     * 学生选课（核心：校验student_id+course_id不重复）
     * @param selectionVO 选课参数（学号+课程ID）
     * @return 选课结果
     */
    ResponseVO<String> selectCourse(SelectionVO selectionVO);

    /**
     * 学生退课
     * @param selectionVO 退课参数（学号+课程ID）
     * @return 退课结果
     */
    ResponseVO<String> dropCourse(SelectionVO selectionVO);

    /**
     * 查询学生已选课程列表
     * @param studentId 学生学号
     * @return 选课列表（含课程信息）
     */
    ResponseVO<List<Selection>> getStudentSelections(String studentId);

    /**
     * 查询课程被选的学生列表
     * @param courseId 课程ID
     * @return 选课列表（含学生信息）
     */
    ResponseVO<List<Selection>> getCourseSelections(Long courseId);

    /**
     * 校验学生是否已选某课程
     * @param studentId 学号
     * @param courseId 课程ID
     * @return true=已选，false=未选
     */
    boolean checkSelected(String studentId, Long courseId);
}