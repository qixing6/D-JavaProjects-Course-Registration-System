package com.example.courseregistrationsystem.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.courseregistrationsystem.entity.Student;
import com.example.courseregistrationsystem.mapper.StudentMapper;
import com.example.courseregistrationsystem.service.StudentService;
import com.example.courseregistrationsystem.vo.LoginVO;
import com.example.courseregistrationsystem.vo.RegisterVO;
import com.example.courseregistrationsystem.vo.ResponseVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import cn.hutool.core.util.StrUtil;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {
    private static final Logger log = LoggerFactory.getLogger(StudentServiceImpl.class);

    // 依赖注入
    private final StringRedisTemplate redis;
    private final StudentMapper studentMapper;

    // 缓存常量（登录令牌）
    private static final String LOGIN_TOKEN_KEY_PREFIX = "student:token:";     // 令牌缓存key
    private static final int LOGIN_TOKEN_EXPIRE_HOURS = 2;                    // 令牌2小时过期
    private static final String PASSWORD_SALT = "course_sys_2026";            // 密码加密盐值

    // 构造器注入
    public StudentServiceImpl(StringRedisTemplate redis, StudentMapper studentMapper) {
        this.redis = redis;
        this.studentMapper = studentMapper;
    }

    // ========== 1. 学生注册（学号+姓名+密码） ==========
    @Override
    public ResponseVO<String> register(RegisterVO registerVO) {
        // 1. 参数校验
        String studentId = registerVO.getStudentId();
        String name = registerVO.getName();
        String password = registerVO.getPassword();

        if (StrUtil.hasBlank(studentId, name, password)) {
            return ResponseVO.fail("学号、姓名、密码不能为空");
        }

        // 2. 校验学号是否已注册（student_id唯一）
        LambdaQueryWrapper<Student> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Student::getStudentId, studentId); // 对应表的student_id字段
        Student existStudent = studentMapper.selectOne(queryWrapper);
        if (existStudent != null) {
            return ResponseVO.fail("学号已注册");
        }

        // 3. 密码加密（MD5+盐值，防止明文存储）
        String encryptPwd = DigestUtil.md5Hex(password + PASSWORD_SALT);

        // 4. 写入数据库（仅操作表中4个字段：id自增，student_id/name/password手动赋值）
        Student student = new Student();
        student.setStudentId(studentId); // 对应表的student_id
        student.setName(name);          // 对应表的name
        student.setPassword(encryptPwd); // 对应表的password
        studentMapper.insert(student);

        log.info("学生注册成功：学号={}，姓名={}", studentId, name);
        return ResponseVO.success("注册成功");
    }

    // ========== 2. 学生登录（学号+密码） ==========
    @Override
    public ResponseVO<String> login(LoginVO loginVO) {
        // 1. 参数校验
        String studentId = loginVO.getStudentId();
        String password = loginVO.getPassword();
        if (StrUtil.hasBlank(studentId, password)) {
            return ResponseVO.fail("学号和密码不能为空");
        }

        // 2. 查询学生（按学号）
        LambdaQueryWrapper<Student> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Student::getStudentId, studentId);
        Student student = studentMapper.selectOne(queryWrapper);
        if (student == null) {
            return ResponseVO.fail("学号未注册");
        }

        // 3. 校验密码（加密后对比）
        String encryptPwd = DigestUtil.md5Hex(password + PASSWORD_SALT);
        if (!encryptPwd.equals(student.getPassword())) {
            return ResponseVO.fail("密码错误");
        }

        // 4. 生成登录令牌（UUID），存入Redis
        String token = UUID.randomUUID().toString().replace("-", "");
        String tokenKey = LOGIN_TOKEN_KEY_PREFIX + token;
        // 仅存学生主键ID，减少Redis存储压力
        redis.opsForValue().set(tokenKey, student.getId().toString(), LOGIN_TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);

        log.info("学生登录成功：学号={}，姓名={}", studentId, student.getName());
        return ResponseVO.success("登录成功", token);
    }

    // ========== 3. 退出登录（删除令牌） ==========
    @Override
    public ResponseVO<String> logout(String token) {
        if (StrUtil.isBlank(token)) {
            return ResponseVO.fail("令牌不能为空");
        }

        String tokenKey = LOGIN_TOKEN_KEY_PREFIX + token;
        Boolean deleted = redis.delete(tokenKey);
        if (deleted != null && deleted) {
            log.info("学生退出登录：令牌={}", token);
            return ResponseVO.success("退出登录成功");
        } else {
            return ResponseVO.fail("令牌无效或已过期");
        }
    }

    // ========== 4. 验证令牌有效性 ==========
    @Override
    public Student verifyToken(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }

        String tokenKey = LOGIN_TOKEN_KEY_PREFIX + token;
        String studentIdStr = redis.opsForValue().get(tokenKey);
        if (studentIdStr == null) {
            return null; // 令牌无效
        }

        // 令牌有效，查询学生信息并续期
        Long id = Long.parseLong(studentIdStr);
        Student student = studentMapper.selectById(id);
        // 令牌续期：延长2小时有效期
        redis.expire(tokenKey, LOGIN_TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);

        return student;
    }
}