package com.mymall.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一响应体
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 返回单个数据
 * R.ok(entity);
 *
 * // 链式返回多个键值对（data 自动变为 Map）
 * R.ok().put("coupons", list).put("total", 2);
 *
 * // 错误响应
 * R.error("参数错误");
 * }</pre>
 *
 * @param <T> 数据泛型
 */
@Data
public class R<T> implements Serializable {

    private Integer code;
    private String msg;
    private T data;

    /**
     * 成功响应（data 初始化为 HashMap，支持后续链式 {@link #put}）
     */
    public static R<Map<String, Object>> ok() {
        R<Map<String, Object>> r = new R<>();
        r.setCode(200);
        r.setMsg("success");
        r.setData(new HashMap<>());
        return r;
    }

    /**
     * 成功响应（携带单个数据对象）
     */
    public static <T> R<T> ok(T data) {
        return restResult(data, 200, "success");
    }

    public static <T> R<T> error(String msg) {
        return restResult(null, 500, msg);
    }

    public static <T> R<T> error(Integer code, String msg) {
        return restResult(null, code, msg);
    }

    /**
     * 链式添加键值对，要求 data 为 Map 类型（通过 {@link #ok()} 创建）
     *
     * <pre>{@code R.ok().put("coupons", list).put("total", 2);}</pre>
     */
    @SuppressWarnings("unchecked")
    public R<T> put(String key, Object value) {
        if (this.data == null || !(this.data instanceof Map)) {
            this.data = (T) new HashMap<String, Object>();
        }
        ((Map<String, Object>) this.data).put(key, value);
        return this;
    }

    private static <T> R<T> restResult(T data, Integer code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
