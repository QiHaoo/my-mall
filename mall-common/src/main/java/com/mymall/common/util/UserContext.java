package com.mymall.common.util;

/**
 * 请求级用户上下文
 *
 * <p>存放当前请求的登录用户 ID，由网关鉴权后通过 {@code X-User-Id} 请求头透传，
 * 各微服务通过 {@link UserContextFilter} 解析写入本上下文。Service 层直接调用
 * {@link #getUserId()} 即可获取上传者身份，无需逐层透传参数。
 *
 * <p>基于 ThreadLocal 实现，{@link UserContextFilter} 在请求结束时负责清理，避免线程池串号。
 *
 * <p><b>线程池注意</b>：本实现为普通 ThreadLocal，在 {@code @Async}、线程池（如定时清理任务、
 * CompletableFuture 默认 ForkJoinPool）中<b>不会</b>自动传递。异步逻辑若需要用户身份，
 * 必须在主线程取出 userId 后作为显式参数传递，或升级为 {@code TransmittableThreadLocal}。
 * 当前所有业务调用均在请求线程内完成，够用。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取当前登录用户 ID，未登录返回 null
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
