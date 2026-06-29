<!-- PR 模板：填写以下信息帮助 Reviewer 快速理解变更 -->

## 变更说明

<!-- 简述本次 PR 做了什么，为什么做 -->

## 变更类型

- [ ] 新功能（feat）
- [ ] Bug 修复（fix）
- [ ] 重构（refactor）
- [ ] 文档（docs）
- [ ] 配置（chore）
- [ ] 测试（test）

## 关联 Issue

<!-- 关联的 Issue 编号，如 Closes #123 -->

## 测试验证

<!-- 描述如何验证本次变更 -->
<!-- 如：已跑 `mvn test` / 已联调验证 / 已写单元测试 -->

- [ ] 单元测试通过
- [ ] 接口联调验证（如涉及）

## 检查清单

- [ ] 代码遵循 [编码规范](../docs/standards/coding-standards.md)
- [ ] Controller 接口遵循 [Controller 规范](../docs/standards/controller-specification.md)
- [ ] 敏感信息（密码/密钥/Token）未硬编码、未入日志
- [ ] SQL 使用 `#{}` 占位符，无 `${}` 拼接用户输入
- [ ] 新增接口配置了权限校验（如涉及鉴权）
- [ ] 进度文档已更新（如涉及功能变更）
