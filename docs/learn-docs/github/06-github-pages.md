# 06 · GitHub Pages

> GitHub Pages 是 GitHub 提供的**静态站点托管服务**。本项目的文档站 `https://qihao0o.github.io/my-mall/` 就跑在 GitHub Pages 上。本章讲清楚：站点类型、两种部署方式、自定义域名、使用限制、与常见文档框架的搭配。

## 6.1 GitHub Pages 是什么

一句话：**把仓库里的静态文件（HTML/CSS/JS/图片）托管成一个公网可访问的网站**。

特点：
- **免费**（public 仓库，private 仓库需付费版）
- **HTTPS 自动启用**
- **全球 CDN**（Fastly 提供）
- **无需服务器**，纯静态托管
- **自定义域名**支持
- **流量软限制**（每月 100GB，个人项目够用）

它不是应用服务器，不能跑 PHP / Java / Node 后端。只能托管**静态文件**。所以配合的文档框架（Jekyll、MkDocs、Hugo、VitePress、Docusaurus）都是「写 Markdown → 编译成静态 HTML → 托管」的模式。

## 6.2 两种站点类型

### User/Organization Site（用户/组织站点）

一个账号只能有**一个**用户站点，地址是 `https://<用户名>.github.io/`。

- 仓库名必须叫 `<用户名>.github.io`（如 `QiHaoo.github.io`）
- 部署后访问 `https://<用户名>.github.io/`（无子路径）
- 适合：个人主页、博客

### Project Site（项目站点）

每个仓库可以有**一个**项目站点，地址是 `https://<用户名>.github.io/<仓库名>/`。

- 仓库名随意（如 `my-mall`）
- 部署后访问 `https://qihao0o.github.io/my-mall/`（注意有 `/my-mall/` 子路径）
- 适合：项目文档、项目展示页

> 本项目是 project site：`https://qihao0o.github.io/my-mall/`。

### 子路径带来的坑

project site 的地址有 `/my-mall/` 前缀，影响所有资源链接：

- `<img src="/logo.png">` 会被解析成 `https://qihao0o.github.io/logo.png` → 404
- 应该写成 `<img src="/my-mall/logo.png">` 或相对路径

文档框架要配 `site_url` 或 `base_url`：

- MkDocs：`mkdocs.yml` 里 `site_url: https://qihao0o.github.io/my-mall/`（本项目就是这么配的）
- VitePress：`config.ts` 里 `base: '/my-mall/'`
- Hugo：`config.toml` 里 `baseURL = "https://qihao0o.github.io/my-mall/"`

漏配的典型症状：本地预览正常，上线后样式 / 图片 / 链接全 404。

## 6.3 两种部署方式

GitHub Pages 历史上有两种部署来源，**现在推荐用 GitHub Actions**。

### 方式一：Deploy from a branch（旧，简单）

仓库 Settings → Pages → Source 选 **Deploy from a branch**：

- 选一个分支（如 `main`）
- 选目录（`/ (root)` 或 `/docs`）
- GitHub 自动把那个目录的静态文件部署到 Pages

特点：
- 配置超简单，选两下就完事
- 推送到该分支就自动重新部署
- **只支持 Jekyll 自动构建**（如果用 Jekyll，GitHub 会自动 build）
- 用其他框架（MkDocs / Hugo）需要你**手动构建**后把 `site/` 内容提交到那个分支（生成 `gh-pages` 分支的常见做法）
- 构建过程不可控，不能跑自定义脚本

### 方式二：GitHub Actions（新，灵活，**推荐**）

仓库 Settings → Pages → Source 选 **GitHub Actions**：

- 部署完全由你的 workflow 控制
- workflow 里用 `actions/configure-pages` + `actions/upload-pages-artifact` + `actions/deploy-pages` 三件套
- 可以跑任何构建步骤（pip install、mvn package、npm run build）
- 构建过程可见、可调试、可加缓存
- 支持自定义构建环境

> 本项目就是方式二。`.github/workflows/mkdocs.yml` 全程控制构建和部署。

### 为什么推荐 GitHub Actions 方式

| | Deploy from a branch | GitHub Actions |
|---|----------------------|----------------|
| 配置难度 | 简单（选两下） | 中等（写 workflow） |
| 构建框架 | 只 Jekyll 自动 | 任意 |
| 自定义构建 | 不行 | 完全可控 |
| 多环境 / 多分支 | 弱 | 强 |
| 调试能力 | 弱 | 看 Actions 日志 |
| 未来扩展 | 受限 | 可加 lint、test、deploy 多个环境 |

只要超过「放几个静态 HTML」的复杂度，都用 GitHub Actions 方式。

## 6.4 GitHub Actions 部署 Pages 的标准三件套

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      # ... 你的构建步骤，产出 site/ 目录 ...

      # 1. 上传 Pages artifact（固定写法）
      - uses: actions/upload-pages-artifact@v3
        with:
          path: site    # 静态站点根目录

  deploy:
    needs: build
    runs-on: ubuntu-latest
    # 2. 声明部署到 Pages 环境
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    permissions:
      pages: write
      id-token: write     # OIDC，deploy-pages 必需
    steps:
      # 3. 部署
      - uses: actions/deploy-pages@v4
        id: deployment
```

三个 Action 的作用：

| Action | 做什么 |
|--------|--------|
| `actions/configure-pages` | （可选）读 Pages 配置，准备 base_path 等。一般省略也能跑 |
| `actions/upload-pages-artifact` | 把 `site/` 打包成 artifact |
| `actions/deploy-pages` | 从 artifact 部署到 Pages，输出 `page_url` |

**workflow 级必须有：**
```yaml
permissions:
  contents: read
  pages: write
  id-token: write    # 关键！deploy-pages 用 OIDC 鉴权
```

漏了 `id-token: write` 会报 `deploy-pages` 鉴权失败。

## 6.5 开启 GitHub Pages（一次性配置）

1. 仓库 Settings → Pages
2. **Build and deployment → Source** 选 **GitHub Actions**
3. 保存

此时 Pages 还没有内容。等你的 workflow 第一次成功跑完，访问地址才上线。

> 本项目已配置好。新仓库第一次开 Pages 选 GitHub Actions 后，可以把官方提供的 starter workflow（Actions 页面 → New workflow → 搜 pages）作为起点。

## 6.6 本地预览 + 上线部署全流程

以本项目 MkDocs 为例：

### 本地预览

```bash
# 安装 mkdocs-material（一次性）
$ py -3 -m pip install --user mkdocs-material

# 启动本地服务，监听文件变化热刷新
$ py -3 -m mkdocs serve
# 访问 http://127.0.0.1:8000/my-mall/  (注意有 /my-mall/，因为 site_url 配了子路径)
```

### 写 / 改文档

编辑 `docs/` 下任意 `.md`，浏览器自动刷新看效果。

### 上线部署

```bash
$ git add docs/ mkdocs.yml
$ git commit -m "docs: 更新 xxx 文档"
$ git push origin main
```

推送后：

1. GitHub 检测到 push 改了 `docs/**` → 触发 `mkdocs.yml` workflow
2. workflow 的 build job 跑：checkout 代码 → 装 Python 3.12 → pip install mkdocs-material → `mkdocs build --strict --clean` → 上传 `site/` artifact
3. deploy job 跑：从 artifact 部署到 GitHub Pages
4. 1-2 分钟后访问 https://qihao0o.github.io/my-mall/ 看到最新内容

> 详细操作手册见 [docs-site-deployment.md](../../docs-site-deployment.md)。

## 6.7 自定义域名

默认地址 `https://<用户名>.github.io/<仓库名>/`。想用独立域名（如 `docs.yourdomain.com`）：

### 步骤

1. **DNS 配置**：在域名服务商为子域名加 CNAME 记录：
   ```
   docs.yourdomain.com  CNAME  qihao0o.github.io.
   ```
   （注意末尾的点，CNAME 指向你的 GitHub Pages 默认地址）

2. **仓库配置**：仓库根目录或 `docs/` 下放一个 `CNAME` 文件（无扩展名，全大写），内容只有一行你的域名：
   ```
   docs.yourdomain.com
   ```
   或者在 GitHub UI：Settings → Pages → Custom domain → 填域名 → Save（GitHub 会自动创建 `CNAME` 文件提交到仓库）。

3. **HTTPS**：等 DNS 生效后（几分钟到几小时），Settings → Pages → Enforce HTTPS 勾上。GitHub 用 Let's Encrypt 自动签发并续期证书。

4. **更新 site_url**：`mkdocs.yml` 里 `site_url` 改成 `https://docs.yourdomain.com/`，保证 sitemap、分享链接、canonical URL 正确。

### 顶级域名 vs 子域名

| 类型 | DNS 记录 | 例子 |
|------|---------|------|
| 子域名 | CNAME | `docs.example.com` → `qihao0o.github.io.` |
| 顶级域名（apex） | A 记录 | `example.com` → GitHub Pages 的 IP（多个） |

顶级域名用 A 记录指向 GitHub 提供的 IP（在 Pages 文档里查最新 IP 列表）。

### CNAME 文件注意

- 用 GitHub Actions 部署方式时，`CNAME` 文件必须在构建产物 `site/` 里
- MkDocs：把 `CNAME` 放在 `docs/` 下，构建时会自动复制到 `site/`
- 其他框架可能要 `cp CNAME site/` 单独处理

## 6.8 使用限制

| 限制 | 数值 | 说明 |
|------|------|------|
| 仓库可见性 | public 免费 / private 需 Pro/Team | 免费账号 private 仓库 Pages 不可用 |
| 站点大小 | 1 GB | 静态文件总和 |
| 月流量 | 100 GB（软限制） | 超了 GitHub 会邮件提醒，不强制下线 |
| 单次构建 | 10 分钟 | workflow 超时 |
| 文件数 | 限制不严格 | 但几万小文件部署慢 |
| 商业用途 | 允许 | 但不能用于「主要目的是提供免费服务的 SaaS」 |

> 软限制 = 超了 GitHub 一般不立刻封，会先提醒。硬限制 = 超了直接拒绝。

### 禁止用途

- 作为 CDN（单纯存图片/视频给其他网站用）
- 主要用于提供免费服务的 SaaS 后端
- 大流量文件分发（用 GitHub Releases 或 Release asset 替代）

## 6.9 常见搭配框架

| 框架 | 语言 | 特点 | 适合 |
|------|------|------|------|
| **MkDocs Material** | Python | 项目文档首选，移动端好，中文搜索友好 | 项目文档 ★ 本项目用 |
| Jekyll | Ruby | GitHub Pages 原生支持，老牌博客系统 | 博客 |
| Hugo | Go | 构建极快，主题多 | 博客、文档 |
| VitePress | Vue/Node | Vue 驱动，前端开发者友好 | 组件库文档、前端项目 |
| Docusaurus | React/Node | Meta 出品，功能丰富 | 大型开源项目文档 |
| Astro | Node | 现代静态站框架，灵活 | 个人站、博客 |

**选型建议：**

- Java 后端项目文档 → MkDocs Material（本项目选型理由）
- 前端组件库 → VitePress
- 个人博客 → Hugo / Jekyll
- 大型开源项目 → Docusaurus

## 6.10 mkdocs.yml 里和 Pages 相关的配置

```yaml
site_name: my-mall
site_url: https://qihao0o.github.io/my-mall/    # 关键：和 Pages 地址一致
site_description: 商城系统学习项目
repo_url: https://github.com/QiHaoo/my-mall     # 仓库链接
repo_name: QiHaoo/my-mall
docs_dir: docs                                   # 文档源目录
```

- `site_url` 决定所有资源链接的前缀，必须和 Pages 地址完全一致
- 改用自定义域名后，这里同步改
- `repo_url` 让文档右上角出现「GitHub 仓库」链接

## 6.11 常见问题

**Q：推了 docs/ 改动，访问 Pages 还是旧的？**

A：先看仓库 Actions 标签页，workflow 是不是跑成功了。失败（红色）的话点进去看日志，常见原因：
- `mkdocs build --strict` 抓到死链 → 修死链
- `pip install` 网络问题 → 重跑
- `id-token: write` 没配 → 部署阶段鉴权失败

**Q：本地预览要加 `/my-mall/` 前缀吗？**

A：要。因为 `site_url` 配了子路径 `/my-mall/`，MkDocs 据此生成所有资源前缀。本地 `http://127.0.0.1:8000/my-mall/`，否则样式 404。

**Q：用了自定义域名后，原 `github.io` 地址还能访问吗？**

A：能，GitHub 会自动重定向到自定义域名。但 sitemap 等以 `site_url` 为准，所以 `mkdocs.yml` 改成自定义域名更干净。

**Q：private 仓库能用 Pages 吗？**

A：免费账号不行。需 GitHub Pro（$4/月）或 Team。或者改用 Cloudflare Pages（免费支持 private 仓库），见 [docs-site-deployment.md 第六节](../../docs-site-deployment.md)。

**Q：怎么强制 HTTPS？**

A：Settings → Pages → Enforce HTTPS 勾上。自定义域名要等证书签发后才能勾。

## 小结

| 你应该记住的 |
|---|
| GitHub Pages = 免费静态站点托管，自动 HTTPS + CDN |
| 两种站点：user site（`用户名.github.io`）/ project site（`用户名.github.io/仓库名/`） |
| 两种部署：Deploy from a branch（旧）/ GitHub Actions（新，推荐） |
| project site 地址有 `/仓库名/` 子路径，框架要配 `site_url` / `base_url` |
| Actions 部署三件套：configure-pages / upload-pages-artifact / deploy-pages |
| 必须配 `permissions: pages: write, id-token: write` |
| 自定义域名用 CNAME 文件 + DNS CNAME 记录 |
| public 仓库免费无限，private 需付费版 |

下一章 [07-Release 与版本管理](./07-releases-versioning.md) 讲怎么用 GitHub 发版本（虽然本项目暂未用，但学完对开源项目发版、SemVer 语义化版本会有完整认识）。
