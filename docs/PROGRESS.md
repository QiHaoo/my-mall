# 项目总进度

> 本文档只记录**已完成**和**当前进行**的事项，不列未来计划。
> 各服务的详细进度见 `docs/{服务名}/PROGRESS.md`。
>
> **提交关联规则**：完成一个事项后，将过程中的多个小提交合并（squash）为一条提交并推送到远程，再在此记录关联的提交 hash。过程中未被合并推送的小提交不在此记录。

---

## 已完成

| 时间 | 事项 | 关联提交 | 说明 |
|------|------|---------|------|
| 2026-06-19 | 架构分析与技术选型 | — | 确定 2026 技术栈方案，输出 `docs/tech-stack-and-architecture-2026.md` |
| 2026-06-19 | 开发环境搭建 | — | WSL2 + Docker Engine + 镜像加速器配置，输出 `dev-environment-setup.md` |
| 2026-06-19 | 中间件编排 | — | `docker-compose.yml`（profiles 分组，按阶段渐进启动） |
| 2026-06-20 | 项目 Maven 骨架搭建 | — | 父 pom + 12 个服务模块 + mall-common 公共模块 |
| 2026-06-20 | 文档体系建立 | 21f2a20 | `AGENTS.md` + `docs/` 目录结构 + 进度文档规范 + Git 工作流 |
| 2026-06-20 | common 模块基础代码补充 | acd780f | R.java、BaseEntity、MyMetaObjectHandler、MybatisPlusConfig、CodeGenerator + MyBatis-Plus 代码生成规范文档 |
| 2026-06-20 | 5 个业务模块代码生成 | d84b5df | product/order/member/ware/coupon 模块 Entity/Mapper/Service 生成，SQL 初始化脚本，CodeGenerator 模块化改造 |
| 2026-06-20 | AI 辅助开发流程规范制定 | 20559b0 | `docs/development-workflow.md`（轻量 Spec + TDD，三阶段流程、核心/样板判定、配套 skill） |
| 2026-06-20 | coupon/member 服务骨架搭建 | 9575ead | CouponApplication、MemberApplication 启动类 + application.yml（Nacos 注册配置）+ `docs/service-registration-config.md` 服务注册配置说明文档 |
| 2026-06-20 | 依赖冲突修复 | d2b577a | 移除 member/coupon 模块中冲突的 resilience4j-spring-cloud2 依赖 |
| 2026-06-20 | MyBatis-Plus ORM 实践笔记 | 7bb7a8a | `docs/mybatis-plus-orm-notes.md`（依赖、配置、BaseEntity、MyMetaObjectHandler、使用约定） |
| 2026-06-20 | Feign 远程调用 + R 链式 put + 规范文档 | `1b97988` `1f72204` `a51a714` | R.put() 链式方法、CouponController/MemberController Feign 示例、@MapperScan 独立配置、@WebMvcTest + WireMock 双测、Controller/Feign/测试规范文档、远程调用学习笔记 |

---

## 当前进行

| 事项 | 模块 | 状态说明 |
|------|------|---------|
| 服务注册验证 | mall-coupon / mall-member | Feign 调用链路已搭建，待启动两个服务验证 Nacos 注册 + 远程调用 |

---

## 服务进度索引

启动某服务开发时，在 `docs/{服务名}/` 下创建 `PROGRESS.md`（从 `docs/_TEMPLATE-PROGRESS.md` 复制）。

| 服务 | 进度文档 | 状态 |
|------|---------|------|
| mall-product | `docs/mall-product/PROGRESS.md` | 骨架 ✅ / 代码生成 ✅ / 服务注册待验证 |
| mall-member | `docs/mall-member/PROGRESS.md` | 骨架 ✅ / 代码生成 ✅ / Feign Consumer ✅ / 双测 ✅ |
| mall-order | `docs/mall-order/PROGRESS.md` | 骨架 ✅ / 代码生成 ✅ |
| mall-ware | `docs/mall-ware/PROGRESS.md` | 骨架 ✅ / 代码生成 ✅ |
| mall-coupon | `docs/mall-coupon/PROGRESS.md` | 骨架 ✅ / 代码生成 ✅ / Feign Provider ✅ |
| mall-seckill | — | 骨架 ✅ |
| mall-auth | — | 骨架 ✅ |
| mall-gateway | — | 骨架 ✅ |
| mall-search | — | 骨架 ✅ |
| mall-cart | — | 骨架 ✅ |
| mall-third | — | 骨架 ✅ |
| mall-admin | — | 骨架 ✅ |
