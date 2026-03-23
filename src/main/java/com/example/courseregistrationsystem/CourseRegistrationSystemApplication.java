package com.example.courseregistrationsystem;

import org.mybatis.spring.annotation.MapperScan;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 课程注册系统启动类
 * 核心：包扫描、Mapper扫描、Redisson自动配置、缓存开启
 */

@SpringBootApplication(scanBasePackages = "com.example.courseregistrationsystem")
@MapperScan("com.example.courseregistrationsystem.mapper") // 扫描MyBatis-Plus的Mapper接口
@EnableCaching // 开启Spring缓存（可选：若后续用@Cacheable注解）
public class CourseRegistrationSystemApplication {

    public static void main(String[] args) {
        // 启动Spring Boot应用
        SpringApplication.run(CourseRegistrationSystemApplication.class, args);
        System.out.println("=====================================");
        System.out.println("  课程注册系统启动成功！访问地址：http://localhost:8080/api");
        System.out.println("=====================================");
    }
}