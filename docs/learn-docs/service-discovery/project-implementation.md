# 本项目实施记录

> 记录 my-mall 项目中服务注册与发现相关的配置和实施情况

## 1. 技术选型

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Cloud Alibaba | 2023.0.1.2 | Nacos Discovery |
| Nacos Server | 2.4.x | 服务注册中心 |
| Spring Cloud LoadBalancer | - | 客户端负载均衡 |

## 2. 基础设施

### 2.1 Nacos 部署

```bash
# Docker Compose 启动
docker compose up -d nacos

# 访问控制台
http://127.0.0.1:8848/nacos
# 默认账号: nacos / nacos
```

### 2.2 命名空间配置

| Namespace | 说明 |
|-----------|------|
| `my-mall` | 项目统一命名空间 |

> 需要在 Nacos 控制台手动创建 namespace

## 3. 已完成的服务注册

### 3.1 mall-coupon（营销中心）

**状态**：✅ 已完成

| 配置项 | 值 |
|--------|-----|
| 服务名 | `mall-coupon` |
| 端口 | `7000` |
| 数据库 | `mymall_sms` |
| Namespace | `my-mall` |

**文件清单**：

```
mall-coupon/
├── src/main/java/com/mymall/coupon/
│   └── CouponApplication.java          # 启动类
└── src/main/resources/
    └── application.yml                 # 配置文件
```

**application.yml 核心配置**：

```yaml
server:
  port: 7000

spring:
  application:
    name: mall-coupon
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: my-mall
```

**启动类**：

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

### 3.2 mall-member（会员中心）

**状态**：✅ 已完成

| 配置项 | 值 |
|--------|-----|
| 服务名 | `mall-member` |
| 端口 | `7100` |
| 数据库 | `mymall_ums` |
| Namespace | `my-mall` |

**文件清单**：

```
mall-member/
├── src/main/java/com/mymall/member/
│   └── MemberApplication.java          # 启动类
└── src/main/resources/
    └── application.yml                 # 配置文件
```

**application.yml 核心配置**：

```yaml
server:
  port: 7100

spring:
  application:
    name: mall-member
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: my-mall
```

## 4. 待完成的服务注册

| 模块 | 服务名 | 计划端口 | 状态 |
|------|--------|---------|------|
| mall-product | `mall-product` | 7200 | ⏳ 待配置 |
| mall-order | `mall-order` | 7300 | ⏳ 待配置 |
| mall-ware | `mall-ware` | 7400 | ⏳ 待配置 |
| mall-seckill | `mall-seckill` | 7500 | ⏳ 待配置 |
| mall-auth | `mall-auth` | 7600 | ⏳ 待配置 |
| mall-gateway | `mall-gateway` | 88 | ⏳ 待配置 |
| mall-search | `mall-search` | 7700 | ⏳ 待配置 |
| mall-cart | `mall-cart` | 7800 | ⏳ 待配置 |
| mall-third | `mall-third` | 7900 | ⏳ 待配置 |
| mall-admin | `mall-admin` | 8080 | ⏳ 待配置 |

## 5. 端口规划

```
┌─────────────────────────────────────────┐
│            网关层                        │
│  mall-gateway: 88                       │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│            业务服务层                     │
│  mall-coupon:  7000  (营销)             │
│  mall-member:  7100  (会员)             │
│  mall-product: 7200  (商品)             │
│  mall-order:   7300  (订单)             │
│  mall-ware:    7400  (库存)             │
│  mall-seckill: 7500  (秒杀)             │
│  mall-auth:    7600  (认证)             │
│  mall-search:  7700  (搜索)             │
│  mall-cart:    7800  (购物车)           │
│  mall-third:   7900  (第三方)           │
│  mall-admin:   8080  (后台管理)         │
└─────────────────────────────────────────┘
```

## 6. 依赖配置

### 6.1 父 POM（版本管理）

```xml
<properties>
    <spring-cloud-alibaba.version>2023.0.1.2</spring-cloud-alibaba.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 6.2 子模块通用依赖

```xml
<dependencies>
    <!-- mall-common（包含 Lombok、MyBatis-Plus 等） -->
    <dependency>
        <groupId>com.mall</groupId>
        <artifactId>mall-common</artifactId>
    </dependency>

    <!-- Nacos 服务发现 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>

    <!-- LoadBalancer -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
</dependencies>
```

## 7. 验证步骤

### 7.1 启动服务

```bash
# 1. 启动 Nacos
docker compose up -d nacos

# 2. 等待 Nacos 就绪（约 10-30s）
curl http://127.0.0.1:8848/nacos/

# 3. 启动业务服务（IDEA 或命令行）
# IDEA: 右键 CouponApplication → Run
# 或: mvn spring-boot:run -pl mall-coupon
```

### 7.2 查看注册结果

1. 打开 Nacos 控制台：http://127.0.0.1:8848/nacos
2. 选择命名空间：`my-mall`
3. 点击「服务列表」
4. 确认服务已注册：
   - `mall-coupon` ✅
   - `mall-member` ✅

### 7.3 测试服务调用

```bash
# 健康检查端点
curl http://127.0.0.1:7000/actuator/health

# 服务信息
curl http://127.0.0.1:7000/actuator/info
```

## 8. 后续规划

### 8.1 短期

- [ ] 完成 mall-product 服务注册
- [ ] 完成 mall-order 服务注册
- [ ] 完成 mall-ware 服务注册
- [ ] 配置 OpenFeign 服务间调用

### 8.2 中期

- [ ] 添加 Nacos Config 配置中心
- [ ] 实现基于元数据的灰度路由
- [ ] 配置服务权重与流量控制

### 8.3 长期

- [ ] Nacos 集群部署（生产环境）
- [ ] 接入 Prometheus + Grafana 监控
- [ ] 配置告警规则

## 9. 相关文件索引

| 文件 | 说明 |
|------|------|
| `mall-coupon/pom.xml` | 营销中心依赖配置 |
| `mall-coupon/src/main/java/.../CouponApplication.java` | 启动类 |
| `mall-coupon/src/main/resources/application.yml` | 应用配置 |
| `mall-member/pom.xml` | 会员中心依赖配置 |
| `mall-member/src/main/java/.../MemberApplication.java` | 启动类 |
| `mall-member/src/main/resources/application.yml` | 应用配置 |
| `docs/learn-docs/service-discovery/` | 服务发现学习文档 |

## 10. 更新日志

| 日期 | 内容 |
|------|------|
| 2026-06-20 | 完成 mall-coupon 服务注册配置 |
| 2026-06-20 | 完成 mall-member 服务注册配置 |
| 2026-06-20 | 创建服务注册与发现学习文档 |
