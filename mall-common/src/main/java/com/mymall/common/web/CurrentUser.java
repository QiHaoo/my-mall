package com.mymall.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 Controller 方法参数为当前登录用户
 *
 * <p>支持两种参数类型：
 * <ul>
 *   <li>{@code @CurrentUser Long userId} —— 仅需用户 ID 的简单场景</li>
 *   <li>{@code @CurrentUser UserInfo user} —— 需要 username / roles 的完整场景</li>
 * </ul>
 *
 * <p>未登录时统一返回 {@code null}，业务层自行决定是否抛
 * {@code BizException(ResultCode.UNAUTHORIZED)}。这样设计的原因：登录态校验交给网关 +
 * 业务主动判断更灵活（如商品详情页允许匿名访问，但下单接口必须登录）。
 *
 * <p>使用示例：
 * <pre>{@code
 * @GetMapping("/orders/{orderId}")
 * public R<OrderVO> getOrder(@PathVariable Long orderId, @CurrentUser Long currentUserId) {
 *     if (currentUserId == null) {
 *         throw new BizException(ResultCode.UNAUTHORIZED);
 *     }
 *     return R.ok(orderService.getOrderForUser(orderId, currentUserId));
 * }
 *
 * @GetMapping("/me")
 * public R<UserInfo> me(@CurrentUser UserInfo user) {
 *     if (user == null) {
 *         throw new BizException(ResultCode.UNAUTHORIZED);
 *     }
 *     return R.ok(user);
 * }
 * }</pre>
 *
 * @see CurrentUserArgumentResolver
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
