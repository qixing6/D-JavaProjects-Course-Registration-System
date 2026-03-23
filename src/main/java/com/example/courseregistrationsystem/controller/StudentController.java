package com.example.courseregistrationsystem.controller;

import com.example.courseregistrationsystem.service.StudentService;
import com.example.courseregistrationsystem.vo.LoginVO;
import com.example.courseregistrationsystem.vo.RegisterVO;
import com.example.courseregistrationsystem.vo.ResponseVO;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

@RestController
@RequestMapping("/student")
public class StudentController {

    @Resource
    private StudentService studentService;

    // 注册接口
    @PostMapping("/register")
    public ResponseVO<String> register(@RequestBody RegisterVO registerVO) {
        return studentService.register(registerVO);
    }

    // 登录接口
    @PostMapping("/login")
    public ResponseVO<String> login(@RequestBody LoginVO loginVO) {
        return studentService.login(loginVO);
    }

    // 退出登录接口
    @PostMapping("/logout")
    public ResponseVO<String> logout(@RequestParam String token) {
        return studentService.logout(token);
    }

    // 验证令牌接口（鉴权用）
    @GetMapping("/verifyToken")
    public ResponseVO<String> verifyToken(@RequestParam String token) {
        return studentService.verifyToken(token) != null
                ? ResponseVO.success("令牌有效")
                : ResponseVO.fail("令牌无效");
    }
}