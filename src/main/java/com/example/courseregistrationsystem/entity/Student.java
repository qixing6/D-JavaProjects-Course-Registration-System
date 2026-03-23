package com.example.courseregistrationsystem.entity;

import lombok.Data;

@Data
public class Student {
    private Long id;
    private String studentId;
    private String name;
    private String password;
}
