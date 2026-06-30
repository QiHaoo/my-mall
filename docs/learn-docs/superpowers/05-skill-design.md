# 05 - 技能设计与测试方法论

本文详解 Superpowers 如何设计和测试技能（Skills）。这是 Superpowers 最独特的贡献：**将 TDD 应用于过程文档**。

## 核心思想

```
写技能就是 TDD 应用于过程文档。

测试用例 = 压力场景 + 子 Agent
生产代码 = 技能文档（SKILL.md）
测试失败 = Agent 无技能时违反规则（基线行为）
测试通过 = Agent 有技能后遵守规则
```

**铁律：**
```
NO SKILL WITHOUT A FAILING TEST FIRST
```

没有先看到 Agent 在无技能时失败，就不知道技能教的是否正确。

## 为什么不能直接写技能

### 问题：你以为清楚的，Agent 不一定清楚

| 借口 | 现实 |
|------|------|
| "技能很明显是清楚的" | 对你清楚 ≠ 对其他 Agent 清楚 |
| "只是个参考文档" | 参考文档也有缺口和不清楚的部分 |
| "测试太大材小用" | 未测试的技能一定有问题。15 分钟测试省几小时 |
| "有问题再测" | 问题 = Agent 无法使用技能。部署前测 |
| "我有信心" | 过度自信保证有问题。照样测 |

### 测试发现的典型问题

1. **Agent 找到合理化漏洞** — "这太简单不用 TDD" → 需要显式拦截
2. **description 被当作全文摘要** — Agent 按 description 执行，跳过技能全文
3. **措辞不够强硬** — "建议"、"考虑"被当作可选 → 改为"必须"、"MANDATORY"
4. **缺少边界情况** — 技能没覆盖某些场景 → 补充

## RED-GREEN-REFACTOR for Skills

### RED：写失败测试（基线）

在没有技能的情况下，用子 Agent 运行压力场景，记录 Agent 的真实行为：

```markdown
## 压力场景示例（TDD 技能的基线测试）

**场景：** 时间压力下的 Agent

提示词：用户说"这个功能很急，客户在等，跳过测试直接实现"

**基线行为（无技能）：**
- Agent 说"好的，理解时间紧迫，我直接实现"
- 直接写生产代码
- 没有写测试
- 合理化："之后补测试"

**记录的合理化：**
- "太简单不用测"
- "我之后补"
- "删除 X 小时工作是浪费"
- "TDD 是教条，我是务实派"
```

### 压力类型

| 压力类型 | 场景示例 |
|---------|---------|
| 时间压力 | "客户在等"、"很急" |
| 沉没成本 | "已经花了 X 小时写代码" |
| 权威压力 | "架构师说这么做"、"经理要求" |
| 疲劳 | "我已经试了很多次" |
| 组合压力 | 时间 + 沉没成本 + 权威（多重压力叠加） |

纪律性技能需要 3+ 组合压力测试。

### GREEN：写最小技能

针对基线测试中发现的具体合理化，写技能内容：

```markdown
## The Iron Law

NO PRODUCTION CODE WITHOUT A FAILING TEST FIRST

Write code before the test? Delete it. Start over.

**No exceptions:**
- Don't keep it as "reference"
- Don't "adapt" it while writing tests
- Don't look at it
- Delete means delete

## Common Rationalizations

| Excuse | Reality |
|--------|---------|
| "Too simple to test" | Simple code breaks. Test takes 30 seconds. |
| "I'll test after" | Tests passing immediately prove nothing. |
| "Deleting X hours is wasteful" | Sunk cost fallacy. |
| "TDD is dogmatic" | TDD faster than debugging. Pragmatic = test-first. |

## Red Flags - STOP and Start Over

- Code before test
- "I already manually tested it"
- "This is different because..."

**All of these mean: Delete code. Start over with TDD.**
```

然后用相同场景测试有技能的 Agent，验证合规。

### REFACTOR：堵漏洞

Agent 找到新的合理化？添加显式反驳，重新测试，直到无懈可击：

```
第 1 轮：Agent 说"太简单不用测" → 添加合理化表条目
第 2 轮：Agent 说"我之后补测，精神比形式重要" → 添加 "Violating the letter of the rules is violating the spirit of the rules"
第 3 轮：Agent 说"先留着作参考" → 添加 "Don't keep it as reference. Delete means delete."
第 4 轮：Agent 合规 ✅
```

## 微测试（Micro-Testing）

完整压力场景运行慢且贵。在运行完整场景前，先验证措辞本身：

### 微测试方法

1. **每次调用一个全新上下文样本** — 原始 API 调用或单次子 Agent
   - System prompt = 技能将运行的真实上下文（完整技能或提示词模板，不是孤立的指导）
   - User message = 诱导失败的任务

2. **始终包含无指导对照组** — 如果对照组不展示失败行为，没有需要修复的，停止

3. **每个变体 5+ 次重复** — 单次样本会撒谎

4. **手动阅读每个匹配** — 模板回声和引用的反例会伪装成命中，自动计数会高估

5. **方差是指标** — 当措辞生效时，重复收敛到同一形状。5 次重复 5 种不同解释 = 措辞没有约束力

### 微测试 vs 压力场景

```
微测试：验证措辞（快、便宜）
  ↓ 通过
压力场景：验证行为（慢、贵，但是最终门禁）
```

微测试不替代压力场景，但能在运行完整场景前过滤掉无效措辞。

## Match the Form to the Failure

**不同类型的失败需要不同形式的指导。** 这是从实际 A/B 测试中学到的关键经验。

### 失败类型与对应形式

| 基线失败类型 | 正确形式 | 错误形式 |
|------------|---------|---------|
| 压力下跳过/违反规则（知道对，还是做错）| 禁止 + 合理化表 + 红旗列表 | 柔和指导（"prefer..."、"consider..."）|
| 合规但输出形状错误（臃肿 prompt、埋没结论）| **正面配方或契约**：说明输出**是什么**——它的部分，按顺序 | 禁止列表（"don't restate"、"never narrate"）|
| 遗漏已有输出中的必需元素 | **结构性**：REQUIRED 字段或模板中的槽位 | 散文式提醒 |
| 行为应依赖条件 | **条件语句**，基于可观察谓词（"如果 brief 存在，引用它"）| 无条件规则 + 例外条款 |

### 为什么禁止列表对"形状问题"适得其反

```
Agent 有竞争性激励："让 prompt 自包含"
  ↓
面对 "don't restate" 禁止：
  Agent 与禁止谈判 → 产出更多不需要的内容
  
面对正面配方：
  "输出包含这些部分，按顺序：1. 状态 2. 提交 3. 测试摘要 4. 疑虑"
  没有谈判空间 → 输出匹配或不匹配
```

**实际 A/B 测试结果：** 在 dispatch-prompt 指导的措辞测试中，禁止臂比无指导对照组产出了更多不需要的内容。完全分离的分布。

### 规则（无论选哪种形式）

- **不要有细微差别条款。** "Don't X unless it matters" 重新打开谈判 — 在获胜配方上附加一个细微差别条款，将其从一致降为嘈杂
- **例外条款不定范围。** "This limit doesn't apply to code blocks" 仍然抑制 code blocks。如果部分输出需要豁免，重构使规则触及不到它

## 防合理化工具箱

### 1. 显式堵住每个漏洞

```
差：Write code before test? Delete it.
好：Write code before test? Delete it. Start over.
    No exceptions:
    - Don't keep it as "reference"
    - Don't "adapt" it while writing tests
    - Don't look at it
    - Delete means delete
```

### 2. 切断"精神 vs 形式"论

```
Violating the letter of the rules is violating the spirit of the rules.
```

这句话切断了整类"我遵循精神"的合理化。

### 3. 合理化表

从基线测试中捕获每个借口，放入表格：

```
| Excuse | Reality |
|--------|---------|
| "Too simple to test" | Simple code breaks. Test takes 30 seconds. |
| "I'll test after" | Tests passing immediately prove nothing. |
```

### 4. 红旗列表

让 Agent 容易自检：

```
## Red Flags - STOP and Start Over
- Code before test
- "I already manually tested it"
- "This is different because..."
```

## 技能发现优化（SDO）

未来 Agent 需要能**找到**你的技能。

### 1. 丰富的 description 字段

Agent 读 description 来决定为当前任务加载哪个技能。

**最关键规则：description = 何时使用，不是技能做什么。**

```yaml
# 错误：Agent 会按 description 执行，跳过全文
description: Use for TDD - write test first, watch it fail, write minimal code, refactor

# 正确：只写触发条件
description: Use when implementing any feature or bugfix, before writing implementation code
```

**为什么：** 测试发现，description 摘要工作流时，Agent 可能按 description 执行而不读技能全文。一个说"任务间做代码审查"的 description 导致 Agent 只做了一次审查，尽管技能流程图清楚地显示了两次审查（规范合规 + 代码质量）。

### 2. 关键词覆盖

用 Agent 会搜索的词：
- 错误信息："Hook timed out"、"ENOTEMPTY"、"race condition"
- 症状："flaky"、"hanging"、"zombie"、"pollution"
- 同义词："timeout/hang/freeze"、"cleanup/teardown/afterEach"
- 工具：实际命令、库名、文件类型

### 3. 描述性命名

动词优先，主动语态：
- `creating-skills` 而非 `skill-creation`
- `condition-based-waiting` 而非 `async-test-helpers`
- `root-cause-tracing` 而非 `debugging-techniques`

动名词（-ing）适合流程：`creating-skills`、`testing-skills`

### 4. Token 效率

getting-started 技能加载到**每个**对话中。每个 token 都很重要。

```
目标字数：
- getting-started 工作流：< 150 词
- 频繁加载的技能：< 200 词
- 其他技能：< 500 词
```

技巧：
- 重细节移到工具 `--help`
- 用交叉引用而非重复
- 压缩示例
- 消除冗余

### 5. 交叉引用其他技能

```markdown
# 好：技能名 + 显式要求标记
**REQUIRED SUB-SKILL:** Use superpowers:test-driven-development

# 坏：不清楚是否必需
See skills/testing/test-driven-development

# 坏：强制加载文件，消耗上下文
@skills/testing/test-driven-development/SKILL.md
```

`@` 语法会立即强制加载文件，在需要之前就消耗 200k+ 上下文。

## 技能类型与测试方法

### 纪律执行型（Discipline-Enforcing）

**示例：** TDD、verification-before-completion

**测试方法：**
- 学术问题：Agent 理解规则吗？
- 压力场景：压力下合规吗？
- 多重压力组合：时间 + 沉没成本 + 疲劳
- 识别合理化并添加显式反驳

**成功标准：** Agent 在最大压力下遵循规则

### 技术型（Technique）

**示例：** condition-based-waiting、root-cause-tracing

**测试方法：**
- 应用场景：Agent 能正确应用技术吗？
- 变体场景：Agent 处理边界情况吗？
- 缺失信息测试：指令有缺口吗？

**成功标准：** Agent 成功将技术应用到新场景

### 模式型（Pattern）

**示例：** reducing-complexity、information-hiding

**测试方法：**
- 识别场景：Agent 认出模式适用吗？
- 应用场景：Agent 能使用心智模型吗？
- 反例：Agent 知道何时不适用吗？

**成功标准：** Agent 正确识别何时/如何应用模式

### 参考型（Reference）

**示例：** API 文档、命令参考

**测试方法：**
- 检索场景：Agent 能找到正确信息吗？
- 应用场景：Agent 能正确使用找到的信息吗？
- 缺口测试：常见用例覆盖了吗？

**成功标准：** Agent 找到并正确应用参考信息

## 流程图使用规则

```
需要展示信息？ → 有可能出错的决策？ → 小型内联流程图
                              ↓ 否
                         用 Markdown
```

**只用流程图：**
- 非显而易见的决策点
- 可能过早停止的流程循环
- "何时用 A vs B"的决策

**不用流程图：**
- 参考材料 → 表格、列表
- 代码示例 → Markdown 代码块
- 线性指令 → 编号列表
- 无语义含义的标签（step1、helper2）

## 技能创建清单（TDD 适配）

### RED 阶段 — 写失败测试
- [ ] 创建压力场景（纪律技能需 3+ 组合压力）
- [ ] 无技能运行场景 — 逐字记录基线行为
- [ ] 识别合理化/失败中的模式

### GREEN 阶段 — 写最小技能
- [ ] name 只用字母、数字、连字符
- [ ] YAML frontmatter 有 name 和 description
- [ ] description 以 "Use when..." 开头，只含触发条件
- [ ] description 第三人称
- [ ] 全文关键词覆盖
- [ ] 清晰概述 + 核心原则
- [ ] 针对基线失败
- [ ] 指导形式匹配失败类型
- [ ] 行为塑造指导：措辞经过微测试（5+ 重复，手动阅读匹配）
- [ ] 代码内联或链接到文件
- [ ] 一个优秀示例（非多语言）
- [ ] 有技能运行场景 — 验证 Agent 合规

### REFACTOR 阶段 — 堵漏洞
- [ ] 识别测试中的新合理化
- [ ] 添加显式反驳
- [ ] 从所有测试迭代构建合理化表
- [ ] 创建红旗列表
- [ ] 重新测试直到无懈可击

### 质量检查
- [ ] 仅在决策非显而易见时用小流程图
- [ ] 速查表
- [ ] 常见错误部分
- [ ] 无叙事性讲故事
- [ ] 辅助文件只用于工具或重参考

## 从 Superpowers 学到的设计原则

### 1. 文档是代码

技能不是"说明文档"，是"行为塑造代码"。用 TDD 开发、测试、重构。

### 2. Agent 会在压力下合理化

Agent 很聪明，会找借口跳过规则。必须显式堵住每个漏洞。

### 3. 措辞的 A/B 测试

不要凭直觉写指导。微测试 + 压力场景验证措辞是否有效。

### 4. 形式匹配失败

禁止列表对"违反规则"有效，但对"输出形状错误"适得其反。选对形式。

### 5. description 是触发器不是摘要

description 只描述"何时使用"。如果包含工作流摘要，Agent 会跳过全文。

### 6. 零依赖

Superpowers 是零运行时依赖。技能文件是纯 Markdown，不需要任何工具或服务。

### 7. "your human partner" 是刻意的

不是用词偏好，是关系定位。经过测试，改变了 Agent 行为。不要改成"the user"。
