package com.mymall.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 链路追踪全局过滤器
 * <p>
 * 为每个请求生成唯一的 TraceId，传递给下游服务和返回给客户端。
 * <p>
 * 执行顺序：order = -200（最先执行，确保 TraceId 在所有 Filter 之前生成）
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查请求中是否已有 TraceId（可能从上游传入）
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = generateTraceId();
        }

        // 将 TraceId 放入请求头，传递给下游服务
        String finalTraceId = traceId;
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        // 将 TraceId 放入响应头，返回给客户端
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        // 记录日志
        log.debug("[TraceId] Generated {} for {}", finalTraceId, exchange.getRequest().getURI().getPath());

        // 继续过滤器链
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 生成 TraceId（去掉横线的 UUID，32 位）
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
