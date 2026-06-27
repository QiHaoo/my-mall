# 全局配置类

> 涉及组件：`MybatisPlusConfig`、`JacksonConfig`、`SpringDocConfig`
>
> 这三个配置类各自独立，解决不同领域的问题，通过 Spring Boot 自动装配机制统一生效。

## MybatisPlusConfig：MyBatis-Plus 全局配置

### 两个拦截器

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    return interceptor;
}
```

| 拦截器 | 作用 | 原理 |
|--------|------|------|
| `PaginationInnerInterceptor` | 自动分页 | 拦截 SQL，改写成 `SELECT COUNT(*)` + `LIMIT` 两条 SQL |
| `OptimisticLockerInnerInterceptor` | 乐观锁 | 拦截 UPDATE，自动追加 `WHERE version=?` 并 `SET version=version+1` |

### 分页上限的安全设计

```java
private static final long MAX_PAGE_LIMIT = 500L;

PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
pagination.setMaxLimit(MAX_PAGE_LIMIT);
```

为什么需要 `maxLimit`？如果不限制，恶意请求 `?pageSize=999999999` 会让数据库一次查百万行，直接拖垮 DB。

500 这个数字和 `PageQuery` 的 `@Max(500)` 一致——双重防线：
- 第一层：`PageQuery` 的 `@Max(500)` 在参数校验阶段拦截
- 第二层：`PaginationInnerInterceptor` 的 `maxLimit` 在 SQL 执行阶段兜底

### 全局兜底配置

```java
@Bean
public GlobalConfig globalConfig() {
    GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
    dbConfig.setIdType(IdType.ASSIGN_ID);
    dbConfig.setLogicDeleteField("isDeleted");
    dbConfig.setLogicDeleteValue("1");
    dbConfig.setLogicNotDeleteValue("0");
    return globalConfig;
}
```

`BaseEntity` 已经用注解声明了 `@TableId(ASSIGN_ID)` 和 `@TableLogic`，这里为什么还要全局配一份？

**生产级原则：不依赖隐式默认**。如果有人写了不继承 `BaseEntity` 的实体，全局配置仍然能提供兜底。这不是重复，是纵深防御。

### `@ComponentScan` 的作用

```java
@ComponentScan("com.mymall.common")
```

业务模块的 `@SpringBootApplication` 默认只扫描自身包（如 `com.mymall.product`），不会扫描 `com.mymall.common`。这个 `@ComponentScan` 确保 common 包下的 `@Component`（如 `MyMetaObjectHandler`、`GlobalExceptionHandler`）能被业务模块扫描到。

### 业界对比：拦截器 vs 插件

| 框架 | 分页实现 | 特点 |
|------|---------|------|
| MyBatis-Plus | InnerInterceptor | 自动改写 SQL，零侵入 |
| MyBatis（原生） | PageHelper | 通过 ThreadLocal 传参，需手动 `PageHelper.startPage()` |
| JPA/Hibernate | Pageable | 方法参数传 Pageable，ORM 层支持 |
| 手写 SQL | LIMIT + COUNT | 最灵活但最重复 |

MyBatis-Plus 的方式最省心——Service 层调 `mapper.selectPage(page, wrapper)`，拦截器自动处理。

## JacksonConfig：序列化配置

### Long → String：前端精度问题

```java
SimpleModule longModule = new SimpleModule();
longModule.addSerializer(Long.class, ToStringSerializer.instance);
longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
```

**问题根源**：JavaScript 的 `Number` 是 IEEE 754 双精度浮点数，最大安全整数是 `2^53 - 1 = 9007199254740991`（16 位）。而雪花算法生成的 ID 是 19 位（如 `1895273648392216576`），超出安全范围，前端解析会丢失精度：

```javascript
// 后端返回的 JSON
{"id": 1895273648392216576}

// 前端 JavaScript 解析
JSON.parse('{"id": 1895273648392216576}')
// → {id: 1895273648392216600}  ← 末尾精度丢失！
```

**解决方案**：全局把 Long 序列化成 String：

```json
{"id": "1895273648392216576"}  // 前端按字符串处理，精度不丢
```

**代价**：非 ID 的 Long 字段（如文件字节数 `134217728`）也会变成字符串。前端需要按字符串数字处理：`parseInt(size)` 或 `Number(size)`。

### 业界对比

| 方案 | 优点 | 缺点 |
|------|------|------|
| 全局 Long → String（本项目） | 一劳永逸 | 非 ID 的 Long 也变字符串 |
| 字段级 `@JsonSerialize(using = ToStringSerializer.class)` | 精确控制 | 每个字段都要加注解 |
| 前端用 json-bigint 解析 | 不改后端 | 前端需要替换 `JSON.parse` |
| 用 String 类型主键 | 彻底避免 | 改变主键类型，影响排序/索引 |

本项目选全局方案，因为简单且覆盖全面。非 ID 的 Long 变字符串，前端 `parseInt` 一下就行。

### 时间格式化

```java
timeModule.addSerializer(LocalDateTime.class,
    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
```

不加配置时，Jackson 默认把 `LocalDateTime` 序列化成时间戳数组：

```json
{"createTime": [2026, 6, 27, 22, 30, 0]}  // 默认：数组
{"createTime": "2026-06-27 22:30:00"}      // 配置后：字符串
```

字符串格式前端可直接展示，不需要额外格式化。

### `Jackson2ObjectMapperBuilderCustomizer` 的选择

```java
@Bean
public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return builder -> { ... };
}
```

为什么不直接 `@Bean ObjectMapper`？因为 Spring Boot 的自动配置对 `ObjectMapper` 做了大量定制（驼峰转下划线、日期处理等），直接替换 `ObjectMapper` 会丢失这些默认行为。`Customizer` 是增量定制，在默认配置基础上追加。

### `failOnUnknownProperties(false)`

```java
builder.failOnUnknownProperties(false);
```

前端多传了字段（如 DTO 里没有 `extra` 但前端传了 `{"extra": "xxx"}`），默认会报错。设为 false 后忽略未知字段，提高前后端联调容错性。

## SpringDocConfig：API 文档配置

```java
@Bean
public OpenAPI customOpenAPI() {
    return new OpenAPI()
            .info(new Info()
                    .title("my-mall API")
                    .version("v1")
                    .description("my-mall 商城微服务接口文档")
                    .contact(new Contact()
                            .name("my-mall")
                            .url("https://github.com/my-mall")));
}
```

### SpringDoc vs SpringFox

| 维度 | SpringFox（Swagger 2） | SpringDoc（OpenAPI 3） |
|------|----------------------|----------------------|
| 规范 | Swagger 2.0 | OpenAPI 3.0 |
| Spring Boot 3 支持 | 不支持（停更） | 原生支持 |
| 启动速度 | 慢（运行时扫描） | 快 |
| 注解 | `@Api` `@ApiOperation` | `@Tag` `@Operation` |

本项目用 SpringDoc 是因为 Spring Boot 3.x 不再支持 SpringFox。

### 为什么放 common 而不是各服务单独配

每个微服务都需要 API 文档，基本信息（title、version、contact）一致，放 common 统一配置。各服务如果有差异化需求，可以在自身模块再定义 `OpenAPI` Bean 覆盖。

注释里提到了后续计划：

> JWT 鉴权：待 mall-auth 落地后，在此追加 securitySchemes（Bearer）与 securityRequirements。

这是增量演进的设计——当前不需要认证，后续加认证时不改结构，只追加配置。

## 三个配置类的自动装配

这三个类都注册在 `AutoConfiguration.imports` 中：

```
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.mymall.common.config.MybatisPlusConfig
com.mymall.common.config.SpringDocConfig
com.mymall.common.config.JacksonConfig
```

业务服务引入 `mall-common` 依赖后，这三个配置类自动生效，无需 `@Import` 或 `@ComponentScan`。自动装配的原理见 [01-overview.md](./01-overview.md)。

## 设计取舍总结

| 决策 | 选择 | 理由 |
|------|------|------|
| 分页防爆破 | PageQuery @Max + Interceptor maxLimit 双重防线 | 纵深防御 |
| Long 序列化 | 全局 Long → String | 一劳永逸解决前端精度问题 |
| Jackson 定制 | BuilderCustomizer 增量定制 | 不破坏 Spring Boot 默认行为 |
| 未知字段 | 忽略不报错 | 前后端联调容错 |
| API 文档 | 放 common 统一配 | 所有服务共享基本配置 |
