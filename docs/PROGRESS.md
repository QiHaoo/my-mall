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
| 2026-06-21 | Nacos 配置中心集成 + 文档补充 | `61e0414` `8cbbaa4` | coupon 模块集成 Nacos Config、ConfigDemoController 演示接口（8个演示场景）、配置规范文档、使用指南、学习笔记 |
| 2026-06-21 | 配置中心文档优化 + ConfigDemoController 修复 | `c2685cd` | service-registration-config.md 重新组织章节、Spring Cloud Nacos Config 集成细节补充、@Qualifier 解决多 ContextRefresher Bean 冲突、HTTP 测试文件 |
| 2026-06-21 | 编码 & 测试规范文档 | `681bc60` | controller-specification.md 生产级增强（校验分组、分页、幂等性）、coding-standards.md 新建（8章节）、testing-specification.md 新建（10章节） |
| 2026-06-21 | common 模块缺失项分析 | `b295e14` | `docs/common-module-gap-analysis.md`（P0/P1/P2/P3 四档，共 17 项缺失） |
| 2026-06-21 | 网关模块搭建 + 文档 | `12445db` | mall-gateway 模块骨架、Spring Cloud Gateway 配置、gateway-config-guide.md、api-gateway 学习笔记 |
| 2026-06-21 | common 模块 P0+P1 补齐 | `1621db2` | 修复包扫描 Bug（AutoConfiguration.imports + @ComponentScan）、BizException + ResultCode + GlobalExceptionHandler、PageQuery、BaseEntity @TableLogic、SpringDocConfig、hutool 版本统一 |
| 2026-06-22 | 商品分类 CRUD 实现 | `699f1b7` | product 模块：CategoryController/Service（树查询、新增、修改、批量删除、拖拽排序）、DTO/VO、@WebMvcTest 单元测试、category-management.md 文档 |
| 2026-06-23 | OSS 对象存储服务实现 | `1cc33c4` | mall-oss 独立服务 + mall-common-oss SDK（MinIO Presigned URL 签发、文件元数据管理、前端直传）、object-storage-design.md 设计文档、oss-demo.http 测试文件、mall_oss.sql 初始化脚本 |
| 2026-06-23 | OSS 安全闭环增强（v1.1） | `7adcace` | Content-Type 回调校验、回调幂等、上传者身份透传（UserContext + UserContextFilter + uploader_id）、删除越权校验、超时 PENDING 定时清理、publicBaseUrl/region 配置（fileUrl 走公网域名、迁移 S3 必填 region）、MinIO 调用移出事务、设计文档同步修订与精简 |
| 2026-06-24 | common 模块生产级闭环（v1.2） | `b60665e` | 统一 HTTP 200+R.code 策略（对齐规范文档）、GlobalExceptionHandler 补全 ConstraintViolation/Bind/HttpMessageNotReadable/MaxUploadSize 等处理器、ResultCode.SUCCESS 统一为 200、BaseEntity 增 @Version+审计字段 createBy/updateBy、MyMetaObjectHandler 补全填充、新增 JacksonConfig（Long→String 防 JS 精度丢失+LocalDateTime 格式化）、MybatisPlusConfig 分页 maxLimit+全局配置显式化、SpringDoc 文案修正、代码生成器依赖移出 runtime |
| 2026-06-24 | 表设计规范 + init SQL 改造 + 实体对齐 | `3b41038` | 新增 table-design-specification.md；pms/sms/ums/wms/oms 53 表生产级改造（PK 统一 id 雪花/去 AUTO_INCREMENT/补审计列/utf8mb4/类型修正/索引）；53 实体统一继承 BaseEntity + Mapper XML 同步；Category 链路 catId→id、逻辑删除改 @TableLogic、showStatus Byte→Integer；修复 Category Service 测试 MP 版本脱节（selectBatchIds→selectByIds） |
| 2026-06-24 | common 模块设计文档 + 规范对齐 | `9eb974d` | 新增 docs/common/common-module-design.md；coding-standards §4 与实现对齐（BizException 签名/异常处理器清单/错误码段/HTTP 200 策略）；gap-analysis 归档至 docs/other/；CLAUDE.md 文档索引更新 |
| 2026-06-24 | 品牌管理实现 | `e53fe25` | product 模块：BrandController/Service（分页查询/详情/新增/修改/状态更新/删除/分类下品牌）+ DTO/VO；mall-common 新增校验分组 Create/Update + 品牌错误码 53001-53005；Brand.showStatus Byte→Integer；product 补 MyBatisConfig(@MapperScan)；BrandServiceTest + BrandControllerTest 全绿（mvn test 58 通过）；brand-management.md 设计文档 + product-brand-demo.http；CLAUDE.md 补 JDK21 路径约定 |
| 2026-06-24 | 文档站搭建 + 文档分层规范 | `ce62329` | MkDocs Material 文档站（mkdocs.yml + GitHub Actions 自动部署到 Pages + serve-docs.bat 本地启动脚本）+ docs/docs-site-deployment.md 部署指南 + docs/documentation-layering-guide.md 三层文档分层规范（全局设计+规范 / 模块设计 / 学习文档）+ docs/index.md 首页 + 修复 2 处跨文档死链；SPU/SKU 属性设计文档（product/spu-sku-attr-design.md）+ 接口设计补充。部署过程修复 workflow setup-python 失败（关 pip cache）、strict 构建死链（docs/ 外文件改 GitHub 绝对链接），站点已上线 https://qihao0o.github.io/my-mall/ |
| 2026-06-21 | 网关路由配置修复 | `5bb4e9a` | RequestLogFilter + application.yml 路由过滤器修正 |
| 2026-06-27 | GitHub 学习文档 + learn-docs 组织原则 | — | 新增 `docs/learn-docs/github/` 12 篇学习文档（基础/协作工作流/PR/分支保护/Actions/Pages/Release/Issue/Secrets/CLI/项目实践串联，含 mkdocs.yml workflow 逐行解读）；AGENTS.md 补充 learn-docs 文档组织原则（按实际内容组织，不套用开发文档模式）；mkdocs.yml 导航新增 GitHub 子项；修复 strict 构建链接警告（外部文件改 GitHub 绝对 URL、修正相对路径） |
| 2026-06-28 | AI Agent 操作 Figma 方案落地 | — | figma-console-mcp 集成（MCP 服务器 + Desktop Bridge 插件 + Trae 配置），验证 WebSocket 连接并生成测试设计稿（仪表盘画板）；新增 `docs/frontend/figma-mcp-guide.md`（选型/配置/流程/核心工具速查）；更新 design-system.md 第七章链接、doc-convention.md 1.4.1 过时规则（AI 现可生成 Figma）、AGENTS.md 文档索引 |
| 2026-06-28 | 分类与品牌管理接口实现同步 v1.1 | `b67f0ee` | 按最新设计文档同步分类/品牌后端实现：分类字段统一 catId、show_status 业务删除、SPU 引用检查与关联表冗余名同步；品牌表单剥离分类关联、新增批量删除与独立关联管理接口（查询/新增/移除）；补充 ResultCode 53006-53008；Service/Controller 单元测试全绿（mall-product 28 个用例通过） |
| 2026-06-28 | 管理后台前端搭建 + 分类/品牌管理页面 | — | mall-admin-frontend：Vite + Vue 3 + TS + Element Plus + Pinia + Axios 脚手架；基础设施（SCSS 样式系统、API 请求层 R\<T\> 剥离、路由系统、AdminLayout 布局、PageTable/FormDialog 通用组件、useTable/useDialog composables、树形工具函数）；分类管理（三级树 CRUD + 拖拽排序 + 批量删除）；品牌管理（分页 CRUD + 显示状态切换 + 关联分类管理）；404 页面；mall-gateway CORS 已允许 5173~5176 开发端口 |
| 2026-06-29 | 前端联调验证与 PageTable 刷新修复 | — | 分类/品牌管理页面对接后端端到端验证通过；修复品牌新增/删除后 PageTable 列表未自动刷新的问题 |
| 2026-06-29 | 前端文档体系补充与学习文档 | — | 修正 overview.md 与实现对齐；新增 infrastructure.md（基础设施设计）、components.md（通用组件设计）、coding-standards.md（编码规范）；调整 design-system.md 组件章节职责；功能域文档章节顺序调整为「需求→前端设计→后端实现」（doc-convention.md 规范 + category/brand-management.md 同步）；新增 learn-docs/frontend/ 10 篇学习文档（Vue3/路由/Pinia/TS/Element Plus/Vite/基础设施/组件/API层/功能页面） |
| 2026-06-29 | 测试规范增强 + 测试学习文档 | — | testing-specification.md 生产级增强（覆盖率门禁 §9、CI 集成 §10、前端测试体系 §11、待补充行动清单 §14）；新增 learn-docs/testing/ 5 篇学习文档（测试金字塔、单元测试、切片测试、集成测试、学习地图） |
| 2026-06-29 | CI/CD 持续集成方案设计 + 学习文档 | — | 新增 docs/standards/ci-cd/ 6 篇设计文档（overview/backend-ci/frontend-ci/docker-publish/harbor-setup/release-and-protection），覆盖可复用工作流组合架构、后端/前端 CI、多阶段 Dockerfile + Harbor 集成、Release 发布 + 分支保护；新增 docs/learn-docs/ci/ 7 篇学习文档（Docker 基础→CI 概念→测试策略→镜像构建→发布管理→项目方案解读）；AGENTS.md + mkdocs.yml 文档索引同步 |
| 2026-06-29 | 文档体系审视 + 安全规范 + CI workflow 落地 | — | 文档体系全面审视，输出 docs/documentation-system-review.md（已有强项、三大缺口、四波落地计划）；新增 docs/standards/security-specification.md（OAuth2 流程、JWT 设计、RBAC 鉴权、敏感数据保护、密钥管理、攻击防护、mall-auth 接口设计）；CI 设计文档落地为实际 yaml（ci.yml 编排器 + backend-ci.yml + frontend-ci.yml + setup-java/setup-frontend composite action）；新增 PR 模板 + Bug/Feature Issue 模板；AGENTS.md 索引修复（git-workflow 路径）+ 合并 JDK21 路径约定 + 新增安全规范与审视报告索引 |
| 2026-06-29 | 属性分组管理设计文档 + 属性管理文档增强 | — | 新增 docs/mall-product/attrgroup-management.md（属性分组 CRUD、分组-属性关联管理、1:1 业务约束）；attr-management.md 大幅增强（规格参数/销售属性 CRUD、分组关联接口）；overview.md 调整属性分组为独立功能域、错误码段拆分（54001~54005 属性 / 54010~54015 属性分组）；category-management.md 补充分类完整路径接口（供级联选择器回显）；接口设计.md 补充分组关联接口与发布商品流程 |
| 2026-07-01 | 商品分类字段命名规范化 | — | 全链路统一字段命名，消除历史遗留不一致：`catId`→`id`（主键对齐 BaseEntity）、`parentCid`→`parentId`、`catLevel`→`level`、`catelogId`→`categoryId`（修正拼写 "catelog"）、`catalogId`→`categoryId`（统一 catalog→category）、`catelogName`→`categoryName`。涉及 6 张表 SQL（字段+索引）+ 6 实体 + 6 DTO/VO + Service/Controller/Mapper + 3 测试类（61 用例全绿）+ 前端 5 组件 + 7 篇模块文档 + 2 篇规范文档。 |
| 2026-07-01 | 日志输出规范文档 | — | 新增 docs/standards/logging-specification.md（日志架构、MDC 链路追踪、logback-spring.xml 配置模板、各环境差异、文件滚动归档、MyBatis SQL 日志、Loki 采集、敏感信息脱敏）；AGENTS.md 索引补充 |
| 2026-07-01 | Spec Kit / Superpowers 学习文档 | — | 新增 docs/learn-docs/spec-kit/（7 篇，SDD 规范驱动开发方法论：SDD 概念/快速上手/模板与质量门禁/宪法/定制系统/集成架构）+ docs/learn-docs/superpowers/（6 篇，AI Agent Skills 方法论：概览/架构/Skills 库/工作流/Skill 设计）；docs/learn-docs/README.md 索引补充 |

---

## 当前进行

| 事项 | 模块 | 状态说明 |
|------|------|---------|
| SPU/SKU/属性体系实现 | mall-product | 设计文档已完成（mall-product/overview.md + attr-management.md + attrgroup-management.md + spu-management.md + sku-management.md），Attr/Spu/Sku 相关 Service 仍为代码生成骨架，实现未开始 |

---

## 服务进度索引

启动某服务开发时，在 `docs/{服务名}/` 下创建 `PROGRESS.md`（从 `docs/_TEMPLATE-PROGRESS.md` 复制）。

| 服务 | 进度文档 | 状态 |
|------|---------|------|
| mall-product | `docs/mall-product/PROGRESS.md` | 骨架 ✅ / 代码生成 ✅ / 分类 CRUD ✅ / 品牌 CRUD ✅ / SPU·SKU·属性 设计✅ 实现中 / 服务注册待验证 |
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
