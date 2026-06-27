# 09 · Secrets 与安全

> CI 要推镜像到 Harbor、要部署到生产、要发 npm 包……这些操作都要密钥。把密钥写死在 workflow 文件里等于裸奔（仓库 public 的话直接泄露）。本章讲 GitHub Secrets 体系、环境密钥、OIDC 联邦免密钥、Dependabot 依赖扫描、CodeQL 代码扫描、Secret Scanning。本项目 mkdocs.yml 里那个 `id-token: write` 就是 OIDC，本章解释为什么需要它。

## 9.1 GitHub Secrets 是什么

Secret 是 GitHub 加密存储的「敏感变量」：

- 写入后**只能读不能看明文**（写入方也看不到值，只能更新或删除）
- 在 workflow 里用 `${{ secrets.NAME }}` 引用
- GitHub 在日志里自动把 secret 值打码（显示为 `***`）
- 加密算法：AES-256-GCM，用 GitHub 维护的密钥加密（libsodium sealed box）
- 每个 secret 最大 48KB

> 注意：Secret 不是绝对安全。能触发 workflow 的人能拿到 secret 值（通过 `echo ${{ secrets.X }}` 打到日志或发到外部）。所以**谁能触发 workflow** 要慎重，public 仓库尤其要小心 pull_request 触发的 workflow 不能用到敏感 secret（见 9.4）。

## 9.2 三种 Secret 层级

| 层级 | 路径 | 作用范围 | 优先级 |
|------|------|----------|--------|
| Repository secrets | 仓库 Settings → Secrets and variables → Actions | 本仓库 | 低 |
| Environment secrets | 仓库 Settings → Environments → 选环境 → secrets | 仅部署到该 environment 的 job | 中 |
| Organization secrets | 组织 Settings → Secrets and variables → Actions | 组织下选定仓库 | 高 |

优先级：Environment > Repository > Organization（更具体覆盖更通用）。

### Repository Secrets

最常用。仓库 Settings → Secrets and variables → Actions → New repository secret。

```yaml
steps:
  - run: docker login -u qihao -p ${{ secrets.DOCKER_PASSWORD }}
```

### Environment Secrets

绑定到 environment。只有 job 声明了 `environment: prod` 才能拿到该环境的 secret。

```yaml
jobs:
  deploy-prod:
    runs-on: ubuntu-latest
    environment: production    # 必须！才能拿到 production 环境的 secret
    steps:
      - run: ./deploy.sh ${{ secrets.PROD_DEPLOY_KEY }}
```

配合 environment 的 `Required reviewers`，部署前要审批，secret 才会注入。这是生产环境密钥的最佳实践。

### Organization Secrets

组织级 secret 一次配多仓库共享。组织 Settings → Secrets and variables → Actions → New organization secret → 选可见范围：

- All repositories：组织内所有仓库
- Selected repositories：选定仓库

适合：公司统一配 NPM_TOKEN、SonarQube token 等。

## 9.3 明文变量 vs 加密 Secret

GitHub 还有一套**明文变量**（Variables）：

| | Secrets | Variables |
|---|---------|-----------|
| 加密 | 是 | 否 |
| 引用 | `${{ secrets.X }}` | `${{ vars.X }}` |
| 看明文 | 不能 | 能 |
| 适合 | 密码、token、私钥 | 非敏感配置（默认分支、版本号、URL） |

仓库 Settings → Secrets and variables → Actions → Variables 标签页。

```yaml
env:
  JAVA_VERSION: ${{ vars.JAVA_VERSION }}    # 21
  DEPLOY_TARGET: ${{ vars.DEPLOY_TARGET }} # production
```

## 9.4 Secret 的安全风险与防御

### 风险一：fork 的 PR 能偷 secret

public 仓库默认 `pull_request` 事件来自 fork 时，**workflow 不能访问 secret**（GitHub 自动隔离）。但如果你给 workflow 加了 `pull_request_target` 触发，会以 base 分支权限跑，**能**访问 secret——这是经典安全坑。

**防御：**
- 不要在 `pull_request_target` 触发的 workflow 里 checkout PR 代码并执行
- 必须用时，先 label（如 `safe-to-test`）再触发，避免任意 PR 触发

### 风险二：日志泄露

```yaml
- run: echo ${{ secrets.TOKEN }}    # ❌ 会把 token 打到日志
```

虽然 GitHub 自动打码，但可以通过 Base64、字符拼接绕过：

```yaml
- run: echo ${{ secrets.TOKEN }} | base64    # 仍可能泄露
```

**防御：**
- 永远不要主动 echo secret
- 把 secret 当环境变量传给程序，由程序内部用，不输出
- 开 `ACTIONS_STEP_DEBUG` 调试时尤其小心

### 风险三：任意 workflow 文件触发

push 到任意分支（含攻击者开的 feature 分支）的 `.github/workflows/evil.yml` 会被识别为 workflow，下次触发时跑。如果它依赖某个 secret 且能拿到，就泄露了。

**防御：**
- 仓库 Settings → Actions → General → Fork pull request workflows → 设为只读
- 配置 `pull_request` 触发只用 base 分支的 workflow
- 敏感操作（部署、推镜像）只允许在 main 分支触发：`if: github.ref == 'refs/heads/main'`

## 9.5 OIDC 联邦身份（免密钥部署）

传统方式部署到云（AWS/GCP/Azure）要在 GitHub 存云的长期密钥，密钥泄露就完蛋。OIDC 联邦身份解决这个：

**原理：**
- GitHub Actions 用短期 OIDC token 证明「我是 GitHub Actions 的某次 run」
- 云厂商信任 GitHub 的 OIDC Provider，签发短期 STS 凭证（15 分钟～1 小时过期）
- workflow 用短期凭证操作云资源
- **完全不存长期密钥**

GitHub 这边只需要 workflow 配 `permissions.id-token: write`，让 GitHub 给这次 run 签发 OIDC token。

### 本项目 mkdocs.yml 为什么需要 id-token: write

```yaml
permissions:
  contents: read
  pages: write
  id-token: write    # ← 这个
```

`actions/deploy-pages` 内部用 OIDC 机制向 GitHub Pages 服务证明身份：
- workflow 拿到 OIDC token
- 用 token 换取 GitHub Pages 的一次性部署凭证
- 用凭证推送构建产物

这比传统「存一个 PAT（Personal Access Token）当 secret」更安全：没有长期凭证可泄露，每次部署都是短期凭证。

### 配 AWS OIDC 示例（理解原理）

```yaml
permissions:
  id-token: write    # 必须有
  contents: read

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::123456789012:role/github-actions-deploy
          aws-region: us-east-1
          # 没有 aws-access-key-id / aws-secret-access-key！
      - run: aws s3 sync ./site s3://my-bucket/
```

AWS 那边一次性配好信任关系（Trust Policy），让 GitHub OIDC 能 assume 这个 role。之后 GitHub 这边零密钥。

## 9.6 Dependabot（依赖安全）

Dependabot 是 GitHub 内置的依赖漏洞监控。

### Dependabot Alerts

仓库 Settings → Code security → Enable Dependabot alerts。开启后：
- GitHub 扫描 `pom.xml` / `package.json` / `requirements.txt` 等依赖清单
- 对比 GitHub Advisory Database（已知漏洞库）
- 发现漏洞 → 仓库 Security 标签页 + 邮件告警

### Dependabot Security Updates

Dependabot Alerts 基础上，自动开 PR 升级有漏洞的依赖：

```
Dependabot 提了个 PR：
  Bump spring-boot from 3.4.0 to 3.4.1
  修复 CVE-2026-12345（高危）
```

你 Review + 合并就修了漏洞。

### Dependabot Version Updates

不只是漏洞，可以定期升级所有依赖到最新：

`.github/dependabot.yml`：

```yaml
version: 2
updates:
  # Maven 依赖每周检查
  - package-ecosystem: "maven"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
    open-pull-requests-limit: 10
    labels:
      - "dependencies"
      - "maven"

  # GitHub Actions 每月检查（actions/checkout 等版本升级）
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "monthly"
    labels:
      - "dependencies"
      - "github-actions"

  # npm 依赖每天检查
  - package-ecosystem: "npm"
    directory: "/mall-admin"
    schedule:
      interval: "daily"
```

Dependabot 会自动开 PR：`Bump actions/checkout from v4.1.0 to v4.2.0`，你合并即升级。

### 本项目建议

未来加 `.github/dependabot.yml`：

```yaml
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/"
    schedule:
      interval: "weekly"
  - package-osystem: "github-actions"
    directory: "/"
    schedule:
      interval: "monthly"
  - package-ecosystem: "pip"
    directory: "/"
    schedule:
      interval: "monthly"
```

## 9.7 Code Scanning（CodeQL）

CodeQL 是 GitHub 的代码静态分析引擎，能找出 SQL 注入、XSS、硬编码密钥、反序列化漏洞等。

### 开启方式

仓库 Security → Code scanning → Set up code scanning → 选 CodeQL。

GitHub 自动生成 `.github/workflows/codeql.yml`：

```yaml
name: CodeQL

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  schedule:
    - cron: '0 0 * * 0'    # 每周扫一次

jobs:
  analyze:
    runs-on: ubuntu-latest
    permissions:
      security-events: write    # 上传扫描结果
      actions: read
      contents: read
    strategy:
      matrix:
        language: ['java', 'javascript']    # 扫哪些语言
    steps:
      - uses: actions/checkout@v4
      - uses: github/codeql-action/init@v3
        with:
          languages: ${{ matrix.language }}
      - uses: github/codeql-action/analyze@v3
```

扫描结果：
- 仓库 Security → Code scanning alerts 查看所有告警
- PR 上会在改动行显示告警
- 可设严重度阈值阻断 PR（配 branch protection 的「Require code scanning results」）

### 支持的语言

Java、Python、JavaScript/TypeScript、C/C++、C#、Go、Ruby、Swift、Kotlin 等。

## 9.8 Secret Scanning

GitHub 扫描仓库里有没有意外提交的密钥：

- AWS access key、Slack token、Stripe key、Google API key 等几百种
- 一旦发现，通知对应服务商自动撤销（push protection）
- 仓库 Security → Secret scanning alerts 查看

开启：仓库 Settings → Code security → Enable secret scanning + Push protection。

**Push Protection**：你 push 的 commit 里如果含已知格式的密钥，GitHub **直接拒绝 push**，从源头堵住泄露。强烈建议开。

## 9.9 安全最佳实践清单

| 项 | 实践 |
|---|------|
| Secret 存储 | 用 GitHub Secrets，绝不硬编码 |
| 生产密钥 | 用 Environment secret + Required reviewers |
| 云部署 | 用 OIDC，不存长期密钥 |
| workflow 权限 | `permissions:` 显式声明，最小权限原则 |
| PR 触发 | `pull_request` 而非 `pull_request_target`（除非懂风险） |
| Fork PR | public 仓库默认不传 secret，别绕过 |
| 日志 | 不 echo secret |
| 依赖 | 开 Dependabot alerts + security updates |
| 代码扫描 | 开 CodeQL |
| 密钥泄露 | 开 Secret Scanning + Push Protection |
| Token 范围 | Personal Access Token 用 fine-grained，最小权限 |
| 分支保护 | main 必须保护（见 04 章） |

## 9.10 本项目对照

本项目 mkdocs.yml 用到的安全相关配置：

```yaml
permissions:
  contents: read        # 只读代码，最小权限
  pages: write          # 部署 Pages
  id-token: write       # OIDC，免密钥部署
```

- 用 OIDC 部署 Pages，**没有**存任何长期 PAT / 密钥
- `permissions` 显式声明，不依赖默认（默认 `GITHUB_TOKEN` 是 read-all，显式声明能收紧）
- `concurrency` 防并发部署冲突

未来本项目要加的安全配置：

1. `.github/dependabot.yml`：监控 Maven / pip / Actions 依赖
2. `.github/workflows/codeql.yml`：Java 代码安全扫描
3. 仓库 Settings → Code security：开 Secret Scanning + Push Protection
4. 部署到 Harbor / K8s 时用 OIDC（Harbor 支持 OIDC，K8s 用 IRSA 等方案）

## 小结

| 你应该记住的 |
|---|
| Secret 加密存储，`${{ secrets.X }}` 引用，日志自动打码 |
| 三层级：Repository / Environment / Organization，环境级配 Required reviewers 最安全 |
| Variables 是明文变量，放非敏感配置 |
| OIDC 联邦身份 = 免长期密钥部署云，本项目 mkdocs.yml 的 `id-token: write` 就是这个 |
| 不在 `pull_request_target` 触发的 workflow 里执行 PR 代码 |
| 永远不 echo secret |
| Dependabot 监控依赖漏洞 + 自动开 PR 升级 |
| CodeQL 静态扫代码漏洞 |
| Secret Scanning + Push Protection 从源头堵密钥泄露 |

下一章 [10-GitHub CLI](./10-github-cli.md) 讲用命令行操作 GitHub，不用每次开浏览器。
