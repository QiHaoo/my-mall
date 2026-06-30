# Spec Kit 学习笔记

> 目标读者：对 AI 辅助开发、规范驱动开发（SDD）感兴趣，想搞清楚 GitHub Spec Kit 到底是什么、怎么用、为什么这么设计的开发者。看完本系列，能独立用 Spec Kit 跑通一个完整的 SDD 流程，并理解它背后的方法论与工程考量。

## 为什么单独学 Spec Kit

Spec Kit（GitHub 官方开源，常称 `spec-kit` / Specify CLI）是 **Spec-Driven Development（SDD，规范驱动开发）** 的落地工具包。SDD 的核心主张是：**规范（specification）才是源头，代码是规范的派生产物**——这和传统「先写代码、文档只是陪衬」完全相反。

值得学的理由：

- **方法论层面的范式转变**：在 LLM 能做大量代码生成的时代，"先写清楚要做什么"比"直接让 AI 写代码"更关键。SDD 提供了一套把模糊想法变成可执行规范的工程化流程。
- **不只是 prompt 技巧**：Spec Kit 用模板、宪法（constitution）、检查清单、质量门禁等机制，把"约束 LLM 输出"做成了系统，而不是靠人肉调 prompt。
- **与本项目的关系**：my-mall 是 AI 辅助开发的项目（见 `docs/standards/ai-assisted-development.md`、`docs/standards/development-workflow.md` 的"轻量 Spec + TDD"流程）。Spec Kit 是同类思路的成熟开源实现，学它能对照理解"规范先行"为什么有效、怎么落地，吸收可复用的设计思想。
- **生态规模说明它的代表性**：30+ AI 编码代理集成、100+ 社区扩展、22 个 preset、106K+ star，是目前 SDD 领域最活跃的工具。

> 注：本系列是学习笔记，目标是建立认知；my-mall 自身仍用项目里已有的开发流程规范，不直接套用 Spec Kit。

## Spec Kit 是什么（一句话）

一个用 CLI（`specify`）初始化项目、用斜杠命令（`/speckit.*`）驱动 AI 编码代理、按 `constitution → spec → plan → tasks → implement` 流程把"想法"变成"代码"的工具包。它本身不写代码，而是给 AI 代理提供结构化的上下文和约束。

## 学习路径

```text
第 1 阶段：建立认知
  01-SDD 方法论 → 02-快速上手

第 2 阶段：理解机制
  03-模板与质量约束 → 04-宪法与架构治理

第 3 阶段：扩展与架构
  05-定制化体系 → 06-集成与内部架构
```

- **第 1 阶段**先回答"是什么"和"怎么跑起来"：SDD 的哲学与原则，再用一个完整流程把 Spec Kit 用一遍。
- **第 2 阶段**深挖"为什么有效"：模板如何约束 LLM 产出高质量规范，宪法如何强制架构纪律。
- **第 3 阶段**看"怎么扩展、怎么搭"：extensions/presets/bundles/workflows 四件套，以及 CLI 内部如何把 30+ 代理接进来。

如果时间有限，至少走完 `01 → 02`，能建立最小闭环认知；`03 → 04` 解释"为什么不是又一个 prompt 玩具"；`05 → 06` 适合想二次定制或研究实现的人。

## 文档清单

| # | 文档 | 核心内容 | 优先级 |
|---|------|---------|--------|
| - | [README.md（本文）](./README.md) | 学习地图、路径、清单 | — |
| 01 | [01-sdd-methodology.md](./01-sdd-methodology.md) | SDD 是什么、权力反转、核心原则、为什么是现在、开发阶段 | 必读 |
| 02 | [02-getting-started.md](./02-getting-started.md) | 安装、init、完整工作流（9 个命令）、产物结构、典型示例 | 必读 |
| 03 | [03-templates-and-quality.md](./03-templates-and-quality.md) | spec/plan/tasks 模板结构、模板如何约束 LLM、检查清单与门禁 | 必读 |
| 04 | [04-constitution.md](./04-constitution.md) | 宪法机制、九条条款、不可变原则、治理与演进 | 推荐 |
| 05 | [05-customization-system.md](./05-customization-system.md) | extensions/presets/bundles/workflows 四层定制、解析栈、何时用哪个 | 推荐 |
| 06 | [06-integrations-and-architecture.md](./06-integrations-and-architecture.md) | 集成架构、IntegrationBase、命令文件格式、添加新代理 | 选读 |

> 标「必读」的是建立 SDD 闭环认知的关键；「推荐」解释机制与扩展；「选读」面向想改源码或自建集成的人。

## 前置知识

| 知识点 | 需要程度 | 说明 |
|--------|---------|------|
| 命令行基础 | 必须 | 能在终端执行 `specify` / `uv` 命令 |
| Git 基本操作 | 必须 | Spec Kit 强依赖分支（feature 分支 = 规范目录） |
| 用过任意 AI 编码代理 | 推荐 | Copilot / Claude Code / Cursor 等，理解"斜杠命令"概念 |
| Python 基础 | 06 需要 | CLI 用 Python 写，看源码要懂 |
| 软件工程经验 | 有则更好 | 理解 PRD、技术方案、任务拆解的传统流程，才能体会 SDD 的差异 |

> 本系列不假设你用过 Spec Kit，但假设你写过代码、用过 Git、大致知道 LLM 能生成代码。

## 核心概念速查

| 概念 | 一句话解释 | 详见 |
|------|-----------|------|
| SDD | 规范驱动开发，规范是源头、代码是派生 | 01 |
| Specify CLI | `specify` 命令，初始化项目、管理扩展 | 02 / 06 |
| Constitution | 项目宪法，不可变架构原则 | 04 |
| spec.md | 功能规范，描述"做什么/为什么" | 02 / 03 |
| plan.md | 技术实现计划，描述"怎么做" | 02 / 03 |
| tasks.md | 可执行任务清单，带依赖与并行标记 | 02 / 03 |
| Slash command | `/speckit.*` 命令，驱动 AI 代理 | 02 |
| Extension | 扩展，加新命令/能力 | 05 |
| Preset | 预设，覆盖模板/命令 | 05 |
| Bundle | 捆绑包，角色化整套配置 | 05 |
| Workflow | 工作流，编排多步骤 | 05 |
| Integration | 集成，对接某个 AI 代理 | 06 |

## 与本项目（my-mall）的关系

my-mall 自身有一套 AI 辅助开发规范（`docs/standards/ai-assisted-development.md`、`docs/standards/development-workflow.md`），采用"轻量 Spec + TDD"思路，**不直接使用 Spec Kit**。两者是同类思想的不同实现：

| 维度 | my-mall 自有流程 | Spec Kit |
|------|----------------|----------|
| 定位 | 项目内开发规范 | 通用 SDD 工具包 |
| 规范形态 | 分散在 docs/ + 任务清单 | 集中在 specs/{feature}/ 下结构化产物 |
| 治理 | AGENTS.md + 各标准文档 | constitution.md 宪法机制 |
| 执行 | 人工 + AI 协作 | 斜杠命令驱动 AI 代理 |
| 适用 | 已确定技术栈的微服务项目 | 任意技术栈、0→1 或存量改造 |

> 学 Spec Kit 不是为了在 my-mall 里替换现有流程，而是吸收"规范先行、模板约束、质量门禁"的工程思想，对照理解自己项目流程的设计取舍。

## 阅读约定

- **概念讲解为主，配 Spec Kit 真实命令/模板举例**：每个概念先讲清楚是什么 / 为什么 / 怎么做，再给真实示例
- 命令行示例中 `$` 开头的是用户输入，其余是输出
- 引用 Spec Kit 源文件时给出相对路径（相对 `D:\WorkSpace\spec-kit\`），方便对照源码
- 重点是「**为什么这么做**」，不只是"怎么做"——理解设计动机比记住命令更重要
- 英文术语保留原文（如 constitution、spec、plan、preset），避免翻译失真

## 参考资源

- [Spec Kit 官方仓库](https://github.com/github/spec-kit) — 源码、README、issue
- [Spec Kit 文档站](https://github.github.io/spec-kit/) — 完整官方文档
- [spec-driven.md](https://github.com/github/spec-kit/blob/main/spec-driven.md) — SDD 方法论深度说明（本系列 01 的主要来源）
- [Quick Start](https://github.github.io/spec-kit/quickstart.html) — 官方快速上手
- [Martin Fowler — SDD 工具综述](https://martinfowler.com/articles/exploring-gen-ai/sdd-3-tools.html) — spec-first / spec-anchored / spec-as-source 三层模型
