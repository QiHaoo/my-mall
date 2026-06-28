# mall-product 进度

> 本文档只记录**已完成**和**当前进行**的事项，不列未来计划。
> 完成一个事项后，将过程中多个小提交合并为一条提交并推送，在此记录关联提交 hash。

---

## 已完成

| 时间 | 事项 | 关联提交 | 说明 |
|------|------|---------|------|
| 2026-06-22 | 商品分类 CRUD 实现 | `699f1b7` | CategoryController/Service（树查询、新增、修改、批量删除、拖拽排序）、DTO/VO、@WebMvcTest 单元测试、category-management.md 文档 |
| 2026-06-24 | 品牌管理实现 | `e53fe25` | BrandController/Service（分页查询、详情、新增、修改、状态更新、删除、分类下品牌）；DTO/VO 5 个；mall-common 新增校验分组 Create/Update + 品牌错误码 53001-53005；Brand.showStatus Byte→Integer；product 补 MyBatisConfig(@MapperScan)；BrandServiceTest + BrandControllerTest 全绿（58 测试通过）；brand-management.md 设计文档 + product-brand-demo.http |
| 2026-06-28 | 品牌管理接口设计重构 v1.1 | `ee9e06c` | 配合前端「关联分类独立弹窗」设计，将关联分类管理从品牌新增/修改接口中剥离，改为独立接口（查询/新增/移除）；新增批量删除品牌接口；DTO/VO 扩展至 8 个；错误码补充 53006-53008；冗余字段同步改由 `CategoryBrandRelationService` 内部方法 `updateBrandName`/`updateCatelogName` 承担。**仅文档调整，代码实现待同步**（BrandServiceImpl 需移除 categoryIds 全量覆盖逻辑、新增 CategoryBrandRelationService/Controller 等）。 |
| 2026-06-28 | 分类与品牌管理接口实现同步 v1.1 | - | 按最新设计文档同步后端实现：分类字段统一 catId、show_status 业务删除、批量删除增加 SPU 引用检查与关联表冗余名同步；品牌表单剥离 categoryIds、新增批量删除与独立关联管理接口；ResultCode 补充 53006-53008；CategoryServiceTest/BrandServiceTest/CategoryControllerTest/BrandControllerTest 全绿（28 个用例通过）。 |

---

## 关键记录

> 记录开发过程中的重要决策、踩坑、技术选型理由等。

- **~~品牌-分类关联同步用 diff 而非"全删再插"~~**（⚠️ v1.1 已废弃）：v1.1 设计改为关联分类独立单条增删，不再有"全量覆盖/diff"逻辑。生产彻底解法建议唯一索引改为 `uk_brand_catelog(brand_id, catelog_id, is_deleted)`。
- **~~修改时 categoryIds 语义~~**（⚠️ v1.1 已废弃）：v1.1 设计下品牌修改接口不处理 `categoryIds`，关联分类由独立接口维护，不再有 null/空数组二义性问题。
- **MyBatis-Plus 单元测试 lambda 缓存坑**：`LambdaUpdateWrapper.set()` 构建时立即解析 lambda 列名（query wrapper 的 eq/like 延迟到生成 SQL 时），纯单元测试无 Spring 上下文会抛 "can not find lambda cache"。BrandServiceTest 用 `@BeforeAll` + `TableInfoHelper.initTableInfo` 预热 TableInfo 缓存解决。后续 service 若用 update wrapper 的 `set()` 同样需要预热。
- **product 模块补 MyBatisConfig**：CLAUDE.md 要求 @MapperScan 独立于启动类（否则 @WebMvcTest 切片测试缺 DataSource 失败），但 product 模块此前缺失，本次补齐（与 coupon/member/oss 对齐）。
