# 02 · 协作工作流（业界主流）

> 一群人共用一个 Git 仓库，怎么分工、怎么合并、怎么不互相踩踏？业界沉淀出几套「工作流（Branching Workflow）」约定。本章讲清三大流派：GitHub Flow、GitFlow、Trunk-based，以及开源专用的 Forking Workflow，并给出选型建议。

## 2.1 为什么需要工作流

假设没有约定，三个人都在 `main` 分支上直接 push：

```
A 掹 commit → push
        B 此时本地还是旧代码，改完 push → 覆盖了 A 的改动？
        C 又基于 B 的旧版本改了一半……
```

Git 本身用「分支」解决了并行开发问题，但**怎么用分支**需要团队约定。工作流就是这套约定，回答：

- 从哪里拉分支？
- 分支怎么命名？
- 改完合并到哪？
- 谁负责合并？
- 怎么保证主干稳定？

## 2.2 GitHub Flow（最简单，**本项目用这个**）

GitHub 官方推崇，2011 年随 GitHub 一起流行起来。规则极简：

```
1. main 分支永远是「可部署状态」
2. 想做改动 → 从 main 拉一个新分支（命名如 feature/xxx、fix/xxx）
3. 在新分支上 commit、push
4. 开 Pull Request 请求合并回 main
5. 团队 Review + CI 通过
6. 合并到 main → 立即部署 main
```

图示：

```mermaid
gitGraph
    commit id: " "
    commit id: " "
    branch feature/1
    checkout feature/1
    commit id: " "
    commit id: " "
    commit id: " "
    checkout main
    merge feature/1 id: "部署"
    branch fix/2
    checkout fix/2
    commit id: " "
    commit id: " "
    commit id: " "
    checkout main
    merge fix/2 id: "部署"
```

### 优点

- 简单到 5 分钟能学会
- 分支生命周期短（一个 PR 周期），不会堆积陈年分支
- main 随时可部署，CI/CD 闭环自然
- 适合持续部署（CD）的 Web / SaaS 项目

### 缺点

- 没有「发布分支」概念，多环境（dev/staging/prod）管理偏弱
- 多版本并行维护（如同时维护 1.x 和 2.x）不太适用
- 对「合并即部署」的要求高，如果部署是手动 / 审批重的，会有摩擦

### 本项目实践

本项目就是 GitHub Flow：

- 主干 `main`
- 功能分支命名 `feature/<模块>-<功能>`、修复分支 `fix/<模块>-<问题>`
- 改完提 PR 合并回 `main`
- `main` 上 `docs/` 的改动会自动触发 GitHub Actions 部署文档站（这是「合并即部署」的体现）

详见项目 [git-workflow.md](../../git-workflow.md)。

## 2.3 GitFlow（经典重型，2010）

Vincent Driessen 在 2010 年提出，曾经是「最标准」的工作流。引入多个长期 / 临时分支：

```
main        ← 只放发布版本，每次 merge 打 tag
develop     ← 日常集成分支
feature/*   ← 功能分支，从 develop 拉，合回 develop
release/*   ← 发布分支，从 develop 拉，准备发版用
hotfix/*    ← 紧急修复，从 main 拉，合回 main 和 develop
```

图示：

```mermaid
gitGraph
    commit id: " "
    branch develop
    checkout develop
    commit id: " "
    commit id: " "
    branch feature/1
    checkout feature/1
    commit id: " "
    commit id: " "
    checkout develop
    merge feature/1 id: " "
    commit id: " "
    commit id: " "
    commit id: " "
    branch release/1.0
    checkout release/1.0
    commit id: " "
    commit id: " "
    checkout main
    merge release/1.0 id: " "
    checkout develop
    merge release/1.0 id: " "
    checkout main
    branch hotfix/1.0.1
    checkout hotfix/1.0.1
    commit id: " "
    checkout main
    merge hotfix/1.0.1 id: " "
    checkout develop
    merge hotfix/1.0.1 id: " "
```

### 各分支职责

| 分支 | 来源 | 合并到 | 生命周期 | 用途 |
|------|------|--------|----------|------|
| `main` | — | — | 永久 | 生产环境代码，每次合并打 tag |
| `develop` | `main` | — | 永久 | 下个版本的集成分支 |
| `feature/*` | `develop` | `develop` | 临时 | 开发新功能 |
| `release/*` | `develop` | `main` + `develop` | 临时 | 准备发版（修 bug、调版本号） |
| `hotfix/*` | `main` | `main` + `develop` | 临时 | 生产环境紧急修复 |

### 优点

- 严谨：发布、热修、功能开发各走各的道
- 适合「有明确版本号」的软件（桌面软件、SDK、嵌入式）
- 多版本并行维护友好

### 缺点

- 复杂：5 种分支，新人晕
- 合并点多，容易冲突
- `develop` 和 `main` 之间反复同步（hotfix 要合回两边）维护成本高
- 对持续部署的 Web 项目过重

### 现在的处境

2015 年后，随着持续部署流行，GitFlow 被认为**对大多数 Web 项目过度设计**。原作者 Vincent Driessen 在 2020 年的博客备注里也承认「这套流程更适合有版本号发布的软件，不适合持续部署的 Web 项目」。

> 本项目**不**用 GitFlow。

## 2.4 Trunk-based Development（主干开发，大厂主流）

Google、Facebook、Meta、Microsoft 等大厂内部普遍采用。核心思想：

```
所有人都往 main（trunk）频繁提交，main 永远可构建可部署
短生命周期分支（最多一两天）只在需要时拉，快速合并回 main
未完成的功能用 Feature Toggle（开关）隐藏，而不是放在长分支里
```

图示：

```mermaid
gitGraph
    commit id: " "
    commit id: " "
    branch short-a
    checkout short-a
    commit id: " "
    checkout main
    merge short-a id: " "
    commit id: " "
    branch short-b
    checkout short-b
    commit id: " "
    checkout main
    merge short-b id: " "
    commit id: " "
    commit id: " "
    commit id: " "
    commit id: " "
    branch short-c
    checkout short-c
    commit id: " "
    checkout main
    merge short-c id: " "
    commit id: " "
    commit id: " "
```

### 关键实践

1. **频繁小提交**：每个人一天多次 push 到 main，commit 粒度小
2. **Feature Toggle**：未做完的功能用配置开关关掉，代码先合进去但不暴露给用户
3. **强 CI**：push 前必跑全量测试，CI 不通过立即回滚
4. **短分支**：要拉分支也别超过一两天，长分支必然冲突
5. **合并即合**：Reviewer 几小时内响应，PR 不堆积

### 优点

- 极快集成反馈，冲突少
- 适合超大团队（几百上千人）
- 和持续部署天然契合
- 没有长分支地狱

### 缺点

- 对工程基础设施要求高（CI 要稳、Feature Toggle 系统要齐备）
- 对个人纪律要求高（小提交、不破坏 main）
- 没有发布分支，多版本并行维护不友好

### 与 GitHub Flow 的区别

乍看很像（都是「短分支合并到主干」），区别：

| | GitHub Flow | Trunk-based |
|---|-------------|-------------|
| 合并方式 | 通过 PR 合并 | 可直接 push 到 main（也可 PR，但强调短） |
| 分支存活 | 一个 PR 周期（可能数天） | 1-2 天内 |
| 是否要求 Feature Toggle | 不一定 | 强烈要求 |
| 团队规模 | 中小团队为主 | 大厂超大规模团队 |
| CI 要求 | 中等 | 极高 |

简单说：**GitHub Flow 是「PR 中心」的轻量工作流，Trunk-based 是「主干中心 + 强基建」的极致工作流**。中小团队用 GitHub Flow 已经足够。

## 2.5 Forking Workflow（开源专用）

前三种都是「同一个仓库内」的协作。开源项目面对的是**外部贡献者**——他们没有你仓库的写权限。这时用 Forking Workflow：

```
1. 贡献者 fork 你的仓库到自己账号
2. clone 自己那份 fork 到本地
3. 在本地建分支改代码
4. push 到自己的 fork
5. 在 GitHub 上发起 Pull Request：从 自己的fork:分支 → 你的原仓库:main
6. 原仓库 maintainer review，决定是否合并
```

图示：

```
upstream (vuejs/core)  ◄──── Pull Request ────  你的 fork (你/core)
                                                     ▲
                                                     │ push
                                                  本地 clone
```

### 关键点

- 贡献者**没有**原仓库写权限，只能改自己的 fork
- PR 跨仓库发起（base = 原仓库，head = 自己 fork 的分支）
- 维护者审查后合并到原仓库
- 贡献者后续要同步原仓库的更新：加一个 `upstream` 远程，定期 `git fetch upstream && git rebase upstream/main`

```bash
# 配置 upstream（原仓库）
$ git remote add upstream git@github.com:vuejs/core.git
$ git remote -v
origin    git@github.com:你/core.git (fetch/push)      ← 你的 fork
upstream  git@github.com:vuejs/core.git (fetch)         ← 原仓库（只 fetch 不 push）

# 同步原仓库最新代码到自己的 fork
$ git fetch upstream
$ git switch main
$ git merge upstream/main
$ git push origin main
```

### 适用场景

- 开源项目接受外部贡献
- 公司内部「上游中台仓库 + 各业务线 fork 定制」的模式

> 本项目是个人学习项目，目前没有外部贡献者，不用 Forking Workflow。但你看的 Spring、Vue、React 等开源项目都是这套。

## 2.6 四种工作流对比

| | GitHub Flow | GitFlow | Trunk-based | Forking |
|---|-------------|---------|-------------|---------|
| 复杂度 | 低 | 高 | 中 | 中 |
| 分支数 | 2 类（main + feature） | 5 类 | 1-2 类 | 2 类 + fork |
| 适合规模 | 中小团队 | 中大团队 | 大厂超大团队 | 开源 / 跨组织 |
| 适合项目 | Web / SaaS / 持续部署 | 有版本号的软件 | 超大规模 Web | 开源 |
| 多版本并行 | 弱 | 强 | 弱 | 中 |
| CI/CD 要求 | 中 | 中 | 极高 | 中 |
| 2026 年主流度 | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐（大厂） | ⭐⭐⭐⭐（开源） |

## 2.7 怎么选

决策树：

```
是开源项目接受外部贡献吗？
├─ 是 → Forking Workflow（叠加在下面某一种之上）
└─ 否 → 团队规模和部署频率？
        ├─ 几十人以上、一天多次部署、有完善 CI + Feature Toggle
        │   → Trunk-based Development
        ├─ 中小团队、Web/SaaS 项目、持续部署
        │   → GitHub Flow ★ 推荐
        └─ 有明确版本号的软件（SDK / 桌面应用 / 嵌入式）、要同时维护多版本
            → GitFlow
```

**对个人学习项目、小型团队、Web 服务**，GitHub Flow 几乎总是最优解。本项目就是这套。

## 2.8 分支命名约定

不管用哪种工作流，分支命名最好统一。常见约定：

| 前缀 | 用途 | 例子 |
|------|------|------|
| `feature/` | 新功能 | `feature/cart-batch-check` |
| `fix/` | bug 修复 | `fix/cart-quantity-overflow` |
| `bugfix/` | 同 fix（有些团队用这个） | `bugfix/login-redirect` |
| `hotfix/` | 紧急生产修复 | `hotfix/payment-double-charge` |
| `release/` | 发布分支（GitFlow） | `release/1.2.0` |
| `docs/` | 文档 | `docs/api-reference` |
| `refactor/` | 重构 | `refactor/order-service` |
| `chore/` | 杂项（依赖升级、配置） | `chore/upgrade-spring-boot` |
| `test/` | 测试相关 | `test/order-e2e` |

本项目规范见 [git-workflow.md](../../git-workflow.md)。

## 小结

| 工作流 | 一句话 |
|--------|--------|
| GitHub Flow | main 永远可部署 + 短分支 PR，简单高效，**本项目用这个** |
| GitFlow | 5 种分支严谨流程，适合有版本号软件，对 Web 项目过重 |
| Trunk-based | 所有人频繁小提交到主干，大厂 + 强基建专用 |
| Forking | fork + 跨仓库 PR，开源项目专用 |

下一章 [03-Pull Request 与代码审查](./03-pull-request-workflow.md) 详解 GitHub Flow 里最核心的环节：PR 流程和 Code Review 实践。
