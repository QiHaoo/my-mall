package com.mymall.common.util;

/**
 * 请求级用户上下文
 *
 * <p>存放当前请求的登录用户身份（{@link UserInfo}），由网关鉴权后通过 {@code X-User-Id} /
 * {@code X-User-Name} / {@code X-User-Roles} 请求头透传，各微服务通过
 * {@link com.mymall.common.web.UserContextFilter} 解析写入本上下文。
 * Service 层调用 {@link #getUserId()} 即可获取上传者身份，无需逐层透传参数。
 *
 * <p>基于 ThreadLocal 实现，{@link com.mymall.common.web.UserContextFilter} 在请求结束时
 * 负责清理，避免线程池串号。
 *
 * <p><b>线程池注意</b>：本实现为普通 ThreadLocal，在 {@code @Async}、线程池（如定时清理任务、
 * CompletableFuture 默认 ForkJoinPool）中<b>不会</b>自动传递。异步逻辑若需要用户身份，
 * 必须在主线程取出 userId 后作为显式参数传递，或升级为 {@code TransmittableThreadLocal}。
 * 当前所有业务调用均在请求线程内完成，够用。
 *
 * <p><b>设计说明</b>：保留 {@link #getUserId()} 静态方法是为了兼容 {@code MyMetaObjectHandler}
 * 等已存在的调用点（仅需要 userId），避免本次基础设施升级引起大规模改动。需要完整用户信息时
 * 调用 {@link #get()} 获取 {@link UserInfo}。
 */
public final class UserContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 设置当前请求用户身份
     */
    public static void set(UserInfo user) {
        HOLDER.set(user);
    }

    /**
     * 获取当前登录用户完整信息，未登录返回 null
     */
    public static UserInfo get() {
        return HOLDER.get();
    }

    /**
     * 获取当前登录用户 ID，未登录返回 null
     *
     * <p>兼容方法：供 {@code MyMetaObjectHandler} 等仅需 userId 的调用点使用。
     */
    public static Long getUserId() {
        UserInfo user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
