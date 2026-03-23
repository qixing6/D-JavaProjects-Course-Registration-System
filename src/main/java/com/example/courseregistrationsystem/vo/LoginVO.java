package com.example.courseregistrationsystem.vo;

import lombok.Data;

@Data
public class LoginVO {
    private String studentId; // 学号（登录唯一标识）
    private String password;  // 密码
}