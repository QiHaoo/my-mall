# 服务注册配置说明

## 1. Nacos 配置

### 基础配置

```yaml
spring:
  application:
    name: mall-coupon          # 服务名称
  cloud:
    nacos:
      config:
        import-check:
          enabled: false       # 禁用配置中心检查（当前只用 Discovery）
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: my-mall     # 需要在 Nacos 控制台提前创建
        username: nacos        # Nacos 2.x 必须配置
        password: nacos
```

### 命名空间创建

1. 访问 http://127.0.0.1:8848/nacos
2. 登录：`nacos` / `nacos`
3. 命名空间 → 新建命名空间
4. ID 填 `my-mall`，名称填 `my-mall`

## 2. 当前阶段禁用的配置

由于部分中间件未启动，需要禁用以下自动配置：

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

### 禁用原因

| 组件 | 原因 | 启用条件 |
|------|------|---------|
| Seata | 需要 `service.vgroupMapping` 配置 | 配置 Seata Server + 数据库 |
| Redisson | Redis 未启动 | `docker compose up -d redis` |
| Spring Data Redis | Redis 未启动 | 同上 |

## 3. 依赖清理

### Resilience4j

只保留 `resilience4j-spring-boot3`，移除 `resilience4j-spring-cloud2`：

```xml
<!-- 保留 -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>

<!-- 移除（会导致 Bean 冲突） -->
<!-- <dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-cloud2</artifactId>
</dependency> -->
```

## 4. 完整 application.yml 示例

```yaml
server:
  port: 7000

spring:
  application:
    name: mall-coupon
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
    url: jdbc:mysql://127.0.0.1:3306/mymall_sms?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  mapper-locations: classpath:mapper/coupon/*.xml
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

## 5. 服务端口规划

| 服务 | 端口 | 数据库 |
|------|------|--------|
| mall-coupon | 7000 | mymall_sms |
| mall-member | 7100 | mymall_ums |
| mall-product | 7200 | mymall_pms |
| mall-order | 7300 | mymall_oms |
| mall-ware | 7400 | mymall_wms |

## 6. 启动验证

```bash
# 1. 启动 Nacos
docker compose up -d nacos

# 2. 创建命名空间 my-mall（首次）

# 3. 启动服务
# IDEA: 右键 Application → Run

# 4. 查看注册结果
# http://127.0.0.1:8848/nacos → 服务列表
```

---

## 7. 服务发现配置规范

### 7.1 命名空间（Namespace）

按**环境**划分，命名统一加环境后缀：

| 命名空间 ID | 名称 | 用途 |
|-------------|------|------|
| `my-mall-dev` | 开发环境 | 本地开发（当前使用 `my-mall`，后续迁移） |
| `my-mall-test` | 测试环境 | 集成测试 / 联调 |
| `my-mall-prod` | 生产环境 | 正式部署 |

> **原则**：命名空间做环境隔离，Group 不做环境区分（避免配置翻倍）。
>
> **生产要点**：不同环境的 Nacos Server 必须是独立实例，不能共用同一个 Server 靠 Namespace 隔离。

### 7.2 分组（Group）

本项目统一使用 `DEFAULT_GROUP`，不按 Group 做业务或环境划分。

**理由**：
- 环境已通过 Namespace 隔离
- 业务通过服务名区分
- 减少 Group 管理成本

### 7.3 服务命名

统一格式：`mall-{模块名}`

| 服务名 | 模块 |
|--------|------|
| `mall-coupon` | 营销中心 |
| `mall-member` | 会员中心 |
| `mall-product` | 商品中心 |
| `mall-order` | 订单中心 |
| `mall-ware` | 库存中心 |
| `mall-gateway` | API 网关 |
| `mall-auth` | 认证服务 |
| `mall-search` | 搜索服务 |
| `mall-cart` | 购物车 |
| `mall-seckill` | 秒杀服务 |
| `mall-third` | 第三方服务 |
| `mall-admin` | 后台管理 |

### 7.4 元数据（Metadata）

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

### 7.5 集群（Cluster）

本项目开发阶段不使用 Cluster 配置，保持默认 `DEFAULT`。

**生产部署**：
- Nacos Server 至少 3 节点集群部署
- 服务实例的 Cluster 按机房/可用区划分，实现就近访问
- 配合 Spring Cloud LoadBalancer 的 `NacosRule` 实现同集群优先调用

### 7.6 开发阶段临时配置

以下配置仅用于当前开发阶段，**生产环境不需要**：

| 配置 | 原因 | 生产环境状态 |
|------|------|------------|
| `autoconfigure.exclude` (Seata/Redis) | 中间件未启动 | 移除，正常启用所有组件 |
| `resilience4j-spring-cloud2` 移除 | Bean 冲突 | 只保留 `spring-boot3` |
| `import-check.enabled: false` | 未启用配置中心 | 移除，启用配置中心 |
| `log-impl: StdOutImpl` | 开发调试用 | 生产使用日志框架，不输出到 stdout |

### 7.7 Nacos Server 部署模式

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
