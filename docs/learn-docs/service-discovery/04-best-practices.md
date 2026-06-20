# 最佳实践与常见问题

## 1. 生产环境最佳实践

### 1.1 注册中心高可用

```yaml
# 多节点配置
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos-1:8848,nacos-2:8848,nacos-3:8848
```

### 1.2 命名空间规划

| 环境 | Namespace | 说明 |
|------|-----------|------|
| 开发 | `dev` 或 UUID | 本地开发 |
| 测试 | `test` | 集成测试 |
| 预发 | `staging` | 预发布验证 |
| 生产 | `prod` | 正式环境 |

### 1.3 健康检查配置

```yaml
# 调整心跳参数（生产环境可适当放宽）
spring:
  cloud:
    nacos:
      discovery:
        # 临时实例（默认）
        ephemeral: true
        # 或使用持久实例
        # ephemeral: false
```

### 1.4 元数据规范

```yaml
spring:
  cloud:
    nacos:
      discovery:
        metadata:
          version: v1.0.0      # 语义化版本
          build-time: 20260620 # 构建时间
          git-commit: abc123   # Git 提交哈希
          team: backend        # 负责团队
```

### 1.5 优雅下线

```java
@Component
public class GracefulShutdown {

    @Autowired
    private NacosRegistration registration;

    @PreDestroy
    public void deregister() {
        // 先下线注册
        registration.setRegisterEnabled(false);
        // 等待流量排空
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

## 2. 开发环境最佳实践

### 2.1 本地开发配置

```yaml
# application-local.yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: dev
        # 本地开发可设置特定 IP，方便调试
        ip: 192.168.1.100
        # 或禁用注册，只消费不提供服务
        # register-enabled: false
```

### 2.2 多实例开发

```yaml
# 同一服务启动多个实例时，指定不同端口
server:
  port: ${SERVER_PORT:7000}
```

启动命令：
```bash
java -jar app.jar --server.port=7000
java -jar app.jar --server.port=7001
```

## 3. 常见问题排查

### 3.1 服务注册失败

**现象**：服务启动后 Nacos 控制台看不到实例

**排查步骤**：

```bash
# 1. 检查网络连通性
curl http://127.0.0.1:8848/nacos/

# 2. 检查日志
tail -f logs/application.log | grep -i nacos

# 3. 检查配置
# - server-addr 是否正确
# - namespace 是否存在
# - 用户名密码是否正确
```

**常见原因**：
- Nacos Server 未启动
- 防火墙/安全组未开放 8848 端口
- namespace 不存在（需要先创建）
- 网络不通

### 3.2 服务发现失败

**现象**：Consumer 调用时报 `No instances available`

**排查步骤**：

```bash
# 1. 确认 Provider 已注册
# Nacos 控制台 → 服务列表 → 查看实例

# 2. 确认 namespace 一致
# Consumer 和 Provider 必须同一 namespace

# 3. 确认 group 一致
# 默认都是 DEFAULT_GROUP

# 4. 检查服务名拼写
# spring.application.name 必须一致
```

### 3.3 心跳超时

**现象**：实例频繁标记为不健康

**原因与解决**：

```yaml
# 可能是网络延迟或 GC 导致心跳丢失
# 1. 检查 JVM GC 日志
jstat -gcutil <pid> 1000

# 2. 调整超时参数（谨慎）
# Nacos Server 端配置
# 默认: 15s 不健康, 30s 剔除
```

### 3.4 权重不生效

**原因**：LoadBalancer 默认使用轮询，不支持权重

**解决**：使用 Nacos Ribbon 或自定义 LoadBalancer

```java
// 自定义权重负载均衡
@Bean
public ReactorLoadBalancer<ServiceInstance> nacosLoadBalancer(
        Environment environment,
        LoadBalancerClientFactory factory) {
    String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
    return new NacosLoadBalancer(
            factory.getLazyProvider(name, ServiceInstanceListSupplier.class),
            name);
}
```

### 3.5 IP 注册错误

**现象**：注册了内网 IP 或错误 IP

**解决**：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        # 强制指定 IP
        ip: 192.168.1.100
        # 或指定网卡
        # network-interface: eth0
        # 或忽略某些网卡
        # preferred-networks: 192.168.1
```

## 4. 性能优化

### 4.1 减少心跳频率

```yaml
# 仅适用于特殊场景，一般不建议修改
# Nacos Server 端配置
nacos.naming.heartbeat.interval=5000  # 默认 5s
```

### 4.2 本地缓存

```yaml
spring:
  cloud:
    nacos:
      discovery:
        # 启用本地缓存，减少对 Nacos Server 的压力
        cache:
          enabled: true
          expire-seconds: 600
```

### 4.3 批量订阅

```java
// 避免频繁订阅/取消订阅
@PostConstruct
public void init() {
    // 一次性订阅所有需要的服务
    List<String> services = Arrays.asList("mall-product", "mall-order", "mall-member");
    services.forEach(this::subscribe);
}
```

## 5. 安全配置

### 5.1 开启鉴权

```properties
# Nacos Server application.properties
nacos.core.auth.enabled=true
nacos.core.auth.system.type=nacos
nacos.core.auth.plugin.nacos.token.secret.key=YourSecretKey
nacos.core.auth.server.identity.key=YourIdentityKey
nacos.core.auth.server.identity.value=YourIdentityValue
```

```yaml
# Client 配置
spring:
  cloud:
    nacos:
      discovery:
        username: nacos
        password: your-password
```

### 5.2 TLS 加密

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos-server:8848
        secure: true  # 启用 HTTPS
```

## 6. 监控告警

### 6.1 Prometheus 指标

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

### 6.2 告警规则

```yaml
# Prometheus Alert Rule
groups:
  - name: nacos-alerts
    rules:
      - alert: NacosServiceDown
        expr: nacos_service_up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Nacos service {{ $labels.service }} is down"
```

## 7. 版本兼容性

| Spring Boot | Spring Cloud | Spring Cloud Alibaba |
|-------------|--------------|---------------------|
| 3.4.x | 2024.0.x | 2023.0.3.x |
| 3.2.x | 2023.0.x | 2023.0.1.x |
| 3.0.x | 2022.0.x | 2022.0.0.0 |
| 2.7.x | 2021.0.x | 2021.0.6.0 |

## 8. 下一步

- [本项目实施记录](./project-implementation.md)
