# 对象存储服务设计文档

> 模块：`mall-oss`  
> 存储引擎：MinIO（S3 兼容协议）  
> 版本：v1.0  
> 更新时间：2026-06-23

---

## 一、业务背景

项目中大量场景需要文件上传与读取能力：

| 使用场景 | 表/模块 | 字段 | 文件类型 |
|---------|--------|------|---------|
| 商品 SPU 图集 | `pms_spu_images` | `img_url` | 图片 |
| 商品 SKU 图集 | `pms_sku_images` | `img_url` | 图片 |
| 分类图标 | `pms_category` | `icon` | 图片 |
| 品牌 Logo | `pms_brand` | `logo` | 图片 |
| 属性图标 | `pms_attr` / `pms_attr_group` | `icon` | 图片 |
| 用户头像 | `ums_member` | `member_icon` | 图片 |
| 评论图片/视频 | `pms_comment_info` | `resources` | 图片/视频 |
| 商品详情页富文本 | `pms_spu_info_desc` | `decript` | 图片（嵌入 HTML） |

**设计目标**：提供统一的文件上传/读取基础设施，前端直传 MinIO 不经过后端，后端仅负责签名鉴权和元数据管理。

---

## 二、方案对比

### 2.1 上传方案对比

| 维度 | 方案 A：后端中转上传 | 方案 B：前端直传（Presigned URL） |
|------|-------------------|-------------------------------|
| 流程 | 前端 → 后端 → MinIO | 前端 → MinIO（后端仅签发 URL） |
| 后端带宽压力 | 大（所有文件流经后端） | **无**（文件不经过后端） |
| 后端内存占用 | 高（文件缓冲在内存/磁盘） | **低**（仅签发 URL） |
| 大文件支持 | 受后端内存/超时限制 | **原生支持**（MinIO multipart） |
| 并发上传 | 受后端连接数瓶颈 | **直接到 MinIO**（高并发） |
| 安全性 | 后端完全可控 | 签名 URL 有时效 + 权限范围 |
| 复杂度 | 低 | 略高（需签名接口） |

**结论**：采用 **方案 B（前端直传 Presigned URL）**，业界主流方案，与阿里云 OSS 直传模式完全一致。

### 2.2 服务架构对比

| 维度 | 方案 A：纯共享 SDK | 方案 B：独立服务 + 共享 SDK |
|------|------------------|--------------------------|
| MinIO 凭据管理 | 每个服务都持有凭据 | **仅 mall-oss 持有**，其他服务通过 Feign 获取签名 |
| 文件元数据管理 | 各服务自行管理 | **统一 DB 表管理** |
| 上传策略控制 | 分散在各服务 | **集中管控** |
| 运维复杂度 | 低 | 中 |
| 安全性 | 凭据暴露面大 | **最小权限** |

**结论**：采用 **方案 B（独立 mall-oss 服务 + mall-common-oss 自动配置）**。mall-oss 服务统一管理签名和元数据；mall-common-oss 提供 MinIO 客户端自动配置，供服务端场景（如批量导入）使用。

---

## 三、整体架构

```
前端（Vue 3）
    │
    │ ① 请求上传凭证
    ▼
Spring Cloud Gateway (:1000)
    │
    │ /api/oss/** → lb://mall-oss
    ▼
mall-oss 服务 (:7300)
    │
    │ ② 生成 Presigned URL
    │    （MinIO SDK，有效期 5 分钟）
    │
    │ ③ 返回 Presigned URL + uploadId
    ▼
前端
    │
    │ ④ 直传文件到 MinIO
    │    （HTTP PUT，不经过后端）
    ▼
MinIO (:9000)
    │
    │ ⑤ 上传完成，前端回调后端
    ▼
mall-oss 服务
    │
    │ ⑥ 写入文件元数据到 DB
    ▼
MySQL (oss_file_meta 表)
```

**服务端直传场景**（如批量导入、报表导出）：

```
业务服务（如 mall-product）
    │
    │ 通过 mall-common-oss 自动配置的
    │ MinIOClient Bean 直接上传/下载
    ▼
MinIO (:9000)
```

---

## 四、基础设施

### 4.1 MinIO 部署

Docker Compose 已配置（`profiles: ["storage"]`）：

```yaml
minio:
  image: minio/minio:latest
  container_name: mall-minio
  environment:
    - MINIO_ROOT_USER=minioadmin
    - MINIO_ROOT_PASSWORD=minioadmin123
  command: server /data --console-address ":9001"
  ports:
    - "9000:9000"   # S3 API（应用对接）
    - "9001:9001"   # 管理控制台（浏览器访问）
  volumes:
    - minio-data:/data
```

**生产环境注意**：
- 凭据通过 Nacos 配置或 K8s Secret 注入，不硬编码
- 启用 HTTPS（MinIO 支持 Let's Encrypt / 自签证书）
- 多节点纠删码模式保证数据冗余

### 4.2 Bucket 规划

| Bucket 名称 | 用途 | 访问权限 | 说明 |
|------------|------|---------|------|
| `mall-product` | 商品图片（SPU/SKU/分类/品牌） | **public-read** | 商品详情页、列表页直接展示 |
| `mall-member` | 用户头像 | **public-read** | 前端直接展示 |
| `mall-comment` | 评论图片/视频 | **public-read** | 前端直接展示 |
| `mall-private` | 导入文件、报表、备份 | **private** | 仅服务端读写 |

> 使用多 Bucket 而非单 Bucket + 前缀，便于独立设置生命周期策略、访问权限、容量统计。

### 4.3 对象命名规范

```
{bucket}/{businessType}/{yyyy}/{MM}/{dd}/{uuid}.{ext}

示例：
mall-product/spu/2026/06/23/a1b2c3d4.jpg
mall-product/brand/2026/06/23/e5f6g7h8.png
mall-member/avatar/2026/06/23/i9j0k1l2.jpg
mall-private/import/2026/06/23/m3n4o5p6.xlsx
```

| 规则 | 说明 |
|------|------|
| 日期分目录 | 避免单目录文件过多（MinIO 单目录性能下降） |
| UUID 文件名 | 防冲突 + 防遍历（不用原始文件名） |
| 保留原始文件名 | 记录到 DB 元数据（下载时使用） |

---

## 五、模块设计

### 5.1 mall-oss 服务（独立微服务）

**职责**：
- 生成 Presigned Upload URL（前端直传用）
- 生成 Presigned Download URL（私有文件临时访问）
- 管理文件元数据（DB 记录）
- 文件删除（MinIO + DB 同步）
- 注册到 Nacos，被 Gateway 路由

**目录结构**：

```
mall-oss/
├── pom.xml
├── src/main/java/com/mymall/oss/
│   ├── OssApplication.java                    # 启动类
│   ├── controller/
│   │   └── OssController.java                 # REST 接口
│   ├── service/
│   │   ├── IOssService.java                   # 业务接口
│   │   └── impl/OssServiceImpl.java           # 业务实现
│   ├── entity/
│   │   └── OssFileMeta.java                   # 文件元数据实体
│   ├── mapper/
│   │   └── OssFileMetaMapper.java             # MyBatis-Plus Mapper
│   └── dto/
│       ├── UploadPolicyDTO.java               # 上传策略请求
│       ├── PresignedUrlVO.java                # Presigned URL 响应
│       └── FileMetaVO.java                    # 文件元数据响应
└── src/main/resources/
    └── application.yml                        # Nacos + MinIO 配置
```

### 5.2 mall-common-oss（共享自动配置模块）

**职责**：
- 提供 `MinioClient` Bean 自动配置
- 封装常用操作工具类 `OssTemplate`
- 供业务服务端直传场景使用（如 mall-product 批量导入 Excel）

**目录结构**：

```
mall-common/
└── src/main/java/com/mymall/common/oss/
    ├── OssProperties.java                     # 配置属性类
    ├── OssAutoConfiguration.java              # 自动配置
    └── OssTemplate.java                       # 操作模板
```

**OssProperties**（配置属性）：

```java
@ConfigurationProperties(prefix = "oss.minio")
@Data
public class OssProperties {
    private String endpoint = "http://127.0.0.1:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin123";
}
```

**OssTemplate**（操作模板）：

```java
@RequiredArgsConstructor
public class OssTemplate {

    private final MinioClient minioClient;

    /** 上传 InputStream */
    public String upload(String bucket, String objectName,
                         InputStream stream, String contentType) { ... }

    /** 生成 Presigned PUT URL（前端直传） */
    public String getPresignedPutUrl(String bucket, String objectName,
                                     int expirySeconds) { ... }

    /** 生成 Presigned GET URL（私有文件下载） */
    public String getPresignedGetUrl(String bucket, String objectName,
                                     int expirySeconds) { ... }

    /** 删除文件 */
    public void delete(String bucket, String objectName) { ... }

    /** 批量删除 */
    public void batchDelete(String bucket, List<String> objectNames) { ... }
}
```

**AutoConfiguration.imports 注册**：

```
com.mymall.common.oss.OssAutoConfiguration
```

**业务服务使用示例**：

```yaml
# application.yml（Nacos 配置）
oss:
  minio:
    endpoint: http://mall-minio:9000
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}
```

```java
// 服务端直传（如批量导入）
@Autowired
private OssTemplate ossTemplate;

public void importProducts(MultipartFile file) {
    String url = ossTemplate.upload("mall-private",
        "import/" + UUID.randomUUID() + ".xlsx",
        file.getInputStream(),
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
}
```

---

## 六、API 设计

### 6.1 获取上传凭证（核心接口）

**用途**：前端上传文件前调用，获取 Presigned URL。

```
POST /api/oss/policy
```

**请求体**：

```json
{
    "bucket": "mall-product",
    "businessType": "spu",
    "contentType": "image/jpeg",
    "fileSize": 1048576,
    "originalFilename": "product-photo.jpg"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| bucket | String | 是 | 目标 Bucket |
| businessType | String | 是 | 业务类型（spu / sku / brand / avatar / import 等） |
| contentType | String | 是 | 文件 MIME 类型 |
| fileSize | Long | 是 | 文件大小（字节），用于校验上限 |
| originalFilename | String | 是 | 原始文件名（记录到元数据） |

**响应**：

```json
{
    "code": 200,
    "data": {
        "uploadId": "a1b2c3d4e5f6",
        "uploadUrl": "http://127.0.0.1:9000/mall-product/spu/2026/06/23/a1b2.jpg?X-Amz-Algorithm=...",
        "objectName": "spu/2026/06/23/a1b2c3d4e5f6.jpg",
        "bucket": "mall-product",
        "fileUrl": "http://127.0.0.1:9000/mall-product/spu/2026/06/23/a1b2c3d4e5f6.jpg",
        "expiresIn": 300
    }
}
```

| 字段 | 说明 |
|------|------|
| uploadId | 上传记录 ID（回调时使用） |
| uploadUrl | Presigned PUT URL（前端用此 URL 直传） |
| objectName | MinIO 中的对象路径 |
| bucket | Bucket 名称 |
| fileUrl | 上传完成后的访问 URL（public bucket） |
| expiresIn | URL 有效期（秒），默认 300 |

**校验规则**：

| 规则 | 说明 |
|------|------|
| bucket 白名单 | 只允许指定的 bucket |
| 文件大小上限 | 图片 ≤ 10MB，视频 ≤ 500MB，其他 ≤ 50MB |
| contentType 白名单 | 只允许 image/*, video/*, application/pdf, application/vnd.openxmlformats-* 等 |
| businessType 枚举 | spu, sku, brand, category, attr, avatar, comment, import, export |

### 6.2 上传完成回调

**用途**：前端上传到 MinIO 成功后，通知后端写入元数据。

```
POST /api/oss/callback
```

**请求体**：

```json
{
    "uploadId": "a1b2c3d4e5f6",
    "bucket": "mall-product",
    "objectName": "spu/2026/06/23/a1b2c3d4e5f6.jpg"
}
```

**响应**：

```json
{
    "code": 200,
    "data": {
        "fileId": 1001,
        "fileUrl": "http://127.0.0.1:9000/mall-product/spu/2026/06/23/a1b2c3d4e5f6.jpg",
        "objectName": "spu/2026/06/23/a1b2c3d4e5f6.jpg",
        "bucket": "mall-product"
    }
}
```

**逻辑**：
1. 校验 uploadId 是否存在且未过期
2. 调用 MinIO `statObject` 确认文件已上传
3. 写入 `oss_file_meta` 表
4. 返回文件信息

### 6.3 文件删除

```
DELETE /api/oss/file?bucket=mall-product&objectName=spu/2026/06/23/a1b2.jpg
```

**逻辑**：
1. 删除 MinIO 中的对象
2. 更新 `oss_file_meta` 表状态为已删除
3. 返回成功

### 6.4 获取下载 URL（私有文件）

```
GET /api/oss/download-url?bucket=mall-private&objectName=import/2026/06/23/file.xlsx
```

**响应**：

```json
{
    "code": 200,
    "data": {
        "downloadUrl": "http://127.0.0.1:9000/mall-private/import/2026/...?X-Amz-Algorithm=...",
        "expiresIn": 600
    }
}
```

---

## 七、数据模型

### 7.1 文件元数据表

```sql
CREATE DATABASE IF NOT EXISTS mall_oss
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE mall_oss;

CREATE TABLE oss_file_meta (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    upload_id       VARCHAR(36)  NOT NULL COMMENT '上传 ID（UUID）',
    bucket          VARCHAR(64)  NOT NULL COMMENT 'Bucket 名称',
    object_name     VARCHAR(512) NOT NULL COMMENT 'MinIO 对象路径',
    original_name   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_size       BIGINT       NOT NULL COMMENT '文件大小（字节）',
    content_type    VARCHAR(128) NOT NULL COMMENT 'MIME 类型',
    business_type   VARCHAR(32)  NOT NULL COMMENT '业务类型',
    file_url        VARCHAR(1024) NOT NULL COMMENT '访问 URL',
    uploader_id     BIGINT       DEFAULT NULL COMMENT '上传者用户 ID',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-已删除',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bucket_object (bucket, object_name(100)),
    INDEX idx_business_type (business_type, create_time),
    INDEX idx_uploader (uploader_id, create_time),
    UNIQUE INDEX uk_upload_id (upload_id)
) ENGINE=InnoDB COMMENT='文件元数据表';
```

---

## 八、上传流程详解

### 8.1 前端直传时序

```
┌────────┐      ┌──────────┐      ┌─────────┐      ┌───────┐
│  前端   │      │ Gateway  │      │ mall-oss│      │ MinIO │
└───┬────┘      └────┬─────┘      └────┬────┘      └───┬───┘
    │                │                  │               │
    │ ① POST /api/oss/policy           │               │
    │───────────────>│─────────────────>│               │
    │                │                  │               │
    │                │  ② 校验参数       │               │
    │                │  ③ 生成 objectName│               │
    │                │  ④ getPresignedPutUrl             │
    │                │                  │──────────────>│
    │                │                  │<──────────────│
    │                │  ⑤ 返回 Presigned URL             │
    │<───────────────│<─────────────────│               │
    │                │                  │               │
    │ ⑥ PUT uploadUrl（文件二进制）      │               │
    │──────────────────────────────────────────────────>│
    │                │                  │               │
    │                │  ⑦ MinIO 存储文件  │               │
    │<──────────────────────────────────────────────────│
    │  200 OK        │                  │               │
    │                │                  │               │
    │ ⑧ POST /api/oss/callback         │               │
    │───────────────>│─────────────────>│               │
    │                │                  │ ⑨ statObject   │
    │                │                  │──────────────>│
    │                │                  │<──────────────│
    │                │  ⑩ 写入 DB 元数据  │               │
    │<───────────────│<─────────────────│               │
    │  200 + fileUrl │                  │               │
```

### 8.2 前端核心代码示例（Vue 3）

```typescript
// composables/useOssUpload.ts
export function useOssUpload() {
    /**
     * 获取 Presigned URL 并上传
     */
    async function upload(file: File, bucket: string, businessType: string) {
        // 1. 获取上传凭证
        const { data: policy } = await request.post('/api/oss/policy', {
            bucket,
            businessType,
            contentType: file.type,
            fileSize: file.size,
            originalFilename: file.name
        });

        // 2. 直传到 MinIO（PUT 请求，Content-Type 必须匹配）
        await fetch(policy.uploadUrl, {
            method: 'PUT',
            headers: { 'Content-Type': file.type },
            body: file
        });

        // 3. 回调通知后端
        const { data: meta } = await request.post('/api/oss/callback', {
            uploadId: policy.uploadId,
            bucket: policy.bucket,
            objectName: policy.objectName
        });

        return meta.fileUrl;
    }

    return { upload };
}
```

### 8.3 多图上传场景（商品图集）

商品 SPU/SKU 通常有多张图片，前端流程：

```
1. 用户选择 N 张图片
2. 并行请求 N 个 Presigned URL（POST /api/oss/policy × N）
3. 并行直传 N 个文件到 MinIO
4. 并行回调 N 次（POST /api/oss/callback × N）
5. 将 N 个 fileUrl 组装到 SPU/SKU 提交请求中
```

> 也可提供批量接口 `POST /api/oss/policy/batch` 一次签发多个 URL，减少请求数。

---

## 九、安全设计

### 9.1 上传安全

| 措施 | 说明 |
|------|------|
| **Bucket 白名单** | 后端只允许签发预定义 Bucket 的 URL |
| **文件大小校验** | 后端校验 fileSize，超限直接拒绝 |
| **ContentType 白名单** | 只允许指定 MIME 类型，防止可执行文件 |
| **URL 有效期** | Presigned URL 默认 5 分钟过期 |
| **上传 ID 时效** | uploadId 关联 Redis 记录，30 分钟未回调自动清理 |
| **MinIO Policy** | public bucket 设为只读（匿名 GET），写入仅通过 Presigned URL |

### 9.2 MinIO Bucket Policy 配置

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {"AWS": ["*"]},
            "Action": ["s3:GetObject"],
            "Resource": [
                "arn:aws:s3:::mall-product/*",
                "arn:aws:s3:::mall-member/*",
                "arn:aws:s3:::mall-comment/*"
            ]
        }
    ]
}
```

> `mall-private` bucket 不配置匿名读取策略，仅通过 Presigned GET URL 或 MinIO 凭据访问。

### 9.3 Gateway 路由

```yaml
- id: oss-route
  uri: lb://mall-oss
  predicates:
    - Path=/api/oss/**
  filters:
    - StripPrefix=1
```

---

## 十、Nacos 配置

### 10.1 mall-oss.yaml

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mall_oss?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: root

oss:
  minio:
    endpoint: http://127.0.0.1:9000
    access-key: minioadmin
    secret-key: minioadmin123
  upload:
    max-image-size: 10485760      # 图片 10MB
    max-video-size: 524288000     # 视频 500MB
    max-other-size: 52428800      # 其他 50MB
    url-expiry: 300               # Presigned URL 有效期（秒）
    callback-expiry: 1800         # uploadId 回调有效期（秒）
    allowed-buckets:
      - mall-product
      - mall-member
      - mall-comment
      - mall-private
    allowed-content-types:
      - image/jpeg
      - image/png
      - image/gif
      - image/webp
      - video/mp4
      - application/pdf
      - application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
```

---

## 十一、错误码

在 `ResultCode` 中新增 OSS 相关错误码（编号段 52001+）：

| 错误码 | 枚举名 | 说明 |
|--------|--------|------|
| 52001 | OSS_BUCKET_NOT_ALLOWED | Bucket 不在白名单中 |
| 52002 | OSS_FILE_TOO_LARGE | 文件超过大小限制 |
| 52003 | OSS_CONTENT_TYPE_NOT_ALLOWED | 不支持的文件类型 |
| 52004 | OSS_UPLOAD_ID_EXPIRED | 上传凭证已过期 |
| 52005 | OSS_UPLOAD_ID_NOT_FOUND | 上传 ID 不存在 |
| 52006 | OSS_FILE_NOT_FOUND | 文件不存在 |
| 52007 | OSS_UPLOAD_VERIFY_FAILED | 文件上传验证失败（MinIO statObject 失败） |

---

## 十二、服务端口

在端口分配表中注册：

| 服务 | 端口 |
|------|------|
| mall-oss | 7300 |

---

## 十三、POM 依赖

### 13.1 mall-oss/pom.xml

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- mall-common（含 MyBatis-Plus、GlobalExceptionHandler、OssTemplate） -->
    <dependency>
        <groupId>com.mall</groupId>
        <artifactId>mall-common</artifactId>
        <version>${project.version}</version>
    </dependency>

    <!-- MinIO Java Client -->
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>8.5.14</version>
    </dependency>

    <!-- Nacos Discovery -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>

    <!-- Nacos Config -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>

    <!-- MySQL Driver -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

### 13.2 mall-common/pom.xml（新增 MinIO 依赖）

```xml
<!-- MinIO Java Client（optional，按需引入） -->
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.14</version>
    <optional>true</optional>
</dependency>
```

> `optional=true`：mall-common 声明为 optional，不自动传递给所有子模块。只有需要 OSS 能力的模块才显式引入。

---

## 十四、与谷粒商城方案对比

### 14.1 总体对比

| 维度 | 谷粒商城（阿里云 OSS） | my-mall（自建 MinIO） |
|------|---------------------|---------------------|
| 存储引擎 | 阿里云 OSS | MinIO（S3 兼容） |
| 上传模式 | 前端获取 Policy 签名 → 直传 OSS | 前端获取 Presigned URL → 直传 MinIO |
| SDK | aliyun-oss-java-sdk | minio-java 8.5.14 |
| 签名方式 | OSS PostPolicy（表单上传） | S3 Presigned PUT URL |
| 文件 URL | `https://{bucket}.oss-cn-{region}.aliyuncs.com/{key}` | `http://{minio-host}:9000/{bucket}/{key}` |
| 私有文件 | OSS 签名 URL | MinIO Presigned GET URL |
| 依赖外部服务 | 需要阿里云账号 + 付费 | 完全自建，零费用 |
| SDK 兼容性 | 阿里云专有 API | S3 标准协议（可无缝迁移到 AWS S3 / 腾讯云 COS） |

### 14.2 两种方案都是最佳实践吗？

**是的，两者都符合最佳实践**。核心原则完全一致：

> **后端只负责签名鉴权，前端直接上传到对象存储，文件不经过后端。**

```
谷粒商城：前端 → 后端(获取 Policy+签名) → OSS(POST form-data) → 回调后端
my-mall：  前端 → 后端(获取 Presigned URL) → MinIO(PUT binary) → 回调后端
```

两者都避免了文件流经后端服务器，后端带宽压力为零，内存占用极低，大文件不受超时限制。

差异仅在于**签名机制**：

| 维度 | OSS PostPolicy（谷粒商城） | S3 Presigned URL（my-mall） |
|------|-------------------------|--------------------------|
| 上传方式 | `POST` + `multipart/form-data` | `PUT` + 原始二进制 |
| 签名载体 | Policy JSON + HMAC-SHA256 放在表单字段中 | AWS Signature V4 放在 URL 查询参数中 |
| 前端复杂度 | 需构造 FormData（约 8 个字段） | 一行 `fetch(url, { method: 'PUT', body: file })` |
| 可移植性 | 阿里云专有 API | S3 标准协议（全球通用） |
| 分片上传 | PostPolicy 不原生支持 | Presigned URL 原生支持 chunked multipart |
| 业界采用 | 国内云生态（阿里云、腾讯云有类似方案） | 全球标准（AWS、Google、Cloudflare、DigitalOcean） |

**我们选择 Presigned URL 的原因**：

1. **前端更简单** — 一个 `PUT` 请求 vs 构造包含 `key`、`policy`、`signature`、`OSSAccessKeyId`、`success_action_status` 等字段的 FormData
2. **S3 标准** — 如果未来从 MinIO 迁移到 AWS S3 或任何 S3 兼容存储，零代码改动
3. **更灵活** — Presigned URL 可精确限定对象名、支持大文件分片上传、兼容任意 HTTP 客户端

---

## 十五、存储服务商迁移评估

### 15.1 架构隔离设计

所有 MinIO 特有代码被隔离在 `OssTemplate` 和 `OssAutoConfiguration` 中，业务服务**永不直接引用** `io.minio.*`：

```
业务服务（mall-product、mall-member 等）
    │
    │ 仅依赖 OssTemplate（我们的抽象层）
    ▼
OssTemplate（mall-common-oss）
    │
    │ 内部使用 MinioClient
    ▼
MinIO (:9000)
```

### 15.2 迁移场景评估

| 目标存储 | 难度 | 改动范围 | 说明 |
|---------|------|---------|------|
| **AWS S3** | 零改动 | 仅修改 Nacos 配置的 endpoint + 凭据 | MinIO 本身就是 S3 协议的开源实现，完全兼容 |
| **腾讯云 COS** | 零改动 | 仅修改 Nacos 配置的 endpoint + 凭据 | COS 支持 S3 兼容模式 |
| **Cloudflare R2** | 零改动 | 仅修改 Nacos 配置的 endpoint + 凭据 | R2 完全兼容 S3 协议 |
| **阿里云 OSS**（S3 模式） | 零改动 | 仅修改 Nacos 配置的 endpoint + 凭据 | OSS 提供 S3 兼容端点 |
| **阿里云 OSS**（原生 SDK） | 中等 | 替换 `OssTemplate` 内部实现（约 1 个文件） | 将 `MinioClient` 调用替换为 `OSSClient`，方法签名不变，业务服务无感知 |
| **Google Cloud Storage** | 低 | 使用 GCS 的 S3 兼容 HMAC API，或替换模板内部实现 | |

### 15.3 迁移示例：切换到阿里云 OSS（S3 兼容模式）

仅修改 Nacos 配置：

```yaml
# 迁移前（MinIO）
oss:
  minio:
    endpoint: http://127.0.0.1:9000
    access-key: minioadmin
    secret-key: minioadmin123

# 迁移后（阿里云 OSS S3 兼容模式）
oss:
  minio:
    endpoint: https://oss-cn-hangzhou.aliyuncs.com
    access-key: LTAI5t...
    secret-key: xxx...
```

MinIO Java Client 说的是 S3 协议，OSS S3 兼容模式理解相同的 `PutObject`、`GetObject`、`PresignedURL` 操作。配置一改即可。

### 15.4 谷粒商城方案的迁移劣势

谷粒商城直接引入 `com.aliyun.oss.*`（阿里云专有 SDK），切换到 MinIO 或 AWS S3 需要**重写所有 OSS 相关代码**。我们的 S3 方案从一开始就避免了这种厂商锁定。

---

## 十六、实现清单

| 序号 | 任务 | 优先级 |
|------|------|--------|
| 1 | 启动 MinIO 容器，创建 4 个 Bucket，配置 Policy | P0 |
| 2 | 创建 `mall_oss` 数据库 + `oss_file_meta` 表 | P0 |
| 3 | 在 mall-common 中新增 `oss` 包（OssProperties + OssAutoConfiguration + OssTemplate） | P0 |
| 4 | 创建 mall-oss 服务骨架（启动类 + application.yml + Nacos 注册） | P0 |
| 5 | 实现 OssController：签发 Presigned URL | P0 |
| 6 | 实现 OssController：上传回调 + 元数据入库 | P0 |
| 7 | 实现 OssController：文件删除 | P1 |
| 8 | 实现 OssController：私有文件下载 URL | P1 |
| 9 | Gateway 添加 `/api/oss/**` 路由 | P1 |
| 10 | 编写 HTTP 调试文件（http/oss-demo.http） | P1 |
| 11 | 编写 Service 纯单元测试 + Controller 切片测试 | P1 |
| 12 | ResultCode 新增 OSS 错误码 | P1 |
| 13 | 前端 useOssUpload composable 封装 | P2 |
