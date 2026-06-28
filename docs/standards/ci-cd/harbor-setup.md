# Harbor 私有镜像仓库部署指南

> 本文档指导 my-mall 项目本地开发环境部署 Harbor 2.x 私有镜像仓库，并对接 GitHub Actions CI 推送流水线。
> Harbor 采用官方安装包独立部署，**不纳入项目 `docker-compose.yml`**，原因见 [十、关键设计决策](#十关键设计决策)。

---

## 一、为什么选 Harbor

Harbor 2.x 是 CNCF 毕业项目，企业级容器镜像仓库，支持镜像管理、漏洞扫描、签名、RBAC、镜像保留策略等能力。本项目技术选型已定为 Harbor 2.x（见 AGENTS.md 技术选型表「镜像仓库」一行）。

对比其他镜像仓库方案：

| 能力 | Harbor 2.x | Docker Registry | GHCR (GitHub Container Registry) |
|------|-----------|-----------------|----------------------------------|
| Web 管理 UI | 有 | 无 | 无（GitHub 页面） |
| 漏洞扫描 | 内置 Trivy | 无 | 无 |
| RBAC 权限 | 项目级 + 角色细粒度 | 无 | 仓库级 |
| 镜像签名 | Cosign / Notary v2 | 无 | 无 |
| 镜像保留策略 | 自动清理旧 tag | 无 | 无 |
| 复制同步 | 跨仓库同步 | 无 | 无 |
| 私有化部署 | 支持（首选场景） | 支持（仅存储） | 公有为主，私有需 Enterprise |
| 审计日志 | 有 | 无 | 有（GitHub Audit） |
| 机器人账号 | 原生支持 | 无 | PAT 替代 |

**结论**：Docker Registry 仅提供存储无管理能力；GHCR 以公有为主，私有化与策略治理能力弱；Harbor 适合本地/私有化部署，满足本项目 CI 推送 + 漏洞扫描 + 保留策略的需求。

---

## 二、部署方式

Harbor 采用**官方 docker-compose 安装包独立部署**，不纳入项目 `docker-compose.yml`。

| 维度 | 说明 |
|------|------|
| 部署方式 | 官方 `harbor-offline-installer` 离线安装包 |
| 部署位置 | 本地开发机 Docker 环境（WSL2 + Docker Engine） |
| 不纳入项目 compose 的理由 | Harbor 安装包自带 9 个组件（core / nginx / db / redis / jobservice / trivy / chartmuseum / registryctl / exporter 等），配置复杂、有独立 `harbor.yml` 与 `prepare` 预处理流程，混入项目 compose 会破坏中间件编排的单一职责 |
| 与项目 compose 的关系 | 物理隔离，各自独立启停；Harbor 连接信息通过本文档 + GitHub Secrets 维护 |

---

## 三、部署步骤

### 3.1 下载 Harbor 安装包

在 WSL2 终端执行，下载 Harbor 2.11.0 离线安装包（offline-installer 自带全部镜像，无需联网拉取）：

```bash
# 下载并解压（建议放在独立目录，如 ~/harbor-install）
mkdir -p ~/harbor-install && cd ~/harbor-install
curl -sL https://github.com/goharbor/harbor/releases/download/v2.11.0/harbor-offline-installer-v2.11.0.tgz | tar xz
cd harbor
```

> 安装包解压后包含 `install.sh`、`prepare`、`harbor.yml.tmpl`、`docker-compose.yml`（Harbor 自己的，与项目无关）以及 `common/` 目录（`prepare` 生成）。

### 3.2 配置 harbor.yml

基于 `harbor.yml.tmpl` 复制为 `harbor.yml` 并修改关键字段。以下为本地开发环境的配置模板：

```bash
cp harbor.yml.tmpl harbor.yml
```

```yaml
# harbor.yml — 本地开发环境配置
# 配置说明见 https://goharbor.io/docs/2.11.0/install-config/configure-yml-file/

# Harbor 访问域名（开发机通过 hosts 解析到 127.0.0.1）
hostname: registry.mall.local

# HTTP 配置（开发环境用 HTTP，生产环境必须 HTTPS）
http:
  port: 80

# HTTPS 配置（开发环境注释掉；生产环境必须启用并提供 CA 签发证书）
# https:
#   port: 443
#   certificate: /data/harbor/cert/server.crt
#   private_key: /data/harbor/cert/server.key

# Harbor 管理员密码（生产环境必须用强密码并定期轮换）
# 强密码生成：openssl rand -base64 24
harbor_admin_password: <替换为强密码>

# Harbor 内置数据库密码
database:
  password: <替换为强密码>
  max_idle_conns: 50
  max_open_conns: 1000
  conn_max_lifetime: 5m
  conn_max_idle_time: 0

# 数据存储卷（镜像层、数据库、Trivy 缓存等）
data_volume: /data/harbor

# Trivy 漏洞扫描（内置，启用）
trivy:
  ignore_unfixed: false
  skip_update: false
  skip_java_db_update: false
  offline_scan: false
  security_check: vuln
  insecure: false

# Prometheus 指标（启用，对接项目监控体系）
metric:
  enabled: true
  port: 9090
  path: /metrics

# 日志配置
log:
  level: info
  local:
    rotate_count: 50
    rotate_size: 200M
    location: /var/log/harbor
```

> **密码生成**：执行 `openssl rand -base64 24` 生成随机强密码，替换上述 `<替换为强密码>` 占位符。不要使用弱密码或默认值 `Harbor12345`。

### 3.3 执行安装

```bash
# 预处理配置：生成 docker-compose.yml、nginx 配置、密钥等
./prepare

# 后台启动 Harbor 全部组件
docker compose up -d

# 查看组件状态（应全部为 healthy）
docker compose ps
```

带 Trivy 扫描的安装可直接使用 `./install.sh --with-trivy`，或在 `harbor.yml` 中配置 `trivy` 段后执行 `./prepare` + `docker compose up -d`。本指南采用后者，配置更显式。

### 3.4 验证部署

1. 浏览器访问 `http://registry.mall.local`（需先按 3.5 配置 hosts）
2. 使用默认账号 `admin` / 配置的 `harbor_admin_password` 登录
3. 验证各组件健康：
   - 顶部菜单 → 「拦截器」无报错
   - 「项目」页面可正常加载
   - 「扫描器」显示 Trivy 可用
4. 命令行验证组件容器状态：

```bash
# 在 harbor 安装目录执行，所有组件应为 healthy
docker compose ps
```

### 3.5 配置 hosts

在开发机配置 hosts，将 `registry.mall.local` 解析到本地：

- **WSL2 / Linux**：编辑 `/etc/hosts`
- **Windows**：以管理员身份编辑 `C:\Windows\System32\drivers\etc\hosts`

```
127.0.0.1 registry.mall.local
```

> WSL2 环境下，Windows 侧的 hosts 与 WSL2 侧的 hosts 需同步配置，确保浏览器（Windows）与 Docker CLI（WSL2）均可解析该域名。

---

## 四、Harbor 项目配置

### 4.1 创建 mall 项目

登录 Harbor UI → 「项目」→ 「新建项目」：

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 项目名称 | `mall` | 与项目命名一致，存放所有微服务镜像 |
| 访问级别 | 私有 | 不公开，需认证才能拉取 |
| 存储配额 | `-1`（无限制） | 开发环境不限制；生产环境按需设置上限 |

### 4.2 创建 CI 机器人账号

为 CI 流水线创建专用机器人账号，遵循最小权限原则，**不使用 admin 账号推送镜像**。

操作路径：进入 `mall` 项目 → 「机器人账号」→ 「添加机器人账号」

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 名称 | `ci-robot` | CI 专用账号 |
| 权限 | 推送（Push）+ 读取（Pull） | 仅满足镜像推送与拉取，无管理权限 |
| 过期时间 | 1 年 | 到期前需更新 GitHub Secrets 中的密码 |
| 描述 | GitHub Actions CI 推送镜像 | 标记用途 |

创建后 Harbor 会生成机器人账号的密码（仅展示一次，需立即保存），此密码将配置到 GitHub Secrets。

### 4.3 配置镜像保留策略

对 `mall` 项目配置保留策略，自动清理旧 tag，避免存储膨胀。

操作路径：进入 `mall` 项目 → 「策略」→ 「保留策略」→ 「添加规则」

| 配置项 | 值 |
|--------|-----|
| 适用仓库 | `**`（项目下所有仓库） |
| 保留策略 | 保留最近 N 个 tag |
| N | 10 |
| 排除 | `latest`（永不清理，保证稳定引用 tag） |
| 调度 | 每日执行 |

**影响**：超过 10 个非 latest tag 的旧镜像将被自动删除，避免开发环境镜像堆积。

### 4.4 配置漏洞扫描

操作路径：Harbor UI → 「配置管理」→ 「扫描器」，确认 Trivy 为默认扫描器。

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 扫描器 | Trivy（Harbor 内置） | 与 CI 流水线中的 Trivy 扫描策略一致 |
| 扫描时机 | 推送时自动扫描（Scan on push） | 镜像 push 后立即触发扫描 |
| 阻断策略 | 记录但暂不阻断 | 与 CI Trivy 扫描策略一致，待漏洞基线建立后再收紧为阻断 |

> **阻断策略演进**：当前阶段记录漏洞不阻断，便于观察。建立漏洞基线后，可将策略调整为「严重漏洞阻断拉取」，与 CI 中 Trivy 严重漏洞 fail-fast 形成双重防护。

---

## 五、GitHub Secrets 配置

CI 流水线推送镜像到 Harbor 需要凭据，通过 GitHub Secrets 注入，避免明文暴露。

| Secret 名 | 值 | 用途 |
|-----------|-----|------|
| `HARBOR_REGISTRY` | `registry.mall.local` | Harbor 仓库地址 |
| `HARBOR_USERNAME` | `ci-robot` | CI 机器人账号（含项目前缀，如为项目级机器人则用 `robot$mall+ci-robot`，以 Harbor 实际生成格式为准） |
| `HARBOR_PASSWORD` | （机器人账号密码） | CI 机器人密码（4.2 创建时保存的值） |

**配置路径**：GitHub 仓库 → `Settings` → `Secrets and variables` → `Actions` → `New repository secret`。

> **安全提示**：机器人账号密码为敏感信息，仅存于 GitHub Secrets，不得写入代码或 workflow 文件。账号过期前需提前更新密码并同步 Secrets。

---

## 六、Docker 客户端信任 HTTP Harbor

开发环境 Harbor 使用 HTTP（无 HTTPS），Docker 客户端默认拒绝与 HTTP 仓库交互，需配置 `insecure-registries` 信任。

配置文件路径：

- **Linux / WSL2**：`/etc/docker/daemon.json`
- **Windows Docker Desktop**：Settings → Docker Engine（JSON 编辑器）

```json
{
  "insecure-registries": ["registry.mall.local:80"]
}
```

配置后重启 Docker 使其生效：

```bash
# Linux / WSL2（Docker Engine）
sudo systemctl restart docker

# Windows Docker Desktop：在界面点击 Apply & Restart
```

> **生产环境警告**：`insecure-registries` 仅用于开发环境。生产环境必须使用 HTTPS + CA 签发证书，**不得**配置 insecure-registries，否则镜像传输无加密，存在被篡改风险。

---

## 七、验证 CI 推送

部署完成后，手动验证 Docker 客户端能否正常登录并推送镜像到 Harbor：

```bash
# 1. 登录 Harbor（使用机器人账号）
docker login registry.mall.local -u ci-robot -p <password>

# 2. 拉取测试镜像
docker pull hello-world

# 3. 打标签，指向 mall 项目
docker tag hello-world registry.mall.local/mall/test:latest

# 4. 推送到 Harbor
docker push registry.mall.local/mall/test:latest
```

验证成功后，在 Harbor UI → `mall` 项目 → `test` 仓库查看镜像。若开启了推送时扫描，应能看到 Trivy 扫描结果。

> 验证通过后可删除测试镜像：Harbor UI → `mall/test` → 删除仓库；或命令行删除 tag。

---

## 八、生产环境注意事项

开发环境与生产环境的 Harbor 配置存在显著差异，生产部署必须按生产标准执行：

| 配置项 | 开发环境 | 生产环境 |
|--------|---------|---------|
| HTTP/HTTPS | HTTP | 必须 HTTPS + CA 签发证书 |
| insecure-registries | 需要配置 | 不需要（且禁止配置） |
| 管理员密码 | 强密码即可 | 强密码 + 定期轮换（如每 90 天） |
| 数据备份 | 无 | 定期备份 `/data/harbor`（数据库 + 镜像存储） |
| HTTPS 证书 | 不需要 | CA 签发证书（如 Let's Encrypt / 内部 CA） |
| 镜像签名 | 无 | Cosign 签名，部署侧校验签名 |
| 网络暴露 | 本机访问 | 内网访问，限制访问来源（防火墙/安全组） |
| 高可用 | 单实例 | 多实例 + 共享存储（S3/NFS） + 负载均衡 |
| 漏洞阻断策略 | 记录不阻断 | 严重漏洞阻断拉取 |

---

## 九、运维操作速查

在 Harbor 安装目录（`~/harbor-install/harbor`）执行以下命令：

| 操作 | 命令 |
|------|------|
| 停止 Harbor | `docker compose down` |
| 启动 Harbor | `docker compose up -d` |
| 重新配置（修改 `harbor.yml` 后） | `./prepare && docker compose up -d` |
| 查看日志 | `docker compose logs -f` |
| 查看指定组件日志 | `docker compose logs -f core` |
| 查看组件状态 | `docker compose ps` |
| 查看存储占用 | Harbor UI → 项目 → 存储统计 |
| 升级 Harbor | 备份 `/data/harbor` → 下载新版安装包 → 更新 `harbor.yml` → `./prepare && docker compose up -d` |

> **升级注意**：跨大版本升级前必须完整备份 `/data/harbor` 目录（含数据库），并查阅目标版本的 Release Notes 确认有无破坏性变更。Harbor 升级路径通常需按版本顺序逐级升级。

---

## 十、关键设计决策

| 决策 | 选择 | 理由 | 影响 |
|------|------|------|------|
| 1. 部署方式 | 独立部署，不纳入项目 compose | Harbor 含 9 个组件、配置复杂、有独立 `prepare` 流程 | 部署独立，连接信息通过本文档 + GitHub Secrets 维护 |
| 2. 开发环境协议 | HTTP | 简化本地开发，避免证书管理开销 | 需配置 `insecure-registries`；生产环境必须 HTTPS |
| 3. CI 推送账号 | 机器人账号 `ci-robot`，不用 admin | 最小权限原则 | CI 仅能推送/读取，无管理权限；降低凭据泄露影响面 |
| 4. 镜像保留策略 | 保留最近 10 个 tag，排除 latest | 自动清理，避免存储膨胀 | 旧 tag 自动删除，开发环境无需人工维护 |
| 5. 漏洞扫描 | Trivy 内置 + 推送时扫描，暂不阻断 | 与 CI Trivy 扫描策略一致，先观察后收紧 | 推送时与 CI 时双重扫描覆盖；待基线建立后收紧为阻断 |
