# 测试学习笔记

> 学完本系列，你能看懂项目 `docs/standards/testing-specification.md` 整套测试设计方案，并能独立写出符合规范的测试代码。

---

## 为什么学测试

很多人觉得测试是"额外工作"，但实际写下来会发现：

- **写测试逼着你把代码设计好**——如果一段代码很难测，说明它耦合太重（比如静态方法调用、字段注入、构造器里干坏事）
- **重构时有底气**——没有测试保护，谁敢动线上跑着的代码？
- **调试更快**——一个 1 秒跑完的单元测试，比每次手动重启服务 + curl 调试快 100 倍

本项目要求"生产级标准"，测试不是可选项。

---

## 学习路径

```
01 测试分层思想   ← 为什么分层、各层解决什么问题（先建立全局认知）
      ↓
02 单元测试核心   ← JUnit 5 + Mockito + AssertJ，最常用，必须吃透
      ↓
03 切片测试       ← @WebMvcTest，验证 Controller 层
      ↓
04 集成测试       ← WireMock，验证 Feign 跨服务链路
      ↓
e2e-testing-strategies.md  ← E2E 方案对比（已有，Testcontainers 选型）
      ↓
../ci/03-ci-testing-strategy.md  ← CI 中的测试与覆盖率门禁（已有，surefire/failsafe/jacoco）
```

> 前四篇是核心，看完就能看懂测试规范。E2E 和 CI 两篇已有文档，作为延伸阅读。

---

## 文档清单

| 文档 | 核心问题 | 重要度 |
|------|---------|--------|
| [01-testing-pyramid.md](./01-testing-pyramid.md) | 为什么测试要分层？各层职责是什么？ | ★★★ |
| [02-unit-testing.md](./02-unit-testing.md) | 单元测试怎么写？JUnit 5 / Mockito / AssertJ 怎么用？ | ★★★ |
| [03-slice-testing.md](./03-slice-testing.md) | @WebMvcTest 切片测试是什么？MockMvc 怎么断言？ | ★★★ |
| [04-integration-testing.md](./04-integration-testing.md) | WireMock 怎么模拟远程服务？集成测试和单元测试有什么区别？ | ★★☆ |
| [e2e-testing-strategies.md](./e2e-testing-strategies.md) | E2E 测试怎么选型？Testcontainers / 契约 / WireMock 怎么选？ | ★☆☆ |
| [../ci/03-ci-testing-strategy.md](../ci/03-ci-testing-strategy.md) | CI 怎么跑测试？覆盖率门禁怎么工作？ | ★★☆ |

---

## 与规范文档的关系

本系列是 `docs/standards/testing-specification.md` 的**前置学习材料**：

- **规范文档**告诉你「项目要求怎么写」——分层规则、命名、结构、提交清单
- **本学习笔记**告诉你「为什么这样要求」——背后的原理、工具怎么工作、怎么上手

建议学习顺序：先看本系列建立理解 → 再读规范文档对照项目要求。
