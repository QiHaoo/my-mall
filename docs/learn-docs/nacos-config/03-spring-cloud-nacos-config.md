# Spring Cloud Nacos Config 实战

## 1. 依赖配置

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

## 2. 配置方式

### 2.1 Spring Cloud 2021+ (spring.config.import)

```yaml
# application.yml
spring:
  application:
    name: mall-coupon
  config:
    import:
      - "nacos:mall-coupon.yaml?refreshEnabled=true"
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        namespace: my-mall
        group: DEFAULT_GROUP
        username: nacos
        password: nacos
```

### 2.2 传统方式 (bootstrap.yml)

```yaml
# bootstrap.yml (Spring Cloud 2020 及之前版本)
spring:
  application:
    name: mall-coupon
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        namespace: my-mall
        file-extension: yaml
```

> **注意**：Spring Cloud 2021+ 默认禁用 bootstrap，推荐使用 `spring.config.import`。

## 3. 动态刷新

### 3.1 @RefreshScope

```java
@RestController
@RefreshScope  // 启用动态刷新
public class ConfigController {

    @Value("${coupon.discount:0.9}")
    private Double discount;

    @GetMapping("/discount")
    public Double getDiscount() {
        return discount;  // 配置变更时自动更新
    }
}
```

### 3.2 @ConfigurationProperties（推荐）

```java
@Data
@Component
@ConfigurationProperties(prefix = "coupon")
@RefreshScope
public class CouponConfig {
    private Double discount;
    private Boolean promotionEnabled;
    private List<String> hotCategories;
}
```

**优势**：
- 类型安全
- IDE 支持
- 结构清晰

## 4. 多配置文件

### 4.1 加载多个配置

```yaml
spring:
  config:
    import:
      - "nacos:mall-coupon.yaml"              # 服务配置
      - "nacos:application-common.yaml"       # 公共配置
      - "nacos:mall-coupon-dev.yaml?refreshEnabled=true"  # 环境配置
```

### 4.2 配置优先级

```
后加载的配置 > 先加载的配置
Nacos 配置 > 本地 application.yml
```

## 5. Profile 配置

### 5.1 按环境加载

```yaml
spring:
  config:
    import:
      - "nacos:mall-coupon.yaml"
      - "nacos:mall-coupon-${spring.profiles.active}.yaml"
  profiles:
    active: dev
```

### 5.2 扩展配置

```yaml
spring:
  cloud:
    nacos:
      config:
        extension-configs:
          - data-id: redis.yaml
            group: DEFAULT_GROUP
            refresh: true
          - data-id: mysql.yaml
            group: DEFAULT_GROUP
            refresh: true
```

## 6. 配置监听

### 6.1 监听配置变更事件

```java
@Component
public class ConfigChangeListener {

    @EventListener
    public void onRefresh(RefreshEvent event) {
        System.out.println("配置已刷新: " + event.getSource());
    }
}
```

### 6.2 @NacosConfigListener

```java
@Component
public class MyConfigListener {

    @NacosConfigListener(
        dataId = "mall-coupon.yaml",
        groupId = "DEFAULT_GROUP"
    )
    public void onChange(String newConfig) {
        System.out.println("配置变更: " + newConfig);
    }
}
```

## 7. 动态数据源配置

```java
@Configuration
@RefreshScope
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Bean
    @RefreshScope
    public DataSource dataSource() {
        // 配置变更时重新创建数据源
        return DataSourceBuilder.create()
            .url(url)
            .build();
    }
}
```

## 8. 配置加密

### 8.1 Jasypt 集成

```xml
<dependency>
    <groupId>com.github.ulisesbocchio</groupId>
    <artifactId>jasypt-spring-boot-starter</artifactId>
    <version>3.0.5</version>
</dependency>
```

```yaml
# Nacos 配置
database:
  password: ENC(nrmZtkF7T0kdG1yZLQ6XzQ==)

jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD}
```

## 9. 最佳实践

### 9.1 配置分层

```
公共配置 (application-common.yaml)
├── Redis 配置
├── MySQL 配置
└── 日志配置

服务配置 (mall-coupon.yaml)
├── 业务配置
└── 服务特有配置
```

### 9.2 敏感配置

```yaml
# 敏感配置使用环境变量或加密
database:
  username: ${DB_USERNAME:root}
  password: ${DB_PASSWORD:root}
```

### 9.3 配置命名规范

```yaml
# 推荐
coupon:
  demo:
    message: "Hello"
    discount: 0.85

# 避免
couponDemoMessage: "Hello"
coupon_demo_discount: 0.85
```

## 10. 核心概念深度解析

### 10.1 旧方式 vs 新方式：配置加载机制变迁

**旧方式（bootstrap.yml，Spring Cloud 2020 及之前）**：

```yaml
# bootstrap.yml
spring:
  application:
    name: mall-coupon
  cloud:
    nacos:
      config:
        file-extension: yaml
```

Nacos 根据 `服务名 + 扩展名` **自动推导**要加载的配置：
- `mall-coupon.yaml`（主配置）
- `mall-coupon-dev.yaml`（Profile 配置，如果 `spring.profiles.active=dev`）
- `application.yaml`（公共配置，所有服务共享）

你不需要显式指定 Data ID，框架自动拼。

**新方式（spring.config.import，Spring Cloud 2021+）**：

```yaml
spring:
  config:
    import:
      - "nacos:mall-coupon.yaml?refreshEnabled=true"
```

**完全显式指定**，框架不再自动推导。你写什么 Data ID 就加载什么：

| 场景 | 旧方式 | 新方式 |
|------|--------|--------|
| 主配置 | 自动加载 `{服务名}.yaml` | 必须显式 import |
| 公共配置 | 自动加载 `application.yaml` | 必须显式 import |
| Profile 配置 | 自动加载 `{服务名}-{profile}.yaml` | 必须显式 import |
| 额外配置 | 用 `extension-configs` | import 或 extension-configs |

**设计哲学**：显式优于隐式。避免「不知道框架自动加载了哪些配置」的问题。

### 10.2 `refreshEnabled` 参数

```yaml
# 开启 → Nacos 客户端监听此配置的变更，实时推送
- "nacos:mall-coupon.yaml?refreshEnabled=true"

# 不开启 → 仅在启动时拉取一次，后续变更不推送
- "nacos:shared-common.yaml"
```

`refreshEnabled=true` 的作用是让 Nacos 客户端对该 Data ID 开启**长轮询监听**：

```
Nacos Server                              Client
    │                                      │
    │◄──── Pull Request (带 MD5) ──────│
    │                                      │
    │──── Hold 30s (配置未变更) ──────►│
    │                                      │
    │◄──── 管理员修改配置 ───────────│
    │                                      │
    │──── 立即返回新配置 ──────────────►│
    │                                      │
```

### 10.3 `refreshEnabled` vs `@RefreshScope`：两层刷新机制

动态刷新需要**两层配合**，缺一不可：

```
Nacos 配置变更
    ↓
① refreshEnabled=true   → Nacos 客户端收到变更通知，触发 Spring RefreshEvent
    ↓
② @RefreshScope          → Spring 收到 RefreshEvent，销毁并重建带此注解的 Bean
    ↓
③ @Value 重新注入        → Bean 重建时读取最新配置值
```

| 维度 | `refreshEnabled=true` | `@RefreshScope` |
|------|----------------------|------------------|
| 归属 | Nacos 客户端层 | Spring Bean 层 |
| 作用 | 告诉 Nacos “这个配置的变更请推送给我” | 告诉 Spring “这个 Bean 在刷新时重建” |
| 缺了会怎样 | 配置变了但客户端不知道 | 客户端知道配置变了但 Bean 不更新 |
| 控制粒度 | 每个 Data ID 独立控制 | 每个 Bean 独立控制 |

**典型场景**：

```yaml
spring:
  config:
    import:
      - "nacos:mall-coupon.yaml?refreshEnabled=true"  # ① 需要动态刷新
      - "nacos:shared-common.yaml"                     # 只启动时加载一次
```

```java
@RefreshScope  // ② Bean 级刷新
@RestController
public class MyController {
    @Value("${coupon.discount:0.9}")  // ③ 重新注入
    private Double discount;
}
```

### 10.4 `spring.config.import` vs `extension-configs`

两种方式都可以加载额外的 Nacos 配置，但属于不同的配置体系：

**方式 A：`spring.config.import`（Spring Cloud 2021+ 推荐）**

```yaml
spring:
  config:
    import:
      - "nacos:shared-common.yaml"
      - "nacos:shared-redis.yaml?refreshEnabled=true"
      - "nacos:mall-coupon.yaml?refreshEnabled=true"
```

**方式 B：`extension-configs`（Nacos 自带机制）**

```yaml
spring:
  config:
    import:
      - "nacos:mall-coupon.yaml?refreshEnabled=true"  # 主配置仍需 import 声明
  cloud:
    nacos:
      config:
        extension-configs:
          - data-id: shared-redis.yaml
            group: DEFAULT_GROUP
            refresh: true
          - data-id: shared-logging.yaml
            group: DEFAULT_GROUP
            refresh: true
```

| 维度 | `spring.config.import` | `extension-configs` |
|------|----------------------|---------------------|
| 所属 | Spring Boot 原生机制 | Nacos Spring Cloud 扩展 |
| 声明方式 | 每个配置文件独立一行 | 在 `nacos.config` 下列表 |
| 优先级控制 | 按 import 列表顺序（后加载的覆盖先加载的） | 按列表序号（index 越大优先级越高） |
| 刷新控制 | `?refreshEnabled=true` | `refresh: true` |
| 可读性 | 所有配置集中一处，一目了然 | 主配置和扩展配置分散两处 |

**选择建议**：

- **配置文件不多（≤ 5 个）**：统一用 `spring.config.import`，更清晰
- **共享配置很多（> 5 个）**：主配置用 import，共享配置用 `extension-configs`，保持 import 列表干净
- **本项目**：统一使用 `spring.config.import`

## 11. 常见问题

### Q1: 配置不生效？

**检查清单**：
- [ ] Namespace 是否正确
- [ ] Data ID 是否与 import 一致
- [ ] import 是否加了 `?refreshEnabled=true`（Nacos 客户端层）
- [ ] 是否加了 `@RefreshScope`（Spring Bean 层）
- [ ] 配置格式是否正确（YAML/Properties）

> 动态刷新需要两层配合：`refreshEnabled=true` + `@RefreshScope`，缺一不可。详见第 10.3 节。

### Q2: 启动报 ConfigNotFoundException？

**解决**：
```yaml
spring:
  cloud:
    nacos:
      config:
        enabled: true
        # 或允许本地 fallback
        fail-fast: false
```

### Q3: 如何禁用配置中心？

```yaml
spring:
  cloud:
    nacos:
      config:
        enabled: false
```

## 12. 下一步

- [项目实施记录](./project-implementation.md)
