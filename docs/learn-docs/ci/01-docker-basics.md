# 01 · Docker 容器基础

> 目标读者：不熟悉 Docker 的 Java 开发者。读完本章你能：
> 1. 理解镜像、容器、Dockerfile 三个核心概念
> 2. 看懂并写出基本的 Dockerfile
> 3. 理解镜像分层与缓存机制
> 4. **看懂本项目 `docs/standards/ci-cd/docker-publish.md` 中的多阶段 Dockerfile**（后端和前端）

---

## 一、为什么需要 Docker

### 1.1 一个开发者的经典痛点

你一定听过或说过这句话：

> "在我电脑上明明能跑啊！"

场景很熟悉：本地开发得好好的应用，部署到测试服务器就报错——JDK 版本不一样、缺某个系统库、环境变量没配、操作系统不同……排查半天，最后发现是服务器装的是 JDK 17 而你本地是 JDK 21。

### 1.2 传统部署方式的麻烦

传统方式部署一个 Java 应用到一台新服务器，要做的步骤：

```
1. 安装 JDK（还要挑对版本）
2. 配置 JAVA_HOME 等环境变量
3. 安装各种系统依赖
4. 把 jar 包传上去
5. 写启动脚本
6. 配置防火墙端口
```

每换一台服务器，这套流程重来一遍。10 台服务器就是 10 遍。而且人肉操作难免出错——这台机器 JDK 装了 21，那台装了 17，又一地鸡毛。

### 1.3 Docker 的解决方案

Docker 的核心思想很简单：**把应用 + 它运行所需的全部环境（JDK、系统库、配置文件）打包成一个整体**，这个整体叫「镜像」。镜像搬到任何装了 Docker 的机器上，一运行就是「容器」——和你打包时的环境一模一样。

> **类比：Docker 镜像就像「预制板房」**
>
> 传统盖房子：运来砖头水泥，到现场砌墙、布线、铺地板——每块地都要从头来，工人手艺不同，盖出来还有差别。
>
> 预制板房：在工厂里把墙、地板、电线、水管全装好，整体运到工地往地基上一放就能住。不管运到哪，内部结构都一样。
>
> Docker 镜像就是那个「工厂里做好的预制板房」，容器就是「放到地基上住人的那间房」。

这就从根本上解决了「在我电脑上能跑」的问题——因为容器里跑的环境，和你构建镜像时的环境完全一致。

---

## 二、核心概念：镜像、容器、Dockerfile

Docker 有三个核心概念，理解了它们，Docker 就懂了一半。

| 概念 | 是什么 | 类比 |
|------|--------|------|
| **镜像 (Image)** | 只读的模板，包含应用 + 完整运行环境 | 面向对象里的「类」/ 预制板房的「设计图」 |
| **容器 (Container)** | 镜像运行起来的实例，可读写 | 面向对象里的「对象」/ 盖好的板房 |
| **Dockerfile** | 描述如何一步步构建镜像的文本文件 | 板房的「施工说明书」 |

如果你是 Java 开发者，用面向对象来理解最直观：

```
Dockerfile  →  class 定义（描述类长什么样）
镜像 Image  →  class 本身（一个模板）
容器 Container → new 出来的对象（运行中的实例）
```

一个镜像可以同时运行多个容器（就像一个类可以 new 多个对象），它们互相隔离、互不影响。

### 三者的关键关系

```
Dockerfile  ──(docker build)──▶  镜像  ──(docker run)──▶  容器
```

对应到命令：

```bash
# Dockerfile → 镜像（-t 给镜像起名:打标签，最后的 . 是构建上下文路径）
docker build -t my-app:1.0 .

# 镜像 → 容器（-p 把容器端口映射到宿主机）
docker run -p 8080:8080 my-app:1.0
```

记住这条主线：**写 Dockerfile → 构建成镜像 → 运行为容器**。后面所有内容都是围绕这条线展开的。

---

## 三、Dockerfile 基本语法

Dockerfile 就是一个文本文件，里面是一条条指令，每条指令描述「构建镜像时做一件事」。下面逐个讲常用指令。

### 3.1 指令速查表

| 指令 | 作用 | 示例 |
|------|------|------|
| `FROM` | 基础镜像（构建的起点） | `FROM eclipse-temurin:21-jre` |
| `WORKDIR` | 设置工作目录 | `WORKDIR /app` |
| `COPY` | 复制文件到镜像内 | `COPY target/app.jar app.jar` |
| `RUN` | 构建时执行命令 | `RUN apt-get update && apt-get install -y curl` |
| `EXPOSE` | 声明端口（文档性质） | `EXPOSE 8080` |
| `ENV` | 设置环境变量 | `ENV JAVA_OPTS="-Xmx512m"` |
| `USER` | 切换运行用户 | `USER mall` |
| `ENTRYPOINT` | 容器启动命令 | `ENTRYPOINT ["java","-jar","app.jar"]` |
| `CMD` | 默认命令（可被覆盖） | `CMD ["--server.port=8080"]` |
| `ARG` | 构建参数 | `ARG SERVICE_NAME=mall-product` |

### 3.2 重点指令详解

#### FROM —— 构建的起点

```dockerfile
FROM eclipse-temurin:21-jre-alpine
```

`FROM` 是 Dockerfile 的第一条指令（多阶段构建除外），指定**基础镜像**——你的镜像是在它之上叠加的。

基础镜像怎么选？这是 Java 开发者最需要关注的问题：

| 基础镜像 | 内容 | 体积 | 用途 |
|----------|------|------|------|
| `eclipse-temurin:21-jdk` | 含 JDK 21 | ~400MB | 需要编译（如构建阶段） |
| `eclipse-temurin:21-jre` | 只含 JRE 21 | ~250MB | 运行 Java 应用 |
| `eclipse-temurin:21-jre-alpine` | JRE 21 + Alpine Linux | ~170MB | **运行 Java 应用（推荐）** |
| `maven:3.9-eclipse-temurin-21` | Maven + JDK 21 | ~600MB | Maven 构建阶段 |

> **本项目选择**：运行阶段用 `eclipse-temurin:21-jre-alpine`，构建阶段用 `maven:3.9-eclipse-temurin-21`。

#### COPY vs RUN —— 构建时 vs 运行时

这两个最容易混淆，但其实区别很明确：

```dockerfile
# COPY：构建时把文件从宿主机复制进镜像
COPY target/app.jar app.jar      # 把本地 jar 复制到镜像的 /app/app.jar

# RUN：构建时在镜像内执行命令
RUN apt-get update && apt-get install -y curl   # 构建时装软件
```

两者都是**构建时执行**的，区别在于 `COPY` 是「往里搬东西」，`RUN` 是「在里面跑命令」。

> 注意：`RUN` 执行的命令是在构建镜像时跑的，不是容器启动时跑的。`RUN mvn package` 是构建时编译打包，容器启动时 jar 已经打好了。

#### ENTRYPOINT vs CMD —— 容器启动时跑什么

这两个都用来指定容器启动时执行的命令，但行为不同：

```dockerfile
# ENTRYPOINT：固定的启动命令，不容易被覆盖
ENTRYPOINT ["java", "-jar", "app.jar"]

# CMD：默认参数，容易被 docker run 后面的参数覆盖
CMD ["--server.port=8080"]
```

**最经典的搭配**：`ENTRYPOINT` 固定「运行 java -jar app.jar」，`CMD` 给默认参数：

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--server.port=8080"]
# 默认启动时执行: java -jar app.jar --server.port=8080
# docker run myapp --server.port=9090 会覆盖 CMD，执行: java -jar app.jar --server.port=9090
```

> **本项目用法**：后端 Dockerfile 用 `ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]`，这里用 `sh -c` 是为了让 `$JAVA_OPTS` 环境变量能被展开（Exec 格式的 ENTRYPOINT 不做变量替换，必须借助 shell）。

#### ARG —— 构建参数

```dockerfile
ARG SERVICE_NAME=mall-product
```

`ARG` 定义构建参数，可以在 `docker build` 时通过 `--build-arg` 注入，让一个 Dockerfile 适配多个服务：

```bash
# 同一个 Dockerfile，构建不同服务的镜像
docker build --build-arg SERVICE_NAME=mall-product -t mall-product:1.0 .
docker build --build-arg SERVICE_NAME=mall-order  -t mall-order:1.0  .
```

> **本项目正是这么做的**：13 个后端服务共用一个 `Dockerfile.backend`，通过 `--build-arg SERVICE_NAME=mall-{service}` 区分。

> 注意：`ARG` 只在构建阶段有效，容器运行时拿不到这个变量。多阶段构建中，每个阶段要重新声明 `ARG` 才能在该阶段使用。

---

## 四、第一个 Dockerfile

写一个最简单的 Spring Boot 应用 Dockerfile，逐行注释：

```dockerfile
# 基础镜像：只含 JRE 21 的 Alpine 版本（体积小）
# eclipse-temurin 是 Eclipse 基金会维护的 OpenJDK 发行版，官方推荐
FROM eclipse-temurin:21-jre-alpine

# 设置工作目录，后续指令的相对路径都基于此
# 如果 /app 不存在会自动创建
WORKDIR /app

# 把本地打好的 jar 包复制进镜像，重命名为 app.jar
COPY target/my-app.jar app.jar

# 声明容器监听 8080 端口（仅文档性质，真正生效需要 docker run -p 映射）
EXPOSE 8080

# 容器启动时执行的命令
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 4.1 为什么选 `eclipse-temurin:21-jre-alpine`

拆开看这个镜像名的三部分：

| 部分 | 含义 |
|------|------|
| `eclipse-temurin` | Eclipse 基金会维护的 OpenJDK 发行版（原 AdoptOpenJDK），Docker 官方推荐 |
| `21-jre` | Java 21，只含 JRE（运行时），不含 JDK（编译器等） |
| `alpine` | 基于 Alpine Linux，一个专为容器设计的极简 Linux 发行版 |

**为什么用 JRE 不用 JDK？** 运行 Java 应用只需要 JRE，JDK 多了编译器、调试工具等，运行时用不到，白白增加体积和攻击面。

**为什么用 alpine？** Alpine Linux 整个系统才约 5MB，而 Ubuntu 基础镜像约 30MB。对于只需要跑个 jar 的场景，Alpine 足够。最终镜像 `eclipse-temurin:21-jre-alpine` 约 170MB，而 `eclipse-temurin:21-jre`（基于 Ubuntu）约 250MB。

### 4.2 构建并运行的完整流程

```bash
# 1. 先用 Maven 打包（在项目根目录）
mvn package -DskipTests

# 2. 构建镜像（-t 镜像名:标签，. 表示构建上下文是当前目录）
docker build -t my-app:1.0 .

# 3. 运行容器（-p 宿主机端口:容器端口，-d 后台运行）
docker run -p 8080:8080 -d my-app:1.0

# 4. 验证
curl http://localhost:8080
```

---

## 五、镜像分层与缓存

这一节是理解多阶段构建和 CI 缓存优化的关键，请务必理解。

### 5.1 镜像是由「层」组成的

Dockerfile 里**每条指令**（FROM、COPY、RUN 等）都会生成一个**层（layer）**，镜像就是这些层叠加起来的。层可以被复用——不同的镜像如果底层一样，就共享同一份层，不重复存储。

### 5.2 构建缓存机制

Docker 构建镜像时有缓存：如果某一层的**输入没变**，Docker 直接用缓存，不重新执行这条指令。

```
Layer N 的输入 = 这条指令本身 + 上一层的输出
```

只要某一层缓存失效（输入变了），它**以及它之后的所有层**都要重新构建。

### 5.3 COPY 顺序为什么重要

这是关键洞察：**把不常变的放前面，常变的放后面**。

看一个反面教材（不好）和正面教材（好）的对比：

**❌ 不好：先 COPY 源码，再下载依赖**

```dockerfile
FROM maven:3.9-eclipse-temurin-21
COPY . .                          # 源码一改，这层就变
RUN mvn dependency:go-offline     # 上面变了 → 这层缓存失效 → 重新下载所有依赖
RUN mvn package
```

每次改一行代码，都要重新下载全部 Maven 依赖（几百 MB），非常慢。

**✅ 好：先 COPY pom.xml，再下载依赖，最后 COPY 源码**

```dockerfile
FROM maven:3.9-eclipse-temurin-21
COPY pom.xml ./                   # pom 不常变，缓存命中
RUN mvn dependency:go-offline     # 上面命中 → 跳过依赖下载
COPY src/ ./                      # 源码常变，这层重建
RUN mvn package                   # 重新编译，但依赖已经下好了
```

改代码时，只有最后两层重建，依赖下载那层命中缓存——构建从几分钟变成几十秒。

### 5.4 分层结构图示

用 ASCII 图直观展示一个 Maven 构建镜像的分层：

```
┌─────────────────────────────────────────────┐
│ Layer 5: COPY src/            ← 源码常变，经常重建 │
├─────────────────────────────────────────────┤
│ Layer 4: RUN mvn package      ← 依赖上面的层，重建 │
├─────────────────────────────────────────────┤
│ Layer 3: RUN mvn go-offline   ← pom 没变，缓存命中 │
├─────────────────────────────────────────────┤
│ Layer 2: COPY pom.xml         ← pom 不常变，命中  │
├─────────────────────────────────────────────┤
│ Layer 1: FROM maven:3.9...    ← 基础镜像，永远命中 │
└─────────────────────────────────────────────┘
```

> **本项目实践**：后端 Dockerfile 把 14 个 `pom.xml` 单独 COPY 成一层，再执行 `dependency:go-offline`，源码单独一层。这样只要依赖不变，`go-offline` 那层就命中缓存，省掉 ~200MB 的依赖下载。这就是项目文档里说的「先 COPY pom.xml 再下载依赖，最后 COPY 源码能加速构建」的原理。

---

## 六、多阶段构建

这是**本项目 Dockerfile 的核心特性**，也是你需要重点理解的部分。

### 6.1 问题：单阶段构建的臃肿

如果用 Maven 镜像直接构建并运行，Dockerfile 是这样：

```dockerfile
FROM maven:3.9-eclipse-temurin-21
COPY . .
RUN mvn package -DskipTests
ENTRYPOINT ["java", "-jar", "target/app.jar"]
```

问题在于：最终镜像里包含了**全部**东西——Maven 本身、JDK、所有源码、几百 MB 的 Maven 依赖缓存、还有你的 jar。镜像体积轻松 800MB+，但真正运行只需要 JRE + 一个 jar 包。

这带来的问题不只是体积大：
- **安全风险**：镜像里带着编译器和源码，攻击面大
- **拉取慢**：每次部署都要拉 800MB 镜像
- **浪费资源**：运行时根本用不到的东西占着空间

### 6.2 解决方案：多阶段构建

多阶段构建的核心思想：**一个 Dockerfile 里有多个 `FROM`，每个 `FROM` 开始一个新阶段，最终镜像只保留最后一个阶段。**

```dockerfile
# ===== 阶段 1: 构建（用完即弃）=====
FROM maven:3.9-eclipse-temurin-21 AS builder
COPY . .
RUN mvn package -DskipTests

# ===== 阶段 2: 运行（最终镜像）=====
FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /target/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

关键点：

1. `AS builder` —— 给阶段 1 起名叫 `builder`
2. `COPY --from=builder ...` —— 从 `builder` 阶段**只复制 jar 包**到阶段 2
3. 阶段 1 里的 Maven、JDK、源码、依赖缓存**全部丢弃**，不进入最终镜像

最终镜像只有 `eclipse-temurin:21-jre-alpine`（~170MB）+ jar（~30-50MB）≈ 200MB，干净利落。

### 6.3 单阶段 vs 多阶段对比

| 对比项 | 单阶段 | 多阶段 |
|--------|--------|--------|
| 最终镜像体积 | ~800MB | ~200MB |
| 包含 Maven | 是 | 否 |
| 包含 JDK | 是（只需 JRE 却带了 JDK） | 否（只有 JRE） |
| 包含源码 | 是 | 否 |
| 安全攻击面 | 大（编译器、源码都在） | 小 |
| 拉取/部署速度 | 慢 | 快 |

### 6.4 `COPY --from` 详解

`COPY --from=<阶段名>` 是多阶段构建的灵魂指令，它从一个**之前的构建阶段**复制文件，而不是从宿主机复制：

```dockerfile
# 从名为 builder 的阶段复制
COPY --from=builder /build/target/app.jar app.jar

# 也可以用阶段序号（从 0 开始）
COPY --from=0 /build/target/app.jar app.jar

# 甚至可以从外部镜像复制
COPY --from=nginx:1.27-alpine /etc/nginx/nginx.conf /nginx.conf
```

> **本项目后端 Dockerfile 中的用法**：
> ```dockerfile
> COPY --from=builder /build/${SERVICE_NAME}/target/*.jar app.jar
> ```
> 从构建阶段复制指定服务的 jar 包。注意 `ARG` 在多阶段构建中需要**在每个阶段重新声明**才能使用，所以项目里阶段 2 又写了一遍 `ARG SERVICE_NAME=mall-product`。

---

## 七、Docker Compose 简介

### 7.1 它解决什么问题

一个微服务项目要跑起来，往往需要一堆中间件：MySQL、Redis、Nacos、RocketMQ…… 用 `docker run` 一个个启动太麻烦，而且它们之间有启动顺序和依赖关系。

Docker Compose 用**一个 YAML 文件**定义多个容器及其配置，一条命令全部启动或停止。

### 7.2 本项目的用法

本项目用 `docker-compose.yml` 编排开发环境的中间件，并用 **profiles 分组**按需启用：

```yaml
# docker-compose.yml（简化示例）
name: mall-dev

services:
  # ---------- Nacos 注册/配置中心 ----------
  nacos:
    image: nacos/nacos-server:v2.4.3
    container_name: mall-nacos
    profiles: ["core"]           # 属于 core 组
    environment:
      - MODE=standalone
    ports:
      - "8848:8848"
      - "9848:9848"              # gRPC 端口
    volumes:
      - nacos-data:/home/nacos/data
    restart: unless-stopped

  # ---------- MySQL 数据库 ----------
  mysql:
    image: mysql:8.4
    container_name: mall-mysql
    profiles: ["core"]
    environment:
      - MYSQL_ROOT_PASSWORD=root123
      - TZ=Asia/Shanghai         # 设置时区
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    restart: unless-stopped

volumes:
  nacos-data:
  mysql-data:
```

### 7.3 profiles 分组的好处

本项目把中间件分成 5 组，按开发阶段渐进启用，不用一次启动全部（省内存）：

```bash
# 只启动核心中间件（Nacos + MySQL + Redis + Seata + Sentinel）
docker compose --profile core up -d

# 加上消息队列
docker compose --profile core --profile mq up -d

# 全部启动
docker compose --profile core --profile mq --profile search --profile storage --profile monitor up -d

# 全部停止
docker compose down
```

| profiles 组 | 包含的中间件 |
|-------------|-------------|
| `core` | Nacos、MySQL、Redis、Seata、Sentinel |
| `mq` | RocketMQ NameServer + Broker + Dashboard |
| `search` | OpenSearch + Dashboards |
| `storage` | MinIO |
| `monitor` | Prometheus + Grafana |

> 这就是项目根目录 `docker-compose.yml` 的设计思路——开发时按需启动，不用一上来就吃掉 8GB 内存。

---

## 八、容器化 Java 应用注意事项

针对 Java 开发者的实践要点，这些是容器化部署时容易踩的坑：

| 要点 | 说明 | 本项目做法 |
|------|------|-----------|
| **基础镜像选择** | 运行用 JRE 足够（不需要 JDK）；Alpine 体积小但偶有兼容问题，需测试 | `eclipse-temurin:21-jre-alpine` |
| **JVM 内存** | 容器内 JVM 需用 `-XX:MaxRAMPercentage` 自适应容器内存限制，**不要用 `-Xmx` 硬编码** | `-XX:MaxRAMPercentage=75.0` |
| **时区** | 容器默认 UTC 时区，日志时间会差 8 小时，需设置 `TZ=Asia/Shanghai` | docker-compose 中 MySQL 等设了 `TZ` |
| **日志输出** | 容器日志应输出到 stdout/stderr，**不要写文件**（日志收集靠 stdout） | Spring Boot 默认输出到 stdout，符合要求 |
| **非 root 运行** | 安全最佳实践，创建专用用户运行应用，防止容器逃逸获 root | 创建 `mall` 用户，`USER mall` 切换 |
| **.dockerignore** | 排除 `target/`、`.git/`、`node_modules/` 等不需要的文件，减小构建上下文 | 前端需排除 `node_modules/`、`dist/` |

### 8.1 为什么不用 `-Xmx` 硬编码

传统部署时 `-Xmx512m` 没问题，因为你清楚服务器内存。但在容器/K8s 环境中，容器内存限制可能随时调整。如果硬编码 `-Xmx512m`，把容器内存限制调到 2GB 也没用——JVM 堆还是 512MB。

用 `-XX:MaxRAMPercentage=75.0` 让 JVM 自动按容器内存限制的 75% 设堆上限，容器给多少内存 JVM 就用多少，灵活且不会 OOM。

### 8.2 为什么日志要输出到 stdout

容器里跑的进程，日志如果写文件，容器删了日志就没了。而且 K8s、Loki 等日志系统都是从容器的 stdout/stderr 收集日志的。所以容器化应用的标准做法是：**日志直接输出到标准输出**，由外部系统负责收集和持久化。Spring Boot 默认就是这样，无需额外配置。

---

## 九、常用命令速查

### 9.1 镜像相关

| 命令 | 作用 |
|------|------|
| `docker build -t name:tag .` | 构建镜像 |
| `docker images` | 查看本地镜像 |
| `docker rmi <image>` | 删除镜像 |
| `docker pull <registry>/<image>:<tag>` | 拉取镜像 |
| `docker push <registry>/<image>:<tag>` | 推送镜像 |
| `docker login <registry>` | 登录镜像仓库 |

### 9.2 容器相关

| 命令 | 作用 |
|------|------|
| `docker run -p 8080:8080 name:tag` | 运行容器 |
| `docker run -p 8080:8080 -d name:tag` | 后台运行容器 |
| `docker ps` | 查看运行中的容器 |
| `docker ps -a` | 查看所有容器（含已停止） |
| `docker logs <container>` | 查看容器日志 |
| `docker logs -f <container>` | 实时跟踪容器日志 |
| `docker stop <container>` | 停止容器 |
| `docker rm <container>` | 删除容器 |
| `docker exec -it <container> sh` | 进入容器（交互式 shell） |

### 9.3 Compose 相关

| 命令 | 作用 |
|------|------|
| `docker compose up -d` | 启动 compose（后台） |
| `docker compose down` | 停止并删除 compose 容器 |
| `docker compose ps` | 查看 compose 容器状态 |
| `docker compose logs -f <service>` | 查看某服务日志 |

### 9.4 构建参数（本项目 CI 用到）

```bash
# 构建时传入 ARG 参数（本项目构建不同服务镜像的方式）
docker build \
  --build-arg SERVICE_NAME=mall-product \
  --build-arg SERVICE_PORT=7200 \
  -t mall-product:1.0 \
  -f docker/Dockerfile.backend \
  .
```

---

## 十、小结与下一步

### 10.1 本章核心知识点

1. **三个概念**：Dockerfile（施工说明书）→ 镜像（预制板房）→ 容器（住人的板房）
2. **Dockerfile 指令**：`FROM` 选基础镜像、`COPY` 搬文件、`RUN` 跑命令、`ENTRYPOINT`/`CMD` 定启动命令
3. **镜像分层与缓存**：每条指令一层，输入不变就命中缓存——所以 COPY 顺序很关键（不常变的放前面）
4. **多阶段构建**：多个 `FROM`，`COPY --from` 跨阶段复制产物，最终镜像只保留最后阶段（小而干净）
5. **Java 容器化要点**：JRE 而非 JDK、`MaxRAMPercentage` 而非 `-Xmx`、非 root 运行、日志走 stdout

### 10.2 回到本项目的 Dockerfile

现在你应该能看懂 `docs/standards/ci-cd/docker-publish.md` 里的后端 Dockerfile 了。回顾一下它的结构：

```
阶段 1 (builder): maven:3.9-eclipse-temurin-21
  ├─ COPY 14 个 pom.xml        ← 缓存层：依赖不变就命中
  ├─ RUN mvn go-offline        ← 预下载依赖
  ├─ COPY 源码                  ← 源码层：变化只从这里重建
  └─ RUN mvn package           ← 编译打包

阶段 2 (运行): eclipse-temurin:21-jre-alpine
  ├─ 创建非 root 用户 mall
  ├─ COPY --from=builder jar   ← 只拿产物，丢弃构建环境
  ├─ USER mall                  ← 非 root 运行
  ├─ ENV JAVA_OPTS              ← MaxRAMPercentage 自适应内存
  └─ ENTRYPOINT sh -c java ...  ← sh -c 展开 $JAVA_OPTS
```

前端 Dockerfile 同理：阶段 1 用 `node:20-alpine` 跑 `pnpm build`，阶段 2 用 `nginx:1.27-alpine` 跑静态文件，`COPY --from=builder` 把编译产物搬到 Nginx 目录。

### 10.3 下一章预告

本章讲了 Docker 的基础概念和 Dockerfile 语法。下一章「CI 核心概念」将讲解：

- **CI/CD 是什么**：持续集成 / 持续交付 / 持续部署的区别
- **GitHub Actions 怎么用**：本项目 CI 用的就是它，会讲 workflow、job、step 的概念
- **CI 中如何构建 Docker 镜像**：把本章学的 `docker build` 放到 CI 流水线里自动化执行
- **镜像仓库 Harbor**：构建好的镜像推到哪里、怎么管理

本章的 Dockerfile 知识是 CI 的基础——CI 流水线本质上是把「手动 docker build + push」自动化了。理解了镜像分层和缓存，你才能理解 CI 为什么要做缓存优化；理解了多阶段构建，你才能看懂 CI 里 14 个服务的镜像构建逻辑。
