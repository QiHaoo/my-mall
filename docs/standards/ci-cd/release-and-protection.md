# Release 发布与分支保护设计

> 本文档定义 my-mall 项目的 GitHub Release 自动发布工作流、Secrets/Variables 管理策略以及分支保护规则。
> 分支策略与发布流程的规范层面定义见 [git-workflow.md](../git-workflow.md)，本文档是其 GitHub 实施层面的补充。

---

## 一、Release 发布工作流概述

`release.yml` 是一个**可复用工作流（Reusable Workflow）**，通过 `workflow_call` 被统一编排器（orchestrator）调用。仅当 `v*.*.*` 格式的 tag 推送时，编排器才会触发 release 流程。

**职责边界**：

| 职责 | 说明 |
|------|------|
| 生成 Changelog | 从 commit history 自动提取 feat/fix/perf 等，按 type 分组 |
| 创建 GitHub Release | 基于 tag 创建 Release，自动填充 Changelog 作为 body |
| 标记 prerelease | 根据 tag 后缀（-alpha/-beta/-rc）自动判断是否为预发布 |
| 上传构建产物 | 可选，上传后端 jar 和前端 dist 作为 Release Assets |

**不涉及**：Docker 镜像构建与推送（由 `docker-publish.yml` 负责）、CD 部署（未来由 ArgoCD 负责）。

**编排器调用关系**：

```
orchestrator.yml (tag: v*.*.*)
├── backend-ci        ← 后端编译 + 测试
├── frontend-ci       ← 前端构建 + 测试
├── docker-publish    ← needs: [backend-ci, frontend-ci]，构建并推送镜像到 Harbor
└── release           ← needs: [docker-publish]，生成 Changelog + 创建 Release
```

> `release` job 的 `needs: [docker-publish]` 依赖关系在编排器中声明，确保只有镜像推送成功后才会创建 Release。

---

## 二、Job 结构

```
release (ubuntu-latest)
├── 1. Checkout (fetch-depth: 0)                    ← 完整拉取历史，Changelog 生成需要
├── 2. 等待 docker-publish 完成（needs: docker-publish）  ← 编排器层面保证，非 step
├── 3. 生成 Changelog（从 commit history 提取 feat/fix/perf 等）
├── 4. 创建 GitHub Release（softprops/action-gh-release@v2）
└── 5. 上传构建产物（可选，后端 jar + 前端 dist）
```

**步骤说明**：

| 步骤 | Action / 命令 | 说明 |
|------|--------------|------|
| 1. Checkout | `actions/checkout@v4` | `fetch-depth: 0` 拉取完整提交历史，Changelog 生成依赖全量 commit log |
| 2. 等待依赖 | — | 由编排器 `needs: [docker-publish]` 保证，release.yml 内无需显式等待 |
| 3. 生成 Changelog | `mikepenz/release-changelog-builder-action@v5` | 解析两个 tag 之间的 commit，按 conventional commit type 分组 |
| 4. 创建 Release | `softprops/action-gh-release@v2` | 使用上一步生成的 Changelog 作为 Release body |
| 5. 上传产物 | `actions/upload-release-asset` 或 gh CLI | 可选步骤，上传 jar/dist 供下载 |

---

## 三、Changelog 生成策略

### 3.1 工具选型

使用 [mikepenz/release-changelog-builder-action@v5](https://github.com/mikepenz/release-changelog-builder-action) 从 commit history 自动提取变更记录。

**选型理由**：
- 原生支持 Conventional Commits 规范，与项目 [git-workflow.md §3](../git-workflow.md) 定义的 commit type 对齐
- 按 commit type 分组输出，生成的 Changelog 结构清晰
- 支持自定义配置 JSON，灵活控制分组规则和输出模板
- 无需手动维护 CHANGELOG.md 文件

### 3.2 Commit Type 到分组标题的映射

与 [git-workflow.md §3](../git-workflow.md) 定义的 type 一一对应：

| Commit Type | 分组标题 | Emoji |
|-------------|---------|-------|
| `feat` | Features | 🚀 |
| `fix` | Fixes | 🐛 |
| `perf` | Performance | ⚡ |
| `refactor` | Refactor | ♻️ |
| `docs` | Documentation | 📚 |
| `ci` | CI/CD | 🔧 |
| `chore` / `style` / `test` / `revert` | Other Changes | 📦 |

### 3.3 配置文件

配置文件放在 `.github/changelog-configuration.json`：

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
| `categories` | 定义分组规则，`labels` 匹配 commit type 前缀 |
| `template` | 整体 Changelog 模板，`#{{CHANGELOG}}` 为分组内容占位符 |
| `pr_template` | 单条变更的输出格式，包含标题、作者、PR 编号 |
| `empty_template` | 无变更时的占位文本 |
| `max_tags_to_fetch` | 最多回溯的 tag 数量，确保能找到上一个 release tag |

---

## 四、prerelease 自动判断

### 4.1 判断规则

根据 tag 后缀自动判断是否为预发布版本，与 [git-workflow.md §5](../git-workflow.md) 语义化版本规范对齐：

| Tag 格式 | 后缀 | prerelease | 说明 |
|----------|------|------------|------|
| `v1.0.0` | 无 | `false` | 正式发布 |
| `v1.0.0-alpha` | `-alpha` | `true` | 内部测试版 |
| `v1.0.0-beta` | `-beta` | `true` | 公开测试版 |
| `v1.0.0-rc.1` | `-rc` | `true` | 候选发布版 |

### 4.2 实现方式

在 `softprops/action-gh-release@v2` 的 `prerelease` 参数中通过 GitHub Actions 表达式直接判断：

```yaml
prerelease: ${{ contains(github.ref_name, '-alpha') || contains(github.ref_name, '-beta') || contains(github.ref_name, '-rc') }}
```

- `github.ref_name`：触发 workflow 的 tag 名称（如 `v1.0.0-alpha`）
- `contains()`：检查 tag 名是否包含预发布后缀
- 纯 semver（`v1.0.0`）不包含任何后缀，返回 `false`，标记为正式发布

> **设计优势**：无需额外 step 或脚本，单一表达式完成判断，逻辑清晰且与 SemVer 规范严格对齐。

---

## 五、完整 workflow YAML

### 5.1 release.yml

文件路径：`.github/workflows/release.yml`

```yaml
# Release 发布工作流
# 通过 workflow_call 被编排器调用，生成 Changelog 并创建 GitHub Release
# 仅在 tag v*.*.* 推送时由编排器触发
name: Release

on:
  workflow_call:
    # 可复用工作流，由编排器 orchestrator.yml 调用
    # needs: [docker-publish] 依赖关系在编排器中声明

jobs:
  release:
    # 编排器层面已保证 needs: [docker-publish]，此处无需重复声明
    runs-on: ubuntu-latest
    # 创建 GitHub Release 需要 contents: write 权限
    # 最小化权限原则：仅授予 Release 创建所需权限，不能修改仓库其他配置
    permissions:
      contents: write

    steps:
      # 步骤 1：拉取完整提交历史（fetch-depth: 0）
      # Changelog 生成需要全量 commit log 来提取两个 tag 之间的变更
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      # 步骤 2：生成 Changelog
      # 从 commit history 提取 feat/fix/perf 等，按 conventional commit type 分组
      # configuration 指向 .github/changelog-configuration.json 配置文件
      - name: Build changelog
        id: build_changelog
        uses: mikepenz/release-changelog-builder-action@v5
        with:
          configuration: ".github/changelog-configuration.json"
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      # 步骤 3：创建 GitHub Release
      # tag_name 使用触发 workflow 的 tag 名
      # body 使用上一步生成的 Changelog
      # prerelease 通过 tag 后缀自动判断（-alpha/-beta/-rc → true）
      - name: Create release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ github.ref_name }}
          name: ${{ github.ref_name }}
          body: ${{ steps.build_changelog.outputs.changelog }}
          draft: false
          prerelease: ${{ contains(github.ref_name, '-alpha') || contains(github.ref_name, '-beta') || contains(github.ref_name, '-rc') }}
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### 5.2 编排器调用示例

编排器 `orchestrator.yml` 中的调用关系（仅展示 release 相关部分）：

```yaml
name: CI/CD Orchestrator

on:
  push:
    tags:
      - 'v*.*.*'      # 仅 tag 推送触发完整发布流水线

jobs:
  # 前置 CI 任务（省略详细定义）
  backend-ci:
    uses: ./.github/workflows/backend-ci.yml
  frontend-ci:
    uses: ./.github/workflows/frontend-ci.yml

  # Docker 镜像构建与推送
  docker-publish:
    needs: [backend-ci, frontend-ci]
    uses: ./.github/workflows/docker-publish.yml

  # Release 发布（依赖 docker-publish 完成）
  release:
    needs: [docker-publish]
    uses: ./.github/workflows/release.yml
```

### 5.3 关键设计说明

| 设计点 | 实现 | 理由 |
|--------|------|------|
| `fetch-depth: 0` | 拉取完整 Git 历史 | Changelog 生成需要遍历两个 tag 之间的所有 commit |
| `permissions: contents: write` | 仅授予 Release 创建权限 | 最小化权限，不能修改仓库设置、Secrets 等 |
| `GITHUB_TOKEN` | 使用 Actions 自动注入的 Token | 无需额外配置 Secret，Token 权限受 `permissions` 字段约束 |
| `prerelease` 表达式判断 | `contains()` 检查 tag 后缀 | 无需额外 step，单一表达式完成 |
| `configuration` 外部文件 | 引用 `.github/changelog-configuration.json` | 配置与逻辑分离，便于维护 |

---

## 六、GitHub Secrets 与 Variables 清单

### 6.1 Secrets（敏感信息）

Secrets 用于存储敏感信息，在 workflow 中引用时不会出现在日志中。

| Secret 名 | 用途 | 配置位置 | 使用方 |
|-----------|------|---------|--------|
| `HARBOR_USERNAME` | Harbor CI 机器人账号（CI 专用，非个人账号） | Repo Settings → Secrets and variables → Actions → Secrets | `docker-publish.yml` |
| `HARBOR_PASSWORD` | Harbor CI 机器人密码（或 Access Token） | Repo Settings → Secrets and variables → Actions → Secrets | `docker-publish.yml` |

> **安全要求**：
> - Harbor CI 机器人账号应仅授予推送镜像到指定项目的权限，禁止管理权限
> - 密码优先使用 Access Token 而非明文密码，便于过期轮换
> - 定期轮换凭据（建议每 90 天）

### 6.2 Variables（非敏感配置）

Variables 用于存储非敏感的配置信息，在 workflow 日志中可见。

| Variable 名 | 值示例 | 用途 | 使用方 |
|-------------|--------|------|--------|
| `HARBOR_REGISTRY` | `registry.mall.local` | Harbor 镜像仓库地址 | `docker-publish.yml` |

### 6.3 为什么 HARBOR_REGISTRY 用 Variables 而非 Secrets

| 维度 | Secrets | Variables |
|------|---------|-----------|
| 敏感性 | 敏感信息（密码、Token） | 非敏感信息（地址、配置项） |
| 日志可见性 | 自动脱敏（显示为 `***`） | 明文可见 |
| 适用场景 | 凭据、密钥 | 域名、端口、环境标识 |

`HARBOR_REGISTRY` 是镜像仓库的公开访问地址，不属于敏感信息。使用 Variables 的优势：

1. **日志可读性**：构建日志中可见完整镜像地址，便于调试
2. **配置清晰**：非敏感配置与敏感凭据分离，职责明确
3. **直接引用**：在 workflow 中通过 `${{ vars.HARBOR_REGISTRY }}` 直接引用，无需担心脱敏

### 6.4 未来 CD 阶段需补充的 Secrets

以下 Secrets 属于 CD（持续部署）范围，本次 CI 阶段不涉及，待引入 ArgoCD 时补充：

| Secret 名 | 用途 | 配置位置 | 阶段 |
|-----------|------|---------|------|
| `KUBE_CONFIG` | Kubernetes 集群连接配置（如需直接 kubectl 部署） | Repo Secrets | CD |
| `ARGOCD_TOKEN` | ArgoCD API Token（GitOps 自动同步触发） | Repo Secrets | CD |
| `ARGOCD_SERVER` | ArgoCD 服务地址 | Repo Variables | CD |
| `DEPLOY_ENV` | 部署环境标识（staging / production） | Repo Variables | CD |

> 上述清单为规划参考，实际配置在 CD 设计文档中详细定义。

---

## 七、分支保护规则

### 7.1 main 分支保护（生产级强制）

main 分支是生产分支，始终保持可部署状态，保护规则必须最严格。

| 规则 | 设置 | 说明 |
|------|------|------|
| Require a pull request before merging | ✅ 启用 | 禁止直接 push，所有变更必须通过 PR 合并 |
| └ Require approvals | ✅ 至少 1 个 | 至少 1 人 review 通过才能合并 |
| └ Require review from Code Owners | ✅ 启用 | 关键路径变更需 Code Owner 审批 |
| Require status checks to pass | ✅ 启用 | CI 必须通过才能合并 |
| └ 必须通过的 check | `backend-ci` / `frontend-ci` | 至少这两个 CI 任务通过 |
| └ Require branches to be up to date | ✅ 启用 | 合并前必须 rebase 到目标分支最新，避免集成冲突 |
| Require conversation resolution | ✅ 启用 | 所有 review 评论必须标记为 resolved |
| Do not allow bypassing the above settings | ✅ 启用 | 管理员也不能跳过检查，最高安全级别 |
| Restrict who can push to matching branches | ✅ 仅限管理员 | 即使有 push 权限也限制直接推送 |

> **生产级要求**：`Do not allow bypassing` 必须开启。这意味着即使仓库管理员也无法跳过 CI 检查直接合并，确保所有代码变更都经过完整的 CI 验证。

### 7.2 develop 分支保护（开发主线，略宽松）

develop 分支是开发主线，所有 feature 分支合并到此。保护规则略宽松以适应快速迭代。

| 规则 | 设置 | 说明 |
|------|------|------|
| Require a pull request before merging | ✅ 启用 | feature → develop 走 PR |
| └ Require approvals | ✅ 至少 1 个 | 至少 1 人 review |
| Require status checks to pass | ✅ 启用 | CI 必须通过才能合并 |
| └ 必须通过的 check | `backend-ci` / `frontend-ci` | CI 通过即可 |
| Require conversation resolution | ✅ 启用 | review 评论需解决 |
| Require branches to be up to date | ❌ 不启用 | 开发节奏快，避免频繁 rebase 烦扰 |
| Do not allow bypassing the above settings | ❌ 不启用 | 管理员可跳过（紧急修复场景） |

**develop 分支不强制 "branches up to date" 的理由**：

develop 分支合并频率高（每个 feature 完成都要合并），如果强制 up to date，每次合并前都要 rebase 最新代码再重新跑 CI。在多人并行开发时会导致：

- 频繁的 rebase 操作增加开发负担
- CI 反复重跑消耗资源
- 合并时间窗口变长，降低开发效率

因此 develop 分支允许在 CI 通过但非最新的情况下合并，由合并后的集成测试兜底。

### 7.3 配置路径

在 GitHub 仓库中配置分支保护的步骤：

```
GitHub 仓库页面
  → Settings
    → Branches
      → Branch protection rules
        → Add branch rule
          → Branch name pattern: main (或 develop)
          → 勾选对应保护规则
          → Create
```

**配置顺序建议**：先配置 main 分支保护，再配置 develop 分支保护。确保生产分支优先得到保护。

---

## 八、版本发布流程

完整发布流程与 [git-workflow.md §4.3 发布上线](../git-workflow.md) 对齐，补充 CI 自动触发环节。

### 8.1 发布步骤

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

# 5. 推送 main 分支和标签（标签推送触发 CI 发布流水线）
git push origin main --tags

# 6. CI 自动触发（编排器 orchestrator.yml）：
#    backend-ci + frontend-ci → docker-publish → release
#    GitHub Release 自动创建，Changelog 自动生成

# 7. 合并回 develop，同步 release 分支上的修复
git checkout develop
git merge --no-ff release/v1.0.0
git push origin develop

# 8. 删除 release 分支
git branch -d release/v1.0.0
git push origin --delete release/v1.0.0
```

### 8.2 预发布版本流程

预发布版本（alpha/beta/rc）流程与正式版一致，仅 tag 名称不同：

```bash
# 创建预发布标签
git tag -a v1.0.0-rc.1 -m "Release v1.0.0-rc.1"

# 推送标签触发 CI
git push origin v1.0.0-rc.1

# CI 自动判断 prerelease=true，GitHub Release 标记为 Pre-release
```

### 8.3 流程与 CI 对应关系

| 步骤 | Git 操作 | CI 触发 | 产物 |
|------|---------|---------|------|
| 合并到 main | `git merge --no-ff` | — | — |
| 打标签 | `git tag -a v1.0.0` | — | — |
| 推送标签 | `git push origin main --tags` | orchestrator.yml | — |
| CI 执行 | — | backend-ci + frontend-ci | 测试报告 |
| Docker 推送 | — | docker-publish | Harbor 镜像 |
| Release 创建 | — | release | GitHub Release + Changelog |

---

## 九、关键设计决策

| # | 决策 | 方案 | 影响 |
|---|------|------|------|
| 1 | Changelog 自动生成 | 使用 `mikepenz/release-changelog-builder-action` 从 commit history 提取 | 无需手动维护 CHANGELOG.md，Changelog 与 commit 规范自动对齐 |
| 2 | prerelease 自动判断 | 基于 tag 后缀（`-alpha`/`-beta`/`-rc`）通过 `contains()` 表达式判断 | alpha/beta/rc 自动标记为预发布，无需手动设置 |
| 3 | permissions 最小化 | release job 仅授予 `contents: write` | 仅能创建 Release，不能修改仓库其他配置（Secrets、Settings 等） |
| 4 | Secrets 与 Variables 分离 | 敏感信息（Harbor 凭据）用 Secrets，非敏感配置（仓库地址）用 Variables | 安全且清晰，日志中非敏感配置明文可见便于调试 |
| 5 | main 分支禁止 bypass | `Do not allow bypassing` 开启 | 管理员也不能跳过检查，最高安全级别，所有变更必须经过 CI |
| 6 | develop 分支略宽松 | 不强制 `branches up to date` | 开发节奏快，避免频繁 rebase 烦扰，由集成测试兜底 |
| 7 | 发布流程与 git-workflow.md 对齐 | release 分支 → main → tag → CI 全链路 | 流程一致，CI 触发策略与分支策略严格对齐 |

---

## 十、与 git-workflow.md 的关联

本文档的分支保护规则和发布流程是对 [git-workflow.md](../git-workflow.md) 的实施补充：

| 维度 | git-workflow.md（规范层） | 本文档（实施层） |
|------|-------------------------|-----------------|
| 分支策略 | 定义 main/develop/feature/fix/hotfix/release 分支模型 | 定义 GitHub 分支保护的具体配置规则 |
| 发布流程 | 定义 release 分支 → main → tag 的 Git 操作流程 | 定义 tag 推送后 CI 自动触发 Release 创建的自动化流程 |
| Commit 规范 | 定义 feat/fix/perf 等 type 及格式 | 定义 commit type 到 Changelog 分组的映射 |
| 版本规范 | 定义 SemVer v*.*.* 格式和预发布后缀 | 定义 prerelease 自动判断逻辑（基于 tag 后缀） |

**对齐关系**：

1. **CI 触发策略与分支策略对齐**：
   - tag `v*.*.*` 推送 → 触发完整发布流水线（CI → Docker → Release）
   - PR 到 main/develop → 触发 backend-ci + frontend-ci（合并门禁）
   - feature/fix 分支 push → 触发 backend-ci + frontend-ci（开发反馈）

2. **Changelog 与 Commit 规范对齐**：
   - Changelog 分组直接映射 [git-workflow.md §3](../git-workflow.md) 定义的 commit type
   - 要求开发者严格遵循 conventional commits 规范，否则 Changelog 分组不准确

3. **prerelease 与版本规范对齐**：
   - prerelease 判断逻辑与 [git-workflow.md §5](../git-workflow.md) 语义化版本规范的预发布后缀定义一致
   - `-alpha` → 内部测试版、`-beta` → 公开测试版、`-rc` → 候选发布版

4. **分支保护与分支策略对齐**：
   - main 分支保护规则强制 PR + CI，落实 git-workflow.md "永远不要直接推送到 main" 的规范
   - develop 分支保护规则要求 PR + CI，落实 feature → develop 走 PR 的规范
