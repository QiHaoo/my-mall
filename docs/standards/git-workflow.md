# Git 管理规范

---

## 一、分支策略

```
main            ← 稳定分支，始终保持可部署状态
 └── develop    ← 开发主线，所有功能合并到此
      ├── feature/xxx   ← 功能分支
      ├── fix/xxx       ← 修复分支
      └── hotfix/xxx    ← 紧急修复（从 main 拉出）
```

| 分支 | 来源 | 合并目标 | 生命周期 |
|------|------|---------|---------|
| `main` | — | — | 永久，只接受 develop 或 hotfix 合并 |
| `develop` | main | main | 永久，日常开发主线 |
| `feature/<name>` | develop | develop | 功能完成后删除 |
| `fix/<name>` | develop | develop | 修复完成后删除 |
| `hotfix/<name>` | main | main + develop | 修复完成后删除 |

---

## 二、分支命名规范

| 类型 | 格式 | 示例 |
|------|------|------|
| 功能开发 | `feature/<简短描述>` | `feature/product-crud` |
| 功能开发 | `feature/<模块>-<描述>` | `feature/order-create-flow` |
| Bug 修复 | `fix/<问题描述>` | `fix/cart-quantity-negative` |
| 紧急修复 | `hotfix/<问题描述>` | `hotfix/payment-callback-null` |
| 发布准备 | `release/<版本号>` | `release/v1.0.0` |

---

## 三、Commit 规范

### 格式

```
<type>(<scope>): <subject>

[可选 body]

[可选 footer]
```

### type 类型

| type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 重构（非新功能、非修复） |
| `docs` | 文档变更 |
| `style` | 代码格式（不影响逻辑） |
| `test` | 测试相关 |
| `chore` | 构建/工具/依赖变更 |
| `perf` | 性能优化 |
| `ci` | CI/CD 配置变更 |
| `revert` | 回滚 |

### scope 范围（对应服务模块）

`gateway` / `auth` / `member` / `product` / `search` / `cart` / `order` / `ware` / `coupon` / `seckill` / `third` / `admin` / `common`

### 示例

```
feat(product): 实现 SPU/SKU 商品模型 CRUD

- 新增 SpuController、SkuController
- 集成 MyBatis-Plus 分页查询
- 添加参数校验

Closes #23
```

```
fix(order): 修复库存回滚时数量为负的问题

库存扣减失败后回滚逻辑未考虑已扣减为 0 的情况，
增加数量下限校验。
```

```
chore: 升级 Spring Boot 至 3.4.2
```

```
docs(common): 补充统一返回结果类的使用说明
```

---

## 四、开发工作流

### 4.1 开始新功能

```bash
# 从 develop 拉取最新代码
git checkout develop
git pull origin develop

# 创建功能分支
git checkout -b feature/product-crud

# 开发过程中可以有多个小提交（WIP、调试等）
git add .
git commit -m "wip: 商品 CRUD 接口骨架"
git commit -m "wip: 补充分页查询"
git commit -m "wip: 修复参数校验"
```

### 4.2 完成事项后合并提交并推送

> **进度文档关联规则**：一个事项（如"接口设计"、"数据库表设计"）完成后，将过程中的多个小提交合并为一条提交，推送远程，再在 `docs/{服务名}/PROGRESS.md` 记录关联的提交 hash。未被合并推送的中间小提交不记录。

```bash
# 方式一：rebase 合并最近 N 个提交为一条（推荐）
git rebase -i HEAD~3
# 在编辑器里把第 2、3 行的 pick 改为 squash（或 s），保存
# 编辑合并后的 commit message，按规范写：
#   feat(product): 实现商品 CRUD 接口
# 保存退出

# 方式二：reset 后重新提交（更简单直接）
git reset --soft HEAD~3
git commit -m "feat(product): 实现商品 CRUD 接口"

# 推送到远程
git push -u origin feature/product-crud

# 记录提交 hash，更新 docs/mall-product/PROGRESS.md
git log -1 --format=%H   # 获取完整 hash，取前 7 位即可
```

### 4.3 合并到 develop

```bash
# 切回 develop，拉取最新
git checkout develop
git pull origin develop

# 合并功能分支（推荐 --no-ff 保留合并记录）
git merge --no-ff feature/product-crud

# 推送
git push origin develop

# 删除本地和远程功能分支
git branch -d feature/product-crud
git push origin --delete feature/product-crud
```
```

### 4.3 发布上线

```bash
# 从 develop 创建 release 分支
git checkout -b release/v1.0.0 develop

# 在 release 分支上做发布前修复（如有）
git commit -m "fix: 发布前修复 xx 问题"

# 合并到 main
git checkout main
git merge --no-ff release/v1.0.0

# 打标签
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin main --tags

# 合并回 develop（同步 release 上的修复）
git checkout develop
git merge --no-ff release/v1.0.0

# 删除 release 分支
git branch -d release/v1.0.0
```

### 4.4 紧急修复

```bash
# 从 main 拉出 hotfix
git checkout -b hotfix/payment-null main

# 修复并提交
git commit -m "hotfix(order): 修复支付回调空指针"

# 合并到 main
git checkout main
git merge --no-ff hotfix/payment-null
git tag -a v1.0.1 -m "Hotfix v1.0.1"
git push origin main --tags

# 同步到 develop
git checkout develop
git merge --no-ff hotfix/payment-null

# 删除 hotfix
git branch -d hotfix/payment-null
```

---

## 五、版本号规范（Semantic Versioning）

格式：`v<MAJOR>.<MINOR>.<PATCH>`

| 部分 | 变更时机 | 示例 |
|------|---------|------|
| MAJOR | 不兼容的 API 变更 | v1.0.0 → v2.0.0 |
| MINOR | 新增功能（向下兼容） | v1.0.0 → v1.1.0 |
| PATCH | Bug 修复（向下兼容） | v1.0.0 → v1.0.1 |

```bash
# 打标签
git tag -a v1.2.0 -m "Release v1.2.0: 商品搜索功能上线"
git push origin --tags

# 查看标签
git tag -l

# 删除错误标签
git tag -d v1.2.0
git push origin --delete v1.2.0
```

---

## 六、注意事项

1. **永远不要直接推送到 main** — main 只接受合并，不直接提交
2. **提交前先 pull** — 避免冲突，合并前先拉取远程最新
3. **小步提交** — 每个 commit 做一件事，便于回滚和 review
4. **不提交敏感信息** — 密码、密钥、Token 等绝不入库，用 Nacos 配置中心或环境变量管理
5. **不提交 IDE 配置** — `.idea/`、`*.iml` 等已在 `.gitignore` 中排除
6. **不提交编译产物** — `target/`、`*.class`、`*.jar` 等不入库
7. **删除已合并的远程分支** — 保持远程仓库整洁
8. **使用 `--no-ff` 合并** — 保留分支合并历史，方便回溯

---

## 七、常用命令速查

```bash
# 状态查看
git status                    # 当前状态
git log --oneline --graph     # 简洁提交历史图
git log --oneline -10         # 最近 10 条提交

# 分支操作
git branch                    # 查看本地分支
git branch -r                 # 查看远程分支
git branch -a                 # 查看所有分支
git checkout -b <branch>      # 创建并切换分支
git branch -d <branch>        # 删除已合并分支
git branch -D <branch>        # 强制删除分支

# 远程操作
git remote -v                 # 查看远程仓库
git fetch origin              # 拉取远程变更（不合并）
git pull origin develop       # 拉取并合并
git push origin <branch>      # 推送分支
git push origin --delete <branch>  # 删除远程分支

# 暂存
git stash                     # 暂存当前修改
git stash pop                 # 恢复暂存
git stash list                # 查看暂存列表

# 回滚
git revert <commit>           # 生成新 commit 撤销指定提交（安全）
git reset --soft HEAD~1       # 撤销最近一次提交（保留修改）

# 标签
git tag -a v1.0.0 -m "msg"   # 创建标签
git push origin --tags        # 推送所有标签
git tag -d v1.0.0             # 删除本地标签
```
