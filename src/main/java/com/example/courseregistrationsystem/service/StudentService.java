package com.example.courseregistrationsystem.service;

import com.example.courseregistrationsystem.entity.Student;
import com.example.courseregistrationsystem.vo.LoginVO;
import com.example.courseregistrationsystem.vo.RegisterVO;
import com.example.courseregistrationsystem.vo.ResponseVO;

public interface StudentService {

    /**
     * 学生注册（学号+姓名+密码）
     * @param registerVO 注册参数（学号、姓名、密码）
     * @return 注册结果
     */
    ResponseVO<String> register(RegisterVO registerVO);

    /**
     * 学生登录（学号+密码）
     * @param loginVO 登录参数（学号 + 密码）
     * @return 登录令牌
     */
    ResponseVO<String> login(LoginVO loginVO);

    /**
     * 退出登录（删除Redis令牌）
     * @param token 登录令牌
     * @return 退出结果
     */
    ResponseVO<String> logout(String token);

    /**
     * 验证登录令牌有效性
     * @param token 登录令牌
     * @return 学生信息（null=令牌无效）
     */
    Student verifyToken(String token);
}