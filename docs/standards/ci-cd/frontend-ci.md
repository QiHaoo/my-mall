# 前端 CI 工作流设计

> 本文档定义 `mall-admin-frontend` 的 GitHub Actions CI 工作流（`frontend-ci.yml`）。
> 技术栈：Vue 3 + TypeScript + Vite 6 + Element Plus + pnpm。
>
> **相关文档**：
> - 编排器总览：`docs/standards/ci-cd/overview.md`
> - 后端 CI：`docs/standards/ci-cd/backend-ci.md`
> - 前端测试体系（待建立）：`docs/standards/testing-specification.md` 第十一章

---

## 一、概述

`frontend-ci.yml` 通过 `workflow_call` 被编排器 `ci.yml` 调用，不直接由 `push` / `pull_request` 触发。这样可以将触发条件、路径过滤等编排逻辑集中在 `ci.yml`，各子工作流只关注自身执行逻辑，职责单一、便于复用。

**职责范围**：

1. 依赖安装（`pnpm install --frozen-lockfile`）
2. ESLint 代码检查（`pnpm lint`）
3. TypeScript 类型检查（`pnpm type-check`）
4. Vitest 单元测试（`pnpm test`，待建立，先留位）
5. 生产构建（`pnpm build`）
6. 上传构建产物 artifact（`dist/`）

> 前端代码位于仓库根目录下的 `mall-admin-frontend/` 子目录，所有 `pnpm` 命令必须在该目录下执行（通过 `defaults.run.working-directory` 统一设置）。

---

## 二、Job 结构

```
frontend-ci (ubuntu-latest, Node 20)
├── 1. Checkout                     # 拉取代码
├── 2. Setup pnpm + Node 20 + 缓存   # composite action 统一环境
├── 3. pnpm install --frozen-lockfile   # 依赖安装（锁定文件）
├── 4. pnpm lint                    # ESLint 检查
├── 5. pnpm type-check              # vue-tsc 类型检查
├── 6. pnpm test                    # vitest 单元测试（待建立，先留位）
├── 7. pnpm build                   # 生产构建
└── 8. 上传构建产物 artifact（dist/）
```

整个工作流为单 Job，步骤按顺序串行执行，任一步骤失败（除显式标记 `continue-on-error` 的步骤）即终止整个 Job。

---

## 三、步骤执行顺序设计

步骤顺序遵循**快速失败（Fail Fast）原则**：耗时短、问题暴露快的检查先跑，耗时长、资源消耗大的构建后跑。这样可以在代码有低级问题时尽早失败，避免浪费后续步骤的 CI 执行时间。

执行顺序：`lint` → `type-check` → `test` → `build`

| 顺序 | 步骤 | 作用 | 失败是否阻断 | 耗时预估 | 排在前面的理由 |
|------|------|------|-------------|---------|---------------|
| 1 | `pnpm install` | 安装依赖 | ✅ 阻断 | 30~60s（缓存命中后 5~15s） | 后续所有步骤的前提 |
| 2 | `pnpm lint` | ESLint 代码规范检查 | ✅ 阻断 | 10~20s | 最快，纯静态分析，无需编译 |
| 3 | `pnpm type-check` | `vue-tsc` 类型检查 | ✅ 阻断 | 20~40s | 较快，类型错误属于低级问题应尽早暴露 |
| 4 | `pnpm test` | Vitest 单元测试 | ⚠️ 暂不阻断（`continue-on-error: true`） | 10~30s | 中等，逻辑正确性验证；当前零测试故暂不阻断 |
| 5 | `pnpm build` | Vite 生产构建 | ✅ 阻断 | 40~90s | 最慢，资源消耗最大，放最后 |

> **快速失败收益**：如果提交有一个 ESLint 错误，10 秒内即可失败反馈；如果放到 build 之后才发现，则白白等待了 1~2 分钟的构建时间。在 PR 频繁触发的 CI 场景下，累计节省的 CI 资源相当可观。

---

## 四、pnpm 缓存策略

pnpm 采用**内容寻址存储（content-addressable store）**机制，全局 store 中每个包只存一份，项目通过硬链接引用。缓存 store 可以大幅减少依赖安装时间。

### 4.1 缓存内容与路径

| 缓存对象 | 路径 | 说明 |
|---------|------|------|
| pnpm store | `~/.local/share/pnpm/store`（Linux runner 默认） | 全局包存储，所有项目共享 |
| 缓存 key | `pnpm-lock.yaml` 的 hash | 锁文件不变则命中缓存 |

### 4.2 缓存配置方式

本项目使用 `actions/setup-node@v4` 内置的 pnpm 缓存能力（`cache: 'pnpm'`），它会自动：

1. 检测 `pnpm-lock.yaml` 作为 cache key 的输入文件
2. 自动缓存/恢复 pnpm store 目录
3. 命中缓存时跳过网络下载，直接从 store 硬链接

```yaml
- uses: pnpm/action-setup@v4
  with:
    version: 9

- uses: actions/setup-node@v4
  with:
    node-version: '20'
    cache: 'pnpm'              # 启用 pnpm store 缓存
    cache-dependency-path: 'mall-admin-frontend/pnpm-lock.yaml'
```

> **`cache-dependency-path` 说明**：因为前端代码在 `mall-admin-frontend/` 子目录，`setup-node` 默认在仓库根目录查找 `pnpm-lock.yaml`，必须显式指定子目录路径，否则缓存 key 计算异常导致永远无法命中。

### 4.3 缓存命中效果

| 场景 | 首次（无缓存） | 后续（缓存命中） |
|------|--------------|----------------|
| `pnpm install` 耗时 | 30~60s | 5~15s |
| 网络下载 | 全量下载 | 仅下载 lockfile 变更的包 |

---

## 五、composite action 复用

前端环境初始化逻辑（checkout + pnpm + node + 缓存）封装为 composite action，供多个工作流复用。

### 5.1 设计位置

```
.github/actions/setup-frontend/
└── action.yml
```

### 5.2 action 内容

```yaml
# .github/actions/setup-frontend/action.yml
name: Setup Frontend Environment
description: 安装 pnpm + Node 20 + 缓存，供前端 CI 和 Docker 构建复用

inputs:
  working-directory:
    description: 前端项目目录（相对于仓库根）
    required: false
    default: 'mall-admin-frontend'

runs:
  using: composite
  steps:
    - name: Checkout
      uses: actions/checkout@v4

    - name: Setup pnpm
      uses: pnpm/action-setup@v4
      with:
        version: 9

    - name: Setup Node 20 with pnpm cache
      uses: actions/setup-node@v4
      with:
        node-version: '20'
        cache: 'pnpm'
        cache-dependency-path: '${{ inputs.working-directory }}/pnpm-lock.yaml'
```

### 5.3 复用场景

| 调用方 | 用途 | 复用价值 |
|--------|------|---------|
| `frontend-ci.yml` | CI 中的 lint / type-check / test / build | 统一前端环境初始化 |
| `docker-publish.yml` | 前端 Docker 镜像构建（多阶段构建的 builder 阶段） | 确保构建环境与 CI 一致 |

> **复用原则**：环境初始化逻辑只写一份，避免 pnpm 版本、Node 版本、缓存配置在多处重复定义导致不一致。后续升级 Node 或 pnpm 版本时只需改 composite action 一处。

---

## 六、vitest 测试步骤说明

### 6.1 当前状态

mall-admin-frontend **当前零测试**——没有安装 vitest 依赖、没有 `vitest.config.ts`、没有任何 `*.spec.ts` 测试文件。前端测试体系属于待建立项，详见 `docs/standards/testing-specification.md` 第十一章「前端测试体系（待建立）」。

### 6.2 CI 中的处理策略

CI 工作流中**预先写入 `pnpm test` 步骤并占位**，但设置 `continue-on-error: true`：

```yaml
- name: Test
  run: pnpm test
  continue-on-error: true   # 待测试体系建立后移除此行
```

**这样设计的原因**：

1. **结构完整**：CI 工作流结构一步到位，测试体系建立后只需移除 `continue-on-error` 即可启用，无需改动工作流骨架。
2. **不阻断主流程**：当前 `pnpm test` 会因缺少 vitest 依赖或 `test` script 而失败，`continue-on-error: true` 确保该失败不影响 lint / type-check / build 的执行。
3. **可见性**：CI 运行日志中会显示该步骤为「passed with warning」（黄色感叹号），提醒团队测试步骤存在但尚未真正生效。

### 6.3 启用条件

待以下条件满足后，移除 `continue-on-error: true`：

- [ ] 安装 vitest + @vue/test-utils + happy-dom 依赖
- [ ] 创建 `vitest.config.ts` 配置文件
- [ ] `package.json` 中 `test` script 定义为 `vitest run`
- [ ] 编写至少一批核心测试用例（工具函数、composables、通用组件）

> 参见 `docs/standards/testing-specification.md` 第十四章行动清单 P4 优先级事项（#16~#23）。

---

## 七、完整 workflow YAML

```yaml
# .github/workflows/frontend-ci.yml
name: Frontend CI

on:
  workflow_call:
    # 被编排器 ci.yml 调用，不直接由 push/pr 触发

jobs:
  frontend-ci:
    runs-on: ubuntu-latest
    defaults:
      run:
        # 前端代码在 mall-admin-frontend/ 子目录，所有 pnpm 命令需在此目录执行
        working-directory: mall-admin-frontend

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup pnpm + Node 20 + cache
        uses: ./.github/actions/setup-frontend
        # composite action：安装 pnpm 9 + Node 20 + pnpm store 缓存

      - name: Install dependencies
        run: pnpm install --frozen-lockfile
        # --frozen-lockfile：CI 强制使用 lockfile 锁定版本，不允许 lockfile 漂移

      - name: Lint
        run: pnpm lint
        # ESLint 代码规范检查

      - name: Type check
        run: pnpm type-check
        # vue-tsc --noEmit 类型检查

      - name: Test
        run: pnpm test
        # 待测试体系建立后移除 continue-on-error
        continue-on-error: true

      - name: Build
        run: pnpm build
        # Vite 生产构建，输出到 dist/

      - name: Upload dist artifact
        uses: actions/upload-artifact@v4
        with:
          name: frontend-dist
          path: mall-admin-frontend/dist/
          retention-days: 7
          # 构建产物保留 7 天，供 docker-publish 等下游工作流拉取
```

### 7.1 关键配置说明

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `on.workflow_call` | — | 仅被编排器调用，不直接触发 |
| `runs-on` | `ubuntu-latest` | GitHub 托管 runner，自带 Node/Docker |
| `defaults.run.working-directory` | `mall-admin-frontend` | 所有 `run` 步骤的默认工作目录 |
| `pnpm install --frozen-lockfile` | — | 禁止 lockfile 漂移，保证可复现构建 |
| `upload-artifact path` | `mall-admin-frontend/dist/` | 注意路径需从仓库根目录算起（artifact 路径不受 `working-directory` 影响） |

> **⚠️ artifact 路径陷阱**：`actions/upload-artifact` 的 `path` 参数是**相对于仓库根目录**的，不受 `defaults.run.working-directory` 影响。因此必须写 `mall-admin-frontend/dist/` 而非 `dist/`，否则会报「no files found」错误。

---

## 八、关键设计决策

| # | 决策 | 理由 | 影响 |
|---|------|------|------|
| 1 | **pnpm 优先** | 项目已使用 pnpm（有 `pnpm-lock.yaml`），CI 保持一致 | 依赖安装快、磁盘占用小（硬链接共享 store）；CI 与本地环境一致，避免「本地通过 CI 失败」 |
| 2 | **`--frozen-lockfile`** | CI 必须强制锁定文件版本，不允许 lockfile 漂移 | 保证构建可复现；防止 `package.json` 与 `pnpm-lock.yaml` 不一致时 CI 静默更新 lockfile 导致依赖不可控 |
| 3 | **lint → type-check → test → build 顺序** | 快速失败原则 | 尽早暴露问题减少 CI 消耗；lint 最快（纯静态）先跑，build 最慢后跑 |
| 4 | **vitest 步骤先留位** | 当前零测试，但 CI 结构需完整 | `continue-on-error: true` 待测试体系建立后去掉；测试就绪即可启用，无需改工作流骨架 |
| 5 | **构建产物上传 artifact** | `dist/` 作为 artifact 上传 | 供 `docker-publish` 工作流拉取用于 Nginx 镜像构建（但实际前端 Dockerfile 自包含构建，artifact 作为备份验证手段） |
| 6 | **Node 20 LTS** | 与 Vite 6 兼容性最佳，LTS 版本稳定可靠 | 避免使用非 LTS 版本导致的偶发问题；GitHub Actions runner 对 LTS 支持最好 |
| 7 | **`working-directory: mall-admin-frontend`** | 前端代码在子目录 | 所有 `pnpm` 命令需在正确目录执行；artifact 路径需额外注意不受此设置影响 |
| 8 | **composite action 封装环境初始化** | checkout + pnpm + node 逻辑被多工作流复用 | 统一版本管理，升级时只改一处；避免 `docker-publish.yml` 重复定义导致环境不一致 |

---

## 九、与后端 CI 的关系

前端 CI 与后端 CI 是两个**完全独立**的工作流，通过编排器 `ci.yml` 统一调度。

### 9.1 并行执行

```
ci.yml（编排器）
├── backend-ci   ──┐
│                  ├── 两个 job 无 needs 依赖，并行执行
└── frontend-ci  ──┘
```

两个工作流在编排器中是独立的 job，没有 `needs` 依赖关系，因此**并行执行**，互不等待。前端 CI 的失败不会阻断后端 CI，反之亦然。

### 9.2 路径隔离

通过编排器 `ci.yml` 的 `paths` 过滤实现变更隔离：

| 变更范围 | 触发的前端 CI | 触发的后端 CI |
|---------|-------------|-------------|
| 仅 `mall-admin-frontend/**` 变更 | ✅ 触发 | ❌ 不触发 |
| 仅 `mall-*/**`（后端服务）变更 | ❌ 不触发 | ✅ 触发 |
| 根目录 `pom.xml` / `docker-compose.yml` 变更 | ❌ 不触发 | ✅ 触发 |
| 同时变更前后端 | ✅ 触发 | ✅ 触发 |

> 路径过滤的配置在编排器 `ci.yml` 中统一管理，`frontend-ci.yml` 本身不关心触发条件。详见 `docs/standards/ci-cd/overview.md`。

---

## 十、前置条件

### 10.1 package.json scripts 要求

CI 工作流依赖 `package.json` 中定义以下 scripts：

| script | 期望命令 | 作用 | 当前状态 |
|--------|---------|------|---------|
| `lint` | `eslint .` | ESLint 检查（CI 中不应带 `--fix`） | ⚠️ 当前为 `eslint . --ext ... --fix --ignore-path .gitignore`，带 `--fix` 会自动修改文件，CI 中应改为只检查不修复 |
| `type-check` | `vue-tsc --noEmit` | TypeScript 类型检查（不输出文件） | ❌ 缺失，需新增（当前类型检查内嵌在 `build` 的 `vue-tsc -b` 中） |
| `test` | `vitest run` | Vitest 单元测试（单次运行模式） | ❌ 缺失（待建立），需新增 |
| `build` | `vite build` | Vite 生产构建 | ⚠️ 当前为 `vue-tsc -b && vite build`，含类型检查；CI 中已有独立的 `type-check` 步骤，建议 `build` 精简为 `vite build` 避免重复检查 |

### 10.2 需补充的 scripts

当前 `package.json` 的 `scripts` 需调整为以下结构以适配 CI（同时兼顾本地开发）：

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "lint": "eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts --ignore-path .gitignore",
    "lint:fix": "eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts --fix --ignore-path .gitignore",
    "type-check": "vue-tsc --noEmit",
    "test": "vitest run",
    "format": "prettier --write src/"
  }
}
```

**调整说明**：

| 调整项 | 原始 | 调整后 | 理由 |
|--------|------|--------|------|
| `lint` | 带 `--fix` | 去掉 `--fix`（只检查） | CI 中不应自动修复，只检查并报告错误；修复交给开发者本地执行 |
| `lint:fix` | — | 新增（原 `lint` 的内容） | 保留本地一键修复能力 |
| `type-check` | — | 新增 `vue-tsc --noEmit` | CI 独立类型检查步骤；`--noEmit` 只检查不生成 `.js` 文件 |
| `test` | — | 新增 `vitest run` | CI 单次运行模式（非 watch）；待 vitest 体系建立后生效 |
| `build` | `vue-tsc -b && vite build` | `vite build` | CI 已有独立 `type-check` 步骤，`build` 无需重复类型检查，加快构建速度 |

> **本地开发兼容**：`lint:fix` 保留给开发者本地使用；CI 用 `lint`（不带 `--fix`）。这样开发者可以 `pnpm lint:fix` 一键修复，CI 用 `pnpm lint` 严格检查。

### 10.3 其他前置条件

| 前置条件 | 当前状态 | 说明 |
|---------|---------|------|
| `pnpm-lock.yaml` 存在 | ✅ 已有 | `--frozen-lockfile` 的前提 |
| `.gitignore` 包含 `dist/` | ✅ 应已有 | 构建产物不入库 |
| composite action `.github/actions/setup-frontend/` | ❌ 需创建 | 见第五章 |
| vitest 依赖与配置 | ❌ 待建立 | 见第六章，不影响 CI 骨架运行 |
