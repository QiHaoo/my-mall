# 03 - 模板与质量约束：怎么把 LLM 产出"逼"向高质量

> 本文回答：spec/plan/tasks 模板各长什么样？模板凭什么能让 LLM 产出高质量规范？这套约束机制的设计精髓在哪？
> 主要来源：`templates/spec-template.md`、`templates/plan-template.md`、`templates/tasks-template.md`、`templates/constitution-template.md`，以及 `spec-driven.md` 的 "Template-Driven Quality" 章节。

## 1. 为什么模板是核心

很多人以为 Spec Kit 就是"几个斜杠命令 + 一堆 markdown 模板"，没什么技术含量。但 `spec-driven.md` 用一整节讲 **Template-Driven Quality**——模板的真正价值不是"格式好看"，而是**用结构约束 LLM 的输出行为**，把它从"创意写手"变成"纪律严明的规范工程师"。

> 原文：*"The templates act as sophisticated prompts that constrain the LLM's output in productive ways."*

模板本质上是**精心设计的 prompt**。下面拆解 7 个约束手法，每个都对应 LLM 的一个常见毛病。

## 2. spec-template.md：规范模板

位置：`templates/spec-template.md`。`/speckit.specify` 命令复制并填充它。

### 2.1 模板骨架

```markdown
# Feature Specification: [FEATURE NAME]
**Feature Branch**: `[###-feature-name]`
**Created**: [DATE]  **Status**: Draft
**Input**: User description: "$ARGUMENTS"

## User Scenarios & Testing *(mandatory)*
### User Story 1 - [Brief Title] (Priority: P1)
[Describe this user journey in plain language]
**Why this priority**: ...
**Independent Test**: ...
**Acceptance Scenarios**:
1. **Given** [state], **When** [action], **Then** [expected outcome]

### Edge Cases
- What happens when [boundary condition]?

## Requirements *(mandatory)*
### Functional Requirements
- **FR-001**: System MUST [specific capability]
- **FR-006**: System MUST authenticate via [NEEDS CLARIFICATION: auth method not specified - email/password, SSO, OAuth?]

### Key Entities *(include if feature involves data)*

## Success Criteria *(mandatory)*
### Measurable Outcomes
- **SC-001**: [Measurable metric]

## Assumptions
- [Assumption about target users]
```

### 2.2 模板里的 7 个约束手法

#### 手法 1：防止过早引入实现细节

模板显式指令：

```text
- ✅ Focus on WHAT users need and WHY
- ❌ Avoid HOW to implement (no tech stack, APIs, code structure)
```

LLM 天然爱跳到"用 React + Redux 实现"，模板把它拉回"用户需要实时更新数据"。**这保证规范与技术解耦**——换技术栈时规范不用动。

#### 手法 2：强制显式不确定性标记

```text
1. **Mark all ambiguities**: Use [NEEDS CLARIFICATION: specific question]
2. **Don't guess**: If the prompt doesn't specify something, mark it
```

LLM 的通病是"合理但可能错的假设"——看到"登录系统"就默认 email/password。模板逼它写成 `[NEEDS CLARIFICATION: auth method not specified - email/password, SSO, OAuth?]`，把猜测变成显式问题。

> 这就是 `/speckit.clarify` 命令要逐条消灭的东西。

#### 手法 3：用检查清单做"结构化自审"

模板带 `### Requirement Completeness` 之类清单：

```markdown
- [ ] No [NEEDS CLARIFICATION] markers remain
- [ ] Requirements are testable and unambiguous
- [ ] Success criteria are measurable
```

这等于给 LLM 一个 QA 框架，逼它系统自检，而不是写完就交。

#### 手法 4：用户故事必须独立可测

模板注释强调：

```text
Each user story/journey must be INDEPENDENTLY TESTABLE
- if you implement just ONE of them, you should still have a viable MVP
Assign priorities (P1, P2, P3...)
```

这阻止 LLM 把功能揉成一团。每个故事都是能独立开发、测试、部署、演示的切片。**P1 单独做出来就是 MVP**。

#### 手法 5：验收用 Given/When/Then

```text
1. **Given** [initial state], **When** [action], **Then** [expected outcome]
```

强制 BDD 风格的验收场景，让需求"可测"——这同时是后续测试场景的来源（SDD 里测试是规范的一部分）。

#### 手法 6：成功标准必须可度量

```text
- **SC-001**: [Measurable metric, e.g., "Users can complete account creation in under 2 minutes"]
```

禁止"系统应快速响应"这种废话，必须有数字。

#### 手法 7：假设要显式列出

`## Assumptions` 段逼 LLM 把"默认选了什么"写出来（如"假设用户有稳定网络""移动端不在 v1 范围"），避免隐含决策。

## 3. plan-template.md：实现计划模板

位置：`templates/plan-template.md`。`/speckit.plan` 命令填充它。

### 3.1 骨架与重点段

```markdown
# Implementation Plan: [FEATURE]
**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

## Summary
[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context
**Language/Version**: ...  **Primary Dependencies**: ...
**Storage**: ...  **Testing**: ...  **Target Platform**: ...
**Performance Goals**: ...  **Constraints**: ...  **Scale/Scope**: ...

## Constitution Check
*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*
[Gates determined based on constitution file]

## Project Structure
### Documentation (this feature)
specs/[###-feature]/
├── plan.md / research.md / data-model.md / quickstart.md / contracts/ / tasks.md

### Source Code (repository root)
# Option 1: Single project (DEFAULT)   src/ models/ services/ cli/ lib/  tests/...
# Option 2: Web application             backend/src/... frontend/src/...
# Option 3: Mobile + API                api/... ios/ or android/...

## Complexity Tracking
> Fill ONLY if Constitution Check has violations that must be justified
| Violation | Why Needed | Simpler Alternative Rejected Because |
```

### 3.2 plan 模板的约束手法

#### 宪法门禁（Constitution Check）

```text
*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*
```

plan 模板把宪法检查做成**前置门禁**——技术方案必须先过宪法才能往下做。具体门禁由项目的 constitution.md 决定（见 04 篇）。

#### 层级化信息管理

模板强调：

```text
**IMPORTANT**: This implementation plan should remain high-level and readable.
Any code samples, detailed algorithms, or extensive technical specifications
must be placed in the appropriate `implementation-details/` file
```

防止规范变成"无法阅读的代码堆"。复杂度抽到 `data-model.md`、`contracts/` 等独立文件，主文档保持可读。

#### 复杂度追踪表（Complexity Tracking）

```text
> Fill ONLY if Constitution Check has violations that must be justified
| Violation | Why Needed | Simpler Alternative Rejected Because |
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
```

如果技术方案违反了宪法（比如用了 4 个 project 而宪法限 3 个），**必须填表交代**：为什么需要、更简单的方案为什么不行。这让架构决策可问责。

#### 项目结构多选一

模板给 3 种结构选项（单项目 / Web / Mobile+API），标 `[REMOVE IF UNUSED]`，逼 LLM 选定一种并填真实路径，而不是含糊带过。

## 4. tasks-template.md：任务清单模板

位置：`templates/tasks-template.md`。`/speckit.tasks` 命令填充它。

### 4.1 骨架

```markdown
# Tasks: [FEATURE NAME]
**Prerequisites**: plan.md (required), spec.md, research.md, data-model.md, contracts/
**Organization**: Tasks are grouped by user story
## Format: `[ID] [P?] [Story] Description`
- **[P]**: Can run in parallel (different files, no dependencies)

## Phase 1: Setup (Shared Infrastructure)
- [ ] T001 Create project structure per implementation plan
- [ ] T003 [P] Configure linting and formatting tools

## Phase 2: Foundational (Blocking Prerequisites)
**⚠️ CRITICAL**: No user story work can begin until this phase is complete
- [ ] T004 Setup database schema and migrations framework
**Checkpoint**: Foundation ready

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP
### Tests for User Story 1 (OPTIONAL)
> NOTE: Write these tests FIRST, ensure they FAIL before implementation
- [ ] T010 [P] [US1] Contract test for [endpoint] in tests/contract/test_[name].py
### Implementation for User Story 1
- [ ] T012 [P] [US1] Create [Entity1] model in src/models/[entity1].py
**Checkpoint**: User Story 1 should be fully functional and testable independently

## Phase N: Polish & Cross-Cutting Concerns
## Dependencies & Execution Order
```

### 4.2 tasks 模板的约束手法

#### 测试优先（Test-First Thinking）

```text
### File Creation Order
1. Create `contracts/` with API specifications
2. Create test files in order: contract → integration → e2e → unit
3. Create source files to make tests pass
```

模板强制"先契约、再测试、最后实现"的顺序。LLM 不能上来就写源码，得先想清楚可测性。

#### 按用户故事分组 + Phase 化

任务按 user story 分组，每个 story 一个 phase，带 `🎯 MVP` 标记。这把"独立可交付"从 spec 一路落到 task 层：

```text
- **Setup (Phase 1)**: 无依赖，立即开始
- **Foundational (Phase 2)**: 依赖 Setup，BLOCKS 所有 story
- **User Stories (Phase 3+)**: 都依赖 Foundational；story 间可并行
- **Polish (Final)**: 依赖所有 story 完成
```

#### 并行标记 [P] 与依赖

```text
- **[P]**: Can run in parallel (different files, no dependencies)
- Models before services, services before endpoints
```

`[P]` 标记哪些任务可并行（不同文件、无依赖）。模板还给出 within-story 的依赖规则，避免 LLM 乱排顺序。

#### Checkpoint 机制

每个 phase 末尾有 `**Checkpoint**`，要求阶段末验证。这是给"增量交付"设的硬停点。

#### 防止投机性功能

```text
- [ ] No speculative or "might need" features
- [ ] All phases have clear prerequisites and deliverables
```

阻止 LLM 加"以后可能有用"的功能——每个功能必须追溯到具体 user story。

### 4.3 任务的执行策略

模板还给出三种团队策略：

```text
MVP First: Setup → Foundational → User Story 1 → STOP VALIDATE
Incremental Delivery: 每个 story 加完都独立测试 + 部署
Parallel Team Strategy: Foundational 后多人并行做不同 story
```

## 5. constitution-template.md：宪法模板

位置：`templates/constitution-template.md`。`/speckit.constitution` 填充。详解见 04 篇，这里只点它的约束作用：

- 用 `[PRINCIPLE_N_NAME]` 占位符逼你显式命名每条原则
- 每条原则带描述（如"Library-First: 每个功能先做成独立库"）
- 末尾有 `## Governance` 段，规定宪法至上、修订要文档化审批

> 宪法模板的特殊性：它是**唯一允许"不可变"的模板**——其他模板可被 preset 覆盖，宪法是项目自己定的治理根基。

## 6. 约束的合力效应

`spec-driven.md` 总结了这堆约束叠加后的效果：

| 目标 | 由哪个手法保证 |
|------|--------------|
| **Complete（完整）** | 检查清单确保不漏 |
| **Unambiguous（无歧义）** | `[NEEDS CLARIFICATION]` 强制显式化 |
| **Testable（可测）** | Given/When/Then + 测试优先 + 验收场景 |
| **Maintainable（可维护）** | 层级化信息管理 + 抽象层级正确 |
| **Implementable（可实现）** | 清晰 phase + 具体交付物 + 依赖明确 |
| **Anti-over-engineering（防过度设计）** | 宪法门禁 + 复杂度追踪表 + 禁投机功能 |

## 7. 一个对比直觉

同样是"让 AI 写规范"，两种做法：

**做法 A（裸 prompt）**：
```
帮我写一个相册应用的需求文档
```
→ LLM 大概率：混入技术栈、假设一堆东西、写一堆不可测的"快速/易用"、漏掉边界情况。

**做法 B（Spec Kit 模板）**：
```
/speckit.specify Build an application that can help me organize my photos...
```
→ 模板强制：用户故事带优先级且独立可测、验收用 Given/When/Then、模糊处标 `[NEEDS CLARIFICATION]`、成功标准带数字、假设显式列出。

> 差别不在"AI 更聪明"，而在**模板把质量要求固化成了结构**。人不用每次提醒"别忘了边界情况"，模板替你提醒。

## 8. 模板的可覆盖性（伏笔）

这些模板都在 `.specify/templates/` 下，是 Spec Kit **core** 的默认值。它们可以被覆盖：

```text
优先级（高 → 低）:
1. 项目本地覆盖   .specify/templates/overrides/
2. preset        .specify/presets/templates/
3. extension     .specify/extensions/templates/
4. Spec Kit core .specify/templates/   ← 上面讲的就是这层
```

> 这意味着组织可以定制自己的 spec/plan/tasks 模板（比如强制加合规段、改术语、本地化语言）。机制见 05 篇。

## 9. 小结

- Spec Kit 模板不是"格式模板"，是**约束 LLM 输出行为的精密 prompt**
- 7 个手法针对 LLM 的 7 个通病：过早实现、隐式假设、不自检、功能揉团、不可测验收、废话标准、隐含决策
- plan 模板用宪法门禁 + 复杂度追踪表把架构纪律前置
- tasks 模板用测试优先 + Phase 化 + [P] 并行 + Checkpoint 把"可执行"落到地
- 这些模板都在 core 层，可被 preset/extension/项目覆盖

下一篇 [04-constitution.md](./04-constitution.md) 深入宪法：九条条款、不可变原则、怎么治理架构纪律。
