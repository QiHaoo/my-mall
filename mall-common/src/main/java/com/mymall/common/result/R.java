package com.mymall.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体
 *
 * @param <T> 数据泛型
 */
@Data
public class R<T> implements Serializable {

    private Integer code;
    private String msg;
    private T data;

    public static <T> R<T> ok() {
        return restResult(null, 200, "success");
    }

    public static <T> R<T> ok(T data) {
        return restResult(data, 200, "success");
    }

    public static <T> R<T> error(String msg) {
        return restResult(null, 500, msg);
    }

    public static <T> R<T> error(Integer code, String msg) {
        return restResult(null, code, msg);
    }

    private static <T> R<T> restResult(T data, Integer code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
