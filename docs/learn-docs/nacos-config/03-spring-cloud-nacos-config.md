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

## 10. 常见问题

### Q1: 配置不生效？

**检查清单**：
- [ ] Namespace 是否正确
- [ ] Data ID 是否与 import 一致
- [ ] 是否添加了 @RefreshScope
- [ ] 配置格式是否正确（YAML/Properties）

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

## 11. 下一步

- [项目实施记录](./project-implementation.md)
