# 05 - 定制化体系：extensions / presets / bundles / workflows

> 本文回答：Spec Kit 的四套定制机制分别干什么？模板解析栈怎么工作？什么时候用哪个？怎么发布自己的？
> 主要来源：`docs/reference/{extensions,presets,bundles,workflows}.md`、`README.md` 的定制化章节、`extensions/`、`presets/`、`workflows/` 目录。

## 1. 四件套总览

Spec Kit 用四层机制满足不同粒度的定制需求：

| 机制 | 定位 | 改变什么 | 粒度 |
|------|------|---------|------|
| **Extension** | 加新能力 | 新增命令、模板、脚本 | 单个能力 |
| **Preset** | 改造现有 | 覆盖 core/extension 的模板、命令、术语 | 模板级 |
| **Bundle** | 角色化整套 | 把 extensions + presets + workflows + steps 打包成一次安装 | 团队/角色级 |
| **Workflow** | 编排多步 | 把命令/prompt/shell/门禁串成可暂停恢复的流程 | 流程级 |

> 还有第五个"项目本地覆盖"（`.specify/templates/overrides/`），是 preset 的轻量替代——单个项目一次性调整，不必做成完整 preset。

## 2. 模板解析栈（理解定制化的关键）

所有定制化都围绕一个**优先级栈**运转。Spec Kit 在运行时按栈自顶向下找文件，第一个命中就用：

```text
优先级（高 → 低）:
1. 项目本地覆盖    .specify/templates/overrides/       ← 一次性调整
2. preset         .specify/presets/{id}/templates/     ← 改造现有
3. extension      .specify/extensions/{id}/templates/  ← 加新能力
4. Spec Kit core  .specify/templates/                  ← 默认（03 篇讲的那套）
```

核心规则：

- **模板/脚本在运行时解析**：Spec Kit 走栈，第一个匹配胜出
- **命令在安装时物化**：`extension add` / `preset add` 时把命令文件写进代理目录（如 `.claude/commands/`）
- **同命令冲突**：最高优先级版本胜；移除时自动恢复次高版本
- **没有覆盖时**：用 core 默认

### 解析栈图示

```text
请求文件: plan-template.md
        ↓
[1] 项目本地覆盖?  ── 命中 → 用这个
        ↓ 未命中
[2] preset: compliance (priority 5)?  ── 命中 → 用这个
        ↓ 未命中
[3] preset: team-workflow (priority 10)?  ── 命中 → 用这个
        ↓ 未命中
[4] extension 文件?  ── 命中 → 用这个
        ↓ 未命中
[5] Spec Kit core  → 用默认
```

> preset 之间按 priority 排序，**数字小的优先**（先检查）。`specify preset resolve <name>` 可追踪某个文件最终用哪一层。

## 3. Extension：加新能力

### 3.1 是什么

Extension 引入 **core 没有的新命令和模板**——比如加 Jira 集成、实现后代码 review、V-Model 测试追溯、项目健康诊断。它扩展"Spec Kit 能做什么"。

### 3.2 命令

```bash
specify extension search [query]            # 搜索（--tag/--author/--verified 过滤）
specify extension add <name>                # 安装（--dev 本地目录 / --from 自定义URL / --priority 优先级）
specify extension list [--available|--all]  # 列已装/可用
specify extension info <name>               # 详情
specify extension update [<name>]           # 更新单个或全部
specify extension enable|disable <name>     # 启用/禁用（不删除）
specify extension remove <name>             # 卸载
specify extension set-priority <name> <N>   # 改优先级（数字小=高优先）
```

### 3.3 配置与目录

扩展安装后在 `.specify/extensions/<ext>/` 下，多数带配置文件：

```text
.specify/extensions/<ext>/
├── <ext>-config.yml           # 项目级配置（进版本控制）
├── <ext>-config.local.yml     # 本地覆盖（gitignore）
└── <ext>-config.template.yml  # 模板参考
```

配置合并顺序（后者覆盖前者）：扩展默认值 → 项目配置 → 本地覆盖 → 环境变量 `SPECKIT_<EXT>_*`。

### 3.4 目录里自带的扩展

仓库 `extensions/` 下有几个内置扩展可读源码学习：

| 扩展 | 作用 |
|------|------|
| `agent-context` | 管理代理上下文文件（CLAUDE.md 等）里的 Spec Kit 段，**全 opt-in**，init 不装 |
| `bug` | bug 评估/修复/测试命令（`speckit.bug.assess/fix/test`） |
| `git` | Git 工作流（commit/feature/initialize/remote/validate），init 不默认装 |
| `template` | 扩展开发模板（照着写自己的） |
| `selftest` | 自测命令 |

> **agent-context 的设计值得注意**：CLI 本身**完全不碰**上下文文件（CLAUDE.md / AGENTS.md / copilot-instructions.md），所有上下文逻辑都在这个 opt-in 扩展里。这保证了"不装就不动你的上下文文件"的干净边界。

## 4. Preset：改造现有

### 4.1 是什么

Preset **不加新能力**，而是覆盖 core 和 extension 的模板、命令、脚本、术语——比如强制 spec 模板带合规追溯段、用领域专属术语、本地化成另一种语言、给 plan 加安全 review 门禁。它定制"Spec Kit 产出的东西长什么样"。

### 4.2 命令

```bash
specify preset search [query]
specify preset add [<id>] [--dev <path> | --from <url>] [--priority <N>]
specify preset list
specify preset info <id>
specify preset resolve <name>          # 追踪某文件最终用哪层（调试用）
specify preset enable|disable <id>
specify preset remove <id>
specify preset set-priority <id> <N>
```

### 4.3 文件解析策略

Preset 提供三类文件：命令、模板、脚本。默认用 **replace** 策略（栈里第一个命中全量替代）。还支持组合策略：

| 策略 | 作用 |
|------|------|
| **replace**（默认） | 第一个命中全量替代 |
| **prepend** | preset 内容放在低优先级内容之前 |
| **append** | preset 内容放在低优先级内容之后 |
| **wrap** | 用 `{CORE_TEMPLATE}` 占位符包裹低优先级内容（脚本用 `$CORE_SCRIPT`） |

> 这意味着 preset 不只能"替换"，还能"叠加"——比如在 core 的 spec 模板前后加自己的段。

### 4.4 目录里的 preset 示例

`presets/` 下有三个示例：

| preset | 作用 |
|--------|------|
| `lean` | 精简版 SDD 命令（constitution/implement/plan/specify/tasks） |
| `scaffold` | 脚手架预设，带扩展命令模板 |
| `self-test` | 自测预设，含全套模板（constitution/plan/spec/tasks/checklist/agent-file） |

> 社区还有更激进的 preset，如 AIDE（7 步 AI 工程生命周期）、Canon（基线驱动）、Product Forge（产品经理向）、FX→.NET（.NET 迁移）、MAQA（多代理 + 质量门）。

## 5. Bundle：角色化整套

### 5.1 是什么

Bundle 把一组 extensions + presets + workflows + steps **打包成版本化、角色化的安装单元**。一次 `bundle install` 就能给某个角色（产品经理、业务分析师、安全研究员、开发者）配齐全部组件。

- 用手写的 `bundle.yml` 清单描述
- 每个组件钉死版本
- 可选指定 `integration`（不指定则是 agnostic，继承项目已有代理）

### 5.2 命令

```bash
specify bundle search [<query>]       # 发现
specify bundle info <bundle-id>       # 查看会装什么（= install 会装的）
specify bundle install <bundle-id>    # 一次装全套
specify bundle list                   # 已装
specify bundle update <bundle-id>     # 或 --all
specify bundle remove <bundle-id>     # 只移除这个 bundle 的组件
```

### 5.3 目录与打包

`examples/bundles/` 下有 4 个示例清单（product-manager / business-analyst / security-researcher / developer）。作者本地打包发布：

```bash
specify bundle validate --path ./my-bundle    # 结构 + 引用校验
specify bundle build --path ./my-bundle       # 产出版本化 .zip
```

### 5.4 关键保证

- **透明**：`info` 显示的就是 `install` 会装的
- **幂等**：安装可重复，局限于项目根
- **非破坏移除**：`remove` 不碰其他 bundle 还在用的组件
- **离线**：consume 和 author 命令都能离线跑（本地或钉死源）

### 5.5 catalog 栈

Bundle 从**优先级排序的 catalog 栈**解析（project > user > built-in）。每个源带安装策略：

- `install-allowed`：可从此安装
- `discovery-only`：search/info 可见但拒绝安装

```bash
specify bundle catalog list|add|remove
```

## 6. Workflow：编排多步

### 6.1 是什么

Workflow 把多步 SDD 过程自动化成可重复序列——串起命令、prompt、shell、人工门禁，支持条件、循环、fan-out/fan-in，还能**暂停后从断点恢复**。

### 6.2 命令

```bash
specify workflow run <source> [-i key=value] [--json]    # 跑（catalog ID/URL/本地文件）
specify workflow resume <run_id> [-i key=value] [--json] # 恢复
specify workflow status [<run_id>] [--json]              # 状态/列表
specify workflow list                                    # 已装
specify workflow add <source>                            # 安装
specify workflow remove <workflow_id>                     # 移除
specify workflow search [query] [--tag]
specify workflow info <workflow_id>
specify workflow catalog list|add|remove
```

### 6.3 内置 workflow 示例

`workflows/speckit/workflow.yml` 是内置的 **Full SDD Cycle**，把 specify → plan → tasks → implement 串起来，中间插 review gate：

```yaml
schema_version: "1.0"
workflow:
  id: "speckit"
  name: "Full SDD Cycle"
steps:
  - id: specify
    command: speckit.specify
    integration: "{{ inputs.integration }}"
    input:
      args: "{{ inputs.spec }}"
  - id: review-spec
    type: gate
    message: "Review the generated spec before planning."
    options: [approve, reject]
    on_reject: abort
  - id: plan
    command: speckit.plan
    ...
```

执行流程：

```text
specify → [review-spec 门禁] → plan → [review-plan 门禁] → tasks → implement
                                拒绝则 abort
```

跑法：

```bash
specify workflow run speckit -i spec="Build a kanban board with drag-and-drop"
```

### 6.4 步骤类型（11 种）

| 类型 | 用途 |
|------|------|
| `command` | 调 Spec Kit 命令（如 speckit.plan） |
| `prompt` | 给 AI 代理发任意 prompt |
| `shell` | 执行 shell 命令并捕获输出 |
| `init` | 引导项目（同 specify init） |
| `gate` | 暂停等人工批准 |
| `if` | 条件分支（then/else） |
| `switch` | 多分支派发 |
| `while` | 条件循环 |
| `do-while` | 至少执行一次再循环 |
| `fan-out` | 对列表每项派发 |
| `fan-in` | 聚合 fan-out 结果 |

> **安全提示**：`shell` 步骤用**你的**权限跑本地命令，**没有沙箱**。`requires` 块只是前置声明（spec-kit 版本、集成），不是运行时门禁，不限制步骤能做什么。下载的 workflow 一定先 review 源码，敏感命令前加 `gate` 步骤要人工批准。

### 6.5 表达式与状态

- 用 `{{ expression }}` 引用输入和上一步输出（`inputs.spec`、`steps.specify.output.file`、`item`）
- 过滤器：`default` / `join` / `contains` / `map` / `from_json`
- 每次运行状态持久化在 `.specify/workflows/runs/<run_id>/`（state.json / inputs.json / log.jsonl），所以能从暂停/失败的精确步骤恢复

## 7. 何时用哪个（决策表）

| 目标 | 用 |
|------|-----|
| 加全新命令或工作流 | Extension |
| 改 spec/plan/tasks 的格式 | Preset |
| 集成外部工具或服务 | Extension |
| 强制组织/合规标准 | Preset |
| 发布可复用的领域模板 | Preset（纯模板）或 Extension（模板带新命令） |
| 一次给角色配齐整套 | Bundle |
| 把多步过程自动化成可恢复流程 | Workflow |
| 单个项目一次性微调 | 项目本地覆盖（`.specify/templates/overrides/`） |

## 8. Catalog 体系（扩展/preset/workflow 共用）

三类组件都从**优先级 catalog 栈**发现和安装，解析顺序一致：

```text
1. 环境变量（如 SPECKIT_CATALOG_URL）        ← 覆盖所有
2. 项目配置（.specify/{extension,preset,workflow}-catalogs.yml）
3. 用户配置（~/.specify/...）
4. 内置默认（官方 catalog + 社区 catalog）
```

> 这套机制让组织能**自建私有 catalog**控制可装内容，支持离线/防火墙后部署——这是 Spec Kit 进企业的关键能力。

## 9. 发布自己的组件

- **Extension**：见 `extensions/EXTENSION-PUBLISHING-GUIDE.md`，社区提交用 `extension_submission.yml` issue 模板
- **Preset**：见 `presets/PUBLISHING.md`
- **Bundle**：本地 `validate` + `build` 产 .zip，托管后加 catalog 源；社区提交用 `bundle_submission.yml`

> 共同原则：作者本地校验打包，分发就是托管产物 + 加 catalog。社区组件独立维护，Spec Kit 维护者不审查、不背书——用前自行 review 源码。

## 10. 小结

- 四件套：extension 加能力、preset 改造现有、bundle 角色化打包、workflow 编排流程
- 都围绕**模板解析栈**运转（项目覆盖 > preset > extension > core）
- preset 支持 replace/prepend/append/wrap 四种策略，不止"替换"
- bundle 给一次性角色配置 + catalog 栈支持企业私有源
- workflow 11 种步骤类型 + 暂停恢复，但 shell 步骤无沙箱要 review
- 决策看"改什么"：新能力→extension，改格式→preset，整套→bundle，自动化→workflow

下一篇 [06-integrations-and-architecture.md](./06-integrations-and-architecture.md) 看内部实现：CLI 怎么把 30+ 代理接进来、命令文件什么格式、怎么加新代理。
