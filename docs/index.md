# My-Mall 项目文档

> 商城系统（my-mall）学习项目文档站 · 技术选型原则：最优 / 最流行 / 最值得学

本项目所有文档均按**生产级标准**编写，可在左侧导航或顶部搜索中查阅。

## 快速导航

- 📊 [项目进度](PROGRESS.md) — 已完成事项与关联提交
- 🏗️ [技术选型与架构设计](tech-stack-and-architecture-2026.md) — 选型理由、架构图、服务划分
- 📐 [开发规范](coding-standards.md) — 分层架构、异常体系、日志、命名规范
- 🗄️ [数据库表设计规范](table-design-specification.md) — 建表模板、审计字段、索引规范
- 🔌 [Controller 接口规范](controller-specification.md) — 参数校验、返回值、URL 设计
- 🧪 [后端测试规范](testing-specification.md) — 测试分层、AssertJ、命名规范

## 在本地预览

```bash
pip install mkdocs-material
mkdocs serve
# 浏览器访问 http://127.0.0.1:8000
```

> 平板/手机查看：推送到 `main` 分支后，GitHub Actions 会自动构建部署到
> **<https://qihao0o.github.io/my-mall/>**，移动端浏览器直接访问即可。
