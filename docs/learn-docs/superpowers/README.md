# Superpowers 学习笔记

> Superpowers 是一套面向 AI 编程 Agent 的完整软件开发方法论，基于可组合的 Skills（技能）体系构建。
> 仓库地址：https://github.com/obra/superpowers

## 这是什么

Superpowers 不是一个代码库，而是一套**行为塑造文档（Skills）+ 自动注入机制（Bootstrap）**的组合。它让 AI 编程 Agent（Claude Code、Codex、Cursor、Gemini CLI 等）在编写代码前自动遵循一套严格的开发流程：

- 先 brainstorm 设计，再动手写代码
- 强制 TDD（测试驱动开发）
- 系统化调试而非盲目猜测
- 子 Agent 驱动的任务执行与双重审查
- 完成前必须运行验证命令

核心理念：**Skills 是塑造 Agent 行为的代码，不是普通的说明文档。**

## 学习路径

| 文档 | 内容 | 建议阅读顺序 |
|------|------|:---:|
| [01-overview.md](./01-overview.md) | 项目概述、核心理念、基本工作流 | 1 |
| [02-architecture.md](./02-architecture.md) | 三层架构（Skills / Tool Mapping / Bootstrap）、多平台适配机制 | 2 |
| [03-skills-library.md](./03-skills-library.md) | 13 个技能的逐一详解 | 3 |
| [04-workflow.md](./04-workflow.md) | 端到端开发工作流：从 brainstorm 到 merge | 4 |
| [05-skill-design.md](./05-skill-design.md) | 技能设计方法论：TDD 应用于文档、防合理化漏洞 | 5 |

## 核心收获

学完后你将理解：

1. **如何用文档控制 Agent 行为** — 不是写提示词，而是写"行为塑造代码"
2. **TDD 的真正含义** — 不只是"先写测试"，而是 RED-GREEN-REFACTOR 的铁律
3. **子 Agent 驱动开发（SDD）** — 如何用隔离上下文的子 Agent 实现高质量自动化
4. **系统化调试** — 4 阶段根因调查法，杜绝"试一下看看"
5. **技能设计方法论** — 如何用 TDD 思路测试文档本身
6. **跨平台架构设计** — 一套内容如何适配 11+ 种 AI Agent 平台

## 快速参考

### 技能清单

| 类别 | 技能 | 作用 |
|------|------|------|
| 元技能 | using-superpowers | 启动时注入，建立技能使用规则 |
| 元技能 | writing-skills | 创建新技能的方法论 |
| 设计 | brainstorming | 将想法转化为设计文档 |
| 规划 | writing-plans | 将设计拆解为可执行的任务计划 |
| 执行 | subagent-driven-development | 子 Agent 逐任务执行 + 双重审查 |
| 执行 | executing-plans | 批量执行计划（人工检查点） |
| 执行 | dispatching-parallel-agents | 并行派发独立任务 |
| 测试 | test-driven-development | RED-GREEN-REFACTOR 铁律 |
| 调试 | systematic-debugging | 4 阶段根因调查 |
| 质量 | verification-before-completion | 声称完成前必须运行验证 |
| 协作 | requesting-code-review | 派发代码审查子 Agent |
| 协作 | receiving-code-review | 接收审查反馈的正确姿态 |
| 工作流 | using-git-worktrees | 隔离工作空间 |
| 工作流 | finishing-a-development-branch | 完成分支：merge/PR/保留/丢弃 |

### 基本工作流

```
brainstorming → using-git-worktrees → writing-plans
  → subagent-driven-development（内含 TDD + code-review）
  → finishing-a-development-branch
```

### 三层架构

```
Skills（平台无关，行为塑造的源代码）
  ↕ Tool Mapping（每个平台一份，动作→工具名映射）
Bootstrap（会话启动时注入 using-superpowers 全文）
```
