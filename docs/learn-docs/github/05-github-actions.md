# 05 · GitHub Actions 详解

> 本章是整个 GitHub 学习笔记的重头戏。本项目的 [.github/workflows/mkdocs.yml](https://github.com/QiHaoo/my-mall/blob/main/.github/workflows/mkdocs.yml) 就是一个 GitHub Actions workflow。读完本章你能：看懂任意 workflow 文件、自己写一个 CI/CD、理解 Runner / Action / Job / Step 的关系、知道 matrix / concurrency / permissions 这些高级用法。

## 5.1 CI / CD 是什么

先理清概念：

| 缩写 | 全称 | 做什么 |
|------|------|--------|
| CI | Continuous Integration 持续集成 | 代码 push / PR 时**自动跑测试 + 构建**，确保不破坏主干 |
| CD | Continuous Delivery 持续交付 | CI 通过后**自动打包产物**，随时可部署（部署动作可能手动触发） |
| CD | Continuous Deployment 持续部署 | CI 通过后**自动部署到生产**，无需人工干预 |

通常连写 CI/CD，三者界线看团队。本项目文档站就是 CD（自动部署到 GitHub Pages）。

GitHub Actions 是 GitHub 内置的 CI/CD 平台，对标 GitLab CI、Jenkins、CircleCI、Travis CI。

## 5.2 核心概念

| 概念 | 说明 | 类比 |
|------|------|------|
| **Workflow** | 一个 YAML 文件，定义一套自动化流程 | 一个「菜谱」 |
| **Job** | workflow 里的一组任务，串行或并行执行 | 菜谱里的「工序」 |
| **Step** | job 里的一个步骤，执行 shell 命令或调用 action | 工序里的「动作」 |
| **Action** | 可复用的 step 单元，封装好的「插件」 | 厨具 |
| **Runner** | 实际执行 job 的机器（GitHub 托管或自建） | 厨师 |
| **Artifact** | job 产出的文件（如构建产物），可上传留待下个 job 用 | 成品菜 |
| **Trigger** | 触发 workflow 的事件（push、PR、定时、手动…） | 开火信号 |
| **Environment** | 部署目标环境（如 prod、staging），可加审批门和密钥 | 上菜窗口 |

层级关系：

```
Workflow (.github/workflows/xxx.yml)
└── Job (并行或串行)
    └── Step (顺序执行)
        ├── run: shell 命令
        └── uses: 调用某个 Action
```

## 5.3 Workflow 文件放在哪

必须是 `.github/workflows/` 目录下的 `.yml` / `.yaml` 文件。文件名随意，多个 workflow 各占一个文件。

```
.github/
└── workflows/
    ├── mkdocs.yml         # 文档站自动部署
    ├── ci.yml             # 后端 CI（跑测试）
    ├── release.yml        # 发布时打 tag 等
    └── codeql.yml         # 安全扫描
```

## 5.4 Workflow 语法逐项讲

下面把所有常用字段讲一遍。**强烈建议对照本项目 mkdocs.yml 一起看**（11 章会逐行解读）。

### 5.4.1 `name`：workflow 显示名

```yaml
name: Deploy MkDocs to GitHub Pages
```

显示在仓库 Actions 标签页的 workflow 列表。省略时用文件名。

### 5.4.2 `on`：触发条件

注意是 `on` 不是 `when`。可以是单个事件、事件列表、或事件 + 过滤器。

**单事件：**
```yaml
on: push
```

**多事件：**
```yaml
on: [push, pull_request]
```

**带过滤器：**

```yaml
on:
  push:
    branches: [main, 'release/*']      # 推到这些分支才触发
    paths:                              # 改了这些路径才触发
      - 'docs/**'
      - 'mkdocs.yml'
      - '.github/workflows/mkdocs.yml'
    tags:                               # 推这些 tag 才触发（与 branches 互斥分组）
      - 'v*'
  pull_request:
    branches: [main]                    # PR 目标分支是 main 才触发
    types: [opened, synchronize, reopened]  # PR 的哪些动作触发
  workflow_dispatch:                    # 手动触发（Actions 页面出现 Run workflow 按钮）
    inputs:
      environment:                      # 手动触发时可填参数
        description: '部署到哪个环境'
        required: true
        default: 'staging'
        type: choice
        options:
          - staging
          - production
  schedule:
    - cron: '0 2 * * *'                 # 每天 UTC 2 点跑（定时任务）
  release:
    types: [published]                  # 发 Release 时触发
  workflow_call:                        # 被其他 workflow 调用（可复用 workflow）
    inputs:
      ref:
        required: false
        type: string
```

**本项目 mkdocs.yml 的触发：**
```yaml
on:
  push:
    branches: [main]
    paths:
      - 'docs/**'
      - 'mkdocs.yml'
      - '.github/workflows/mkdocs.yml'
  workflow_dispatch:
```
含义：推送到 `main` 且改了 `docs/`、`mkdocs.yml` 或 workflow 本身时触发；也可在 Actions 页面手动触发。

> `paths` 过滤是「**或**」关系：改了任一路径就触发。注意：如果 commit 涉及多个路径但都不在 paths 列表里，则不触发。

### 5.4.3 `permissions`：GITHUB_TOKEN 权限

每个 workflow 自动有一个 `GITHUB_TOKEN`，可访问本仓库的 API。出于安全，要显式声明需要哪些权限（最小权限原则）。

```yaml
permissions:
  contents: read        # 读仓库内容（默认 read）
  pages: write          # 写 GitHub Pages
  id-token: write       # 开 OIDC（部署 Pages 必需）
```

常用权限：`contents`（代码）、`pages`（Pages）、`issues`（Issue）、`pull-requests`（PR）、`packages`（包仓库）、`id-token`（OIDC 联邦身份）。

> 本项目 mkdocs.yml 用了 `contents: read, pages: write, id-token: write`，这是部署 GitHub Pages 的标准三件套。`id-token: write` 是为了让 deploy-pages Action 通过 OIDC 拿到一次性凭证（见 09 章）。

### 5.4.4 `concurrency`：并发控制

防止同一个东西被并发跑多次。

```yaml
concurrency:
  group: pages               # 同名 group 一次只能跑一个
  cancel-in-progress: true   # 新跑的取消旧的（避免排队堆积）
```

**本项目 mkdocs.yml 用：**
```yaml
concurrency:
  group: pages
  cancel-in-progress: true
```
含义：你连续 push 多次，只跑最新一次构建，旧的取消。避免资源浪费 + 部署冲突。

`group` 可用变量区分：`group: ${{ github.workflow }}-${{ github.ref }}` 让不同分支互不干扰、同分支串行取消旧。

### 5.4.5 `env`：全局环境变量

```yaml
env:
  JAVA_VERSION: '21'
  MAVEN_OPTS: '-Xmx2g'
```

workflow 级的 env 对所有 job 生效。job / step 内也可定义自己的 env，优先级更高。

### 5.4.6 `jobs`：任务集合

```yaml
jobs:
  build:       # job id（自定义，下面叫啥这里就叫啥）
    runs-on: ubuntu-latest
    steps: [...]
  test:
    runs-on: ubuntu-latest
    needs: build                      # 等 build 跑完再跑 test
    steps: [...]
  deploy:
    needs: [test]
    if: github.ref == 'refs/heads/main'   # 只有 main 才部署
    runs-on: ubuntu-latest
    steps: [...]
```

- job 之间默认**并行**，用 `needs` 串起来变成串行或 DAG
- `needs: build` 表示等 build 成功后才跑
- `if:` 条件决定是否跑这个 job

### 5.4.7 `runs-on`：选 Runner

```yaml
runs-on: ubuntu-latest     # GitHub 托管的 Ubuntu 最新版
```

GitHub 托管 Runner 选项：

| 标签 | 系统 | 备注 |
|------|------|------|
| `ubuntu-latest` / `ubuntu-24.04` | Ubuntu 24.04 | 最常用，免费 |
| `ubuntu-22.04` | Ubuntu 22.04 | |
| `macos-latest` / `macos-14` | macOS 14 (Apple Silicon) | 贵 10 倍，iOS/macOS 开发用 |
| `windows-latest` | Windows Server | 贵 2 倍，.NET / Windows 专用 |
| `ubuntu-latest[4core]` | 4 核 Ubuntu | 大型构建用，消耗免费额度更快 |

> 免费额度：public 仓库**无限免费**；private 仓库个人账号每月 2000 分钟（Ubuntu），macOS ×10 倍计费、Windows ×2 倍。本项目是 public，无限免费。

自建 Runner：`runs-on: self-hosted` 或自定义标签，企业内部常用。

### 5.4.8 `steps`：步骤序列

```yaml
steps:
  # 1. 调用别人写好的 Action
  - uses: actions/checkout@v4
    with:
      fetch-depth: 0

  # 2. 运行 shell 命令
  - name: Install dependencies
    run: pip install mkdocs-material

  # 3. 多行 shell
  - name: Build
    run: |
      mkdocs build --strict --clean
      echo "Build done"

  # 4. 条件执行
  - name: Deploy
    if: github.ref == 'refs/heads/main'
    run: mkdocs gh-deploy
```

- `uses`：调用 Action（带版本号 `@v4`）
- `run`：跑 shell 命令，`|` 多行
- `name`：step 显示名
- `with`：传给 Action 的参数
- `if`：条件
- `env`：本 step 专属环境变量

### 5.4.9 `uses`：调用 Action

Action 是可复用单元，写法 `uses: 仓库名@版本`：

```yaml
- uses: actions/checkout@v4        # 官方 checkout Action，v4 大版本
- uses: actions/setup-python@v5    # 官方装 Python
  with:
    python-version: '3.12'
- uses: actions/upload-pages-artifact@v3   # 官方上传 Pages 产物
  with:
    path: site
- uses: ./.github/actions/my-action        # 仓库内的本地 Action
- uses: acme-org/my-action@main            # 别人仓库的 Action（不推荐用分支名，要用 tag）
```

**版本选择原则：**
- 优先用 `@v4` 这种大版本号标签（自动接收小版本补丁）
- 不要用 `@main` / `@master`（分支会变，安全性差）
- 高安全场景用 commit SHA：`@a1b2c3d4...`（最稳，但更新麻烦）

### 5.4.10 表达式与上下文 `${{ ... }}`

workflow 里动态值用 `${{ 表达式 }}` 包裹：

```yaml
- name: Show context
  run: |
    echo "Repo: ${{ github.repository }}"
    echo "Branch: ${{ github.ref }}"
    echo "Actor: ${{ github.actor }}"
    echo "Commit: ${{ github.sha }}"

- if: ${{ github.event_name == 'push' && github.ref == 'refs/heads/main' }}
  run: echo "main 分支 push 时才跑"

- run: echo "Tag is ${{ github.ref_name }}"
```

常用上下文：

| 上下文 | 内容 | 例子 |
|--------|------|------|
| `github.*` | 触发事件信息 | `github.repository`, `github.ref`, `github.event_name`, `github.sha`, `github.actor` |
| `env.*` | 环境变量 | `env.JAVA_VERSION` |
| `secrets.*` | 加密密钥 | `secrets.NPM_TOKEN` |
| `vars.*` | 仓库变量（明文） | `vars.DEFAULT_BRANCH` |
| `inputs.*` | 手动触发传入参数 | `inputs.environment` |
| `job.*` | 当前 job 信息 | `job.status` |
| `steps.*` | 之前 step 的输出 | `steps.build.outputs.version` |
| `matrix.*` | 矩阵构建当前组合 | `matrix.os`, `matrix.java` |

> `${{ }}` 在 `if:` 里可省略（GitHub 自动当表达式算），但在 `run:` / `with:` 里必须显式写。

### 5.4.11 `strategy.matrix`：矩阵构建

一次跑多种组合（多 OS、多语言版本、多模块）：

```yaml
strategy:
  fail-fast: false    # 一个失败不取消其他
  matrix:
    os: [ubuntu-latest, macos-latest, windows-latest]
    java: ['17', '21']
    exclude:
      - os: macos-latest
        java: '17'    # 排除这个组合
    include:
      - os: ubuntu-latest
        java: '21'
        experimental: true   # 追加一个变量
runs-on: ${{ matrix.os }}
steps:
  - uses: actions/setup-java@v4
    with:
      java-version: ${{ matrix.java }}
```

上面会跑 3×2 - 1 = 5 个 job 并行。

### 5.4.12 `needs` + `outputs`：job 间传值

```yaml
jobs:
  build:
    outputs:
      version: ${{ steps.meta.outputs.version }}
    steps:
      - id: meta
        run: echo "version=1.2.3" >> $GITHUB_OUTPUT

  deploy:
    needs: build
    steps:
      - run: echo "Deploying ${{ needs.build.outputs.version }}"
```

写 outputs 到 `$GITHUB_OUTPUT` 文件（旧写法 `::set-output` 已废弃）。

### 5.4.13 `environment`：部署环境

```yaml
deploy:
  needs: build
  runs-on: ubuntu-latest
  environment:
    name: github-pages
    url: ${{ steps.deployment.outputs.page_url }}
  steps:
    - uses: actions/deploy-pages@v4
      id: deployment
```

- `environment: github-pages`：把这个 job 标记为部署到 Pages 环境
- 如果环境配了 required reviewers，job 会**暂停等待 approve** 才继续
- `url:` 让 PR / Actions 页面显示「部署到了哪个地址」

> 本项目 mkdocs.yml deploy job 用 `environment: github-pages`，`url` 用 deploy-pages Action 的输出，部署完后 Actions 页面顶部会显示文档站地址。

### 5.4.14 artifact 上传下载

```yaml
- uses: actions/upload-artifact@v4
  with:
    name: build-output
    path: target/*.jar
    retention-days: 7    # 保留 7 天

- uses: actions/download-artifact@v4
  with:
    name: build-output
    path: ./artifacts
```

artifact 跨 job 共享文件。本项目 mkdocs.yml 用 `actions/upload-pages-artifact` 把 `site/` 上传，deploy job 用 `actions/deploy-pages` 直接从 artifact 部署（不用显式 download）。

### 5.4.15 缓存

```yaml
- uses: actions/cache@v4
  with:
    path: ~/.m2/repository
    key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
    restore-keys: |
      ${{ runner.os }}-maven-
```

加速构建。也可以用 `actions/setup-java` 自带的 `cache: maven` 选项一行搞定。

> 本项目 mkdocs.yml **没有** 用 cache：`actions/setup-python` 的 `cache: pip` 需要依赖清单文件（requirements.txt），项目没维护这个文件，开启反而可能因为找不到清单失败。注释里有说明。

### 5.4.16 `continue-on-error` 和 `timeout-minutes`

```yaml
- name: Flaky test
  continue-on-error: true    # 失败不阻断后续 step
  run: npm run flaky-test

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30      # 30 分钟没跑完强制杀
```

### 5.4.17 可复用 Workflow（Reusable Workflow）

把常用 workflow 抽出来被调用：

```yaml
# .github/workflows/ci-template.yml
on:
  workflow_call:               # 声明可被调用
    inputs:
      java-version:
        required: false
        type: string
        default: '21'
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: ${{ inputs.java-version }}
      - run: mvn test
```

调用方：

```yaml
# .github/workflows/ci.yml
on: [push]
jobs:
  ci:
    uses: ./.github/workflows/ci-template.yml
    with:
      java-version: '21'
```

适合多仓库统一 CI / 同仓库多模块共享流程。

## 5.5 GitHub Marketplace 与 Action 选择

去 https://github.com/marketplace?type=actions 搜索。选择标准：

- 用官方或维护活跃的（最近一年有更新）
- 看 star 数、issue 响应速度
- 优先用 `@vX` 大版本标签
- 高安全场景用 commit SHA 锁版本

**几个常用官方 Action：**

| Action | 用途 |
|--------|------|
| `actions/checkout` | 拉代码到 runner（默认不拉！必须显式用） |
| `actions/setup-java` / `setup-python` / `setup-node` | 装语言运行时 |
| `actions/cache` | 缓存依赖 |
| `actions/upload-artifact` / `download-artifact` | 传产物 |
| `actions/upload-pages-artifact` / `deploy-pages` | GitHub Pages 专用 |
| `actions/labeler` | PR 自动打 label |
| `actions/dependency-review-action` | PR 依赖审查 |
| `github/codeql-action` | 代码安全扫描 |

> **关键坑：workflow 默认不 checkout 代码**。必须先 `uses: actions/checkout@v4`，否则后续 step 在空目录里跑。

## 5.6 Runner 详解

### GitHub-hosted Runner

| 系统 | 标签 | CPU/内存/磁盘 | 免费额度倍率 |
|------|------|--------------|-------------|
| Linux | `ubuntu-latest` | 4 核 / 16 GB / 14 GB | 1× |
| Windows | `windows-latest` | 4 核 / 16 GB / 14 GB | 2× |
| macOS Intel | `macos-13` | 4 核 / 14 GB / 14 GB | 10× |
| macOS ARM | `macos-14` | 3 核 / 14 GB / 14 GB | 10× |

public 仓库无限免费；private 仓库每月有分钟数额度（个人 2000 min，组织 3000 min）。

### Self-hosted Runner

公司内部用，省额度 + 能访问内网：

- 仓库 Settings → Actions → Runners → New self-hosted runner
- 按指引在自建机器上跑一段注册脚本
- workflow 里 `runs-on: self-hosted` 或自定义标签如 `runs-on: [self-hosted, linux, x64]`

> 自建 Runner 安全风险高，**不要** 用于 public 仓库（任意人提 PR 就能在你机器上执行代码）。public 仓库必须用 GitHub-hosted。

## 5.7 触发 workflow 的几种方式实操

### push 触发

最常见。`on: push` 或带过滤（见 5.4.2）。

### workflow_dispatch 手动触发

```yaml
on:
  workflow_dispatch:
    inputs:
      reason:
        description: '为啥手动跑'
        required: false
        type: string
```

Actions 页面 → 选 workflow → Run workflow → 填参数 → 跑。

### workflow_call 被调用

见 5.4.17。

### schedule 定时

```yaml
on:
  schedule:
    - cron: '0 2 * * *'    # 每天 UTC 02:00（北京 10:00）
    - cron: '0 0 * * 0'    # 每周日 UTC 00:00
```

> cron 用 UTC 时间。schedule 不保证准时，高峰期可能延迟 15 分钟以上，别用于关键时序。

### repository_dispatch 外部触发

通过 GitHub API 从外部系统触发：

```bash
curl -X POST \
  -H "Authorization: token $PAT" \
  https://api.github.com/repos/QiHaoo/my-mall/dispatches \
  -d '{"event_type":"build","client_payload":{"env":"prod"}}'
```

### issue / PR 评论触发

```yaml
on:
  issue_comment:
    types: [created]
jobs:
  test:
    if: ${{ github.event.issue.pull_request && contains(github.event.comment.body, '/test') }}
    runs-on: ubuntu-latest
    steps:
      - run: echo "Someone commented /test on PR"
```

## 5.8 Secret 与环境变量

详见 09 章，这里先记住基本用法：

```yaml
# 仓库 Settings → Secrets and variables → Actions → New repository secret
# 假设加了 DOCKER_PASSWORD

steps:
  - run: docker login -u qihao -p ${{ secrets.DOCKER_PASSWORD }}
```

- `${{ secrets.XXX }}`：密钥（写入后只能读不能看明文）
- `${{ vars.XXX }}`：明文变量（可看明文，适合放非敏感配置）

## 5.9 调试 workflow

### 看日志

Actions 页面 → 点某次 run → 点 job → 展开 step 看输出。失败 step 红色标出。

### 启用 step debug log

仓库 Settings → Secrets and variables → Actions → New repository secret：
- 名字 `ACTIONS_STEP_DEBUG`，值 `true`

之后所有 workflow 跑时输出超详细日志。

### 本地用 `act` 模拟跑

[act](https://github.com/nektos/act) 工具能在本地用 Docker 模拟 GitHub Actions 跑：

```bash
$ act -j build    # 在本地跑 build job
```

适合调试 workflow 语法，不用每次推代码触发。

### `set-output` 与输出调试

```yaml
- run: echo "::notice::这是一条 notice"
- run: echo "::warning::这是一条 warning"
- run: echo "::error file=app.js,line=10::这是一条 error，会在 PR 上标红"
- id: meta
  run: echo "version=1.2.3" >> $GITHUB_OUTPUT
- run: echo "Output is ${{ steps.meta.outputs.version }}"
```

## 5.10 本项目对照

本项目的 [.github/workflows/mkdocs.yml](https://github.com/QiHaoo/my-mall/blob/main/.github/workflows/mkdocs.yml) 完整结构：

```
name: Deploy MkDocs to GitHub Pages
on:
  push: { branches: [main], paths: [docs/**, mkdocs.yml, .github/workflows/mkdocs.yml] }
  workflow_dispatch:
permissions: { contents: read, pages: write, id-token: write }
concurrency: { group: pages, cancel-in-progress: true }

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - actions/checkout@v4 (fetch-depth: 0)
      - actions/setup-python@v5 (python 3.12, no cache)
      - run: pip install mkdocs-material
      - run: mkdocs build --strict --clean
      - actions/upload-pages-artifact@v3 (path: site)

  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment: { name: github-pages, url: ... }
    steps:
      - actions/deploy-pages@v4
```

字段对照本章所学：`on`（5.4.2）、`permissions`（5.4.3）、`concurrency`（5.4.4）、`jobs.needs`（5.4.6）、`runs-on`（5.4.7）、`steps.uses/run/with`（5.4.8/5.4.9）、`environment`（5.4.13）、`upload-pages-artifact`（5.4.14）。

> 完整的逐行注释版见 [11-project-practice.md](./11-project-practice.md)。

## 5.11 一个完整的 Java CI 示例

把本章学的串起来，给本项目写一个未来要用的后端 CI workflow：

```yaml
name: Backend CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'    # 自动缓存 ~/.m2

      - name: Run tests
        run: mvn -B test

      - name: Upload test report
        if: always()    # 即使测试失败也上传
        uses: actions/upload-artifact@v4
        with:
          name: test-report
          path: target/surefire-reports/

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'
      - run: mvn -B package -DskipTests
      - uses: actions/upload-artifact@v4
        with:
          name: jars
          path: mall-*/target/*.jar
          retention-days: 7
```

## 小结

| 你应该记住的 |
|---|
| Workflow 文件放 `.github/workflows/`，是 YAML |
| 层级：Workflow → Job → Step（uses 调 Action / run 跑 shell） |
| `on` 控制触发（push / PR / dispatch 手动 / schedule 定时 / workflow_call 复用） |
| `needs` 串行 job，`matrix` 矩阵并行，`concurrency` 并发控制 |
| `permissions` 最小权限声明 GITHUB_TOKEN |
| 必须先 `actions/checkout` 才有代码 |
| Actions 用 `@v4` 大版本标签，不用 `@main` |
| public 仓库免费无限，private 仓库每月有分钟数额度 |
| 调试：Actions 页面看日志 / `act` 本地模拟 / `::error::` 输出标注 |

下一章 [06-GitHub Pages](./06-github-pages.md) 讲 mkdocs.yml 的 deploy job 最终把 `site/` 部署到的地方——GitHub Pages。
