# 04 - 端到端开发工作流

本文追踪一个功能从想法到合并的完整生命周期，展示所有技能如何串联工作。

## 全景流程图

```
用户："做个 X"
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│ 1. brainstorming                                         │
│    探索上下文 → 逐个提问 → 2-3 方案 → 分节设计           │
│    → 写 spec 文档 → 自审 → 用户审查                      │
│    输出：docs/superpowers/specs/YYYY-MM-DD-<topic>.md   │
└──────────────────────────┬──────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────┐
│ 2. using-git-worktrees                                  │
│    检测现有隔离 → 创建 worktree → 项目 setup            │
│    → 验证测试基线                                       │
│    输出：隔离工作空间 + 干净测试基线                     │
└──────────────────────────┬──────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────┐
│ 3. writing-plans                                        │
│    文件结构规划 → 任务拆解（2-5 分钟/步）               │
│    → 每任务含精确路径+完整代码+验证步骤                  │
│    → 自审（spec 覆盖+占位符+类型一致性）                 │
│    输出：docs/superpowers/plans/YYYY-MM-DD-<feature>.md │
└──────────────────────────┬──────────────────────────────┘
                           ▼
                    用户选择执行方式
              ┌────────────┴────────────┐
              ▼                         ▼
┌──────────────────────┐  ┌──────────────────────────┐
│ subagent-driven      │  │ executing-plans          │
│ （推荐，有子 Agent）  │  │ （无子 Agent 时）        │
└──────────┬───────────┘  └────────────┬─────────────┘
           ▼                          ▼
┌─────────────────────────────────────────────────────────┐
│ 4. 逐任务执行（以 SDD 为例）                             │
│                                                         │
│  Task 1 ──────────────────────────────────────────      │
│  │ 派发实现者子 Agent                                   │
│  │  └─ TDD: RED(写失败测试) → GREEN(最小实现) →        │
│  │           REFACTOR(重构)                             │
│  │  └─ 自审 + 提交                                      │
│  │ 派发审查者子 Agent                                   │
│  │  └─ 规范合规 ✅ + 代码质量 ✅                        │
│  │  └─ 有问题 → 修复子 Agent → 重新审查                 │
│  │ 记录进度账本                                         │
│  │                                                     │
│  Task 2 ... Task N（同上）                              │
│                                                         │
│  最终全分支审查                                         │
└──────────────────────────┬──────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────┐
│ 5. finishing-a-development-branch                       │
│    验证测试 → 检测环境 → 提供 4 选项                     │
│    → 执行选择 → 清理 worktree                           │
│    输出：merge / PR / 保留 / 丢弃                       │
└─────────────────────────────────────────────────────────┘
```

## 阶段一：Brainstorming（设计）

### 触发

用户说"做个 X"、"加个 Y 功能"、"改一下 Z 的行为"。

### 实际对话示例

```
用户：Let's make a react todo list

Agent：Using brainstorming to design this feature.

[探索项目上下文：检查文件、文档、最近提交]

Agent：I see this is a fresh project. Before I design the todo list,
a few questions.

First: Should this be a single-page app or part of a larger application?

用户：Single-page is fine.

Agent：Got it. Next: Do you need persistence (localStorage, API, none)?

用户：localStorage.

Agent：Great. Here are two approaches:

  A. Single component with inline state (recommended) — simplest,
     fits a single-page todo list perfectly
  B. Component + custom hook (useTodos) — separates logic from UI,
     better if you plan to add features later

I recommend A for now. We can always extract a hook if needed (YAGNI).

用户：A sounds good.

Agent：Here's the design.

## Architecture
Single React component managing todo state with useState.
Todos persisted to localStorage on every change.

## Components
- App: renders input, list, filter buttons
- TodoItem: single todo with toggle/delete

## Data Flow
App → (props) → TodoItem
App ← (callbacks) ← TodoItem

Does this look right so far?

用户：Yes.

Agent：## Error Handling
- Empty input: ignore (no error)
- localStorage unavailable: catch, console.warn, continue in-memory

## Testing
- Component tests with React Testing Library
- Test: add, toggle, delete, filter, persist

Does this look right?

用户：Looks good.

[写 spec 到 docs/superpowers/specs/2026-01-15-react-todo-list-design.md]
[自审：无占位符、无矛盾、范围合适、无歧义]
[提交到 git]

Agent：Spec written and committed. Please review it and let me know
if you want any changes before we start the implementation plan.

用户：Approved.

Agent：[调用 writing-plans 技能]
```

### 关键约束

- **硬门禁**：设计批准前不写任何代码
- **一次一个问题**：不要一次问多个
- **YAGNI**：从设计中移除一切不必要的
- **终态**：唯一可以调用的下一个技能是 writing-plans

## 阶段二：Git Worktree（隔离）

### 触发

brainstorming 完成后，writing-plans 之前（或执行前）。

### 流程

```
Step 0: 检测
  GIT_DIR == GIT_COMMON → 普通 repo，需要创建 worktree
  GIT_DIR != GIT_COMMON → 已在 worktree，跳过

Step 1: 创建
  有原生工具（EnterWorktree）→ 用它
  没有 → git worktree add .worktrees/feature-branch -b feature-branch
  → 验证 .worktrees 在 .gitignore 中

Step 2: 项目 setup
  npm install / cargo build / pip install / go mod download

Step 3: 验证基线
  npm test → 全绿 → "Worktree ready, 0 failures"
  有失败 → 报告，询问是否继续
```

### 为什么重要

- 隔离工作：不影响当前分支
- 干净基线：新 bug 和已有 bug 可区分
- 安全实验：可以丢弃整个 worktree

## 阶段三：Writing Plans（规划）

### 触发

brainstorming 批准后。

### 计划示例

```markdown
# React Todo List Implementation Plan

> REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development

**Goal:** Single-page React todo list with localStorage persistence

**Architecture:** Single App component + TodoItem child, useState for state

**Tech Stack:** React 18, TypeScript, React Testing Library, Vitest

## Global Constraints
- React 18+
- TypeScript strict mode
- No external state management library

---

### Task 1: TodoItem component

**Files:**
- Create: `src/components/TodoItem.tsx`
- Test: `src/components/TodoItem.test.tsx`

**Interfaces:**
- Produces: `TodoItem({ todo, onToggle, onDelete })` component

- [ ] Step 1: Write the failing test

```tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { TodoItem } from './TodoItem'

test('renders todo text', () => {
  render(<TodoItem todo={{ id: 1, text: 'Buy milk', done: false }}
    onToggle={() => {}} onDelete={() => {}} />)
  expect(screen.getByText('Buy milk')).toBeInTheDocument()
})

test('calls onToggle when clicked', () => {
  const onToggle = vi.fn()
  render(<TodoItem todo={{ id: 1, text: 'Buy milk', done: false }}
    onToggle={onToggle} onDelete={() => {}} />)
  fireEvent.click(screen.getByRole('checkbox'))
  expect(onToggle).toHaveBeenCalledWith(1)
})
```

- [ ] Step 2: Run test to verify it fails
- [ ] Step 3: Write minimal implementation
- [ ] Step 4: Run test to verify it passes
- [ ] Step 5: Commit

### Task 2: App component with add functionality
### Task 3: Toggle and delete
### Task 4: localStorage persistence
### Task 5: Filter buttons
```

### 关键约束

- **每步 2-5 分钟**：bite-sized
- **无占位符**：每步含实际代码
- **精确路径**：始终用精确文件路径
- **Interfaces 块**：声明任务间依赖的精确签名

## 阶段四：SDD 执行

### 触发

计划写好后，用户选择执行方式。

### SDD 详细流程

```
读计划 → 创建所有任务的 todos
  → 预飞行计划审查（扫描冲突）

Task 1:
  1. 运行 scripts/task-brief PLAN_FILE 1 → 提取任务全文到文件
  2. 派发实现者子 Agent：
     - "这是项目中的位置"
     - "读 task-1-brief.md，它是你的需求"
     - "早期任务的接口和决策"
     - "报告写到 task-1-report.md"
  3. 实现者工作：
     - 读 brief
     - 有问题？→ 问控制器（控制器回答后重新派发）
     - TDD: RED → GREEN → REFACTOR
     - 自审
     - 提交
     - 写报告到文件
     - 返回状态（DONE / DONE_WITH_CONCERNS / NEEDS_CONTEXT / BLOCKED）
  4. 控制器：运行 scripts/review-package BASE HEAD → 生成 diff 包
  5. 派发审查者子 Agent：
     - brief 路径 + 报告路径 + diff 包路径
     - 全局约束
  6. 审查者返回：
     - 规范合规 ✅/❌（是否做了该做的，没做不该做的）
     - 代码质量 ✅/❌（实现是否良好）
     - 发现：Critical / Important / Minor
  7. Critical/Important → 派发修复子 Agent（附所有发现）
     → 修复者修+跑测试+报告
     → 重新审查
  8. 通过 → 在 progress.md 记录 "Task 1: complete (commits abc..def, review clean)"
  9. 标记 todo 完成

Task 2 ... Task N：同上

全部完成 → 派发最终全分支审查者
  → 使用 code-reviewer.md 模板
  → 审查整个分支的 diff
  → 有发现 → 一个修复子 Agent 处理所有发现
```

### 进度账本

```
# .superpowers/sdd/progress.md

Task 1: complete (commits a7981ec..3df7661, review clean)
Task 2: complete (commits 3df7661..8b2c4f9, review clean, 1 Minor deferred)
Task 3: complete (commits 8b2c4f9..d1e5a3b, review clean)
```

会话压缩后，读账本恢复进度，不重新派发已完成的任务。

### 文件交接的核心思想

**一切通过文件传递，不粘贴大段文本。**

```
控制器 → 实现者：task-brief 文件（需求）+ report 文件路径（报告写到哪）
实现者 → 控制器：report 文件（完整报告）+ 返回消息（状态+摘要）
控制器 → 审查者：brief 文件 + report 文件 + review-package 文件
```

原因：粘贴到 dispatch prompt 的所有文本会常驻控制器上下文整个会话，每个后续 turn 都会重新读取。一个真实会话的 dispatch 达到 42k 字符，其中 99% 是粘贴的历史。

## 阶段五：Finishing

### 触发

所有任务完成 + 最终审查通过。

### 流程

```
1. 验证测试
   npm test → 全绿 → 继续
   有失败 → 停止，不继续

2. 检测环境
   GIT_DIR == GIT_COMMON → normal repo，4 选项
   GIT_DIR != GIT_COMMON, named branch → worktree，4 选项
   detached HEAD → 3 选项（无 merge）

3. 提供选项
   Implementation complete. What would you like to do?
   1. Merge back to main locally
   2. Push and create a Pull Request
   3. Keep the branch as-is
   4. Discard this work

4. 执行选择
   Option 1: checkout main → merge → 验证测试 → 删 worktree → 删分支
   Option 2: push -u origin → 不删 worktree（PR 迭代需要）
   Option 3: 不做任何事
   Option 4: 确认 "discard" → 删 worktree → force delete 分支

5. 清理
   只清理 .worktrees/ 或 worktrees/ 下的 worktree
   cd 到主仓库根目录再 git worktree remove
   git worktree prune
```

## 横切关注点

### TDD 贯穿全程

TDD 不是独立阶段，而是每个实现步骤的执行方式：

```
每个 Task 的每个 Step：
  RED：写失败测试 → 运行 → 确认失败
  GREEN：写最小代码 → 运行 → 确认通过
  REFACTOR：清理 → 运行 → 确认仍通过
  COMMIT：提交
```

### Verification 在每个声称前

```
"测试通过" → 必须运行测试命令，看到 0 failures
"Bug 修复" → 必须运行原始症状测试，看到通过
"构建成功" → 必须运行 build，看到 exit 0
"任务完成" → 必须对照计划逐项检查
```

### Systematic Debugging 在任何故障时

```
测试失败 / Bug / 异常行为
  → Phase 1: 根因调查（读错误、复现、查变更、收集证据）
  → Phase 2: 模式分析（找工作的类似代码、对比差异）
  → Phase 3: 假设与测试（单一假设、最小测试）
  → Phase 4: 实现（失败测试、单一修复、验证）
  → 3 次失败 → 质疑架构
```

## 工作流变体

### 小任务（单文件修改）

不需要完整流程：
```
brainstorming（简短设计，几句话）
→ TDD（直接实现）
→ verification-before-completion
```

### 大项目（多子系统）

```
brainstorming
  → 识别为多子系统
  → 分解为子项目
  → 每个子项目独立走：
     spec → plan → SDD → finish
  → 按依赖顺序实施
```

### Bug 修复

```
systematic-debugging
  → Phase 4: 创建失败测试（TDD）
  → 修复
  → verification-before-completion
  → requesting-code-review（如果复杂）
```

### 并行任务

```
dispatching-parallel-agents
  → 识别独立域
  → 每域一个子 Agent
  → 并行执行
  → 审查整合
```
