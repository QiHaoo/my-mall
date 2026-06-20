# AI 辅助开发流程规范

> 基于「轻量 Spec + TDD」范式，平衡开发效率与代码质量。
> 核心逻辑严格 TDD，样板代码按需生成，规范轻量不堆砌。

---

## 一、流程总览

```
功能需求
   │
   ▼
【阶段1：规范】理清意图，写简短规范，判定核心/样板
   │
   ▼
【阶段2：实现】核心逻辑走 TDD，样板代码直接生成
   │
   ▼
【阶段3：验证】跑测试、查编译、清 TODO
   │
   ▼
【收尾】AI 审查 → 提交代码 → 更新进度文档
```

**设计原则**：
- **Skill 是工具不是枷锁** — 简单功能可跳过部分阶段（如样板 CRUD 无需 brainstorming）
- **规范要轻** — 每个功能的规范控制在 30 行以内，避免文档工程
- **测试聚焦核心** — 不为覆盖率写无意义测试，聚焦业务逻辑正确性

---

## 二、核心逻辑 vs 样板代码判定

开发任何功能前，先判定它属于哪一类，决定走哪条实现路径。

| 分类 | 判定标准 | 实现方式 | 是否写测试 |
|------|---------|---------|-----------|
| **核心逻辑** | 业务规则、状态流转、金额计算、库存扣减、并发控制、分布式事务 | 严格 TDD（RED→GREEN→REFACTOR） | ✅ 必须 |
| **样板代码** | 单表 CRUD、DTO/VO 转换、Mapper 接口、简单查询 | MyBatis-Plus 生成 + 手写 Controller | 按需 |

**各服务的典型分类参考**：

| 服务 | 核心逻辑（TDD） | 样板代码（生成） |
|------|----------------|-----------------|
| mall-product | 商品上下架状态流转、SKU 价格计算 | SPU/SKU/分类/品牌/属性 CRUD |
| mall-order | 下单流程、状态机流转、超时取消 | 订单表 CRUD、订单项 CRUD |
| mall-ware | 库存扣减/回滚、库存预警 | 仓库表 CRUD、库存表 CRUD |
| mall-coupon | 优惠券领取规则、满减计算 | 优惠券 CRUD、活动 CRUD |
| mall-seckill | 秒杀预减库存、防超卖、削峰 | 秒杀活动 CRUD |
| mall-member | 积分规则、等级计算 | 会员 CRUD、地址 CRUD |
| mall-auth | OAuth2 授权流程、JWT 签发校验 | — |
| mall-cart | 购物车合并、选中状态计算 | — |

---

## 三、阶段详解

### 阶段1：规范

**目标**：理清功能意图，写一份简短规范，判定核心/样板。

**做法**：
1. 用 `brainstorming` skill 理解需求（一次一个问题，探索方案）
2. 在 `docs/{服务名}/specs/{功能名}.md` 写简短规范（模板见文末）
3. 规范内容：功能描述 + 接口契约 + 核心逻辑要点 + 测试要点
4. 判定该功能是核心逻辑还是样板代码

**何时可跳过**：纯样板 CRUD（用代码生成器直接生成）无需写规范，按 `docs/mybatis-plus-codegen-guide.md` 执行即可。

### 阶段2：实现

**目标**：按规范实现代码，核心逻辑走 TDD，样板代码直接生成。

**核心逻辑路径（TDD 三步循环）**：

用 `test-driven-development` skill，严格遵循：

```
RED    写一个失败的测试，运行确认它失败（且因正确原因失败）
  │
  ▼
GREEN  写最小的实现代码让测试通过，不多写
  │
  ▼
REFACTOR  在测试保持绿色的前提下重构代码
  │
  └─→ 重复 RED→GREEN→REFACTOR，直到功能完整
```

> **铁律**：没有失败的测试，就不写生产代码。

**样板代码路径**：
- 按 `docs/mybatis-plus-codegen-guide.md` 用代码生成器生成 Entity/Mapper/Service
- 手写 Controller（含参数校验、分页查询等）
- 不强制写单元测试

### 阶段3：验证

**目标**：确认代码真正可用，不凭感觉声称完成。

用 `verification-before-completion` skill：
1. 运行单元测试，确认全部通过
2. 运行集成测试（如有）
3. 确认编译无误（`mvn compile`）
4. 检查无遗留 TODO / FIXME
5. 检查无未捕获异常、无硬编码配置

> **铁律**：没有新鲜的验证证据，就不能声称完成。

### 收尾

**目标**：AI 审查质量，提交代码，记录进度。

1. **AI 代码审查**：用 `requesting-code-review` skill 派发审查
   - 审查维度：规范合规性 + 代码质量
   - 用 `receiving-code-review` skill 处理反馈（先理解→验证→评估→实现，不盲从）
2. **提交代码**：遵循 `docs/git-workflow.md` 的 squash 规范
   - 过程中的多个小提交合并为一条提交
   - 推送到远程
3. **更新进度文档**：在 `docs/{服务名}/PROGRESS.md` 记录关联提交 hash

---

## 四、配套 Skill

以下 skill 已通过 superpowers 插件安装，开发时按阶段触发使用：

| 阶段 | Skill | 触发时机 | 作用 |
|------|-------|---------|------|
| 规范 | `brainstorming` | 开始任何创造性工作前 | 理解意图，探索方案，形成设计 |
| 实现 | `test-driven-development` | 实现核心逻辑功能/bugfix 前 | RED-GREEN-REFACTOR 循环 |
| 调试 | `systematic-debugging` | 遇到 bug/测试失败时 | 四阶段根因调查与修复 |
| 验证 | `verification-before-completion` | 声称工作完成前 | 运行验证命令，确认输出 |
| 审查 | `requesting-code-review` | 完成任务/重大功能后 | 派发 AI 代码审查 |
| 审查 | `receiving-code-review` | 收到审查反馈时 | 理解→验证→评估→实现 |
| 收尾 | `finishing-a-development-branch` | 实现完成、测试通过后 | 合并/提交/清理 |

**本流程不使用的 skill**（对学习项目过重）：
- `using-git-worktrees` — 学习项目无需 worktree 隔离，直接在功能分支开发
- `subagent-driven-development` / `executing-plans` — 多 subagent 执行过重，单会话直接实现即可
- `writing-plans` — 轻量方案用简短规范替代详尽计划文档

---

## 五、与其他规范的衔接

| 规范文档 | 衔接点 |
|---------|--------|
| `docs/git-workflow.md` | 收尾阶段的提交遵循 squash 规范和 commit message 格式 |
| `docs/mybatis-plus-codegen-guide.md` | 样板代码路径的生成与包结构遵循此规范 |
| `docs/PROGRESS.md` + `docs/{服务名}/PROGRESS.md` | 收尾阶段更新进度，记录关联提交 hash |
| `AGENTS.md` | 本文档在项目文档索引中登记 |

---

## 六、规范文档模板

开发核心逻辑功能时，在 `docs/{服务名}/specs/{功能名}.md` 创建规范：

```markdown
# {功能名} 规范

## 功能描述
{一段话描述这个功能做什么}

## 接口契约
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /xxx | ... |

## 核心逻辑要点
- {业务规则1}
- {业务规则2}

## 测试要点
- {边界场景1}
- {边界场景2}

## 分类
[x] 核心逻辑（TDD）  [ ] 样板代码（生成）
```

> 规范控制在 30 行以内，写清意图即可，不堆砌细节。

---

## 七、完整开发示例（参考）

以「mall-order 下单流程」为例：

1. **规范阶段**：在 `docs/mall-order/specs/create-order.md` 写规范
   - 功能描述：用户提交订单，扣减库存，生成订单号，写入订单表
   - 核心逻辑：库存预扣 → 生成订单 → 发送延迟消息（超时取消）
   - 分类：核心逻辑（TDD）

2. **实现阶段**：走 TDD
   - RED：写 `OrderServiceTest.createOrder_shouldDeductStock()`，运行确认失败
   - GREEN：写最小实现让测试通过
   - REFACTOR：抽取库存扣减逻辑，保持测试绿色
   - 重复，覆盖：库存不足、并发下单、超时取消等场景

3. **验证阶段**：运行 `mvn test`，确认全绿

4. **收尾阶段**：
   - AI 审查（规范合规 + 代码质量）
   - squash 提交：`feat(order): 实现下单流程（库存预扣+延迟取消）`
   - 推送后更新 `docs/mall-order/PROGRESS.md` 记录 hash
