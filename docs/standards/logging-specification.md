# 日志输出规范

> 对标生产级日志体系，所学内容可直接迁移到生产环境。
>
> **文档范围**：本文档覆盖日志输出架构、MDC 链路追踪、logback-spring.xml 配置、各环境差异、
> 文件滚动与归档、MyBatis SQL 日志、Loki 采集、敏感信息脱敏。
>
> **与编码规范的关系**：[编码规范 - 第五章 日志](./coding-standards.md#五日志) 规定"怎么写日志代码"
> （框架选型、级别、占位符、各层策略），本文档规定"日志怎么输出、怎么采集、怎么带链路上下文"。
> 两者互补，共同构成完整的日志规范。

---

## 一、整体架构

### 1.1 日志流转链路

```
应用代码 log.info(...)
    │
    ▼
SLF4J ──► Logback（logback-spring.xml 配置）
    │           │
    │           ├── 控制台（dev：人类可读 / prod：JSON）
    │           └── 文件（滚动归档，按天 + 大小切割）
    │
    ▼
Promtail（采集 agent）
    │   读取 ./logs/{service}/*.log，打上 job/instance 标签
    ▼
Loki（日志存储与索引）
    │
    ▼
Grafana（查询面板，按 traceId 串联全链路）
```

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **生产可观测** | 日志必须落文件 + 被采集，不能只打控制台（容器重启即丢失） |
| **链路可串** | 每条日志带 traceId，能在 Grafana 按 traceId 串联一次请求的全链路 |
| **环境隔离** | dev 重可读性（控制台彩色），prod 重可采集（JSON 结构化） |
| **容量可控** | 滚动切割 + 保留天数 + 总量上限，避免磁盘打满 |
| **性能无损** | 生产用 AsyncAppender，日志 IO 不阻塞业务线程 |

---

## 二、日志框架与依赖

### 2.1 框架选型

统一使用 **SLF4J + Logback**（Spring Boot 默认），不直接使用 `System.out.println`、不引入 Log4j2。

- 编码侧用 Lombok `@Slf4j` 注解获取 `log` 对象，详见 [编码规范 - 5.1 日志框架](./coding-standards.md#51-日志框架)。
- 配置侧用 `logback-spring.xml`（**不要**用 `logback.xml`，后者无法使用 Spring Profile 区分环境）。

### 2.2 生产环境结构化日志依赖

生产环境日志必须以 **JSON 格式**输出，便于 Promtail 采集和 Loki 字段索引。引入 `logstash-logback-encoder`：

```xml
<!-- mall-common/pom.xml 或各服务 pom.xml -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

> 该依赖同时兼容 Logback 1.5（Spring Boot 3.4 内置版本）。JSON 输出会自动包含 MDC 字段（`traceId` 等）、
> 时间戳、线程、logger、level、message，无需手动拼装。

### 2.3 链路追踪演进路径

| 阶段 | 方案 | traceId 来源 | MDC key |
|------|------|-------------|---------|
| **当前** | 自建 TraceIdFilter（header `X-Trace-Id` 透传） | UUID 生成 | `traceId` |
| **未来** | Micrometer Tracing + OpenTelemetry（对接 Tempo） | OTel SDK 自动生成 W3C traceId | `traceId`（OTel 默认注入 MDC） |

> **关键约定**：MDC key 统一用 `traceId`（小写驼峰），与 Micrometer Tracing 默认 key 一致。
> 未来切换到 OTel 时，logback pattern 中的 `%X{traceId}` 无需修改，实现平滑迁移。
>
> 当前阶段的 TraceIdFilter 已在 Gateway 实现（见 [第三章](#三mdc-与链路追踪)），业务服务侧需补充 MDC Filter。

---

## 三、MDC 与链路追踪

### 3.1 traceId 注入链路

```
客户端
  │  （无 X-Trace-Id）
  ▼
mall-gateway: TraceIdFilter
  │  ① 检查 header，无则生成 UUID（去横线，32位）
  │  ② 写入请求头 X-Trace-Id 透传给下游
  │  ③ 写入响应头 X-Trace-Id 返回客户端
  ▼
业务服务（Servlet MVC）: TraceIdFilter
  │  ① 从 header 取 X-Trace-Id，无则生成
  │  ② 放入 MDC（key=traceId）
  │  ③ finally 清理 MDC（防 ThreadLocal 泄漏）
  ▼
log.info(...)  ──► Logback pattern %X{traceId} 自动输出
  │
  ▼
Feign 调用下游服务: FeignTraceIdInterceptor
  │  从 MDC 取 traceId，放入请求头 X-Trace-Id
  ▼
下游业务服务（同上，traceId 延续）
```

### 3.2 Gateway 侧（WebFlux，已有）

Gateway 是响应式（WebFlux），**不使用 MDC**（响应式线程切换会导致 MDC 丢失），改为在 header 中透传 traceId，
由 `RequestLogFilter` 记录请求日志。

现有实现：
- [TraceIdFilter.java](file:///d:/WorkSpace/my-mall/mall-gateway/src/main/java/com/mymall/gateway/filter/TraceIdFilter.java) — 生成/透传 `X-Trace-Id`，order=-200
- [RequestLogFilter.java](file:///d:/WorkSpace/my-mall/mall-gateway/src/main/java/com/mymall/gateway/filter/RequestLogFilter.java) — 记录请求/响应耗时，order=-100

> Gateway 日志的 traceId 在 header 中，不进 MDC。若需在 Gateway 日志中也体现 traceId，
> 可在 `RequestLogFilter` 中从 exchange 取 `X-Trace-Id` 头拼入日志消息。

### 3.3 业务服务侧（Servlet MVC，需补充）

业务服务（mall-product / mall-member / mall-coupon / mall-oss 等）基于 Servlet，**必须**在 `mall-common` 新增
`TraceIdFilter`，从 header 提取 traceId 放入 MDC。

**应在 `mall-common` 新增** `com.mymall.common.config.TraceIdFilter`：

```java
package com.mymall.common.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 业务服务 TraceId 过滤器（Servlet 版）
 * <p>
 * 从请求头 X-Trace-Id 提取链路 ID（网关已生成），放入 MDC 供 Logback 输出。
 * 若无（如直接访问服务，未经过网关），则本地生成。
 * <p>
 * MDC key 统一为 traceId，与 Micrometer Tracing 默认 key 一致，
 * 未来切换 OTel 时 pattern 无需修改。
 * <p>
 * order = Ordered.HIGHEST_PRECEDENCE + 10，确保最先执行，所有日志都能拿到 traceId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter implements Filter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = generateTraceId();
        }
        MDC.put(MDC_TRACE_ID, traceId);
        // 响应头也带回，便于前端/运维定位
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 必须清理，防止线程池复用导致 traceId 串号
            MDC.remove(MDC_TRACE_ID);
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
```

### 3.4 Feign 跨服务透传（需补充）

业务服务通过 Feign 调用其他微服务时，**必须**透传 traceId，否则下游日志无法串联。
**应在 `mall-common` 新增** `FeignTraceIdInterceptor`：

```java
package com.mymall.common.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Feign 请求拦截器：透传 traceId 到下游服务。
 * 从当前 MDC 取出 traceId，放入请求头 X-Trace-Id。
 */
@Component
public class FeignTraceIdInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if (traceId != null) {
            template.header(TraceIdFilter.TRACE_ID_HEADER, traceId);
        }
    }
}
```

> 两个组件放在 `mall-common`，通过自动装配对所有业务服务生效。新增后在
> [common 模块设计文档](../common/common-module-design.md) 速查表同步登记。

---

## 四、logback-spring.xml 配置规范

### 4.1 配置文件位置与原则

- 文件位置：`{service}/src/main/resources/logback-spring.xml`
- **必须**用 `logback-spring.xml`（不是 `logback.xml`），才能使用 `<springProfile>` 区分环境
- 每个业务服务都需要配置，不依赖 Spring Boot 默认日志输出

### 4.2 日志格式

**dev/test（人类可读，彩色）**：

```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %highlight(%-5level) [%X{traceId:-}] %cyan(%logger{40}) - %msg%n
```

- `%X{traceId:-}`：从 MDC 取 traceId，`:-` 表示无值时显示为空（不显示 `null`）
- `highlight` / `cyan`：控制台彩色，仅 ANSI 终端生效

**prod（JSON 结构化，便于采集）**：

用 `LogstashEncoder`，自动输出为 JSON，包含 `@timestamp`、`level`、`logger_name`、`thread_name`、
`message`、`traceId`（来自 MDC）等字段。

### 4.3 完整配置模板

各服务复制此模板，**仅需修改 `LOG_PATH` 和 `APP_NAME`**：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true" scanPeriod="30 seconds">

    <!-- ==================== 应用标识（按服务修改） ==================== -->
    <property name="APP_NAME" value="mall-product"/>
    <!-- 日志根目录：相对路径基于服务启动目录，生产环境建议挂载到容器卷 -->
    <property name="LOG_PATH" value="./logs/mall-product"/>
    <!-- 单文件最大大小 -->
    <property name="MAX_FILE_SIZE" value="100MB"/>
    <!-- 总大小上限 -->
    <property name="TOTAL_SIZE_CAP" value="10GB"/>
    <!-- 普通日志保留天数 -->
    <property name="MAX_HISTORY" value="30"/>
    <!-- 错误日志保留天数 -->
    <property name="MAX_HISTORY_ERROR" value="90"/>

    <!-- ==================== 控制台 Pattern（人类可读） ==================== -->
    <property name="CONSOLE_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %highlight(%-5level) [%X{traceId:-}] %cyan(%logger{40}) - %msg%n"/>

    <!-- ==================== 控制台 Appender ==================== -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${CONSOLE_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- ==================== 文件 Appender（全部级别，滚动归档） ==================== -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/${APP_NAME}.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <!-- 按天 + 按大小滚动，归档文件带日期和序号 -->
            <fileNamePattern>${LOG_PATH}/${APP_NAME}.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>${MAX_FILE_SIZE}</maxFileSize>
            <maxHistory>${MAX_HISTORY}</maxHistory>
            <totalSizeCap>${TOTAL_SIZE_CAP}</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>${CONSOLE_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- ==================== 错误日志单独文件（便于排查） ==================== -->
    <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/${APP_NAME}-error.log</file>
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>ERROR</level>
        </filter>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/${APP_NAME}-error.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>${MAX_FILE_SIZE}</maxFileSize>
            <maxHistory>${MAX_HISTORY_ERROR}</maxHistory>
            <totalSizeCap>${TOTAL_SIZE_CAP}</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>${CONSOLE_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- ==================== JSON Appender（生产环境结构化输出） ==================== -->
    <appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/${APP_NAME}.json.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/${APP_NAME}.%d{yyyy-MM-dd}.%i.json.log.gz</fileNamePattern>
            <maxFileSize>${MAX_FILE_SIZE}</maxFileSize>
            <maxHistory>${MAX_HISTORY}</maxHistory>
            <totalSizeCap>${TOTAL_SIZE_CAP}</totalSizeCap>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!-- 自动包含 MDC 字段（traceId 等），无需额外配置 -->
        </encoder>
    </appender>

    <!-- ==================== 异步 Appender（生产环境提升性能） ==================== -->
    <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
        <!-- 队列大小，默认 256，高并发场景建议调大 -->
        <queueSize>1024</queueSize>
        <!-- 队列剩余 20% 时丢弃 TRACE/DEBUG/INFO，只保留 WARN/ERROR -->
        <discardingThreshold>20</discardingThreshold>
        <!-- 不阻塞业务线程：队列满时直接丢弃（避免 OOM），生产可接受少量日志丢失 -->
        <neverBlock>true</neverBlock>
        <appender-ref ref="JSON_FILE"/>
    </appender>

    <appender name="ASYNC_ERROR" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>512</queueSize>
        <!-- 错误日志不丢弃，必须保留 -->
        <discardingThreshold>0</discardingThreshold>
        <appender-ref ref="ERROR_FILE"/>
    </appender>

    <!-- ==================== 环境隔离 ==================== -->

    <!-- 开发环境：仅控制台，彩色可读，不落文件 -->
    <springProfile name="dev">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        <!-- 业务包开 debug，便于调试 -->
        <logger name="com.mymall" level="DEBUG"/>
    </springProfile>

    <!-- 测试环境：控制台 + 可读文件 -->
    <springProfile name="test">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="FILE"/>
            <appender-ref ref="ERROR_FILE"/>
        </root>
        <logger name="com.mymall" level="DEBUG"/>
    </springProfile>

    <!-- 生产环境：JSON 控制台（供 docker logs） + JSON 异步文件（供 Promtail 采集） -->
    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
            <appender-ref ref="ASYNC_FILE"/>
            <appender-ref ref="ASYNC_ERROR"/>
        </root>
        <!-- 生产关闭业务 debug -->
        <logger name="com.mymall" level="INFO"/>
        <!-- 三方框架降噪 -->
        <logger name="org.springframework" level="WARN"/>
        <logger name="org.hibernate" level="WARN"/>
        <logger name="com.zaxxer.hikari" level="WARN"/>
    </springProfile>

</configuration>
```

> **prod 环境控制台也用 JSON？** 是的。容器化部署下，`docker logs` / `kubectl logs` 会被 Promtail 采集，
> JSON 格式能保留字段结构。若本地排障需要可读格式，临时切换 profile 到 dev 即可。

---

## 五、各环境配置差异

| 维度 | dev | test | prod |
|------|-----|------|------|
| 输出目标 | 控制台 | 控制台 + 文件 | 控制台（JSON） + 文件（JSON） |
| 日志格式 | 彩色可读 | 彩色可读 | JSON 结构化 |
| 业务包级别 | DEBUG | DEBUG | INFO |
| 框架包级别 | INFO | INFO | WARN |
| 文件输出 | 否 | 是 | 是 |
| 异步 Appender | 否 | 否 | 是 |
| traceId | 有（MDC） | 有（MDC） | 有（MDC） |
| 文件保留 | — | 30 天 | 30 天（error 90 天） |
| 单文件大小 | — | 100MB | 100MB |
| 总量上限 | — | 10GB | 10GB |

> dev 环境不落文件：本地开发频繁重启，文件碎片化无意义，控制台足够。

---

## 六、日志文件管理

### 6.1 目录与命名约定

```
./logs/
├── mall-gateway/
│   ├── mall-gateway.log            # 全量日志
│   ├── mall-gateway-error.log      # 仅 ERROR
│   └── mall-gateway.2026-06-30.0.log.gz   # 归档
├── mall-product/
│   ├── mall-product.json.log       # 生产 JSON 格式
│   ├── mall-product-error.log
│   └── ...
└── {其他服务}/
```

- 目录名与服务名一致（`mall-{module}`）
- 主日志文件：`{APP_NAME}.log`（dev/test）或 `{APP_NAME}.json.log`（prod）
- 错误日志：`{APP_NAME}-error.log`，单独切割便于快速定位故障
- 归档文件：`{APP_NAME}.{yyyy-MM-dd}.{序号}.log.gz`，gz 压缩节省空间

### 6.2 滚动策略

统一用 `SizeAndTimeBasedRollingPolicy`（同时按时间 + 大小滚动）：

| 参数 | 值 | 说明 |
|------|-----|------|
| `maxFileSize` | 100MB | 单文件超过即切割，避免单文件过大难读取 |
| `maxHistory` | 30 | 普通日志保留 30 天 |
| `maxHistory`（error） | 90 | 错误日志保留 90 天，便于事后追溯 |
| `totalSizeCap` | 10GB | 总量上限，超过自动删除最老归档，防止磁盘打满 |

### 6.3 容器化部署注意

- 日志目录必须挂载到宿主机卷（`-v ./logs:/app/logs`），否则容器重启日志丢失
- 或配置 Docker logging driver 为 `json-file` + `max-size` + `max-file`，让 docker 管理控制台日志滚动
- K8s 环境建议用 sidecar 或 node-level Promtail 采集日志文件

---

## 七、MyBatis SQL 日志

### 7.1 禁用 StdOutImpl

当前各服务 `application.yml` 配置了 `log-impl: org.apache.ibatis.logging.stdout.StdOutImpl`，
这会将 SQL 直接打印到 `System.out`（**绕过 SLF4J**，不受 logback 管理，无法控制级别、无法进文件、无法带 traceId）。

```yaml
# ❌ 当前写法（生产不合适）
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

### 7.2 正确做法：用 SLF4J 日志级别控制

移除 `log-impl`，改用 `logging.level` 控制 Mapper 包级别：

```yaml
mybatis-plus:
  configuration:
    # 不配置 log-impl，MyBatis-Plus 默认用 SLF4J
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl

logging:
  level:
    # dev/test：打印 SQL（mapper 包级别设为 debug）
    com.mymall.product.mapper: debug
```

```yaml
# prod：application-prod.yml
logging:
  level:
    # 生产关闭 SQL 日志（信息量太大，靠慢查询日志和 APM 代替）
    com.mymall.product.mapper: warn
```

> 这样 SQL 日志走 SLF4J → Logback，与业务日志统一格式、统一带 traceId、统一落文件。
> 建议各服务统一改为 `Slf4jImpl` 并清理 `StdOutImpl`。

---

## 八、Loki 采集

### 8.1 采集架构

```
各服务 ./logs/{service}/*.log
         │
         ▼
Promtail（每台机器一个 agent）
    │   - 抓取日志文件
    │   - 打标签：job / instance / service
    ▼
Loki（存储）
    │   - 按 label 索引（不索引日志正文，只索引标签）
    ▼
Grafana
    - 按 service / traceId / level 查询
    - LogQL：{job="mall-product"} |= "traceId=abc123"
```

### 8.2 Promtail 配置示例

`config/promtail/promtail.yml`：

```yaml
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  # 采集所有微服务日志
  - job_name: mall-services
    static_configs:
      - targets: [localhost]
        labels:
          job: mall-services
          # 按 service 区分，通过 __path__ 通配
          __path__: /app/logs/mall-*/*.log
    pipeline_stages:
      # 解析 JSON 日志（prod 环境输出格式）
      - json:
          expressions:
            level: level
            traceId: traceId
            logger: logger_name
      # 将 traceId 提取为 label，便于按 traceId 查询
      - labels:
          level:
          traceId:
```

> **关键**：将 `traceId` 提取为 label 后，可在 Grafana 用 `{traceId="abc123"}` 直接查询全链路日志，
> 跨服务串联一次请求。

### 8.3 docker-compose 集成

`docker-compose.yml` 的 monitor profile 中补充 loki + promtail 服务，挂载日志目录：

```yaml
services:
  loki:
    image: grafana/loki:2.9.x
    profiles: [monitor]
    ports: ["3100:3100"]
    volumes:
      - ./config/loki/loki-config.yml:/etc/loki/local-config.yaml

  promtail:
    image: grafana/promtail:2.9.x
    profiles: [monitor]
    volumes:
      - ./config/promtail/promtail.yml:/etc/promtail/config.yml
      - ./logs:/app/logs:ro          # 只读挂载日志目录
    command: -config.file=/etc/promtail/config.yml
```

---

## 九、敏感信息与脱敏

### 9.1 禁止记录的字段

| 类型 | 示例 | 原因 |
|------|------|------|
| 认证凭据 | password、token、secret、apiKey | 泄漏即被冒用 |
| 个人隐私 | 身份证号、手机号、银行卡号 | 合规要求（个人信息保护法） |
| 支付信息 | 完整卡号、CVV | PCI-DSS 合规 |

### 9.2 脱敏规则

必须记录但需脱敏的字段，按规则打码后再记录：

| 字段 | 脱敏规则 | 示例 |
|------|---------|------|
| 手机号 | 保留前 3 后 4 | `138****1234` |
| 身份证号 | 保留前 3 后 4 | `110***********1234` |
| 邮箱 | 用户名仅保留首字符 | `z***@example.com` |
| 银行卡号 | 保留后 4 位 | `************1234` |

```java
// ✅ 正确：脱敏后记录
log.info("用户登录: userId={}, phone={}", userId, DesensitizeUtil.mobile(phone));

// ❌ 错误：明文记录
log.info("用户登录: phone={}", phone);
```

> 脱敏工具方法建议封装在 `mall-common` 的 `util/` 包中，全项目复用。

---

## 十、提交前自查清单

新增/修改涉及日志的代码时，对照检查：

- [ ] 日志用 SLF4J `log.xxx()` + 占位符 `{}`，不用字符串拼接？
- [ ] 异常日志 `log.error("...", e)` 保留了完整堆栈（不是 `e.getMessage()`）？
- [ ] 关键写操作（创建/修改/删除/状态流转）有 INFO 日志？
- [ ] 敏感信息（密码/token/身份证/手机号）已脱敏或不记录？
- [ ] 本服务 `logback-spring.xml` 已配置（非依赖默认）？
- [ ] 日志格式包含 `%X{traceId:-}`，能输出链路 ID？
- [ ] 跨服务 Feign 调用透传了 `X-Trace-Id`？
- [ ] MyBatis 未使用 `StdOutImpl`，改用 `Slf4jImpl`？
- [ ] 新引入的三方库未污染日志级别（必要时在 logback 中降噪）？
