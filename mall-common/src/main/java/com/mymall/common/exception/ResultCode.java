package com.mymall.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举
 * <p>
 * 码段规划：
 * <ul>
 *   <li>200 - 成功（与 HTTP 200 对齐，R.code=200 即成功）</li>
 *   <li>400~499 - 通用客户端错误（参数/认证/权限/路由）</li>
 *   <li>500~599 - 通用服务端错误</li>
 *   <li>40001~49999 - 优惠券服务</li>
 *   <li>50001~59999 - 商品服务</li>
 *   <li>51001~51999 - 商品分类</li>
 *   <li>52001~52999 - 对象存储</li>
 *   <li>60001~69999 - 订单服务</li>
 *   <li>70001~79999 - 会员服务</li>
 *   <li>80001~89999 - 库存服务</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ==================== 通用 ====================
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或 token 已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ==================== 优惠券服务 40001+ ====================
    COUPON_NOT_FOUND(40001, "优惠券不存在"),
    COUPON_EXPIRED(40002, "优惠券已过期"),
    COUPON_LIMIT_EXCEEDED(40003, "优惠券领取数量已达上限"),

    // ==================== 商品服务 50001+ ====================
    PRODUCT_NOT_FOUND(50001, "商品不存在"),
    PRODUCT_OFF_SHELF(50002, "商品已下架"),

    // ==================== 商品分类 51001+ ====================
    CATEGORY_NOT_FOUND(51001, "分类不存在"),
    CATEGORY_HAS_PRODUCTS(51002, "分类下存在关联商品，无法删除"),
    CATEGORY_HAS_BRANDS(51003, "分类下存在关联品牌，无法删除"),
    CATEGORY_LEVEL_EXCEEDED(51004, "分类最多支持三级"),
    CATEGORY_NAME_DUPLICATE(51005, "同级分类名称已存在"),
    CATEGORY_CIRCULAR_REF(51006, "不能将分类移动到自身的子节点下"),
    CATEGORY_ROOT_DELETE(51007, "一级分类不允许删除"),

    // ==================== 对象存储 52001+ ====================
    OSS_BUCKET_NOT_ALLOWED(52001, "Bucket 不在白名单中"),
    OSS_FILE_TOO_LARGE(52002, "文件超过大小限制"),
    OSS_CONTENT_TYPE_NOT_ALLOWED(52003, "不支持的文件类型"),
    OSS_UPLOAD_ID_EXPIRED(52004, "上传凭证已过期"),
    OSS_UPLOAD_ID_NOT_FOUND(52005, "上传 ID 不存在"),
    OSS_FILE_NOT_FOUND(52006, "文件不存在"),
    OSS_UPLOAD_VERIFY_FAILED(52007, "文件上传验证失败"),

    // ==================== 订单服务 60001+ ====================
    ORDER_NOT_FOUND(60001, "订单不存在"),
    ORDER_STATUS_ERROR(60002, "订单状态异常"),

    // ==================== 会员服务 70001+ ====================
    MEMBER_NOT_FOUND(70001, "会员不存在"),
    MEMBER_DISABLED(70002, "会员已被禁用"),

    // ==================== 库存服务 80001+ ====================
    STOCK_NOT_ENOUGH(80001, "库存不足"),
    ;

    private final int code;
    private final String message;
}
