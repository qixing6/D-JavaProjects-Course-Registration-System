package com.example.courseregistrationsystem.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Selection {
    private Long id;
    private String studentId;
    private Long courseId;
    private LocalDateTime createTime;
}
