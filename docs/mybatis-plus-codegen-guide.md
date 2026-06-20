# MyBatis-Plus 代码生成规范

> **方案一**：用 MyBatis-Plus FastAutoGenerator 批量生成 Entity / Mapper / Service 空壳，Controller 手写。
> 生成器代码统一放在 `mall-common` 模块，所有服务共用。

---

## 一、依赖配置

### mall-common/pom.xml

`mall-common` 作为公共模块，统一管理 MyBatis-Plus 相关依赖，各业务模块只需依赖 `mall-common` 即可。

```xml
<!-- MyBatis-Plus Spring Boot 3 Starter -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.9</version>
</dependency>

<!-- 代码生成器（仅用于开发期，可选 scope=provided 或放 test） -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-generator</artifactId>
    <version>3.5.9</version>
</dependency>

<!-- 模板引擎（FastAutoGenerator 依赖） -->
<dependency>
    <groupId>org.apache.velocity</groupId>
    <artifactId>velocity-engine-core</artifactId>
    <version>2.4</version>
</dependency>
```

> **版本说明**：`mybatis-plus-spring-boot3-starter` 是 MyBatis-Plus 专为 Spring Boot 3.x 提供的 Starter，3.5.9 是当前稳定版。各业务模块的 pom.xml **不需要**重复声明这些依赖，依赖 `mall-common` 即可传递。

---

## 二、包结构规范

基础包名：`com.mymall`

```
com.mymall
├── common                          # mall-common 模块
│   ├── generator                   # 代码生成器
│   │   └── CodeGenerator.java      # 放 src/test/java，不打包进产物
│   ├── config
│   │   └── MybatisPlusConfig.java  # 分页插件 + 乐观锁插件
│   ├── handler
│   │   └── MyMetaObjectHandler.java # 公共字段自动填充
│   └── entity
│       └── BaseEntity.java         # 公共基类
│
└── product                         # mall-product 模块（示例）
    ├── MallProductApplication.java # 启动类
    ├── entity                      # ✅ 生成器生成
    ├── mapper                      # ✅ 生成器生成
    ├── service                     # ✅ 生成器生成
    │   └── impl
    └── controller                  # ✅ 手写
```

| 层 | 包路径 | 生成方式 |
|----|--------|---------|
| Entity | `com.mymall.{module}.entity` | 生成器 |
| Mapper | `com.mymall.{module}.mapper` | 生成器 |
| Mapper XML | `resources/mapper/{module}/` | 生成器（空模板） |
| Service | `com.mymall.{module}.service` | 生成器 |
| ServiceImpl | `com.mymall.{module}.service.impl` | 生成器 |
| Controller | `com.mymall.{module}.controller` | **手写** |

---

## 三、公共组件（mall-common 模块）

以下公共类放在 `mall-common` 模块，所有业务模块通过依赖自动引入。

### 3.1 BaseEntity 公共基类

路径：`com.mymall.common.entity.BaseEntity`

```java
package com.mymall.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
```

- 主键策略：`ASSIGN_ID`（雪花算法，分布式友好）
- 所有生成的 Entity 继承 `BaseEntity`，表中不再重复定义这三个字段
- 业务表只需定义业务字段（如 spu_name、price 等）

### 3.2 公共字段自动填充

路径：`com.mymall.common.handler.MyMetaObjectHandler`

```java
package com.mymall.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

- 插入时自动填充 `createTime` 和 `updateTime`
- 更新时自动刷新 `updateTime`

### 3.3 MyBatis-Plus 配置

路径：`com.mymall.common.config.MybatisPlusConfig`

```java
package com.mymall.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
```

- 分页插件：`PaginationInnerInterceptor`，MyBatis-Plus 分页必须配置
- 乐观锁插件：`OptimisticLockerInnerInterceptor`，配合 `@Version` 注解使用

### 3.4 统一响应体

路径：`com.mymall.common.result.R`

```java
package com.mymall.common.result;

import lombok.Data;
import java.io.Serializable;

@Data
public class R<T> implements Serializable {

    private Integer code;
    private String msg;
    private T data;

    public static <T> R<T> ok() {
        return restResult(null, 200, "success");
    }

    public static <T> R<T> ok(T data) {
        return restResult(data, 200, "success");
    }

    public static <T> R<T> error(String msg) {
        return restResult(null, 500, msg);
    }

    public static <T> R<T> error(Integer code, String msg) {
        return restResult(null, code, msg);
    }

    private static <T> R<T> restResult(T data, Integer code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
```

---

## 四、数据库表设计规范

### 4.1 表命名

表名使用 **模块前缀 + 下划线** 命名法：`{模块缩写}_{业务名}`

| 模块 | 前缀 | 示例表名 |
|------|------|---------|
| 商品 | pms_ | `pms_spu_info`, `pms_sku_info`, `pms_brand`, `pms_category` |
| 订单 | oms_ | `oms_order`, `oms_order_item` |
| 会员 | ums_ | `ums_member`, `ums_member_level` |
| 库存 | wms_ | `wms_ware_info`, `wms_ware_sku` |
| 优惠券 | sms_ | `sms_coupon`, `sms_coupon_history` |

### 4.2 公共字段

所有表统一包含以下公共字段（对应 BaseEntity）：

```sql
id          BIGINT   NOT NULL  PRIMARY KEY  COMMENT '主键',
create_time DATETIME NOT NULL  DEFAULT CURRENT_TIMESTAMP              COMMENT '创建时间',
update_time DATETIME NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
```

### 4.3 字段命名

- 数据库字段：下划线命名（`spu_name`, `catalog_id`）
- Java 字段：驼峰命名（`spuName`, `catalogId`）
- MyBatis-Plus 自动完成下划线 ↔ 驼峰映射，无需手写 `@TableField`

---

## 五、代码生成器使用方法

### 5.1 生成器代码

路径：`mall-common/src/test/java/com/mymall/common/generator/CodeGenerator.java`

> 放在 `src/test/java` 下，不打包进正式产物，仅开发时运行。

```java
package com.mymall.common.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.util.Collections;

public class CodeGenerator {

    // ========== 修改这几项配置 ==========
    private static final String URL = "jdbc:mysql://localhost:3306/mymall_product?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root123";
    private static final String MODULE_NAME = "product";           // 模块名
    private static final String[] TABLES = {"pms_spu_info", "pms_sku_info"};  // 表名
    private static final String TABLE_PREFIX = "pms_";             // 表前缀（生成时去掉）
    // ====================================

    private static final String PARENT_PACKAGE = "com.mymall";
    private static final String OUTPUT_DIR = System.getProperty("user.dir") + "/mall-" + MODULE_NAME + "/src/main/java";
    private static final String XML_OUTPUT_DIR = System.getProperty("user.dir") + "/mall-" + MODULE_NAME + "/src/main/resources/mapper/" + MODULE_NAME;

    public static void main(String[] args) {
        FastAutoGenerator.create(URL, USERNAME, PASSWORD)
                .globalConfig(builder -> builder
                        .author("mymall")
                        .outputDir(OUTPUT_DIR)
                        .disableOpenDir()
                )
                .packageConfig(builder -> builder
                        .parent(PARENT_PACKAGE)
                        .moduleName(MODULE_NAME)
                        .pathInfo(Collections.singletonMap(
                                OutputFile.xml, XML_OUTPUT_DIR))
                )
                .strategyConfig(builder -> builder
                        .addInclude(TABLES)
                        .addTablePrefix(TABLE_PREFIX)
                )
                .strategyConfig(builder -> builder
                        .entityBuilder()
                        .superClass("com.mymall.common.entity.BaseEntity")
                        .enableLombok()
                        .enableChain()
                        .enableSerialVersionUID()
                        .naming(NamingStrategy.underline_to_camel)
                        .columnNaming(NamingStrategy.underline_to_camel)
                        .addIgnoreColumns("id", "create_time", "update_time")  // 公共字段由 BaseEntity 管理
                )
                .strategyConfig(builder -> builder
                        .mapperBuilder()
                        .enableBaseResultMap()
                )
                .strategyConfig(builder -> builder
                        .serviceBuilder()
                        .formatServiceFileName("%sService")
                        .formatServiceImplFileName("%sServiceImpl")
                )
                .strategyConfig(builder -> builder
                        .controllerBuilder()
                        .disable()  // 不生成 Controller，手写
                )
                .templateEngine(new VelocityTemplateEngine())
                .execute();

        System.out.println("代码生成完成！输出目录：" + OUTPUT_DIR);
    }
}
```

### 5.2 操作步骤

以 mall-product 服务为例：

1. **建库建表**

   ```sql
   CREATE DATABASE IF NOT EXISTS mymall_product DEFAULT CHARSET utf8mb4;
   USE mymall_product;

   CREATE TABLE pms_spu_info (
       id          BIGINT       NOT NULL PRIMARY KEY COMMENT '主键',
       spu_name    VARCHAR(200) NOT NULL COMMENT '商品名称',
       spu_description TEXT     COMMENT '商品描述',
       catalog_id  BIGINT       COMMENT '分类ID',
       brand_id    BIGINT       COMMENT '品牌ID',
       price       DECIMAL(10,2) COMMENT '价格',
       publish_status TINYINT   DEFAULT 0 COMMENT '上架状态[0-下架 1-上架]',
       create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
       update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
   ) COMMENT '商品SPU信息表';
   ```

2. **修改生成器配置**

   打开 `CodeGenerator.java`，修改以下常量：
   - `URL`：数据库名改为 `mymall_product`
   - `MODULE_NAME`：改为 `product`
   - `TABLES`：填入要生成的表名
   - `TABLE_PREFIX`：填入表前缀（如 `pms_`）

3. **运行生成器**

   在 IDEA 中打开 `CodeGenerator.java`，右键 → Run 'CodeGenerator.main()'。

4. **检查生成结果**

   生成的代码会输出到 `mall-product/src/main/java/com/mymall/product/` 下：
   ```
   entity/SpuInfoEntity.java
   mapper/SpuInfoMapper.java
   service/SpuInfoService.java
   service/impl/SpuInfoServiceImpl.java
   ```
   Mapper XML 输出到 `mall-product/src/main/resources/mapper/product/`。

5. **手写 Controller**

   在 `com.mymall.product.controller` 包下手写 Controller（见第六节规范）。

6. **配置 Mapper 扫描**

   启动类加 `@MapperScan("com.mymall.product.mapper")`：
   ```java
   @SpringBootApplication
   @MapperScan("com.mymall.product.mapper")
   public class MallProductApplication {
       public static void main(String[] args) {
           SpringApplication.run(MallProductApplication.class, args);
       }
   }
   ```

   application.yml 配置 Mapper XML 路径：
   ```yaml
   mybatis-plus:
     mapper-locations: classpath:mapper/**/*.xml
   ```

---

## 六、生成代码规范示例

以 `pms_spu_info` 表为例，生成器产出的代码：

### 6.1 Entity

```java
package com.mymall.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mymall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_spu_info")
public class SpuInfoEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String spuName;
    private String spuDescription;
    private Long catalogId;
    private Long brandId;
    private BigDecimal price;
    private Integer publishStatus;
}
```

- 继承 `BaseEntity`，不重复定义 id / createTime / updateTime
- `@TableName` 指定表名
- `@EqualsAndHashCode(callSuper = true)` 包含父类字段

### 6.2 Mapper

```java
package com.mymall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mymall.product.entity.SpuInfoEntity;

public interface SpuInfoMapper extends BaseMapper<SpuInfoEntity> {
}
```

- 继承 `BaseMapper<T>`，自动获得 insert / deleteById / updateById / selectById 等方法
- 复杂 SQL 在此接口定义方法 + XML 实现

### 6.3 Service

```java
package com.mymall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mymall.product.entity.SpuInfoEntity;

public interface SpuInfoService extends IService<SpuInfoEntity> {
}
```

- 继承 `IService<T>`，自动获得 save / removeById / updateById / getById / list / page 等方法

### 6.4 ServiceImpl

```java
package com.mymall.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mymall.product.entity.SpuInfoEntity;
import com.mymall.product.mapper.SpuInfoMapper;
import com.mymall.product.service.SpuInfoService;
import org.springframework.stereotype.Service;

@Service
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoMapper, SpuInfoEntity> implements SpuInfoService {
}
```

- 继承 `ServiceImpl<M, T>`，实现对应 Service 接口
- 空实现即可，通用方法已由父类提供；业务方法后续在此添加

---

## 七、Controller 手写规范

Controller 不使用生成器，全部手写。遵循以下规范：

### 7.1 基本结构

```java
package com.mymall.product.controller;

import com.mymall.common.result.R;
import com.mymall.product.entity.SpuInfoEntity;
import com.mymall.product.service.SpuInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/product/spu")
public class SpuInfoController {

    @Autowired
    private SpuInfoService spuInfoService;

    /// 简单 CRUD —— 直接调用 IService 提供的方法

    @GetMapping("/{id}")
    public R<SpuInfoEntity> getById(@PathVariable Long id) {
        return R.ok(spuInfoService.getById(id));
    }

    @GetMapping("/list")
    public R<List<SpuInfoEntity>> list() {
        return R.ok(spuInfoService.list());
    }

    @PostMapping
    public R<Void> save(@RequestBody SpuInfoEntity entity) {
        spuInfoService.save(entity);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody SpuInfoEntity entity) {
        spuInfoService.updateById(entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        spuInfoService.removeById(id);
        return R.ok();
    }
}
```

### 7.2 分页 + 条件查询

复杂查询使用 `LambdaQueryWrapper`，条件式拼接：

```java
@GetMapping("/search")
public R<Page<SpuInfoEntity>> search(
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "10") Integer pageSize,
        @RequestParam(required = false) Long catalogId,
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) String keyword) {

    Page<SpuInfoEntity> page = new Page<>(pageNum, pageSize);
    LambdaQueryWrapper<SpuInfoEntity> wrapper = new LambdaQueryWrapper<>();

    // 条件式拼接：参数非空才加入查询条件
    wrapper.eq(catalogId != null, SpuInfoEntity::getCatalogId, catalogId)
           .eq(brandId != null, SpuInfoEntity::getBrandId, brandId)
           .eq(status != null, SpuInfoEntity::getPublishStatus, status)
           .like(keyword != null && !keyword.isEmpty(), SpuInfoEntity::getSpuName, keyword)
           .orderByDesc(SpuInfoEntity::getCreateTime);

    return R.ok(spuInfoService.page(page, wrapper));
}
```

### 7.3 复杂业务逻辑

复杂业务逻辑写在 Service 层，Controller 只做参数接收和结果返回：

```java
// Controller
@PostMapping("/publish/{spuId}")
public R<Void> publish(@PathVariable Long spuId) {
    spuInfoService.publishSpu(spuId);
    return R.ok();
}

// ServiceImpl
public void publishSpu(Long spuId) {
    // 1. 校验商品信息完整性
    // 2. 远程调用库存服务初始化库存
    // 3. 远程调用搜索服务上架商品
    // 4. 更新商品上架状态
    // 5. 发送 RocketMQ 消息通知其他服务
}
```

---

## 八、各服务生成参数速查表

开发每个服务时，修改 `CodeGenerator.java` 的配置即可：

| 服务模块 | 数据库 | MODULE_NAME | TABLE_PREFIX | 示例表名 |
|---------|--------|------------|-------------|---------|
| mall-product | mymall_product | product | pms_ | pms_spu_info |
| mall-order | mymall_order | order | oms_ | oms_order |
| mall-member | mymall_member | member | ums_ | ums_member |
| mall-ware | mymall_ware | ware | wms_ | wms_ware_info |
| mall-coupon | mymall_coupon | coupon | sms_ | sms_coupon |
| mall-seckill | mymall_seckill | seckill | sks_ | sks_session |

> 表前缀参考谷粒商城原教程的命名规范，pms = Product Management System，oms = Order Management System，以此类推。
