# 持续集成学习笔记

> 目标读者：不熟悉持续集成、Docker 也不太熟的开发者。看完本系列，能读懂 my-mall 项目的 CI 设计方案（`docs/standards/ci-cd/` 目录下的 6 篇设计文档），并理解 `.github/workflows/` 配置背后的工程考量，而不只是照抄。

## 为什么单独学 CI

持续集成（CI）是现代软件工程的基建。不理解它，就看不懂项目 `.github/workflows/` 下的配置文件在做什么、为什么这么编排，出了问题更不知道从哪查起。

本项目已有 GitHub Actions 语法笔记（[docs/learn-docs/github/05-github-actions.md](../github/05-github-actions.md)），覆盖了 workflow / job / step / action 这类「语法本身」。本系列不再重复 Actions 语法，而是聚焦**持续集成的工程实践视角**——为什么要做 CI、CI 里该做什么、每一步怎么落地、做了之后能解决什么问题。

Docker 是 CI 镜像构建的前置知识：项目最终要把 14 个后端服务 + 1 个前端打成 Docker 镜像推到 Harbor。如果对镜像、容器、Dockerfile 多阶段构建没有概念，直接看 `docker-publish.md` 设计文档会一头雾水。所以本系列从容器基础讲起，再进入 CI 核心实践，最后回到项目本身的设计文档。

## 学习路径

```text
第 1 阶段：打基础
  01-Docker 容器基础 → 02-CI 核心概念

第 2 阶段：CI 核心实践
  03-测试与质量门禁 → 04-构建与镜像仓库

第 3 阶段：发布与落地
  05-发布与版本管理 → 06-读懂本项目 CI 方案
```

- **第 1 阶段**建立两块地基：Docker（镜像怎么来、怎么跑）和 CI（它到底是什么、解决什么问题）。
- **第 2 阶段**进入 CI 流水线的两个核心环节：测试门禁（保证质量）和构建发布（产出可部署物）。
- **第 3 阶段**收口到工程化：版本怎么管、Release 怎么发，最后把所有概念串起来逐个解读项目设计文档。

如果时间有限，至少走完 `01 → 02 → 06` 这条主线，能建立最小闭环认知；`03 → 04 → 05` 是把闭环填实。

## 文档清单

| # | 文档 | 核心内容 | 优先级 |
|---|------|---------|--------|
| - | [README.md（本文）](./README.md) | 学习地图、路径、清单 | — |
| 01 | [01-docker-basics.md](./01-docker-basics.md) | 镜像/容器/Dockerfile/构建/运行/多阶段构建 | 必读 |
| 02 | [02-ci-fundamentals.md](./02-ci-fundamentals.md) | CI/CD 概念、CI 解决什么问题、流水线设计思路 | 必读 |
| 03 | [03-ci-testing-strategy.md](./03-ci-testing-strategy.md) | 测试分层、覆盖率门禁、快速失败原则 | 必读 |
| 04 | [04-ci-build-and-images.md](./04-ci-build-and-images.md) | 构建产物、Docker 镜像、镜像仓库（Harbor）、tag 策略 | 必读 |
| 05 | [05-ci-release-workflow.md](./05-ci-release-workflow.md) | 版本号、Git tag、GitHub Release、Changelog | 推荐 |
| 06 | [06-project-ci-walkthrough.md](./06-project-ci-walkthrough.md) | 逐个解读 `docs/standards/ci-cd/` 下的设计文档 | 必读 |

> 标「必读」的是建立 CI 认知闭环的关键节点；06 是总收尾，把前 5 篇的概念全部落到项目设计文档上。标「推荐」的 05 涉及发布与版本管理，对理解完整 CI/CD 链路有帮助。

## 前置知识

| 知识点 | 需要程度 | 学习资源 |
|--------|---------|---------|
| Git 基本操作 | 必须 | [docs/learn-docs/github/01-github-fundamentals.md](../github/01-github-fundamentals.md) |
| GitHub Actions 语法 | 推荐（06 会引用） | [docs/learn-docs/github/05-github-actions.md](../github/05-github-actions.md) |
| Maven 基础 | 有则更好 | — |
| 命令行基础 | 必须 | — |

> 本系列假设你已经会用 Git 提交、推送、切分支，能在终端执行命令。GitHub Actions 语法不是硬性前置——02 会从「CI 是什么」讲起——但 06 解读项目设计文档时会频繁引用 Actions 概念，提前看一遍 05 会更顺畅。

## 与项目的关系

本系列学习笔记与项目设计文档（`docs/standards/ci-cd/`）的对应关系：

| 学习文档 | 对应的项目设计文档 | 关系 |
|---------|------------------|------|
| 01-Docker 容器基础 | [docs/standards/ci-cd/docker-publish.md](../../standards/ci-cd/docker-publish.md) | 前置知识，讲清 Dockerfile 多阶段构建原理 |
| 02-CI 核心概念 | [docs/standards/ci-cd/overview.md](../../standards/ci-cd/overview.md) | 讲清 CI/CD 边界、编排器设计思路 |
| 03-测试与质量门禁 | [docs/standards/ci-cd/backend-ci.md](../../standards/ci-cd/backend-ci.md) + [docs/standards/testing-specification.md](../../standards/testing-specification.md) | 讲清测试分层、覆盖率门禁为什么这么做 |
| 04-构建与镜像仓库 | [docs/standards/ci-cd/docker-publish.md](../../standards/ci-cd/docker-publish.md) + [docs/standards/ci-cd/harbor-setup.md](../../standards/ci-cd/harbor-setup.md) | 讲清镜像构建、Harbor、tag 策略 |
| 05-发布与版本管理 | [docs/standards/ci-cd/release-and-protection.md](../../standards/ci-cd/release-and-protection.md) | 讲清版本号、Release、分支保护 |
| 06-读懂本项目 CI 方案 | [docs/standards/ci-cd/](../../standards/ci-cd/) 全部 | 串联所有概念，逐个解读设计文档 |

> 设计文档（`docs/standards/ci-cd/`）回答「项目要怎么做」，偏规范与落地；本系列学习笔记回答「为什么这么做、底层概念是什么」，偏认知与原理。两者互补：先看学习笔记建立概念，再对照设计文档看项目决策。

## 阅读约定

- **概念讲解为主，配本项目真实配置举例**：每个概念先讲清楚是什么 / 为什么 / 怎么做，再用项目的真实配置或命令举例
- 命令行示例中 `$` 开头的是用户输入，其余是输出
- 引用项目文件时给出相对路径，方便对照源码
- 重点是「**为什么这么做**」，不只是「怎么做」——知其然更知其所以然

## 参考资源

- [Docker 官方文档](https://docs.docker.com/) — 镜像、容器、Dockerfile、多阶段构建权威说明
- [GitHub Actions 文档](https://docs.github.com/actions) — workflow 语法速查（语法细节查这里，本系列不重复）
- [《持续交付》](https://book.douban.com/subject/4255324/) — Jez Humble 经典著作，讲部署流水线与发布工程
- [Google SRE Book — Release Engineering](https://sre.google/sre-book/release-engineering/) — 持续集成与发布工程的生产实践视角
