# 数据库表设计规范

> 生产级 DDL 约定，所有新建表必须遵循。改造历史表（谷粒商城迁移表）时也按此对齐。
> ORM 层的实体规范见 [编码规范](./coding-standards.md)，生成器用法见 [MyBatis-Plus 代码生成规范](./mybatis-plus-codegen-guide.md)。

---

## 一、建表模板

每张业务表必须包含以下结构（以 `pms_brand` 为例）：

```sql
CREATE TABLE pms_brand (
    id              BIGINT       NOT NULL                COMMENT '主键',
    name            VARCHAR(64)  NOT NULL                COMMENT '品牌名',
    logo            VARCHAR(512)         DEFAULT NULL    COMMENT '品牌 logo URL',
    show_status     TINYINT      NOT NULL DEFAULT 1      COMMENT '显示状态[0-不显示 1-显示]',
    first_letter    VARCHAR(1)            DEFAULT NULL    COMMENT '检索首字母',
    sort            INT          NOT NULL DEFAULT 0      COMMENT '排序',
    -- ↓↓↓ 审计与控制字段（所有表统一） ↓↓↓
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP              COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by       BIGINT                DEFAULT NULL    COMMENT '创建人用户 ID',
    update_by       BIGINT                DEFAULT NULL    COMMENT '更新人用户 ID',
    is_deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除[0-正常 1-删除]',
    version         INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品品牌';
```

### 1.1 主键

| 项 | 约定 |
|----|------|
| 列名 | 统一 `id`（不再用 `cat_id`/`attr_id`/`brand_id` 等领域名做主键） |
| 类型 | `BIGINT` |
| 策略 | **雪花算法（`ASSIGN_ID`）**，DDL **不写** `AUTO_INCREMENT` |
| 理由 | 雪花 ID 对 InnoDB B+ 树插入性能与自增相当，且对 ShardingSphere 分库分表友好；自增 ID 不利于分片、暴露业务量、跨库冲突 |

> 业务/外键列保留领域命名（如 `parent_cid`、`spu_id`、`cat_id` 作为外键引用），只有**主键列**标准化为 `id`。

### 1.2 审计与控制字段（所有表统一，不可省略）

| 列 | 类型 | 默认 | 说明 |
|----|------|------|------|
| `create_time` | DATETIME | `CURRENT_TIMESTAMP` | 创建时间，由 DB 生成 + MetaObjectHandler 兜底 |
| `update_time` | DATETIME | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |
| `create_by` | BIGINT | NULL | 创建人用户 ID，MetaObjectHandler 从 UserContext 填充 |
| `update_by` | BIGINT | NULL | 更新人用户 ID |
| `is_deleted` | TINYINT | 0 | 逻辑删除，`@TableLogic`，0=正常 1=删除 |
| `version` | INT | 0 | 乐观锁，`@Version` + OptimisticLockerInnerInterceptor |

> 这六个字段对应 `BaseEntity`，实体继承 `BaseEntity` 后无需重复声明。MyMetaObjectHandler 自动填充 `create_time/update_time/create_by/update_by/is_deleted/version`。

### 1.3 存储引擎与字符集

```sql
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
```

- **必须显式声明**，不依赖数据库默认值（生产环境不同实例默认值可能不同，会导致中文/emoji 乱码或排序不一致）。
- `utf8mb4` 支持完整 Unicode（含 emoji），`utf8mb4_unicode_ci` 排序规则对多语言更准确。

---

## 二、类型规范

| 场景 | 推荐类型 | 禁止 | 说明 |
|------|---------|------|------|
| 主键 / 外键 ID | `BIGINT` | `int` | 雪花 ID 19 位，int 装不下 |
| 状态 / 枚举 | `TINYINT` | `bigint`/`char` | 如 `show_status TINYINT`，禁止 `enable bigint` 这种错误类型 |
| 布尔 | `TINYINT(1)` | `bit`/`char(1)` | 0/1 |
| 金额 | `DECIMAL(18,4)` | `float`/`double` | 浮点数有精度损失，金额必须定点数 |
| 短字符串 | `VARCHAR(n)` | `CHAR(n)` | CHAR 定长浪费空间；`char(30)` 应改 `varchar(30)` |
| 长文本 | `TEXT` / `MEDIUMTEXT` | `VARCHAR(10000)` | 超长内容用 TEXT 系列 |
| 时间 | `DATETIME` | `TIMESTAMP` | TIMESTAMP 2038 问题 + 时区敏感；DATETIME 范围大 |
| 手机号 | `VARCHAR(20)` | `char(13)` | 国际号码长度不定 |
| 布尔标志位 | `TINYINT` | `bigint` | 见 status |

### 2.1 字段默认值

- `NOT NULL` 字段必须给 `DEFAULT`（除主键由应用层赋值）。
- 可空字段显式 `DEFAULT NULL`。
- 状态类字段给业务默认值（如 `show_status TINYINT NOT NULL DEFAULT 1`）。

---

## 三、注释规范

- **每个表**必须有 `COMMENT '表说明'`。
- **每个列**必须有 `COMMENT '列说明'`，枚举值列出含义：`COMMENT '状态[0-禁用 1-启用]'`。
- 注释是生产环境 DBA 协作、数据排查的重要依据，不可省略。

---

## 四、索引规范

| 类型 | 命名 | 场景 |
|------|------|------|
| 主键 | `PRIMARY KEY` | 自动，名为 `id` |
| 唯一索引 | `uk_字段名` | 业务唯一约束（如 `uk_name`、`uk_upload_id`） |
| 普通索引 | `idx_字段名` | 查询条件字段 |
| 联合索引 | `idx_字段1_字段2` | 多列查询，遵循最左前缀 |

原则：
- 索引建在高频查询的 WHERE / ORDER BY / JOIN 字段上。
- 单表索引不超过 5 个，避免影响写入性能。
- 长字符串字段建索引用前缀索引（`INDEX idx_xxx (name(20))`）。
- 外键列必须建索引（`parent_cid`、`spu_id` 等）。

---

## 五、命名规范

| 对象 | 规范 | 示例 |
|------|------|------|
| 库 | `mall_{业务}` 或沿用 `mymall_{业务}` | `mall_product`、`mymall_pms` |
| 表 | `{前缀}_{实体}` 全小写下划线 | `pms_brand`、`pms_category` |
| 列 | 全小写下划线 | `create_time`、`parent_cid` |
| 索引 | `uk_`/`idx_` 前缀 | `uk_name`、`idx_parent_cid` |
| 表前缀 | 按业务域：`pms`(商品) `sms`(营销) `ums`(会员) `wms`(仓储) `oms`(订单) `mall_oss`(对象存储) | — |

> 现有 `mymall_*` 库名为谷粒商城迁移遗留，可逐步过渡到 `mall_*`；表前缀 `pms/sms/...` 保留。

---

## 六、迁移表改造要点（谷粒商城 → 生产级）

迁移自谷粒商城的表（`init/mysql/mymall_*.sql`，共 53 张）存在以下非生产级问题，改造时统一处理：

| 问题 | 改造 |
|------|------|
| 无 `ENGINE=InnoDB CHARSET=utf8mb4` | 补齐显式声明 |
| PK 用 `AUTO_INCREMENT` 且命名混乱（`cat_id`/`attr_id`/`id`） | PK 列统一为 `id BIGINT`，去 `AUTO_INCREMENT` |
| 缺审计列 | 补 `create_time/update_time/create_by/update_by/is_deleted/version` |
| 类型错误（`enable bigint`、`char(30)`、`phone char(13)`） | 按 §二 修正 |
| 无列注释 / 表注释不全 | 逐列补 COMMENT |
| 无索引 | 按查询场景补 `idx_`/`uk_` |

> 改造保留业务列名与表注释语义不变，只动结构。改造后实体统一继承 `BaseEntity`。

---

## 七、与 ORM 的对应

| DDL 列 | BaseEntity 字段 | 注解 |
|--------|----------------|------|
| `id` | `id` | `@TableId(type = ASSIGN_ID)` |
| `create_time` | `createTime` | `@TableField(fill = INSERT)` |
| `update_time` | `updateTime` | `@TableField(fill = INSERT_UPDATE)` |
| `create_by` | `createBy` | `@TableField(fill = INSERT)` |
| `update_by` | `updateBy` | `@TableField(fill = INSERT_UPDATE)` |
| `is_deleted` | `isDeleted` | `@TableLogic` + `@TableField(fill = INSERT)` |
| `version` | `version` | `@Version` + `@TableField(fill = INSERT)` |

实体继承 `BaseEntity` 即自动获得上述字段，表中只需建对应列，实体类中不重复声明。

---

## 八、检查清单

建表/改造表时自查：

- [ ] 主键 `id BIGINT`，无 `AUTO_INCREMENT`
- [ ] 六个审计控制字段齐全（create_time/update_time/create_by/update_by/is_deleted/version）
- [ ] `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
- [ ] 每列每表有 COMMENT，枚举值列出含义
- [ ] 类型规范：金额 DECIMAL、状态 TINYINT、字符串 VARCHAR、时间 DATETIME
- [ ] NOT NULL 字段有 DEFAULT
- [ ] 外键列建索引，唯一约束用 `uk_`，查询字段用 `idx_`
- [ ] 无 `char(n)` 定长、无 `bigint` 存状态等错误类型
