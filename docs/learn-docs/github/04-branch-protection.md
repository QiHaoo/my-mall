# 04 · 分支保护与规则

> 上一章讲 PR 流程，但全靠自觉——谁能保证没人图省事直接 push 到 `main`？分支保护规则就是把这些约定**强制化**：没 Review 不能合、CI 没过不能合、不能直接 push 到主干。本章讲 Protection Rules（旧版）和 Repository Rulesets（新版，2023+ 推荐）。

## 4.1 为什么需要保护规则

没有保护规则时，`main` 分支对任何有写权限的人都是「裸奔」的：

- 直接 `git push origin main` 推一段未审过的代码 → 绕过 Review
- PR 没等 CI 跑完就 Merge → 引入未测试代码
- 强推 `git push --force` 改写 main 历史 → 别人拉代码全乱
- 删除 main 分支 → 灾难

保护规则把这些都禁掉，让 GitHub Flow 真正落地。

## 4.2 两种配置方式

GitHub 历史上提供过两套配置，现在**推荐用 Rulesets**：

| | Branch protection rules（旧） | Repository Rulesets（新，2023+） |
|---|-------------------------------|----------------------------------|
| 推出时间 | 早期就有 | 2023 年 |
| 路径 | Settings → Branches | Settings → Rules → Rulesets |
| 粒度 | 按分支名 / 通配符 | 按分支名 / 通配符 / Tag / 任意 ref |
| 作用范围 | 单仓库 | 可仓库级，也可组织级（一条规则管多仓库） |
| 可叠加 | 单层规则 | 多条 ruleset 可叠加，按优先级生效 |
| 状态可见性 | 配置项较散 | 有 Bypass、Enforcement status 等更细控制 |
| 旧规则是否还能用 | 能，但 GitHub 推荐迁移到 Rulesets | — |

> 实际操作时，新仓库直接用 Rulesets；老仓库可能还有旧 Branch protection rules。两者可以共存，规则取并集（更严的生效）。

## 4.3 配置入口

仓库 **Settings → Rules → Rulesets → New ruleset → New branch ruleset**。

> 注：免费账号的 public/private 仓库都能用 Rulesets。私组织级 Rulesets 需要 GitHub Enterprise。

## 4.4 常用规则项（Rulesets）

下面是最常用的几类规则，按场景分组讲解。

### 4.4.1 限制谁能 push

**Require a pull request before merging**（最核心）

勾上后：
- 不能直接 push 到目标分支
- 必须通过 PR 合并
- 可设置「Required approvals」数量（如至少 1 个 Approve 才能合并）
- 可设置「Dismiss stale pull request approvals when new commits are pushed」（PR 有新 commit 时，旧 Approve 自动失效，需重新审）
- 可设置「Require review from Code Owners」（必须 Code Owner 审，见 4.6）

**Restrict who can push to matching branches**

可指定允许 push 的用户/团队（一般留空，配合上一条让所有人都必须走 PR）。

### 4.4.2 强制 CI 通过

**Require status checks to pass before merging**

勾上后，PR 合并前必须等指定 CI checks 全部通过：

- 「Require branches to be up to date before merging」：合并前必须把 base 分支最新代码合并到 feature 分支（避免合到旧 base 上）
- 「Status checks」列表：选哪些 check 必须通过（如 `build`、`test`、`lint`）。**只有历史上跑过的 check 才会出现在选项里**，第一次配置时可能要等 PR 跑过一次 CI 才能选。

本项目对应：`.github/workflows/mkdocs.yml` 跑出来的 check 名是 `build` / `deploy`（job 名）。

### 4.4.3 强制线性历史

**Require linear history**

禁止产生 merge commit，要求所有合并用 squash 或 rebase。配合 03 章讲的「只开 Squash 和 Rebase 合并方式」食用，main 历史完全线性。

### 4.4.4 禁止强推和删除

**Do not allow bypassing the above settings** / **Restrict force pushes** / **Block force pushes**

- 禁止 `git push --force` / `--force-with-lease`
- 禁止删除分支

main 分支务必禁强推禁删除。

### 4.4.5 Code Scanning 结果

**Require code scanning results**

如果开了 GitHub Code Scanning（CodeQL，见 09 章），可强制 PR 合并前扫描结果无严重漏洞。

### 4.4.6 部署环境

**Require deployments to pass before merging**

要求指定 environment 的部署成功才能合并（适合有 staging 环境的发布流程）。

## 4.5 一个实用的 main 分支 Ruleset 配置

适合本项目（个人学习项目，未来扩展到小团队）：

```
New branch ruleset:
  Name: main-protection
  Enforcement status: Active
  Target branches: main

  ┌─ Bypass list: (空，谁都不能绕过)
  │
  ├─ Require a pull request before merging:
  │    Required approvals: 1
  │    Dismiss stale approvals on new commits: ✅
  │
  ├─ Require status checks to pass:
  │    Require branches up to date: ✅
  │    Required checks: build (等 workflow 跑过一次后选)
  │
  ├─ Require linear history: ✅
  ├─ Do not allow force pushes: ✅
  └─ Do not allow deletions: ✅
```

仓库设置里再配合：

```
Settings → General → Pull Requests → Merge button:
  ☐ Allow merge commits        (关掉)
  ☑ Allow squash merging       (开)
  ☐ Allow rebase merging       (关掉，简化为只用 squash)
  ☑ Automatically delete head branches  (PR 合并后自动删 feature 分支)
```

## 4.6 CODEOWNERS

`CODEOWNERS` 文件让 GitHub 知道「这个目录的代码改动必须由谁审」。放在仓库根目录、`.github/` 或 `docs/` 下。

格式（每行：路径 + 负责人）：

```
# 仓库根目录的 CODEOWNERS 示例

# 默认 owner
*                              @QiHaoo

# mall-gateway 改动必须 gateway 团队审
/mall-gateway/                 @QiHaoo @gateway-team

# mall-order 改动必须 order 维护者审
/mall-order/                   @order-maintainer

# docs 改动任意 maintainer 都行
/docs/                         @QiHaoo

# 所有 .yml 配置文件
*.yml                          @QiHaoo

# 全局 CI 配置必须 SRE 审
/.github/                      @sre-team
```

配合 Rulesets 的「Require review from Code Owners」，PR 触及某目录时，对应的 owner 会**自动被指派为 Reviewer**，且必须他们 approve 才能合并。

## 4.7 环境（Environments）

环境是 GitHub 给部署流程加的「保护层」，主要用于 Actions 部署 job（05 章会用到）。

仓库 **Settings → Environments → New environment**，可配置：

| 配置 | 作用 |
|------|------|
| Required reviewers | 部署到这个环境前必须有人 approve |
| Wait timer | approve 后等待 N 分钟才允许部署（冷静期） |
| Deployment branches | 限制哪些分支能部署到这个环境（如只 main 能部署 prod） |
| Environment secrets | 这个环境专属的密钥，只在部署 job 注入 |

典型用法：

- `production` 环境：必须 2 人 approve + 只 main 分支能触发 + 存生产密钥
- `staging` 环境：必须 1 人 approve + main 或 release/* 能触发
- `github-pages` 环境：GitHub Pages 部署专用，本项目 mkdocs.yml 里 `environment: github-pages` 就是它

> 本项目 mkdocs.yml workflow 的 deploy job 用了 `environment: github-pages`，这是 GitHub Pages 自动创建的环境，可以额外配 reviewer 但默认无需审批。

## 4.8 Bypass 与管理员权限

Ruleset 里可设 Bypass list（绕过列表），列出的人不受这条 ruleset 限制。

- 谨慎用：bypass 等于「规则对你无效」，破坏保护意义
- 仓库 admin 默认能改规则、能在 UI 上强制合并（force merge），但**不**自动 bypass ruleset——除非显式加入 bypass list

最佳实践：
- 生产仓库 bypass list 留空
- admin 也走 PR + Review + CI
- 紧急修复走 hotfix 流程，不走 bypass

## 4.9 本项目现状与建议

本项目目前 **未** 配置分支保护（个人项目初期方便快速迭代）。建议在 `main` 上有了一定内容后配置：

1. 仓库 Settings → Rules → Rulesets → New branch ruleset
2. 按 4.5 的配置设好
3. 配置 `CODEOWNERS`（即使只有自己一人，也保留扩展余地）
4. 关闭 merge commit 合并方式，只留 squash

这样即使将来加入协作者，规则已经在位，不会出现「人多了才想起来加规则，结果规则太严打乱节奏」。

## 小结

| 你应该记住的 |
|---|
| 用 Repository Rulesets（新版）保护分支，不要靠自觉 |
| main 分支至少配：必须 PR + 至少 1 Approve + CI 通过 + 禁强推禁删除 |
| 合并方式只开 Squash，配合 Require linear history，main 历史清爽线性 |
| CODEOWNERS 让「这块代码归谁审」自动化 |
| Environments 给部署 job 加审批门 + 环境密钥隔离 |

下一章 [05-GitHub Actions](./05-github-actions.md) 进入重头戏——本项目 mkdocs.yml 那个 workflow 文件，每一行都是什么意思。
