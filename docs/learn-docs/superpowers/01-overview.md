# 01 - Superpowers 概述与核心理念

## 什么是 Superpowers

Superpowers 是一套面向 AI 编程 Agent 的**完整软件开发方法论**，由 [Jesse Vincent](https://blog.fsck.com) 和 [Prime Radiant](https://primeradiant.com) 团队开发。

它不是一个代码框架，不是一个 IDE 插件 API，而是一组**行为塑造文档（Skills）**加上一套**自动注入机制（Bootstrap）**，让 AI Agent 从会话启动的第一刻起就遵循严格的开发流程。

### 核心问题

AI 编程 Agent 的常见问题：

| 问题 | Superpowers 的解决方式 |
|------|----------------------|
| 不问需求就直接写代码 | brainstorming 技能强制先设计后实现 |
| 写完代码才补测试 | TDD 技能强制先写失败测试 |
| 调试靠猜，反复试错 | systematic-debugging 强制 4 阶段根因调查 |
| 声称"已完成"但不验证 | verification-before-completion 铁律 |
| 上下文污染导致质量下降 | 子 Agent 驱动开发，每个任务隔离上下文 |
| 不做代码审查 | 每个任务后自动派发审查子 Agent |

### 核心理念

```
1. Test-Driven Development — 永远先写测试
2. Systematic over ad-hoc — 流程优于猜测
3. Complexity reduction — 简洁是首要目标
4. Evidence over claims — 声称前必须有证据
```

## 它是怎么工作的

从用户发第一条消息开始：

1. **会话启动** — Bootstrap 钩子将 `using-superpowers` 技能全文注入 Agent 上下文
2. **技能检查** — Agent 收到任何消息，先检查是否有相关技能（1% 可能性就要检查）
3. **自动触发** — 用户说"做个 X"，Agent 自动触发 brainstorming，不直接写代码
4. **设计→规划→执行** — 按工作流逐步推进，每步有对应技能
5. **持续审查** — 子 Agent 执行任务，审查子 Agent 逐任务审查

> 关键：这一切是**自动的**。用户不需要手动调用技能，Agent 自己判断该用哪个技能。

## 基本工作流

```
1. brainstorming
   └─ 将模糊想法通过对话转化为设计文档
   └─ 提出 2-3 个方案，推荐一个，分节展示设计
   └─ 用户逐节确认后，写入 spec 文档

2. using-git-worktrees
   └─ 创建隔离工作空间（新分支）
   └─ 运行项目 setup，验证测试基线

3. writing-plans
   └─ 将设计拆解为 2-5 分钟的小任务
   └─ 每个任务有精确文件路径、完整代码、验证步骤
   └─ 无占位符，无"TODO"，无"类似上文"

4. subagent-driven-development（推荐）或 executing-plans
   └─ SDD：每个任务派发新子 Agent，双重审查（规范+质量）
   └─ executing-plans：批量执行，人工检查点

5. test-driven-development（贯穿实现全过程）
   └─ RED：写失败测试 → 验证失败
   └─ GREEN：写最小代码通过 → 验证通过
   └─ REFACTOR：重构，保持绿色

6. requesting-code-review
   └─ 任务间自动审查，Critical 问题阻塞进度

7. finishing-a-development-branch
   └─ 验证测试 → 提供 4 个选项（merge/PR/保留/丢弃）
   └─ 清理工作空间
```

## 技能是"行为塑造代码"

这是理解 Superpowers 的关键：**Skills 不是普通的说明文档，而是塑造 Agent 行为的代码。**

### 与普通文档的区别

| 普通文档 | Superpowers Skill |
|---------|-------------------|
| 描述"怎么做" | 强制"必须怎么做" |
| 读者自由选择是否遵循 | Agent 必须遵循，有 Red Flags 表拦截合理化 |
| 一次写完 | TDD 开发：先写压力测试，再写技能，再堵漏洞 |
| 靠人自觉执行 | 靠 Bootstrap 自动注入 + 合理化表拦截 |
| 语气柔和（"建议..."）| 语气强硬（"Iron Law"、"MANDATORY"、"No exceptions"）|

### 防合理化设计

Agent 很聪明，会在压力下找借口跳过规则。Superpowers 的应对：

```markdown
## Red Flags - STOP and Start Over

- "Too simple to test" → Simple code breaks. Test takes 30 seconds.
- "I'll test after" → Tests passing immediately prove nothing.
- "Deleting X hours is wasteful" → Sunk cost fallacy.
- "This is different because..." → All of these mean: Delete code. Start over.

**Violating the letter of the rules is violating the spirit of the rules.**
```

每个纪律性技能都有：
- **Iron Law（铁律）**：不可违反的核心规则
- **Rationalization Table（合理化表）**：列出常见借口和现实
- **Red Flags（红旗列表）**：出现这些想法就停下来

## 多平台支持

Superpowers 支持 11+ 种 AI 编程平台：

| 平台 | 安装方式 |
|------|---------|
| Claude Code | `/plugin install superpowers@claude-plugins-official` |
| Codex App/CLI | 官方插件市场 |
| Cursor | `/add-plugin superpowers` |
| Gemini CLI | `gemini extensions install` |
| GitHub Copilot CLI | `copilot plugin install` |
| Antigravity | `agy plugin install` |
| Factory Droid | `droid plugin install` |
| Kimi Code | `/plugins` 市场 |
| OpenCode | 配置文件引用 |
| Pi | `pi install` |

一套技能内容，通过三种适配机制（Shell Hook / 进程内插件 / 指令文件）覆盖所有平台。详见 [02-architecture.md](./02-architecture.md)。

## 项目哲学

### 1. 流程优于猜测

```
Random fixes waste time and create new bugs.
Quick patches mask underlying issues.

Core principle: ALWAYS find root cause before attempting fixes.
```

系统化方法不是慢，而是更快：15-30 分钟系统调试 vs 2-3 小时盲目试错。

### 2. 证据优于声明

```
NO COMPLETION CLAIMS WITHOUT FRESH VERIFICATION EVIDENCE

"Should work now" → RUN the verification
"I'm confident" → Confidence ≠ evidence
"Agent said success" → Verify independently
```

### 3. YAGNI（You Aren't Gonna Need It）

设计阶段无情削减不必要的功能。代码阶段不写测试不需要的功能。

### 4. "Your human partner" 而非 "the user"

Superpowers 刻意使用"your human partner"（你的人类伙伴）而非"the user"（用户）。这不是用词偏好，而是**关系定位**：Agent 不是执行命令的工具，而是与人类平等协作的开发伙伴。这个用词经过了大量测试，改变了 Agent 的行为模式。

## 与本项目（my-mall）的关系

本项目的 AGENTS.md 中提到了 AI 辅助开发体系（Skill 体系），Trae IDE 也内置了类似的 Skill 工具。学习 Superpowers 可以：

1. 理解 Skill 体系的设计原理——为什么用文档控制 Agent 行为
2. 学习 TDD、系统化调试等实践的标准做法
3. 借鉴 SDD（子 Agent 驱动开发）的模式用于复杂任务
4. 理解如何编写有效的 Skill（行为塑造文档）

> 参见 [docs/standards/ai-assisted-development.md](../../standards/ai-assisted-development.md) 和 [docs/standards/development-workflow.md](../../standards/development-workflow.md)
