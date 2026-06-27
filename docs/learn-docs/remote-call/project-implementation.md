# 远程调用 — 项目实施记录

> 当前状态：✅ 完成 Feign 基本调用链路搭建

## 已完成

### 依赖搭建

- `mall-common` 统一依赖 `spring-cloud-starter-openfeign` + `spring-cloud-starter-loadbalancer`
- mall-member 模块已引入并配置

### Feign 客户端：CouponFeignClient

```
位置：mall-member/src/main/java/com/mymall/member/feign/CouponFeignClient.java

    mall-member ──── Feign ────► mall-coupon
        │                           │
  CouponFeignClient.list()    CouponController.list()
        │                           │
  GET /coupon/coupon/list     return R
```

| 属性 | 值 |
|------|-----|
| 目标服务 | `mall-coupon`（Nacos 服务名） |
| 目标路径 | `/coupon/coupon/list` |
| 返回值 | `R`（统一响应体） |

### 测试

| 测试 | 方式 | 类 |
|------|------|-----|
| 单元测试 | @WebMvcTest + MockitoBean | `MemberControllerTest` |
| 集成测试 | @SpringBootTest + WireMock | `MemberControllerIT` |
| 手动联调 | 启动两个服务 + .http 文件 | `http/member-coupon-remote-call.http` |

## 待完成

- [ ] 压测 / 熔断降级配置（Sentinel 集成）
- [ ] 认证 Token 透传（Feign 拦截器）
- [ ] 超时与重试策略调整

## 相关文档

- [项目远程调用配置说明](../../microservice/feign-config.md)
- [Controller 接口编写规范](../../standards/controller-specification.md)
- [E2E 测试方案对比](../testing/e2e-testing-strategies.md)
