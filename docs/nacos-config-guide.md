# Nacos 配置中心使用指南

## 1. 依赖配置

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

## 2. application.yml 配置

```yaml
spring:
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

**关键参数**：
| 参数 | 说明 |
|------|------|
| `spring.config.import` | 指定配置文件，`refreshEnabled=true` 启用动态刷新 |
| `namespace` | 命名空间（需提前在 Nacos 控制台创建） |
| `group` | 分组，默认 `DEFAULT_GROUP` |
| `file-extension` | 配置格式，默认 yaml |

## 3. 在 Nacos 控制台创建配置

1. 访问 http://127.0.0.1:8848/nacos
2. 切换到命名空间 `my-mall`
3. 配置管理 → 配置列表 → 点击「+」新建配置
4. 填写：
   - **Data ID**: `mall-coupon.yaml`
   - **Group**: `DEFAULT_GROUP`
   - **配置格式**: YAML
   - **配置内容**:

```yaml
coupon:
  demo:
    # 基础配置
    message: "Hello from Nacos Config Center!"
    discount: 0.85
    max-order-items: 5
    hot-categories: 手机数码,家用电器,服饰鞋包

    # 功能开关
    promotion-enabled: true
    promotion-name: "双11大促"
    promotion-rule: "满200减50"

    # 动态阈值
    rate-limit: 100

    # 服务降级（0=完整 1=部分降级 2=完全降级）
    degrade-level: 0

    # 分类折扣（Map 类型）
    category-discount:
      数码: 0.95
      家电: 0.90
      服饰: 0.85
      食品: 0.98

    # A/B 测试
    ab-test:
      enabled: false
      percentage: 50
```

## 4. 代码中使用

### 4.1 @Value + @RefreshScope

```java
@RestController
@RequestMapping("/config-demo")
@RefreshScope  // 启用动态刷新
public class ConfigDemoController {

    @Value("${coupon.demo.message:默认值}")
    private String message;

    @Value("${coupon.demo.discount:0.9}")
    private Double discount;

    @GetMapping("/message")
    public String getMessage() {
        return message;
    }
}
```

### 4.2 @ConfigurationProperties（推荐）

```java
@Data
@Component
@ConfigurationProperties(prefix = "coupon.demo")
@RefreshScope
public class CouponDemoConfig {
    private String message;
    private Double discount;
    private Boolean promotionEnabled;
    private List<String> hotCategories;
}
```

## 5. 演示接口

| 接口 | 方法 | 说明 | 演示能力 |
|------|------|------|----------|
| `/config-demo/all` | GET | 配置概览 | 动态刷新 |
| `/config-demo/calculate?price=100` | GET | 折扣计算 | 配置驱动业务逻辑 |
| `/config-demo/promotion-center` | GET | 促销活动中台 | 功能开关 (Feature Toggle) |
| `/config-demo/api-call` | GET | 模拟 API 调用 | 动态阈值 (限流) |
| `/config-demo/reset-counter` | POST | 重置计数器 | - |
| `/config-demo/coupons` | GET | 优惠券查询 | 服务降级 (0/1/2 级) |
| `/config-demo/category-price?category=数码&price=200` | GET | 分类折扣 | Map 类型配置 |
| `/config-demo/ab-test` | GET | A/B 测试 | 灰度百分比 |
| `/config-demo/refresh` | POST | 手动刷新配置 | ContextRefresher API |

## 6. 验证动态刷新

```bash
# 1. 启动服务，调用接口
curl http://localhost:7000/config-demo/all

# 2. 在 Nacos 控制台修改配置
# 例如将 discount 改为 0.75

# 3. 再次调用（无需重启）
curl http://localhost:7000/config-demo/all
# 应该看到 discount 已更新为 0.75
```

### 验证场景示例

**场景 1：功能开关**
```bash
# 促销关闭时
curl http://localhost:7000/config-demo/promotion-center
# → status: CLOSED

# Nacos 中将 promotion-enabled 改为 true
curl http://localhost:7000/config-demo/promotion-center
# → status: OPEN，返回完整促销列表
```

**场景 2：服务降级**
```bash
# 完整模式 (degrade-level=0)
curl http://localhost:7000/config-demo/coupons
# → 返回 DB + Cache + Recommend 完整数据

# 改为部分降级 (degrade-level=1)
curl http://localhost:7000/config-demo/coupons
# → 仅返回缓存数据，推荐服务已降级
```

**场景 3：A/B 测试**
```bash
# Nacos 中启用 A/B 测试，设置 percentage=30
curl http://localhost:7000/config-demo/ab-test
# 多次调用，观察 A/B 版本分流比例
```

## 7. 多环境配置

### 按 Profile 加载不同配置

```yaml
spring:
  config:
    import:
      - "nacos:mall-coupon.yaml"              # 公共配置
      - "nacos:mall-coupon-${spring.profiles.active}.yaml"  # 环境配置
  profiles:
    active: dev
```

### Nacos 配置优先级

```
Nacos 环境配置 > Nacos 公共配置 > application-{profile}.yml > application.yml
```

## 8. 配置分组

| Group | 用途 |
|-------|------|
| `DEFAULT_GROUP` | 默认分组，开发环境 |
| `TEST_GROUP` | 测试环境 |
| `PROD_GROUP` | 生产环境 |

```yaml
spring:
  cloud:
    nacos:
      config:
        group: PROD_GROUP
```

## 9. 常见问题

### Q: 配置不生效？
- 检查 namespace 是否正确
- 检查 Data ID 是否与 `spring.config.import` 一致
- 检查是否添加了 `@RefreshScope`

### Q: 如何监听配置变化？
```java
@RefreshScope
@Component
public class MyService {
    @Autowired
    private Environment env;

    // 通过 Environment 获取最新配置
    public String getConfig() {
        return env.getProperty("coupon.demo.message");
    }
}
```

### Q: 配置文件找不到？
启动时报错 `No spring.config.import property has been defined`，需要添加：
```yaml
spring:
  config:
    import: "nacos:mall-coupon.yaml"
```

---

## 10. 配置中心规范

### 10.1 命名空间与分组

与服务发现保持一致：

| 维度 | 规范 | 说明 |
|------|------|------|
| Namespace | 按环境划分 | `my-mall-dev` / `my-mall-test` / `my-mall-prod` |
| Group | 统一 `DEFAULT_GROUP` | 不做额外分组 |

> **生产要点**：不同环境的 Nacos Server 必须是独立实例，不能共用同一个 Server 靠 Namespace 隔离。

### 10.2 Data ID 命名

| 类型 | 命名格式 | 示例 |
|------|---------|------|
| 服务配置 | `{服务名}.yaml` | `mall-coupon.yaml` |
| 共享配置 | `shared-{名称}.yaml` | `shared-redis.yaml` |

> **规则**：Data ID 与服务名保持一致，全小写 + 短横线。

### 10.3 配置拆分策略

配置分三层：

```
共享配置（shared-*.yaml）
├── shared-redis.yaml          → Redis 连接信息（多服务共用）
├── shared-logging.yaml        → 日志级别、格式
└── shared-common.yaml         → 通用配置（时区、编码、通用业务参数）

服务配置（{服务名}.yaml）
├── 本服务独有的业务配置
└── 本服务的数据库连接（每个服务独立数据库）

本地配置（application.yml）
└── 服务名、端口、Nacos 地址等启动必需配置
```

**拆分原则**：

| 配置类型 | 存放位置 | 理由 |
|---------|---------|------|
| 服务名、端口 | 本地 application.yml | 每个服务不同，启动必需 |
| Nacos 地址 | 本地 application.yml | 配置中心自身的地址不能放在配置中心里 |
| 数据库连接 | 服务配置 | **生产环境每个服务独立数据库**，连接信息不同 |
| Redis 连接 | 共享配置 | 同一环境多个服务共用同一个 Redis 集群 |
| 业务开关/阈值 | 服务配置 | 需要动态刷新 |
| 日志级别 | 共享配置 | 统一管理，支持动态调整 |
| 第三方 API 地址 | 共享配置 | 多服务共用 |
| Feign 超时 | 共享配置 | 统一服务间调用超时策略 |

> **生产要点**：数据库连接放服务配置而非共享配置。生产环境遵循「一个服务一个数据库」原则，避免服务间数据库耦合。

### 10.4 配置加载顺序

```yaml
spring:
  config:
    import:
      - "nacos:shared-common.yaml"                # 1. 公共配置
      - "nacos:shared-redis.yaml"                  # 2. Redis 配置
      - "nacos:mall-coupon.yaml?refreshEnabled=true"  # 3. 服务配置（可覆盖）
```

**优先级**：后加载的覆盖先加载的

```
mall-coupon.yaml > shared-redis.yaml > shared-common.yaml > 本地 application.yml
```

### 10.5 配置 Key 命名

统一使用小写 + 短横线（kebab-case）：

```yaml
# ✅ 推荐
coupon:
  order-timeout: 30        # 订单超时（分钟）
  max-retry-count: 3       # 最大重试次数
  promotion-enabled: true  # 促销开关

# ❌ 避免
coupon:
  orderTimeout: 30         # 驼峰命名
  ORDER_TIMEOUT: 30        # 大写命名
  order_timeout: 30        # 下划线命名
```

### 10.6 敏感配置

**开发环境**：环境变量占位符 + 默认值

```yaml
spring:
  datasource:
    password: ${DB_PASSWORD:root}
```

**生产环境**：必须使用以下方案之一，禁止明文写入配置

| 方案 | 说明 | 推荐度 |
|------|------|--------|
| 环境变量注入 | K8s Secret → Pod 环境变量 | ⭐⭐⭐ |
| Jasypt 加密 | 配置文件中存密文，启动时解密 | ⭐⭐ |
| K8s ConfigMap + Secret | 敏感信息用 Secret，非敏感用 ConfigMap | ⭐⭐⭐ |

```yaml
# 生产环境配置（K8s Secret 注入）
spring:
  datasource:
    password: ${DB_PASSWORD}    # 无默认值，未注入则启动失败
```

> **生产要点**：默认密码 `nacos/nacos` 必须在生产环境修改。所有敏感配置（密码、密钥、Token）禁止明文写入 Nacos 配置文件。

### 10.7 配置变更规范

| 规则 | 说明 |
|------|------|
| **先改配置，再发布** | 配置变更和服务发布分开操作，避免同时变更 |
| **灰度发布** | 先用 Nacos Beta 发布到少量实例，验证无误后全量 |
| **保留历史** | Nacos 自带配置历史版本，定期清理无效配置 |
| **配置审查** | 生产环境配置变更需 Code Review（可通过 Git 管理 Nacos 配置） |

### 10.8 当前项目配置清单

| Data ID | Group | 说明 | 状态 |
|---------|-------|------|------|
| `mall-coupon.yaml` | DEFAULT_GROUP | 营销中心业务配置 | ✅ 已创建 |
| `shared-common.yaml` | DEFAULT_GROUP | 共享通用配置 | ⏳ 待创建 |
| `shared-redis.yaml` | DEFAULT_GROUP | 共享 Redis 配置 | ⏳ 待创建 |
| `shared-logging.yaml` | DEFAULT_GROUP | 共享日志配置 | ⏳ 待创建 |
| `mall-member.yaml` | DEFAULT_GROUP | 会员中心业务配置 | ⏳ 待创建 |
| `mall-product.yaml` | DEFAULT_GROUP | 商品中心业务配置 | ⏳ 待创建 |
| `mall-order.yaml` | DEFAULT_GROUP | 订单中心业务配置 | ⏳ 待创建 |
| `mall-ware.yaml` | DEFAULT_GROUP | 库存中心业务配置 | ⏳ 待创建 |
