# 07 · Release 与版本管理

> 项目做完一个阶段，要打一个「版本」让用户能引用、能下载、能回溯。GitHub 提供 Tag + Release 机制。本章讲 Tag 与 Release 的关系、语义化版本（SemVer）、Release Notes 怎么写、业界怎么自动化发版。本项目暂未发版，但学完对开源项目发版会有完整认识。

## 7.1 Tag：给某个 commit 打标签

Tag 是 Git 原生概念：**给某个 commit 起一个有意义的名字**，通常是版本号。

```bash
# 给当前 commit 打 lightweight tag
$ git tag v1.0.0

# 给指定 commit 打 tag
$ git tag v1.0.0 a1b2c3d

# 打 annotated tag（推荐，带作者/时间/说明）
$ git tag -a v1.0.0 -m "Release 1.0.0: 首个正式版本"

# 推送单个 tag 到远程
$ git push origin v1.0.0

# 推送所有本地 tag
$ git push origin --tags

# 查看所有 tag
$ git tag
v1.0.0
v1.1.0
v2.0.0

# 查看某个 tag 指向的 commit
$ git show v1.0.0
```

### lightweight tag vs annotated tag

| | lightweight | annotated |
|---|-------------|-----------|
| 创建 | `git tag v1.0.0` | `git tag -a v1.0.0 -m "..."` |
| 含元信息吗 | 不含（只是个指针） | 含作者、邮箱、时间、说明 |
| 可签名吗 | 不行 | 可 GPG 签名（`-s`） |
| 推荐场景 | 临时标记 | **正式发版用** ★ |

> 正式发版用 annotated tag，因为它有完整元信息和签名能力，更可信。

## 7.2 GitHub Release：在 Tag 之上加发布信息

Tag 只是 Git 层的标记。GitHub 在 Tag 之上加了 **Release** 概念：一个 Release = 一个 Tag + 标题 + 发布说明（Release Notes）+ 二进制附件。

仓库主页右侧 Releases 标签 → 进入 Release 列表。

### 创建 Release

**方式一：UI 操作**

Releases → Draft a new release：
- Choose a tag：选已有 tag 或新建（GitHub 会帮你打 tag）
- Target：基于哪个 commit（通常 main 的最新，或 release 分支末尾）
- Title：版本标题（如 `v1.0.0 - 首个正式版`）
- Description：Release Notes（Markdown）
- Attach binaries：上传二进制（如 jar、zip、安装包）
- Set as latest：标为最新版本
- Save as draft / Publish release

**方式二：gh CLI**

```bash
$ gh release create v1.0.0 \
    --title "v1.0.0 - 首个正式版" \
    --notes "首个正式版本发布" \
    --target main \
    mall-product/target/mall-product.jar   # 附件
```

**方式三：GitHub Actions 自动发版**

见 7.5 节。

### Release 的价值

| 价值 | 说明 |
|------|------|
| 给用户一个稳定下载点 | 不用让用户 clone 代码自己 build |
| 发布说明透明 | 告诉用户这次更新了啥、有什么 breaking change |
| 二进制附件 | 直接提供 jar / 安装包 / docker 镜像 |
| 引用方便 | Maven / npm / Docker 等可按 tag 拉特定版本 |
| 与 Issue/PR 联动 | Release Notes 自动列出本次包含的 PR |
| 可被监控 | 用户 Watch Releases 后，新版本发布会收到通知 |

## 7.3 语义化版本 SemVer

业界主流的版本号约定，由 https://semver.org/ 规范。

格式：`MAJOR.MINOR.PATCH`，如 `1.4.2`

| 位 | 含义 | 何时递增 |
|----|------|----------|
| MAJOR（主版本） | 不兼容的 API 变更 | 有 breaking change |
| MINOR（次版本） | 向后兼容的新功能 | 加了新功能，旧代码仍能跑 |
| PATCH（补丁） | 向后兼容的 bug 修复 | 只修 bug，没加功能 |

例子：
- `1.0.0` → `1.0.1`：修了个 bug
- `1.0.1` → `1.1.0`：加了个新接口
- `1.1.0` → `2.0.0`：改了已有接口签名，旧调用方要改代码

### 预发布版本

正式版前用 `-` 后缀标预发布：

| 写法 | 含义 |
|------|------|
| `1.0.0-alpha` | 内部测试版 |
| `1.0.0-beta` | 公开测试版 |
| `1.0.0-rc.1` | 候选发布版（Release Candidate） |
| `1.0.0-rc.2` | 第二个候选 |

排序：`1.0.0-alpha < 1.0.0-beta < 1.0.0-rc.1 < 1.0.0`（预发布 < 正式版）。

### 0.x.x 阶段

`0.x.x` 表示「还在开发，API 不稳定」，按惯例：
- `0.x.0` → `0.y.0`（x → y）也可能有 breaking change
- 1.0.0 才表示「API 稳定，可以认真对外」

### Build metadata

`+` 后缀附加构建信息（不参与版本排序）：

```
1.0.0+20260627
1.0.0+beta
1.0.0+exp.sha.5114f85
```

### 何时该升 1.0.0

业界共识：
- API 已经稳定，能对外公开承诺兼容
- 已经有真实用户依赖
- 不再是「实验性」项目

学习项目不强制发版本，但练习用 SemVer 是好习惯。

## 7.4 Release Notes 怎么写

好的 Release Notes 让用户秒懂「这次更新了什么、要不要升级」。

### 推荐模板

```markdown
## v1.2.0 - 2026-06-27

### ✨ New Features
- 购物车支持批量勾选 (#42)
- 商品搜索新增按品牌筛选 (#45)

### 🐛 Bug Fixes
- 修复下单时优惠券重复扣减问题 (#50)
- 修复会员登录 token 过期不刷新 (#51)

### ⚠️ Breaking Changes
- `POST /cart/add` 的 `skuId` 字段改名为 `sku_id`，旧客户端需更新

### 📦 Dependencies
- Spring Boot 3.4.0 → 3.4.1

### 📄 Documentation
- 新增对象存储设计文档

**Full Changelog**: https://github.com/QiHaoo/my-mall/compare/v1.1.0...v1.2.0
```

### 分类约定

业界常用 emoji 前缀分类（来自 Conventional Commits 思路）：

| 类别 | emoji | 对应 commit 前缀 |
|------|-------|------------------|
| 新功能 | ✨ | `feat:` |
| Bug 修复 | 🐛 | `fix:` |
| Breaking Change | ⚠️ | `feat!:` / `fix!:` |
| 重构 | ♻️ | `refactor:` |
| 性能 | ⚡ | `perf:` |
| 文档 | 📄 | `docs:` |
| 测试 | ✅ | `test:` |
| 依赖 | 📦 | `chore(deps):` |

### 自动生成

GitHub UI 创建 Release 时有「Generate release notes」按钮，自动按 PR 列表生成。也可用工具自动生成（见 7.5）。

## 7.5 自动化发版

手动发版又累又容易忘。业界有成熟的自动化方案：

### 方案一：GitHub 原生 auto-generated notes

仓库 Settings → General → Features → Releases → 勾选 auto-generated release notes，可配分类规则（`.github/release.yml`）：

```yaml
changelog:
  categories:
    - title: ✨ New Features
      labels:
        - 'feature'
        - 'enhancement'
    - title: 🐛 Bug Fixes
      labels:
        - 'bug'
        - 'fix'
    - title: 📦 Dependencies
      labels:
        - 'dependencies'
    - title: Other Changes
      labels:
        - '*'
```

发 Release 时点「Generate release notes」会按 label 分类列出 PR。

### 方案二：release-please（Google 出品）

最流行的自动化发版工具。原理：
- 你正常提 PR（commit message 遵循 Conventional Commits）
- release-please 自动维护一个「准备发版」PR，把本次要发布的改动汇总
- 你合并这个 release PR → release-please 自动打 tag + 创建 Release + 升版本号

workflow 示例：

```yaml
name: Release

on:
  push:
    branches: [main]

permissions:
  contents: write    # release-please 要创建 tag 和 release

jobs:
  release-please:
    runs-on: ubuntu-latest
    steps:
      - uses: googleapis/release-please-action@v4
        with:
          release-type: simple
          # 按 conventional commits 自动决定升 MAJOR/MINOR/PATCH
```

工作流：
```
你提 feat: 添加购物车批量勾选 → 合并 main
  ↓
release-please 创建/更新一个 PR：「chore(main): release 1.2.0」
  ↓
你合并这个 release PR
  ↓
release-please 自动：
  - 打 tag v1.2.0
  - 创建 Release v1.2.0
  - 生成 Release Notes（从 commit message 提取）
  - 升版本号到 1.3.0-SNAPSHOT
```

### 方案三：semantic-release

Node.js 生态主流，配置灵活，插件多。原理类似 release-please，但更代码化。

### 方案四：Changesets

monorepo 友好（pnpm / Turborepo 项目），每个 PR 配一个 `.changeset/*.md` 描述变更，发版时聚合。

## 7.6 版本号管理实践

### 单一 version 文件

小项目用一个文件存版本：

- Maven：`pom.xml` 的 `<version>1.2.0</version>`
- npm：`package.json` 的 `"version": "1.2.0"`
- Python：`__init__.py` 的 `__version__ = "1.2.0"`

发版时改文件 → commit → tag → push。

### SNAPSHOT / pre-release

开发中版本用后缀标记：

- Maven：`1.2.0-SNAPSHOT`（开发中，未发布）
- npm：`1.2.0-rc.1`
- Python：`1.2.0.dev1`

正式发版去掉后缀：`1.2.0-SNAPSHOT` → `1.2.0`。

### 多模块版本

Maven 多模块项目用 `<parent>` 统一版本，或用 `revision` 属性：

```xml
<properties>
    <revision>1.2.0-SNAPSHOT</revision>
</properties>
<version>${revision}</version>
```

发版时改 `revision` 属性即可。

## 7.7 Changelog 文件 vs Release Notes

两个概念容易混：

| | CHANGELOG.md | GitHub Release Notes |
|---|--------------|----------------------|
| 形态 | 仓库里的一个文件 | GitHub Release 页面的描述 |
| 历史 | 保留所有版本记录 | 每次 Release 一份 |
| 维护 | 手动 / 工具生成 | GitHub UI / 工具生成 |
| 离线可见 | 是（仓库里就有） | 否（在 GitHub 平台） |

很多项目两者都有，CHANGELOG.md 是「仓库内的版本史」，Release Notes 是「GitHub 上的发布页」。release-please 这类工具会同时维护两边。

CHANGELOG.md 格式参考 https://keepachangelog.com/。

## 7.8 本项目现状与建议

本项目目前不发版（学习项目，按文档迭代），但建议在阶段性完成时发版：

1. 在 `mkdocs.yml` 同级建一个 `CHANGELOG.md`，按 Keep a Changelog 格式记录
2. 阶段性里程碑（如「商品中心完成」「订单流程打通」）打 annotated tag：
   ```bash
   $ git tag -a v0.1.0 -m "商品中心 CRUD 完成"
   $ git push origin v0.1.0
   ```
3. 在 GitHub Releases 发布，附 Release Notes 描述本阶段内容
4. 未来想自动化可引入 release-please（前提是 commit message 严格遵循 Conventional Commits，本项目 [git-workflow.md](../../standards/git-workflow.md) 已规范）

## 小结

| 你应该记住的 |
|---|
| Tag 是 Git 概念（给 commit 起名），Release 是 GitHub 概念（Tag + 说明 + 附件） |
| 正式发版用 annotated tag（`git tag -a`） |
| SemVer：`MAJOR.MINOR.PATCH`，breaking / 新功能 / 修 bug 分别递增 |
| 0.x.x 表示 API 不稳定，1.0.0 表示稳定 |
| Release Notes 按 emoji + 类别分类（✨ / 🐛 / ⚠️ / 📦） |
| 自动化发版用 release-please（基于 Conventional Commits） |
| CHANGELOG.md 是仓库内的版本史，Release Notes 是 GitHub 上的发布页 |

下一章 [08-Issue 与项目管理](./08-issues-projects.md) 讲怎么用 GitHub 的 Issue / Projects / Labels 管理任务。
