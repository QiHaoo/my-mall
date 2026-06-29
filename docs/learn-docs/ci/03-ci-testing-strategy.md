# 03 · CI 中的测试与质量门禁

> 前置阅读：[01-docker-basics.md](./01-docker-basics.md)、[02-ci-fundamentals.md](./02-ci-fundamentals.md)
>
> 本章讲清三件事：CI 为什么要分层测试、覆盖率门禁怎么工作、快速失败原则怎么设计步骤顺序。
> 看完后你会理解 my-mall 后端 `mvn verify` 一条命令背后串联了多少测试环节，以及为什么阈值要分阶段提升。

---

## 一、为什么 CI 要跑测试

先说结论：**CI 跑测试不是「额外负担」，而是「保险机制」**。我们从个人和团队两个角度来看。

### 1.1 个人角度——本地跑测试容易忘、容易跳过

开发时，你是不是经常这样：

- 改完代码，心想「就改了一行，应该没问题」，直接提交了
- 本地跑了几个自己写的测试，通过了，但没跑全量测试（太慢了）
- 「这次只是改个返回值格式，老测试肯定还能过」——结果格式变了，老断言挂了

本地跑测试最大的问题是**没有人强制你**。你可以在提交前跳过测试，也没有谁能拦住你。但 CI 不一样——CI 是一条流水线，测试是其中一环，**不通过就不让你合并**。它不讲人情，每次提交都老老实实跑一遍。

### 1.2 团队角度——合并前验证，保护别人的功能

更重要的场景是：**你的改动可能破坏别人的代码，而你自己根本不知道**。

举个 my-mall 里的实际例子：

```
你改了 mall-product 里的 ProductService.getById() 方法
  ↓
本地跑了自己的 ProductServiceTest，通过了 ✓
  ↓
但你不知道 mall-order 的 OrderService 通过 Feign 调用了这个方法
  ↓
你的改动改变了返回结构，OrderService 解析时报错
  ↓
本地你不会去跑 mall-order 的测试（你甚至不知道有这个依赖）
  ↓
CI 跑全量 mvn verify → mall-order 的测试挂了 → CI 失败 → 你被拦住了
```

CI 跑全量测试的价值就在这里——**它能发现你意识不到的跨模块影响**。个人测试只验证「我写的代码对不对」，CI 测试验证「我的改动有没有破坏整个系统」。

> **一句话总结**：本地测试是「自检」，CI 测试是「门禁」。自检可以偷懒，门禁不能绕过。

---

## 二、测试金字塔

### 2.1 经典分层模型

测试分层有一个经典的「测试金字塔」模型——底层测试多、顶层测试少：

```
        /\
       /  \        E2E 测试（少）
      /    \       端到端，最慢最贵
     /------\
    /        \     集成测试（中）
   /          \    跨模块/跨服务
  /------------\
 /              \  单元测试（多）
/________________\ 最快最便宜
```

为什么是金字塔形状？**越往上越慢越贵，所以数量应该越少**：

- 底层单元测试：毫秒级，一个模块可以有几百个，跑完只要几秒
- 顶层 E2E 测试：几十秒一个，跑十几个就要好几分钟，还可能因为环境问题偶发失败

如果倒过来（冰淇淋模型——顶层多底层少），CI 会变得又慢又不稳定，团队最终会放弃跑测试。

### 2.2 本项目的四层测试

my-mall 是 Java 21 + Spring Boot 3.4 项目，测试分层如下：

| 层级 | 是什么 | 速度 | 本项目工具 | CI 中执行方式 |
|------|--------|------|----------|-------------|
| 单元测试 | 测单个类/方法，无外部依赖 | 毫秒级 | JUnit 5 + Mockito | surefire（mvn test） |
| 切片测试 | 测某一层（Controller/Service），轻量 mock | 秒级 | @WebMvcTest + WireMock | surefire（mvn test） |
| 集成测试 | 测多组件协作，可能需数据库/中间件 | 秒~分钟级 | @SpringBootTest + Testcontainers | failsafe（mvn verify） |
| E2E 测试 | 完整业务流程，端到端 | 分钟级 | Testcontainers + 全链路 | failsafe（mvn verify） |

逐层说明：

**单元测试**——纯 JUnit 5 + Mockito，不加任何 Spring 注解。Mock 所有依赖（Mapper、Feign Client），只验证业务规则。比如「库存不足时下单应抛异常」，不需要真实数据库，用 `@Mock` 桩一个返回值就行。极快，一个测试不到 1 秒。

**切片测试**——用 `@WebMvcTest` 只加载 Controller 层，Service 用 `@MockitoBean` Mock。验证 HTTP 层行为：路由对不对、参数校验有没有生效、响应 JSON 格式对不对。需要轻量启动 Spring 上下文，约 2 秒一个。

**集成测试**——用 `@SpringBootTest` 启动完整 Spring 上下文，用 WireMock 模拟远程服务，用 H2 内存库替代 MySQL。验证 Controller → Feign → HTTP 全链路，包括序列化/反序列化、路由配置。约 5 秒一个。

**E2E 测试**——用 Testcontainers 启动真实的 MySQL/Redis/RocketMQ 容器，验证真实的事务、缓存、消息行为。比如「下单全链路：扣库存 → 创建订单 → 消息通知」。最慢，10~30 秒一个，只写关键业务链路。

> **为什么底层多、顶层少**：单元测试便宜又快，能覆盖大量边界条件；E2E 测试贵又慢，但能发现集成层面的问题。合理的比例是：单元测试占 70%、切片 + 集成占 20%、E2E 占 10%。这不是硬性规定，而是「投入产出比」的平衡。

---

## 三、Maven 测试插件：surefire vs failsafe

本项目用 Maven 管理构建，测试由两个插件分工执行。理解它们的区别，才能理解为什么 CI 用 `mvn verify` 而不是 `mvn test`。

### 3.1 两个插件的分工

| 插件 | 绑定阶段 | 匹配文件 | 执行时机 |
|------|---------|---------|---------|
| maven-surefire-plugin | test | `*Test.java` / `*Tests.java` | `mvn test` 时执行 |
| maven-failsafe-plugin | integration-test + verify | `*IT.java` / `*ITCase.java` | `mvn verify` 时执行 |

关键区别：

- **surefire** 绑定 `test` 阶段，跑单元测试和切片测试（类名以 `Test` 结尾）
- **failsafe** 绑定 `integration-test` + `verify` 阶段，跑集成测试和 E2E 测试（类名以 `IT` 结尾）

### 3.2 为什么要分两个插件

你可能会问：一个插件跑所有测试不行吗？为什么要分两个？

核心原因是**两类测试的「性格」不同**：

| 维度 | 单元测试（surefire） | 集成测试（failsafe） |
|------|---------------------|---------------------|
| 速度 | 极快（毫秒级） | 较慢（秒~分钟级） |
| 稳定性 | 高（纯内存，无外部依赖） | 较低（可能依赖数据库/网络） |
| 失败处理 | 失败立即停止（快速失败） | 失败可重试（容忍偶发失败） |
| 设计哲学 | 快速反馈，错了立刻停 | 尽量跑完，收集所有失败 |

surefire 的名字就是「确定性的火」——快速、果断，一失败就停。failsafe 的名字是「故障安全的」——慢一点没关系，但要跑完，而且它的设计允许重试（`-Dfailsafe.rerunFailingTestsCount=2`）。

这就是为什么要分两个插件：**单元测试要快（失败立即停），集成测试要稳（可重试、可收集所有失败）**。

### 3.3 mvn test vs mvn verify

这是最容易混淆的点：

```bash
# 只跑 surefire（单元/切片测试），不跑集成测试
mvn test

# 跑 surefire + failsafe（单元/切片 + 集成/E2E），完整覆盖
mvn verify
```

Maven 的生命周期阶段是有顺序的：

```
validate → compile → test → package → integration-test → verify
           (编译)    (surefire)           (failsafe)      (failsafe verify + jacoco check)
```

- `mvn test` 执行到 `test` 阶段——只触发 surefire
- `mvn verify` 执行到 `verify` 阶段——依次触发 surefire（test 阶段）→ failsafe（integration-test + verify 阶段）→ jacoco check（verify 阶段）

**本项目 CI 用 `mvn verify`**，完整覆盖所有测试层。实际的 CI 命令是：

```bash
mvn verify -B -ntp -pl !mall-admin-frontend
```

各参数含义：

| 参数 | 说明 |
|------|------|
| `-B` | Batch mode，CI 非交互环境，禁用下载确认等提示 |
| `-ntp` | No transfer progress，关闭依赖下载进度条，减少日志噪音 |
| `-pl !mall-admin-frontend` | 排除前端目录（非 Maven 模块） |
| `verify` | 执行到 verify 阶段，串联 surefire + failsafe + jacoco |

> **一句话记忆**：`mvn test` 只跑快的（单元），`mvn verify` 跑全部的（单元 + 集成 + 覆盖率门禁）。CI 用 `verify` 才完整。

---

## 四、覆盖率与门禁

### 4.1 什么是覆盖率

用最通俗的话讲：**覆盖率 = 你的测试代码「跑过」了多少业务代码**。

举个例子：

```java
// ProductService.java —— 100 行业务代码
public R getProduct(Long id) {
    if (id == null) {           // 第 1 行
        return R.fail("ID不能为空");  // 第 2 行
    }
    Product product = mapper.selectById(id);  // 第 3 行
    if (product == null) {      // 第 4 行
        return R.fail("商品不存在");  // 第 5 行
    }
    return R.ok(product);       // 第 6 行
}
```

如果你的测试只调了 `getProduct(1L)`（正常情况），执行时走了第 1、3、6 行——那 3 行被覆盖了，第 2、4、5 行没走到。覆盖率 = 3/6 = 50%。

如果你再补一个测试调 `getProduct(null)`，第 2 行也走到了——覆盖率 = 4/6 ≈ 67%。

### 4.2 覆盖率的类型

覆盖率不是单一指标，常见的有几种：

| 指标 | 含义 | 本项目采用 |
|------|------|-----------|
| 行覆盖率（LINE） | 多少行代码被执行 | ✅ 门禁指标 |
| 分支覆盖率（BRANCH） | 多少 if/else 分支被覆盖 | 参考指标 |
| 方法覆盖率（METHOD） | 多少方法被调用过 | 参考指标 |
| 类覆盖率（CLASS） | 多少类被覆盖 | 参考指标 |

**本项目用 LINE 覆盖率作为门禁指标**——最直观、最好理解，也最常被业界采用。分支覆盖率作为参考，不在门禁中强制。

> 行覆盖率和分支覆盖率的区别：一段 `if (x > 0) { A } else { B }`，行覆盖率看你有没有走到 A 和 B 的行；分支覆盖率看你有没有同时覆盖 `x > 0` 为 true 和 false 两种情况。分支覆盖率更严格，但对历史代码来说门槛太高。

### 4.3 jacoco 工作原理

jacoco（Java Code Coverage）是 Java 生态最主流的覆盖率工具。它的工作原理分三步：

```
1. prepare-agent：在 JVM 启动时注入 agent（探针），统计代码执行情况
   ↓
2. test：测试运行时，agent 记录每行代码是否被执行
   ↓
3. report + check：生成覆盖率报告，检查是否达到门禁阈值
```

**第 1 步：prepare-agent（绑定 initialize 阶段）**

jacoco 在 JVM 启动时注入一个 agent（Java Agent），这个 agent 会在每行代码前后插入「探针」（probe）。探针本质上是一个布尔标志位——代码执行到这行，标志位就翻成 true。

在 Maven 中配置：

```xml
<execution>
    <id>prepare-agent</id>
    <goals><goal>prepare-agent</goal></goals>
</execution>
```

prepare-agent 会把 agent 参数写到 Maven 的 `argLine` 属性里。surefire/failsafe 启动测试 JVM 时读取这个属性，agent 就跟着一起加载了。

**第 2 步：test（测试运行时记录）**

测试跑的时候，agent 在后台默默记录：第 3 行执行了 ✓、第 5 行没执行 ✗。这些记录写到 `target/jacoco.exec` 文件中。

**第 3 步：report + check（绑定 verify 阶段）**

- `report` goal：读取 `jacoco.exec`，生成人类可读的 HTML 报告（`target/site/jacoco/index.html`）和机器可读的 XML 报告（`jacoco.xml`）
- `check` goal：读取覆盖率数据，和门禁阈值对比。不达标则**构建失败**（BUILD FAILURE）

```xml
<!-- 生成报告 -->
<execution>
    <id>report</id>
    <goals><goal>report</goal></goals>
</execution>

<!-- 门禁检查：不达标则构建失败 -->
<execution>
    <id>check</id>
    <goals><goal>check</goal></goals>
    <configuration>
        <rules>
            <rule>
                <element>BUNDLE</element>      <!-- BUNDLE = 整个模块 -->
                <limits>
                    <limit>
                        <counter>LINE</counter>           <!-- 行覆盖率 -->
                        <value>COVEREDRATIO</value>       <!-- 覆盖比例 -->
                        <minimum>0.60</minimum>           <!-- 最低 60% -->
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</execution>
```

> **`@{argLine}` 是什么**：surefire 的配置里有 `<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>`。`@` 前缀表示「延迟属性引用」——Maven 解析配置时不展开，等到 surefire 真正 fork JVM 时才读取 `argLine` 的值。此时 jacoco 的 prepare-agent 已经把 agent 参数写进去了，两者就能同时生效。如果不写 `@` 前缀，jacoco agent 参数会丢失，覆盖率统计就不准了。

### 4.4 为什么要设门禁

先看没有门禁会怎样：

```
没有门禁的世界：
  第 1 个月：覆盖率 60%，大家还写测试
  第 3 个月：忙起来，新功能不写测试了，覆盖率 55%
  第 6 个月：新人来了，看老代码没测试，也不写，覆盖率 45%
  第 12 个月：覆盖率 20%，测试形同虚设，出 Bug 全靠线上反馈
```

这就是「破窗效应」——一旦覆盖率开始下降且没有人阻止，它会持续恶化。

有门禁的世界：

```
有门禁的世界：
  覆盖率低于 60% → CI 失败 → PR 不能合并 → 你必须补测试
  覆盖率持续达标 → 测试体系稳定 → 重构有信心 → Bug 率下降
```

门禁的本质是**倒逼机制**：不是「鼓励」你写测试，而是「强制」你写。你可以说它不近人情，但它确实有效。

### 4.5 门禁策略：分阶段提升

my-mall 不是新项目——有大量历史代码没有测试。如果一上来就设 80% 门禁，会发生什么？

```
直接设 80%：
  mvn verify → 覆盖率 35% → BUILD FAILURE
  → 所有 PR 都合并不了
  → 开发停滞
  → 团队放弃 CI，直接绕过
  → 门禁形同虚设
```

所以本项目采用**分阶段提升策略**（对齐 `testing-specification.md` §9.2）：

| 阶段 | 阈值 | 时机 | 为什么 |
|------|------|------|--------|
| 当前 | 60% | 立即启用 | 历史代码无测试，阈值宽松避免构建频繁失败 |
| 过渡 | 70% | 补齐 common/gateway 测试后 | 公共组件有测试保障后提升 |
| 目标 | 80% | 测试体系成熟后 | 生产级标准，测试覆盖充分 |

**为什么不一开始就 80%**：60% 是一个「踮脚够得着」的阈值——历史代码虽然覆盖率低，但新写的代码有测试，整体能维持在 60% 以上。这保证了门禁能真正生效（不是形同虚设），同时不会因为过高而阻塞开发。

**为什么最终目标是 80%**：80% 是业界公认的「良好」覆盖率水平。剩下的 20% 留给难以测试的代码（如静态方法、异常分支）。追求 100% 覆盖率投入产出比太低，而且可能诱导写无意义的测试。

> **增量覆盖率（后期规划）**：全量覆盖率门禁有个问题——老代码拉低整体指标。后期可引入增量覆盖率检查（只看本次 PR 新增/修改代码的覆盖率），PR 新代码 LINE 覆盖率 ≥ 80%。这样老代码不影响新代码的质量要求。

### 4.6 排除规则

不是所有代码都应该计入覆盖率。如果纯模型类（getter/setter）也算进去，大量无逻辑的代码会稀释指标，让门禁失去意义。

本项目在 jacoco 中配置了排除规则（对齐 `testing-specification.md` §9.3）：

```xml
<configuration>
    <excludes>
        <!-- 启动类：只有 main 方法，无业务逻辑 -->
        <exclude>**/Application.class</exclude>
        <!-- 代码生成器：生成的工具，不测 -->
        <exclude>**/generator/**</exclude>
        <!-- 纯模型类：只有 getter/setter，没有逻辑 -->
        <exclude>**/entity/**</exclude>
        <exclude>**/dto/**</exclude>
        <exclude>**/vo/**</exclude>
        <!-- MyBatis-Plus 生成的 Mapper 实现：框架保证 -->
        <exclude>**/mapper/impl/**</exclude>
        <!-- 配置类：除非有复杂条件逻辑，否则不测 -->
        <exclude>**/config/*Config.class</exclude>
    </excludes>
</configuration>
```

**为什么要排除**：

假设项目有 100 个 Entity 类，每个 20 行 getter/setter，共 2000 行无逻辑代码。如果不排除，这 2000 行的覆盖率是 0%（没人给 getter 写测试），会大幅拉低整体指标。排除后，覆盖率只统计有业务逻辑的代码，门禁才有意义。

> **排除原则**：排除「没有逻辑的代码」（模型类、配置类、启动类），保留「有逻辑的代码」（Service、Controller、工具类、处理器）。排除不是为了美化数字，而是让指标聚焦在真正需要测试的代码上。

---

## 五、快速失败原则

### 5.1 什么是快速失败

快速失败（Fail Fast）是 CI 步骤顺序设计的核心原则：**最快的检查放最前面，一旦失败立即停止，不浪费时间跑后面的步骤**。

想象一个场景：你提交了一个 PR，代码里有一个 ESLint 错误（语法规范问题）和一个单元测试 Bug。如果 CI 先跑测试（2 分钟）再跑 lint（5 秒），你要等 2 分钟才知道有 lint 错误。如果先跑 lint，5 秒就失败了，你立刻就能去修。

### 5.2 理想的步骤顺序

```
lint（最快，秒级）
  → 编译（快，秒级）
    → 单元测试（中，分钟级）
      → 集成测试（慢，分钟级）
        → 覆盖率检查（快，但依赖测试完成）
          → 镜像构建（最慢）
```

每一步失败时省下的时间：

| 步骤 | 耗时 | 失败后省下 |
|------|------|----------|
| lint | 5s | 不跑编译，省 30s |
| 编译 | 30s | 不跑测试，省 5min |
| 单元测试 | 2min | 不跑集成测试，省 3min |
| 集成测试 | 3min | 不构建镜像，省 5min |

**原则**：把「最可能出错」且「最快能发现」的检查放前面。lint 和编译最容易失败（低级错误多），且最快；集成测试和镜像构建最慢，放后面。

### 5.3 本项目的步骤顺序

**后端**——Maven 内部按生命周期阶段自动排序：

```bash
mvn verify -B -ntp -pl !mall-admin-frontend
```

看起来是一条命令，但 Maven 内部按阶段顺序执行：

```
compile（编译）→ test（surefire 单元/切片）→ integration-test（failsafe 集成）→ verify（jacoco check 门禁）
```

编译失败 → 不会跑测试；单元测试失败 → 不会跑集成测试；集成测试失败 → 不会检查覆盖率。Maven 生命周期天然实现了快速失败。

**前端**——在 workflow 中显式按顺序执行：

```yaml
- name: Lint
  run: pnpm lint                    # 1. 最快，10~20s

- name: Type check
  run: pnpm type-check              # 2. 较快，20~40s

- name: Test
  run: pnpm test                    # 3. 中等，10~30s
  continue-on-error: true           # 当前零测试，暂不阻断

- name: Build
  run: pnpm build                   # 4. 最慢，40~90s
```

前端步骤顺序是 `lint → type-check → test → build`，严格遵循快速失败原则。前端的步骤是在 YAML 中显式声明的（每一步是一个 step），任一步骤失败（除 `continue-on-error` 标记的）即终止整个 Job。

> **后端 vs 前端的区别**：后端的步骤顺序由 Maven 生命周期保证（编译→测试→集成→门禁），一条命令串联；前端的步骤顺序由 workflow YAML 显式编排（lint→type-check→test→build），每步是独立的 step。

---

## 六、前端测试策略

前端测试体系当前**待建立**（对齐 `testing-specification.md` 第十一章），这里先讲规划。

### 6.1 前端测试分层

| 层级 | 工具 | 本项目测试对象 | 说明 |
|------|------|--------------|------|
| 工具函数 | Vitest | `tree.ts`（树形工具） | 纯函数，最好测，必须写 |
| Composables | Vitest | `useTable.ts` / `useDialog.ts` | 组合式函数，测状态变化 |
| 组件 | Vue Test Utils | PageTable / FormDialog | 通用组件，测渲染和交互 |
| E2E | Playwright（后期） | 关键业务流程 | 端到端，最慢，后期再建 |

### 6.2 CI 中的处理策略

前端 CI 中**预先写入 `pnpm test` 步骤并占位**，但设置 `continue-on-error: true`：

```yaml
- name: Test
  run: pnpm test
  continue-on-error: true   # 待测试体系建立后移除此行
```

**为什么先留位不启用**：

1. 当前前端零测试——没有 vitest 依赖、没有 `vitest.config.ts`、没有 spec 文件
2. `pnpm test` 会因缺少依赖或 script 而失败
3. `continue-on-error: true` 确保这个失败不阻断 lint / type-check / build
4. CI 日志中会显示该步骤为「passed with warning」（黄色感叹号），提醒团队测试步骤存在但未真正生效

**启用条件**（满足后移除 `continue-on-error`）：

- 安装 vitest + @vue/test-utils + happy-dom 依赖
- 创建 `vitest.config.ts` 配置文件
- `package.json` 中 `test` script 定义为 `vitest run`
- 编写至少一批核心测试用例

> 这就像装修时先预留网线口——现在不用，但等要用了直接插上就行，不用重新砸墙。CI 工作流结构一步到位，测试体系建好后只需移除一行 `continue-on-error`。

---

## 七、PR 上的覆盖率评论

### 7.1 为什么要在 PR 上展示覆盖率

门禁只告诉你「过/不过」，但不告诉你**具体差多少、哪些文件没覆盖**。PR 覆盖率评论解决这个问题——在 PR 上直接展示覆盖率详情，让评审者和提交者一眼看到问题。

### 7.2 实现方式

本项目用 `Madrapps/jacoco-report` 这个 GitHub Action（对齐 `backend-ci.md` 第七章）：

```yaml
- name: PR coverage comment
  if: github.event_name == 'pull_request'
  uses: Madrapps/jacoco-report@v1.2
  with:
    paths: '**/target/site/jacoco/jacoco.xml'
    token: ${{ secrets.GITHUB_TOKEN }}
    min-coverage-overall: 60           # 总体覆盖率最低 60%
    min-coverage-changed-files: 60     # 本次变更文件覆盖率最低 60%
```

工作流程：

1. CI 跑完 `mvn verify`，jacoco 生成了 `jacoco.xml`（机器可读的覆盖率报告）
2. `Madrapps/jacoco-report` action 读取所有模块的 `jacoco.xml`
3. 在 PR 上发表评论：总体覆盖率、本次 PR 变更文件的覆盖率
4. 如果覆盖率低于 `min-coverage-overall` 或 `min-coverage-changed-files`，action 标记为失败，阻断合并

### 7.3 PR 评论示例

一个典型的覆盖率评论长这样：

```markdown
## 📊 JaCoCo Coverage Report

| Metric | Coverage | Minimum | Status |
|--------|----------|---------|--------|
| **Overall** | 65.3% | 60% | ✅ |
| **Changed Files** | 82.1% | 60% | ✅ |

### Changed Files Coverage

| File | Line Coverage | Branch Coverage |
|------|--------------|-----------------|
| `OrderService.java` | 92.0% | 75.0% |
| `OrderServiceImpl.java` | 85.5% | 66.7% |
| `WareFeignClient.java` | 100% | N/A |

### Overall Coverage Trend

| Module | Line Coverage |
|--------|--------------|
| mall-common | 78.2% |
| mall-product | 62.1% |
| mall-member | 70.5% |
| mall-order | 55.3% ⚠️ |
```

这个评论告诉你三件事：

1. **总体覆盖率 65.3%**——高于 60% 门禁，CI 通过 ✅
2. **本次变更文件覆盖率 82.1%**——你新写的代码测试覆盖很好 ✅
3. **mall-order 模块只有 55.3%**——虽然没触发门禁（门禁看的是全量 BUNDLE），但提醒你这个模块覆盖率偏低 ⚠️

> **两个阈值的区别**：`min-coverage-overall` 看整个项目的总体覆盖率（和父 POM 的 jacoco check 一致）；`min-coverage-changed-files` 只看本次 PR 修改的文件——这相当于一种轻量的增量覆盖率检查，确保新代码有测试覆盖。

---

## 八、测试与 CI 的配合总结

把前面所有内容串起来，看看一次 `mvn verify` 在 CI 中到底做了什么：

| CI 步骤 | 做什么 | Maven 命令/阶段 | 产出 |
|--------|--------|----------------|------|
| 构建 | 编译代码 | `mvn verify`（compile 阶段） | class 文件 |
| 单元/切片测试 | 跑 surefire | `mvn verify`（test 阶段） | 测试结果 |
| 集成测试 | 跑 failsafe | `mvn verify`（integration-test 阶段） | 测试结果 |
| 覆盖率报告 | jacoco 生成 | `mvn verify`（report goal） | HTML/XML 报告 |
| 覆盖率门禁 | jacoco check | `mvn verify`（check goal） | 通过/失败 |
| 报告上传 | artifact | `actions/upload-artifact` | 可下载的报告 |
| PR 评论 | 覆盖率展示 | `Madrapps/jacoco-report` | PR 评论 |

**重点**：前 5 步全部由一次 `mvn verify` 串联完成，Maven 生命周期自动按阶段顺序执行。你不需要写 5 条命令，一条 `mvn verify` 搞定。

完整的 CI 步骤（对齐 `backend-ci.yml`）：

```
PR / Push to feature branch
  → Checkout 代码（fetch-depth: 0，支持 Git blame 覆盖率归属）
    → Setup JDK 21 + Maven 缓存（composite action）
      → mvn verify -B -ntp -pl !mall-admin-frontend
          ├─ compile（编译，失败即停）
          ├─ test（surefire 单元/切片测试，失败即停）
          ├─ integration-test（failsafe 集成测试，失败即停）
          ├─ jacoco report（生成覆盖率报告）
          └─ jacoco check（门禁检查，不达标则 BUILD FAILURE）
        → 上传 jacoco 报告 artifact（if: always()，失败也上传）
          → PR 覆盖率评论（仅 PR 事件）
```

> **`if: always()` 的意义**：即使测试失败，也上传覆盖率报告。这样你可以从 artifact 下载报告，排查是哪些测试用例失败了、覆盖率差多少。如果不加 `if: always()`，测试失败时后续步骤全部跳过，你就拿不到报告了。

---

## 九、小结与下一步

### 核心知识点回顾

| 知识点 | 一句话总结 |
|--------|----------|
| 测试金字塔 | 底层多（单元测试快又便宜）、顶层少（E2E 慢又贵） |
| surefire + failsafe | surefire 跑单元（`*Test`，快，失败即停），failsafe 跑集成（`*IT`，稳，可重试） |
| mvn verify | 一条命令串联编译→单元测试→集成测试→覆盖率门禁，Maven 生命周期自动排序 |
| 覆盖率门禁 | jacoco check 不达标则构建失败，倒逼团队写测试 |
| 门禁分阶段 | 60%（当前）→ 70%（过渡）→ 80%（目标），不能一上来就设太高 |
| 排除规则 | 排除模型类/配置类/启动类，让覆盖率聚焦在有逻辑的代码上 |
| 快速失败 | 最快的检查放最前面，失败立即停，不浪费时间跑后面的步骤 |
| PR 覆盖率评论 | 在 PR 上展示覆盖率详情，低于阈值则阻断合并 |

### 关键命令速查

```bash
# 本地：只跑单元/切片测试（快）
mvn test -pl mall-member

# 本地：跑全部测试 + 覆盖率门禁（完整）
mvn verify -pl mall-member

# 本地：查看覆盖率报告
# 打开 target/site/jacoco/index.html

# CI：完整构建（排除前端目录）
mvn verify -B -ntp -pl !mall-admin-frontend
```

### 下一步

本章讲了 CI 中「测试与质量门禁」部分——`mvn verify` 产出的是测试结果和覆盖率报告。但 CI 还有一个重要产出物：**Docker 镜像**。

下一章 [04 · 构建与镜像仓库](./04-ci-build-and-images.md) 会讲：
- 测试通过后，CI 如何把 Java 应用打包成 Docker 镜像
- 多阶段构建（multi-stage build）怎么减小镜像体积
- 镜像推送到 Harbor 私有仓库的流程
- 镜像标签策略（latest / commit-hash / semver）

从「代码通过测试」到「镜像推送到仓库」，这是 CI 流水线的后半段。
