# 06 - 集成与内部架构：30+ 代理怎么接进来

> 本文回答：Spec Kit CLI 内部长什么样？30+ AI 代理是怎么"插"进来的？斜杠命令文件什么格式？想加一个新代理要做什么？
> 主要来源：`AGENTS.md`（仓库根，给贡献者的集成开发指南）、`src/specify_cli/integrations/`、`templates/commands/`。

## 1. 整体架构：CLI + 受管项目文件

Spec Kit 是一个**两层结构**：

```text
┌──────────────────────────────────────────────────┐
│  Specify CLI（Python 包，specify 命令）           │
│  - 装在用户环境（uv tool / pipx）                 │
│  - 职责：init 项目、装/卸扩展、生成命令文件        │
│  - 本身不写代码、不调 LLM                         │
└──────────────────────────────────────────────────┘
              │ specify init / extension add / ...
              ▼
┌──────────────────────────────────────────────────┐
│  受管项目（你的代码仓库）                          │
│  .specify/                 ← Spec Kit 受管文件    │
│    ├── memory/constitution.md                    │
│    ├── templates/            ← 核心模板           │
│    ├── scripts/              ← bash + ps1 两套    │
│    ├── extensions/ presets/ workflows/           │
│    └── feature.json          ← 当前 feature 标记  │
│  .claude/commands/  或  .github/prompts/  …      │
│    ← 斜杠命令文件，装哪个目录取决于 integration    │
│  specs/{###-feature}/       ← SDD 产物            │
└──────────────────────────────────────────────────┘
```

**关键认知**：CLI 只负责"铺设受管文件"，真正执行 `/speckit.*` 命令的是你选的 AI 代理。CLI 把命令文件生成到代理对应的目录，代理读这些文件来理解命令该做什么。

> 这也是为什么 Spec Kit 能支持 30+ 代理——它不依赖任何代理的私有 API，只生成"代理能读的命令文件"。

## 2. 集成架构：每个代理一个子包

所有代理集成放在 `src/specify_cli/integrations/<key>/`，每个子包暴露一个类，继承自基类，声明自己的元数据。

```text
src/specify_cli/integrations/
├── __init__.py            # INTEGRATION_REGISTRY + _register_builtins()
├── base.py                # 基类：IntegrationBase / MarkdownIntegration / TomlIntegration / YamlIntegration / SkillsIntegration
├── manifest.py            # IntegrationManifest（文件追踪）
├── claude/                # 示例：SkillsIntegration 子类
│   └── __init__.py        #   ClaudeIntegration 类
├── gemini/                # 示例：TomlIntegration 子类
├── kilocode/              # 示例：MarkdownIntegration 子类
├── copilot/               # 示例：IntegrationBase 子类（自定义 setup）
└── ...                    # 一个子包对应一个代理
```

`INTEGRATION_REGISTRY` 是 **Python 集成层的单一真相源**——支持哪些代理、目录、格式、能力都从集成类派生。

### 2.1 四个基类怎么选

| 你的代理需要… | 继承 |
|--------------|------|
| 标准 markdown 命令（`.md`） | `MarkdownIntegration` |
| TOML 格式命令（`.toml`） | `TomlIntegration` |
| YAML recipe 文件（`.yaml`） | `YamlIntegration` |
| Skill 目录（`speckit-<name>/SKILL.md`） | `SkillsIntegration` |
| 完全自定义输出（伴生文件、settings 合并等） | `IntegrationBase` 直接 |

> 大多数代理只需 `MarkdownIntegration`——一个零方法覆盖的最小子类。Kilo Code 就是这么简单：

```python
class KilocodeIntegration(MarkdownIntegration):
    key = "kilocode"
    config = {
        "name": "Kilo Code",
        "folder": ".kilocode/",
        "commands_subdir": "workflows",
        "install_url": None,
        "requires_cli": False,   # IDE 类代理，无需 CLI
    }
    registrar_config = {
        "dir": ".kilocode/workflows",
        "format": "markdown",
        "args": "$ARGUMENTS",
        "extension": ".md",
    }
```

### 2.2 必填字段

| 字段 | 位置 | 用途 |
|------|------|------|
| `key` | 类属性 | 唯一标识；CLI 类代理（`requires_cli: True`）必须等于可执行名 |
| `config` | 类属性（dict） | 代理元数据：name / folder / commands_subdir / install_url / requires_cli |
| `registrar_config` | 类属性（dict） | 命令输出：dir / format / args 占位符 / extension |

> **关键设计规则**：CLI 类代理的 `key` 必须是实际可执行名（如 `"cursor-agent"` 而非 `"cursor"`），这样 `shutil.which(key)` 能直接查 CLI 工具是否装了，不用特例映射。IDE 类代理（`requires_cli: False`）用规范标识符即可。

## 3. 命令文件格式

不同代理读不同格式的命令文件，Spec Kit 按代理的 `registrar_config` 生成对应格式。

### 3.1 Markdown 格式（最常见）

```markdown
---
description: "Command description"
---

Command content with {SCRIPT} and $ARGUMENTS placeholders.
```

Copilot Chat Mode 还多个 `mode` 字段：

```markdown
---
description: "Command description"
mode: speckit.command-name
---
```

### 3.2 TOML 格式（如 Gemini）

```toml
description = "Command description"

prompt = """
Command content with {SCRIPT} and {{args}} placeholders.
"""
```

### 3.3 YAML 格式（如 Goose）

```yaml
version: 1.0.0
title: "Command Title"
description: "Command description"
author:
  contact: spec-kit
extensions:
  - type: builtin
    name: developer
activities:
  - Spec-Driven Development
prompt: |
  Command content with {SCRIPT} and {{args}} placeholders.
```

## 4. 参数占位符

不同代理用不同的参数占位符，**以 `registrar_config["args"]` 为准**：

| 类型 | 占位符 | 典型代理 |
|------|--------|---------|
| Markdown / prompt 类 | `$ARGUMENTS` | 大多数 markdown 代理 |
| TOML 类 | `{{args}}` | Gemini |
| YAML 类 | `{{args}}` | Goose |
| 自定义 | `{{parameters}}` | Forge |
| 脚本占位符 | `{SCRIPT}` | 替换为实际脚本路径 |
| 代理占位符 | `__AGENT__` | 替换为代理名 |

## 5. 特殊处理：几个"不标准"的代理

大多数代理用标准基类就行，少数有特殊需求要自定义 `setup()`。

### 5.1 Copilot（最复杂）

Copilot 直接继承 `IntegrationBase`，因为它的命令格式很特别：

- 命令用 `.agent.md` 扩展名（不是 `.md`）
- 每个命令配一个伴生 `.prompt.md` 文件（在 `.github/prompts/`）
- 装一份 `.vscode/settings.json` 推荐 prompt 文件
- 上下文文件在 `.github/copilot-instructions.md`

自定义 `setup()` 流程：处理模板 → 生成伴生 `.prompt.md` → 合并 VS Code settings。

**Skills 模式**（`--integration-options="--skills"`）：另一种布局，命令装成 `.github/skills/speckit-<name>/SKILL.md`，不生成伴生文件、不合 settings。两种模式互斥。

### 5.2 Forge

- 用 `{{parameters}}` 而非 `$ARGUMENTS`
- 去掉 `handoffs` frontmatter 字段（Forge 专属协作功能）
- 缺 `name` 字段时注入

### 5.3 Goose

YAML 格式代理，用 Block 的 recipe 系统：`.goose/recipes/` 下 YAML 文件，`prompt: |` 块标量装命令内容。

## 6. 上下文文件的处理（重要设计决策）

很多代理有"上下文文件"（Claude 的 `CLAUDE.md`、Copilot 的 `.github/copilot-instructions.md`、根 `AGENTS.md`）。**Specify CLI 完全不碰这些文件**——不创建、不更新、不删除、不解析、不迁移。

```text
Specify CLI 不携带任何 agent-context 状态。
集成类不声明 context_file，CLI 永不碰上下文文件。
```

上下文文件的"Spec Kit 段"由**可选的 `agent-context` 扩展**全权负责：

- `specify init` 默认**不装**这个扩展
- 用户主动 `specify extension add agent-context` 后，扩展自带的脚本维护上下文段
- 扩展不在或禁用 → Spec Kit 完全不碰上下文文件

扩展读自己的配置 `.specify/extensions/agent-context/agent-context-config.yml`：

```yaml
context_file: CLAUDE.md
context_markers:
  start: "<!-- SPECKIT START -->"
  end: "<!-- SPECKIT END -->"
```

> **设计意图**：把"上下文文件管理"这个有争议、易出错的能力做成**完全可选**，而不是内置默认。新集成加什么都不用考虑上下文文件。这是值得学习的解耦。

## 7. 加一个新代理的完整流程

`AGENTS.md` 给了清晰的 7 步：

1. **选基类**：按命令格式选 MarkdownIntegration / TomlIntegration / YamlIntegration / SkillsIntegration / IntegrationBase
2. **建子包**：`src/specify_cli/integrations/<package_dir>/__init__.py`。包目录名：key 无连字符就用原值（`gemini` → `gemini/`），有连字符转下划线（`kiro-cli` → `kiro_cli/`）。`key` 类属性保留原连字符值
3. **注册**：在 `__init__.py` 的 `_register_builtins()` 里加 import + `_register()` 调用（都按字母序）
4. **上下文文件行为**：什么都不加（见上节）
5. **测试**：`specify init my-project --integration <key>` 验证文件生成；`pytest tests/integrations/test_integration_<key>.py`
6. **可选覆盖**：`command_filename()` / `options()` / `setup()` / `teardown()`——只在偏离标准模式时覆盖
7. **（可选）更新 Devcontainer**：VS Code 扩展类加到 `.devcontainer/devcontainer.json`；CLI 类加安装命令到 `post-create.sh`

### 覆盖点速查

| 覆盖 | 何时用 | 例子 |
|------|--------|------|
| `command_filename()` | 自定义文件名/扩展名 | Copilot → `speckit.{name}.agent.md` |
| `options()` | 代理专属 CLI flag | Codex → `--skills` |
| `setup()` | 自定义安装逻辑 | Copilot → `.agent.md` + `.prompt.md` + settings |
| `teardown()` | 自定义卸载逻辑 | 很少需要，基类处理 manifest 追踪的文件 |

## 8. 命令是怎么被"执行"的

这里要厘清一个容易误解的点：**Spec Kit 不执行斜杠命令**。

```text
1. specify init / extension add
   → 把命令文件（.md/.toml/.yaml）写到代理目录
2. 用户在代理里输入 /speckit.specify ...
   → 代理读对应命令文件
   → 代理按文件里的指令 + 模板 + 脚本执行
   → 代理调用 LLM 生成 spec.md 等
3. 脚本（.specify/scripts/）由代理在命令执行时调用
   → 建分支、setup-plan、setup-tasks 等自动化
```

所以命令文件本质是**给代理的"工作指令书"**，告诉代理：读哪些模板、调哪些脚本、产出什么文件、遵循什么纪律。Spec Kit 提供"指令书 + 模板 + 脚本"，代理提供"执行引擎（LLM）"。

## 9. 分支命名约定

Spec Kit 的 feature 流程强依赖 Git 分支，`AGENTS.md` 规定了命名规范：

```text
<type>/<number>-<short-slug>   # 有 issue 时
<type>/<short-slug>            # 无 issue（纯 PR）
```

| 前缀 | 用途 | 例子 |
|------|------|------|
| `feat/` | 新功能 | `feat/2342-workflow-cli-alignment` |
| `fix/` | bug 修复 | `fix/2653-paths-only-validation` |
| `docs/` | 文档 | `docs/2677-branch-naming-convention` |
| `community/` | 社区 catalog | `community/2492-add-mde-extension` |
| `chore/` | 维护/工具/CI | `chore/2366-editorconfig` |

规则：有 issue 就带号（可追溯）；slug 用 kebab-case；短到不用查 issue 也能认出。

## 10. 代理披露规范（给 AI 协作的纪律）

`AGENTS.md` 有一段针对"AI 代理参与 PR/评论/提交"的披露规范，值得一看——它要求**持续披露**而非一次性：

- **每个 commit** 要带 `Assisted-by:` trailer 说明代理及是否自主
- **评论**里要声明代理身份 + 代表谁
- **每轮 review 总结**要重新声明身份（之前的 PR body 披露不覆盖后续）
- 反模式：秒级"Done"+ 推 fix 而不披露是代理生成；声称"我 review 测试理解了"但实际是自动循环

> 这套规范反映 GitHub 对"AI 参与开源协作"的工程化态度——自动化可以，但要可审计。对做 AI 辅助开发的项目（包括 my-mall）有参考价值。

## 11. 常见坑

`AGENTS.md` 列的几个典型坑：

1. **CLI 类代理 key 写错**：`requires_cli: True` 的代理，key 必须是可执行名（`cursor-agent` 不是 `cursor`），否则 `shutil.which` 查不到
2. **把上下文处理塞回 CLI**：上下文文件归 `agent-context` 扩展管，集成类不能声明 `context_file`
3. **`requires_cli` 设错**：有 CLI 工具的设 True，纯 IDE 的设 False
4. **参数格式用错**：Markdown 用 `$ARGUMENTS`，TOML 用 `{{args}}`
5. **漏注册**：import 和 `_register()` 两个都要加
6. **测试跑错环境**：要用本仓库自己的 venv（`uv sync --extra test` 后 `.venv/bin/python -m pytest`），裸 `uv run pytest` 可能解析到全局解释器的旧 `.pth`

## 12. 小结

- Spec Kit 是两层结构：CLI 铺设受管文件，代理执行命令
- 每个代理一个集成子包，继承 4 个基类之一，大多只需 `MarkdownIntegration`
- 命令文件 3 种格式（md/toml/yaml），参数占位符以 `registrar_config["args"]` 为准
- 上下文文件由可选的 `agent-context` 扩展全权管理——CLI 零状态
- 加新代理 7 步：选基类 → 建包 → 注册 → 不管上下文 → 测试 → 可选覆盖 → 可选 devcontainer
- Spec Kit 不执行命令，只生成"给代理的指令书"

---

至此 6 篇学完。回到 [README](./README.md) 看整体地图，或复习概念速查表。
