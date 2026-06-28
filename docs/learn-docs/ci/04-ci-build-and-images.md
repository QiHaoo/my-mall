# 04 · 构建产物与镜像仓库

> 前置阅读：[01-docker-basics.md](./01-docker-basics.md)、[02-ci-fundamentals.md](./02-ci-fundamentals.md)、[03-ci-testing-strategy.md](./03-ci-testing-strategy.md)
>
> 本章讲清三件事：CI 怎么把源码变成 Docker 镜像、镜像怎么推送到 Harbor、tag 策略和安全扫描怎么设计。
> 看完后你会理解一次 `git push` 到 main 后，14 个服务的镜像是怎么并行构建并推到镜像仓库的。

---

## 一、CI 的产出物是什么

上一章讲了 CI 中的测试——`mvn verify` 跑完后产出的是测试结果和覆盖率报告。但 CI 的最终目的不是「跑测试」，而是**产出可部署的东西**。

### 1.1 什么是「制品」

测试通过后，CI 就可以放心地构建「制品」了。**制品（Artifact）= 可部署的产物**——它是最终要在服务器上运行的东西。

打个比方：

```
源代码    ←→  食材（生的，不能直接吃）
测试      ←→  检查食材新不新鲜（合格才下锅）
构建      ←→  把食材做成菜（烹饪）
制品      ←→  做好的菜（可以直接端上桌）
部署      ←→  端上桌给客人吃
```

测试是「检查」，构建是「加工」，制品是「加工后的成品」。

### 1.2 制品的几种形式

不同技术栈的制品形式不同：

| 制品形式 | 优点 | 缺点 | 本项目 |
|---------|------|------|--------|
| jar 包 | 简单直接，Maven 产物 | 需目标机器装 JDK，环境不一致 | 不用 |
| Docker 镜像 | 环境自包含，到哪都能跑 | 需镜像仓库 | ✅ 用 |
| 前端静态文件 | 轻量 | 需 Nginx 托管 | 打包进 Nginx 镜像 |

逐个解释：

**jar 包**——Java 最传统的制品。`mvn package` 产出一个 `.jar` 文件，丢到服务器上 `java -jar app.jar` 就能跑。问题是：服务器必须装 JDK，而且每台服务器的 JDK 版本、系统依赖可能不同，容易出「我这能跑你那不能跑」的问题。

**Docker 镜像**——把应用代码 + JRE 运行环境 + 系统依赖全部打包成一个镜像。镜像到了哪台机器上行为都一样，因为它自带运行环境。这是本项目的选择。

**前端静态文件**——前端 `pnpm build` 产出一堆 HTML/JS/CSS 静态文件（`dist/` 目录）。这些文件本身不能独立运行，需要 Nginx 这样的 Web 服务器托管。所以本项目的做法是：**把静态文件打包进 Nginx 镜像**，统一成 Docker 镜像这一种制品格式。

### 1.3 为什么统一用 Docker 镜像

本项目有 14 个后端 Java 服务 + 1 个前端。如果后端用 jar、前端用静态文件，那部署流程就要分两套——后端用 Java 部署工具，前端用 Nginx 配置。

统一成 Docker 镜像后：

```
后端服务 → Docker 镜像（JRE + jar）  ─┐
                                       ├─→ 统一的部署流程（K8s + ArgoCD）
前端     → Docker 镜像（Nginx + dist）─┘
```

所有服务都是镜像，部署方式完全一样——K8s 拉镜像、启动容器。不区分前后端，不区分技术栈。这就是「统一制品格式」的好处。

> **一句话总结**：制品 = 可部署的成品。本项目统一用 Docker 镜像作为制品，前端打包进 Nginx 镜像，后端打包进 JRE 镜像，部署流程统一。

---

## 二、从源码到镜像：CI 中的构建流程

### 2.1 完整流程图

源码到镜像的完整流程如下：

```
源码（GitHub 仓库）
  │
  ▼ docker build（多阶段 Dockerfile）
  │
  ├── 阶段 1: Maven 构建 → jar 包
  │
  └── 阶段 2: JRE + jar → Docker 镜像
  │
  ▼ docker push
  │
  镜像仓库（Harbor）
    registry.mall.local/mall/product:latest
    registry.mall.local/mall/product:v1.0.0
    registry.mall.local/mall/product:a1b2c3d
```

### 2.2 多阶段构建是什么

上一章（01-docker-basics.md）讲过 Dockerfile 的基础。这里讲**多阶段构建（multi-stage build）**——一个 Dockerfile 里有多个 `FROM` 指令，每个 `FROM` 开始一个新阶段。

为什么要多阶段？看一个对比：

**不用多阶段（单阶段）**：

```dockerfile
FROM maven:3.9-eclipse-temurin-21
COPY . .
RUN mvn package -DskipTests
# 最终镜像包含：Maven + JDK + 源码 + jar = ~800MB
CMD ["java", "-jar", "target/app.jar"]
```

问题：最终镜像里带着 Maven、JDK、源码——这些东西只在**构建时**需要，**运行时**根本用不到。镜像臃肿（800MB），安全攻击面大。

**用多阶段**：

```dockerfile
# 阶段 1: 构建（用完就扔）
FROM maven:3.9-eclipse-temurin-21 AS builder
COPY . .
RUN mvn package -DskipTests

# 阶段 2: 运行（只留需要的）
FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /build/target/*.jar app.jar
# 最终镜像只含：JRE + jar = ~200MB
CMD ["java", "-jar", "app.jar"]
```

关键在 `COPY --from=builder`——只从构建阶段**拷贝 jar 包**到运行镜像，Maven、JDK、源码全部留在构建阶段，不进入最终镜像。

用一个比喻：

```
多阶段构建 = 工厂流水线
  阶段 1（builder）= 车间，用机床（Maven+JDK）把原料（源码）加工成零件（jar）
  阶段 2（runtime）= 仓库，只收成品零件（jar），机床和废料留在车间
  COPY --from=builder = 把零件从车间搬到仓库
```

### 2.3 本项目的多阶段 Dockerfile

本项目后端 13 个服务共用一个 `docker/Dockerfile.backend` 模板，通过构建参数（`build-arg`）区分不同服务。简化版如下：

```dockerfile
# ===========================================================================
# 阶段 1: Maven 构建（用完即弃，不进入最终镜像）
# ===========================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

# 服务名与端口，由构建参数注入（默认 mall-product）
ARG SERVICE_NAME=mall-product
ARG SERVICE_PORT=7200

WORKDIR /build

# 先 COPY 所有 pom.xml，利用 Docker layer cache 加速依赖下载
COPY pom.xml ./
COPY mall-common/pom.xml   mall-common/
COPY mall-product/pom.xml  mall-product/
# ... 其余模块的 pom.xml ...

# 预下载依赖（仅 pom.xml 变化时重新执行）
RUN mvn dependency:go-offline -pl mall-common,${SERVICE_NAME} -am -B

# COPY 源码
COPY mall-common/src   mall-common/src
COPY ${SERVICE_NAME}/src  ${SERVICE_NAME}/src

# 编译打包，跳过测试（测试已在 CI 的 test 阶段完成）
RUN mvn package -pl ${SERVICE_NAME} -am -DskipTests -B

# ===========================================================================
# 阶段 2: 运行时镜像（仅含 JRE，体积约 170MB + jar ~30-50MB）
# ===========================================================================
FROM eclipse-temurin:21-jre-alpine

# 创建非 root 用户
RUN addgroup -S mall && adduser -S mall -G mall

WORKDIR /app

# 多阶段构建中 ARG 需重新声明
ARG SERVICE_NAME=mall-product
ARG SERVICE_PORT=7200
COPY --from=builder /build/${SERVICE_NAME}/target/*.jar app.jar

USER mall
EXPOSE ${SERVICE_PORT}

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

几个关键设计点：

| 设计 | 为什么这么做 |
|------|------------|
| `ARG SERVICE_NAME` | 同一个 Dockerfile 模板适用 13 个后端服务，构建时通过 `--build-arg SERVICE_NAME=mall-gateway` 区分 |
| 先 `COPY pom.xml` 再下载依赖 | 利用 Docker 分层缓存——pom.xml 不变时跳过依赖下载层（详见第八节） |
| `mvn package -DskipTests` | 测试已在 CI 前置阶段跑完了，镜像构建不重复跑，加速构建 |
| `eclipse-temurin:21-jre-alpine` | 最终镜像只含 JRE（不含 JDK），Alpine 版本体积小（~170MB），安全漏洞面小 |
| `USER mall`（非 root） | 安全最佳实践——容器以非 root 用户运行，防止容器逃逸后获取 root 权限 |
| `ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS ..."]` | 用 `sh -c` 间接调用以支持 `$JAVA_OPTS` 变量展开。Exec 格式的 ENTRYPOINT 不做变量替换 |

> **为什么测试在镜像构建时跳过**：CI 流水线分两个阶段——先跑 `mvn verify`（测试 + 覆盖率门禁），测试通过后才触发镜像构建。镜像构建时再跑一遍测试是浪费。所以 Dockerfile 里用 `-DskipTests` 跳过。这呼应了上一章讲的「快速失败」——测试在前，构建在后，测试不过不构建。

### 2.4 前端的 Dockerfile

前端也用多阶段构建，但工具链不同——Node.js 编译 + Nginx 运行：

```dockerfile
# 阶段 1: pnpm 构建
FROM node:20-alpine AS builder
RUN corepack enable
WORKDIR /build
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# 阶段 2: Nginx 运行时
FROM nginx:1.27-alpine
COPY --from=builder /build/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

同样的思路：阶段 1 用 Node.js 编译前端代码产出 `dist/` 静态文件，阶段 2 只把 `dist/` 拷贝进 Nginx 镜像。Node.js、源码、node_modules 全部留在构建阶段。

> **`--frozen-lockfile` 是什么**：pnpm 安装依赖时严格按 `pnpm-lock.yaml` 的锁定版本安装，不允许 lockfile 漂移。CI 中必须用这个参数——防止 CI 环境意外升级依赖版本导致「本地能过 CI 不能过」。

---

## 三、镜像仓库是什么

### 3.1 镜像仓库的概念

镜像构建好了，接下来要**存起来**——这就是镜像仓库的作用。

```
镜像仓库 = 存放 Docker 镜像的地方
```

如果你用过 Maven，可以这么类比：

| 概念 | Maven 仓库 | 镜像仓库 |
|------|-----------|---------|
| 存什么 | jar 包 | Docker 镜像 |
| 怎么存 | `mvn deploy` 推到仓库 | `docker push` 推到仓库 |
| 怎么取 | `mvn dependency` 从仓库拉 | `docker pull` 从仓库拉 |
| 版本标识 | GAV 坐标 + version | registry/project/name:tag |
| 公有仓库 | Maven Central | Docker Hub、GHCR |
| 私有仓库 | Nexus、Artifactory | Harbor、Nexus |

### 3.2 公有 vs 私有

镜像仓库分公有和私有两类：

| 仓库 | 类型 | 优点 | 缺点 | 本项目 |
|------|------|------|------|--------|
| Docker Hub | 公有 | 免费、通用、生态最大 | 限额拉取（匿名 100/6h，登录 200/6h）、镜像默认公开 | 不用 |
| GHCR | 公有 | 与 GitHub 无缝集成、权限继承仓库 | 以公开为主，私有有容量限制 | 不用 |
| Harbor | 私有 | 完全可控、内置漏洞扫描、RBAC 权限、镜像保留策略 | 需自己部署运维 | ✅ 用 |

**为什么选 Harbor**：

1. **私有可控**——项目镜像不公开，数据在自建服务器上
2. **内置 Trivy 扫描**——推送时自动扫描漏洞，不用额外搭扫描服务
3. **RBAC 权限**——CI 机器人账号只能推拉，不能删仓库；K8s 拉取账号只能拉，不能推
4. **镜像保留策略**——自动清理旧 tag，防止存储无限膨胀

> **Docker Hub 的限额问题**：Docker Hub 对匿名拉取限制为每 6 小时 100 次。如果 K8s 集群有几十个 Pod 同时拉镜像，很容易触发限额导致部署失败。私有 Harbor 没有这个问题。

---

## 四、Harbor 简介

### 4.1 Harbor 是什么

Harbor 是 CNCF 毕业项目（和 Kubernetes、Prometheus 同级），企业级容器镜像仓库。它不是简单地存镜像，而是一整套镜像治理平台。

Harbor 的核心能力：

| 能力 | 说明 |
|------|------|
| 镜像管理 | 存储、推拉、标签管理 |
| 漏洞扫描 | 内置 Trivy，推送时自动扫描 CVE 漏洞 |
| RBAC 权限 | 项目级 + 角色细粒度（管理员/开发者/访客/机器人） |
| 镜像保留策略 | 自动清理旧 tag（如保留最近 10 个，排除 latest） |
| 审计日志 | 记录谁在什么时候推/拉了什么镜像 |
| 复制同步 | 跨仓库同步镜像（多机房灾备） |

### 4.2 Harbor 的组织结构

Harbor 用三级结构管理镜像：

```
Harbor（registry.mall.local）
  └── 项目（Project）：mall              ← 类比「网盘的文件夹」
        ├── 仓库（Repository）：product   ← 类比「文件夹里的文件」
        │     ├── tag: latest             ← 类比「文件的版本」
        │     ├── tag: v1.0.0
        │     └── tag: a1b2c3d
        ├── 仓库（Repository）：gateway
        │     ├── tag: latest
        │     └── tag: a1b2c3d
        └── 仓库（Repository）：frontend
              └── tag: latest
```

用网盘类比理解：

```
Harbor = 镜像的网盘
  项目（Project）= 网盘的文件夹（如 "mall" 文件夹放所有商城镜像）
    仓库（Repository）= 文件夹里的文件（如 "product" 文件就是一个服务的镜像）
      tag = 文件的版本（latest、v1.0.0、a1b2c3d 就像 v1、v2、v3）
```

你还能设置权限——谁能上传（push）、谁能下载（pull）、谁能删除。这就是 RBAC。

### 4.3 本项目的 Harbor 配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| Harbor 地址 | `registry.mall.local` | 内部 DNS 域名 |
| 项目名 | `mall` | 所有微服务镜像存于此项目下 |
| 访问级别 | 私有 | 需认证才能推拉 |
| 镜像保留策略 | 保留最近 10 个 tag，排除 `latest` | 防止 tag 无限增长 |
| 扫描器 | Trivy（内置） | 推送时自动扫描 |

> Harbor 的部署细节（安装、配置 HTTPS、配置保留策略等）详见设计文档 [harbor-setup.md](../../standards/ci-cd/harbor-setup.md)，本章只讲 CI 怎么用它。

---

## 五、镜像命名规范

### 5.1 完整命名格式

一个完整的镜像名称由四部分组成：

```
registry.mall.local / mall / product : v1.0.0
└──────────────────┘   └──┘   └────┘   └─────┘
     Harbor 地址       项目名   服务名    版本tag
```

逐部分说明：

| 部分 | 值 | 说明 |
|------|-----|------|
| registry | `registry.mall.local` | Harbor 仓库地址（内部 DNS） |
| project | `mall` | Harbor 中的项目名 |
| repository | `product` | 服务名（去掉 `mall-` 前缀） |
| tag | `v1.0.0` | 版本标签 |

### 5.2 为什么服务名去掉 mall- 前缀

本项目服务模块名叫 `mall-product`、`mall-gateway`，但镜像名是 `product`、`gateway`——去掉了 `mall-` 前缀。

原因：Harbor 项目名已经是 `mall` 了，如果镜像名还叫 `mall-product`，完整路径就是 `registry.mall.local/mall/mall-product:latest`——`mall` 重复了，冗余且难看。

去掉前缀后是 `registry.mall.local/mall/product:latest`——简洁明了，镜像名即服务名。

### 5.3 完整示例

本项目 14 个服务的镜像命名：

```
registry.mall.local/mall/gateway:latest
registry.mall.local/mall/auth:latest
registry.mall.local/mall/member:latest
registry.mall.local/mall/product:latest
registry.mall.local/mall/search:latest
registry.mall.local/mall/cart:latest
registry.mall.local/mall/order:latest
registry.mall.local/mall/ware:latest
registry.mall.local/mall/coupon:latest
registry.mall.local/mall/seckill:latest
registry.mall.local/mall/third:latest
registry.mall.local/mall/admin:latest
registry.mall.local/mall/oss:latest
registry.mall.local/mall/frontend:latest
```

> **注意**：`mall-common` 是公共依赖模块（被各服务通过 Maven `-am` 引入），不单独构建镜像。所以是 13 个后端服务镜像 + 1 个前端镜像 = 14 个镜像。

---

## 六、镜像 tag 策略

这是本章的重点。镜像构建出来后，要打什么标签（tag）？为什么不是只打一个 `latest`？

### 6.1 三种 tag 的用途

本项目每个镜像同时打三种 tag：

| tag 类型 | 示例 | 什么时候打 | 用途 |
|---------|------|----------|------|
| latest | `:latest` | 每次 main 合并 / 发版 tag | 指向最新版本，ArgoCD 滚动部署用 |
| commit hash | `:a1b2c3d` | 每次构建 | 精确追溯，回滚用 |
| 语义版本 | `:v1.0.0` | 打 Git tag 发版 | 正式版本标记 |

### 6.2 为什么需要多种 tag

如果只打一个 `latest` 会怎样？看三个场景：

**场景 1：ArgoCD 自动部署（需要 latest）**

```
开发者合并 PR 到 main
  → CI 构建镜像，打 :latest tag，推到 Harbor
  → ArgoCD 监听 :latest tag，发现新镜像
  → ArgoCD 自动滚动更新 K8s 中的 Pod
```

ArgoCD 配置成「监听 `:latest` tag」，只要 Harbor 上 `:latest` 更新了，ArgoCD 就自动拉新镜像部署。这是 GitOps 自动部署的基础。

**场景 2：生产回滚（需要 commit hash）**

```
生产环境出了 Bug，需要回滚到上一个版本
  → 但 :latest 已经被新版本覆盖了，指向有 Bug 的版本
  → 怎么回到上一个版本？
  → 用 commit hash tag！上一次构建的镜像 tag 是 :a1b2c3d
  → 把 K8s Deployment 的镜像改成 ...product:a1b2c3d
  → 回滚完成，精确到某次提交
```

`latest` 是个「移动指针」——它永远指向最新版本，旧版本信息丢了。commit hash 是「固定标记」——每次构建一个，永久不变，可以精确回滚到任意历史版本。

**场景 3：正式发版（需要语义版本）**

```
v1.0.0 正式发版
  → 打 Git tag v1.0.0
  → CI 识别到 tag 推送，构建镜像并打 :v1.0.0 tag
  → :v1.0.0 永远指向这个发版版本
  → 以后说「v1.0.0 有什么问题」，能精确对应到代码和镜像
```

语义版本是人类可读的版本号——`v1.0.0`、`v1.1.0`、`v2.0.0`。commit hash 虽然精确但不可读（`a1b2c3d` 是什么版本？没人知道）。正式发版用语义版本，方便沟通和追溯。

> **一句话总结**：`latest` 给自动部署用（移动指针），`commit hash` 给精确回滚用（固定标记），`语义版本` 给正式发版用（人类可读）。

### 6.3 不同触发场景的 tag 生成

CI 工作流用 `docker/metadata-action@v5` 根据触发事件自动生成 tag：

| 触发场景 | 生成的 tag | 示例（以 product 为例） |
|---------|-----------|----------------------|
| Push main | `latest` + commit hash | `:latest` + `:a1b2c3d` |
| Tag `v*.*.*` | semver + `latest` + commit hash | `:v1.0.0` + `:latest` + `:a1b2c3d` |
| 手动触发 (workflow_dispatch) | commit hash | `:a1b2c3d` |

对应的配置（简化版）：

```yaml
- name: Extract metadata (tags, labels)
  id: meta
  uses: docker/metadata-action@v5
  with:
    images: ${{ secrets.HARBOR_REGISTRY }}/mall/${{ matrix.service }}
    tags: |
      # main 分支：打 latest 标签
      type=raw,value=latest,enable=${{ github.ref == 'refs/heads/main' }}
      # tag 推送（v*.*.*）：打 latest 标签
      type=raw,value=latest,enable=${{ startsWith(github.ref, 'refs/tags/v') }}
      # 所有场景：打 commit hash 短格式标签（精确回滚用）
      type=sha,format=short
      # tag 推送：输出原始版本号（如 v1.0.0）
      type=semver,pattern={{raw}}
```

逐行解释：

- `type=raw,value=latest,enable=...main` —— 当触发分支是 main 时，打 `latest` tag
- `type=raw,value=latest,enable=...tags/v` —— 当触发的是 `v` 开头的 tag 时，也打 `latest` tag（发版时更新 latest）
- `type=sha,format=short` —— 所有场景都打 commit hash 短格式 tag（如 `a1b2c3d`）
- `type=semver,pattern={{raw}}` —— tag 推送时输出原始版本号（如 `v1.0.0`）

> **`enable` 参数**：控制某种 tag 在什么场景下生成。`enable=${{ github.ref == 'refs/heads/main' }}` 表示「只有 main 分支触发时才生成这个 tag」。这样同一段配置能适配多种触发场景。

---

## 七、CI 中的镜像构建：矩阵并行

### 7.1 14 个服务如何并行构建

本项目有 14 个服务镜像要构建。如果串行构建——一个接一个，14 个 × 6 分钟 = 84 分钟，太慢了。

GitHub Actions 的 **matrix 策略**可以并行执行——14 个服务同时构建，互不阻塞：

```yaml
strategy:
  fail-fast: false   # 一个服务失败不影响其他服务
  matrix:
    include:
      # ---- 后端服务（统一使用 Dockerfile.backend）----
      - { service: "gateway",  context: ".", file: "docker/Dockerfile.backend", port: "1000" }
      - { service: "auth",     context: ".", file: "docker/Dockerfile.backend", port: "2000" }
      - { service: "search",   context: ".", file: "docker/Dockerfile.backend", port: "4000" }
      - { service: "cart",     context: ".", file: "docker/Dockerfile.backend", port: "5000" }
      - { service: "seckill",  context: ".", file: "docker/Dockerfile.backend", port: "6000" }
      - { service: "coupon",   context: ".", file: "docker/Dockerfile.backend", port: "7000" }
      - { service: "member",   context: ".", file: "docker/Dockerfile.backend", port: "7100" }
      - { service: "product",  context: ".", file: "docker/Dockerfile.backend", port: "7200" }
      - { service: "oss",      context: ".", file: "docker/Dockerfile.backend", port: "7300" }
      - { service: "ware",     context: ".", file: "docker/Dockerfile.backend", port: "7400" }
      - { service: "order",    context: ".", file: "docker/Dockerfile.backend", port: "7500" }
      - { service: "third",    context: ".", file: "docker/Dockerfile.backend", port: "8000" }
      - { service: "admin",    context: ".", file: "docker/Dockerfile.backend", port: "9000" }
      # ---- 前端 ----
      - { service: "frontend", context: "mall-admin-frontend", file: "docker/Dockerfile.frontend", port: "80" }
```

这段配置的意思是：GitHub Actions 会为 matrix 中的每一行生成一个独立的 Job，14 行就是 14 个 Job，它们**同时执行**。

并行执行示意：

```
        docker-publish（一个 workflow）
        ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
        │   │   │   │   │   │   │   │   │   │   │   │   │   │   │
       gw  auth srch cart seck cpn mem prd oss ware ord 3rd adm  fe
        │   │   │   │   │   │   │   │   │   │   │   │   │   │   │
        └───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘
        ←————————————— 14 个 Job 并行执行 ——————————————→
        总耗时 = 最慢的那个服务（约 6 分钟），不是 14 × 6 = 84 分钟
```

### 7.2 fail-fast: false 是什么意思

默认情况下，GitHub Actions 的 matrix 有一个 `fail-fast: true` 行为——**任何一个 Job 失败，立即取消其他正在运行的 Job**。

这对本项目不合适：

```
fail-fast: true（默认）的问题：
  mall-product 构建失败（比如有个编译错误）
  → GitHub Actions 立即取消 mall-gateway、mall-order 等其他 13 个 Job
  → 你只看到 product 的错误，其他服务有没有问题不知道
  → 修完 product 的错误重新跑，结果 gateway 又挂了
  → 再修再跑，order 又挂了... 反复折腾
```

设 `fail-fast: false` 后：

```
fail-fast: false：
  mall-product 构建失败
  → 其他 13 个 Job 继续跑完
  → 一次 CI 跑完后你能看到所有服务的构建结果
  → 哪些过了、哪些挂了，一目了然
  → 一次性修完所有问题
```

> **一句话总结**：`fail-fast: false` 让所有服务都跑完，一次看到全部结果，不用反复触发 CI。

### 7.3 同一个 Dockerfile 怎么构建 13 个服务

注意 matrix 配置中，13 个后端服务都指向同一个 `docker/Dockerfile.backend` 文件。怎么区分？靠构建参数：

```yaml
build-args: |
  SERVICE_NAME=mall-${{ matrix.service }}
  SERVICE_PORT=${{ matrix.port }}
```

当 matrix 的 `service` 是 `product` 时，构建命令等效于：

```bash
docker build \
  --build-arg SERVICE_NAME=mall-product \
  --build-arg SERVICE_PORT=7200 \
  -f docker/Dockerfile.backend \
  .
```

Dockerfile 中的 `ARG SERVICE_NAME` 接收这个参数，`COPY ${SERVICE_NAME}/src` 就会拷贝 `mall-product/src`，`mvn package -pl ${SERVICE_NAME}` 就会构建 `mall-product` 模块。

一个模板 + 构建参数 = 13 个服务的镜像。这就是模板复用的威力。

---

## 八、镜像缓存优化

### 8.1 问题：每次构建都重新下载依赖

Docker 构建是「分层」的——每条指令生成一层（layer）。如果某层没变，Docker 会复用缓存的层，跳过执行。

问题在于：**CI 环境每次都是全新的容器**，本地缓存不存在。如果不做特殊处理，每次 CI 构建都要从零开始：

```
没有缓存的 CI 构建：
  Layer 1: COPY pom.xml          ← 执行
  Layer 2: RUN mvn go-offline    ← 重新下载 200MB 依赖（3~5 分钟）
  Layer 3: COPY src              ← 执行
  Layer 4: RUN mvn package       ← 重新编译（1 分钟）
  总计：~6 分钟
```

200MB 的 Maven 依赖每次都重新下载，太浪费了。

### 8.2 解决：Docker Buildx 缓存

Docker Buildx 提供了 `cache-from` / `cache-to` 参数，可以把构建缓存**存储到镜像仓库**，跨 CI 次次复用：

```yaml
cache-from: type=registry,ref=registry.mall.local/mall/product:buildcache
cache-to: type=registry,ref=registry.mall.local/mall/product:buildcache,mode=max
```

工作原理：

```
第 1 次构建（冷启动）：
  cache-from 拉 buildcache → 没有（首次）
  正常构建，下载依赖 200MB
  cache-to 把所有层推到 Harbor 的 buildcache 镜像

第 2 次构建（有缓存，pom.xml 未变）：
  cache-from 拉 buildcache → 有！缓存命中
  Layer 1: COPY pom.xml    → pom.xml 没变，命中缓存 ✓
  Layer 2: RUN go-offline  → Layer 1 命中，此层也命中缓存 ✓（跳过下载）
  Layer 3: COPY src        → 源码变了，此层重建
  Layer 4: RUN mvn package → 重新编译
  cache-to 更新 buildcache
  总计：~1 分钟（只编译，不下载依赖）
```

| 参数 | 说明 |
|------|------|
| `cache-from` | 构建前从 Harbor 拉取 `buildcache` 镜像作为缓存源 |
| `cache-to` | 构建后将缓存层推送到 Harbor 的 `buildcache` 镜像 |
| `mode=max` | 缓存所有中间层（包括多阶段构建的 builder 阶段），最大化缓存命中率 |

### 8.3 缓存效果对比

| 场景 | 无缓存 | 有缓存 | 加速比 |
|------|--------|--------|--------|
| 后端依赖未变（仅源码变更） | ~6min（go-offline + 编译） | ~1min（跳过 go-offline，仅编译） | ~6x |
| 前端依赖未变（仅源码变更） | ~3min（pnpm install + build） | ~30s（跳过 install，仅 build） | ~6x |
| 全量变更（pom.xml / package.json 变化） | ~6min / ~3min | ~6min / ~3min（缓存失效） | 1x |

### 8.4 Dockerfile 中的缓存层设计

缓存能命中，前提是 Dockerfile 的**分层顺序**设计合理。看后端 Dockerfile 的分层：

```
Layer 1: COPY pom.xml (14个)          ← 依赖不变时命中缓存
Layer 2: RUN mvn dependency:go-offline ← Layer 1 命中则跳过（省 ~200MB 下载）
Layer 3: COPY src                     ← 源码变更只触发此层及后续重建
Layer 4: RUN mvn package              ← 重新编译（~40s）
```

关键设计：**先 COPY pom.xml，再下载依赖，最后 COPY 源码**。

为什么这个顺序重要？Docker 的缓存规则是——**某一层变了，它后面的所有层都失效**。

```
如果先 COPY 源码再下载依赖（错误顺序）：
  Layer 1: COPY src        ← 源码经常变，此层每次都变
  Layer 2: COPY pom.xml    ← Layer 1 变了，此层也失效
  Layer 3: RUN go-offline  ← Layer 2 失效，此层也失效 → 重新下载 200MB！
  Layer 4: RUN mvn package
  → 每次都要重新下载依赖，缓存形同虚设

正确顺序（先 pom 后 src）：
  Layer 1: COPY pom.xml    ← pom.xml 很少变，此层通常命中缓存
  Layer 2: RUN go-offline  ← Layer 1 命中，此层也命中 → 跳过下载！
  Layer 3: COPY src        ← 源码变了，此层重建（但前面的层不受影响）
  Layer 4: RUN mvn package ← Layer 3 变了，重新编译
  → 只有编译层重建，依赖下载层保持缓存
```

> **一句话总结**：把「变化频率低的」（pom.xml）放前面，「变化频率高的」（源码）放后面，这样源码变更不会让依赖下载层失效。这是 Dockerfile 缓存优化的核心原则。

---

## 九、安全扫描

### 9.1 为什么要扫描镜像

Docker 镜像不是凭空生成的——它基于基础镜像（如 `eclipse-temurin:21-jre-alpine`），基础镜像里又包含操作系统（Alpine Linux）和各种库（如 OpenSSL、glibc）。

这些库可能有 **CVE（Common Vulnerabilities and Exposures，通用漏洞披露）**——已知的安全漏洞。比如 OpenSSL 某个版本有缓冲区溢出漏洞，攻击者可以利用它远程执行代码。

你的应用代码可能很安全，但基础镜像里的库可能有漏洞。**安全扫描就是检查镜像里有没有已知漏洞**。

### 9.2 Trivy 扫描

本项目用 **Trivy** 做镜像扫描。Trivy 是 Aqua Security 开源的漏洞扫描工具，能扫描：

- 操作系统包（Alpine 的 apk、Debian 的 dpkg）
- 语言依赖（Java 的 jar、Node.js 的 npm）
- 配置文件（Dockerfile、K8s YAML）

CI 中的扫描配置：

```yaml
- name: Trivy image scan
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: ${{ secrets.HARBOR_REGISTRY }}/mall/${{ matrix.service }}:${{ steps.meta.outputs.version }}
    format: sarif                           # 输出 SARIF 格式（GitHub Security tab 可读）
    output: trivy-results.sarif
    exit-code: 0                            # 第一版不阻断，仅记录
    ignore-unfixed: true                    # 忽略尚无修复方案的漏洞
    severity: CRITICAL,HIGH                 # 只扫描严重和高危级别

- name: Upload Trivy scan results to GitHub Security
  uses: github/codeql-action/upload-sarif@v3
  if: always()                              # 即使前面步骤失败也上传
  with:
    sarif_file: trivy-results.sarif
```

几个关键参数：

| 参数 | 说明 |
|------|------|
| `image-ref` | 扫描目标——刚推送的镜像 |
| `format: sarif` | SARIF 是标准安全报告格式，GitHub Security tab 能可视化展示 |
| `exit-code: 0` | 扫描发现漏洞也不让 CI 失败（第一版宽松策略） |
| `ignore-unfixed: true` | 忽略尚无官方修复方案的漏洞——这些漏洞修不了，报了也是噪音 |
| `severity: CRITICAL,HIGH` | 只看严重和高危漏洞，忽略低危的 |

### 9.3 扫描策略的分阶段演进

安全扫描不能一上来就「有漏洞就不让发布」——因为存量镜像可能已经有一堆漏洞，直接阻断会导致 CI 频繁失败，开发停滞。

本项目采用分阶段收紧策略：

| 阶段 | 策略 | 配置 | 说明 |
|------|------|------|------|
| 第一版（当前） | 扫描但不阻断 | `exit-code: 0` | 记录漏洞，结果上传 GitHub Security tab 供审计，但不阻止镜像发布。目的是先建立漏洞基线 |
| 基线建立后 | CRITICAL 阻断 | `exit-code: 1`，`severity: CRITICAL` | 仅 CRITICAL 级别漏洞阻断发布。HIGH 级别记录告警 |
| 成熟期 | CRITICAL + HIGH 阻断 | `exit-code: 1`，`severity: CRITICAL,HIGH` | HIGH 及以上级别漏洞阻断发布，强制修复后才能上线 |

**演进原则**：先建立漏洞可见性（记录），再逐步收紧阻断策略。避免一开始就阻断导致 CI 频繁失败，影响开发效率。

用比喻理解：

```
扫描策略演进 = 小区安保升级
  第一版：装监控摄像头（记录不拦截）—— 先看清有哪些问题
  基线后：严重事件报警拦截（CRITICAL 阻断）—— 大问题立刻拦
  成熟期：高危也拦截（HIGH 阻断）—— 标准提高，小问题也不放过
```

> **`exit-code: 0` 和 `continue-on-error: true` 的区别**：两者都能让扫描不阻断 CI。`exit-code: 0` 是 Trivy 自己的参数——扫描完正常退出（退出码 0），CI 认为成功。`continue-on-error: true` 是 GitHub Actions 的参数——步骤失败了也继续。本项目用 `exit-code: 0`，因为这样 Trivy 的退出码是可控的，更精确。

---

## 十、Harbor 凭据管理

### 10.1 凭据不能写在代码里

CI 要推送镜像到 Harbor，需要登录：

```bash
docker login registry.mall.local -u ci-robot -p <password>
```

问题：账号密码不能写在 workflow 文件里——workflow 文件在 Git 仓库里，任何人都能看到代码，密码就泄露了。

### 10.2 GitHub Secrets 加密存储

GitHub 提供 **Secrets** 机制——加密存储敏感信息，CI 运行时通过 `${{ secrets.XXX }}` 引用，且在日志中自动脱敏（显示为 `***`）。

本项目配置的 Secrets：

| Secret 名 | 值 | 敏感性 | 用途 |
|-----------|-----|--------|------|
| `HARBOR_REGISTRY` | `registry.mall.local` | 非敏感 | Harbor 地址（可用 Variables 明文存） |
| `HARBOR_USERNAME` | `ci-robot` | 中等敏感 | CI 机器人账号 |
| `HARBOR_PASSWORD` | `******` | 敏感 | CI 机器人密码（必须用 Secrets 加密） |

配置路径：GitHub 仓库 → `Settings` → `Secrets and variables` → `Actions` → `New repository secret`。

CI 中的登录配置：

```yaml
- name: Login to Harbor
  uses: docker/login-action@v3
  with:
    registry: ${{ secrets.HARBOR_REGISTRY }}
    username: ${{ secrets.HARBOR_USERNAME }}
    password: ${{ secrets.HARBOR_PASSWORD }}
```

### 10.3 为什么用机器人账号而非 admin

Harbor 默认有管理员账号 `admin`。为什么不直接用 admin 推送镜像？

**最小权限原则**——给 CI 的权限应该刚好够用，不多给。

```
用 admin 账号的风险：
  admin 能做一切——推送、删除、改配置、删项目...
  如果 Secrets 泄露，攻击者拿到 admin 权限
  → 能删除所有镜像 → 能修改 Harbor 配置 → 能删整个项目
  → 灾难性后果

用机器人账号 ci-robot 的好处：
  ci-robot 只有 mall 项目的推送+拉取权限
  如果 Secrets 泄露，攻击者只能推拉镜像
  → 不能删项目 → 不能改配置 → 影响面有限
```

| 账号 | 权限 | 用途 |
|------|------|------|
| `admin` | 全部权限（管理员） | 仅 Harbor 运维人员使用，不用于 CI |
| `ci-robot` | `mall` 项目的推送 + 拉取 | CI 推送镜像 |
| `mall-pull`（后续配置） | `mall` 项目的只读拉取 | K8s 拉取镜像 |

> 推送和拉取用不同账号——CI 只推不拉（CI 不需要从 Harbor 拉镜像），K8s 只拉不推（K8s 不应该能推镜像）。各司其职，权限隔离。

---

## 十一、完整流程串联

把前面所有知识点串起来，看一个完整场景。

### 11.1 场景：开发者合并 PR 到 main 分支

```
1. 开发者合并 PR 到 main 分支
   │
2. CI 触发：主编排器（ci.yml）执行
   │
3. ci-test 阶段：mvn verify（单元测试 + 集成测试 + 覆盖率门禁）
   │  测试通过 ✓
   │
4. docker-publish 工作流执行（测试通过后才会触发）
   │
5. matrix 并行构建 14 个镜像
   ├── mall-product:  Maven 构建 → jar → JRE 镜像（~6min，有缓存 ~1min）
   ├── mall-gateway:  Maven 构建 → jar → JRE 镜像
   ├── mall-order:    Maven 构建 → jar → JRE 镜像
   ├── ... 其余 10 个后端服务 ...
   └── mall-frontend: pnpm build → dist → Nginx 镜像（~3min，有缓存 ~30s）
   │
6. docker login registry.mall.local（用 GitHub Secrets 中的 ci-robot 凭据）
   │
7. docker push registry.mall.local/mall/product:latest
   │  docker push registry.mall.local/mall/product:a1b2c3d
   │  （每个镜像推 2~3 个 tag）
   │
8. Trivy 扫描镜像（记录漏洞到 GitHub Security tab，暂不阻断）
   │
9. Harbor 中出现新镜像 tag
   ├── mall/product: latest, a1b2c3d
   ├── mall/gateway: latest, a1b2c3d
   └── ... 14 个服务都更新了 ...
   │
10. （后续）ArgoCD 监听到 :latest tag 更新 → 自动部署到 K8s
```

### 11.2 流程中的知识点对应

| 流程步骤 | 对应本章知识点 |
|---------|--------------|
| 步骤 4 | 制品 = Docker 镜像（第一节） |
| 步骤 5 | 多阶段构建（第二节） + 矩阵并行（第七节） + 缓存优化（第八节） |
| 步骤 6 | Harbor 凭据管理（第十节） |
| 步骤 7 | 镜像命名规范（第五节） + tag 策略（第六节） |
| 步骤 8 | 安全扫描（第九节） |
| 步骤 9 | Harbor 组织结构（第四节） |
| 步骤 10 | latest tag 供 ArgoCD 自动部署（第六节） |

---

## 十二、小结与下一步

### 核心知识点回顾

| 知识点 | 一句话总结 |
|--------|----------|
| CI 制品 | 测试通过后产出的可部署物，本项目统一用 Docker 镜像 |
| 多阶段构建 | 构建阶段（Maven/JDK）与运行阶段（JRE）分离，最终镜像小而安全 |
| 镜像仓库 | 存放 Docker 镜像的地方，本项目用 Harbor（私有、可控、可扫描） |
| 镜像命名 | `registry.mall.local/mall/{服务名}:{tag}`，服务名去掉 mall- 前缀 |
| tag 策略 | latest（自动部署）+ commit hash（精确回滚）+ 语义版本（正式发版） |
| 矩阵并行 | 14 个服务同时构建，fail-fast: false 一次看全部结果 |
| 缓存优化 | pom.xml 不变时跳过依赖下载层，构建从 6min 降到 1min |
| 安全扫描 | Trivy 扫描 CVE 漏洞，分阶段收紧（先记录后阻断） |
| 凭据管理 | 用 GitHub Secrets 加密存储，机器人账号最小权限 |

### 关键配置速查

```yaml
# 矩阵并行构建
strategy:
  fail-fast: false
  matrix:
    include:
      - { service: "product", context: ".", file: "docker/Dockerfile.backend", port: "7200" }
      # ... 14 个服务

# 登录 Harbor（凭据来自 Secrets）
- uses: docker/login-action@v3
  with:
    registry: ${{ secrets.HARBOR_REGISTRY }}
    username: ${{ secrets.HARBOR_USERNAME }}
    password: ${{ secrets.HARBOR_PASSWORD }}

# 构建并推送（带缓存）
- uses: docker/build-push-action@v5
  with:
    context: ${{ matrix.context }}
    file: ${{ matrix.file }}
    push: true
    tags: ${{ steps.meta.outputs.tags }}
    build-args: |
      SERVICE_NAME=mall-${{ matrix.service }}
      SERVICE_PORT=${{ matrix.port }}
    cache-from: type=registry,ref=${{ secrets.HARBOR_REGISTRY }}/mall/${{ matrix.service }}:buildcache
    cache-to: type=registry,ref=${{ secrets.HARBOR_REGISTRY }}/mall/${{ matrix.service }}:buildcache,mode=max

# Trivy 扫描（第一版不阻断）
- uses: aquasecurity/trivy-action@master
  with:
    image-ref: ${{ secrets.HARBOR_REGISTRY }}/mall/${{ matrix.service }}:${{ steps.meta.outputs.version }}
    format: sarif
    exit-code: 0
    ignore-unfixed: true
    severity: CRITICAL,HIGH
```

### 下一步

本章讲了 CI 流水线的后半段——从「测试通过」到「镜像推送到 Harbor」。CI 的工作到此基本完成：代码 → 测试 → 镜像 → 仓库。

但镜像推到仓库后还没完——还需要**部署**。下一章会讲：

- **发布与版本管理**——Git tag 和 GitHub Release 怎么管理版本，发版流程是什么样的
- 从「镜像在仓库里」到「服务在 K8s 上跑起来」，那是 CD（持续部署）的范畴

> 本章涉及的设计文档：[docker-publish.md](../../standards/ci-cd/docker-publish.md)（镜像构建工作流设计）、[harbor-setup.md](../../standards/ci-cd/harbor-setup.md)（Harbor 部署指南）。想看完整 workflow 配置和 Dockerfile 原文，移步设计文档。
