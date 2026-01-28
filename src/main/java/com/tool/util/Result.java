package com.tool.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @Description：通用 API 响应封装类
 * @param <T> 数据类型
 * @author Lachesism
 * @date 2026-01-27
 */

public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码：200=成功, 400=客户端错误, 500=服务器错误 */
    private int code;

    /** 提示信息（可为空） */
    private String message;

    /** 返回数据（可为空） */
    private T data;

    public Result() {}

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }


    public static Result<Void> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success(String message) {
        return new Result<>(200, message, null);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    // Map
    public static Result<Map<String, Object>> successMap(Map<String, Object> data) {
        return new Result<>(200, "success", data);
    }

    // List
    public static <E> Result<List<E>> successList(List<E> data) {
        return new Result<>(200, "success", data);
    }

    public static Result<Void> error() {
        return new Result<>(500, "error", null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(int code, String message, T data) {
        return new Result<>(code, message, data);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    // =================== 实用方法 ===================

    /** 是否成功 */
    @JsonIgnore
    public boolean isSuccess() { return this.code == 200; }

    /** 是否失败 */
    @JsonIgnore
    public boolean isError() { return this.code != 200; }

    public Result<T> withMessage(String message) {
        this.message = message;
        return this;
    }

    public Result<T> withData(T data) {
        this.data = data;
        return this;
    }
}
