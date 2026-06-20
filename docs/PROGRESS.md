# 项目开发进度

---

## 已完成

### 基础设施

| 事项 | 状态 | 说明 |
|------|------|------|
| 技术选型与架构设计 | ✅ | Spring Boot 3.4 + Spring Cloud 2024.0 + Java 21 |
| Git 仓库初始化 | ✅ | GitHub remote: `git@github.com:QiHaoo/my-mall.git`，分支 `main` |
| .gitignore | ✅ | Java/Maven/IDE（IDEA/VS Code/Eclipse/NetBeans）/OS/.workbuddy |
| AGENTS.md | ✅ | 技术选型表 + 架构图 + 服务划分 + 文档索引 |
| Maven 多模块骨架 | ✅ | 父 POM 集中版本管理 + 13 个子模块 pom.xml + 标准目录结构 |
| Docker Compose | ✅ | profiles 分组：core / mq / search / storage / monitor |
| 本地开发环境 | ✅ | WSL2 + Docker Engine + 镜像加速，各中间件可访问 |
| Git 管理规范 | ✅ | 分支策略 + Commit 规范 + 发布流程 |
| 数据库初始化 | ✅ | 5 个库已创建（mymall_pms / oms / ums / wms / sms），表结构已通过 `init/mysql/*.sql` 导入 |

### mall-common 公共模块

| 事项 | 状态 | 说明 |
|------|------|------|
| BaseEntity | ✅ | 雪花 ID + createTime/updateTime 自动填充 |
| MyMetaObjectHandler | ✅ | insert/update 时间字段自动填充 |
| MybatisPlusConfig | ✅ | 分页插件 + 乐观锁插件 |
| R 统一响应体 | ✅ | ok() / error() 静态方法 |
| CodeGenerator 代码生成器 | ✅ | 5 个模块预配置（@Test 方法），右键运行即可生成，不继承 BaseEntity |

### 各服务模块

| 模块 | 骨架 | 代码生成 | 业务开发 |
|------|------|---------|----------|
| mall-product（商品） | ✅ | ✅ 15 张表 | — |
| mall-order（订单） | ✅ | ✅ 8 张表 | — |
| mall-member（会员） | ✅ | ✅ 9 张表 | — |
| mall-ware（库存） | ✅ | ✅ 6 张表 | — |
| mall-coupon（营销） | ✅ | ✅ 15 张表 | — |
| mall-seckill（秒杀） | ✅ | — | — |
| mall-auth（认证） | ✅ | — | — |
| mall-gateway（网关） | ✅ | — | — |
| mall-search（搜索） | ✅ | — | — |
| mall-cart（购物车） | ✅ | — | — |
| mall-third（第三方） | ✅ | — | — |
| mall-admin（后台） | ✅ | — | — |

---

## 下一步

1. **编写各模块启动类** — `@SpringBootApplication` + `@MapperScan` + `application.yml`
2. **手写 Controller** — 按规范手写 CRUD + 分页查询接口
3. **启动验证** — Nacos + MySQL + Redis 容器启动后，验证服务注册和接口调用

---

## 文档索引

| 文档 | 说明 |
|------|------|
| `docs/tech-stack-and-architecture-2026.md` | 技术选型与架构设计 |
| `docs/git-workflow.md` | Git 管理规范 |
| `docs/local-dev-reference.md` | 本地开发环境手册 |
| `docs/mybatis-plus-codegen-guide.md` | MyBatis-Plus 代码生成规范 |
| `dev-environment-setup.md` | 开发环境搭建指南（WSL2 + Docker） |
