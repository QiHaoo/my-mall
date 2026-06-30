# 03 - 技能库详解

Superpowers 包含 13 个技能，按功能分为 5 大类。本文逐一详解。

---

## 一、元技能（Meta）

### 1. using-superpowers — 技能系统引导

**作用：** 会话启动时注入，建立"使用任何技能前先检查"的规则。

**核心规则：**
- 如果有 1% 的可能某技能适用，就必须调用它
- 技能检查在回答问题之前——包括澄清性问题
- 宣布使用："Using [skill] to [purpose]"

**红旗表（拦截合理化）：**

| 想法 | 现实 |
|------|------|
| "这只是个简单问题" | 问题也是任务，检查技能 |
| "我先探索一下代码库" | 技能告诉你怎么探索，先检查 |
| "我记得这个技能" | 技能会演进，读当前版本 |
| "这个技能太大材小用了" | 简单的事会变复杂，用它 |

**技能优先级：**
1. 流程技能先（brainstorming、systematic-debugging）— 决定"怎么做"
2. 实现技能后（frontend-design 等）— 指导执行

**技能类型：**
- Rigid（刚性）：TDD、systematic-debugging — 严格遵循，不可变通
- Flexible（柔性）：模式类 — 原则适配上下文

### 2. writing-skills — 创建新技能

**作用：** 用 TDD 方法论创建和测试新技能。

**核心理念：** 写技能就是 TDD 应用于过程文档。

| TDD 概念 | 技能创建对应 |
|---------|-------------|
| 测试用例 | 压力场景 + 子 Agent |
| 生产代码 | 技能文档（SKILL.md）|
| 测试失败（RED）| Agent 无技能时违反规则（基线行为）|
| 测试通过（GREEN）| Agent 有技能后遵守规则 |
| 重构（REFACTOR）| 堵住新发现的合理化漏洞 |

**铁律：**
```
NO SKILL WITHOUT A FAILING TEST FIRST
```

没有先看到 Agent 在无技能时失败，就不知道技能教的是否正确。

**技能发现优化（SDO）：**
- description 只写"何时使用"，不写"技能做什么"（否则 Agent 会按 description 执行跳过全文）
- 关键词覆盖：错误信息、症状、同义词、工具名
- 命名用动词开头：`creating-skills` 而非 `skill-creation`

详见 [05-skill-design.md](./05-skill-design.md)。

---

## 二、设计与规划（Design & Planning）

### 3. brainstorming — 头脑风暴与设计

**作用：** 在任何创造性工作之前，将模糊想法通过对话转化为设计文档。

**硬门禁：**
```
在展示设计并获得用户批准之前，不得调用任何实现技能、写任何代码、
搭建任何项目、或采取任何实现行动。
```

**反模式："这太简单了不需要设计"**

每个项目都要走这个流程。Todo list、单函数工具、配置修改——全部都要。"简单"项目恰恰是未经检验的假设造成最多浪费的地方。

**流程：**
1. 探索项目上下文（文件、文档、最近提交）
2. 逐个提问澄清（优先多选题，一次一个问题）
3. 提出 2-3 个方案 + 权衡 + 推荐
4. 分节展示设计，逐节确认
5. 写设计文档到 `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`
6. 自审（占位符扫描、内部一致性、范围检查、歧义检查）
7. 用户审查写好的 spec
8. 调用 writing-plans 技能

**关键原则：**
- 一次一个问题——不要用多个问题淹没用户
- YAGNI 无情——从所有设计中移除不必要的功能
- 增量验证——逐节展示，逐节确认
- 大项目先分解——如果项目太大，先分解为子项目，每个子项目独立走 spec→plan→实现

**可视化伴侣（Visual Companion）：**
- 浏览器中显示 mockup、图表、视觉对比
- 不是模式，是工具——按需使用
- 只在"展示比描述更清晰"时提出（如布局对比、架构图）
- 概念性问题用终端（如"这个上下文中的 X 是什么意思？"）

**终态：** 唯一可以调用的下一个技能是 writing-plans。

### 4. writing-plans — 编写实现计划

**作用：** 将设计文档拆解为可执行的任务计划。

**假设：** 执行计划的工程师对代码库零上下文，品味堪忧。

**任务粒度：每步一个动作（2-5 分钟）**
```
- "写失败测试" — 一步
- "运行确认失败" — 一步
- "写最小实现代码" — 一步
- "运行确认通过" — 一步
- "提交" — 一步
```

**计划文档头部（必需）：**
```markdown
# [Feature Name] Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development

**Goal:** [一句话描述]
**Architecture:** [2-3 句方法]
**Tech Stack:** [关键技术]

## Global Constraints
[项目级约束——版本底线、依赖限制、命名规则]
```

**任务结构：**
```markdown
### Task N: [Component Name]

**Files:**
- Create: `exact/path/to/file.py`
- Modify: `exact/path/to/existing.py:123-145`
- Test: `tests/exact/path/to/test.py`

**Interfaces:**
- Consumes: [从早期任务使用什么——精确签名]
- Produces: [后续任务依赖什么——精确函数名、参数和返回类型]

- [ ] Step 1: Write the failing test
[实际测试代码]
- [ ] Step 2: Run test to verify it fails
[运行命令 + 预期输出]
- [ ] Step 3: Write minimal implementation
[实际代码]
- [ ] Step 4: Run test to verify it passes
- [ ] Step 5: Commit
```

**禁止占位符：**
- "TBD"、"TODO"、"稍后实现"
- "添加适当的错误处理"（没有实际代码）
- "类似 Task N"（必须重复代码，工程师可能乱序读）
- 引用未在任何任务中定义的类型/函数/方法

**自审：**
1. Spec 覆盖：每个 spec 要求都能指到对应任务
2. 占位符扫描：搜索红旗模式
3. 类型一致性：Task 3 的 `clearLayers()` 和 Task 7 的 `clearFullLayers()` 是 bug

**执行交接：**
- Subagent-Driven（推荐）：每任务新子 Agent + 双重审查
- Inline Execution：executing-plans 批量执行 + 检查点

---

## 三、执行（Execution）

### 5. subagent-driven-development（SDD）— 子 Agent 驱动开发

**作用：** 逐任务派发新子 Agent 执行，每个任务后双重审查（规范合规 + 代码质量）。

**为什么用子 Agent：**
- 隔离上下文：子 Agent 不继承会话历史，你精确构造它需要的信息
- 保护主上下文：大量工具输出不进入你的上下文
- 专注：子 Agent 只看到它的任务，不会被其他任务干扰

**核心流程：**
```
读计划，创建 todos
  → 每个任务：
    1. 运行 task-brief 提取任务全文到文件
    2. 派发实现者子 Agent（附 brief 路径 + 报告路径 + 上下文）
    3. 实现者实现、测试、提交、自审
    4. 运行 review-package 生成 diff 包
    5. 派发任务审查者子 Agent（附 brief + 报告 + diff 包）
    6. 审查者报告：规范 ✅/❌ + 质量 ✅/❌
    7. 有问题 → 派发修复子 Agent → 重新审查
    8. 通过 → 标记完成，记录进度账本
  → 所有任务完成后：
    派发最终全分支审查者
    → 调用 finishing-a-development-branch
```

**模型选择策略：**
- 机械实现任务（1-2 文件，完整 spec）→ 便宜快速模型
- 集成判断任务（多文件协调）→ 标准模型
- 架构设计任务 → 最强模型
- 始终显式指定模型，否则继承会话默认（最贵）模型

**文件交接（File Handoffs）：**
- **Task brief**：`scripts/task-brief PLAN_FILE N` 提取任务到文件
- **Report file**：实现者写报告到文件，只返回状态+摘要
- **Reviewer inputs**：brief + 报告 + diff 包，三个文件路径
- 一切通过文件传递，不粘贴大段文本到 dispatch prompt

**持久化进度（Durable Progress）：**
```
.superpowers/sdd/progress.md  ← 进度账本
```
- 会话压缩后上下文会丢失，账本是恢复地图
- 完成的任务标记在账本中，压缩后不重新派发
- 信任账本和 `git log`，不信任自己的记忆

**处理实现者状态：**
- **DONE**：生成 review package，派发审查者
- **DONE_WITH_CONCERNS**：读疑虑，正确性/范围问题先解决，观察性问题继续审查
- **NEEDS_CONTEXT**：提供缺失上下文，重新派发
- **BLOCKED**：评估阻塞——上下文问题→加信息，推理问题→换更强模型，任务太大→拆分，计划错误→升级人类

**红旗（绝不）：**
- 在 main/master 上开始实现（未经用户同意）
- 跳过任务审查
- 并行派发多个实现子 Agent（会冲突）
- 让子 Agent 读整个计划文件（给它 task brief）
- 接受"差不多"的规范合规
- 告诉审查者"不要标记 X"（预判发现是违规的）

### 6. executing-plans — 批量执行计划

**作用：** 在没有子 Agent 支持时，在当前会话中批量执行计划。

**流程：**
1. 加载计划，批判性审查，有疑虑先提出
2. 逐任务执行：标记 in_progress → 按步骤执行 → 运行验证 → 标记完成
3. 全部完成后调用 finishing-a-development-branch

**何时停止：**
- 遇到阻塞（缺依赖、测试失败、指令不清）
- 计划有关键缺口
- 不理解指令
- 验证反复失败

> 技能明确说明：如果有子 Agent 支持，用 SDD 而不是这个。

### 7. dispatching-parallel-agents — 并行派发

**作用：** 面对 2+ 个独立任务时，并行派发子 Agent。

**使用条件：**
- 3+ 个测试文件失败，根因不同
- 多个子系统独立故障
- 每个问题可以不需要其他问题的上下文理解
- 调查之间无共享状态

**不使用：**
- 故障相关（修一个可能修复其他）
- 需要理解完整系统状态
- Agent 会互相干扰（编辑同一文件、使用同一资源）

**模式：**
1. 按故障分组（每个域一个 Agent）
2. 每个 Agent 获得：具体范围、明确目标、约束、期望输出
3. 在同一响应中派发所有 Agent → 并行执行
4. 审查每个摘要，检查冲突，运行全套测试

**关键：** 一个响应中多个 dispatch 调用 = 并行。每响应一个 = 顺序。

---

## 四、测试与质量（Testing & Quality）

### 8. test-driven-development（TDD）— 测试驱动开发

**铁律：**
```
NO PRODUCTION CODE WITHOUT A FAILING TEST FIRST
```

先写了代码？删掉。从头来。
- 不要留作"参考"
- 不要"改改"它来写测试
- 不要看它
- 删除就是删除

**RED-GREEN-REFACTOR 循环：**

```
RED：写一个最小失败测试
  → 验证失败（MANDATORY，绝不跳过）
  → 确认：测试失败（不是 error），失败信息符合预期，因为功能缺失而失败

GREEN：写最小代码通过测试
  → 验证通过（MANDATORY）
  → 确认：测试通过，其他测试也通过，输出干净

REFACTOR：清理
  → 去重复、改善命名、提取辅助函数
  → 保持绿色，不加行为
```

**好测试的标准：**
- 最小：测一件事。名字里有"and"？拆分。
- 清晰：名字描述行为
- 展示意图：展示期望的 API
- 真实代码：除非不可避免，不用 mock

**为什么顺序重要：**

"我先写代码后补测试" → 测试立即通过，什么也证明不了：
- 可能测错了东西
- 可能测了实现而非行为
- 可能遗漏了边界情况
- 你从没看到它捕获 bug

先写测试强制你看到测试失败，证明它确实在测东西。

**常见合理化：**

| 借口 | 现实 |
|------|------|
| "太简单不用测" | 简单代码也会坏。测试 30 秒。 |
| "我之后补测" | 立即通过的测试什么也证明不了 |
| "删除 X 小时工作是浪费" | 沉没成本谬误。保留不可信代码才是技术债 |
| "TDD 是教条，我是务实派" | TDD 就是务实：提交前找 bug 比之后调试快 |
| "这不一样因为..." | 所有这些意味着：删代码，TDD 重来 |

### 9. systematic-debugging — 系统化调试

**铁律：**
```
NO FIXES WITHOUT ROOT CAUSE INVESTIGATION FIRST
```

没完成 Phase 1，不能提修复方案。

**四个阶段：**

**Phase 1：根因调查**
1. 仔细读错误信息（不要跳过，通常包含解决方案）
2. 一致复现（不能稳定复现→收集更多数据，不要猜）
3. 检查最近变更（git diff、新依赖、配置变更）
4. 多组件系统中收集证据（在每个组件边界日志输入输出）
5. 追踪数据流（坏值从哪来？谁调用的？一直追到源头）

**Phase 2：模式分析**
1. 找工作中的类似代码
2. 完整读参考实现（不要略读）
3. 列出工作版本和故障版本的每个差异
4. 理解依赖

**Phase 3：假设与测试**
1. 形成单一假设："我认为 X 是根因因为 Y"
2. 最小化测试（一次一个变量）
3. 验证后继续，失败→新假设（不要叠加修复）

**Phase 4：实现**
1. 创建失败测试用例
2. 实现单一修复（一个改动，不捆绑重构）
3. 验证修复
4. 修复不工作→停止。尝试 <3 次→回 Phase 1。≥3 次→质疑架构

**3 次修复失败规则：**
- 每次修复揭示新问题→这是架构问题，不是假设失败
- 停下来质疑基础：模式是否合理？是否因惯性坚持？
- 与人类伙伴讨论后再尝试

**红旗：**
- "先快速修复，之后调查"
- "试试改 X 看看行不行"
- "一次改多个，跑测试"
- "我大概知道是 X，让我修一下"
- 提出方案前没追踪数据流
- 已试 2+ 次还说"再试一次"

### 10. verification-before-completion — 完成前验证

**铁律：**
```
NO COMPLETION CLAIMS WITHOUT FRESH VERIFICATION EVIDENCE
```

在这条消息中没运行验证命令，就不能声称它通过了。

**门函数：**
```
声称任何状态或表达满意之前：
1. IDENTIFY：什么命令证明这个声称？
2. RUN：执行完整命令（新鲜的、完整的）
3. READ：完整输出，检查退出码，数失败数
4. VERIFY：输出确认了声称？
   - 否 → 陈述实际状态和证据
   - 是 → 带证据陈述声称
5. ONLY THEN：做出声称

跳过任何步骤 = 说谎，不是验证
```

**常见失败：**

| 声称 | 需要 | 不够 |
|------|------|------|
| 测试通过 | 测试命令输出：0 失败 | 之前的结果、"应该通过" |
| Build 成功 | build 命令：exit 0 | linter 通过、"日志看起来不错" |
| Bug 修复 | 测试原始症状：通过 | 改了代码、"假设修好了" |
| Agent 完成 | VCS diff 显示变更 | Agent 报告"成功" |

**红旗：**
- 用 "should"、"probably"、"seems to"
- 验证前表达满意（"Great!"、"Done!"）
- 信任 Agent 成功报告
- 依赖部分验证

---

## 五、协作与工作流（Collaboration & Workflow）

### 11. requesting-code-review — 请求代码审查

**作用：** 派发代码审查子 Agent，在问题级联前捕获。

**何时审查：**
- 必须审查：SDD 中每个任务后、大功能完成后、merge 前必须审查
- 可选但有价值：卡住时（新视角）、重构前（基线）、复杂 bug 修复后

**如何请求：**
1. 获取 git SHA：`BASE_SHA` 和 `HEAD_SHA`
2. 派发 general-purpose 子 Agent，填充 code-reviewer.md 模板
3. 按反馈行动：
   - Critical → 立即修复
   - Important → 继续前修复
   - Minor → 记录稍后处理
   - 审查者错了 → 用技术理由反驳

### 12. receiving-code-review — 接收代码审查

**作用：** 接收审查反馈时的正确姿态——技术严谨，不是表演性同意。

**响应模式：**
```
1. READ：完整读反馈，不反应
2. UNDERSTAND：用自己的话复述需求（或提问）
3. VERIFY：对照代码库现实检查
4. EVALUATE：对这个代码库技术合理？
5. RESPOND：技术确认或有理由的反驳
6. IMPLEMENT：一次一项，每项测试
```

**禁止的响应：**
- "You're absolutely right!"（表演性同意）
- "Great point!" / "Excellent feedback!"
- "Let me implement that now"（验证之前）

**正确的确认：**
- "Fixed. [简述改了什么]"
- "Good catch - [具体问题]. Fixed in [位置]."
- 直接修了展示在代码里

**何时反驳：**
- 建议破坏现有功能
- 审查者缺少完整上下文
- 违反 YAGNI（未使用的功能）
- 对当前技术栈技术不正确
- 与人类伙伴的架构决策冲突

**YAGNI 检查：**
```
审查者说"要规范实现"
→ grep 代码库看是否有人调用
→ 没人调用 → "这个 endpoint 没人调用，移除它（YAGNI）？"
→ 有人调用 → 那就规范实现
```

### 13. using-git-worktrees — 使用 Git Worktree

**作用：** 确保工作在隔离工作空间进行。

**Step 0：检测现有隔离**
- `GIT_DIR != GIT_COMMON`（且不是 submodule）→ 已在 worktree，跳过创建
- submodule 守卫：`git rev-parse --show-superproject-working-tree`

**Step 1：创建隔离工作空间**
- 1a. 优先用平台原生 worktree 工具（如 `EnterWorktree`）
- 1b. 回退到 `git worktree add`
  - 目录优先级：用户指令 > 已有 `.worktrees/` > 默认 `.worktrees/`
  - 必须验证目录在 `.gitignore` 中
  - 创建后 cd 到新目录

**Step 2：项目 setup**
- 自动检测 package.json / Cargo.toml / requirements.txt / go.mod 并安装依赖

**Step 3：验证干净基线**
- 运行测试套件
- 失败→报告并询问是否继续
- 通过→报告就绪

**常见错误：**
- 有原生工具却用 `git worktree add`（#1 错误）
- 跳过检测在已有 worktree 里创建嵌套 worktree
- 不验证 .gitignore 就创建
- 跳过基线测试验证

### 14. finishing-a-development-branch — 完成开发分支

**作用：** 实现完成后，引导收尾工作。

**流程：**
1. **验证测试** — 测试不过就不继续
2. **检测环境** — normal repo / worktree / detached HEAD
3. **确定 base 分支**
4. **提供选项**（normal repo 和 worktree 4 选项；detached HEAD 3 选项）

**4 个选项：**

| 选项 | merge | push | 保留 worktree | 清理分支 |
|------|-------|------|:---:|:---:|
| 1. 本地 merge | ✅ | — | — | ✅ |
| 2. push + PR | — | ✅ | ✅ | — |
| 3. 保留原样 | — | — | ✅ | — |
| 4. 丢弃 | — | — | — | ✅(force) |

**清理规则：**
- 只清理 Superpowers 创建的 worktree（`.worktrees/` 或 `worktrees/` 下）
- 不清理平台创建的 worktree
- Option 2/3 不清理（用户需要迭代 PR 反馈）
- Option 4 需要输入 "discard" 确认
- `git worktree remove` 前必须 cd 到主仓库根目录

---

## 技能间的依赖关系

```
using-superpowers（所有技能的入口）
  ├─ brainstorming
  │   └─ writing-plans
  │       ├─ subagent-driven-development
  │       │   ├─ test-driven-development（子 Agent 使用）
  │       │   ├─ requesting-code-review（每任务后 + 最终审查）
  │       │   └─ finishing-a-development-branch
  │       └─ executing-plans
  │           └─ finishing-a-development-branch
  ├─ using-git-worktrees（brainstorming 后、执行前）
  ├─ systematic-debugging（任何 bug/测试失败时）
  │   └─ test-driven-development（Phase 4 创建失败测试）
  ├─ verification-before-completion（任何完成声称前）
  ├─ dispatching-parallel-agents（独立任务并行）
  ├─ receiving-code-review（收到反馈时）
  └─ writing-skills（创建/修改技能时）
       └─ test-driven-development（技能测试的基础）
```
