package com.example.courseregistrationsystem.vo;

import lombok.Data;

@Data
public class RegisterVO {
    private String studentId; // 学号（对应表的student_id）
    private String name;      // 姓名（对应表的name）
    private String password;  // 密码（对应表的password）
}