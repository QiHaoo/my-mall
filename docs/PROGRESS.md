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
| 2026-06-20 | common 模块基础代码补充 | acd780f | R.java 统一响应封装、BaseEntity、MyMetaObjectHandler、MybatisPlusConfig、CodeGenerator 代码生成器 + MyBatis-Plus 代码生成规范文档 |

## 当前进行

| 事项 | 模块 | 状态说明 |
|------|------|---------|
| — | — | 暂无进行中的任务，待启动第一个服务开发 |

---

## 服务进度索引

启动某服务开发时，在 `docs/{服务名}/` 下创建 `PROGRESS.md`（从 `docs/_TEMPLATE-PROGRESS.md` 复制）。

| 服务 | 进度文档 | 状态 |
|------|---------|------|
| mall-product | `docs/mall-product/PROGRESS.md` | 未启动 |
| mall-member | — | 未启动 |
| mall-order | — | 未启动 |
| mall-ware | — | 未启动 |
| mall-cart | — | 未启动 |
| mall-coupon | — | 未启动 |
| mall-seckill | — | 未启动 |
| mall-search | — | 未启动 |
| mall-auth | — | 未启动 |
| mall-gateway | — | 未启动 |
| mall-third | — | 未启动 |
| mall-admin | — | 未启动 |
