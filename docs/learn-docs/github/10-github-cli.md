# 10 · GitHub CLI（gh）

> 每次开 Issue / PR / 看 workflow 状态都要打开浏览器，烦。GitHub 官方命令行工具 `gh` 让你在终端里完成绝大多数 GitHub 操作。本章讲安装、认证、常用命令，以及在 CI 里用 gh 自动化。

## 10.1 gh 是什么

GitHub CLI（命令 `gh`）是 GitHub 官方推出的命令行工具，2020 年发布。

- 用一个命令完成 Issue / PR / Release / Actions / repo / codespace 等操作
- 调用 GitHub REST / GraphQL API，但封装得像 git 一样顺手
- 支持脚本化（CI 里常用）
- 跨平台（Windows / macOS / Linux）

和 git 的关系：git 管「版本控制」，gh 管「GitHub 平台操作」。两者配合。

## 10.2 安装

### Windows

```powershell
# 用 winget（推荐）
$ winget install --id GitHub.cli

# 或用 Scoop
$ scoop install gh

# 或用 Chocolatey
$ choco install gh
```

### macOS

```bash
$ brew install gh
```

### Linux

```bash
# Ubuntu / Debian
$ curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg
$ echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null
$ sudo apt update && sudo apt install gh

# Fedora / CentOS
$ sudo dnf install gh
```

### 验证

```bash
$ gh --version
gh version 2.45.0 (2024-01-15)
```

## 10.3 认证

```bash
$ gh auth login
? What account do you want to log into? GitHub.com
? What is your preferred protocol for Git operations? SSH
? Upload your SSH public key to your GitHub account? /Users/you/.ssh/id_ed25519.pub
? How would you like to authenticate GitHub CLI? Login with a web browser

! First copy your one-time code: XXXX-XXXX
Press Enter to open github.com in your browser...
```

浏览器打开后输入一次性代码，授权完成。

### 验证认证

```bash
$ gh auth status
github.com
  ✓ Logged in to github.com as qihao
  ✓ Git operations for github.com configured as ssh.
  ✓ Token: gho_xxxxxxxxxxxx
```

### 在 CI 中认证

CI 环境不能用浏览器，用 token：

```bash
$ echo $GH_TOKEN | gh auth login --with-token
```

GitHub Actions 里有内置的 `GITHUB_TOKEN`，但范围受限。需要更广权限时创建 Personal Access Token 存为 secret（名 `GH_TOKEN`），workflow 里：

```yaml
env:
  GH_TOKEN: ${{ secrets.GH_TOKEN }}
steps:
  - run: gh issue create --title "..." --body "..."
```

## 10.4 常用命令

gh 的命令是分组式的：`gh <group> <action>`。常用 group：

| Group | 作用 |
|-------|------|
| `gh repo` | 仓库操作 |
| `gh issue` | Issue |
| `gh pr` | Pull Request |
| `gh workflow` | Actions workflow |
| `gh run` | Actions 单次运行 |
| `gh release` | Release |
| `gh codespace` | Codespaces |
| `gh secret` | 仓库 secret |
| `gh variable` | 仓库 variable |
| `gh extension` | 安装扩展命令 |
| `gh api` | 直接调 GitHub API（万能后门） |

### 10.4.1 仓库操作 `gh repo`

```bash
# 克隆（自动用 SSH/HTTPS 按你的偏好）
$ gh repo clone QiHaoo/my-mall

# 在浏览器打开当前仓库主页
$ gh repo view --web

# 创建新仓库（在当前目录）
$ gh repo create my-mall --public --source=. --remote=origin --push

# Fork 一个仓库到自己账号
$ gh repo fork vuejs/core --clone

# 查看仓库信息
$ gh repo view QiHaoo/my-mall
```

### 10.4.2 Issue 操作 `gh issue`

```bash
# 列出 issue（默认前 30 条 open）
$ gh issue list

# 只看自己负责的
$ gh issue list --assignee @me

# 按标签过滤
$ gh issue list --label bug --label "priority:high"

# 看某条 issue 详情
$ gh issue view 42

# 在浏览器打开
$ gh issue view 42 --web

# 新建 issue（自动用编辑器写 body）
$ gh issue create --title "购物车勾选状态丢失" --body "刷新页面后..."

# 用模板新建
$ gh issue create --template bug_report.md

# 加标签 / 指派
$ gh issue edit 42 --add-label "bug" --add-assignee @me

# 关闭 / 重新打开
$ gh issue close 42
$ gh issue reopen 42

# 评论
$ gh issue comment 42 --body "已修复，请验证"
```

### 10.4.3 Pull Request 操作 `gh pr`

```bash
# 列出 PR
$ gh pr list

# 看详情
$ gh pr view 42

# 创建 PR（从当前分支推到 main，自动以当前分支为 head）
$ gh pr create --base main --head feature/cart-batch \
    --title "feat(cart): 购物车支持批量勾选" \
    --body "## 改了什么..."

# 当前分支对应的 PR
$ gh pr status

# 检出别人 PR 的分支到本地（review 用）
$ gh pr checkout 42

# Review + Approve
$ gh pr review 42 --approve --body "LGTM"

# Request changes
$ gh pr review 42 --request-changes --body "请修复 N+1 查询"

# 合并
$ gh pr merge 42 --squash --delete-branch   # squash 合并并删分支

# 关闭
$ gh pr close 42
```

### 10.4.4 Actions workflow `gh workflow`

```bash
# 列出仓库所有 workflow
$ gh workflow list

# 看某个 workflow 最近运行
$ gh workflow list --all
$ gh workflow view "Deploy MkDocs to GitHub Pages"

# 手动触发 workflow_dispatch 类型的 workflow
$ gh workflow run mkdocs.yml
$ gh workflow run mkdocs.yml --ref feature/test

# 带参数触发
$ gh workflow run deploy.yml -f environment=production -f dry_run=true

# 禁用 / 启用 workflow
$ gh workflow disable mkdocs.yml
$ gh workflow enable mkdocs.yml
```

### 10.4.5 Actions 运行 `gh run`

```bash
# 列出最近运行
$ gh run list

# 只看失败的
$ gh run list --status failure

# 看某次运行详情
$ gh run view 12345678

# 实时看日志
$ gh run watch 12345678

# 看某个 job 的日志
$ gh run view 12345678 --log | grep -i error

# 重新跑失败的
$ gh run rerun 12345678 --failed

# 取消正在跑的
$ gh run cancel 12345678
```

### 10.4.6 Release `gh release`

```bash
# 列出
$ gh release list

# 看详情
$ gh release view v1.0.0

# 创建（自动打 tag）
$ gh release create v1.0.0 \
    --title "v1.0.0 - 首个正式版" \
    --notes "首个版本发布" \
    --target main \
    mall-product/target/mall-product.jar   # 附件

# 上传附件到已有 release
$ gh release upload v1.0.0 mall-order/target/mall-order.jar

# 下载 release
$ gh release download v1.0.0
$ gh release download v1.0.0 --pattern '*.jar'

# 删除
$ gh release delete v1.0.0
```

### 10.4.7 Secret / Variable

```bash
# 列出 secret（不能看值）
$ gh secret list

# 设置 secret（会提示输入值，不在 history 留痕）
$ gh secret set DOCKER_PASSWORD
> Paste your secret: ********

# 从文件设
$ gh secret set DOCKER_PASSWORD < password.txt

# 从 stdin 设
$ echo "my-token" | gh secret set GH_TOKEN

# 删除
$ gh secret delete DOCKER_PASSWORD

# Variable（明文变量）类似
$ gh variable set JAVA_VERSION --body "21"
$ gh variable list
```

### 10.4.8 直接调 API `gh api`

需要 gh 没封装的接口时，直接调 GitHub REST/GraphQL API：

```bash
# 列出仓库 webhook
$ gh api repos/QiHaoo/my-mall/hooks

# 创建一个 issue
$ gh api --method POST repos/QiHaoo/my-mall/issues \
    -f title="测试 API 创建" \
    -f body="通过 gh api 创建的 issue"

# GraphQL 查询
$ gh api graphql -f query='
  query {
    viewer {
      login
      repositories(first: 5) {
        nodes { name }
      }
    }
  }'
```

`gh api` 是万能后门，凡 GitHub API 支持的都能做。

## 10.5 实用技巧

### 10.5.1 别名

`gh alias set` 自定义快捷命令：

```bash
# 把 gh issue list 缩成 gh il
$ gh alias set il 'issue list'

# 复合别名
$ gh alias set bugs 'issue list --label bug --assignee @me'

# 用
$ gh bugs
```

### 10.5.2 扩展 `gh extension`

社区扩展，装新功能：

```bash
# 列出扩展
$ gh extension search

# 装 markdown 渲染扩展
$ gh extension install dlvhdr/gh-dash

# 用：dashboard 看 PR 状态
$ gh dash
```

热门扩展：
- `dlvhdr/gh-dash`：PR/Issue 仪表盘
- `github/gh-skrep`：代码搜索增强
- `kyleliao/gh-dev`：开发环境快捷

### 10.5.3 在脚本里用 gh

CI 里典型用法：

```yaml
# 等 CI 通过后自动合并 PR
- run: |
    gh pr merge ${{ github.event.pull_request.number }} \
      --squash \
      --delete-branch \
      --admin
  env:
    GH_TOKEN: ${{ secrets.GH_TOKEN }}

# workflow 失败发 Issue 通知
- if: failure()
  run: |
    gh issue create \
      --title "CI 失败：${{ github.run_id }}" \
      --body "见 ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}" \
      --label ci-failure
```

### 10.5.4 配合 jq 处理 JSON

gh 输出可转 JSON，配合 `jq` 提取：

```bash
# 列出所有 open PR 的标题和 URL
$ gh pr list --json title,url | jq -r '.[] | "\(.title) - \(.url)"'

# 看 main 分支最近 5 次 commit 作者
$ gh api repos/QiHaoo/my-mall/commits?sha=main\&per_page=5 \
    | jq -r '.[].commit.author.name'
```

## 10.6 常用速查表

| 我想…… | 命令 |
|--------|------|
| 浏览器打开当前仓库 | `gh repo view --web` |
| 看我负责的 issue | `gh issue list --assignee @me` |
| 当前分支建 PR | `gh pr create` |
| 看 PR 检查状态 | `gh pr checks` |
| 合并 PR | `gh pr merge <id> --squash` |
| 看 CI 最近运行 | `gh run list` |
| 实时盯 CI | `gh run watch` |
| 看 CI 失败日志 | `gh run view --log-failed` |
| 手动触发 workflow | `gh workflow run <file>` |
| 创建 release | `gh release create v1.0.0` |
| 设 secret | `gh secret set NAME` |
| API 调用 | `gh api ...` |

## 10.7 本项目对照

本项目日常操作示例：

```bash
# 推完一个 feature 分支，建 PR
$ git push -u origin feature/cart-batch
$ gh pr create --base main --title "feat(cart): 批量勾选" --body "..."

# 看 workflow 跑完没
$ gh run list --workflow=mkdocs.yml --limit 3
$ gh run watch

# 失败了看日志
$ gh run view --log-failed

# 改完文档 push 后，盯部署
$ git push origin main
$ gh run watch
# 等出现 ✓ Deploy to GitHub Pages，访问 https://qihao0o.github.io/my-mall/
```

## 小结

| 你应该记住的 |
|---|
| gh 是 GitHub 官方 CLI，终端里做 Issue/PR/Workflow/Release 等 |
| 安装：`winget install GitHub.cli`（Windows）/ `brew install gh`（macOS） |
| 认证：`gh auth login`（交互）/ token stdin（CI） |
| 常用：`gh pr create` / `gh run watch` / `gh release create` |
| 万能后门：`gh api` 直接调 GitHub API |
| 别名 `gh alias set` + 扩展 `gh extension install` 扩展能力 |
| CI 里用 gh 自动化（合并 PR、失败建 issue 通知等） |

下一章 [11-本项目实践串联](./11-project-practice.md) 把前面所有概念落到本项目的 `.github/workflows/mkdocs.yml` 上，逐行解读。
