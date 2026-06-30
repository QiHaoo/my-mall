# 02 - Superpowers 架构设计

## 三层架构

Superpowers 的核心架构由三层组成，各层职责清晰分离：

```
┌─────────────────────────────────────────────────┐
│              Skills（技能层）                     │
│  平台无关的行为塑造文档，所有平台的共同源代码       │
│  skills/*/SKILL.md                               │
├─────────────────────────────────────────────────┤
│          Tool Mapping（工具映射层）                │
│  每个平台一份，将"动作"翻译为平台原生工具名         │
│  references/<harness>-tools.md                   │
├─────────────────────────────────────────────────┤
│          Bootstrap（引导层）                      │
│  会话启动时自动注入 using-superpowers 全文         │
│  hooks/session-start 或 进程内插件 或 指令文件     │
└─────────────────────────────────────────────────┘
```

### 设计原则

**Skills 只描述动作，从不提及具体工具名。**

例如技能中说"dispatch a subagent"（派发子 Agent），而不是"调用 Task 工具"。
这让同一份技能文档可以在 Claude Code、Codex、Gemini 等所有平台运行而无需修改。

## 第一层：Skills（技能）

### 目录结构

```
skills/
  brainstorming/
    SKILL.md                    # 主文档（必需）
    scripts/                    # 可执行脚本
    visual-companion.md         # 辅助文档
  test-driven-development/
    SKILL.md
    testing-anti-patterns.md    # 辅助参考
  systematic-debugging/
    SKILL.md
    root-cause-tracing.md       # 辅助技术
    defense-in-depth.md
    condition-based-waiting.md
  subagent-driven-development/
    SKILL.md
    implementer-prompt.md       # 子 Agent 提示词模板
    task-reviewer-prompt.md
    scripts/
      review-package            # 生成 diff 包
      task-brief                # 提取任务简报
      sdd-workspace             # 工作空间管理
  ...
```

### SKILL.md 结构

每个技能的主文档遵循统一结构：

```markdown
---
name: skill-name-with-hyphens     # YAML 前置元数据
description: Use when [触发条件]   # 只描述何时用，不描述做什么
---

# Skill Name

## Overview
核心原则，1-2 句话。

## When to Use / When NOT to Use
触发条件、症状、场景。

## The Process / Core Pattern
流程图（graphviz dot）、步骤、代码示例。

## Common Rationalizations（纪律性技能）
借口 → 现实 对照表。

## Red Flags
出现这些想法就停下来。

## Quick Reference
速查表。
```

### 前置元数据（Frontmatter）

```yaml
---
name: test-driven-development
description: Use when implementing any feature or bugfix, before writing implementation code
---
```

**关键设计：description 只描述"何时使用"，不描述"技能做什么"。**

测试发现，如果 description 包含了工作流摘要，Agent 会直接按 description 执行而跳过阅读技能全文。例如：

```yaml
# 错误：Agent 会按这个执行，跳过全文
description: Use for TDD - write test first, watch it fail, write minimal code, refactor

# 正确：只写触发条件，Agent 必须读全文才知道怎么做
description: Use when implementing any feature or bugfix, before writing implementation code
```

### 技能类型

| 类型 | 特点 | 示例 |
|------|------|------|
| **Rigid（刚性）** | 必须严格遵循，不可"灵活变通" | TDD、systematic-debugging |
| **Flexible（柔性）** | 原则可适配上下文 | 设计模式类技能 |

技能文档本身会告诉你它是哪种。

## 第二层：Tool Mapping（工具映射）

每个平台有一份工具映射文件，将通用"动作"翻译为平台原生工具名。

### 映射文件位置

```
skills/using-superpowers/references/
  claude-code-tools.md    # Claude Code 工具映射
  codex-tools.md          # Codex 工具映射
  copilot-tools.md        # Copilot CLI 工具映射
  gemini-tools.md         # Gemini CLI 工具映射
  pi-tools.md             # Pi 工具映射
  antigravity-tools.md    # Antigravity 工具映射
```

### 映射内容示例

每个映射文件覆盖这些动作：

| 通用动作 | Claude Code | Codex | Gemini CLI |
|---------|-------------|-------|------------|
| 读文件 | Read | read | read_file |
| 写/编辑文件 | Write / Edit | apply_patch | write_file / edit_file |
| 运行命令 | RunCommand | shell | run_shell_command |
| 派发子 Agent | Task（subagent_type） | task | — |
| 创建 Todo | TodoWrite | todo | — |
| 调用技能 | Skill | 原生加载 | activate_skill |
| 搜索代码 | Grep / Glob / SearchCodebase | grep / glob | grep_files / list_files |
| 获取 URL | WebFetch / WebSearch | fetch | web_fetch |

### 没有原生技能工具的平台

部分平台（如 Pi、Antigravity）没有原生的 `Skill` 工具。这时**读取 SKILL.md 文件**就是官方认可的技能加载方式：

```markdown
# pi-tools.md
- invoke a skill: read the relevant skills/<name>/SKILL.md with the file-read tool when the skill applies
```

`using-superpowers` 中说"Never read skill files manually"指的是"不要绕过平台机制"，在没有 Skill 工具的平台上，读文件就是平台机制。

## 第三层：Bootstrap（引导注入）

**这是整个系统的核心。** 没有 Bootstrap，技能文件只是磁盘上的死文件，永远不会被调用。

### 作用

在每次会话启动时（包括 clear 和 compact 后），自动将 `using-superpowers/SKILL.md` 的完整内容注入 Agent 上下文：

```
<EXTREMELY_IMPORTANT>
You have superpowers.

Below is the full content of your 'superpowers:using-superpowers' skill -
your introduction to using skills. For all other skills, use the Skill tool:

[using-superpowers/SKILL.md 全文]
</EXTREMELY_IMPORTANT>
```

### 注入内容教了什么

这段注入的技能建立了**技能使用规则**：

1. **1% 规则**：如果有 1% 的可能某个技能适用，就必须调用它
2. **优先级**：用户指令 > Superpowers 技能 > 默认系统提示
3. **技能优先级**：流程技能（brainstorming、debugging）先于实现技能
4. **红旗表**：拦截"这只是个简单问题"等合理化想法
5. **宣布**：使用技能时要宣布"Using [skill] to [purpose]"

### 三种注入机制（Shape）

| Shape | 机制 | 平台 | 工作方式 |
|-------|------|------|---------|
| **A - Shell Hook** | 会话启动时运行 shell 命令，读取 stdout | Claude Code、Codex、Cursor、Copilot CLI | `hooks/session-start` 脚本读取 SKILL.md，输出 JSON |
| **B - 进程内插件** | JS/TS 模块，生命周期回调注入消息 | OpenCode、Pi | 读取 SKILL.md，去掉 frontmatter，组装字符串注入为 user 消息 |
| **C - 指令文件** | 扩展自带的 context 文件 | Gemini CLI、Antigravity | `GEMINI.md` 用 `@`-include 引入 SKILL.md |

### Shape A：Shell Hook 详解

```
会话启动
  → hooks/hooks.json 注册了 SessionStart 事件
  → 运行 hooks/run-hook.cmd session-start
  → run-hook.cmd 定位 bash，执行 hooks/session-start 脚本
  → 脚本 cat using-superpowers/SKILL.md
  → 转义 JSON，输出到 stdout
  → 平台读取 stdout，注入 Agent 上下文
```

**关键细节：不同平台的 JSON 输出格式不同**

```json
// Claude Code
{ "hookSpecificOutput": { "hookEventName": "SessionStart", "additionalContext": "..." } }

// Cursor
{ "additional_context": "..." }

// Copilot CLI / SDK 标准
{ "additionalContext": "..." }
```

输出错误格式会导致静默失败或重复注入。

### Shape B：进程内插件详解

```javascript
// .opencode/plugins/superpowers.js（简化）
const bootstrap = readSkillMd('using-superpowers/SKILL.md')
                  .replace(/^---[\s\S]*?---/, '')  // 去掉 frontmatter

const sessionContext = `<EXTREMELY_IMPORTANT>
You have superpowers. ${bootstrap}
</EXTREMELY_IMPORTANT>`

// 在消息转换钩子中注入
export default {
  config: ({ config }) => {
    // 注册 skills 目录
  },
  experimental: {
    chat: {
      messages: {
        transform: ({ messages }) => {
          // 去重检查 + 注入 bootstrap
          if (!hasBootstrap(messages)) {
            messages.unshift({ role: 'user', content: sessionContext })
          }
          return messages
        }
      }
    }
  }
}
```

**关键细节：**
- 注入为 **user 消息**，不是 system 消息（system 消息会膨胀 token，且破坏部分模型）
- **去重守卫**：回调可能每步都触发，需检查是否已注入
- **Compaction 处理**：压缩历史后需重新注入

### Shape C：指令文件详解

```markdown
<!-- GEMINI.md（Gemini 扩展自带的指令文件）-->
@./skills/using-superpowers/SKILL.md
@./skills/using-superpowers/references/gemini-tools.md
```

Gemini 的 `@`-include 语法在加载扩展时自动展开这两个文件的内容。无需脚本、无需代码。

**注意：** `@`-include 是否真正展开需要验证。某些 Gemini 衍生工具会将 `@./path` 当作"模型可以选择读取的提示"而非" guaranteed inline expansion"。需用唯一标记测试确认。

## 自动触发机制

Bootstrap 注入的 `using-superpowers` 技能建立了**自动触发循环**：

```
用户发送消息
  → Agent 检查：有 1% 可能某技能适用吗？
    → 是 → 调用 Skill 工具加载技能
      → 宣布 "Using [skill] to [purpose]"
      → 有 checklist？为每项创建 todo
      → 严格按技能执行
    → 否 → 直接响应
```

### 验收测试

一个平台是否真正集成了 Superpowers，验收标准是：

> 在干净会话中发送：`Let's make a react todo list`
>
> **通过：** brainstorming 技能在任何代码编写之前自动触发
> **失败：** Agent 直接开始写代码

这个测试必须通过，否则集成不被接受。

## 优先级体系

```
1. 用户的显式指令（CLAUDE.md、AGENTS.md、直接请求）  ← 最高
2. Superpowers 技能                                  ← 覆盖默认行为
3. 默认系统提示                                       ← 最低
```

如果用户的 AGENTS.md 说"不用 TDD"，技能说"必须用 TDD"，遵循用户指令。用户始终拥有控制权。

## 跨平台兼容性设计

### 1. Skills 只描述动作

```
技能中说：dispatch a subagent（派发子 Agent）
不说：    call the Task tool with subagent_type parameter
```

### 2. 零运行时依赖

Superpowers 是零依赖插件。不依赖任何第三方工具或服务（新增平台适配是唯一例外）。

### 3. Windows 兼容

`hooks/run-hook.cmd` 是一个**多语言脚本**（polyglot）——同一个文件既是有效的 Windows 批处理脚本，也是有效的 Unix shell 脚本：

- Windows：`cmd.exe` 执行批处理部分，定位 bash，执行 hook 脚本
- Unix：开头的 `:` 使批处理块成为 no-op，shell 直接执行

Hook 脚本**没有扩展名**（`session-start` 而非 `session-start.sh`），因为 Claude Code 的 Windows 处理会对包含 `.sh` 的命令预置 `bash`，导致重复调用。

## 测试体系

### 插件基础设施测试（tests/）

```
tests/
  hooks/              # 测试 session-start 输出格式
  brainstorm-server/  # 测试可视化伴侣服务器
  claude-code/        # Claude Code 集成测试
  opencode/           # OpenCode 集成测试
  pi/                 # Pi 扩展测试
  explicit-skill-requests/  # 技能触发行为测试
```

### 技能行为评估（evals/）

使用 [superpowers-evals](https://github.com/prime-radiant-inc/superpowers-evals/) 的 Drill 评估框架：

- 驱动真实 tmux 会话（Claude Code / Codex / Gemini CLI）
- 用 LLM 评判技能合规性
- 压力测试：时间压力、沉没成本、权威压力、疲劳

详见 [05-skill-design.md](./05-skill-design.md)。
