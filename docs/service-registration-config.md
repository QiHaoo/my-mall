# 服务注册与配置说明

## 1. 服务注册配置

### 1.1 基础配置

每个微服务在 `application.yml` 中配置 Nacos Discovery：

```yaml
spring:
  application:
    name: mall-coupon          # 服务名称，必须与注册中心一致
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: my-mall     # 需要在 Nacos 控制台提前创建
        username: nacos        # Nacos 2.x 必须配置
        password: nacos
```

### 1.2 命名空间创建

1. 访问 http://127.0.0.1:8848/nacos
2. 登录：`nacos` / `nacos`
3. 命名空间 → 新建命名空间
4. ID 填 `my-mall`，名称填 `my-mall`

### 1.3 启动类

```java
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.mymall.coupon.mapper")
public class CouponApplication {
    public static void main(String[] args) {
        SpringApplication.run(CouponApplication.class, args);
    }
}
```

### 1.4 启动验证

```bash
# 1. 启动 Nacos
docker compose up -d nacos

# 2. 创建命名空间 my-mall（首次）

# 3. 启动服务
# IDEA: 右键 Application → Run

# 4. 查看注册结果
# http://127.0.0.1:8848/nacos → 服务列表
```

## 2. 配置中心集成

配置中心集成方式详见 [Nacos 配置中心使用指南](./nacos-config-guide.md)，本节仅记录服务注册侧的差异。

| 模块 | 配置中心状态 | spring.config.import | import-check.enabled |
|------|-------------|---------------------|---------------------|
| mall-coupon | ✅ 已启用 | `"nacos:mall-coupon.yaml?refreshEnabled=true"` | 已移除 |
| mall-member | ❌ 仅 Discovery | 未配置 | `false` |
| mall-product | ❌ 未配置 | 未配置 | `false` |
| mall-order | ❌ 未配置 | 未配置 | `false` |
| mall-ware | ❌ 未配置 | 未配置 | `false` |

## 3. 当前阶段临时配置

由于部分中间件未启动，需要禁用相关自动配置以避免启动失败。

### 3.1 禁用自动配置

```yaml
spring:
  autoconfigure:
    exclude:
      # Seata - 分布式事务（当前不需要）
      - io.seata.spring.boot.autoconfigure.SeataAutoConfiguration
      # Redisson - Redis 客户端
      - org.redisson.spring.starter.RedissonAutoConfigurationV2
      # Spring Data Redis
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
```

### 3.2 禁用原因与恢复条件

| 组件 | 禁用原因 | 恢复条件 | 恢复方式 |
|------|---------|---------|---------|
| Seata | 需要 `service.vgroupMapping` 配置 | 部署 Seata Server + 配置事务组 | 移除 exclude 项 |
| Redisson | Redis 未启动 | `docker compose up -d redis` | 移除 exclude 项 |
| Spring Data Redis | Redis 未启动 | 同上 | 移除 exclude 项 |

### 3.3 依赖清理

只保留 `resilience4j-spring-boot3`，移除 `resilience4j-spring-cloud2`（会导致 Bean 冲突）：

```xml
<!-- 保留 -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>

<!-- 移除（会导致 bulkheadEndpoint Bean 冲突） -->
<!-- <dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-cloud2</artifactId>
</dependency> -->
```

## 4. 服务端口规划

| 服务 | 端口 | 数据库 | 说明 |
|------|------|--------|------|
| mall-coupon | 7000 | mymall_sms | 营销中心 |
| mall-member | 7100 | mymall_ums | 会员中心 |
| mall-product | 7200 | mymall_pms | 商品中心 |
| mall-order | 7300 | mymall_oms | 订单中心 |
| mall-ware | 7400 | mymall_wms | 库存中心 |
| mall-gateway | 1000 | — | API 网关 |
| mall-auth | 2000 | — | 认证服务 |
| mall-search | 4000 | — | 搜索服务 |
| mall-cart | 5000 | — | 购物车 |
| mall-seckill | 6000 | — | 秒杀服务 |
| mall-third | 8000 | — | 第三方服务 |
| mall-admin | 9000 | — | 后台管理 |

## 5. 各模块 application.yml 参考

### 5.1 mall-coupon（已集成配置中心）

```yaml
server:
  port: 7000

spring:
  application:
    name: mall-coupon
  config:
    import:
      - "nacos:mall-coupon.yaml?refreshEnabled=true"
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        namespace: my-mall
        group: DEFAULT_GROUP
        username: nacos
        password: nacos
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: my-mall
        username: nacos
        password: nacos
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mymall-sms?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: root123
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  mapper-locations: classpath:mapper/coupon/*.xml
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

logging:
  level:
    com.mymall.coupon: debug
```

### 5.2 mall-member（仅服务注册）

```yaml
server:
  port: 7100

spring:
  application:
    name: mall-member
  autoconfigure:
    exclude:
      - io.seata.spring.boot.autoconfigure.SeataAutoConfiguration
      - org.redisson.spring.starter.RedissonAutoConfigurationV2
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
  cloud:
    nacos:
      config:
        import-check:
          enabled: false
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: my-mall
        username: nacos
        password: nacos
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mymall-ums?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: root123
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  mapper-locations: classpath:mapper/member/*.xml
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

logging:
  level:
    com.mymall.member: debug
```

## 6. 服务发现配置规范（生产级）

### 6.1 命名空间（Namespace）

按**环境**划分，命名统一加环境后缀：

| 命名空间 ID | 名称 | 用途 |
|-------------|------|------|
| `my-mall-dev` | 开发环境 | 本地开发（当前暂用 `my-mall`，后续迁移） |
| `my-mall-test` | 测试环境 | 集成测试 / 联调 |
| `my-mall-prod` | 生产环境 | 正式部署 |

> **原则**：命名空间做环境隔离，Group 不做环境区分（避免配置翻倍）。
>
> **生产要点**：不同环境的 Nacos Server 必须是独立实例，不能共用同一个 Server 靠 Namespace 隔离。

### 6.2 分组（Group）

本项目统一使用 `DEFAULT_GROUP`，不按 Group 做业务或环境划分。

**理由**：
- 环境已通过 Namespace 隔离
- 业务通过服务名区分
- 减少 Group 管理成本

### 6.3 服务命名

统一格式：`mall-{模块名}`，全小写 + 短横线。

### 6.4 元数据（Metadata）

按需添加，不做强制要求。推荐场景：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        metadata:
          version: v1.0          # 版本号（灰度发布时用）
```

| 场景 | 元数据 | 说明 |
|------|--------|------|
| 灰度发布 | `version` | 按版本号路由流量 |
| 就近访问 | `region` | 按区域路由 |

### 6.5 集群（Cluster）

本项目开发阶段不使用 Cluster 配置，保持默认 `DEFAULT`。

**生产部署**：
- 服务实例的 Cluster 按机房/可用区划分，实现就近访问
- 配合 Spring Cloud LoadBalancer 的 `NacosRule` 实现同集群优先调用

### 6.6 Nacos Server 部署模式

| 环境 | 模式 | 说明 |
|------|------|------|
| 开发 | 单机模式 | `docker run` 即可 |
| 测试 | 单机/双机 | 可容忍短暂不可用 |
| 生产 | 集群模式（≥ 3 节点） | 必须高可用，配合 MySQL 持久化 |

生产集群要求：
- Nacos Server ≥ 3 节点
- 使用外部 MySQL 存储（不用内嵌 Derby）
- 开启鉴权（`nacos.core.auth.enabled=true`）
- 修改默认密码（默认 `nacos/nacos` 必须改）
- 配置 `nacos.core.auth.server.identity.key/value` 防止绕过鉴权

### 6.7 生产环境检查清单

服务上线前确认以下配置：

- [ ] `autoconfigure.exclude` 已全部移除（Seata、Redis 等正常启用）
- [ ] `import-check.enabled: false` 已移除（配置中心已启用）
- [ ] `log-impl: StdOutImpl` 已移除（使用日志框架，不输出到 stdout）
- [ ] `logging.level` 调整为 `info` 或 `warn`
- [ ] `spring.config.import` 使用 `nacos:` 前缀（非 `optional:nacos:`）
- [ ] Nacos 密码已修改（非默认 `nacos/nacos`）
- [ ] `spring.datasource.password` 使用环境变量或加密，非明文
- [ ] Nacos Server 为集群部署（≥ 3 节点）
- [ ] Namespace 为对应环境（`my-mall-prod`）
