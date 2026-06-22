package com.mymall.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 请求日志全局过滤器
 * <p>
 * 记录所有经过网关的请求和响应，包括耗时时间。
 * <p>
 * 执行顺序：order = -100（比 TraceIdFilter 后执行，比框架路由 Filter 先执行）
 */
@Component
public class RequestLogFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);
    private static final String START_TIME = "gatewayRequestStartTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String query = request.getURI().getQuery();
        String clientIp = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        // 记录请求开始
        String logQuery = query != null ? "?" + query : "";
        log.info(">>> [Gateway] {} {}{} from {}", method, path, logQuery, clientIp);

        // 记录开始时间
        exchange.getAttributes().put(START_TIME, System.currentTimeMillis());

        // 继续过滤器链，响应返回时记录耗时
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            Long startTime = exchange.getAttribute(START_TIME);
            if (startTime != null) {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = exchange.getResponse().getStatusCode() != null
                        ? exchange.getResponse().getStatusCode().value()
                        : 0;
                Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                String routeId = route != null ? route.getId() : "null";
                String routeUri = route != null ? route.getUri().toString() : "-";
                log.info("<<< [Gateway] {} {}{} -> {} ({}ms) route={} uri={}",
                        method, path, logQuery, statusCode, duration, routeId, routeUri);
            }
        }));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
