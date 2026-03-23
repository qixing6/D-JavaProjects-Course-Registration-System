package com.example.courseregistrationsystem.entity;

import lombok.Data;

@Data
public class Course {
    private Long id;
    private String courseName;
    private String teacher;
    private Integer maxNum;
    private Integer selectedNum;
}
