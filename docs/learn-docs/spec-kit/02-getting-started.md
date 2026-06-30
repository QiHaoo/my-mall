# 02 - 快速上手：安装到跑通完整流程

> 本文回答：怎么装 Spec Kit？怎么初始化项目？完整工作流有哪些命令、各产出什么？跑一遍长什么样？
> 主要来源：`README.md`、`docs/quickstart.md`、`docs/reference/core.md`、`templates/commands/`。

## 1. 前置条件

| 依赖 | 说明 |
|------|------|
| Python 3.11+ | CLI 是 Python 写的 |
| uv（推荐）或 pipx | 包管理，推荐 uv |
| Git | Spec Kit 强依赖分支驱动 feature |
| 一个 AI 编码代理 | Copilot / Claude Code / Gemini CLI / Cursor 等，30+ 选一个 |

> Spec Kit 本身不写代码——它生成结构化上下文和斜杠命令文件，由你选的 AI 代理来执行。所以"装代理 CLI"是前置（IDE 类代理如 Copilot 不需要单独 CLI）。

## 2. 安装 Specify CLI

推荐用 `uv tool install`（持久化安装到独立环境，不污染系统 Python）：

```bash
# 把 vX.Y.Z 换成 Releases 页面的最新 tag
uv tool install specify-cli --from git+https://github.com/github/spec-kit.git@vX.Y.Z
```

装完后验证：

```bash
$ specify --version
# 或 specify version（更详细，含 Python/平台信息）
```

### 升级

Spec Kit 自带自管理命令：

```bash
specify self check          # 只检查有没有新版本，不改任何东西
specify self upgrade --dry-run   # 预览会跑什么，不真升级
specify self upgrade        # 升级到最新稳定版（自动识别 uv tool / pipx）
specify self upgrade --tag vX.Y.Z  # 钉死某个版本
```

> 裸 `specify self upgrade` 立即执行，行为类似 `pip install -U`。`uvx`（临时运行）和源码 checkout 会被识别并给出路径提示，而不是跑安装器。

## 3. 初始化项目

```bash
# 新建项目目录
specify init my-project --integration copilot
cd my-project

# 或在当前目录初始化
specify init --here --integration copilot
# 非空目录强制合并
specify init --here --force --integration copilot
```

关键参数（完整见 `docs/reference/core.md`）：

| 参数 | 作用 |
|------|------|
| `--integration <key>` | 指定 AI 代理（copilot/claude/gemini/codex…），省略则交互式选择，非交互默认 copilot |
| `--integration-options` | 代理专属选项，如 `--skills`（装成 skill 而非斜杠命令） |
| `--script sh\|ps` | 脚本类型：bash 或 PowerShell（CLI 按系统自动选，可强制） |
| `--here` / `.` | 在当前目录初始化，不新建子目录 |
| `--force` | 非空目录强制合并 |
| `--ignore-agent-tools` | 跳过代理 CLI 是否安装的检查 |
| `--preset <id>` | 初始化时同时装一个 preset |

### init 之后会发生什么

`specify init` 在项目里生成 `.specify/` 目录（受管项目文件）：

```text
.
├── .specify/
│   ├── memory/
│   │   └── constitution.md        # 项目宪法（先空模板，由 /speckit.constitution 填）
│   ├── scripts/                    # 自动化脚本（bash + powershell 两套）
│   │   ├── bash/  和  powershell/
│   │       ├── check-prerequisites.sh/.ps1
│   │       ├── common.sh/.ps1
│   │       ├── create-new-feature.sh/.ps1
│   │       ├── setup-plan.sh/.ps1
│   │       └── setup-tasks.sh/.ps1
│   └── templates/                  # 核心模板
│       ├── constitution-template.md
│       ├── spec-template.md
│       ├── plan-template.md
│       └── tasks-template.md
└── .claude/commands/  或  .github/  等   # 取决于 integration，装斜杠命令文件
```

同时把 `/speckit.*` 斜杠命令文件装进代理对应的目录（如 Claude 是 `.claude/commands/`、Copilot 是 `.github/prompts/` + `.agent.md`）。具体目录映射见 06 篇。

> **注意**：Git 仓库初始化和分支管理由 **git 扩展** 负责，默认不装。需要的话 init 后跑 `specify extension add git`。

## 4. 完整工作流：9 个命令

这是 Spec Kit 的核心。按官方推荐顺序（`docs/quickstart.md`）：

```text
/speckit.constitution
  → /speckit.specify
  → /speckit.clarify
  → /speckit.plan
  → /speckit.checklist
  → /speckit.tasks
  → /speckit.analyze
  → /speckit.implement
  → /speckit.converge
```

分两组理解：**核心 5 个**（constitution/specify/plan/tasks/implement）是必走主线；**质量门 4 个**（clarify/checklist/analyze/converge）是生产级特性的质量保障。

### 4.1 核心命令

| 命令 | 作用 | 产出 |
|------|------|------|
| `/speckit.constitution` | 建立项目治理原则与开发准则 | `.specify/memory/constitution.md` |
| `/speckit.specify` | 定义"做什么/为什么"（需求+用户故事） | `specs/{###-feature}/spec.md` + 新建 feature 分支 |
| `/speckit.plan` | 给技术栈与架构，生成实现计划 | `specs/{###-feature}/plan.md` + `research.md` + `data-model.md` + `contracts/` + `quickstart.md` |
| `/speckit.tasks` | 从 plan 拆出可执行任务清单 | `specs/{###-feature}/tasks.md` |
| `/speckit.implement` | 按 tasks.md 执行实现 | 实际代码 |

### 4.2 质量门命令（可选但推荐）

| 命令 | 时机 | 作用 |
|------|------|------|
| `/speckit.clarify` | specify 之后、plan 之前 | 结构化澄清歧义，把 `[NEEDS CLARIFICATION]` 逐条解决，记录到 Clarifications 段 |
| `/speckit.checklist` | plan 之后 | 生成质量检查清单，验证需求完整性/清晰度/一致性（"英文的单元测试"） |
| `/speckit.analyze` | tasks 之后、implement 之前 | 跨产物一致性分析（spec/plan/tasks 三者对齐），可重复跑 |
| `/speckit.converge` | implement 之后 | 对照 spec/plan/tasks 评估代码库，把未完成工作追加为新 task；有新 task 就再 implement，直到收敛 |

### 4.3 顺序背后的逻辑

```
constitution  ── 给全局约束（宪法），后面所有阶段都要遵守
specify        ── 只写 what/why，严禁 how（避免提前绑定技术）
clarify        ── 把模糊处逼出来显式化（减少下游返工）
plan           ── 这时才引入技术栈（how），受 constitution 约束
checklist      ── 给需求做"单元测试"
tasks          ── 从 plan 派生任务，带依赖与 [P] 并行标记
analyze        ── 实现前最后一次跨产物对齐
implement      ── 真正写代码
converge       ── 实现后查漏补缺，循环到收敛
```

> **关键纪律**：specify 阶段不谈技术栈，plan 阶段才谈。这保证规范与技术解耦，未来换技术栈时规范能复用。

## 5. 一个完整示例（Taskify）

以官方文档的 Taskify（团队生产力平台）为例，走一遍。

### Step 1：宪法

```
/speckit.constitution Taskify 是"安全优先"应用，所有用户输入必须校验。用微服务架构，代码必须完整文档化。
```

产出 `.specify/memory/constitution.md`，后续 plan/implement 都会引用它。

### Step 2：specify

```
/speckit.specify 开发 Taskify，团队生产力平台。允许创建项目、加成员、分配任务、评论、看板拖拽。
预定义 5 个用户（1 PM + 4 工程师），3 个示例项目，标准看板列（To Do/In Progress/In Review/Done）。
本阶段无登录……
```

这一步会：

1. **自动编号**：扫描已有 specs，确定下一个编号（001、002…自动扩位）
2. **建分支**：从描述生成语义分支名（如 `001-create-taskify`）
3. **按模板生成** `specs/001-create-taskify/spec.md`

### Step 3：clarify（关键，别跳）

```
/speckit.clarify 我要澄清任务卡细节：每个任务卡可在看板列间改状态、可无限评论、可分配有效用户。
启动时列出 5 个用户供选，无密码，点用户进主视图显示项目列表……
```

> 官方反复强调：**别把 AI 的第一版当最终版**。clarify 是把"AI 猜的"变成"你确认的"。

### Step 4：plan

```
/speckit.plan 用 .NET Aspire + Postgres，前端 Blazor server 拖拽看板实时更新，REST API（projects/tasks/notifications）。
```

产出多个文件：

```text
specs/001-create-taskify/
├── spec.md          # 已有
├── plan.md          # 实现计划
├── research.md      # 技术调研（库选型、版本）
├── data-model.md    # 数据模型
├── contracts/       # API 契约
│   ├── api-spec.json
│   └── signalr-spec.md
└── quickstart.md    # 关键验证场景
```

> 研究文档 `research.md` 要重点看——AI 可能调研偏了（比如泛泛研究 .NET Aspire 而非具体问题），可以引导它"先列要研究的问题，再为每个问题开并行调研子任务"。

### Step 5：checklist + validate plan

```
/speckit.checklist
```

让 AI 跑一遍实现计划的审计，找盲点。还要检查是否有**过度设计**（AI 容易过度热情加你没要的东西）。

### Step 6：tasks

```
/speckit.tasks
```

产出 `tasks.md`，结构化任务清单（见 03 篇模板详解）：按 user story 分组、带 `[P]` 并行标记、依赖顺序、文件路径、可选测试任务。

### Step 7：analyze + implement

```
/speckit.analyze      # 实现前跨产物对齐
/speckit.implement    # 执行
```

`/speckit.implement` 会：校验前置产物齐备 → 解析 tasks.md → 按依赖与并行标记执行 → TDD（若任务含测试）→ 进度更新与错误处理。

> **重要**：实现时代理会执行本地 CLI（dotnet/npm/…），相关工具链得装好。运行时错误（如浏览器控制台报错）可能不在 CLI 日志里，要手动粘回代理解决。

### Step 8：converge（收敛循环）

```
/speckit.converge
# 如果追加了新 task → /speckit.implement → /speckit.converge，直到报告已收敛
```

## 6. 上下文感知：分支 = feature

Spec Kit 的命令会**根据当前 Git 分支自动定位 feature**。分支名 `001-create-taskify` 就对应 `specs/001-create-taskify/`。

> 切换 feature = 切换 Git 分支，无需额外配置。

非 Git 仓库也能用：设环境变量 `SPECIFY_FEATURE=001-photo-albums` 指定当前 feature。

### 相关环境变量（CI/非交互场景）

| 变量 | 作用 |
|------|------|
| `SPECIFY_INIT_DIR` | 从目录外指定项目根（含 `.specify/` 的目录） |
| `SPECIFY_FEATURE_DIRECTORY` | 覆盖当前 feature 目录（优先于 `.specify/feature.json`） |
| `SPECIFY_FEATURE` | 非 Git 仓库时指定 feature 目录名 |

> 两条独立轴：`SPECIFY_INIT_DIR` 选**项目**，`SPECIFY_FEATURE_DIRECTORY` 选项目内的**feature**。

## 7. 处理复杂 feature

大 feature 在 `/speckit.implement` 中途容易"迷失"——上下文窗口耗尽，开始忽略任务或幻觉。`docs/concepts/complex-features.md` 给了四种应对：

| 方法 | 做法 | 适用 |
|------|------|------|
| **限制每次任务数** | `/speckit.implement only execute tasks T001-T010, then stop` | 任何代理，最简单 |
| **按 phase 限制** | `/speckit.implement only execute the Setup phase, then stop` | 同上 |
| **委派子代理** | `/speckit.implement delegate each parallel [P] task to a sub-agent` | 支持子代理的代理（如 Copilot CLI） |
| **拆成多个小 spec** | 把大 feature 拆成独立子 feature，各自走完整流程 | 单 phase 都撑不住时 |

> 完成的任务在 `tasks.md` 里标 `[X]`，下次 implement 自动接着跑。大多数情况"限制任务数"就够。

## 8. 几个上手提醒

- **第一版不是最终版**：specify/plan 出来的东西，主动用 clarify、手动追问去打磨
- **盯过度设计**：AI 容易加你没要的组件，要它说明理由和来源
- **constitution 先行**：没宪法就 plan，等于没有约束基准
- **分阶段实现**：大项目别一次 implement 到底，按 phase 验证后继续
- **脚本两套**：`.specify/scripts/` 下 bash 和 powershell 各一份，CLI 按系统选，可 `--script` 强制

## 9. 小结

- 安装：`uv tool install specify-cli`；初始化：`specify init --here --integration <代理>`
- 完整流程 9 个命令，核心 5 + 质量门 4，顺序有逻辑（what → how → 拆 → 实现 → 收敛）
- 产物集中在 `specs/{feature}/` 下，分支即 feature
- 大 feature 靠"限制任务数/委派子代理/拆小 spec"应对上下文耗尽

下一篇 [03-templates-and-quality.md](./03-templates-and-quality.md) 拆开模板看：spec/plan/tasks 各长什么样、模板怎么把 LLM 产出"逼"向高质量。
