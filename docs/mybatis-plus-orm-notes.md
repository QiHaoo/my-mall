# 项目 ORM 实践笔记

> 记录 my-mall 项目中 MyBatis-Plus 的完整配置、公共组件和使用约定。

---

## 一、依赖说明

### 1.1 依赖都在 mall-common

所有 MyBatis-Plus 相关依赖统一在 `mall-common/pom.xml` 中声明，各业务模块只需依赖 `mall-common`，依赖自动传递。

```xml
<!-- mall-common/pom.xml 核心依赖 -->

<!-- Spring Boot 3.x 适配版 Starter -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>

<!-- 分页 / 乐观锁插件（3.5.4+ 必须单独引入，不再捆绑在 starter 中） -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
</dependency>

<!-- 代码生成器（仅开发期，设为 optional，打进生产包也不影响） -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-generator</artifactId>
    <optional>true</optional>
</dependency>

<!-- 模板引擎（代码生成器依赖） -->
<dependency>
    <groupId>org.apache.velocity</groupId>
    <artifactId>velocity-engine-core</artifactId>
    <optional>true</optional>
</dependency>
```

> **📌 学习要点**：`mybatis-plus-jsqlparser` 从 MyBatis-Plus 3.5.4 起不再随 starter 捆绑，必须手动引入，否则分页/乐观锁插件会在启动时报 `ClassNotFoundException`。

### 1.2 版本在哪管

具体版本号在 **父 pom**（`/pom.xml`）的 `<dependencyManagement>` 中统一管理：

```xml
<mybatis-plus.version>3.5.9</mybatis-plus.version>
<velocity.version>2.4</velocity.version>
```

---

## 二、全局配置 — MybatisPlusConfig

位置：`mall-common/src/main/java/com/mymall/common/config/MybatisPlusConfig.java`

```java
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // ① 物理分页插件 — 必须配置，否则 selectPage() 无效
        //    DbType.MYSQL 让插件知道用什么数据库的分页语法
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        // ② 乐观锁插件 — 配合实体上 @Version 注解使用
        //    更新时自动 WHERE version = ? AND SET version = version + 1
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }
}
```

> **📌 常见坑**：如果忘记引入 `mybatis-plus-jsqlparser`，Spring Boot 启动时会提示 `java.lang.NoClassDefFoundError: net/sf/jsqlparser/...`。

---

## 三、BaseEntity — 公共实体基类

位置：`mall-common/src/main/java/com/mymall/common/entity/BaseEntity.java`

```java
@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)           // 雪花算法，19 位 Long
    private Long id;

    @TableField(fill = FieldFill.INSERT)        // 新增时自动填入
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE) // 新增 + 修改时自动刷新
    private LocalDateTime updateTime;
}
```

### 使用方式

所有数据库实体继承它即可，**不要在子类再声明 id / createTime / updateTime**：

```java
@Data
@EqualsAndHashCode(callSuper = true)      // ⚠️ 继承场景必须加
@TableName("mall_coupon")
public class Coupon extends BaseEntity {
    private String name;
    // ...其他业务字段
}
```

> **📌 学习要点 — IdType.ASSIGN_ID（雪花算法）**：
> - 优点：全局唯一、趋势递增、不依赖数据库自增、分布式环境下不会冲突
> - 缺点：比自增 ID 长（19 位 vs 11 位），存储空间略大
> - 适用场景：微服务分库分表、分布式系统
> - 如果用 `IdType.AUTO`（数据库自增），分库分表时 ID 会冲突

---

## 四、自动填充 — MyMetaObjectHandler

位置：`mall-common/src/main/java/com/mymall/common/handler/MyMetaObjectHandler.java`

```java
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // strictInsertFill 只在目标字段为 null 时才填充，避免覆盖前端传值
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

### 工作原理

```
执行 insert / update
    → MyBatis-Plus 拦截
        → 检查 entity 字段上的 @TableField(fill = ...) 注解
            → 调用 MyMetaObjectHandler 对应方法
                → 字段值为 null → 自动填入
                → 字段已有值 → 跳过（strict 模式）
```

> **📌 为什么用 strictInsertFill 而不是 setFieldValByName**：`strictInsertFill` 只在目标字段为 null 时填充，如果你业务上需要手动指定 createTime，它不会覆盖。`setFieldValByName` 会无条件覆盖。

---

## 五、各服务 application.yml 配置

每个业务模块的 `application.yml` 中，**必须**配置 MyBatis-Plus 的 SQL XML 路径：

```yaml
# 以 mall-coupon 为例
mybatis-plus:
  mapper-locations: classpath:mapper/coupon/*.xml   # 每个服务路径不同
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 开发期打印 SQL
```

### 各服务 mapper-locations 对照表

| 服务 | mapper-locations | 数据库 |
|------|------------------|--------|
| mall-coupon | `classpath:mapper/coupon/*.xml` | mymall-sms |
| mall-member | `classpath:mapper/member/*.xml` | mymall-ums |
| mall-product | `classpath:mapper/product/*.xml` | mymall-pms |
| mall-order | `classpath:mapper/order/*.xml` | mymall-oms |
| mall-ware | `classpath:mapper/ware/*.xml` | mymall-wms |

> **📌 注意**：`StdOutImpl` 在生产环境应换掉（改为 `Slf4jImpl` 或直接去掉），否则 SQL 日志会打到标准输出不便于收集。

---

## 六、使用约定

### 6.1 通用 CRUD 直接用 BaseMapper

```java
// 常规单表操作，直接用 MyBatis-Plus 内置方法，不需要写 SQL
mapper.selectById(id);
mapper.selectList(new LambdaQueryWrapper<Coupon>().eq(Coupon::getStatus, 1));
mapper.insert(entity);
mapper.updateById(entity);
```

### 6.2 复杂 SQL 写在 XML 里

多表关联、复杂聚合、报表查询 → 写对应的 XML 文件：

```xml
<!-- resources/mapper/coupon/CouponMapper.xml -->
<mapper namespace="com.mymall.coupon.mapper.CouponMapper">
    <select id="selectWithDetails" resultType="...">
        SELECT c.*, ...
        FROM mall_coupon c
        LEFT JOIN ...
    </select>
</mapper>
```

### 6.3 分页查询

```java
// 不需要写 limit offset，PaginationInnerInterceptor 自动拦截处理
Page<Coupon> page = new Page<>(1, 10); // 第1页，每页10条
IPage<Coupon> result = couponMapper.selectPage(page, wrapper);
```

---

## 🔗 关联文档

- [MyBatis-Plus 代码生成规范](./mybatis-plus-codegen-guide.md) — 如何用 FastAutoGenerator 批量生成 Entity / Mapper / Service
- [MyBatis-Plus 官方文档](https://baomidou.com/)
