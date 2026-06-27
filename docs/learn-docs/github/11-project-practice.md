# 11 · 本项目实践串联

> 前面 10 章学的概念散落在各处。本章把它们全部落到本项目 [.github/workflows/mkdocs.yml](https://github.com/QiHaoo/my-mall/blob/main/.github/workflows/mkdocs.yml) 上：逐行解读这个 workflow 文件，把「写文档 → push → Actions 构建 → 部署到 Pages → 公网访问」的全链路串清楚。读完这一章，你能彻底看懂这个文件、改得动、出问题会调。

## 11.1 项目用到的 GitHub 特性总览

回顾一下，本项目实际用到的 GitHub 平台能力：

| 特性 | 在哪 | 对应章节 |
|------|------|---------|
| Git 仓库托管 | `github.com/QiHaoo/my-mall` | 01 |
| GitHub Flow 工作流 | `main` + 功能分支 + PR | 02、03 |
| Pull Request 协作 | PR + Code Review + Squash 合并 | 03 |
| Repository Rulesets | （待配置）分支保护 | 04 |
| GitHub Actions | `.github/workflows/mkdocs.yml` | 05 |
| GitHub Pages | 文档站 `https://qihao0o.github.io/my-mall/` | 06 |
| Environments | deploy job 用 `github-pages` 环境 | 04、05、09 |
| OIDC 联邦身份 | `id-token: write` 免密钥部署 | 09 |
| Concurrency | 取消旧构建 | 05 |
| GITHUB_TOKEN 权限 | `permissions:` 显式声明 | 05、09 |
| Actions Marketplace | `actions/checkout` 等 4 个官方 Action | 05 |

暂未用到但未来会加：Releases（07）、Dependabot（09）、CodeQL（09）、Secret Scanning（09）、gh CLI 自动化（10）。

## 11.2 mkdocs.yml 逐行解读

把 [`.github/workflows/mkdocs.yml`](https://github.com/QiHaoo/my-mall/blob/main/.github/workflows/mkdocs.yml) 完整内容贴下面，每段加注释讲清「是什么 / 为什么 / 怎么改」。

### 11.2.1 文件头：name + 注释

```yaml
# GitHub Actions：自动构建 MkDocs Material 并发布到 GitHub Pages
# 推送到 main 分支后，docs/ 目录变更会自动重新构建部署
# 部署地址：https://qihao0o.github.io/my-mall/
name: Deploy MkDocs to GitHub Pages
```

**讲解：**

- `#` 开头是 YAML 注释，说明这个 workflow 干什么、什么时候触发、部署地址
- `name:` 是 workflow 显示名，会出现在仓库 Actions 标签页的左侧 workflow 列表
- 没写 `run-name:`，默认用 commit message 当每次运行的标题

> 改名建议：可以更短 `name: docs-deploy`，但当前描述性更好，保留。

### 11.2.2 触发条件 `on`

```yaml
on:
  push:
    branches: [main]
    paths:
      - 'docs/**'
      - 'mkdocs.yml'
      - '.github/workflows/mkdocs.yml'
  # 允许手动触发
  workflow_dispatch:
```

**讲解：**

- `on:` 是触发条件（不是 `when`，YAML 关键字）
- 两个触发器：
  - `push`：推送到 `main` 分支，且改了 `docs/**` / `mkdocs.yml` / workflow 本身，才触发
  - `workflow_dispatch`：Actions 页面手动触发（页面会出现「Run workflow」按钮）

**为什么这么过滤：**

- 只在 `main` 触发：feature 分支的 docs 改动不部署，避免开发中状态污染文档站
- 路径过滤：避免改了 README、pom.xml 之类的非文档文件也触发构建，浪费 Actions 分钟数
- 把 workflow 自身路径也列入：改了 workflow 配置后自动重新跑一次验证

**`paths` 是「或」关系**：commit 涉及任一列出的路径就触发。如果某次 push 只改了 `pom.xml`，不触发。

**手动触发场景：** 文档站莫名没更新、workflow 上次失败想重跑、改了 GitHub Pages 设置想验证，都能用 `workflow_dispatch`。

### 11.2.3 权限 `permissions`

```yaml
permissions:
  contents: read
  pages: write
  id-token: write
```

**讲解：**

每个 workflow 自动有一个 `GITHUB_TOKEN`，默认权限较宽（安全隐患）。显式 `permissions:` 收紧到本次实际需要的最小权限：

- `contents: read`：读仓库代码（checkout 要用）
- `pages: write`：写 GitHub Pages（部署要用）
- `id-token: write`：开 OIDC（`actions/deploy-pages` 内部用 OIDC 拿一次性部署凭证，见 09 章）

**为什么不用默认权限：** GitHub 默认给 `GITHUB_TOKEN` 的权限是 `contents: write` 等较宽权限，万一 workflow 文件被攻击者改了，能改仓库代码、推分支、删 release。显式声明把权限收紧到只读 + Pages 写 + OIDC，最小权限原则。

> 如果忘了配 `id-token: write`，`actions/deploy-pages` 会报鉴权失败，错误信息大致是 `Failed to create deployment, forbidden`。

### 11.2.4 并发控制 `concurrency`

```yaml
# 同一分支新推送取消旧构建
concurrency:
  group: pages
  cancel-in-progress: true
```

**讲解：**

- `concurrency.group: pages`：所有用了 `group: pages` 的运行视为同一组，一次只能跑一个
- `cancel-in-progress: true`：新的运行开始时，取消正在跑的旧运行

**为什么这么配：**

你连续 push 三次：
1. 第 1 次 push 触发 workflow A 开始构建
2. 第 2 次 push 触发 workflow B
3. 第 3 次 push 触发 workflow C

不开 concurrency：A、B、C 并行跑，三个都部署，最后一个赢——浪费资源，可能部署顺序乱。

开了 concurrency + cancel-in-progress：A 开始 → B 开始时取消 A → C 开始时取消 B → 只 C 跑完。最终文档站反映最新代码。

**改进点：** 当前 `group: pages` 是全局唯一的，意味着不同分支的 push 也会互相取消。如果想保留多分支并行，可以改成：

```yaml
concurrency:
  group: pages-${{ github.ref }}
  cancel-in-progress: true
```

`github.ref` 是分支名，这样不同分支互不干扰、同分支串行取消旧。但本项目只在 main 触发，当前配置已够。

### 11.2.5 build job

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: actions/setup-python@v5
        with:
          python-version: '3.12'
          # 不开启 cache: pip 缓存需解析依赖清单，setup-python 阶段若清单缺失/网络受限会导致整步失败
          cache: ''

      - name: Install MkDocs Material
        run: pip install --upgrade pip && pip install mkdocs-material mkdocs-material-extensions

      - name: Build site
        run: mkdocs build --strict --clean

      - name: Upload Pages artifact
        uses: actions/upload-pages-artifact@v3
        with:
          path: site
```

**逐 step 讲解：**

#### `runs-on: ubuntu-latest`

Runner 选 GitHub 托管的 Ubuntu 最新版（免费、public 仓库无限额度）。详见 05 章 5.4.7。

#### Step 1: checkout 代码

```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: 0
```

- `actions/checkout@v4`：官方 Action，把仓库代码拉到 runner 工作目录。**必须先 checkout，否则后续 step 在空目录跑**
- `fetch-depth: 0`：拉完整 Git 历史（默认 `fetch-depth: 1` 只拉最新一次 commit）
- 为什么拉完整历史：MkDocs 的 `git-revision-date-localized` 插件（如果用了）需要读 commit 历史算「文档最后更新时间」。本项目 `mkdocs.yml` 启用了相关插件，所以要 `fetch-depth: 0`

#### Step 2: 装 Python

```yaml
- uses: actions/setup-python@v5
  with:
    python-version: '3.12'
    cache: ''
```

- `actions/setup-python@v5`：官方 Action，装指定 Python 版本
- `python-version: '3.12'`：Python 3.12（mkdocs-material 支持 3.8+，3.12 是 2024 年稳定版）
- `cache: ''`：**不**开 pip 缓存

**为什么不开 cache：**

`actions/setup-python` 的 `cache: pip` 需要依赖清单文件（`requirements.txt`）来计算缓存 key。本项目没维护 `requirements.txt`，开了 cache 会因找不到清单失败。注释里有说明。

**改进建议：** 加一个 `requirements-docs.txt` 锁定 mkdocs-material 版本：

```
# requirements-docs.txt
mkdocs-material==9.5.*
mkdocs-material-extensions==1.3.*
```

然后：

```yaml
- uses: actions/setup-python@v5
  with:
    python-version: '3.12'
    cache: pip
    cache-dependency-path: requirements-docs.txt

- run: pip install -r requirements-docs.txt
```

好处：
- 版本固定，避免某天 mkdocs-material 升级有 breaking change 导致构建挂
- pip 缓存生效，构建快 30s+

#### Step 3: 装 MkDocs Material

```yaml
- name: Install MkDocs Material
  run: pip install --upgrade pip && pip install mkdocs-material mkdocs-material-extensions
```

- `pip install --upgrade pip`：先升级 pip 本身，避免老 pip 解析包有问题
- `pip install mkdocs-material mkdocs-material-extensions`：装框架 + 主题 + 扩展
- `mkdocs-material` 自动带 `mkdocs` 本体，不用单独装

`run:` 执行 shell 命令，`&&` 串行（前一个成功才跑下一个）。

#### Step 4: 构建站点

```yaml
- name: Build site
  run: mkdocs build --strict --clean
```

- `mkdocs build`：读 `mkdocs.yml` 配置，把 `docs/` 下的 Markdown 编译成静态 HTML 输出到 `site/`
- `--strict`：严格模式，发现死链（链接指向不存在的页面）直接构建失败（默认只 WARNING）
- `--clean`：构建前清空 `site/`，避免旧文件残留

**`--strict` 的意义：** 强制文档质量。任何死链、配置错误都会让 CI 失败，提醒你修。本项目 [docs-site-deployment.md](../../docs-site-deployment.md) 提到「strict 构建报 anchor 不存在 INFO」是已知小问题，不影响部署。

构建成功后，`site/` 目录就是完整的静态站点。

#### Step 5: 上传 Pages artifact

```yaml
- name: Upload Pages artifact
  uses: actions/upload-pages-artifact@v3
  with:
    path: site
```

- `actions/upload-pages-artifact@v3`：官方 Action，把 `site/` 打包成 artifact
- `path: site`：要打包的目录
- 这个 artifact 名字固定为 `github-pages`，下一个 job 的 `actions/deploy-pages` 会自动找这个 artifact

> 不需要 `actions/download-artifact` 显式下载，`deploy-pages` 内部自动处理。

### 11.2.6 deploy job

```yaml
  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

**逐项讲解：**

#### `needs: build`

deploy job 等 build job 成功后才跑。build 失败 → deploy 不跑。

#### `environment:`

```yaml
environment:
  name: github-pages
  url: ${{ steps.deployment.outputs.page_url }}
```

- `name: github-pages`：声明这个 job 部署到 `github-pages` environment
- 第一次部署时 GitHub 会自动创建这个 environment
- `url:` 是部署后展示的地址，从 deploy-pages 的输出取

**environment 的作用：**
- 如果给 `github-pages` environment 配了 `Required reviewers`，部署前要审批（本项目没配，免审批）
- environment 也能放 environment-level secret（本项目没用）
- 在 Actions 运行页面顶部显示「部署到了 https://...」，方便点过去看

#### Step 1: deploy-pages

```yaml
- name: Deploy to GitHub Pages
  id: deployment
  uses: actions/deploy-pages@v4
```

- `actions/deploy-pages@v4`：官方 Action，从上一步 upload-pages-artifact 上传的 `github-pages` artifact 部署到 GitHub Pages
- `id: deployment`：给这个 step 起个 id，后面 `environment.url` 能引用它的输出 `steps.deployment.outputs.page_url`
- 内部用 OIDC（依赖 workflow 级的 `id-token: write`）拿一次性凭证推送

部署成功后，文档站 `https://qihao0o.github.io/my-mall/` 立即可访问（CDN 缓存可能 1-2 分钟生效）。

## 11.3 全链路：从写文档到公网访问

把整条链路画出来：

```
┌────────────────────────────────────────────────────────────────────┐
│  本地                                                                │
│  ┌──────────────────────────────────────────┐                       │
│  │ 编辑 docs/learn-docs/github/11-...md     │                       │
│  │ py -3 -m mkdocs serve  ← 本地预览         │                       │
│  └────────────────────┬─────────────────────┘                       │
└───────────────────────┼────────────────────────────────────────────┘
                        │ git add / commit / push
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│  GitHub 仓库（main 分支）                                            │
│  接收 push，检测 .github/workflows/mkdocs.yml 的 on.push 触发条件   │
│  改了 docs/** 吗？✅ 触发 workflow                                   │
└───────────────────────┬────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│  GitHub Actions Runner（ubuntu-latest）                              │
│  ┌────────────────────────────────────────────────────────┐         │
│  │ build job                                              │         │
│  │ 1. actions/checkout（fetch-depth: 0）                  │         │
│  │ 2. actions/setup-python 3.12                           │         │
│  │ 3. pip install mkdocs-material                         │         │
│  │ 4. mkdocs build --strict --clean  →  site/             │         │
│  │ 5. actions/upload-pages-artifact (path: site)          │         │
│  └────────────────────┬───────────────────────────────────┘         │
└───────────────────────┼────────────────────────────────────────────┘
                        │ artifact github-pages
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│  GitHub Actions Runner                                              │
│  ┌────────────────────────────────────────────────────────┐         │
│  │ deploy job (needs: build, environment: github-pages)   │         │
│  │ 1. actions/deploy-pages  (OIDC 鉴权)                   │         │
│  │    输出 page_url = https://qihao0o.github.io/my-mall/  │         │
│  └────────────────────┬───────────────────────────────────┘         │
└───────────────────────┼────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│  GitHub Pages CDN                                                    │
│  全球 Fastly CDN 缓存分发                                            │
│  HTTPS 自动启用                                                       │
└───────────────────────┬────────────────────────────────────────────┘
                        │
                        ▼
            https://qihao0o.github.io/my-mall/
            任何浏览器、平板、手机都能访问
```

**时间线：** push 后 1-2 分钟内能看到新内容上线。CDN 缓存可能让某些边缘节点稍滞后。

## 11.4 日常更新文档的标准动作

```bash
# 1. 切到 main 拉最新
$ git switch main
$ git pull

# 2. 建 feature 分支（遵循 GitHub Flow）
$ git switch -c docs/github-learning-notes

# 3. 编辑 / 新增 docs/ 下 markdown
# 4. 本地预览
$ py -3 -m mkdocs serve
# 浏览器 http://127.0.0.1:8000/my-mall/ 检查

# 5. （可选）本地用 --strict 验证
$ py -3 -m mkdocs build --strict --clean

# 6. 在 mkdocs.yml 的 nav 里登记新文档（否则不进导航）

# 7. commit + push
$ git add docs/ mkdocs.yml
$ git commit -m "docs: 新增 GitHub 学习文档"
$ git push -u origin docs/github-learning-notes

# 8. 发 PR
$ gh pr create --base main --title "docs: 新增 GitHub 学习文档" \
    --body "新增 docs/learn-docs/github/ 11 篇学习文档..."

# 9. 等 CI（如果有）通过，合并 PR（squash）
$ gh pr merge --squash --delete-branch

# 10. 合并到 main 后，mkdocs.yml workflow 自动触发部署
$ gh run watch    # 盯构建过程

# 11. 1-2 分钟后访问 https://qihao0o.github.io/my-mall/
```

## 11.5 出问题怎么调

### 症状：push 后文档站没更新

排查顺序：

1. **看 workflow 跑没跑**
   ```bash
   $ gh run list --workflow=mkdocs.yml --limit 3
   ```
   - 没记录：触发条件没满足，检查 push 的路径是否在 `paths:` 列表里、分支是否是 main
   - 有记录但失败：进入第 2 步

2. **看 workflow 失败原因**
   ```bash
   $ gh run view --log-failed
   ```
   常见失败：
   - `mkdocs build --strict` 报死链 → 修死链
   - `pip install` 网络失败 → 重跑（`gh run rerun`）
   - `deploy-pages` 鉴权失败 → 检查 `permissions: id-token: write` 是否在
   - `pages: write` 拒绝 → 检查仓库 Settings → Pages → Source 是否选了 GitHub Actions

3. **看 GitHub Pages 设置**
   仓库 Settings → Pages：
   - Source 必须是「GitHub Actions」，不是「Deploy from a branch」
   - 自定义域名（如果有）配对没

4. **看 Pages 部署历史**
   Settings → Pages → 顶部 See build log，能看到每次部署状态

5. **CDN 缓存**
   部署成功但浏览器看旧版：强刷（Ctrl+F5）、等几分钟、用无痕模式

### 症状：本地预览正常，上线后样式 404

`mkdocs.yml` 的 `site_url` 没配对，资源路径错。

检查 `site_url` 必须是 `https://qihao0o.github.io/my-mall/`（带 `/my-mall/` 子路径），不能是 `https://qihao0o.github.io/`。

### 症状：自定义域名访问报 404

- DNS CNAME 没生效（`dig docs.yourdomain.com` 验证）
- 仓库 `docs/CNAME` 文件内容错或缺失
- 等待 HTTPS 证书签发期间

### 症状：中文搜索搜不到

`mkdocs.yml` 的 `plugins.search.separator` 要按中文字符切分，`lang` 含 `zh`。本项目已配，详细见 [docs-site-deployment.md 第五节](../../docs-site-deployment.md)。

## 11.6 改进建议

基于前面 10 章所学，本项目 GitHub 相关配置可以做的改进：

### 短期（低成本高收益）

1. **配置 main 分支 Ruleset**（04 章）
   - 必须通过 PR 合并
   - 至少 1 个 Approve（团队项目；个人项目可设 0 但保留 PR 流程留痕）
   - 禁强推、禁删除
   - 关闭 merge commit 合并方式，只留 squash

2. **加 `.github/dependabot.yml`**（09 章）
   ```yaml
   version: 2
   updates:
     - package-ecosystem: "maven"
       directory: "/"
       schedule: { interval: "weekly" }
     - package-ecosystem: "github-actions"
       directory: "/"
       schedule: { interval: "monthly" }
     - package-ecosystem: "pip"
       directory: "/"
       schedule: { interval: "monthly" }
   ```

3. **加 `.github/CODEOWNERS`**（04 章）
   ```
   *                              @QiHaoo
   /mall-gateway/                 @QiHaoo
   /.github/                      @QiHaoo
   ```

4. **开 Secret Scanning + Push Protection**（09 章）
   仓库 Settings → Code security → 一键开

5. **加 `requirements-docs.txt` 固定文档依赖版本**（11.2.5 提到）

### 中期（按需）

6. **后端 CI workflow**（05 章 5.11 示例）
   `.github/workflows/ci.yml`：push/PR 时跑 Maven test

7. **CodeQL 安全扫描**（09 章）
   `.github/workflows/codeql.yml`：扫 Java 代码漏洞

8. **PR 模板** `.github/PULL_REQUEST_TEMPLATE.md`（03 章）

9. **Issue 模板** `.github/ISSUE_TEMPLATE/`（08 章）

### 长期（项目成熟后）

10. **Release 自动化**：引入 release-please（07 章），基于 Conventional Commits 自动打 tag + 发 Release

11. **多环境部署**：staging / production environment + 审批门

12. **镜像构建 workflow**：CI 通过后构建 Docker 镜像推 Harbor（用 OIDC 免密钥，09 章）

## 11.7 学完应该能做到

回到 README 的目标——「从没用过 GitHub 到全面理解」。学完这 11 章后，你应该能：

| 能力 | 体现 |
|------|------|
| 看懂任意 GitHub Actions workflow 文件 | 11.2 的逐行解读你已经会做了 |
| 自己写一个 CI/CD workflow | 05 章 5.11 的 Java CI 示例是起点 |
| 看懂 PR / Issue / Release 操作 | 03、07、08 章 |
| 配置分支保护 | 04 章 4.5 的实用配置 |
| 调试 workflow 失败 | 11.5 的排查清单 |
| 用 gh CLI 提效 | 10 章速查表 |
| 评估安全风险 | 09 章最佳实践清单 |
| 解释 mkdocs.yml 里每一行 | 11.2 全章 |
| 看懂业界主流工作流 | 02 章三种工作流对比 |

## 11.8 下一步学什么

GitHub 生态还有这些方向（不在本笔记范围，列出供后续探索）：

- **GitHub Codespaces**：云端开发环境，浏览器/VS Code 直连
- **GitHub Copilot**：AI 编程助手
- **GitHub Advanced Security**：企业版安全功能（code scanning 高级、secret scanning 自定义模式）
- **GitHub Actions Runner Controller (ARC)**：K8s 上自建 Runner 弹性扩缩
- **GitHub Packages**：GitHub 内置的 npm/Maven/Docker 包仓库
- **GitHub Container Registry (ghcr.io)**：Docker 镜像仓库
- **GitHub Discussions API**：Discussions 编程化
- **GitHub Webhooks**：事件推送到外部系统

## 小结

恭喜你读完整个 GitHub 学习笔记。你现在应该能：

- 看懂本项目的 `.github/workflows/mkdocs.yml` 每一行
- 理解「写文档 → push → Actions 构建 → Pages 部署 → 公网访问」全链路
- 自己写 workflow、配分支保护、用 gh CLI、排查问题
- 评估业界主流工作流（GitHub Flow / GitFlow / Trunk-based）的取舍
- 识别 GitHub 安全风险并用最佳实践防御

回到 README 的 [学习地图](./README.md)，对照看哪章还想复习。

## 参考资源

- [本项目 mkdocs.yml 源文件](https://github.com/QiHaoo/my-mall/blob/main/.github/workflows/mkdocs.yml)
- [本项目文档站部署手册](../../docs-site-deployment.md)
- [本项目 git 工作流规范](../../git-workflow.md)
- [GitHub Actions 官方文档](https://docs.github.com/actions)
- [GitHub Pages 官方文档](https://docs.github.com/pages)
- [MkDocs Material 文档](https://squidfunk.github.io/mkdocs-material/)
- [GitHub Skills 交互式教程](https://skills.github.com/)
