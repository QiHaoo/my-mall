# CI/CD 总体设计

> 本文档是 my-mall 项目 CI/CD 体系的总体设计文档，定义工作流组织方案、CI/CD 边界划分、编排器结构与关键设计决策。
> 各子工作流的详细设计见本文档第九章索引的系列文档。
>
> **文档定位**：本文档是 `docs/standards/testing-specification.md` 第十章「CI 集成」骨架的**生产级完整实现**。

---

## 一、背景与现状

### 1.1 项目规模

my-mall 是基于 Spring Boot 3.4 + Spring Cloud 的微服务商城项目，构建产物规模：

| 类型 | 数量 | 模块 | 构建工具 |
|------|------|------|---------|
| 后端 Maven 模块 | 14 | mall-gateway / mall-auth / mall-member / mall-product / mall-search / mall-cart / mall-order / mall-ware / mall-coupon / mall-seckill / mall-third / mall-admin / mall-oss / mall-common | Maven（多模块 reactor） |
| 前端项目 | 1 | mall-admin-frontend | pnpm + Vite |

> **模块依赖关系**：`mall-common` 被所有业务模块依赖，是 reactor 构建的根依赖；其余业务模块之间通过 OpenFeign 远程调用解耦，无 Maven 编译期依赖（除 mall-common 外）。这一依赖关系直接影响 CI 并行策略（见第二章方案 C 分析）。

### 1.2 当前 CI/CD 现状

| 维度 | 现状 | 缺口 |
|------|------|------|
| 后端 CI | ❌ 无 | PR/Push 不跑 `mvn verify`，测试只能本地手跑，PR 合并不卡测试通过 |
| 前端 CI | ❌ 无 | 无 lint / type-check / build 自动校验 |
| 镜像发布 | ❌ 无 | 无 Dockerfile，无 Harbor 集成，无镜像版本管理 |
| Release 发布 | ❌ 无 | 无 GitHub Release，无版本号管理 |
| 文档部署 | ✅ 已有 | `.github/workflows/mkdocs.yml` 部署 MkDocs 文档站 |
| CD 部署 | ❌ 无 | 无 ArgoCD，无 K8s 部署自动化 |

### 1.3 已有规划

- `docs/standards/testing-specification.md` 第十章「CI 集成（GitHub Actions）」已规划 CI 骨架（单文件 `Backend CI` workflow，跑 `mvn verify` + jacoco 上传），但**仅是骨架，未落地实现**，且未覆盖前端、镜像发布、Release。
- 技术栈已确定：**GitHub Actions（CI）+ Harbor（镜像仓库）+ ArgoCD（CD）**，见 `AGENTS.md` 技术选型表。

### 1.4 本文目标

基于上述现状，设计一套**生产级、可分阶段落地**的 CI/CD 总体方案，明确：

1. CI 工作流的组织方式（单文件 vs 可复用组合 vs 矩阵并行）
2. CI 与 CD 的边界划分（本次设计范围 vs 后续范围）
3. 编排器的触发策略与 job 编排逻辑
4. 关键设计决策的记录与依据
5. 分阶段实施路径

---

## 二、方案选型

针对 CI 工作流的组织方式，对比三种方案：

### 方案 A：分阶段单工作流（Phased Single Workflow）

**思路**：一个 `.github/workflows/ci.yml` 文件承载所有逻辑，通过 `if` 条件按事件类型 / 分支触发不同阶段。

| 维度 | 评价 |
|------|------|
| 优点 | 单文件一目了然，初学者易于理解全貌 |
| 缺点 | 文件臃肿（后端 + 前端 + 镜像 + Release 全塞一个文件，预计 300+ 行）；job 间耦合重；增删一个阶段需改动核心文件，扩展性差；无法被其他仓库复用 |
| 适用 | 极小项目（1-2 个 job） |

### 方案 B：可复用工作流组合（Reusable Workflow Composition）✅ 选用

**思路**：按 job 职责拆分为多个子工作流文件，通过 `workflow_call` 触发器串联；公共步骤（如 JDK 安装、pnpm 安装）抽取为 composite action 复用。编排器（`ci.yml`）只负责按事件调用子工作流。

| 维度 | 评价 |
|------|------|
| 优点 | 模块化（每个文件职责单一）；可复用（子工作流可被其他仓库 `uses` 引用）；天然支持分阶段实现（先落地 backend-ci，再补 frontend-ci）；关注点分离；CI 与 CD 边界清晰（CI 子工作流只管构建测试，发布子工作流只管镜像/Release）；业界标准做法（GitHub 官方推荐） |
| 缺点 | 文件多（5 个 workflow + 2 个 composite action） |
| 适用 | 中大型项目、多模块项目、需分阶段演进的项目 |

### 方案 C：矩阵并行构建（Matrix Parallel Build）

**思路**：用 `matrix` 策略并行构建每个 Maven 模块，每个模块一个 job。

| 维度 | 评价 |
|------|------|
| 优点 | 理论上构建最快（14 模块并行） |
| 缺点 | Maven 多模块有依赖关系——`mall-common` 被所有模块依赖，矩阵并行需**先 `install` mall-common 到本地仓库，再并行构建业务模块**，复杂度高；模块间存在传递依赖时需精确拓扑排序；GitHub Actions 免费额度下 14 个并行 job 占用 runner 数量大；对学习项目属**过度优化** |
| 适用 | 超大型单体仓库（monorepo）、模块完全独立无依赖 |

### 结论

**选用方案 B（可复用工作流组合）**。

| 选型理由 | 说明 |
|---------|------|
| 业界标准 | GitHub 官方推荐的可复用工作流模式，文档生态成熟 |
| 天然支持分阶段实现 | 子工作流相互独立，可按 `backend-ci → frontend-ci → docker-publish → release` 顺序逐步落地，每阶段独立可用 |
| 关注点分离 | 每个子工作流只负责一件事（构建测试 / 镜像发布 / Release），便于维护和排查 |
| CI/CD 边界清晰 | CI 子工作流（backend-ci / frontend-ci）只管代码质量，发布子工作流（docker-publish / release）只管交付物，ArgoCD 只管部署 |
| 复用性 | 子工作流可被其他仓库引用，composite action 可在多个 workflow 间共享 |

---

## 三、CI/CD 边界划分

### 3.1 全流程总览

```
                          ┌─────────────────────────────────────────────────────┐
                          │                    开发者提交代码                       │
                          └──────────────────────────┬──────────────────────────┘
                                                     │
                                                     ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                            CI 阶段（本次设计范围）                                    │
│                                                                                    │
│   ┌─────────────────────────┐        ┌─────────────────────────┐                   │
│   │      backend-ci         │        │      frontend-ci        │                   │
│   │  mvn verify + jacoco    │        │  pnpm lint + type-check │                   │
│   │  （单元/切片/集成测试）   │        │  + build + vitest       │                   │
│   └────────────┬────────────┘        └────────────┬────────────┘                   │
│                │                                  │                                │
│                └──────────────┬───────────────────┘                                │
│                               ▼                                                    │
│                    CI 通过（质量门禁）                                              │
└───────────────────────────────┼────────────────────────────────────────────────────┘
                                │
                                │ 仅 main 分支 / tag 触发
                                ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                          发布阶段（本次设计范围）                                     │
│                                                                                    │
│   ┌─────────────────────────────────┐    ┌──────────────────────────────────┐     │
│   │        docker-publish           │    │             release              │     │
│   │  多阶段 Dockerfile 构建镜像       │    │   GitHub Release 发布            │     │
│   │  → 推送 Harbor                   │    │   （仅 tag 触发）                 │     │
│   │  （main / tag 触发）             │    │                                  │     │
│   └────────────────┬────────────────┘    └──────────────────────────────────┘     │
│                    │                                                              │
│                    ▼ 镜像推送到 Harbor                                             │
└────────────────────┼─────────────────────────────────────────────────────────────┘
                     │
                     │ Harbor 中存在新镜像 tag
                     ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                       CD 阶段（后续设计，本次不含）                                   │
│                                                                                    │
│   ArgoCD 监听镜像 tag / Git 仓库 → 滚动部署 K8s                                      │
│   （Application 资源 + GitOps 模式，详见后续 argocd-cd.md 文档）                      │
└────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 本次设计范围（明确边界）

| 阶段 | 是否本次范围 | 交付物 |
|------|------------|--------|
| **CI 全链路** | ✅ 本次范围 | backend-ci.yml + frontend-ci.yml + 编排器 ci.yml + composite actions |
| **Harbor 搭建** | ✅ 本次范围 | Harbor 私有镜像仓库部署（docker-compose 或独立部署） |
| **镜像发布** | ✅ 本次范围 | docker-publish.yml + 多阶段 Dockerfile |
| **Release 发布** | ✅ 本次范围 | release.yml + GitHub Release + 分支保护 |
| **CD 部署（ArgoCD）** | ❌ 本次不含 | ArgoCD 监听镜像 tag / Git 仓库 → 滚动部署 K8s，后续独立文档 `argocd-cd.md` 设计 |

> **边界说明**：本次设计 = **CI 全链路 + Harbor 搭建 + 镜像发布 + Release 发布**，不含 ArgoCD CD 细节。CI 产出的镜像推送到 Harbor 后，ArgoCD 如何监听并部署 K8s 属于 CD 阶段，由后续文档承接。

---

## 四、工作流文件组织

### 4.1 文件结构

```
.github/
├── workflows/
│   ├── ci.yml                  # 编排器：按事件调用子工作流（入口）
│   ├── backend-ci.yml          # 后端构建 + 测试 + 覆盖率（workflow_call）
│   ├── frontend-ci.yml         # 前端 lint + type-check + build + 测试（workflow_call）
│   ├── docker-publish.yml      # 镜像构建 + 推送 Harbor（workflow_call）
│   ├── release.yml             # GitHub Release 发布（workflow_call）
│   └── mkdocs.yml              # 已有：MkDocs 文档部署
└── actions/
    ├── setup-java/             # composite action：JDK 21 + Maven 依赖缓存
    │   └── action.yml
    └── setup-frontend/         # composite action：pnpm + node_modules 缓存
        └── action.yml
```

### 4.2 文件职责说明

| 文件 | 类型 | 职责 | 触发方式 |
|------|------|------|---------|
| `ci.yml` | 编排器 | 检测变更范围，按事件 / 分支编排调用子工作流 | `pull_request` / `push` / `workflow_dispatch` |
| `backend-ci.yml` | 子工作流 | `mvn verify`（surefire + failsafe）+ jacoco 覆盖率报告上传 | `workflow_call`（被 ci.yml 调用） |
| `frontend-ci.yml` | 子工作流 | `pnpm lint` + `pnpm type-check` + `pnpm build` + `pnpm test`（vitest） | `workflow_call`（被 ci.yml 调用） |
| `docker-publish.yml` | 子工作流 | 多阶段 Dockerfile 构建镜像 → 登录 Harbor → 推送镜像 tag | `workflow_call`（被 ci.yml 调用） |
| `release.yml` | 子工作流 | 读取 CHANGELOG / 生成 GitHub Release（含镜像 tag 引用） | `workflow_call`（被 ci.yml 调用） |
| `mkdocs.yml` | 独立 workflow | 构建 MkDocs 文档站 → 部署到 GitHub Pages | `push` to main（`docs/**` 路径） |
| `actions/setup-java/action.yml` | composite action | 安装 JDK 21 + 配置 Maven 仓库缓存（`~/.m2/repository`） | 被 backend-ci / docker-publish 引用 |
| `actions/setup-frontend/action.yml` | composite action | 安装 pnpm + Node.js + 缓存 `node_modules`（pnpm store） | 被 frontend-ci / docker-publish 引用 |

> **composite action 的价值**：`setup-java` 被 `backend-ci.yml` 和 `docker-publish.yml`（Docker 构建前的 Maven 打包阶段）共同使用；`setup-frontend` 同理。抽取 composite action 避免 JDK / pnpm 安装逻辑在多个文件中重复。

---

## 五、编排器触发与编排逻辑

### 5.1 触发事件矩阵

| 事件 | 触发条件 | 调用的子工作流 | 说明 |
|------|---------|--------------|------|
| `pull_request` → main / develop | 自动 | backend-ci + frontend-ci（按 paths 过滤） | PR 检查，只跑 CI 不发布 |
| `push` → develop | 自动 | backend-ci + frontend-ci（按 paths 过滤） | develop 集成检查，只跑 CI 不发布 |
| `push` → main | 自动 | backend-ci + frontend-ci + docker-publish | main 合并即发布镜像（持续交付） |
| `push` tag `v*.*.*` | 自动 | backend-ci + frontend-ci + docker-publish + release | 正式版本发布，打 Release |
| `workflow_dispatch` | 手动 | 可选择执行哪条链路（输入参数控制） | 应急触发 / 调试 |

### 5.2 paths 过滤策略

为避免无关变更浪费 CI 资源，编排器通过 `dorny/paths-filter@v3` 检测变更范围，按需触发：

| 变更路径 | 触发的子工作流 | 不触发的子工作流 |
|---------|--------------|----------------|
| `mall-*/**`、`pom.xml`（后端代码） | backend-ci | frontend-ci |
| `mall-admin-frontend/**`（前端代码） | frontend-ci | backend-ci |
| `docs/**`、`*.md` | 均不触发 | backend-ci + frontend-ci |
| `.github/workflows/**` | 两者都触发（workflow 自身变更需全量验证） | — |

> **设计意图**：前端改文档不触发后端 14 模块 `mvn verify`（耗时数分钟），后端改 Java 不触发前端 build。这是 paths 过滤的核心价值。

---

## 六、编排器完整结构

### 6.1 ci.yml 完整 YAML

```yaml
name: CI

# ============================================================
# 编排器：按事件类型 / 分支调用子工作流
# 不直接执行构建逻辑，只负责「检测变更 + 编排调用」
# ============================================================

on:
  # PR 检查：仅跑 CI，不发布
  pull_request:
    branches: [main, develop]
  # develop 集成检查：仅跑 CI，不发布
  push:
    branches: [develop]
    paths-ignore:
      - 'docs/**'
      - '**/*.md'
  # main 合并：跑 CI + 发布镜像（持续交付）
  # tag 推送：跑 CI + 发布镜像 + Release（正式发布）
  # 注：main 和 tag 共用 push 触发，通过 job 的 if 条件区分
  workflow_dispatch:
    inputs:
      run_backend:
        description: '执行后端 CI'
        required: false
        default: 'true'
        type: boolean
      run_frontend:
        description: '执行前端 CI'
        required: false
        default: 'true'
        type: boolean
      run_publish:
        description: '执行镜像发布'
        required: false
        default: 'false'
        type: boolean

permissions:
  contents: read

concurrency:
  # 同一分支的 CI 取消旧的运行，避免并发浪费
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  # ----------------------------------------------------------
  # Job 1: 检测变更范围
  # 用 dorny/paths-filter 检测 backend / frontend 是否变更
  # 后续 job 根据此 job 的输出决定是否执行
  # ----------------------------------------------------------
  detect-changes:
    name: Detect Changes
    runs-on: ubuntu-latest
    outputs:
      backend: ${{ steps.filter.outputs.backend }}
      frontend: ${{ steps.filter.outputs.frontend }}
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Detect changed paths
        uses: dorny/paths-filter@v3
        id: filter
        with:
          filters: |
            backend:
              - 'mall-*/**'
              - 'pom.xml'
              - '.github/workflows/backend-ci.yml'
              - '.github/workflows/ci.yml'
              - '.github/actions/setup-java/**'
            frontend:
              - 'mall-admin-frontend/**'
              - '.github/workflows/frontend-ci.yml'
              - '.github/workflows/ci.yml'
              - '.github/actions/setup-frontend/**'

  # ----------------------------------------------------------
  # Job 2: 后端 CI
  # needs detect-changes，仅当 backend 变更时执行
  # 与 frontend-ci 无 needs 依赖，两者并行
  # ----------------------------------------------------------
  backend-ci:
    name: Backend CI
    needs: detect-changes
    if: ${{ needs.detect-changes.outputs.backend == 'true' }}
    uses: ./.github/workflows/backend-ci.yml
    secrets: inherit

  # ----------------------------------------------------------
  # Job 3: 前端 CI
  # needs detect-changes，仅当 frontend 变更时执行
  # 与 backend-ci 无 needs 依赖，两者并行
  # ----------------------------------------------------------
  frontend-ci:
    name: Frontend CI
    needs: detect-changes
    if: ${{ needs.detect-changes.outputs.frontend == 'true' }}
    uses: ./.github/workflows/frontend-ci.yml
    secrets: inherit

  # ----------------------------------------------------------
  # Job 4: 镜像构建与发布
  # needs [backend-ci, frontend-ci]
  # 仅 main 分支或 tag 触发
  # if: always() —— 不因 CI 被 paths 跳过的分支而失败
  # ----------------------------------------------------------
  docker-publish:
    name: Docker Publish
    needs: [backend-ci, frontend-ci]
    if: always() && (github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/'))
    uses: ./.github/workflows/docker-publish.yml
    secrets: inherit

  # ----------------------------------------------------------
  # Job 5: GitHub Release 发布
  # needs docker-publish
  # 仅 tag 触发
  # ----------------------------------------------------------
  release:
    name: GitHub Release
    needs: [docker-publish]
    if: startsWith(github.ref, 'refs/tags/')
    uses: ./.github/workflows/release.yml
    secrets: inherit
```

### 6.2 关键设计决策说明

> 以下 5 条决策是编排器的核心设计，每条均有明确的技术理由，变更前需评估影响。

#### 决策 1：后端前端并行执行

`backend-ci` 和 `frontend-ci` 两个 job 在编排器中**无 `needs` 依赖**，GitHub Actions 会并行调度。后端 `mvn verify`（14 模块）耗时数分钟，前端 `pnpm build` 耗时 1-2 分钟，并行可将总耗时压缩为两者较长者，而非两者之和。

#### 决策 2：paths 过滤，按需触发

通过 `dorny/paths-filter@v3` 检测变更范围，前端变更不触发后端 CI，后端变更不触发前端 CI。避免「改一行前端 CSS 触发 14 模块 Maven 构建」的资源浪费。`docs/**` 和 `*.md` 变更直接在 `push` 触发器用 `paths-ignore` 排除，连 `detect-changes` job 都不运行。

#### 决策 3：镜像构建只在 main / tag 触发

`docker-publish` 的 `if` 条件限定 `github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/')`。feature 分支的 PR 不构建镜像，避免 Harbor 存储膨胀（每个 feature 分支 PR 都产镜像会快速堆积垃圾镜像）。main 分支合并即发布（持续交付），tag 推送即正式发版。

#### 决策 4：CI 与 CD 解耦

CI 阶段（backend-ci / frontend-ci）只管代码质量门禁，产出物是「测试通过的代码」。发布阶段（docker-publish）产出物是「Harbor 中的镜像 tag」。CD 阶段（ArgoCD，后续）监听 Harbor / Git 自动部署 K8s。三者通过**镜像 tag** 这一契约解耦：CI 不关心如何部署，CD 不关心如何构建。这种解耦使 CI 可独立演进，CD 可独立替换。

#### 决策 5：`if: always()` 配合跳过的 CI

`docker-publish` 的 `needs: [backend-ci, frontend-ci]`，但这两个 job 可能因 paths 过滤被**跳过**（skipped）。GitHub Actions 默认行为：`needs` 中任一 job 被 skipped，依赖 job 也会被跳过。这在 main 分支只改了后端代码（前端 CI 跳过）时会导致 `docker-publish` 错误地不执行。`if: always()` 确保无论 CI 是否被跳过，只要满足分支条件就执行发布。

> **`if: always()` 的风险与缓解**：`always()` 会让 job 在上游 CI **失败**时也执行。为避免 CI 失败仍发布镜像，`docker-publish` 子工作流内部需额外校验上游结果（通过 `needs.backend-ci.result` / `needs.frontend-ci.result` 判断），或在 `if` 中细化条件。具体实现见 `docker-publish.md`。

---

## 七、关键设计决策汇总

| # | 决策内容 | 理由 | 影响 |
|---|---------|------|------|
| 1 | 选用可复用工作流组合（方案 B） | 业界标准、模块化、可复用、支持分阶段实现、CI/CD 边界清晰 | 文件数较多（5 workflow + 2 composite action），但每个文件职责单一 |
| 2 | 后端 / 前端 CI 并行执行 | 缩短总耗时（取两者较长者而非之和） | 两个 job 无 `needs` 依赖，需注意两者无共享状态 |
| 3 | paths 过滤按需触发 CI | 避免无关变更浪费 CI 资源（如改文档不触发构建） | 需维护 paths 过滤规则，新增模块需同步更新 `detect-changes` 的 filter |
| 4 | 镜像构建只在 main / tag 触发 | 避免 feature 分支 PR 产垃圾镜像导致 Harbor 存储膨胀 | feature 分支无法预览镜像，需通过 main 合并后验证 |
| 5 | CI 与 CD 通过镜像 tag 解耦 | CI 可独立演进，CD 可独立替换；ArgoCD 监听 Harbor/Git 自动部署 | CD 阶段（ArgoCD）后续独立设计，本次不含 |
| 6 | `if: always()` 处理 CI 跳过场景 | main 分支只改单端代码时，另一端 CI 被 skipped，需确保发布仍执行 | `always()` 会让 CI 失败时也执行发布，子工作流需内部校验上游结果 |
| 7 | composite action 复用环境准备步骤 | `setup-java` / `setup-frontend` 被多个子工作流引用，避免重复 | composite action 变更影响所有引用方，需谨慎 |
| 8 | `concurrency` 取消旧运行 | 同一分支多次推送时取消旧的 CI 运行，节省 runner 资源 | 推送过快可能导致部分检查未完成，PR 合并需确认最新运行通过 |
| 9 | 不采用矩阵并行构建（方案 C） | Maven 多模块依赖关系复杂，需先 install mall-common 再并行，过度优化 | 全量 `mvn verify` 较慢，后期可用 `-T 1C` 并行线程优化，见 testing-specification.md 10.4 |
| 10 | Harbor 私有镜像仓库而非 Docker Hub | 私有可控、支持镜像扫描、支持多租户、生产级标准 | 需自行搭建运维 Harbor，见 `harbor-setup.md` |

---

## 八、分阶段实施路径

> 设计文档已完整，但实现可分阶段。每个阶段是一个**独立可用的闭环**，前一阶段不阻塞后一阶段的设计验证。

### 阶段 1：后端 CI 最小闭环

**目标**：PR 合并前自动跑后端测试，作为合并门禁。

| 交付物 | 文件 | 说明 |
|--------|------|------|
| 编排器 | `.github/workflows/ci.yml` | detect-changes + backend-ci 两个 job |
| 后端 CI 子工作流 | `.github/workflows/backend-ci.yml` | `mvn verify` + jacoco 上传 |
| JDK composite action | `.github/actions/setup-java/action.yml` | JDK 21 + Maven 缓存 |
| 分支保护规则 | GitHub 仓库设置 | main / develop 要求 backend-ci 通过方可合并 |

### 阶段 2：前端 CI 补齐

**目标**：前端代码提交自动跑 lint / type-check / build。

| 交付物 | 文件 | 说明 |
|--------|------|------|
| 前端 CI 子工作流 | `.github/workflows/frontend-ci.yml` | pnpm lint + type-check + build + test |
| 前端 composite action | `.github/actions/setup-frontend/action.yml` | pnpm + Node.js + 缓存 |
| 编排器更新 | `.github/workflows/ci.yml` | 新增 frontend-ci job（并行于 backend-ci） |
| 分支保护更新 | GitHub 仓库设置 | 新增 frontend-ci 为必需检查 |

### 阶段 3：镜像发布闭环

**目标**：main 合并 / tag 推送自动构建镜像并推送 Harbor。

| 交付物 | 文件 | 说明 |
|--------|------|------|
| Harbor 部署 | docker-compose 或独立部署 | 见 `harbor-setup.md` |
| 多阶段 Dockerfile | 各服务模块 `Dockerfile` | 多阶段构建（构建层 + 运行层），见 `docker-publish.md` |
| 镜像发布子工作流 | `.github/workflows/docker-publish.yml` | 构建镜像 → 登录 Harbor → 推送 tag |
| Harbor 凭证 | GitHub Secrets | `HARBOR_USERNAME` / `HARBOR_PASSWORD` / `HARBOR_HOST` |
| 编排器更新 | `.github/workflows/ci.yml` | 新增 docker-publish job |

### 阶段 4：Release 发布管理

**目标**：tag 推送自动生成 GitHub Release，管理版本号。

| 交付物 | 文件 | 说明 |
|--------|------|------|
| Release 子工作流 | `.github/workflows/release.yml` | 读取 CHANGELOG → 生成 GitHub Release |
| 发布流程文档 | `release-and-protection.md` | 版本号规范、tag 规范、Release 模板 |
| Secrets 配置 | GitHub Secrets | `RELEASE_TOKEN`（需 `contents: write` 权限） |
| 编排器更新 | `.github/workflows/ci.yml` | 新增 release job |

---

## 九、相关文档索引

### 9.1 CI/CD 系列文档（本系列）

| 文档 | 状态 | 说明 |
|------|------|------|
| `overview.md` | ✅ 本文 | CI/CD 总体设计 |
| `backend-ci.md` | ✅ 已完成 | 后端 CI 工作流详细设计（mvn verify、jacoco、分阶段测试策略、模块构建优化） |
| `frontend-ci.md` | ✅ 已完成 | 前端 CI 工作流详细设计（pnpm lint / type-check / build / vitest） |
| `docker-publish.md` | ✅ 已完成 | 镜像构建与 Harbor 集成（多阶段 Dockerfile、镜像 tag 规范、推送策略） |
| `harbor-setup.md` | ✅ 已完成 | Harbor 私有镜像仓库部署指南（docker-compose 部署、项目配置、机器人账户） |
| `release-and-protection.md` | ✅ 已完成 | Release 发布 + Secrets 管理 + 分支保护规则 |
| `argocd-cd.md` | 📝 后续 | ArgoCD CD 部署设计（GitOps、Application 资源、滚动更新）—— 本次设计不含 |

### 9.2 关联文档

| 文档 | 关联点 |
|------|--------|
| `docs/standards/testing-specification.md` 第十章「CI 集成」 | 该章规划了 CI 骨架（单文件 Backend CI workflow），**本文档是其生产级完整实现**（扩展为可复用工作流组合，覆盖前端 + 镜像 + Release） |
| `docs/standards/testing-specification.md` 第十一章「前端测试体系」 | 前端 CI 的 vitest 集成依据该章工具链选型（Vitest + Vue Test Utils + happy-dom） |
| `docs/standards/git-workflow.md` | 分支策略（main / develop / feature / fix / hotfix）—— CI 触发策略与之对齐：PR→main/develop、push→develop、push→main、tag 推送 |
| `AGENTS.md` 技术选型表 | GitHub Actions (CI) + Harbor (镜像仓库) + ArgoCD (GitOps CD) 选型依据 |
