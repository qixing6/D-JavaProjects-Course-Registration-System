package com.example.courseregistrationsystem.vo;

import lombok.Data;

@Data
public class ResponseVO<T> {
    private int code;    // 状态码（200成功，其他失败）
    private String msg;  // 提示信息
    private T data;      // 返回数据

    // 成功响应
    public static <T> ResponseVO<T> success(String msg) {
        ResponseVO<T> vo = new ResponseVO<>();
        vo.setCode(200);
        vo.setMsg(msg);
        return vo;
    }

    public static <T> ResponseVO<T> success(String msg, T data) {
        ResponseVO<T> vo = new ResponseVO<>();
        vo.setCode(200);
        vo.setMsg(msg);
        vo.setData(data);
        return vo;
    }

    // 失败响应
    public static <T> ResponseVO<T> fail(String msg) {
        ResponseVO<T> vo = new ResponseVO<>();
        vo.setCode(500);
        vo.setMsg(msg);
        return vo;
    }
}