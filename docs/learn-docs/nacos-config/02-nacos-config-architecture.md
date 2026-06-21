# Nacos 配置架构

## 1. Nacos Config 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      Nacos Server                            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    Config Service                      │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐  │  │
│  │  │ Config Dump  │  │ Config Disk │  │ Config Notify│  │  │
│  │  └─────────────┘  └─────────────┘  └──────────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    Storage Layer                       │  │
│  │         MySQL (持久化) / Derby (嵌入式)                  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 2. 数据模型

```
Namespace
    └── Group
        └── Data ID (配置文件)
            └── Content (配置内容)
```

### 2.1 Namespace

物理隔离单位：

```yaml
spring:
  cloud:
    nacos:
      config:
        namespace: my-mall  # 或 UUID
```

**用途**：
- 环境隔离：dev / test / prod
- 租户隔离：多租户场景

### 2.2 Group

逻辑分组：

```yaml
spring:
  cloud:
    nacos:
      config:
        group: DEFAULT_GROUP
```

**常用分组**：
- `DEFAULT_GROUP`：默认
- `DEV_GROUP`：开发环境
- `PROD_GROUP`：生产环境

### 2.3 Data ID

配置文件标识：

```
mall-coupon.yaml
mall-coupon-dev.yaml
application-common.yaml
```

**命名规范**：
- `{服务名}.{扩展名}`
- `{服务名}-{profile}.{扩展名}`

## 3. 配置推送机制

### 3.1 长轮询

```
Client                          Server
  │                               │
  │──── Pull Request ────────────►│
  │     (带 MD5 校验值)            │
  │                               │
  │◄──── Hold (30s) ───────────── │  (配置未变更，挂起请求)
  │                               │
  │                               │◄── 管理员修改配置
  │                               │
  │◄──── Response (新配置) ───────│  (配置变更，立即返回)
  │                               │
```

**特点**：
- 实时性：配置变更秒级推送
- 低压力：减少无效请求
- 容错：断线自动重连

### 3.2 本地缓存

```yaml
# Client 会缓存配置到本地文件
${user.home}/nacos/config/
├── {namespace}/
│   └── {group}/
│       └── {dataId}
```

**作用**：
- Server 不可用时使用本地缓存
- 启动加速

## 4. 配置存储

### 4.1 MySQL 模式（推荐）

```sql
-- Nacos 配置表
CREATE TABLE config_info (
    id BIGINT NOT NULL AUTO_INCREMENT,
    data_id VARCHAR(255),
    group_id VARCHAR(128),
    content LONGTEXT,
    md5 VARCHAR(32),
    ...
);
```

**配置**：
```properties
# application.properties
spring.datasource.platform=mysql
db.num=1
db.url.0=jdbc:mysql://localhost:3306/nacos_config?...
```

### 4.2 Derby 模式（嵌入式）

- 无需外部数据库
- 适合单机/测试环境
- 数据存储在 `${nacos.home}/data`

## 5. 配置版本管理

### 5.1 历史版本

每次配置变更都会记录历史：

```
版本1: coupon.discount = 0.9
版本2: coupon.discount = 0.85
版本3: coupon.discount = 0.8
```

### 5.2 配置回滚

Nacos 控制台支持一键回滚到历史版本。

### 5.3 配置对比

可以对比不同版本的配置差异。

## 6. 灰度发布

### 6.1 Beta 发布

按 IP 灰度：

```
指定 IP 列表：
- 192.168.1.10
- 192.168.1.11

只有这些 IP 的服务实例会收到新配置
```

### 6.2 标签发布

按标签灰度：

```yaml
# 服务配置标签
spring:
  cloud:
    nacos:
      config:
        metadata:
          version: gray
```

只有 `version=gray` 的实例收到灰度配置。

## 7. 配置监听

### 7.1 API 方式

```java
ConfigService configService = NacosFactory.createConfigService(properties);

configService.addListener("mall-coupon.yaml", "DEFAULT_GROUP", new Listener() {
    @Override
    public void receiveConfigInfo(String configInfo) {
        System.out.println("配置变更: " + configInfo);
    }
});
```

### 7.2 Spring Cloud 方式

使用 `@RefreshScope` 自动监听：

```java
@RefreshScope
@Component
public class MyConfig {
    @Value("${my.config}")
    private String config;  // 自动更新
}
```

## 8. 配置加密

Nacos 支持配置加密（需要开启）：

```yaml
# 加密配置
encrypt:
  key: your-secret-key
```

```yaml
# 使用加密值
database:
  password: ENC(加密后的密文)
```

## 9. 下一步

- [Spring Cloud 集成](./03-spring-cloud-nacos-config.md)
