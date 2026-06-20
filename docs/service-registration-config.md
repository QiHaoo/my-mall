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
