# 08 · Issue 与项目管理

> 代码写到一半发现 bug、想到新功能、需要跟踪进度——这些「非代码」的协作怎么管？GitHub 提供 Issue（任务跟踪）、Labels（分类）、Milestones（里程碑）、Projects（看板/表格）、Discussions（讨论）。本章讲怎么用它们做轻量项目管理。本项目目前用 `docs/PROGRESS.md` 记进度，本章也对比下两种方式的取舍。

## 8.1 Issue 详解

### Issue 是什么

01 章已介绍：Issue 是 GitHub 上的「任务/问题记录单元」。一条 Issue = 一个待办事项。

典型用途：
- Bug 报告
- 功能请求
- 任务清单
- 跟踪某个改造

### 创建 Issue

仓库 → Issues → New issue。关键字段：

| 字段 | 作用 |
|------|------|
| Title | 标题，简明描述问题 |
| Description | 正文，Markdown |
| Assignees | 负责人（最多 10 人） |
| Labels | 分类标签 |
| Projects | 关联 Project 看板 |
| Milestone | 关联里程碑 |
| Linked issues | 关联其他 Issue（blocks / is blocked by 等） |

### Issue 描述模板

Bug 报告模板：

```markdown
## 问题描述
购物车勾选商品后，刷新页面勾选状态丢失。

## 复现步骤
1. 登录会员账号
2. 加入 3 个商品到购物车
3. 勾选其中 2 个
4. 刷新页面

## 期望行为
勾选的 2 个商品仍处于勾选状态。

## 实际行为
所有商品变为未勾选。

## 环境
- 浏览器：Chrome 126
- 是否登录：是
- 操作系统：Windows 11

## 截图 / 日志
（贴图或贴日志）
```

功能请求模板：

```markdown
## 想要的功能
购物车支持批量勾选/取消勾选。

## 为什么需要
当前要逐个点击勾选，3+ 商品时操作繁琐。

## 期望的实现
- 列表头加全选 checkbox
- 支持Shift+点击范围选择

## 备选方案
- 现状：逐个点
- 其他平台做法：京东支持全选/范围选
```

### Issue 模板文件

仓库根目录 `.github/ISSUE_TEMPLATE/` 放模板，发 Issue 时自动可选：

```
.github/
└── ISSUE_TEMPLATE/
    ├── bug_report.md         # Bug 报告模板
    ├── feature_request.md    # 功能请求模板
    ├── config.yml            # Issue 模板配置
    └── custom.md             # 其他模板
```

`bug_report.md` 示例：

```markdown
---
name: Bug 报告
about: 报告一个 bug
title: '[BUG] '
labels: bug
assignees: ''
---

## 问题描述
（在这里描述）

## 复现步骤
1.
2.
3.

## 期望 / 实际行为
**期望**：
**实际**：
```

`config.yml` 可配 issue 选择器：

```yaml
blank_issues_enabled: false    # 禁止空白 issue，必须选模板
contact_links:
  - name: 提问讨论
    url: https://github.com/QiHaoo/my-mall/discussions
    about: 不确定是不是 bug 的，去 Discussions 讨论
```

## 8.2 Labels（标签）

Label 给 Issue / PR 分类。仓库 Issues → Labels 管理。

### 常用 Label 体系

| 类别 | Label | 颜色建议 | 含义 |
|------|-------|----------|------|
| 类型 | `bug` | 红 | Bug |
| | `feature` | 绿 | 新功能 |
| | `enhancement` | 浅绿 | 增强 |
| | `documentation` | 蓝 | 文档 |
| | `refactor` | 紫 | 重构 |
| | `test` | 黄 | 测试 |
| 优先级 | `priority:high` | 红 | 高优先级 |
| | `priority:medium` | 黄 | 中 |
| | `priority:low` | 绿 | 低 |
| 状态 | `in-progress` | 蓝 | 进行中 |
| | `blocked` | 红 | 阻塞 |
| | `wontfix` | 灰 | 不修 |
| | `duplicate` | 灰 | 重复 |
| 模块 | `module:gateway` | — | 网关模块 |
| | `module:order` | — | 订单模块 |
| 难度 | `good first issue` | 绿 | 新人友好 |
| | `help wanted` | 绿 | 求帮助 |

> GitHub 内置一套默认 label（bug、enhancement、documentation 等），可改可不改。开源项目普遍用 `good first issue` 标记新人友好的入门任务。

### 批量管理 Label

`gh` CLI 可批量加 label：

```bash
$ gh issue edit 42 --add-label "bug,priority:high"
$ gh issue edit 42 --remove-label "in-progress"
```

## 8.3 Milestone（里程碑）

Milestone 把多个 Issue / PR 聚成一个「版本目标」。

例如建一个 Milestone「v1.0 - 商品中心」：
- Due date：2026-07-31
- 关联所有商品中心的 Issue / PR
- 进度条显示「12/15 closed」

适合**有明确发布目标**的场景：发版前所有关联 Issue 必须关闭。

仓库 Issues → Milestones → New milestone。

## 8.4 Projects（新版 v2）

GitHub 2021 年推出新版 Projects（基于数据库，不是旧版 Projects Classic 的看板）。

### Projects v2 的特点

- **不是仓库专属**：一个 Project 可跨多个仓库
- **多种视图**：看板 / 表格 / 时间线 / Roadmap
- **灵活字段**：text / number / date / single select / iteration（迭代）
- **支持 Issue / PR / 自定义 Draft Issue**
- **可分享链接**：项目进度对外可见

### 创建 Project

用户主页或组织主页 → Projects → New project。或仓库 Projects 标签（仓库级 Project）。

### 视图类型

| 视图 | 用途 | 类比 |
|------|------|------|
| Table | 表格视图，每行一个 item，列是字段 | Excel |
| Board | 看板视图，按某字段分列（如 Status） | Trello / Jira 看板 |
| Roadmap | 时间线视图，按 date 字段横向排开 | Gantt 图 |
| Calendar | 日历视图 | Google 日历 |

### 典型用法

**用法一：Kanban 看板**

字段：`Status`（Todo / In Progress / In Review / Done）
视图：Board by Status
拖拽 Issue 在列间移动 → 自动更新 Status 字段。

```
┌─────────┬──────────────┬───────────┬──────┐
│  Todo   │ In Progress  │ In Review │ Done │
├─────────┼──────────────┼───────────┼──────┤
│ Issue 1 │ Issue 3      │ Issue 5   │ #42  │
│ Issue 2 │ Issue 4      │           │ #45  │
│ Issue 6 │              │           │ #50  │
└─────────┴──────────────┴───────────┴──────┘
```

**用法二：迭代（Sprint）管理**

字段：`Iteration`（2 周一个 sprint）
视图：Table grouped by Iteration
每个 sprint 一组，排期清晰。

**用法三：跨仓库 Roadmap**

组织级 Project，加 Issues / PRs from 多个仓库
视图：Roadmap by date
看整个组织下所有项目的进度。

### 自动化

Project 可配自动化：

- 新 Issue 自动加到 Project
- Issue 关闭自动移到 Done 列
- PR 合并自动移到 Done 列

仓库 Settings → Actions → 可用 `actions/add-to-project` 等官方 Action 自动同步。

## 8.5 Discussions（讨论区）

01 章提过， Discussions 是 Issue 的「轻量版」，适合：
- 不确定是不是 bug，想先讨论
- 提问怎么用
- 想法收集

仓库 Settings → General → Features → Discussions 勾选开启。

Discussions 有分类（Announcements、Ideas、Polls、Q&A、Show and tell），可配模板。

开源项目常用 Discussions 做 Q&A 社区，避免 Issue 列表被提问淹没。

## 8.6 Issue / PR 联动技巧

### 自动关闭 Issue

PR 描述写 `closes #42`，合并时 Issue #42 自动关闭（见 03 章）。

### 引用 Issue

任意 Issue / PR / commit message 里写 `#42`，GitHub 自动生成链接。

### 跨仓库引用

`QiHaoo/my-mall#42` 引用别的仓库的 Issue。

### Task List

Issue / PR 描述里用 task list 跟踪子任务：

```markdown
## 商品中心 v1
- [x] SPU/SKU 模型设计
- [x] 三级分类管理
- [ ] 品牌管理
- [ ] 属性管理
```

勾选状态会显示在 Issue 列表里（标题旁显示 `2 of 4`）。

### Sub-issue / Linked issues

新版 GitHub 支持给 Issue 加关联：

- `blocks`：当前 Issue 阻塞另一个
- `is blocked by`：当前 Issue 被另一个阻塞
- `relates to`：相关
- `duplicate of`：重复

Issue 右侧「Linked issues」可配置，或在描述用 `blocks #42` `is blocked by #88`。

## 8.7 本项目对照：PROGRESS.md vs GitHub Projects

本项目目前用 `docs/PROGRESS.md`（项目级）和 `docs/{服务}/PROGRESS.md`（服务级）记录进度，不用 GitHub Projects。两种方式对比：

| | PROGRESS.md（本项目用） | GitHub Projects |
|---|------------------------|-----------------|
| 形态 | Markdown 文件 | 平台看板/表格 |
| 与代码关联 | 同仓库，PR 一起改 | 平台层，需手动同步 |
| 历史可追溯 | Git 历史 | 平台历史（弱） |
| 离线可见 | 是（clone 即有） | 否 |
| 视图灵活度 | 低（线性列表） | 高（看板/表格/时间线） |
| 自动化 | 靠 AI/人工编辑 | 内置自动化 + Actions |
| 适合场景 | 学习项目 / 文档驱动 | 团队协作 / 多人项目管理 |
| 与 commit 关联 | 强（AGENTS.md 要求进度文档与提交同步） | 弱 |

**本项目用 PROGRESS.md 的原因：**

1. 学习项目，进度本质是「学到了什么、做了什么」，本质是文档
2. 与 git commit 强关联（AGENTS.md 规定每次提交前要更新 PROGRESS.md），用文件更顺
3. 文档站会渲染 PROGRESS.md（MkDocs），公网可见
4. 单人开发，不需要看板协作

**何时切换到 GitHub Projects：**

- 团队扩展到 2+ 人，需要看板分配任务
- 多个并行任务需要可视化排期
- 跨仓库管理（一个 Project 跟踪多个微服务）

两种方式不互斥，可以 PROGRESS.md 记「项目级里程碑」，GitHub Projects 记「日常任务清单」。

## 8.8 实战：给本项目建一个简单 Project（可选练习）

如果想试一下 GitHub Projects，建一个看板跟踪剩余学习任务：

1. 仓库 Projects → New project → 选「Board」模板
2. 默认字段：Item / Status (Todo / In Progress / Done) / Labels
3. 加几个 Draft Issue（不一定要建 Issue，Draft Issue 直接在看板上）：
   - 配置 main 分支 Ruleset
   - 给后端加 CI workflow
   - 写 CodeQL 安全扫描 workflow
   - 加 Release 自动化
4. 拖到 In Progress 开始做，做完拖到 Done

不用也能用 PROGRESS.md，但试一下能直观感受 GitHub Projects 的能力。

## 小结

| 你应该记住的 |
|---|
| Issue 是任务/问题记录单元，配 `.github/ISSUE_TEMPLATE/` 模板 |
| Labels 分类、Milestones 聚合版本目标、Projects 看板管理 |
| Projects v2 支持看板/表格/时间线，可跨仓库 |
| Discussions 做 Q&A，避免 Issue 被提问淹没 |
| PR 描述 `closes #42` 自动关闭 Issue |
| Task list `- [x]` 跟踪子任务，状态显示在列表 |
| 本项目用 PROGRESS.md 记进度（文档驱动、与 commit 关联强），团队大了可切到 Projects |

下一章 [09-Secrets 与安全](./09-secrets-security.md) 讲怎么安全地管理 CI 密钥、用 Dependabot 监控依赖漏洞、用 CodeQL 扫代码漏洞。
