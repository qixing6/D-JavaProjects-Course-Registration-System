package com.example.courseregistrationsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.courseregistrationsystem.entity.Student;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {
}
