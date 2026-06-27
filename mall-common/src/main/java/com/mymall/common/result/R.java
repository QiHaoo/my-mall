package com.mymall.common.result;

import com.mymall.common.exception.ResultCode;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一响应体
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 成功（无数据）—— 新增/修改/删除等操作
 * R.ok();
 *
 * // 成功（携带数据）
 * R.ok(entity);
 *
 * // 成功（携带分页数据）
 * R.ok(PageVO.of(page));
 *
 * // 失败（使用错误码枚举）
 * R.error(ResultCode.PARAM_ERROR);
 *
 * // 失败（错误码枚举 + 自定义消息）
 * R.error(ResultCode.STOCK_NOT_ENOUGH, "SKU[" + skuId + "] 库存不足");
 * }</pre>
 *
 * @param <T> 数据泛型
 */
@Data
public class R<T> implements Serializable {

    private static final int SUCCESS_CODE = 200;
    private static final String SUCCESS_MSG = "success";

    private Integer code;
    private String msg;
    private T data;

    // ==================== Success ====================

    /**
     * 成功（无数据），用于新增/修改/删除等不需要返回数据的操作
     */
    public static R<Void> ok() {
        return restResult(null, SUCCESS_CODE, SUCCESS_MSG);
    }

    /**
     * 成功（携带单个数据对象）
     */
    public static <T> R<T> ok(T data) {
        return restResult(data, SUCCESS_CODE, SUCCESS_MSG);
    }

    // ==================== Error ====================

    /**
     * 失败（使用 ResultCode 枚举）
     */
    public static R<Void> error(ResultCode resultCode) {
        return restResult(null, resultCode.getCode(), resultCode.getMessage());
    }

    /**
     * 失败（使用 ResultCode 枚举 + 自定义 message，覆盖枚举默认 message）
     */
    public static R<Void> error(ResultCode resultCode, String message) {
        return restResult(null, resultCode.getCode(), message);
    }

    /**
     * 失败（自定义 code + message）
     */
    public static R<Void> error(int code, String msg) {
        return restResult(null, code, msg);
    }

    // ==================== Utility ====================

    /**
     * 判断是否为成功响应
     */
    public boolean isSuccess() {
        return SUCCESS_CODE == this.code;
    }

    /**
     * 链式添加键值对，要求 data 为 Map 类型（通过 {@link #ok(Object)} 传入 Map 创建）。
     *
     * <pre>{@code
     * Map<String, Object> data = new HashMap<>();
     * data.put("coupons", list);
     * data.put("total", 2);
     * R.ok(data);
     * }</pre>
     *
     * @throws IllegalStateException 如果 data 不是 Map 类型
     */
    @SuppressWarnings("unchecked")
    public R<T> put(String key, Object value) {
        if (this.data == null) {
            this.data = (T) new HashMap<String, Object>();
        } else if (!(this.data instanceof Map)) {
            throw new IllegalStateException(
                    "put() 要求 data 为 Map 类型，当前 data 类型: " + this.data.getClass().getName());
        }
        ((Map<String, Object>) this.data).put(key, value);
        return this;
    }

    // ==================== Internal ====================

    private static <T> R<T> restResult(T data, int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
