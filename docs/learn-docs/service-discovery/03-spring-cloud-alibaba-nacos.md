# Spring Cloud Alibaba Nacos Discovery 实战

## 1. 依赖配置

### 1.1 父 POM（版本管理）

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

### 1.2 子模块

```xml
<dependencies>
    <!-- Nacos 服务发现 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    
    <!-- LoadBalancer（必须，替代 Ribbon） -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
</dependencies>
```

## 2. 基础配置

### 2.1 application.yml

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
        group: DEFAULT_GROUP
        # 可选配置
        # cluster-name: hangzhou
        # ephemeral: true  # 临时实例（默认）
        # weight: 100
        # metadata:
        #   version: v1.0
```

### 2.2 核心配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server-addr` | Nacos 地址 | - |
| `namespace` | 命名空间 | public |
| `group` | 分组 | DEFAULT_GROUP |
| `cluster-name` | 集群名 | DEFAULT |
| `ephemeral` | 是否临时实例 | true |
| `weight` | 权重 | 100 |
| `username` | 用户名 | nacos |
| `password` | 密码 | nacos |

## 3. 启动类

```java
package com.mymall.coupon;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient  // 启用服务注册与发现
@MapperScan("com.mymall.coupon.mapper")
public class CouponApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponApplication.class, args);
    }
}
```

> **注意**：Spring Cloud 2020+ 版本中，`@EnableDiscoveryClient` 可省略，默认自动启用。

## 4. 服务调用

### 4.1 RestTemplate + LoadBalancer

```java
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced  // 启用负载均衡
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

@Service
public class ProductService {

    @Autowired
    private RestTemplate restTemplate;

    public Product getProduct(Long id) {
        // 服务名替代 IP:Port
        String url = "http://mall-product/product/" + id;
        return restTemplate.getForObject(url, Product.class);
    }
}
```

### 4.2 OpenFeign（推荐）

```java
// 1. 启动类添加 @EnableFeignClients
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class CouponApplication {
    // ...
}

// 2. 定义 Feign 接口
@FeignClient(name = "mall-product", path = "/product")
public interface ProductFeignClient {

    @GetMapping("/{id}")
    Product getProduct(@PathVariable("id") Long id);

    @GetMapping("/list")
    List<Product> listProducts();
}

// 3. 注入使用
@Service
public class CouponService {

    @Autowired
    private ProductFeignClient productFeignClient;

    public void someMethod() {
        Product product = productFeignClient.getProduct(1L);
    }
}
```

## 5. DiscoveryClient 高级用法

```java
@Service
public class DiscoveryService {

    @Autowired
    private DiscoveryClient discoveryClient;

    // 获取所有服务名
    public List<String> getAllServices() {
        return discoveryClient.getServices();
    }

    // 获取指定服务的所有实例
    public List<ServiceInstance> getInstances(String serviceId) {
        return discoveryClient.getInstances(serviceId);
    }

    // 手动选择实例
    public ServiceInstance chooseInstance(String serviceId) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
        // 自定义选择逻辑
        return instances.get(0);
    }
}
```

## 6. 监听服务变化

```java
@Component
public class ServiceChangeListener {

    @Autowired
    private NacosServiceManager nacosServiceManager;

    @PostConstruct
    public void subscribe() throws NacosException {
        NamingService namingService = nacosServiceManager
                .getNamingService(new Properties());

        namingService.subscribe("mall-product", event -> {
            if (event instanceof NamingEvent) {
                NamingEvent namingEvent = (NamingEvent) event;
                List<Instance> instances = namingEvent.getInstances();
                System.out.println("服务实例变化: " + instances.size());
            }
        });
    }
}
```

## 7. 多环境配置

### 7.1 命名空间隔离

```yaml
# 开发环境
spring:
  cloud:
    nacos:
      discovery:
        namespace: dev  # 或 dev 的 UUID

---
# 生产环境
spring:
  cloud:
    nacos:
      discovery:
        namespace: prod
```

### 7.2 Profile 切换

```yaml
# application.yml
spring:
  profiles:
    active: dev

---
# application-dev.yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: dev

---
# application-prod.yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos.prod.svc.cluster.local:8848
        namespace: prod
```

## 8. 元数据路由

### 8.1 设置元数据

```yaml
spring:
  cloud:
    nacos:
      discovery:
        metadata:
          version: v2
          env: gray
```

### 8.2 基于元数据路由

```java
@Configuration
public class GrayLoadBalancerConfig {

    @Bean
    public ReactorLoadBalancer<ServiceInstance> grayLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory loadBalancerClientFactory) {

        return new GrayLoadBalancer(
                loadBalancerClientFactory.getLazyProvider(
                        environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME),
                        ServiceInstanceListSupplier.class),
                environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME));
    }
}

public class GrayLoadBalancer implements ReactorLoadBalancer<ServiceInstance> {
    // 实现基于 metadata 的路由逻辑
    // 优先选择 metadata.version = "v2" 的实例
}
```

## 9. 与 Nacos Config 结合

```xml
<!-- Nacos 配置中心 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

```yaml
# bootstrap.yml（必须在 Nacos 配置生效前加载）
spring:
  application:
    name: mall-coupon
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        namespace: my-mall
        group: DEFAULT_GROUP
        file-extension: yaml
```

## 10. 监控与日志

```yaml
logging:
  level:
    com.alibaba.nacos: debug  # Nacos 客户端日志

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

## 11. 下一步

- [最佳实践与常见问题](./04-best-practices.md)
- [本项目实施记录](./project-implementation.md)
