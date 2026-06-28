# 06 · 读懂本项目 CI 方案

> 前置阅读：[01-docker-basics.md](./01-docker-basics.md) ~ [05-ci-release-workflow.md](./05-ci-release-workflow.md)
>
> 本章是 CI 学习系列的收尾篇。前 5 章讲了 Docker、CI 概念、测试、镜像、发布的通用知识，本章把这些知识落到本项目的具体设计上，逐个解读 `docs/standards/ci-cd/` 下的 6 篇设计文档。
>
> 看完后你应该能回答：这套 CI 方案整体怎么搭的？每个工作流在干什么？为什么这么设计？哪些地方做了分阶段妥协？接下来怎么落地实现？

---

## 一、本项目 CI 方案全貌

my-mall 是一个微服务商城项目：14 个 Java 后端模块 + 1 个 Vue 前端（`mall-admin-frontend`）。CI 方案采用**可复用工作流组合**，由一个编排器按事件类型调用若干子工作流。先用一张总览图建立全局认知：

```
开发者提交代码
    │
    ▼
┌─────────────────────────────────────────┐
│  编排器 ci.yml                            │
│  ├── detect-changes（检测变更范围）        │
│  ├── backend-ci（后端 CI，并行）           │
│  ├── frontend-ci（前端 CI，并行）          │
│  ├── docker-publish（镜像发布，main/tag）  │
│  └── release（GitHub Release，仅 tag）     │
└─────────────────────────────────────────┘
```

这个结构来自总体设计文档 [overview.md](../../standards/ci-cd/overview.md)。本章会按下面的顺序逐个解读 6 篇设计文档，每篇都关联前 5 章学过的概念：

| 解读顺序 | 设计文档 | 对应学习章节 | 核心问题 |
|---------|---------|------------|---------|
| 1 | [overview.md](../../standards/ci-cd/overview.md) | 02-CI 核心概念 | 整体架构怎么设计的？ |
| 2 | [backend-ci.md](../../standards/ci-cd/backend-ci.md) | 03-测试策略 | 后端 CI 怎么跑测试？ |
| 3 | [frontend-ci.md](../../standards/ci-cd/frontend-ci.md) | 03-测试策略 | 前端 CI 怎么跑？ |
| 4 | [docker-publish.md](../../standards/ci-cd/docker-publish.md) | 01-Docker + 04-镜像构建 | 镜像怎么构建推送？ |
| 5 | [harbor-setup.md](../../standards/ci-cd/harbor-setup.md) | 04-镜像构建 | Harbor 怎么部署？ |
| 6 | [release-and-protection.md](../../standards/ci-cd/release-and-protection.md) | 05-发布管理 | 版本怎么发布？分支怎么保护？ |

> 提醒：设计文档回答「项目要怎么做」，偏规范与落地；本章回答「为什么这么做、和前面学的概念怎么对应」，偏解读。两者配合看效果最好。

---

## 二、解读 overview.md：总体架构

[overview.md](../../standards/ci-cd/overview.md) 是整个 CI/CD 体系的总纲，定义了工作流组织方案、CI/CD 边界、编排器结构与关键决策。

### 2.1 方案选型：为什么选可复用工作流组合

回顾 02 章讲的工作流组织三种方案：A（分阶段单工作流）、B（可复用工作流组合）、C（矩阵并行构建）。overview.md 第二章对比后**选了方案 B**。

选 B 的关键理由（见 overview.md 第二章结论表）：

- **模块化**：每个子工作流职责单一，一个文件管一件事（构建测试 / 镜像发布 / Release），出问题好定位
- **天然支持分阶段实现**：子工作流相互独立，可以按 `backend-ci → frontend-ci → docker-publish → release` 顺序逐步落地，每阶段独立可用
- **CI/CD 边界清晰**：CI 子工作流只管质量，发布子工作流只管交付，关注点分离
- **业界标准**：GitHub 官方推荐的可复用工作流模式

方案 C（矩阵并行构建每个 Maven 模块）为什么不选？overview.md 第二章点出了核心矛盾：Maven 多模块有依赖关系，`mall-common` 被所有模块依赖，矩阵并行得**先 install mall-common 再并行构建业务模块**，复杂度高，对学习项目属过度优化。矩阵构建后来用在了镜像发布阶段（14 个镜像并行），那里才是它的用武之地。

本项目的文件组织和职责如下（来自 overview.md 第四章）：

| 文件 | 类型 | 职责 |
|------|------|------|
| `ci.yml` | 编排器 | 检测变更范围，按事件/分支编排调用子工作流 |
| `backend-ci.yml` | 子工作流 | `mvn verify` + jacoco 覆盖率 |
| `frontend-ci.yml` | 子工作流 | pnpm lint + type-check + build + test |
| `docker-publish.yml` | 子工作流 | 多阶段 Dockerfile 构建 → 推送 Harbor |
| `release.yml` | 子工作流 | 生成 Changelog → 创建 GitHub Release |
| `actions/setup-java/` | composite action | JDK 21 + Maven 缓存（被 backend-ci / docker-publish 复用） |
| `actions/setup-frontend/` | composite action | pnpm + Node 20 + 缓存（被 frontend-ci / docker-publish 复用） |

注意 composite action 的存在——它解决的是**步骤级复用**。`setup-java` 被 backend-ci 和 docker-publish 都用到，抽出来避免 JDK 版本、缓存配置在多处重复定义。这正是 02 章讲的「DRY 原则在工作流层面的体现」。

### 2.2 CI/CD 边界

回顾 02 章讲的 CI/CD 关系——CI 管质量，CD 管部署。overview.md 第三章把这条边界划得很清楚：

- **CI 阶段** = backend-ci + frontend-ci（只管代码质量门禁）
- **发布阶段** = docker-publish + release（只管交付物：镜像 + Release）
- **CD 阶段** = ArgoCD（后续独立文档 `argocd-cd.md` 设计，本次不含）

这个边界的设计价值在于**通过镜像 tag 解耦**：CI 不关心怎么部署，CD 不关心怎么构建。CI 产出「测试通过的代码」，docker-publish 产出「Harbor 中的镜像 tag」，ArgoCD 监听这个 tag 自动部署 K8s。三者用「镜像 tag」这一契约衔接，任何一环都可以独立演进甚至替换——比如以后把 ArgoCD 换成别的 CD 工具，CI 完全不用动。

### 2.3 编排器：ci.yml

编排器是整个 CI 的入口，完整 YAML 在 [overview.md 第六章](../../standards/ci-cd/overview.md)。它本身不执行构建逻辑，只负责「检测变更 + 编排调用」。这里有 5 个关键设计点，每个都能关联到前面章节的概念：

**1. detect-changes（paths 过滤）——关联 02 章「paths 过滤」**

编排器第一个 job 用 `dorny/paths-filter@v3` 检测变更范围：`mall-*/**` 和 `pom.xml` 算后端变更，`mall-admin-frontend/**` 算前端变更。后续 job 根据这个输出决定是否执行。这就是 02 章讲的 paths 过滤的价值——改一行前端 CSS 不会触发 14 模块的 Maven 构建。`docs/**` 和 `*.md` 变更更彻底，在 `push` 触发器用 `paths-ignore` 直接排除，连 detect-changes job 都不运行。

**2. backend-ci 和 frontend-ci 并行——关联 02 章「串行 vs 并行」**

这两个 job 在编排器中**没有 `needs` 依赖**，GitHub Actions 会并行调度。后端 `mvn verify`（14 模块）耗时数分钟，前端 build 耗时 1-2 分钟，并行能把总耗时压成两者较长者，而不是两者之和。这是 02 章讲的「能并行就别串行」的典型应用。

**3. docker-publish 的 `if: always()`——关联 02 章，解释为什么需要 always()**

```yaml
docker-publish:
  needs: [backend-ci, frontend-ci]
  if: always() && (github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/'))
```

这是编排器里最微妙的一处设计。`docker-publish` 依赖 backend-ci 和 frontend-ci，但这两个 job 可能因 paths 过滤被**跳过**（skipped）。GitHub Actions 默认行为是：`needs` 里任一 job 被 skipped，依赖它的 job 也会被跳过。想象 main 分支只改了后端代码，frontend-ci 被跳过，于是 docker-publish 也错误地不执行了——这显然不对。

`if: always()` 破除了这个默认行为，确保只要满足分支条件就执行发布。overview.md 第六章也提醒了 `always()` 的风险：它会让 job 在上游 CI **失败**时也执行。所以 docker-publish 子工作流内部还需要校验上游结果，避免「测试挂了还发镜像」。

**4. release 仅 tag 触发——关联 05 章「Git tag 触发 Release」**

```yaml
release:
  needs: [docker-publish]
  if: startsWith(github.ref, 'refs/tags/')
```

release job 用 `startsWith(github.ref, 'refs/tags/')` 限定只在 tag 推送时执行。这和 05 章讲的一致——正式发版靠打 tag，PR 和普通 push 不创建 Release。结合触发事件矩阵（overview.md 第五章）：PR/push 到 main/develop 只跑 CI；push 到 main 额外发镜像（持续交付）；tag 推送才跑完整流水线（CI → 镜像 → Release）。

**5. concurrency 取消旧运行——关联 02 章「快速反馈」**

```yaml
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true
```

同一分支短时间内多次推送时，`cancel-in-progress: true` 会取消旧的 CI 运行，节省 runner 资源。这就是 02 章讲的「快速反馈」——别让排队积压的旧运行浪费资源，永远优先最新一次提交的结果。

---

## 三、解读 backend-ci.md：后端 CI

[backend-ci.md](../../standards/ci-cd/backend-ci.md) 是后端构建测试工作流的详细设计。它是一个 `workflow_call` 可调用工作流，由编排器调用，自己不直接监听事件。

### 3.1 核心命令

后端 CI 的核心就一条命令：

```bash
mvn verify -B -ntp -pl !mall-admin-frontend
```

回顾 03 章讲的 surefire + failsafe + jacoco，这条命令一次串联了三层测试和覆盖率门禁：

- `verify` 生命周期阶段会依次触发：`test` 阶段（surefire 跑单元/切片测试 `*Test.java`）→ `integration-test`/`verify` 阶段（failsafe 跑集成测试 `*IT.java`）→ jacoco 的 `check` goal（覆盖率门禁校验）
- `-B` 批处理模式禁用交互提示（CI 环境没人交互）
- `-ntp` 关闭下载进度条减少日志噪音
- `-pl !mall-admin-frontend` 排除前端目录——前端是 Vite 项目不是 Maven 模块，防御性排除避免 reactor 误入

backend-ci.md 第三章特别强调：**为什么用 `verify` 而非 `test`**。`mvn test` 只触发 surefire，不跑 failsafe 的集成测试，也不触发 jacoco 门禁。只有 `mvn verify` 才能完整覆盖「测试 + 门禁」。

### 3.2 覆盖率门禁

回顾 03 章讲的覆盖率门禁——jacoco 的 `check` goal 绑定 `verify` 阶段，低于阈值则构建失败。backend-ci.md 第四章给出了本项目的分阶段提升策略：

| 阶段 | lines/branches 覆盖率 | 触发时机 |
|------|----------------------|---------|
| 当前 | 60% | 立即启用（历史代码未补测试，阈值宽松） |
| 过渡 | 70% | 补齐 common / gateway 测试后提升 |
| 目标 | 80% | 生产级标准，测试体系成熟后收紧 |

为什么不一步到位 80%？03 章讲过——历史无测试代码一次性拉高阈值会导致构建频繁失败，反而逼团队绕过门禁。分阶段提升是务实的妥协。

门禁还配了排除规则：启动类、DTO/VO/Entity 纯模型类、配置类、代码生成器产物不计入覆盖率，避免纯模型类稀释指标。这个排除清单和测试规范 `testing-specification.md` §9 对齐。

### 3.3 前置条件

这是 backend-ci.md 第九章的重点提醒：**backend-ci.yml 能跑的前提是父 POM 要补三个插件**（对应 03 章讲过的 P0 行动清单）：

| # | 前置事项 | 说明 |
|---|---------|------|
| 1 | 父 POM 补 `maven-failsafe-plugin` | 当前缺失，导致 `*IT.java` 集成测试在 `mvn verify` 时不执行 |
| 2 | 父 POM 补 `jacoco-maven-plugin` | 当前无覆盖率统计和门禁（prepare-agent + report + check） |
| 3 | surefire `argLine` 改为 `@{argLine}` | 配合 jacoco 注入 agent |

第 3 点尤其值得注意——surefire 的 argLine 要从 `-XX:+EnableDynamicAgentLoading -Xshare:off` 改成：

```xml
<argLine>@{argLine} -XX:+EnableDynamicAgentLoading -Xshare:off</argLine>
```

回顾 03 章讲的 `@{argLine}` 机制：`@` 前缀表示**延迟属性引用**，Maven 在插件配置解析阶段不展开，等 surefire 实际 fork JVM 时才读取 `argLine` 属性——此时 jacoco `prepare-agent` 已把 agent 参数写进去了。如果用 `${argLine}`（立即引用），解析阶段属性还是空的，jacoco agent 就注入失败。这是 03 章强调的「jacoco 与 surefire 协作的坑」，不补这步 CI 跑出来的覆盖率全是 0。

### 3.4 PR 覆盖率评论

backend-ci.md 第七章用 `Madrapps/jacoco-report@v1.2` 在 PR 上展示覆盖率：

```yaml
- name: PR coverage comment
  if: github.event_name == 'pull_request'
  uses: Madrapps/jacoco-report@v1.2
  with:
    paths: '**/target/site/jacoco/jacoco.xml'
    token: ${{ secrets.GITHUB_TOKEN }}
    min-coverage-overall: 60
    min-coverage-changed-files: 60
```

回顾 03 章讲的 PR 评论——它的价值是让评审者在合并前**直接在 PR 上看到覆盖率变化**，不用手动下载 artifact。`min-coverage-overall` 和 `min-coverage-changed-files` 都设 60，和父 POM 门禁阈值一致；后续随测试规范分阶段提升到 80 时，这里同步收紧。

另外注意报告上传步骤用了 `if: always()`——即使测试失败也上传 jacoco 报告，方便从 artifact 下载下来排查失败用例的覆盖情况。这是 03 章讲的「失败也要留下证据」。

---

## 四、解读 frontend-ci.md：前端 CI

[frontend-ci.md](../../standards/ci-cd/frontend-ci.md) 定义 `mall-admin-frontend` 的 CI。技术栈是 Vue 3 + TypeScript + Vite 6 + pnpm。

### 4.1 步骤顺序

回顾 03 章讲的快速失败原则——耗时短、问题暴露快的检查先跑，耗时长、资源大的后跑。frontend-ci.md 第三章把步骤排成：

```
lint → type-check → test → build
```

| 顺序 | 步骤 | 耗时 | 排在前面的理由 |
|------|------|------|---------------|
| 1 | `pnpm install` | 30~60s | 后续所有步骤的前提 |
| 2 | `pnpm lint` | 10~20s | 最快，纯静态分析无需编译 |
| 3 | `pnpm type-check` | 20~40s | 较快，类型错误属低级问题应尽早暴露 |
| 4 | `pnpm test` | 10~30s | 中等，逻辑正确性验证 |
| 5 | `pnpm build` | 40~90s | 最慢，资源消耗最大，放最后 |

收益很直接：如果提交有个 ESLint 错误，10 秒内就能失败反馈；要是放到 build 之后才发现，就白白等了 1~2 分钟构建时间。在 PR 频繁触发的场景下，累计节省的 CI 资源相当可观。

### 4.2 vitest 留位策略

frontend-ci.md 第六章点出一个现实：前端**当前零测试**——没装 vitest、没 `vitest.config.ts`、没测试文件。但 CI 里还是预先写了 `pnpm test` 步骤，只是加了 `continue-on-error: true`：

```yaml
- name: Test
  run: pnpm test
  continue-on-error: true   # 待测试体系建立后移除此行
```

这个「留位」策略关联 03 章的前端测试策略——CI 结构一步到位，测试体系建立后只需移除 `continue-on-error` 即可启用，不用动工作流骨架。当前 `pnpm test` 会因缺 vitest 依赖而失败，但 `continue-on-error: true` 保证这个失败不阻断 lint / type-check / build。CI 日志里会显示黄色感叹号，提醒团队「测试步骤存在但还没真正生效」。

启用条件（frontend-ci.md §6.3）：装好 vitest + @vue/test-utils + happy-dom、建好 `vitest.config.ts`、`package.json` 的 `test` script 定义为 `vitest run`、写出至少一批核心测试后，去掉 `continue-on-error` 即可。

### 4.3 scripts 调整

frontend-ci.md 第十章提醒：前端 `package.json` 的 scripts 需要调整才能配合 CI。关键两项：

- **`lint` 要去掉 `--fix`**：当前 `lint` 带了 `--fix` 会自动改文件，CI 里不应自动修复，只检查并报错。修复交给开发者本地用 `lint:fix` 执行。这是「CI 只验证不改代码」的原则。
- **新增 `type-check` 独立脚本**：当前类型检查内嵌在 `build` 的 `vue-tsc -b` 里，CI 要单独跑类型检查，所以拆出 `vue-tsc --noEmit`。同时 `build` 精简为 `vite build`，避免和 type-check 重复检查。

调整后本地用 `pnpm lint:fix` 一键修复，CI 用 `pnpm lint` 严格检查，两者各司其职。

---

## 五、解读 docker-publish.md：镜像构建

[docker-publish.md](../../standards/ci-cd/docker-publish.md) 是镜像构建与 Harbor 集成的设计，覆盖后端/前端 Dockerfile、矩阵并行、tag 策略、安全扫描。这里能用到 01 章和 04 章的大量概念。

### 5.1 多阶段 Dockerfile

回顾 01 章讲的多阶段构建——把构建环境和运行环境分离，最终镜像只含运行所需内容。本项目后端用一个统一模板 `docker/Dockerfile.backend`，所有 13 个服务共用，通过 `--build-arg` 注入服务名和端口。两个阶段：

- **阶段 1（builder）**：`FROM maven:3.9-eclipse-temurin-21 AS builder`，全量 Maven + JDK 镜像，编译打包出 jar
- **阶段 2（运行时）**：`FROM eclipse-temurin:21-jre-alpine`，只含 JRE，运行 jar

重点解读几个设计点（都能关联 01 章）：

**pom.xml 缓存层——关联 01 章「镜像分层与缓存」**

Dockerfile 里先 `COPY` 全部 14 个 `pom.xml`，再 `RUN mvn dependency:go-offline` 预下载依赖，最后才 `COPY` 源码。回顾 01 章讲的「把变化频率低的放前面」——pom.xml 变化频率远低于源码，只要依赖不变，go-offline 这层命中缓存，跳过约 200MB 依赖下载。源码变更只触发从 `COPY src` 开始的层重建。docker-publish.md 第九章给了加速比：依赖未变时构建从 ~6min 降到 ~1min。

**非 root 运行——关联 01 章「容器化 Java 注意事项」**

阶段 2 里 `RUN addgroup -S mall && adduser -S mall -G mall` 创建系统用户，`USER mall` 切换。容器以 `mall` 身份运行，符合安全最佳实践——万一容器逃逸，攻击者也拿不到 root 权限。

**JAVA_OPTS 用 MaxRAMPercentage——关联 01 章**

```dockerfile
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

`MaxRAMPercentage=75.0` 让 JVM 堆自动适配容器内存限制（CGroup awareness），不用写死 `-Xmx`。回顾 01 章讲的「JDK 11+ 能感知容器内存限制」——这是容器化 Java 的正确姿势。`JAVA_OPTS` 用环境变量注入，K8s 部署时可以通过 env 覆盖，不用重建镜像。注意 `ENTRYPOINT` 用 `sh -c` 间接调用——exec 格式的 `ENTRYPOINT` 不做变量替换，必须借助 shell 才能展开 `$JAVA_OPTS`。

前端 Dockerfile（`docker/Dockerfile.frontend`）也是多阶段：`node:20-alpine` 阶段 `pnpm build` 出静态产物，`nginx:1.27-alpine` 阶段跑 Nginx。这里有个 01 章提过的坑——前端构建上下文要配 `.dockerignore` 排除 `node_modules/`，否则 `COPY . .` 会把本地几百 MB 的 `node_modules` 复制进构建容器。

### 5.2 矩阵并行构建

回顾 04 章讲的矩阵并行——用 `matrix` 策略让多个 job 并行跑。这里终于用上矩阵了（overview.md 没在后端 CI 用矩阵，但在镜像发布用了）：

```yaml
strategy:
  fail-fast: false
  matrix:
    include:
      - { service: "gateway",  context: ".", file: "docker/Dockerfile.backend", port: "1000" }
      - { service: "auth",     context: ".", file: "docker/Dockerfile.backend", port: "2000" }
      # ... 共 13 后端 + 1 前端
```

14 个服务并行构建，`fail-fast: false` 保证单个服务构建失败不影响其他服务——否则一个服务的镜像构建挂了，其余 13 个都被取消，排查起来很痛苦。这正是 04 章讲的「矩阵并行的 fail-fast 取舍」。

### 5.3 tag 策略

回顾 04 章讲的 tag 策略——用 `docker/metadata-action@v5` 根据触发事件自动生成 tag。本项目三种 tag（docker-publish.md 第六章）：

| 触发场景 | 生成的 tag | 用途 |
|---------|-----------|------|
| Push main | `latest` + commit hash | latest 供 ArgoCD 滚动部署，hash 供精确回滚 |
| Tag `v*.*.*` | semver + `latest` + commit hash | semver 是正式版本标记 |
| 手动触发 | commit hash | 调试用 |

`latest` 给 ArgoCD 追踪，commit hash 给精确回滚（出问题一键切到任意历史版本，不受 latest 滚动影响），semver 给正式发版。三者各司其职，这是 04 章讲的多 tag 策略的典型落地。

### 5.4 安全扫描

回顾 04 章讲的 Trivy 扫描——扫镜像 CVE 漏洞。docker-publish.md 第八章的策略是**第一版宽松**：

```yaml
- name: Trivy image scan
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: ${{ secrets.HARBOR_REGISTRY }}/mall/${{ matrix.service }}:${{ steps.meta.outputs.version }}
    format: sarif
    output: trivy-results.sarif
    exit-code: 0           # 第一版不阻断，仅记录
    ignore-unfixed: true   # 忽略尚无修复方案的漏洞
    severity: CRITICAL,HIGH
```

`exit-code: 0` 意味着扫出漏洞也不阻断发布，结果上传到 GitHub Security tab 供审计。设计意图是**先建立漏洞基线**——一开始就阻断会导致 CI 频繁失败，影响开发效率。待基线稳定后收紧为 CRITICAL 阻断，再进一步到 CRITICAL + HIGH 阻断。这个「先可见再收紧」的演进思路，和后端覆盖率门禁的分阶段提升是同一种工程智慧。

---

## 六、解读 harbor-setup.md：Harbor 部署

[harbor-setup.md](../../standards/ci-cd/harbor-setup.md) 是 Harbor 私有镜像仓库的部署指南。docker-publish 依赖 Harbor，所以这步要先行。

### 6.1 为什么独立部署

harbor-setup.md 第二章明确：Harbor **不纳入项目 `docker-compose.yml`**。原因是 Harbor 安装包自带 9 个组件（core / nginx / db / redis / jobservice / trivy / chartmuseum / registryctl / exporter 等），配置复杂、有独立的 `harbor.yml` 和 `prepare` 预处理流程。混进项目 compose 会破坏中间件编排的单一职责——项目 compose 管的是 MySQL/Redis/RocketMQ 这些业务中间件，Harbor 是 CI 基础设施，两者物理隔离、各自独立启停更清晰。

### 6.2 关键配置

部署步骤这里不展开（harbor-setup.md 第三章有完整流程），重点解读几个和 CI 相关的关键配置：

**创建 mall 项目**——Harbor 里的项目名固定为 `mall`，所有微服务镜像存于此。镜像命名格式 `registry.mall.local/mall/{service}:{tag}`，服务名去掉 `mall-` 前缀避免 `mall/mall-product` 的冗余。

**创建 ci-robot 机器人账号——关联 04 章「最小权限原则」**

回顾 04 章讲的镜像仓库认证——CI 推送要用专用账号，不用 admin。harbor-setup.md §4.2 创建了 `ci-robot` 机器人账号，只给推送 + 读取权限，无管理权限，过期时间 1 年。这样即使凭据泄露，影响面也只限于「能推镜像」，不能改 Harbor 配置或删项目。K8s 拉取镜像另配只读账号，和推送账号分离。

**镜像保留策略——避免膨胀**

harbor-setup.md §4.3 配了保留策略：保留最近 10 个 tag，排除 `latest`（永不清理）。开发环境每次 main 合并和 tag 都产镜像，不清理会快速堆积，这个策略自动兜底。

**Trivy 内置扫描**

Harbor 内置 Trivy，配成「推送时自动扫描」（Scan on push）。这样镜像进 Harbor 时扫一次，CI 流水线里又扫一次（见 5.4），双重覆盖。当前都是记录不阻断，待基线建立后收紧。

### 6.3 开发环境用 HTTP

harbor-setup.md 第六章提醒：开发环境 Harbor 用 HTTP（无 HTTPS），Docker 客户端默认拒绝和 HTTP 仓库交互，需配 `insecure-registries`：

```json
{
  "insecure-registries": ["registry.mall.local:80"]
}
```

回顾 01 章讲的 Docker 基础——这是开发环境的妥协，简化本地开发避免证书管理开销。**生产环境必须 HTTPS + CA 签发证书，禁止配 insecure-registries**，否则镜像传输无加密有被篡改风险。harbor-setup.md 第八章专门列了开发环境 vs 生产环境的配置差异表，HTTPS、证书、镜像签名、高可用这些生产项一项都不能省。

---

## 七、解读 release-and-protection.md：发布与保护

[release-and-protection.md](../../standards/ci-cd/release-and-protection.md) 定义 GitHub Release 自动发布、Secrets/Variables 管理和分支保护规则。

### 7.1 Changelog 自动生成

回顾 05 章讲的 Changelog——从 commit history 自动提取变更记录。本项目用 `mikepenz/release-changelog-builder-action@v5`，配置文件 `.github/changelog-configuration.json` 把 commit type 映射成分组标题：

| Commit Type | 分组标题 |
|-------------|---------|
| `feat` | 🚀 Features |
| `fix` | 🐛 Fixes |
| `perf` | ⚡ Performance |
| `refactor` | ♻️ Refactor |
| `docs` | 📚 Documentation |
| `ci` | 🔧 CI/CD |
| `chore`/`style`/`test`/`revert` | 📦 Other Changes |

这个映射和项目的 [git-workflow.md](../../standards/git-workflow.md) 定义的 commit type 一一对应。设计价值在于——**无需手动维护 CHANGELOG.md**，只要开发者严格遵循 conventional commits 规范，Release 的变更说明就自动生成且分组清晰。这也意味着 commit message 写得不规范，Changelog 分组就会出错，反过来倒逼规范执行。

### 7.2 prerelease 自动判断

回顾 05 章讲的预发布版本——alpha/beta/rc。本项目用一个表达式自动判断：

```yaml
prerelease: ${{ contains(github.ref_name, '-alpha') || contains(github.ref_name, '-beta') || contains(github.ref_name, '-rc') }}
```

`github.ref_name` 是触发的 tag 名（如 `v1.0.0-alpha`），`contains()` 检查是否含预发布后缀。纯 semver（`v1.0.0`）不匹配任何后缀返回 false，标记为正式发布。设计优势是无需额外 step 或脚本，单一表达式完成判断，和 SemVer 规范严格对齐。

### 7.3 分支保护

回顾 05 章讲的分支保护——强制 PR + CI 通过才能合并。本项目 main 和 develop 保护力度不同：

| 规则 | main（生产级强制） | develop（略宽松） |
|------|------------------|------------------|
| 必须 PR 合并 | ✅ | ✅ |
| 至少 1 个 approval | ✅ | ✅ |
| CI 必须通过 | ✅ | ✅ |
| 必须通过的 check | `backend-ci` / `frontend-ci` | `backend-ci` / `frontend-ci` |
| Code Owner 审批 | ✅ | — |
| 合并前必须 up to date | ✅ | ❌ |
| 禁止 bypass（管理员也不能跳过） | ✅ | ❌ |

关键差异在两个地方：

- **main 的 `Do not allow bypassing` 必须开**——这意味着即使仓库管理员也无法跳过 CI 检查直接合并，最高安全级别，确保所有代码变更都经过完整 CI 验证。develop 不开，留出紧急修复场景的快速通道。
- **develop 不强制 up to date**——develop 合并频率高，强制 up to date 意味着每次合并前都要 rebase 最新再重跑 CI，多人并行开发时频繁 rebase 烦扰、CI 反复重跑耗资源。develop 允许 CI 通过但非最新时合并，由合并后的集成测试兜底。main 不妥协，因为生产分支必须保证集成一致。

### 7.4 Secrets vs Variables

回顾 05 章讲的 Secrets 和 Variables 的区别——Secrets 存敏感信息（日志脱敏），Variables 存非敏感配置（日志明文可见）。本项目的划分：

- `HARBOR_REGISTRY`（仓库地址 `registry.mall.local`）用 **Variable**——这是公开访问地址，不属于敏感信息。放 Variable 的好处是构建日志里能看到完整镜像地址，便于调试。
- `HARBOR_USERNAME` / `HARBOR_PASSWORD`（机器人账号凭据）用 **Secret**——敏感信息，日志自动脱敏显示 `***`。

这个划分体现了 05 章讲的原则：**敏感与非敏感分离，职责明确**。别图省事全塞 Secrets——那样调试时连仓库地址都被脱敏，反而麻烦。

---

## 八、分阶段实施路径

overview.md 第八章给了分阶段实施路径。设计文档虽然完整，但实现可以分 4 个阶段，每个阶段是**独立可用的闭环**：

| 阶段 | 目标 | 交付物 | 依赖前序 |
|------|------|--------|---------|
| 1 | 后端 CI 最小闭环 | ci.yml + backend-ci.yml + setup-java action + 分支保护 | 无 |
| 2 | 前端 CI 补齐 | frontend-ci.yml + setup-frontend action + 编排器加 frontend-ci job | 阶段 1 |
| 3 | 镜像发布闭环 | Harbor 部署 + docker-publish.yml + Dockerfile + 编排器加 docker-publish job | 阶段 1-2 |
| 4 | Release 发布管理 | release.yml + Secrets 配置 + 编排器加 release job + 分支保护完善 | 阶段 3 |

强调一点：**每个阶段独立可用**。实现完阶段 1，PR 合并就有后端测试门禁了，不必等全部做完。这是方案 B（可复用工作流组合）的核心优势——子工作流相互独立，先上一个就有价值，后续逐步补齐。

---

## 九、知识串联图

最后用一张图把 6 篇设计文档和 5 章学习笔记的知识点串联起来，作为收尾：

```
学习笔记              设计文档                    核心知识点
─────────             ─────────                   ─────────
01-Docker 基础   ──→  docker-publish.md          多阶段构建、分层缓存、非 root、MaxRAMPercentage
02-CI 概念      ──→  overview.md                方案选型(方案B)、CI/CD 边界、编排器、paths 过滤、concurrency
03-测试策略     ──→  backend-ci.md              surefire/failsafe/jacoco、覆盖率门禁(60→80)、argLine 坑
                 ──→  frontend-ci.md            lint→type-check→test→build、vitest 留位、scripts 调整
04-镜像构建     ──→  docker-publish.md          矩阵并行(14服务)、tag 策略(latest+hash+semver)、Trivy
                 ──→  harbor-setup.md           Harbor 独立部署、ci-robot 最小权限、保留策略、HTTP/HTTPS
05-发布管理     ──→  release-and-protection.md  Changelog 自动生成、prerelease 判断、分支保护、Secrets/Variables
```

前 5 章是「为什么」，6 篇设计文档是「怎么做」，本章是两者之间的桥。走到这里，你应该能完全看懂这套 CI 方案——不只是知道每个文件在干什么，还能说清每个决策背后的工程考量。

---

## 十、下一步：实现

设计文档已完整，接下来是落地实现。建议按分阶段实施路径的顺序推进，几个注意事项：

1. **先补 P0 前置条件再上 backend-ci**。父 POM 的三个插件（failsafe、jacoco、argLine 改 `@{argLine}`）不补，backend-ci 跑起来集成测试不执行、覆盖率全是 0。这是 backend-ci.md 第九章明确的前置依赖。

2. **Harbor 需先部署再配 docker-publish**。docker-publish.yml 依赖 `HARBOR_REGISTRY` / `HARBOR_USERNAME` / `HARBOR_PASSWORD` 三个凭证，这些要 Harbor 部署完、创建好 ci-robot 账号后才能配。开发环境别忘配 `insecure-registries` 信任 HTTP Harbor。

3. **分支保护规则在 GitHub UI 配置**。这部分不是 workflow 文件，是在仓库 Settings → Branches 里手动配。建议先配 main（生产级强制），再配 develop（略宽松）。

4. **前端 scripts 调整和 vitest 留位**。上 frontend-ci 前先把 `package.json` 的 `lint` 去掉 `--fix`、新增 `type-check`，否则 CI 会自动改代码或类型检查跑不了。vitest 步骤先 `continue-on-error: true` 留位，测试体系建立后启用。

5. **Trivy 和覆盖率门禁先宽松后收紧**。两者都遵循「先建立基线，再逐步收紧」——Trivy 第一版 `exit-code: 0` 记录不阻断，覆盖率门禁当前 60% 目标 80%。别一上来就收紧，否则 CI 频繁失败反而逼团队绕过。

按这个顺序走，每个阶段都能独立交付价值，不必等全部做完才有 CI。
