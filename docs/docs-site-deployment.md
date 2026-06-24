# 文档站部署指南（MkDocs Material + GitHub Pages）

> 本文档记录如何把项目 `docs/` 目录渲染成一个带侧边栏导航、全文搜索、移动端自适应的文档站，并部署到 GitHub Pages 实现公网随时访问（平板 / 手机 / 任意电脑浏览器）。

## 一、方案概览

| 项 | 选型 |
|----|------|
| 文档框架 | [MkDocs](https://www.mkdocs.org/) + [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/) 主题 |
| 配置文件 | [`mkdocs.yml`](../../mkdocs.yml)（项目根目录） |
| 文档源 | [`docs/`](../../docs/) 目录 |
| 本地预览 | `mkdocs serve`（实时热刷新） |
| 公网部署 | GitHub Actions 自动构建 → GitHub Pages |
| 部署地址 | <https://qihao0o.github.io/my-mall/> |

**为什么选 MkDocs Material：** 专为项目文档设计，移动端体验佳，内置全文搜索（支持中文）、明暗主题、代码复制、Tab 导航；纯静态产物，托管零成本。符合本项目「生产级标准」定位。

## 二、本地预览

### 2.1 安装

仅需 Python 3.8+，一条命令装好框架 + 主题：

```bash
# Windows（已装 Python）
py -3 -m pip install --user mkdocs-material

# macOS / Linux
python3 -m pip install --user mkdocs-material
```

> `mkdocs-material` 会自动带上 `mkdocs` 本体，无需单独安装。

### 2.2 启动本地服务

在项目根目录执行：

```bash
py -3 -m mkdocs serve
```

默认监听 `http://127.0.0.1:8000`。由于 `mkdocs.yml` 中 `site_url` 配置了子路径 `/my-mall/`（与 GitHub Pages 部署路径保持一致），本地访问地址为：

```
http://127.0.0.1:8000/my-mall/
```

启动后会监听 `docs/` 和 `mkdocs.yml` 的变化，**修改文档自动热刷新**，无需重启。

### 2.3 让平板 / 手机在局域网内访问

默认 `mkdocs serve` 只绑定 `127.0.0.1`，局域网设备访问不到。绑定到所有网卡：

```bash
py -3 -m mkdocs serve --dev-addr 0.0.0.0:8000
```

然后查本机局域网 IP：

```bash
# Windows
ipconfig | findstr IPv4
# macOS / Linux
ifconfig | grep inet
```

确保平板 / 手机与电脑连同一个 WiFi，浏览器访问：

```
http://<本机局域网IP>:8000/my-mall/
# 例如 http://192.168.31.227:8000/my-mall/
```

> ⚠️ Windows 防火墙可能拦截入站连接，首次访问若超时，需在「Windows Defender 防火墙 → 允许应用通过防火墙」中放行 Python，或临时关闭防火墙测试。

### 2.4 构建静态产物（可选）

```bash
py -3 -m mkdocs build --clean
```

产物输出到 `site/`（已在 `.gitignore` 中忽略），可丢到任意静态服务器（Nginx / OSS / Cloudflare Pages）托管。`--strict` 参数会在有死链时构建失败，适合 CI 校验：

```bash
py -3 -m mkdocs build --strict --clean
```

## 三、GitHub Pages 公网部署

### 3.1 前置条件

1. 仓库已推送到 GitHub（本项目：`QiHaoo/my-mall`）
2. **仓库需为 public**：GitHub 免费账号的 Pages 仅对 public 仓库免费；private 仓库需 GitHub Pro / Team 付费计划。学习项目建议设为 public。

   > 若仓库当前是 private：Settings → 滚到底部 Danger Zone → Change visibility → Make public。
   >
   > 若坚持用 private 仓库又想免费公网访问，见 [第六节 Cloudflare Pages 替代方案](#六替代方案cloudflare-pages支持-private-仓库)。

### 3.2 开启 GitHub Pages（一次性）

1. 进入仓库 **Settings → Pages**
2. **Build and deployment → Source** 选择 **GitHub Actions**（不是 *Deploy from a branch*）

   > 这一步是关键：选 GitHub Actions 后，部署由 workflow 触发，而不是从某个分支静态读文件。本项目 workflow 见 [`.github/workflows/mkdocs.yml`](../../.github/workflows/mkdocs.yml)。

3. 保存。此时 Pages 还没有内容，等首次推送触发 workflow 后才会生成。

### 3.3 自动部署流程

部署已由 [.github/workflows/mkdocs.yml](../../.github/workflows/mkdocs.yml) 配置好，逻辑如下：

- **触发条件**：推送到 `main` 分支，且改动涉及 `docs/**`、`mkdocs.yml` 或 workflow 本身；也支持手动触发（Actions 页面 → Run workflow）
- **构建**：Ubuntu runner 装 Python 3.12 + mkdocs-material，执行 `mkdocs build --strict --clean`（死链会直接构建失败，保证文档质量）
- **部署**：用官方 `actions/upload-pages-artifact` + `actions/deploy-pages` 推送到 GitHub Pages

因此日常更新文档的流程就是：

```bash
# 1. 写 / 改 docs/ 下的 markdown
# 2. 提交并推送
git add docs/ mkdocs.yml
git commit -m "docs: 更新 xxx 文档"
git push origin main
# 3. 等 1~2 分钟，访问 https://qihao0o.github.io/my-mall/ 即可看到最新内容
```

### 3.4 查看部署状态与日志

- 仓库 **Actions** 标签页可看到每次 "Deploy MkDocs to GitHub Pages" 的运行记录
- 构建失败（通常是 `--strict` 抓到死链）会在 Actions 里红色标出，点进去看日志定位修复
- 部署成功后，Settings → Pages 顶部会显示最终公网 URL

### 3.5 自定义域名（可选）

默认地址是 `https://<用户名>.github.io/my-mall/`。想用独立域名（如 `docs.yourdomain.com`）：

1. 在 DNS 服务商为该域名添加 CNAME 记录，指向 `qihao0o.github.io.`
2. 仓库根目录新建 `docs/CNAME` 文件（无扩展名），内容写你的域名：
   ```
   docs.yourdomain.com
   ```
3. 推送后等 DNS 生效（通常几分钟到几小时），GitHub Pages 会自动启用 HTTPS（Let's Encrypt）

> 用了自定义域名后，`mkdocs.yml` 的 `site_url` 建议同步改成 `https://docs.yourdomain.com/`，以保证 sitemap、搜索、分享链接正确。

## 四、配置说明（mkdocs.yml）

关键配置点速览，方便后续调整：

| 配置 | 作用 |
|------|------|
| `site_url` | 公网地址，决定子路径 `/my-mall/`；改域名时同步更新 |
| `docs_dir: docs` | 文档源目录 |
| `nav` | 左侧 / 顶部导航结构，新增文档需在此登记 |
| `theme.palette` | 明暗主题 + 自动跟随系统 |
| `plugins.search.separator` | 中文分词分隔符，保证中文可搜索 |
| `markdown_extensions` | 表格、代码高亮、admonition 提示框、Tab 等扩展 |

> **新增文档后：** 把文件放进 `docs/` 对应目录，再到 `mkdocs.yml` 的 `nav` 里登记一项，否则它不会出现在导航中（但仍可被搜索到）。

## 五、常见问题

**Q：本地访问是 `localhost:8000`，为什么要加 `/my-mall/` 前缀？**
A：`site_url` 配了子路径 `/my-mall/`（与 GitHub Pages 项目站点路径一致），MkDocs 据此生成所有资源链接的相对前缀。不加前缀会导致本地能看、上线后样式 / 链接 404。

**Q：strict 构建报「anchor 不存在」INFO，要不要修？**
A：不影响页面渲染和部署（是 INFO 不是 WARNING）。是文档里手写的标题锚点（如 `#返回值与-http-状态码`）和 MkDocs 自动生成的 slug 不一致，点击这类跨文档链接可能跳不到精确小节。想彻底对齐可后续单独清理。

**Q：中文搜不到？**
A：`mkdocs.yml` 已配 `search.separator` 按中文字符切分。若仍异常，确认 `plugins.search.lang` 包含 `zh`。

**Q：改了文档没生效？**
A：本地 `mkdocs serve` 会自动热刷新；线上需 `git push` 触发 workflow，等 1~2 分钟。

## 六、替代方案：Cloudflare Pages（支持 private 仓库）

若仓库为 private 又想免费公网访问，用 Cloudflare Pages 替代 GitHub Pages：

1. 注册 Cloudflare → Pages → Create a project → Connect to Git → 授权并选 `QiHaoo/my-mall` 仓库（private 也可）
2. 构建配置：
   - Framework preset：`None`
   - Build command：`pip install mkdocs-material && mkdocs build`
   - Build output directory：`site`
   - 环境变量：`PYTHON_VERSION = 3.12`
3. 部署后得到 `https://my-mall.pages.dev`，支持自定义域名 + 免费 HTTPS + 全球 CDN

> Cloudflare Pages 对 private 仓库免费，且不限流量，是 GitHub Pages 的优质替代。切换后记得把 `mkdocs.yml` 的 `site_url` 改成 Cloudflare 地址，并删除或停用 `.github/workflows/mkdocs.yml`（避免两边重复部署）。
