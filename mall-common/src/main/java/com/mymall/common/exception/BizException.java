package com.mymall.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * <p>
 * Service 层统一抛出的运行时异常，由 GlobalExceptionHandler 统一捕获处理。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 使用 ResultCode 枚举
 * throw new BizException(ResultCode.COUPON_NOT_FOUND);
 *
 * // 使用自定义 code + message
 * throw new BizException(40010, "该优惠券已被领完");
 *
 * // 使用 ResultCode 枚举 + 自定义 message（覆盖默认 message）
 * throw new BizException(ResultCode.STOCK_NOT_ENOUGH, "SKU[" + skuId + "] 库存不足");
 * }</pre>
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    /**
     * 自定义 code + message
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 使用 ResultCode 枚举
     */
    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 使用 ResultCode 枚举 + 自定义 message
     */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
