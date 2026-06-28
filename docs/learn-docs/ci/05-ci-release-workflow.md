# 05 · 发布与版本管理

> 前置阅读：[01-docker-basics.md](./01-docker-basics.md)、[02-ci-fundamentals.md](./02-ci-fundamentals.md)、[03-ci-testing-strategy.md](./03-ci-testing-strategy.md)、[04-ci-build-and-images.md](./04-ci-build-and-images.md)
>
> 本章讲清五件事：版本号怎么管、Git tag 怎么打、GitHub Release 怎么自动建、Changelog 怎么自动生成、分支保护怎么给 CI 门禁「执法权」。
> 看完后你会理解：为什么推送一个 `v1.0.0` 标签就能让 CI 自动跑完整条发布链路、自动生成变更日志、自动创建 Release——以及为什么 main 分支谁都不能直接 push。

---

## 一、为什么需要版本管理

前 4 章我们讲了 CI 的核心：测试门禁保证「代码能跑」，镜像构建产出「可部署的制品」。但制品产出到用户/运维手里，中间还缺一块——**这个制品到底叫什么、改了什么、能不能回退**。这就是版本管理要解决的问题。

### 1.1 没有版本管理的世界

先看几个熟悉的对话：

```
生产出 bug，运维问：「现在跑的哪个版本？」
开发答：「就是最新的那个。」
运维：「最新的哪个？今天合并了好几个 PR。」
开发：「……我也不确定，反正拉的是 main 最新代码。」
```

```
凌晨排查线上问题，要回滚到上一个稳定版本。
问：「上一个稳定版本是哪个 commit？」
答：「不知道，git log 翻了半天，找到几个 merge commit，看不出哪个是发布点。」
```

```
用户问：「你们 v1.2 相比 v1.1 改了什么？」
答：「呃……大概加了几个功能，改了几个 bug，具体记不清了。」
```

这三个场景的根因都是同一个：**没有给「发布」这件事打上标记**。代码每天都在变，但「哪一次变更构成了一个发布」「这个发布包含了什么」全凭记忆，没有任何客观依据。

### 1.2 版本管理解决的三个问题

| 问题 | 没有版本管理 | 有版本管理 |
|------|------------|----------|
| **精确追溯** | 「跑的是哪个版本？」——不知道 | 「跑的是 v1.2.0」，git tag 一查就知道对应哪个 commit |
| **回滚定位** | 翻 git log 找发布点，靠猜 | v1.2.0 出问题，明确回滚到 v1.1.0，干净利落 |
| **变更记录** | 每个版本改了什么，全凭记忆 | Release Notes 自动列出本版本所有变更 |

用三个具体场景说明价值：

- **精确追溯**：生产环境跑的镜像 tag 是 `v1.2.0`，你立刻知道它对应的 commit 是 `a3f9c21`，这个 commit 改了 `mall-order` 的下单逻辑。排查 bug 时不用在几千个 commit 里大海捞针。
- **回滚定位**：v1.2.0 引入了一个库存超卖 bug，运维一键把 K8s 的镜像 tag 从 `v1.2.0` 切回 `v1.1.0`，问题立即缓解。因为 v1.1.0 是一个明确的、之前验证过的发布点。
- **变更记录**：用户/团队其他成员打开 GitHub Releases 页面，看到 v1.2.0 的 Release Notes：「新增商品搜索、修复购物车数量负数 bug」，一目了然。

> **一句话总结**：版本管理不是「给代码起个名字」这么简单，它是「追溯、回滚、沟通」三件事的基础设施。没有它，CI 产出的镜像再多也是一团乱麻——你只知道「有镜像」，不知道「哪个镜像对应哪个版本、改了什么」。

---

## 二、语义化版本（SemVer）

### 2.1 版本号规则

版本号不能随便起（`v1`、`v1-final`、`v1-真的最终版`、`v1-这次真的是最终版`……）。业界有一个广泛遵循的标准叫**语义化版本（Semantic Versioning，SemVer）**，本项目对齐 [git-workflow.md](../../standards/git-workflow.md) §5 的规范。

格式：

```
v<MAJOR>.<MINOR>.<PATCH>
```

三部分含义：

| 部分 | 变更时机 | 示例 |
|------|---------|------|
| MAJOR（主版本号） | 不兼容的 API 变更（破坏性改动） | v1.0.0 → v2.0.0 |
| MINOR（次版本号） | 新增功能（向下兼容） | v1.0.0 → v1.1.0 |
| PATCH（修订号） | Bug 修复（向下兼容） | v1.0.0 → v1.0.1 |

### 2.2 一个类比：书籍的版次

版本号就像书籍的「版次」：

- **MAJOR = 大改版**（第 2 版）：内容结构都变了，老读者得重新学。对应 API 不兼容变更，调用方必须改代码。
- **MINOR = 新增章节**（第 1.2 版）：加了新内容，但老内容还在原来的位置，老读者能无缝衔接。对应新增功能，调用方不用改代码也能继续用。
- **PATCH = 修错字**（第 1.0.1 版）：只是纠正了错别字，内容没变。对应 bug 修复，行为更正确了但接口没变。

这个类比的核心是：**看到版本号的变化，就能判断「我升级会不会出问题」**。MINOR 和 PATCH 升级理论上不会破坏你的代码（向下兼容），MAJOR 升级则要小心——可能要改代码。

### 2.3 为什么要用 SemVer

如果没有统一规则，版本号就只是个名字，传递不了任何信息。用了 SemVer，版本号本身就携带语义：

- 看到 `v1.2.0 → v1.2.1`：哦，只是修 bug，放心升级。
- 看到 `v1.2.0 → v1.3.0`：有新功能，兼容的，可以升级试试。
- 看到 `v1.2.0 → v2.0.0`：破坏性变更，得看迁移文档再决定。

这对自动化也有意义——CI 可以解析版本号判断变更类型，依赖管理工具（如 Maven、npm）能据此决定是否允许自动升级。本项目 CI 就是通过解析 tag 后缀来自动判断「是不是预发布版本」（见第六章）。

### 2.4 预发布版本

正式发布前，往往要先发一个「不太稳定但可以提前体验」的版本。SemVer 用 tag 后缀来表示预发布阶段：

| 格式 | 含义 | 场景 |
|------|------|------|
| `v1.0.0-alpha` | 内测版（alpha） | 内部测试，功能可能不完整，bug 较多 |
| `v1.0.0-beta` | 公测版（beta） | 公开测试，功能基本完整，邀请外部用户试用 |
| `v1.0.0-rc.1` | 候选版（Release Candidate） | 候选发布版，如果没有重大问题就会变成正式版 |

```
v1.0.0-alpha   →   v1.0.0-beta   →   v1.0.0-rc.1   →   v1.0.0
  (内测)             (公测)             (候选)            (正式)
```

> **为什么要分这么多阶段**：直接发正式版风险太大——一旦有重大 bug，所有用户都受影响。通过 alpha/beta/rc 逐步扩大测试范围，把问题暴露在小范围内，等正式版发布时已经经过充分验证。本项目 CI 会根据这些后缀自动把 Release 标记为「Pre-release」（见第六章），让用户一眼区分正式版和测试版。

---

## 三、Git Tag

### 3.1 Tag 是什么

Git tag 是**给某个 commit 打的永久标记**。理解它的关键是和分支对比：

| 概念 | 会移动吗 | 用途 |
|------|---------|------|
| 分支（branch） | 会，每次 commit 都往前移 | 标记一条「正在开发的线」 |
| 标签（tag） | 不会，永远指向同一个 commit | 标记一个「特定的发布点」 |

打个比方：

- **分支像书签**——你读到哪一页，书签就插到哪一页，每天都在往后移。
- **标签像盖章**——在某一页盖上「第 1 版定稿」的章，这一页就永久是「第 1 版」，不管你后面又读了多少页。

所以 tag 特别适合标记发布点：v1.0.0 永远指向那次发布的 commit，不会因为后面继续开发而改变。

### 3.2 两类 Tag

Git 有两种 tag：

| 类型 | 命令 | 特点 | 本项目用不用 |
|------|------|------|------------|
| 轻量标签（lightweight） | `git tag v1.0.0` | 只是一个指向 commit 的指针，没有额外信息 | ❌ |
| 注释标签（annotated） | `git tag -a v1.0.0 -m "msg"` | 包含打标签者、日期、说明信息，是一个独立对象 | ✅ |

**本项目用注释标签**（对齐 [git-workflow.md](../../standards/git-workflow.md) §5）。原因：

- 注释标签携带「谁、什么时候、为什么」打这个标签的信息，便于追溯
- `git push --tags` 推送后，GitHub 上能看到标签的说明信息
- 生产级规范要求发布点有完整记录，轻量标签信息太单薄

### 3.3 完整的 Tag 操作

```bash
# 1. 打注释标签（在要发布的 commit 上）
git tag -a v1.0.0 -m "Release v1.0.0"

# 2. 推送标签到远程（这一步会触发 CI 的 release 工作流）
git push origin v1.0.0

# 3. 查看所有标签
git tag -l

# 4. 查看某个标签的详细信息（注释标签能看到说明信息和指向的 commit）
git show v1.0.0

# 5. 删除本地标签（打错了想重来）
git tag -d v1.0.0

# 6. 删除远程标签（如果已经推上去过）
git push origin --delete v1.0.0
```

### 3.4 推送 Tag 触发 CI

这是最重要的一点：**推送一个 `v*.*.*` 格式的 tag 会触发 CI 的完整发布流水线**。

为什么？因为项目编排器 `orchestrator.yml` 配置了监听 tag 推送（对齐 [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) §5.2）：

```yaml
on:
  push:
    tags:
      - 'v*.*.*'      # 仅 v*.*.* 格式的 tag 推送触发完整发布流水线
```

这意味着：

```
git push origin v1.0.0
   │
   ▼
GitHub 检测到 tag 推送，匹配 'v*.*.*' 模式
   │
   ▼
触发 orchestrator.yml
   ├── backend-ci        （后端编译 + 测试）
   ├── frontend-ci       （前端构建 + 测试）
   ├── docker-publish    （构建镜像推 Harbor，依赖上面两个通过）
   └── release           （生成 Changelog + 创建 GitHub Release，依赖镜像推送完成）
```

所以「打 tag 并推送」不是单纯的 Git 操作，而是**发布动作的扳机**。这一推，整条 CI/CD 链路就跑起来了。

> **注意**：tag 必须符合 `v*.*.*` 格式才会触发（如 `v1.0.0`、`v2.3.1`）。如果你打了个 `v1.0` 或 `version-1`，CI 不会响应。格式的严格匹配是为了避免误触发——只有规范的语义化版本 tag 才代表「我要发版了」。

---

## 四、GitHub Release

### 4.1 Release 是什么

讲到这里你可能会问：tag 不就能标记发布点了吗，为什么还要 GitHub Release？

因为 **tag 是 Git 概念，Release 是 GitHub 在 tag 之上的增强**。两者的关系：

| 概念 | 是什么 | 在哪 | 包含什么 |
|------|--------|------|---------|
| Git Tag | Git 的标记 | Git 仓库（本地 + 远程） | 只是标签名 + 指向某个 commit 的引用 |
| GitHub Release | GitHub 的版本发布 | GitHub 网页 UI（Releases 页面） | tag + 标题 + Changelog（变更说明）+ 附件 |

换句话说：

- **tag** 是底层的「锚点」，存在于 Git 层面，任何 Git 客户端都能看到
- **Release** 是 GitHub 提供的「门面」，在 tag 锚点上叠加了人类可读的变更说明、可下载的附件，让发布信息对用户友好

### 4.2 没有 Release 会怎样

只有 tag 没有 Release 时：

- 用户得自己 `git log v1.0.0...v1.1.0` 看变更，门槛高
- 没有统一的「版本说明」页面
- 没法挂附件（如编译好的 jar、前端 dist 包）

有 Release 后：

- 用户打开 GitHub Releases 页面，所有版本一目了然
- 每个版本有标题、变更说明（Changelog）
- 可以挂附件供下载
- 预发布版本有「Pre-release」标记，和正式版视觉区分

### 4.3 本项目的自动化

本项目不需要手动创建 Release——**推送 tag 后，CI 会自动创建 GitHub Release**（对齐 [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) §一）：

```
推送 tag v1.0.0
   │
   ▼
CI 的 release job 执行：
   1. 从 commit history 自动生成 Changelog（见第五章）
   2. 调用 softprops/action-gh-release 创建 Release
      ├── tag_name: v1.0.0
      ├── name: v1.0.0
      ├── body: 自动生成的 Changelog
      └── prerelease: false（正式版）
   │
   ▼
GitHub Releases 页面出现新条目：v1.0.0，附带变更说明
```

所以在本项目的流程里，开发者要做的就是「打 tag + 推送」，剩下的事 CI 全包了。

---

## 五、Changelog 自动生成

每个 Release 都要有一份变更说明（Changelog），告诉用户「这个版本改了什么」。这一节讲 CI 怎么自动生成它。

### 5.1 传统方式的问题

手动写 Changelog 是这样的：

```
发版前，打开编辑器，凭记忆回忆这个版本做了什么……
  - 容易遗漏：忘了上周修的那个 bug
  - 格式不统一：每次写得都不一样
  - 开发者懒得写：「代码都写完了还要写文档，烦」
```

结果就是要么没有 Changelog，要么有一份残缺、过时的 Changelog。这是所有手动流程的通病——**人是最不可靠的执行者**。

### 5.2 自动生成的原理

既然项目用了 conventional commits 规范（`feat`/`fix`/`perf`/...，见 [git-workflow.md](../../standards/git-workflow.md) §3），每个 commit 的 type 就携带了「这是什么类型的变更」的语义。CI 可以**解析两个 tag 之间的所有 commit，按 type 自动分组**，生成结构化的 Changelog：

```
🚀 Features（feat 类型：新功能）
  - 实现商品 CRUD 接口 (abc1234)
  - 新增品牌管理 (def5678)

🐛 Fixes（fix 类型：bug 修复）
  - 修复库存回滚负数 (ghi9012)

⚡ Performance（perf 类型：性能优化）
  - 优化分页查询 (jkl3456)
```

这个流程的前提是：**开发者必须严格遵循 conventional commits 规范**。如果你把新功能写成了 `chore: 加了个下单功能`，它就会被分到「Other Changes」而不是「Features」，Changelog 就不准了。所以 commit 规范不只是为了好看，它直接决定了 Release Notes 的质量。

> 这就是为什么第二章和 [git-workflow.md](../../standards/git-workflow.md) 反复强调 commit 规范——它不是形式主义，而是自动化的数据基础。规范越严格，自动化越可靠。

### 5.3 本项目的工具与配置

本项目用 [mikepenz/release-changelog-builder-action](https://github.com/mikepenz/release-changelog-builder-action) 来生成 Changelog（对齐 [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) §三）。

**选型理由**：

- 原生支持 Conventional Commits，和项目的 commit type 一一对应
- 按 type 分组输出，结构清晰
- 支持自定义配置文件，灵活控制分组规则
- 无需手动维护 `CHANGELOG.md` 文件

**commit type 到分组标题的映射**（与 [git-workflow.md](../../standards/git-workflow.md) §3 的 type 对齐）：

| Commit Type | 分组标题 | Emoji |
|-------------|---------|-------|
| `feat` | Features | 🚀 |
| `fix` | Fixes | 🐛 |
| `perf` | Performance | ⚡ |
| `refactor` | Refactor | ♻️ |
| `docs` | Documentation | 📚 |
| `ci` | CI/CD | 🔧 |
| `chore` / `style` / `test` / `revert` | Other Changes | 📦 |

配置文件放在 `.github/changelog-configuration.json`（配置与逻辑分离，便于维护）：

```json
{
  "categories": [
    {
      "title": "## 🚀 Features",
      "labels": ["feat", "feature"]
    },
    {
      "title": "## 🐛 Fixes",
      "labels": ["fix"]
    },
    {
      "title": "## ⚡ Performance",
      "labels": ["perf"]
    },
    {
      "title": "## ♻️ Refactor",
      "labels": ["refactor"]
    },
    {
      "title": "## 📚 Documentation",
      "labels": ["docs"]
    },
    {
      "title": "## 🔧 CI/CD",
      "labels": ["ci"]
    },
    {
      "title": "## 📦 Other Changes",
      "labels": ["chore", "style", "test", "revert"]
    }
  ],
  "template": "#{{CHANGELOG}}",
  "pr_template": "- #{{TITLE}} by @#{{AUTHOR}} in ##{{NUMBER}}",
  "empty_template": "- No changes",
  "max_tags_to_fetch": 200
}
```

**配置项说明**：

| 字段 | 说明 |
|------|------|
| `categories` | 分组规则，`labels` 匹配 commit type 前缀 |
| `template` | 整体 Changelog 模板，`#{{CHANGELOG}}` 是分组内容占位符 |
| `pr_template` | 单条变更的输出格式（标题、作者、PR 编号） |
| `empty_template` | 无变更时的占位文本 |
| `max_tags_to_fetch` | 最多回溯的 tag 数量，确保能找到上一个 release tag |

CI 中调用这个工具的关键 step（对齐 [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) §5.1）：

```yaml
# 生成 Changelog：从 commit history 提取 feat/fix/perf 等，按 type 分组
- name: Build changelog
  id: build_changelog
  uses: mikepenz/release-changelog-builder-action@v5
  with:
    configuration: ".github/changelog-configuration.json"
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

注意一个前置 step：Checkout 时必须用 `fetch-depth: 0` 拉取**完整**提交历史。Changelog 生成需要遍历两个 tag 之间的所有 commit，如果只拉了浅历史（默认只拉最近 1 个 commit），就找不到上一个 release tag，Changelog 会是空的或残缺的。

```yaml
- name: Checkout
  uses: actions/checkout@v4
  with:
    fetch-depth: 0   # 拉取完整历史，Changelog 生成依赖全量 commit log
```

> **一句话总结**：Changelog 自动生成的本质是「把 commit 规范变成可读的发布说明」。你按规范写 commit，CI 就能自动产出结构清晰的 Release Notes——零额外工作量。

---

## 六、预发布版本自动判断

### 6.1 为什么要自动判断

第六章（预发布）和正式版在 GitHub 上的展示是不一样的：预发布版本要标记「Pre-release」，提醒用户「这是测试版，别用在生产」。如果靠人手动标记，迟早会忘——把 alpha 版标成正式版，用户以为是稳定版就升级了，结果踩坑。

所以本项目让 CI 根据 tag 后缀**自动判断**是否为预发布（对齐 [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) §四）。

### 6.2 判断规则

| Tag 格式 | 后缀 | prerelease | 说明 |
|----------|------|------------|------|
| `v1.0.0` | 无 | `false` | 正式发布 |
| `v1.0.0-alpha` | `-alpha` | `true` | 内部测试版 |
| `v1.0.0-beta` | `-beta` | `true` | 公开测试版 |
| `v1.0.0-rc.1` | `-rc` | `true` | 候选发布版 |

规则很简单：**tag 名里包含 `-alpha`、`-beta` 或 `-rc` 就是预发布，否则是正式版**。

### 6.3 实现方式

在 `softprops/action-gh-release@v2` 的 `prerelease` 参数里，用一个 GitHub Actions 表达式直接判断，不需要额外写脚本或 step（对齐 [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) §4.2）：

```yaml
prerelease: ${{ contains(github.ref_name, '-alpha') || contains(github.ref_name, '-beta') || contains(github.ref_name, '-rc') }}
```

逐部分拆解：

| 表达式 | 含义 |
|--------|------|
| `github.ref_name` | 触发 workflow 的 tag 名称（如 `v1.0.0-alpha`） |
| `contains(github.ref_name, '-alpha')` | tag 名里是否包含 `-alpha` |
| `||` | 逻辑或——任一为 true 则整体为 true |
| 整体表达式 | 包含任一预发布后缀 → `true`（预发布）；都不包含 → `false`（正式版） |

举例：

- tag `v1.0.0` → 不包含任何后缀 → `false` → 正式版
- tag `v1.0.0-rc.1` → 包含 `-rc` → `true` → 预发布

### 6.4 GitHub UI 上的差异

判断结果会体现在 GitHub Releases 页面：

```
Releases
─────────────────────────────────────────
v1.1.0                    Latest         ← 正式版，无特殊标记
v1.1.0-rc.1   Pre-release                ← 预发布版，标 "Pre-release" 黄色标签
v1.1.0-beta   Pre-release
v1.0.0
```

预发布版本会被打上「Pre-release」标记，并且**不会**被自动推荐为「Latest」（最新版），用户下载时默认看到的是最新的正式版。这样用户就不会误把测试版当成稳定版来用。

> **设计优势**：无需额外 step 或脚本，单一表达式完成判断，逻辑清晰且与 SemVer 规范严格对齐。开发者只要按规范打 tag（`v1.0.0` 或 `v1.0.0-rc.1`），CI 自动处理剩下的事。

---

## 七、分支保护

前面几章讲了 CI 怎么测试、怎么构建、怎么发布。但光有 CI 不够——如果开发者能绕过 CI 直接合并代码，CI 就形同虚设。**分支保护规则给 CI 门禁提供了「执法权」**：不通过 CI，谁都别想合并。

这一节对齐 [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) §七。

### 7.1 没有分支保护时

先看没有分支保护的世界：

```
开发者 A：「这个 PR 我本地能跑，CI 失败肯定是环境问题，直接合并吧。」
  → 绕过 CI，直接合并到 main
  → main 上是没通过测试的代码
  → 部署到生产，挂了

管理员：「紧急修复，来不及等 CI 了，我直接 push 到 main。」
  → main 又混入了未验证的代码
  → 主干可能随时是坏的
```

没有分支保护，CI 的所有努力都可能在最后一刻被「人工绕过」摧毁。CI 是建议，分支保护是强制。

### 7.2 有分支保护后

配置了分支保护后：

- main 禁止直接 push，所有变更必须走 PR
- PR 必须通过指定的 CI check 才能合并
- 所有 review 评论必须标记为 resolved
- 即使是管理员也不能跳过这些检查

**main 分支的保护规则**（对齐 [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) §7.1）：

| 规则 | 设置 | 作用 |
|------|------|------|
| Require a pull request before merging | ✅ | 禁止直接 push，必须走 PR |
| Require approvals | ✅ 至少 1 个 | 至少 1 人 review 通过才能合并 |
| Require review from Code Owners | ✅ | 关键路径变更需 Code Owner 审批 |
| Require status checks to pass | ✅ | CI 必须通过才能合并 |
| └ 必须通过的 check | `backend-ci` / `frontend-ci` | 这两个 CI 任务必须绿 |
| Require branches to be up to date | ✅ | 合并前必须 rebase 到最新，避免集成冲突 |
| Require conversation resolution | ✅ | 所有 review 评论必须解决 |
| Do not allow bypassing the above settings | ✅ | 管理员也不能跳过检查 |
| Restrict who can push to matching branches | ✅ 仅限管理员 | 限制直接推送权限 |

> **生产级要求**：`Do not allow bypassing` 必须开启。这意味着即使仓库管理员也无法跳过 CI 检查直接合并。这是最高安全级别——没有任何「紧急后门」，所有代码变更无一例外必须经过完整 CI 验证。这听起来「不近人情」，但正是这种不可绕过的强制性，才能保证主干永远处于可部署状态。

### 7.3 main vs develop 分支保护差异

main 是生产分支，develop 是开发主线，两者的保护力度不同（对齐 [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) §7.2）：

| 规则 | main | develop | 原因 |
|------|------|---------|------|
| Require PR | ✅ | ✅ | 都走 PR，不能直接 push |
| Require approvals | ✅ ≥1 | ✅ ≥1 | 都要 review |
| Require status checks | ✅ | ✅ | 都要 CI 通过 |
| Require branches up to date | ✅ | ❌ | develop 节奏快，不强制 rebase |
| Do not allow bypassing | ✅ | ❌ | develop 略宽松，管理员可跳过（紧急场景） |

**为什么 develop 不强制「branches up to date」**：

develop 分支合并频率高（每个 feature 完成都要合并），如果强制 up to date，每次合并前都要 rebase 到最新代码再重跑 CI。多人并行开发时这会导致：

- 频繁 rebase 增加开发负担
- CI 反复重跑消耗资源
- 合并时间窗口变长，降低效率

所以 develop 允许在「CI 通过但非最新」的情况下合并，由合并后的集成测试兜底。main 则不行——生产分支必须保证每次合并都是基于最新代码验证过的，不能有任何集成风险。

### 7.4 分支保护 = CI 的执法权

回顾第二章末尾那句话：**分支保护 = CI 门禁的执法权**。现在你应该能完全理解了：

- CI 提供「检查能力」（测试、构建、覆盖率门禁）
- 分支保护提供「强制能力」（不通过检查就不让合并）
- 两者缺一不可：只有 CI 没有保护 → CI 可被绕过；只有保护没有 CI → 没有客观的检查标准

配置路径（GitHub 仓库）：

```
GitHub 仓库页面
  → Settings
    → Branches
      → Branch protection rules
        → Add branch rule
          → Branch name pattern: main（或 develop）
          → 勾选对应保护规则
          → Create
```

> **配置顺序建议**：先配置 main 分支保护（生产分支优先），再配置 develop。确保生产分支第一时间得到保护。

---

## 八、完整发布流程

前面几章分别讲了 tag、Release、Changelog、分支保护。这一节用一个完整场景把它们串起来，对齐 [git-workflow.md](../../standards/git-workflow.md) §4.3 的发布上线流程。

### 8.1 场景：发布 v1.0.0

假设 develop 上已经积累了足够的功能，要发布 v1.0.0 正式版。完整流程：

```bash
# 1. 从 develop 创建 release 分支
git checkout develop
git pull origin develop
git checkout -b release/v1.0.0 develop

# 2. 在 release 分支做发布前修复（如有版本号、文档等收尾工作）
git commit -m "chore: 升级版本号至 1.0.0"
git commit -m "docs: 更新发布说明"

# 3. 合并到 main（保留合并记录）
git checkout main
git pull origin main
git merge --no-ff release/v1.0.0

# 4. 打标签（annotated tag，正式发版必须用 -a）
git tag -a v1.0.0 -m "Release v1.0.0"

# 5. 推送 main 分支和标签（标签推送触发 CI 全链路）
git push origin main --tags

# 6. CI 自动执行（无需人工干预）：
#    backend-ci + frontend-ci → docker-publish → release

# 7. 合并回 develop，同步 release 分支上的修复
git checkout develop
git merge --no-ff release/v1.0.0
git push origin develop

# 8. 删除 release 分支
git branch -d release/v1.0.0
git push origin --delete release/v1.0.0
```

### 8.2 为什么要有 release 分支

你可能会问：为什么不直接从 develop 合并到 main 打 tag？为什么要绕一圈建个 release 分支？

因为**发布前往往需要一段「稳定期」**：

- develop 上还在不断合并新功能，直接拿 develop 发版，会混入未完成的功能
- release 分支从 develop 拉出来后**冻结功能**，只允许修 bug、改文档、调版本号
- 这段「只修 bug 不加功能」的时间，让发布内容稳定可控

```
develop  ──●──●──●──●──●──●──●──●──●──●──●──   （持续合并新功能）
            \              ●──●──●               （release/v1.0.0：只修 bug）
             \              ↑    ↑
              \           修复   打 tag v1.0.0
main       ───────────────────●──────────────   （合并 release 分支）
```

release 分支是「功能冻结 + bug 修复」的隔离区，让发布内容不被新功能污染。

### 8.3 CI 触发与发布流程的对应关系

第 5 步 `git push origin main --tags` 同时推送了 main 分支和 tag，触发的 CI 和产出如下（对齐 [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) §8.3）：

| 发布步骤 | 触发的 CI | 产出 |
|---------|----------|------|
| 合并到 main + 推送 main | backend-ci + frontend-ci + docker-publish | 镜像推送到 Harbor |
| 推送 tag v1.0.0 | 上述全部 + release | GitHub Release（含自动生成的 Changelog） |

CI 内部的执行链路（编排器 `orchestrator.yml` 调度）：

```
推送 tag v1.0.0
   │
   ▼
orchestrator.yml 触发
   │
   ├── backend-ci        （并行）后端编译 + 测试 + 覆盖率门禁
   ├── frontend-ci       （并行）前端 lint + type-check + build
   │
   ▼ （两者都通过）
   ├── docker-publish    构建镜像并推送到 Harbor
   │
   ▼ （镜像推送成功）
   └── release           生成 Changelog + 创建 GitHub Release
```

注意 `release` job 依赖 `docker-publish`（在编排器中声明 `needs: [docker-publish]`），确保**只有镜像推送成功后才会创建 Release**。这是有意的顺序设计：Release 代表「这个版本已经可用」，而「可用」的前提是镜像已经在 Harbor 里，可以随时拉取部署。如果镜像推送失败就发 Release，用户看到新版本却拉不到镜像，体验会很差。

### 8.4 预发布版本的流程

预发布版本（alpha/beta/rc）的流程和正式版几乎一样，只是 tag 名称不同：

```bash
# 创建预发布标签（在 main 上）
git tag -a v1.0.0-rc.1 -m "Release v1.0.0-rc.1"

# 推送标签触发 CI
git push origin v1.0.0-rc.1

# CI 自动判断 prerelease=true，GitHub Release 标记为 Pre-release
```

CI 会根据 `-rc` 后缀自动把 Release 标记为预发布（见第六章），无需任何额外操作。

> **小结**：整个发布流程对开发者的要求只有两件事——「按规范打 tag」和「推送」。剩下的（测试、构建镜像、生成 Changelog、创建 Release、标记预发布）全部由 CI 自动完成。这就是 CI/CD 的价值：把容易出错的手动流程，变成可靠的自动化流水线。

---

## 九、GitHub Secrets 与 Variables

发布流程中 CI 需要一些凭据和配置（如 Harbor 账号密码）。这些敏感信息不能写在 workflow 文件里（会随代码库暴露），而是用 GitHub 的 Secrets 和 Variables 机制管理。这一节对齐 [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) §六。

### 9.1 本项目的凭据清单

| 名称 | 类型 | 值（示例） | 用途 |
|------|------|-----------|------|
| `HARBOR_REGISTRY` | Variable | `registry.mall.local` | Harbor 镜像仓库地址（非敏感） |
| `HARBOR_USERNAME` | Secret | `ci-robot` | Harbor CI 机器人账号（敏感） |
| `HARBOR_PASSWORD` | Secret | `******` | Harbor 密码 / Access Token（敏感） |

> 注意：`HARBOR_*` 这组凭据主要给 `docker-publish` 用（推镜像到 Harbor）。Release 本身用的是 GitHub 自动注入的 `GITHUB_TOKEN`（创建 Release 需要 `contents: write` 权限），不需要额外配置 Secret。这里一并讲是因为它们都属于「发布链路」的凭据管理。

### 9.2 Secret 和 Variable 的区别

这是初学者容易混淆的点：

| 维度 | Secrets | Variables |
|------|---------|-----------|
| 敏感性 | 敏感信息（密码、Token） | 非敏感配置（域名、端口、环境标识） |
| 存储方式 | 加密存储 | 明文存储 |
| 日志可见性 | 自动脱敏（显示为 `***`） | 明文可见 |
| 引用方式 | `${{ secrets.名称 }}` | `${{ vars.名称 }}` |
| 适用场景 | 凭据、密钥 | 域名、配置项 |

### 9.3 为什么 HARBOR_REGISTRY 用 Variable 而非 Secret

`HARBOR_REGISTRY`（如 `registry.mall.local`）是镜像仓库的访问地址，不属于敏感信息——它本来就藏在每次 `docker pull` 命令里，任何能拉镜像的人都能看到。把它放 Variable 的好处：

1. **日志可读性**：构建日志里能看到完整的镜像地址 `registry.mall.local/mall-product:v1.0.0`，排查问题时一目了然。如果放 Secret，日志里会变成 `***/mall-product:v1.0.0`，反而难调试。
2. **职责清晰**：敏感凭据（账号密码）和非敏感配置（地址）分离，一眼能分清哪些是「不能泄露的」、哪些是「公开的」。
3. **直接引用**：workflow 里 `${{ vars.HARBOR_REGISTRY }}` 拿到的就是明文，拼接镜像 tag 时不用担心脱敏。

### 9.4 安全要求

- Harbor 账号用**专用的 CI 机器人账号**（如 `ci-robot`），不是个人账号——便于审计、便于回收
- 密码优先用 **Access Token** 而非明文密码，便于过期轮换
- 账号仅授予「推送镜像到指定项目」的权限，**禁止管理权限**（最小权限原则）
- 定期轮换凭据（建议每 90 天）

### 9.5 Release 用的 GITHUB_TOKEN

创建 GitHub Release 需要写权限，本项目用的是 GitHub Actions **自动注入**的 `GITHUB_TOKEN`，不需要手动配置 Secret：

```yaml
permissions:
  contents: write    # 最小化权限：仅授予 Release 创建所需权限

steps:
  - name: Create release
    uses: softprops/action-gh-release@v2
    with:
      tag_name: ${{ github.ref_name }}
      body: ${{ steps.build_changelog.outputs.changelog }}
    env:
      GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}   # Actions 自动注入
```

> **最小化权限原则**：`permissions: contents: write` 表示这个 job 只能创建/修改 Release，不能改仓库设置、不能读其他 Secret。即使 workflow 被恶意修改，攻击面也被限制在「Release」这一项上。这是生产级 CI 的安全基线——每个 job 只授予它完成任务所需的最小权限。

---

## 十、小结与下一步

### 核心知识点回顾

| 知识点 | 一句话总结 |
|--------|----------|
| 语义化版本（SemVer） | `MAJOR.MINOR.PATCH`，分别对应破坏性变更 / 新功能 / bug 修复 |
| 预发布版本 | `-alpha`（内测）/ `-beta`（公测）/ `-rc`（候选），CI 据此自动标记 Pre-release |
| Git Tag | 给发布点打的永久标记（不随 commit 移动），本项目用注释标签 `-a` |
| Tag 触发 CI | 推送 `v*.*.*` tag 触发编排器的完整发布流水线 |
| GitHub Release | tag 之上的增强：tag + 标题 + Changelog + 附件，CI 自动创建 |
| Changelog 自动生成 | 从 conventional commits 解析 type 自动分组，工具是 `mikepenz/release-changelog-builder-action` |
| 预发布自动判断 | `contains(github.ref_name, '-alpha/-beta/-rc')` 单表达式判断 |
| 分支保护 | CI 门禁的「执法权」：main 最严格（禁止 bypass），develop 略宽松 |
| Secrets vs Variables | 敏感信息（密码）用 Secret 脱敏，非敏感配置（地址）用 Variable 明文 |
| 完整流程 | release 分支 → main → 打 tag → 推送 → CI 全链路 → GitHub Release |

### 关键命令速查

```bash
# 打注释标签
git tag -a v1.0.0 -m "Release v1.0.0"

# 推送标签（触发 CI 发布流水线）
git push origin v1.0.0

# 推送 main 分支和所有标签
git push origin main --tags

# 查看所有标签
git tag -l

# 查看标签详情
git show v1.0.0

# 删除本地标签
git tag -d v1.0.0

# 删除远程标签
git push origin --delete v1.0.0
```

### 一句话记住整个发布链路

```
开发者：打 tag + push
   │
   ▼
CI 自动：测试 → 构建镜像 → 推 Harbor → 生成 Changelog → 建 Release
   │
   ▼
用户看到：GitHub Releases 页面出现新版本，附带变更说明
```

开发者的工作止于「打 tag 并推送」，之后的一切都是自动化的。

### 下一步

到这里，CI 系列的核心概念都讲完了：

- 01-04 讲了 CI 的基建：Docker、CI 概念、测试策略、镜像构建
- 05（本章）讲了发布与版本管理：版本号、tag、Release、Changelog、分支保护

但这些都是「概念层面」的。下一章 [06 · 读懂本项目 CI 方案](./06-project-ci-walkthrough.md) 会把这些概念全部落到项目的真实设计文档上——逐个解读 `docs/standards/ci-cd/` 目录下的 6 篇设计文档，看本项目在每个环节做了什么决策、为什么这么决策。届时你会发现，前 5 章学的概念都能在项目里找到对应的落地，知其然更知其所以然。
