# 本项目配置中心实施记录

> 记录 my-mall 项目中 Nacos 配置中心的配置和实施情况

## 1. 当前状态

| 模块 | 配置中心 | 状态 |
|------|---------|------|
| mall-coupon | ✅ 已配置 | 演示中 |
| mall-member | ⏳ 待配置 | - |
| mall-product | ⏳ 待配置 | - |
| mall-order | ⏳ 待配置 | - |
| mall-ware | ⏳ 待配置 | - |

## 2. mall-coupon 配置详情

### 2.1 依赖

```xml
<!-- mall-coupon/pom.xml -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

### 2.2 application.yml

```yaml
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

### 2.3 Nacos 控制台配置

**Data ID**: `mall-coupon.yaml`

**配置内容**：
```yaml
coupon:
  demo:
    message: "Hello from Nacos Config Center!"
    discount: 0.85
    promotion-enabled: true
    hot-categories:
      - 手机数码
      - 家用电器
      - 服饰鞋包
```

## 3. 演示 Controller

**文件**：`ConfigDemoController.java`

```java
@RestController
@RequestMapping("/config-demo")
@RefreshScope
public class ConfigDemoController {

    @Value("${coupon.demo.message:默认消息-本地配置}")
    private String message;

    @Value("${coupon.demo.discount:0.9}")
    private Double discount;

    @Value("${coupon.demo.promotion-enabled:false}")
    private Boolean promotionEnabled;

    @Value("#{'${coupon.demo.hot-categories:数码,家电,服饰}'.split(',')}")
    private List<String> hotCategories;

    // ... 接口方法
}
```

### 演示接口

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

## 4. 验证步骤

### 4.1 启动服务

```bash
# 1. 确保 Nacos 运行
docker compose up -d nacos

# 2. 在 Nacos 控制台创建命名空间 my-mall（首次）

# 3. 在 Nacos 控制台创建配置
# Data ID: mall-coupon.yaml
# Group: DEFAULT_GROUP
# 格式: YAML
# 内容: 见上文配置

# 4. 启动服务
mvn spring-boot:run -pl mall-coupon
```

### 4.2 调用接口

```bash
# 配置概览
curl http://localhost:7000/config-demo/all

# 功能开关演示（促销关闭时返回 CLOSED）
curl http://localhost:7000/config-demo/promotion-center

# 服务降级演示（降级级别 0 = 完整模式）
curl http://localhost:7000/config-demo/coupons

# A/B 测试演示
curl http://localhost:7000/config-demo/ab-test
```

### 4.3 验证动态刷新

```bash
# 1. 修改 Nacos 配置
# 将 discount 改为 0.75

# 2. 再次调用（无需重启服务）
curl http://localhost:7000/config-demo/calculate?price=100

# 响应
{
  "originalPrice": 100,
  "discount": 0.75,
  "finalPrice": 75.00,
  "_tip": "修改 coupon.demo.discount 可动态调整全局折扣"
}
```

## 5. 配置项说明

| 配置项 | 默认值 | 说明 | 演示能力 |
|--------|--------|------|----------|
| `coupon.demo.message` | 默认消息-本地配置 | 字符串配置 | 动态刷新 |
| `coupon.demo.discount` | 0.9 | 全局折扣 | 配置驱动业务逻辑 |
| `coupon.demo.promotion-enabled` | false | 促销模块开关 | 功能开关 |
| `coupon.demo.promotion-name` | 默认促销 | 促销名称 | 功能开关 |
| `coupon.demo.promotion-rule` | 满100减10 | 促销规则 | 功能开关 |
| `coupon.demo.rate-limit` | 100 | 限流阈值 | 动态阈值 |
| `coupon.demo.degrade-level` | 0 | 降级级别 | 服务降级 |
| `coupon.demo.category-discount.*` | 0.95/0.90/0.85/0.98 | 分类折扣 | Map 配置 |
| `coupon.demo.ab-test.enabled` | false | A/B 测试开关 | 灰度发布 |
| `coupon.demo.ab-test.percentage` | 50 | B 版本流量占比 | 灰度发布 |
| `coupon.demo.hot-categories` | 数码,家电,服饰 | 热门分类 | SpEL 表达式 |

## 6. 后续规划

- [ ] 为其他模块添加配置中心
- [ ] 配置多环境（dev/test/prod）
- [ ] 添加配置变更监听
- [ ] 敏感配置加密

## 7. 相关文件

| 文件 | 说明 |
|------|------|
| `mall-coupon/pom.xml` | 依赖配置 |
| `mall-coupon/src/main/resources/application.yml` | 本地配置 |
| `mall-coupon/src/main/java/.../ConfigDemoController.java` | 演示 Controller |
| `docs/nacos-config-guide.md` | 项目配置中心指南 |

## 8. 更新日志

| 日期 | 内容 |
|------|------|
| 2026-06-21 | mall-coupon 配置中心演示完成 |
