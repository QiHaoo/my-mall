# 后端 CI 工作流设计

> 本文是 `docs/standards/testing-specification.md` 第十章「CI 集成骨架」的生产级完整实现。
> 覆盖 `backend-ci.yml` 可调用工作流的职责边界、Job 结构、Maven 命令、覆盖率门禁、缓存策略、composite action 复用、完整 YAML 与关键设计决策。

---

## 一、概述

`backend-ci.yml` 是一个**可调用工作流（reusable workflow）**，通过 `on: workflow_call` 声明，由编排器 `ci.yml` 在 job 层级以 `uses` 引用调用，**不直接监听 `push` / `pull_request` 事件**。

这种分层设计的优势：

- **触发逻辑集中**：事件监听、路径过滤、并发控制等编排逻辑统一收敛在 `ci.yml`，`backend-ci.yml` 只关注"构建 + 测试 + 覆盖率"这一单一职责。
- **复用性**：同一套后端 CI 既能被 PR 流程调用，也能被定时任务（`schedule`）、手动触发（`workflow_dispatch`）、发布流程调用，无需重复定义。
- **可演进**：后续新增前端 CI、安全扫描等 job 时，编排器横向扩展即可，互不干扰。

**职责边界**：Maven 构建 + 测试执行（surefire 单元/切片 + failsafe 集成）+ 覆盖率报告生成与上传 + PR 覆盖率评论。**不负责**镜像构建、部署、安全扫描（这些由 `docker-publish.yml`、`security-scan.yml` 等独立工作流承担）。

---

## 二、Job 结构

`backend-ci.yml` 只包含一个 job `backend-ci`，运行在 `ubuntu-latest` 上，使用 JDK 21。整体流程如下：

```
backend-ci (ubuntu-latest, JDK 21)
├── 1. Checkout (fetch-depth: 0)
│       拉取完整 Git 历史，支持覆盖率按提交归属
├── 2. Setup JDK 21 (temurin) + Maven 缓存（composite action）
│       ./.github/actions/setup-java 统一 JDK 环境与 ~/.m2 缓存
├── 3. mvn verify -B -ntp -pl !mall-admin-frontend
│       ├── surefire: 单元/切片测试（@WebMvcTest + WireMock）
│       ├── failsafe: 集成测试（*IT.java）
│       └── jacoco: 覆盖率报告 + 门禁检查（check goal，不达标则失败）
├── 4. 上传 jacoco 报告 artifact（if: always()）
│       即使测试失败也上传，方便排查
└── 5. PR 覆盖率评论（仅 PR 时）
        Madrapps/jacoco-report 在 PR 上展示行覆盖率与变化值
```

> 第 3 步是核心：`mvn verify` 会依次执行到 `verify` 生命周期阶段。surefire 绑定 `test` 阶段（单元/切片测试），failsafe 绑定 `integration-test` + `verify` 阶段（集成测试），jacoco 的 `check` goal 绑定 `verify` 阶段执行门禁校验。三者串联，一次命令完成全部测试与门禁。

---

## 三、Maven 命令设计

核心构建命令：

```bash
mvn verify -B -ntp -pl !mall-admin-frontend
```

各参数说明：

| 参数 | 说明 |
|------|------|
| `-B` | Batch mode（批处理模式），CI 环境不交互，禁用下载进度确认等交互提示 |
| `-ntp` | No transfer progress，关闭依赖下载进度条，减少日志噪音 |
| `-pl !mall-admin-frontend` | Project list 排除前端目录（非 Maven 模块，避免 reactor 误入） |
| `verify` | 执行到 `verify` 阶段，包含 `test`（surefire）+ `integration-test`/`verify`（failsafe）+ jacoco 门禁 |

**为什么用 `verify` 而非 `test`**：`mvn test` 只触发 surefire（单元/切片测试），不会执行 failsafe 的集成测试，也不会触发 jacoco `check` 门禁。`mvn verify` 才能完整覆盖三层测试 + 覆盖率卡点。

**为什么排除 `mall-admin-frontend`**：前端代码位于 `mall-admin-frontend/` 目录，使用 Vite + npm 工具链，不是 Maven 模块。虽然父 POM 的 `<modules>` 未声明它，`-pl !mall-admin-frontend` 作为防御性排除，确保即使目录结构变化也不会让 Maven reactor 尝试解析前端的（不存在的）`pom.xml`。

---

## 四、覆盖率门禁策略

覆盖率门禁与测试规范 `testing-specification.md` 第九章对齐，采用**分阶段提升**策略，避免历史无测试代码一次性拉低导致构建频繁失败：

| 阶段 | lines / branches | 触发时机 |
|------|------------------|---------|
| 当前 | 60% | 立即启用（历史代码未补测试，阈值宽松） |
| 过渡 | 70% | 补齐 common / gateway 测试后提升 |
| 目标 | 80% | 生产级标准，测试体系成熟后收紧 |

**门禁机制**：jacoco 的 `check` goal 绑定 `verify` 阶段，对 `BUNDLE` 级别校验 `LINE` 覆盖率 `COVEREDRATIO`，低于阈值则构建失败。阈值在父 POM `jacoco-maven-plugin` 的 `<minimum>` 中配置（当前 `0.60`）。

**排除规则**：以下非业务类不计入覆盖率统计（在 jacoco `excludes` 中配置），避免纯模型类、配置类、启动类稀释指标：

```xml
<configuration>
    <excludes>
        <!-- 启动类 -->
        <exclude>**/Application.class</exclude>
        <!-- 代码生成器 -->
        <exclude>**/generator/**</exclude>
        <!-- DTO/VO/Entity 等纯模型类 -->
        <exclude>**/entity/**</exclude>
        <exclude>**/dto/**</exclude>
        <exclude>**/vo/**</exclude>
        <!-- MyBatis-Plus 生成的 Mapper -->
        <exclude>**/mapper/impl/**</exclude>
        <!-- 配置类（无复杂逻辑） -->
        <exclude>**/config/*Config.class</exclude>
    </excludes>
</configuration>
```

> 完整的 jacoco 插件配置（prepare-agent + report + check）见测试规范 §5.1。

---

## 五、缓存策略

Maven 本地仓库缓存由 `actions/setup-java@v4` 的 `cache: maven` 选项内置支持，封装在 composite action 中，无需单独编写 `actions/cache` 步骤。

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 缓存路径 | `~/.m2/repository` | Maven 本地仓库，存放下载的依赖 jar |
| 缓存 key | `maven-{hash}` | hash 由所有 `**/pom.xml` 文件内容计算得出，pom 变化即失效 |
| restore-keys | `maven-` | key 精确匹配失败时，按前缀匹配最近一次缓存（部分命中） |

**fallback 机制**：当 `pom.xml` 变更导致精确 key 未命中时，`restore-keys: maven-` 会装载最近一次的缓存（依赖版本大部分未变），随后 Maven 只需下载增量变更的依赖，避免每次全量下载。这是 GitHub Actions cache 的标准降级策略，setup-java@v4 已内置。

> 缓存命中时首次 `mvn verify` 可节省 1~3 分钟依赖下载时间。缓存未命中（新增依赖或首次运行）时仍可正常构建，只是稍慢。

---

## 六、composite action 复用

JDK 环境配置抽取为 composite action `.github/actions/setup-java/`，被 `backend-ci.yml` 和 `docker-publish.yml` 共同复用，统一 JDK 版本与 Maven 缓存配置。

**设计要点**：

- composite action 封装 `actions/setup-java@v4`（`java-version: '21'`，`distribution: 'temurin'`，`cache: maven`）。
- **checkout 不纳入 composite action**：不同消费方对 `fetch-depth` 需求不同——`backend-ci.yml` 需要 `fetch-depth: 0`（支持 Git blame 覆盖率归属），`docker-publish.yml` 通常只需默认浅克隆。因此 checkout 由各 workflow 单独声明，composite action 专注 JDK + 缓存。
- JDK 版本通过 input 暴露（默认 `'21'`），保留演进灵活性，当前全项目锁定 Java 21。

composite action 文件 `.github/actions/setup-java/action.yml`：

```yaml
name: Setup JDK 21 + Maven Cache
description: 统一 JDK 21 (temurin) 环境与 Maven 本地仓库缓存，供 backend-ci / docker-publish 复用

inputs:
  java-version:
    description: JDK 版本
    required: false
    default: '21'

runs:
  using: composite
  steps:
    - name: Setup JDK
      uses: actions/setup-java@v4
      with:
        java-version: ${{ inputs.java-version }}
        distribution: temurin
        cache: maven
```

> composite action 是 **step 级复用**（在 `steps:` 下用 `uses:` 引用），区别于可调用工作流的 job 级复用。适合抽取几个重复步骤（此处仅 setup-java），避免在每个 workflow 重复写 JDK 版本、distribution、cache 配置。

---

## 七、完整 workflow YAML

`.github/workflows/backend-ci.yml`：

```yaml
name: Backend CI

# 可调用工作流：由编排器 ci.yml 通过 workflow_call 调用，不直接监听事件
# 后续如需参数化（如覆盖率阈值、跳过测试），可通过 inputs 扩展
on:
  workflow_call:

jobs:
  backend-ci:
    name: Build & Test (Maven)
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          # 完整历史，支持覆盖率按提交归属（Git blame）
          fetch-depth: 0

      - name: Setup JDK 21 + Maven cache
        uses: ./.github/actions/setup-java

      - name: Run tests
        run: mvn verify -B -ntp -pl !mall-admin-frontend

      - name: Upload coverage report
        # 即使测试失败也上传报告，方便排查
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-reports
          path: '**/target/site/jacoco/'
          if-no-files-found: warn

      - name: PR coverage comment
        # 仅 PR 事件时评论；workflow_call 调用时 github 上下文从编排器透传
        if: github.event_name == 'pull_request'
        uses: Madrapps/jacoco-report@v1.2
        with:
          paths: '**/target/site/jacoco/jacoco.xml'
          token: ${{ secrets.GITHUB_TOKEN }}
          min-coverage-overall: 60
          min-coverage-changed-files: 60
```

**关键步骤说明**：

| 步骤 | 作用 | 备注 |
|------|------|------|
| Checkout | 拉取代码，`fetch-depth: 0` 获取完整历史 | 支持 Git blame 覆盖率归属 |
| Setup JDK 21 + Maven cache | composite action，配置 JDK 21 + 缓存 `~/.m2` | 被 docker-publish 复用 |
| Run tests | `mvn verify` 执行 surefire + failsafe + jacoco | 核心 step，失败即终止 job |
| Upload coverage report | 上传 HTML/XML 覆盖率报告为 artifact | `if: always()` 保证失败也上传 |
| PR coverage comment | 在 PR 上评论覆盖率变化 | 仅 `pull_request` 事件触发 |

> `min-coverage-overall` 与 `min-coverage-changed-files` 当前均设为 60，与父 POM jacoco 门禁阈值一致。后续随测试规范 §9.2 分阶段提升至 80 时，此处同步收紧。

---

## 八、关键设计决策

| # | 决策 | 理由 | 影响 |
|---|------|------|------|
| 1 | **全量构建优先**，第一版不做 path filter 选择性构建 | 模块间依赖复杂（common 被多模块依赖），path filter 误判漏构建的风险高于节省的 CI 时间 | CI 时间较长但结果准确，后期可引入 `-T 1C` 并行优化 |
| 2 | **`fetch-depth: 0`** 完整历史 | 支持覆盖率按提交归属（Git blame），定位未覆盖代码的引入者 | 略微增加 checkout 时间，但信息价值高 |
| 3 | **`if: always()` 上传报告** | 即使测试失败也上传 jacoco 报告 | 方便从 artifact 下载报告排查失败用例的覆盖情况 |
| 4 | **PR 覆盖率评论**用 `Madrapps/jacoco-report@v1.2` | 在 PR 上直观展示行覆盖率与变化值，无需手动下载 artifact | PR 评审时可见覆盖率指标，低于阈值时 action 标记失败 |
| 5 | **覆盖率门禁分阶段**，当前 60%、目标 80% | 历史代码未补测试，过高阈值会阻塞开发 | 不会因门禁过高导致构建频繁失败，随测试补齐逐步收紧 |
| 6 | **surefire argLine 兼容 jacoco** | jacoco `prepare-agent` 注入 agent 到 `argLine`，surefire 需用 `@{argLine}` 延迟引用 | 父 POM 需调整 argLine（见第九章前置条件），否则 jacoco agent 与 JDK 21 动态 agent 参数冲突 |

---

## 九、前置条件

本工作流依赖父 POM 的测试插件配置。以下三项来自测试规范 `testing-specification.md` 第十四章 P0 行动清单，**必须先完成**，否则 `mvn verify` 无法正确执行集成测试和覆盖率统计：

| # | 前置事项 | 涉及文件 | 说明 |
|---|---------|---------|------|
| 1 | 父 POM 补 `maven-failsafe-plugin` | `pom.xml` | 当前缺失，导致 `*IT.java` 集成测试在 `mvn verify` 时不执行（surefire 默认不匹配 `*IT`） |
| 2 | 父 POM 补 `jacoco-maven-plugin` | `pom.xml` | 当前无覆盖率统计和门禁（prepare-agent + report + check） |
| 3 | surefire `argLine` 改为 `@{argLine}` 配合 jacoco | `pom.xml` | 当前为 `-XX:+EnableDynamicAgentLoading -Xshare:off`，需改为 `@{argLine} -XX:+EnableDynamicAgentLoading -Xshare:off` |

**调整后的父 POM surefire 配置**（详见测试规范 §5.1）：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <!-- @{argLine} 由 jacoco prepare-agent 注入 agent（@ 表示延迟属性引用） -->
        <!-- -XX:+EnableDynamicAgentLoading 消除 Mockito/ByteBuddy 在 JDK 21 的动态 agent 告警 -->
        <argLine>@{argLine} -XX:+EnableDynamicAgentLoading -Xshare:off</argLine>
    </configuration>
</plugin>
```

> `@{argLine}` 的 `@` 前缀表示**延迟属性引用**：Maven 在插件配置解析阶段不展开，而是等到 surefire 实际 fork JVM 时才读取 `argLine` 属性的值——此时 jacoco `prepare-agent` 已将 agent 参数写入该属性，从而保证 jacoco agent 与 JDK 21 动态 agent 参数同时生效。

---

## 十、与测试规范的关联

本文档是测试规范 `docs/standards/testing-specification.md` 第十章「CI 集成（GitHub Actions）」骨架的**生产级完整实现**，两者的对应关系：

| 测试规范（骨架） | 本文档（完整实现） |
|-----------------|-------------------|
| §10.1 CI 流程设计（Checkout → JDK → Cache → mvn verify → artifact → PR 评论） | 第二章 Job 结构（ASCII 流程图 + 分步说明） |
| §10.2 workflow 骨架（直接监听 pull_request/push） | 第七章完整 YAML（改为 `workflow_call` 可调用，由 ci.yml 编排） |
| §10.3 分阶段引入策略（阶段1 test → 阶段2 verify → 阶段3 E2E） | 第四章覆盖率门禁分阶段（60% → 70% → 80%） |
| §9 覆盖率门禁（Jacoco） | 第四章覆盖率门禁策略（阈值、排除规则对齐） |
| §5.1 父 POM 插件配置（surefire + failsafe + jacoco） | 第九章前置条件（P0 项：补 failsafe、补 jacoco、改 argLine） |
| §14 行动清单 P0/P1 | 第九章前置条件表 |

**核心对齐点**：

- **测试分层**：surefire（单元/切片 `*Test.java`）+ failsafe（集成 `*IT.java`）的分工与测试规范 §3 一致，由 `mvn verify` 一次串联执行。
- **覆盖率门禁**：阈值（当前 60% → 目标 80%）、指标（LINE 覆盖率）、排除规则（entity/dto/vo/config 等）均与测试规范 §9 对齐，CI 门禁与本地 `mvn verify` 行为一致。
- **argLine 兼容**：`@{argLine}` 配合 jacoco 的方案来自测试规范 §5.1，是本工作流能正确统计覆盖率的前提。

> 测试规范第十章的骨架直接监听 `pull_request`/`push` 事件；本文档将其演化为 `workflow_call` 可调用工作流，由编排器 `ci.yml` 统一管理触发逻辑。这是生产级 CI 编排的标准做法——触发编排与构建逻辑分离。
