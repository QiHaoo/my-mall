# 文档分层规范

> 本文档定义 my-mall 项目文档的三层划分，明确每层的定位、读者、目录归属与写作要求。新增或调整文档前请先对照本文确认它属于哪一层。

## 一、为什么分层

项目的 `docs/` 目录里同时存在两类性质截然不同的内容：

- **工程产物**：开发过程中必须遵守的规范、必须参考的设计——这些是项目"怎么造"的依据，写错或过时会直接误导开发。
- **学习材料**：把项目用到的技术点展开讲解的笔记——这些与项目开发无关，是个人学习沉淀，观点可能是一家之言、可能随技术演进过时。

把两者混在一起，读者（包括未来的自己）会分不清"这条是必须遵守的规范"还是"这只是学习笔记里的某种说法"。分层的目的就是划清这条边界，让每一层有明确的**权威性**和**写作标准**。

## 二、三层划分

| 层次 | 名称 | 性质 | 权威性 |
|------|------|------|--------|
| 第一层 | 全局设计 + 全局规范 | 项目开发的"宪法" | 必须遵守 |
| 第二层 | 模块需求与设计 | 落地第一层的具体设计 | 开发依据 |
| 第三层 | 学习文档 | 技术点展开讲解的笔记 | 仅供参考 |

### 第一层：全局设计与全局规范

**定位：** 跨越所有模块、贯穿项目全生命周期的设计与规范。是第二层模块设计的上位约束。

**包含内容：**

- 全局设计：技术选型与架构、服务划分、本地开发环境、服务注册配置、Nacos / 网关 / Feign 等全局中间件配置
- 全局规范：编码规范、Controller 接口规范、测试规范、数据库表设计规范、Git 管理规范、AI 辅助开发流程、MyBatis-Plus 代码生成 / ORM 规范

**读者：** 所有参与本项目开发的人（开发前必读）。

**目录归属：** `docs/` 根目录（如 `docs/coding-standards.md`），以及全局性配置类文档。

**写作要求：**

- 必须可执行：每条规范都要能直接落地，不写含糊的"建议尽量"
- 必须给理由：规范后附"为什么"，便于团队理解和未来修订
- 生产级标准：按 [CLAUDE.md](https://github.com/QiHaoo/my-mall/blob/main/CLAUDE.md) 核心原则，不允许简化或省略
- 修订需谨慎：改动影响所有模块，应在 PR 描述中说明影响范围

**当前属于本层的文档：**

- `tech-stack-and-architecture-2026.md` — 技术选型与架构设计
- `local-dev-reference.md` — 本地开发环境手册
- `service-registration-config.md` — 服务注册配置说明
- `nacos-config-guide.md` — Nacos 配置中心指南
- `gateway-config-guide.md` — API 网关配置指南
- `feign-config.md` — Feign 远程调用配置
- `coding-standards.md` — 编码规范
- `controller-specification.md` — Controller 接口规范
- `testing-specification.md` — 后端测试规范
- `table-design-specification.md` — 数据库表设计规范
- `git-workflow.md` — Git 管理规范
- `development-workflow.md` — AI 辅助开发流程
- `mybatis-plus-codegen-guide.md` — MyBatis-Plus 代码生成规范
- `mybatis-plus-orm-notes.md` — MyBatis-Plus ORM 实践笔记

### 第二层：模块需求与设计

**定位：** 单个模块 / 功能的需求与详细设计。必须在第一层的约束下编写，是开发该模块的直接依据。

**包含内容：** 各服务的需求文档、接口设计、数据模型设计、模块特有配置等。

**读者：** 负责或参与该模块的开发。

**目录归属：** `docs/{服务名}/`（如 `docs/product/`、`docs/common/`）。该模块所有文档（含 PROGRESS）放其中。

**写作要求：**

- 遵从第一层：模块设计不得违反全局规范，如命名、异常处理、表结构须符合对应规范
- 自洽完整：一个模块文档应能独立讲清"做什么、怎么设计、接口长什么样、错误码有哪些"
- 与实现同步：设计变更需同步更新文档，并在 [PROGRESS.md](PROGRESS.md) 记录关联提交（见 CLAUDE.md「进度文档与提交关联规则」）
- 错误码、DTO、接口契约要具体到可直接据此编码

**当前属于本层的文档：**

- `common/common-module-design.md` — mall-common 公共模块设计
- `mall-product/overview.md` — 商品中心模块概述（职责、功能域、概念、ER 图）
- `mall-product/category-management.md` — 商品分类管理
- `mall-product/brand-management.md` — 品牌管理
- `mall-product/attr-management.md` — 属性管理（属性元数据 + 属性分组）
- `mall-product/spu-management.md` — SPU 管理
- `mall-product/sku-management.md` — SKU 管理
- `mall-product/object-storage-design.md` — 对象存储设计

### 第三层：学习文档

**定位：** 与项目开发**无关**的个人学习笔记。把项目中用到的技术点（如 Nacos、Feign、网关）展开原理性讲解，用于学习沉淀，不是开发依据。

**读者：** 学习者本人；对技术原理感兴趣的读者。

**目录归属：** `docs/learn-docs/`，按技术主题分子目录（如 `learn-docs/service-discovery/`）。

**写作要求：**

- 明确边界：文档开篇应注明"本文为学习笔记，非项目开发规范"，避免与第一/二层混淆
- 可以发散：可讲原理、源码、对比、历史演进，不要求可执行
- 观点自由：可以是一家之言，但需注明出处或依据
- 不阻塞开发：学习文档的过时或错误不影响项目，但仍应及时订正

**当前属于本层的文档：**

- `learn-docs/service-discovery/` — 服务发现系列
- `learn-docs/nacos-config/` — Nacos 配置系列
- `learn-docs/remote-call/` — 远程调用（Feign）系列
- `learn-docs/api-gateway/` — API 网关系列
- `learn-docs/testing/` — 测试相关笔记

> `learn-docs/` 中的 `project-implementation.md` 类文件会联系学习内容与项目实现，但它仍是学习视角的梳理，不属于第二层模块设计。

## 三、三层之间的关系

```
第一层 全局设计 + 规范   ──约束──▶   第二层 模块需求与设计   ──实现──▶   代码
        │
        │ （第一/二层是项目工程产物，权威性递减但都属于开发依据）
        │
        ▼
第三层 学习文档   ◀──讲解──   项目用到的技术点（与开发流程无关）
```

- **第一层 → 第二层**：第二层必须服从第一层。若模块设计发现全局规范不合理，应反向修订第一层，而非在模块里绕开。
- **第一/二层 vs 第三层**：工程产物与学习材料互不管辖。学习文档可以引用项目实现作例子，但不能反过来规定项目该怎么做；项目规范也不必迁就学习文档里的讲法。

## 四、写作前的自检清单

新增一篇文档前，先回答：

1. **它属于哪一层？** 按上面三层的定位对号入座，不确定时按"它是不是开发必须读的"判断——必须读的是第一/二层，可不读的是第三层。
2. **目录放对了吗？** 第一层放 `docs/` 根，第二层放 `docs/{服务名}/`，第三层放 `docs/learn-docs/{主题}/`。
3. **写作标准对齐了吗？** 第一/二层按生产级标准写且必须可执行；第三层可发散但需标明是学习笔记。
4. **导航登记了吗？** 新文档需在 [`mkdocs.yml`](https://github.com/QiHaoo/my-mall/blob/main/mkdocs.yml) 的 `nav` 中登记，否则不出现在文档站导航（但仍可被搜索到）。
5. **索引同步了吗？** 全局性文档（第一层）应在 [CLAUDE.md](https://github.com/QiHaoo/my-mall/blob/main/CLAUDE.md) 的文档索引表中登记一行。

## 五、文档站导航中的体现

当前 [`mkdocs.yml`](https://github.com/QiHaoo/my-mall/blob/main/mkdocs.yml) 的导航结构已隐含三层划分：

| 导航分区 | 对应层次 |
|---------|---------|
| 技术与架构 / 开发规范 | 第一层 |
| 模块设计 | 第二层 |
| 学习笔记 | 第三层 |
| 归档 | 历史分析资料，非活跃文档 |

> 项目进度（PROGRESS）和文档站部署指南属于项目工程管理类文档，单列于导航顶部，不归入三层。

这种按主题分区的方式与三层划分基本吻合，无需为分层而重构导航。三层划分的核心价值在于**写作时明确权威性边界**，而非物理上隔离目录。
