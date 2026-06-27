# 01 · GitHub 基础概念

> 这一章把 GitHub 上最常出现的名词一次性讲清楚：仓库、Issue、Pull Request、Fork、Clone、Star、Watch……以及怎么配置 SSH key 让本地 Git 能推送代码到 GitHub。

## 1.1 Git vs GitHub

新手最容易混淆这两个概念：

| | Git | GitHub |
|---|-----|--------|
| 是什么 | 一个**分布式版本控制系统**（软件） | 一个**基于 Git 的代码托管 + 协作平台**（网站服务） |
| 谁做的 | Linus Torvalds（2005，Linux 之父） | GitHub Inc.（2008，现属 Microsoft） |
| 装在哪 | 你电脑本地 | 云端，浏览器访问 github.com |
| 能单独用吗 | 能（纯本地仓库也能版本控制） | 不能脱离 Git（GitHub 依赖 Git） |

**一句话区分：** Git 是引擎，GitHub 是基于这引擎造的「车库 + 协作广场」。

类比：Git 像「相机」，GitHub 像「 Flickr / 图虫」——相机本身能拍照存本地，Flickr 让你能上传、分享、让别人看、评论。

> 还有哪些类似的「车库」：GitLab、Gitee（码云）、Bitbucket、Coding.net。它们底层都是 Git，只是平台功能各有差异。本笔记以 GitHub 为准。

## 1.2 账号与 SSH 配置

### 注册账号

去 https://github.com/signup 注册，用户名要慎重（会出现在你的个人主页 `github.com/<用户名>` 和仓库地址 `github.com/<用户名>/<仓库名>` 里）。

### 为什么需要 SSH key

把代码从本地推送到 GitHub 有两种协议：

| 协议 | 地址形式 | 鉴权 | 适用 |
|------|---------|------|------|
| HTTPS | `https://github.com/user/repo.git` | 用户名 + Personal Access Token | 顺手，但每次推代码要输 token（可配 credential helper 缓存） |
| SSH | `git@github.com:user/repo.git` | SSH 公钥 | **推荐**：一次配置好，之后免密推送 |

SSH 的原理：你在本地生成一对密钥（私钥 + 公钥），把**公钥**贴到 GitHub 账号设置里。推送时 GitHub 用你留下的公钥验证你的身份，本地私钥永不外传。

### 配置 SSH key（Windows / macOS / Linux 通用）

```bash
# 1. 检查是否已有密钥（默认生成在 ~/.ssh/）
ls ~/.ssh
# 若已有 id_ed25519 / id_rsa 则跳过生成步骤

# 2. 生成 Ed25519 密钥（比老式 RSA 更短更快更安全，2026 年首选）
$ ssh-keygen -t ed25519 -C "your_email@example.com"
# 一路回车即可（passphrase 留空方便自动化，想更安全可设密码）

# 3. 查看公钥内容并复制
cat ~/.ssh/id_ed25519.pub
# 输出形如：ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI... your_email@example.com
```

去 GitHub：**Settings → SSH and GPG keys → New SSH key** → Title 随便填（如「我的笔记本」）→ Key 粘贴上面复制的整行 → Add SSH key。

### 验证 SSH 连接

```bash
$ ssh -T git@github.com
# 成功会输出：
# Hi <你的用户名>! You've successfully authenticated, but GitHub does not provide shell access.
```

> 看到 `does not provide shell access` 别慌，这是正常提示，说明认证通过。GitHub 不允许你 SSH 进它的服务器执行 shell 命令，只用来做 Git 操作鉴权。

### 配置 Git 全局身份

```bash
$ git config --global user.name "Your Name"
$ git config --global user.email "your_email@example.com"
```

每次 commit 都会带上这两个字段，作为这次变更的「作者」。注意 email 要和 GitHub 账号绑定的某个邮箱一致，GitHub 才会把这次 commit 标成「你的头像」（在 commit 历史里显示绿色头像而非灰色邮箱）。

## 1.3 仓库（Repository）

仓库是 GitHub 上承载一个项目的容器，常简称 **repo**。

### 创建仓库

GitHub 首页右上角 `+` → New repository，关键选项：

| 选项 | 说明 |
|------|------|
| Owner | 仓库归属（你的个人账号，或你有权限的组织） |
| Repository name | 仓库名，全小写连字符风格（如 `my-mall`） |
| Description | 一句话描述，会显示在搜索结果和仓库主页 |
| Public / Private | 公开 / 私有。GitHub 免费账号 public/private 都无限免费 |
| Initialize with README | 勾选会自动创建一个初始 `README.md`。**本地已有项目要推上来时不要勾**（会冲突），空仓库才勾 |
| .gitignore | 选模板（如 Java、Node），自动生成忽略规则文件 |
| License | 开源协议。学习项目常用 MIT（最宽松）/ Apache-2.0 |

### 仓库地址的两种写法

```bash
# SSH（推荐，配过 SSH key 后用这个）
git@github.com:QiHaoo/my-mall.git

# HTTPS
https://github.com/QiHaoo/my-mall.git
```

> 本项目用 SSH：`git@github.com:QiHaoo/my-mall.git`

### 仓库类型

| 类型 | 说明 | 例子 |
|------|------|------|
| 普通 public | 任何人可见可克隆 | 本项目 `QiHaoo/my-mall` |
| 普通 private | 只有协作人员可见 | 你公司的内部项目 |
| Fork | 从别人仓库「分叉」出来的副本，保留与原仓库的关联 | 你 fork 一份开源项目准备提 PR |
| Template | 模板仓库，可一键基于它创建新仓库 | 官方的 `template-java` 起手项目 |

### 仓库根目录常见文件

| 文件 | 作用 |
|------|------|
| `README.md` | 仓库首页展示的项目说明 |
| `.gitignore` | 告诉 Git 哪些文件不纳入版本控制（如 `target/`、`node_modules/`、`*.log`） |
| `LICENSE` | 开源协议全文 |
| `CONTRIBUTING.md` | 给贡献者看的协作规范 |
| `CODE_OF_CONDUCT.md` | 社区行为准则 |
| `.github/` | GitHub 平台配置目录（workflow、Issue 模板、PR 模板、dependabot 配置等都放这里） |
| `CNAME` | GitHub Pages 自定义域名时用，本项目不用 |

本项目根目录就有 `AGENTS.md`（给 AI 助手的项目规范）和 `CLAUDE.md`，这是项目自定义的协作文档。

## 1.4 Clone / Fork / Star / Watch

四个高频动作，新手经常分不清：

### Clone（克隆）

把远程仓库**完整复制一份到本地**，包括所有分支、所有历史 commit。

```bash
# SSH 克隆本项目
$ git clone git@github.com:QiHaoo/my-mall.git
Cloning into 'my-mall'...
remote: Enumerating objects: 1234, done.
...

# 克隆到指定目录名
$ git clone git@github.com:QiHaoo/my-mall.git my-mall-dev

# 只克隆最新一次 commit（大仓库省流量，但没历史）
$ git clone --depth 1 git@github.com:QiHaoo/my-mall.git
```

> 本项目的 `.github/workflows/mkdocs.yml` 里就用了 `fetch-depth: 0`（完整历史），因为 MkDocs 的 git-revision-date 插件需要历史信息。

### Fork（分叉）

在 GitHub 上**把别人的仓库复制一份到你自己账号下**，副本和原仓库保留关联（GitHub 知道这是从哪 fork 来的）。

典型场景：你想给开源项目 `vuejs/core` 贡献代码，但不能直接推它的仓库 → fork 一份到自己账号 `你的名字/core` → clone 你那份到本地 → 改完 push 回你那份 → 在 GitHub 上发起 Pull Request 请求把改动合回 `vuejs/core`。

```
vuejs/core  ──fork──►  你的名字/core  ──clone──►  本地
                           │                          │
                           └────── push ──────────────┘
                           │
                           └── Pull Request ──► vuejs/core
```

### Star（标星）

给仓库点个赞 / 收藏。意义：
- 项目主页右上角那个 Star 按钮，点了就 +1
- 是项目受欢迎程度的粗略指标
- Star 过的仓库在你个人主页 `Stars` 标签下能找到，相当于收藏夹

### Watch（关注）

订阅仓库的活动通知。比 Star 更「重」：
- Watching：仓库有任何动静（新 issue、新 PR、新 release、新评论）都发邮件通知
- Releases only：只通知发新版本
- Discussions only：只通知讨论区
- Ignore：完全屏蔽

> 一般人 Star 多、Watch 少。只有你深度参与的项目才 Watch，否则邮件会被吵爆。

## 1.5 Issue / Pull Request / Discussion 三兄弟

这三个是 GitHub 上最核心的「协作载体」，新手经常分不清该用哪个。

### Issue（议题）

**提出问题、跟踪任务、报告 bug、请求新功能**的地方。

- 每条 Issue 有唯一编号（如 `#42`）
- 可以打 Label（bug、enhancement、help-wanted……）
- 可以指派 Assignee（谁负责处理）
- 可以放进 Milestone（哪个版本要解决）
- 可以关联 PR（PR 描述里写 `fixes #42`，合并后 Issue 自动关闭）

例子：「商品分类拖拽排序时，跨层级移动偶尔报错」 → 开个 Issue 描述复现步骤，等开发者修复。

### Pull Request（PR，拉取请求）

**请求把某个分支的改动合并到另一个分支**。是 GitHub 协作的核心，后面 03 章会详细讲。

- 「Pull」是从目标分支视角说的：我有一堆改动，请你「拉」过去
- 一次 PR = 一次代码审查 + 合并的单元
- 不是 Git 的原生概念，是 GitHub（及同类平台）加在 Git 之上的协作封装

例子：你建了分支 `fix/cart-bug`，改了 3 个文件，提 PR 请求合并到 `main` → reviewer 审查 → 通过后点 Merge。

> GitHub 把 PR 也归类成一种特殊的 Issue（PR 列表和 Issue 列表其实可以混着看），所以 PR 也有编号、Label、Assignee。

### Discussion（讨论）

**问问题、聊想法、收集意见**的场所，是 Issue 的「轻量版」。

什么时候用 Discussion 而非 Issue：
- 不确定是不是 bug，想先讨论一下 → Discussion
- 有个想法但还没决定要不要做 → Discussion
- 提问怎么用这个项目 → Discussion
- 确定要做的具体任务 / 明确的 bug → Issue

Discussion 默认不开启，仓库 Settings → General → Features → Discussions 勾选才出现。

### 三者对比

| | Issue | Pull Request | Discussion |
|---|------|--------------|------------|
| 本质 | 一条任务/问题记录 | 一次代码合并请求 | 一条讨论帖 |
| 必须改代码吗 | 不一定 | 必须（PR 一定有代码改动） | 不涉及代码 |
| 默认关闭条件 | 解决了 / PR 合并 | 合并 / 关闭 | 讨论结束（不会自动关） |
| 编号 | #1, #2… | 和 Issue 共用编号池 | 单独编号 |
| 典型用法 | 「这个 bug 修一下」 | 「我修了 bug，请合并」 | 「这块设计大家怎么看」 |

## 1.6 Commit、Branch、Merge

这三个是 Git 本身的概念，但 GitHub UI 上天天显示，必须熟。

### Commit（提交）

把工作区的改动「存档」为一次版本快照。每个 commit 有：
- 一个 40 字符的 SHA-1 哈希（如 `a1b2c3d...`），全局唯一
- 作者、时间戳
- 提交信息（commit message）

```bash
$ git commit -m "feat(cart): 购物车支持批量勾选"
[main a1b2c3d] feat(cart): 购物车支持批量勾选
 3 files changed, 42 insertions(+), 8 deletions(-)
```

> 本项目遵循 Conventional Commits 规范（见 [git-workflow.md](../../git-workflow.md)），提交信息前缀有 `feat / fix / docs / refactor / chore` 等。

### Branch（分支）

一条**独立的开发线**。可以从某个 commit 拉出新分支，在新分支上随便改，不影响主干。

```
main:     A ── B ── C ────────────── F (merge)
                  \                 /
feature:           D ── E ─────────
```

```bash
# 当前在 main 上，建一个新分支并切过去
$ git switch -c feature/cart-batch-check
Switched to a new branch 'feature/cart-batch-check'

# 等价老写法
$ git checkout -b feature/cart-batch-check
```

GitHub 上仓库的分支列表：主页 → 分支下拉框，或 `Code` 页面左侧 branch selector。

### Merge（合并）

把一个分支的改动合并到另一个分支。在 GitHub 上通常通过 **Pull Request → Merge 按钮** 完成，三种合并方式（详见 03 章）：

| 方式 | 行为 | 历史 |
|------|------|------|
| Merge commit | 保留所有分支历史 + 加一个 merge commit | 多分叉 |
| Squash and merge | 把分支上的多个 commit 压成一个，再合到目标分支 | 干净线性 |
| Rebase and merge | 把分支上的 commit 逐个挪到目标分支末尾 | 线性，保留每个 commit |

## 1.7 本地与远程的同步

克隆后本地仓库有一个默认远程叫 `origin`，指向你克隆的那个 GitHub 仓库。

```bash
# 查看远程地址
$ git remote -v
origin  git@github.com:QiHaoo/my-mall.git (fetch)
origin  git@github.com:QiHaoo/my-mall.git (push)

# 拉取远程更新（不合并到工作区）
$ git fetch origin

# 拉取并合并到当前分支（= fetch + merge）
$ git pull

# 推送本地提交到远程
$ git push origin main
```

**日常节奏：** 开始工作前 `git pull` 拿最新代码 → 本地改 + commit → `git push` 推到远程。

## 1.8 本项目对照

回到本项目 `QiHaoo/my-mall`：

- 仓库类型：public
- 默认分支：`main`
- 远程协议：SSH（`git@github.com:QiHaoo/my-mall.git`）
- 根目录有 `README.md`、`AGENTS.md`、`CLAUDE.md`、`.gitignore`、`docker-compose.yml`
- `.github/workflows/mkdocs.yml` 是一个 GitHub Actions workflow（05 章详解）
- `docs/` 是文档源目录，由 workflow 构建成静态站后部署到 GitHub Pages（06 章详解）
- 项目用 Issue / PR 协作（03 章详解），遵循 [git-workflow.md](../../git-workflow.md) 里的分支与提交规范

## 小结

| 你应该记住的 |
|---|
| Git 是版本控制软件，GitHub 是基于 Git 的协作平台 |
| SSH key 一次配置免密推送，公钥贴 GitHub，私钥留本地 |
| Clone = 复制仓库到本地；Fork = 复制别人仓库到自己账号 |
| Issue 提问题、PR 提代码合并请求、Discussion 聊想法 |
| Commit 是快照，Branch 是独立开发线，Merge 是合并分支 |
| 日常节奏：pull → 改 + commit → push |

下一章 [02-协作工作流](./02-collaboration-workflows.md) 讲业界怎么用这些基础元素组织团队协作 —— GitHub Flow、GitFlow、Trunk-based 三大流派。
