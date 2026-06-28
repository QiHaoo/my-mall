# 镜像构建与 Harbor 集成设计

> 本文档定义 `docker-publish.yml` 工作流的详细设计，覆盖后端 / 前端 Dockerfile 设计、Harbor 镜像仓库集成、安全扫描与缓存优化。
>
> - Harbor 部署与初始化配置详见 [harbor-setup.md](./harbor-setup.md)
> - CI/CD 整体编排见 [overview.md](./overview.md)（`docker-publish` 通过 `workflow_call` 被主编排器调用）

---

## 一、概述

`docker-publish.yml` 是可复用工作流（`workflow_call`），被 CI/CD 主编排器调用。**仅在 main 分支 push 或 tag 推送时执行**，职责包括：

1. **多阶段 Dockerfile 构建镜像** —— 后端 13 个服务 + 前端 1 个，共 14 个镜像
2. **推送 Harbor** —— 私有镜像仓库统一管理
3. **安全扫描** —— Trivy 扫描镜像 CVE 漏洞

```
主编排器 (ci.yml)
  │
  ├─ ci-test        ← 单元测试 + 集成测试（通过后才会调用本工作流）
  │
  └─ docker-publish ← 本文档：构建 + 推送 + 扫描
       │
       ├─ matrix: 13 后端服务 → Dockerfile.backend → Harbor
       ├─ matrix: 1 前端      → Dockerfile.frontend → Harbor
       │
       └─ trivy 扫描（第一版宽松，记录不阻断）
```

---

## 二、后端 Dockerfile 设计

### 2.1 完整 Dockerfile

以下为统一模板 `docker/Dockerfile.backend`，以 `mall-product` 为默认值示例，各服务通过 `build-arg` 覆盖 `SERVICE_NAME` 与 `SERVICE_PORT`：

```dockerfile
# ===========================================================================
# my-mall 后端服务统一 Dockerfile 模板
# 所有业务微服务共用此文件，通过 build-arg 传入 SERVICE_NAME / SERVICE_PORT
# ===========================================================================

# ---------------------------------------------------------------------------
# 阶段 1: Maven 构建
# 使用全量 Maven + JDK 镜像，仅在构建阶段使用，不进入最终镜像
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder

# 服务名（如 mall-product）与端口，由构建参数注入
ARG SERVICE_NAME=mall-product
ARG SERVICE_PORT=7200

WORKDIR /build

# 先 COPY 所有 pom.xml，利用 Docker layer cache 加速依赖下载
# 依赖不变时，此层命中缓存，跳过 go-offline 下载（~200MB → 0s）
COPY pom.xml ./
COPY mall-common/pom.xml   mall-common/
COPY mall-gateway/pom.xml  mall-gateway/
COPY mall-auth/pom.xml     mall-auth/
COPY mall-member/pom.xml   mall-member/
COPY mall-product/pom.xml  mall-product/
COPY mall-search/pom.xml   mall-search/
COPY mall-cart/pom.xml     mall-cart/
COPY mall-order/pom.xml    mall-order/
COPY mall-ware/pom.xml     mall-ware/
COPY mall-coupon/pom.xml   mall-coupon/
COPY mall-seckill/pom.xml  mall-seckill/
COPY mall-third/pom.xml    mall-third/
COPY mall-admin/pom.xml    mall-admin/
COPY mall-oss/pom.xml      mall-oss/

# 预下载依赖（仅 pom.xml 变化时重新执行）
RUN mvn dependency:go-offline -pl mall-common,${SERVICE_NAME} -am -B

# COPY 源码（源码变化只触发从此处开始的层重建）
COPY mall-common/src   mall-common/src
COPY ${SERVICE_NAME}/src  ${SERVICE_NAME}/src

# 编译打包，跳过测试（测试已在 CI 的 test 阶段完成）
RUN mvn package -pl ${SERVICE_NAME} -am -DskipTests -B

# ---------------------------------------------------------------------------
# 阶段 2: 运行时镜像
# 仅含 JRE，体积约 170MB + jar ~30-50MB
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# 创建非 root 用户
RUN addgroup -S mall && adduser -S mall -G mall

WORKDIR /app

# 从构建阶段复制 jar（ARG 在多阶段构建中需重新声明）
ARG SERVICE_NAME=mall-product
ARG SERVICE_PORT=7200
COPY --from=builder /build/${SERVICE_NAME}/target/*.jar app.jar

# 切换到非 root 用户运行
USER mall

# 声明服务端口（文档用途，实际监听端口由 application.yml 决定）
EXPOSE ${SERVICE_PORT}

# JVM 参数：MaxRAMPercentage 让 JVM 自适应容器内存限制
# 可在 K8s 部署时通过 env 覆盖，无需重建镜像
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 2.2 关键指令设计说明

| 指令 | 设计意图 |
|------|----------|
| `FROM maven:3.9-eclipse-temurin-21 AS builder` | 多阶段构建阶段 1，使用官方 Maven + JDK 21 全量镜像。此镜像仅存在于构建阶段，不进入最终产物 |
| `ARG SERVICE_NAME=mall-product` | 通过构建参数注入服务名，使同一 Dockerfile 模板适用于全部 13 个后端服务。默认值 `mall-product` 供本地单独构建时使用 |
| `ARG SERVICE_PORT=7200` | 服务端口同样参数化，`EXPOSE` 指令引用此变量。端口值与各服务 `application.yml` 中的 `server.port` 保持一致 |
| `WORKDIR /build` | 构建阶段工作目录，隔离构建产物 |
| `COPY pom.xml ...` (14 个模块) | 先复制全部 `pom.xml` 文件。Docker 按层缓存，只要 pom.xml 不变，后续 `dependency:go-offline` 层命中缓存，跳过 ~200MB 依赖下载 |
| `RUN mvn dependency:go-offline -pl mall-common,${SERVICE_NAME} -am -B` | 预下载依赖。`-pl` 指定目标模块，`-am` 同时构建依赖模块（mall-common 是公共依赖），`-B` 批处理模式关闭交互提示 |
| `COPY .../src ...` | 源码单独一层，与 pom.xml 层分离。源码变更只触发此层及后续层重建，依赖层保持缓存命中 |
| `RUN mvn package -pl ${SERVICE_NAME} -am -DskipTests -B` | 编译打包目标服务。`-DskipTests` 跳过测试——测试已在 CI 流水线的前置阶段（ci-test job）执行，镜像构建阶段不重复 |
| `FROM eclipse-temurin:21-jre-alpine` | 多阶段构建阶段 2，仅含 JRE 21 的 Alpine 镜像。体积约 170MB，满足安全扫描基线要求 |
| `RUN addgroup -S mall && adduser -S mall -G mall` | 创建非 root 系统用户。容器以 `mall` 用户运行，符合安全最佳实践，防止容器逃逸后获取 root 权限 |
| `COPY --from=builder ... app.jar` | 仅从构建阶段复制 jar 产物。Maven、源码、依赖缓存等全部留在构建阶段，不进入运行镜像 |
| `USER mall` | 切换到非 root 用户。此指令之后的进程均以 `mall` 身份运行 |
| `EXPOSE ${SERVICE_PORT}` | 声明容器监听端口，供文档与编排工具参考。实际端口由 `application.yml` 的 `server.port` 决定 |
| `ENV JAVA_OPTS="..."` | JVM 参数通过环境变量注入。`MaxRAMPercentage=75.0` 让 JVM 堆自动适配容器内存限制（CGroup awareness），`UseG1GC` 适用低延迟场景 |
| `ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]` | 使用 `sh -c` 间接调用以支持 `$JAVA_OPTS` 变量展开。Exec 格式 `ENTRYPOINT` 不做变量替换，必须借助 shell |

---

## 三、前端 Nginx Dockerfile 设计

### 3.1 完整 Dockerfile

文件路径 `docker/Dockerfile.frontend`：

```dockerfile
# ===========================================================================
# my-mall 前端 (mall-admin-frontend) Dockerfile
# 多阶段构建：Node.js 编译 → Nginx 运行
# ===========================================================================

# ---------------------------------------------------------------------------
# 阶段 1: pnpm 构建
# ---------------------------------------------------------------------------
FROM node:20-alpine AS builder

# 启用 corepack 管理 pnpm 版本（Node 20 内置 corepack）
RUN corepack enable

WORKDIR /build

# 先复制依赖描述文件，利用 layer cache
COPY package.json pnpm-lock.yaml ./

# --frozen-lockfile 确保使用 lockfile 中的精确版本，CI 中不允许 lockfile 漂移
RUN pnpm install --frozen-lockfile

# 复制源码并构建
COPY . .
RUN pnpm build

# ---------------------------------------------------------------------------
# 阶段 2: Nginx 运行时
# ---------------------------------------------------------------------------
FROM nginx:1.27-alpine

# 从构建阶段复制静态产物到 Nginx 默认目录
COPY --from=builder /build/dist /usr/share/nginx/html

# 覆盖默认配置（SPA 路由 + gzip + 缓存 + API 反向代理）
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
```

> **前端 `.dockerignore`**：前端构建上下文（`mall-admin-frontend/`）须配置 `.dockerignore` 排除 `node_modules/`、`dist/`、`.git/`，否则 `COPY . .` 会将本地 `node_modules`（可达数百 MB）复制进构建容器，拖慢构建并可能引入平台不兼容的依赖。

### 3.2 nginx.conf 生产级配置

文件路径 `docker/nginx.conf`：

```nginx
# ===========================================================================
# mall-admin-frontend Nginx 配置
# SPA history 模式路由 + gzip + 静态资源缓存 + API 反向代理
# ===========================================================================

# API 网关上游（K8s 中替换为 Service DNS: mall-gateway.<namespace>.svc.cluster.local:1000）
upstream mall_gateway {
    server mall-gateway:1000;
}

server {
    listen       80;
    server_name  _;

    # 根目录指向 Vite 构建产物
    root   /usr/share/nginx/html;
    index  index.html;

    # -----------------------------------------------------------------------
    # gzip 压缩
    # -----------------------------------------------------------------------
    gzip               on;
    gzip_comp_level    6;
    gzip_min_length    1024;
    gzip_vary          on;
    gzip_proxied       any;
    gzip_types
        text/plain
        text/css
        text/xml
        application/json
        application/javascript
        application/xml
        application/xml+rss
        text/javascript
        image/svg+xml;

    # -----------------------------------------------------------------------
    # API 反向代理 → Spring Cloud Gateway
    # /api/ 前缀转发到网关，保留完整路径
    # -----------------------------------------------------------------------
    location /api/ {
        proxy_pass http://mall_gateway;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 请求体大小限制（后台管理涉及文件上传）
        client_max_body_size  50m;

        # 超时设置
        proxy_connect_timeout 10s;
        proxy_read_timeout    60s;
        proxy_send_timeout    60s;
    }

    # -----------------------------------------------------------------------
    # 静态资源缓存
    # Vite 构建的带 hash 文件名（assets/*.js, *.css）可长期缓存
    # -----------------------------------------------------------------------
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # -----------------------------------------------------------------------
    # SPA history 模式路由
    # 前端路由不匹配时回退到 index.html，由 Vue Router 接管
    # -----------------------------------------------------------------------
    location / {
        try_files $uri $uri/ /index.html;
    }

    # index.html 禁止缓存——确保用户总是拿到最新版本入口
    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }

    # 安全头
    add_header X-Frame-Options       "SAMEORIGIN";
    add_header X-Content-Type-Options "nosniff";
    add_header X-XSS-Protection      "1; mode=block";
}
```

### 3.3 nginx.conf 关键配置说明

| 配置项 | 作用 |
|--------|------|
| `upstream mall_gateway` | 定义网关上游地址。本地 Docker Compose 用 `mall-gateway:1000`，K8s 中通过 ConfigMap 覆盖为 Service DNS |
| `proxy_pass http://mall_gateway` | `/api/` 请求转发到网关，保留完整路径前缀。网关层做路由分发到各微服务 |
| `gzip on` + `gzip_types` | 启用 gzip 压缩，压缩级别 6。JS/CSS/JSON 等文本资源压缩率可达 60-80% |
| `expires 1y` + `immutable` | Vite 构建产物文件名含 contenthash（如 `index-a1b2c3d4.js`），内容变更时文件名随之变化，可安全长期缓存 |
| `try_files $uri $uri/ /index.html` | SPA history 模式核心：路由不匹配时回退到 `index.html`，交给前端路由处理 |
| `Cache-Control: no-cache` (index.html) | `index.html` 是入口文件，禁止缓存确保用户始终获取最新版本引用 |
| `X-Frame-Options` / `X-Content-Type-Options` | 安全响应头，防止点击劫持与 MIME 类型嗅探 |

---

## 四、Dockerfile 模板复用策略

### 4.1 模板文件规划

```
my-mall/
├── docker/
│   ├── Dockerfile.backend      # 后端 13 个服务统一模板
│   ├── Dockerfile.frontend     # 前端 Nginx 模板
│   └── nginx.conf              # 前端 Nginx 配置
```

**复用原则**：

- 后端 13 个服务（`mall-common` 是公共依赖模块，不单独构建镜像）复用同一 `Dockerfile.backend`，通过 `--build-arg SERVICE_NAME=mall-{service} --build-arg SERVICE_PORT={port}` 区分
- 前端单独使用 `Dockerfile.frontend`，构建上下文为 `mall-admin-frontend/` 目录
- 后端构建上下文为项目根目录（`.`），因为多模块 Maven 项目需要访问 `pom.xml` 及各模块源码

### 4.2 后端服务镜像与端口规划

| 服务名 | 镜像名 | 端口 | 说明 |
|--------|--------|------|------|
| `gateway` | `registry.mall.local/mall/gateway` | 1000 | API 网关 |
| `auth` | `registry.mall.local/mall/auth` | 2000 | 认证授权 |
| `search` | `registry.mall.local/mall/search` | 4000 | 搜索服务 |
| `cart` | `registry.mall.local/mall/cart` | 5000 | 购物车 |
| `seckill` | `registry.mall.local/mall/seckill` | 6000 | 秒杀服务 |
| `coupon` | `registry.mall.local/mall/coupon` | 7000 | 营销中心 |
| `member` | `registry.mall.local/mall/member` | 7100 | 会员中心 |
| `product` | `registry.mall.local/mall/product` | 7200 | 商品中心 |
| `oss` | `registry.mall.local/mall/oss` | 7300 | 对象存储 |
| `ware` | `registry.mall.local/mall/ware` | 7400 | 库存中心 |
| `order` | `registry.mall.local/mall/order` | 7500 | 订单中心 |
| `third` | `registry.mall.local/mall/third` | 8000 | 第三方服务 |
| `admin` | `registry.mall.local/mall/admin` | 9000 | 后台管理 |
| `frontend` | `registry.mall.local/mall/frontend` | 80 | 前端 Nginx |

> **说明**：`mall-common` 是公共依赖模块（被各服务 `-am` 引入），不单独构建镜像。端口与各服务 `application.yml` 中 `server.port` 保持一致。

---

## 五、docker-publish.yml 工作流

```yaml
# ===========================================================================
# docker-publish.yml —— 镜像构建与推送可复用工作流
# 被 CI/CD 主编排器通过 workflow_call 调用
# 职责：多阶段 Dockerfile 构建镜像 → 推送 Harbor → Trivy 安全扫描
# ===========================================================================
name: Docker Publish

on:
  workflow_call:
    inputs:
      ref:
        description: '构建的 Git 引用（分支名或 tag 名）'
        required: false
        type: string
        default: ''

# 同一服务的新构建取消旧构建
concurrency:
  group: docker-publish-${{ github.ref }}
  cancel-in-progress: true

jobs:
  docker-publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
      security-events: write  # Trivy 扫描结果上传到 Security tab

    strategy:
      fail-fast: false  # 单个服务构建失败不影响其他服务
      matrix:
        include:
          # ---- 后端服务（统一使用 Dockerfile.backend）----
          - { service: "gateway",  context: ".", file: "docker/Dockerfile.backend",  port: "1000" }
          - { service: "auth",     context: ".", file: "docker/Dockerfile.backend",  port: "2000" }
          - { service: "search",   context: ".", file: "docker/Dockerfile.backend",  port: "4000" }
          - { service: "cart",     context: ".", file: "docker/Dockerfile.backend",  port: "5000" }
          - { service: "seckill",  context: ".", file: "docker/Dockerfile.backend",  port: "6000" }
          - { service: "coupon",   context: ".", file: "docker/Dockerfile.backend",  port: "7000" }
          - { service: "member",   context: ".", file: "docker/Dockerfile.backend",  port: "7100" }
          - { service: "product",  context: ".", file: "docker/Dockerfile.backend",  port: "7200" }
          - { service: "oss",      context: ".", file: "docker/Dockerfile.backend",  port: "7300" }
          - { service: "ware",     context: ".", file: "docker/Dockerfile.backend",  port: "7400" }
          - { service: "order",    context: ".", file: "docker/Dockerfile.backend",  port: "7500" }
          - { service: "third",    context: ".", file: "docker/Dockerfile.backend",  port: "8000" }
          - { service: "admin",    context: ".", file: "docker/Dockerfile.backend",  port: "9000" }
          # ---- 前端 ----
          - { service: "frontend", context: "mall-admin-frontend", file: "docker/Dockerfile.frontend", port: "80" }

    steps:
      # ------------------------------------------------------------------
      # 1. 检出代码
      # ------------------------------------------------------------------
      - name: Checkout
        uses: actions/checkout@v4

      # ------------------------------------------------------------------
      # 2. 配置 Docker Buildx（支持多阶段构建 + 缓存导出）
      # ------------------------------------------------------------------
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      # ------------------------------------------------------------------
      # 3. 登录 Harbor
      # ------------------------------------------------------------------
      - name: Login to Harbor
        uses: docker/login-action@v3
        with:
          registry: ${{ secrets.HARBOR_REGISTRY }}
          username: ${{ secrets.HARBOR_USERNAME }}
          password: ${{ secrets.HARBOR_PASSWORD }}

      # ------------------------------------------------------------------
      # 4. 提取镜像 tag（latest + commit hash + semver）
      # ------------------------------------------------------------------
      - name: Extract metadata (tags, labels)
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ secrets.HARBOR_REGISTRY }}/mall/${{ matrix.service }}
          tags: |
            # main 分支：打 latest 标签
            type=raw,value=latest,enable=${{ github.ref == 'refs/heads/main' }}
            # tag 推送（v*.*.*）：打 latest + semver 标签
            type=raw,value=latest,enable=${{ startsWith(github.ref, 'refs/tags/v') }}
            # 所有场景：打 commit hash 短格式标签（精确回滚用）
            type=sha,format=short
            # tag 推送：输出原始版本号（如 v1.0.0）
            type=semver,pattern={{raw}}

      # ------------------------------------------------------------------
      # 5. 构建并推送镜像
      # ------------------------------------------------------------------
      - name: Build and push
        id: build
        uses: docker/build-push-action@v5
        with:
          context: ${{ matrix.context }}
          file: ${{ matrix.file }}
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          # 后端服务传入 SERVICE_NAME / SERVICE_PORT 构建参数
          # 前端 Dockerfile 不使用这些参数，传入也无副作用
          build-args: |
            SERVICE_NAME=mall-${{ matrix.service }}
            SERVICE_PORT=${{ matrix.port }}
          # Buildx 缓存：利用 Harbor 作为缓存后端
          cache-from: type=registry,ref=${{ secrets.HARBOR_REGISTRY }}/mall/${{ matrix.service }}:buildcache
          cache-to: type=registry,ref=${{ secrets.HARBOR_REGISTRY }}/mall/${{ matrix.service }}:buildcache,mode=max

      # ------------------------------------------------------------------
      # 6. Trivy 安全扫描（第一版宽松：记录不阻断）
      # ------------------------------------------------------------------
      - name: Trivy image scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ secrets.HARBOR_REGISTRY }}/mall/${{ matrix.service }}:${{ steps.meta.outputs.version }}
          format: sarif
          output: trivy-results.sarif
          exit-code: 0           # 第一版不阻断，仅记录
          ignore-unfixed: true   # 忽略尚无修复方案的漏洞
          severity: CRITICAL,HIGH

      - name: Upload Trivy scan results to GitHub Security
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: trivy-results.sarif
```

---

## 六、镜像 tag 策略

`docker/metadata-action@v5` 根据触发事件自动生成 tag：

| 触发场景 | 生成的 tag | 示例（以 product 为例） |
|---------|-----------|----------------------|
| Push main | `latest` + commit hash | `mall/product:latest`、`mall/product:a1b2c3d` |
| Tag `v*.*.*` | semver + `latest` + commit hash | `mall/product:v1.0.0`、`mall/product:latest`、`mall/product:a1b2c3d` |
| 手动触发 (workflow_dispatch) | commit hash | `mall/product:a1b2c3d` |

**tag 用途**：

| tag 类型 | 用途 |
|---------|------|
| `latest` | ArgoCD 滚动部署的默认追踪标签。每次 main 合并或 tag 发布都会更新 |
| `commit hash`（如 `a1b2c3d`） | 精确回滚。出问题时可一键切换到任意历史版本，不受 `latest` 滚动影响 |
| `semver`（如 `v1.0.0`） | 正式版本标记。对应 Git tag，便于版本追溯与发版管理 |

---

## 七、镜像命名规范

Harbor 中的镜像统一命名格式：

```
registry.mall.local/mall/{service}:{tag}
```

| 组成部分 | 说明 | 示例 |
|---------|------|------|
| `registry.mall.local` | Harbor 仓库地址（内部 DNS） | `registry.mall.local` |
| `mall` | Harbor 项目名（创建项目时固定） | `mall` |
| `{service}` | 服务名，不含 `mall-` 前缀 | `gateway`、`auth`、`product`、`frontend` |
| `{tag}` | 版本标签 | `latest`、`v1.0.0`、`a1b2c3d` |

**完整示例**：

```
registry.mall.local/mall/gateway:latest
registry.mall.local/mall/product:v1.0.0
registry.mall.local/mall/frontend:a1b2c3d
```

> **注意**：服务名统一去掉 `mall-` 前缀，因为 Harbor 项目名已是 `mall`，避免 `mall/mall-product` 的冗余。镜像名即服务名，简洁明了。

---

## 八、安全扫描策略

### 8.1 Trivy 扫描配置

使用 `aquasecurity/trivy-action@master` 对推送后的镜像进行 CVE 漏洞扫描：

- **扫描方式**：直接扫描镜像（`image-ref`），而非文件系统扫描
- **输出格式**：SARIF，上传到 GitHub Security tab 可视化查看
- **漏洞过滤**：`ignore-unfixed: true` 忽略尚无官方修复方案的漏洞，减少噪音
- **严重级别**：扫描 CRITICAL + HIGH 级别

### 8.2 扫描级别与处理策略

| 阶段 | exit-code | 行为 | 说明 |
|------|-----------|------|------|
| **第一版（当前）** | `0` | 记录不阻断 | `continue-on-error` 等效。扫描结果上传 GitHub Security tab 供审计，但不阻止镜像发布。目的是先建立漏洞基线 |
| **基线建立后** | `1` | CRITICAL 阻断 | 收紧为 `exit-code: 1`，仅 CRITICAL 级别漏洞阻断发布。HIGH 级别记录告警 |
| **成熟期** | `1` | CRITICAL + HIGH 阻断 | 进一步收紧，HIGH 及以上级别漏洞阻断发布，强制修复后才能上线 |

> **演进原则**：先建立漏洞可见性（记录），再逐步收紧阻断策略。避免一开始就阻断导致 CI 频繁失败，影响开发效率。

---

## 九、缓存优化

### 9.1 Buildx 缓存策略

利用 Docker Buildx 的 `cache-from` / `cache-to` 将构建缓存存储到 Harbor，实现跨构建的层复用：

```yaml
cache-from: type=registry,ref=registry.mall.local/mall/{service}:buildcache
cache-to: type=registry,ref=registry.mall.local/mall/{service}:buildcache,mode=max
```

| 参数 | 说明 |
|------|------|
| `cache-from` | 构建前从 Harbor 拉取 `buildcache` 镜像作为缓存源 |
| `cache-to` | 构建后将缓存层推送到 Harbor 的 `buildcache` 镜像 |
| `mode=max` | 缓存所有中间层（包括多阶段构建的 builder 阶段），最大化缓存命中率 |

### 9.2 缓存效果

| 场景 | 无缓存 | 有缓存 | 加速比 |
|------|--------|--------|--------|
| 后端依赖未变（仅源码变更） | ~6min（go-offline + 编译） | ~1min（跳过 go-offline，仅编译） | ~6x |
| 前端依赖未变（仅源码变更） | ~3min（pnpm install + build） | ~30s（跳过 install，仅 build） | ~6x |
| 全量变更（pom.xml / package.json 变化） | ~6min / ~3min | ~6min / ~3min（缓存失效） | 1x |

### 9.3 缓存层设计（后端 Dockerfile）

```
Layer 1: COPY pom.xml (14个)          ← 依赖不变时命中缓存
Layer 2: RUN mvn dependency:go-offline ← Layer 1 命中则跳过（省 ~200MB 下载）
Layer 3: COPY src                     ← 源码变更只触发此层及后续重建
Layer 4: RUN mvn package              ← 重新编译（~40s）
```

---

## 十、关键设计决策

| # | 决策 | 影响 |
|---|------|------|
| 1 | **多阶段构建** —— 构建环境（Maven + JDK）与运行环境（JRE）分离 | 最终镜像仅含 JRE + jar，体积从 ~800MB 降到 ~200MB，减少攻击面 |
| 2 | **JRE Alpine 基础镜像** —— `eclipse-temurin:21-jre-alpine` 约 170MB | 镜像体积小、安全漏洞面小，生产级安全扫描通过率高 |
| 3 | **非 root 运行** —— 创建 `mall` 系统用户，`USER mall` 切换 | 安全最佳实践，防止容器逃逸后获取 root 权限 |
| 4 | **JAVA_OPTS 环境变量** —— JVM 参数通过 `ENV` 注入，`sh -c` 展开 | 可在 K8s 部署时通过 env 覆盖，无需重建镜像。`MaxRAMPercentage` 让 JVM 自适应容器内存限制 |
| 5 | **pom 缓存层** —— 先 `COPY pom.xml` 再 `dependency:go-offline` | 依赖不变时利用 Docker layer cache 跳过 ~200MB 依赖下载，构建从 ~6min 降到 ~1min |
| 6 | **-DskipTests** —— 镜像构建阶段跳过测试 | 测试已在 CI 流水线前置阶段（ci-test job）完成，镜像构建不重复执行，加速构建 |
| 7 | **矩阵并行构建** —— 13 后端 + 1 前端 = 14 个 job 并行 | 充分利用 GitHub Actions 并发额度，总构建时间取最慢服务而非累加 |
| 8 | **双 tag 策略** —— `latest` + commit hash | `latest` 供 ArgoCD 自动滚动部署，commit hash 供精确回滚到任意历史版本 |
| 9 | **Trivy 安全扫描** —— 第一版 `exit-code: 0`（宽松） | 记录漏洞但不阻断发布，先建立漏洞基线，待基线稳定后收紧为 CRITICAL 级别阻断 |
| 10 | **前端 Dockerfile 自包含构建** —— 不依赖 frontend-ci 的 artifact | 工作流解耦更清晰，每个服务的镜像构建独立，不依赖其他 job 的 artifact 传递 |

---

## 十一、Harbor 配置关联

> Harbor 的详细部署与配置步骤见 [harbor-setup.md](./harbor-setup.md)。以下为本工作流依赖的 Harbor 侧配置项。

### 11.1 Harbor 项目配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 项目名 | `mall` | 镜像统一存放于此项目下 |
| 访问级别 | 私有 | 仅授权账号可推拉镜像 |
| 镜像保留策略 | `latest` 保留最近 10 个 tag | 防止 tag 无限增长，定期清理历史版本 |

### 11.2 CI 机器人账号

创建专用的 CI 推送账号（机器人账号），不使用管理员账号：

| 配置项 | 说明 |
|--------|------|
| 账号名 | `mall-ci` |
| 权限 | `mall` 项目的推送权限（Developer 角色） |
| 用途 | 仅用于 GitHub Actions 推送镜像，不用于 K8s 拉取（拉取另配只读账号或匿名拉取） |

### 11.3 GitHub Secrets 配置

在 GitHub 仓库 → Settings → Secrets and variables → Actions 中配置以下 Secrets：

| Secret 名 | 值 | 说明 |
|-----------|-----|------|
| `HARBOR_REGISTRY` | `registry.mall.local` | Harbor 仓库地址（不含协议与端口，默认 443 HTTPS） |
| `HARBOR_USERNAME` | `mall-ci` | CI 机器人账号 |
| `HARBOR_PASSWORD` | `<机器人账号密码>` | CI 机器人账号密码（或 API Token） |

### 11.4 K8s 拉取镜像配置

K8s 集群需配置 `imagePullSecret` 以拉取私有镜像：

```yaml
# k8s secret：mall-harbor-pull
apiVersion: v1
kind: Secret
metadata:
  name: mall-harbor-pull
  namespace: mall
type: kubernetes.io/dockerconfigjson
data:
  .dockerconfigjson: <base64 编码的 docker config json>
```

```bash
# 生成 imagePullSecret
kubectl create secret docker-registry mall-harbor-pull \
  --docker-server=registry.mall.local \
  --docker-username=mall-pull \
  --docker-password=<只读账号密码> \
  --namespace=mall
```

> ArgoCD 部署时在 Deployment 的 `spec.template.spec.imagePullSecrets` 中引用此 Secret。
