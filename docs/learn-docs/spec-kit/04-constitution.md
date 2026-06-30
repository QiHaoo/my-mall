# 04 - 宪法与架构治理：把原则变成可执行的门禁

> 本文回答：constitution（宪法）到底是什么？九条条款讲了什么？"不可变原则"怎么落地？它和模板怎么配合强制架构纪律？
> 主要来源：`spec-driven.md` 的 "Constitutional Foundation" 章节、`templates/constitution-template.md`、`.specify/memory/constitution.md`。

## 1. 宪法是什么

Spec Kit 的 constitution（宪法）是项目的**架构 DNA**——一组不可变原则，规定"规范如何变成代码"的底线性约束。

- 位置：`.specify/memory/constitution.md`（init 后是空模板，由 `/speckit.constitution` 填充）
- 作用：后续所有阶段（plan/implement）都会引用它，作为决策基准
- 性质：**原则不可变，应用可演进**

> 用原文的话：宪法不是规则书，是**塑造 LLM 如何思考代码生成的哲学**。

## 2. 为什么需要宪法

LLM 生成代码时有个通病：**过度热情**——加你没要的抽象层、引入不必要的依赖、搞 elaborate 的设计。没有约束基准，每次生成的代码风格、架构取向都不一致。

宪法解决的就是这个问题：把"我们团队的非协商标准"固化下来，让：

1. **跨时间一致**：今天生成的代码和明年生成的遵循同一原则
2. **跨 LLM 一致**：不同 AI 模型产出架构兼容的代码
3. **架构完整性**：每个 feature 强化而非削弱系统设计
4. **质量保证**：test-first、library-first、simplicity 等确保可维护

## 3. 九条条款（Nine Articles）

`spec-driven.md` 给了一个示例性的"九条条款"结构。注意：**这是示例，不是 Spec Kit 强制内容**——项目用自己的 constitution 填充，条款内容因项目而异。

### Article I：Library-First（库优先）

每个 feature 必须先作为独立库存在，绝不在应用代码里直接实现。

```text
Every feature MUST begin its existence as a standalone library.
No feature shall be implemented directly within application code without
first being abstracted into a reusable library component.
```

**目的**：逼出模块化设计。LLM 生成实现计划时，必须把 feature 结构化为边界清晰、依赖最小的库。

### Article II：CLI Interface（CLI 接口强制）

每个库必须通过 CLI 暴露功能：

```text
All CLI interfaces MUST:
- Accept text as input (stdin/args/files)
- Produce text as output (stdout)
- Support JSON for structured data exchange
```

**目的**：可观测 + 可测试。功能不能藏在 opaque 类里，一切通过文本接口可访问、可验证。

### Article III：Test-First（测试优先，不可协商）

最颠覆的一条——**代码之前先写测试**：

```text
NON-NEGOTIABLE: All implementation MUST follow strict TDD.
No implementation code shall be written before:
1. Unit tests are written
2. Tests are validated and approved by the user
3. Tests are confirmed to FAIL (Red phase)
```

**目的**：完全反转传统 AI 代码生成。不是"生成代码然后祈祷它能跑"，而是"先生成定义行为的测试 → 用户批准 → 确认测试失败（Red）→ 才生成实现"。Red-Green-Refactor 严格强制。

### Article IV / V / VI：项目自定义治理

这三条**故意留空**——由项目自己的 constitution 填充，而非 Spec Kit 预设。

```text
Articles IV, V, VI are intentionally defined by each project's constitution.
模板给占位符和示例关注点（集成测试、可观测性、版本管理、破坏性变更），
团队替换成匹配自己系统和组织的原则。
```

**设计意图**：保持九条结构稳定，同时让每个项目编码自己的非协商标准。比如：

- A 项目的 Article IV 可能管安全与访问边界
- B 项目的 Article IV 可能定义集成测试要求

`/speckit.analyze` 命令会评估项目里**具体的**宪法，所以这些自定义条款和内置示例一样参与合规检查。

### Article VII & VIII：简单与反抽象

这两条配对，**对抗过度设计**：

```text
Section 7.3: Minimal Project Structure
- Maximum 3 projects for initial implementation
- Additional projects require documented justification

Section 8.1: Framework Trust
- Use framework features directly rather than wrapping them
```

**目的**：LLM 天然爱造抽象层，这两条逼它为每一层复杂度做交代。plan 模板的 "Phase -1 Gates" 直接强制这两条（见 03 篇）。

### Article IX：集成优先测试

优先真实环境测试，而非隔离单元测试：

```text
Tests MUST use realistic environments:
- Prefer real databases over mocks
- Use actual service instances over stubs
- Contract tests mandatory before implementation
```

**目的**：确保生成的代码在实践中能跑，而不只是理论上能跑。

## 4. 宪法如何被强制：Phase -1 门禁

光有原则没用，得有强制点。plan 模板把宪法条款操作化成**前置门禁**（见 03 篇的 Constitution Check）：

```markdown
### Phase -1: Pre-Implementation Gates

#### Simplicity Gate (Article VII)
- [ ] Using ≤3 projects?
- [ ] No future-proofing?

#### Anti-Abstraction Gate (Article VIII)
- [ ] Using framework directly?
- [ ] Single model representation?

#### Integration-First Gate (Article IX)
- [ ] Contracts defined?
- [ ] Contract tests written?
```

**机制**：LLM 不能跳过这些门禁往下做——要么通过，要么在 "Complexity Tracking" 段**文档化交代例外**。这相当于架构原则的"编译期检查"。

> 这就是 03 篇讲的"复杂度追踪表"的来源：违反宪法不是不行，是必须填表说清"为什么需要、更简单方案为什么不行"——让架构决策可问责。

## 5. 不可变原则的力量

宪法的力量在于**不可变性**。实现细节可以演进，但核心原则保持不变。这带来：

1. **跨时间一致**：今天和明年生成的代码遵循同样原则
2. **跨 LLM 一致**：不同 AI 模型产出架构兼容
3. **架构完整性**：每个 feature 强化而非削弱设计
4. **质量保证**：test-first / library-first / simplicity 确保可维护

> 这也是为什么 SDD 能放大而非取代开发者：把"机械翻译"自动化，把"原则守护"制度化。

## 6. 宪法的演进（有约束的演进）

原则不可变，但**应用可以演进**：

```text
Section 4.2: Amendment Process
Modifications to this constitution require:
- Explicit documentation of the rationale for change
- Review and approval by project maintainers
- Backwards compatibility assessment
```

- 修订要显式文档化理由
- 要项目维护者评审批准
- 要做向后兼容评估

宪法用带日期的修订记录展示自己的演进，证明原则可以基于实践经验精炼，同时保持稳定性。

## 7. 宪法背后的哲学

宪法不是规则书，是塑造 LLM 思考方式的哲学：

| 传统倾向 | 宪法主张 |
|---------|---------|
| Opacity（藏起来） | Observability（可观测，一切 CLI 可查） |
| Cleverness（炫技） | Simplicity（简单优先，复杂要证明必要） |
| Isolation（隔离测试） | Integration（真实环境测试） |
| Monolith（单体） | Modularity（每 feature 是边界清晰的库） |

> 把这些原则嵌进规范和计划过程，SDD 确保生成的代码不只是"能跑"，而是可维护、可测试、架构合理。宪法把 AI 从"代码生成器"变成"尊重并强化系统设计的架构伙伴"。

## 8. 实操：怎么写自己的宪法

用 `/speckit.constitution` 命令，在参数里给原则方向：

```
/speckit.constitution 本项目遵循"库优先"。所有功能先做成独立库。
严格 TDD。偏好函数式编程模式。代码质量、测试标准、UX 一致性、性能要求都要覆盖。
```

命令会读 `constitution-template.md`，把 `[PRINCIPLE_N_NAME]` 占位符替换成你命名的原则，生成 `.specify/memory/constitution.md`。

> 建议：宪法不必照搬"九条"示例。挑你团队真正在意的几条（比如 test-first、simplicity、security），写具体、写可检查。空泛的"高质量"等于没写。

## 9. 与 my-mall 的对照

my-mall 的 `AGENTS.md` 起到了类似宪法的角色——它规定"生产级标准、命名/配置/异常/日志规范不允许简化、技术选型原则"。两者对比：

| 维度 | my-mall AGENTS.md | Spec Kit constitution |
|------|-------------------|----------------------|
| 形态 | 项目根的工程约定 | `.specify/memory/` 下独立文件 |
| 强制点 | 人工 review + AI 遵循 | plan 模板的 Phase -1 门禁 + analyze 检查 |
| 内容 | 技术选型 + 架构 + 服务划分 + 文档索引 | 架构原则（test-first / simplicity 等） |
| 演进 | 直接改文档 | 修订要理由 + 审批 + 兼容评估 |

> 可借鉴：Spec Kit 把原则做成"门禁 + 例外追踪表"的机制化做法，比纯文档约定更硬。my-mall 的 `docs/standards/development-workflow.md` 里"核心/样板判定"也是类似思路的轻量版。

## 10. 小结

- 宪法是项目架构 DNA，不可变原则 + 可演进应用
- 九条是示例结构（library-first / CLI / test-first / 自定义三条 / simplicity / anti-abstraction / integration-first）
- 强制靠 plan 模板的 Phase -1 门禁 + 复杂度追踪表：违反要交代
- 不可变性带来跨时间、跨 LLM 的一致性
- 哲学：observability / simplicity / integration / modularity

下一篇 [05-customization-system.md](./05-customization-system.md) 看定制化：extensions/presets/bundles/workflows 怎么扩展和改造 Spec Kit。
