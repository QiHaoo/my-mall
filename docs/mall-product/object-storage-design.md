# 对象存储服务设计文档

> 模块：`mall-oss`  
> 存储引擎：MinIO（S3 兼容协议）  
> 版本：v1.1（v1.0 基础方案；v1.1 安全闭环增强：Content-Type 回调校验、回调幂等、上传者身份透传、删除越权校验、PENDING 清理、publicBaseUrl/region 配置）  
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

| Bucket 名称 | 用途 | 访问权限 | 公网访问域名（生产） | 说明 |
|------------|------|---------|---------|------|
| `mall-product` | 商品图片（SPU/SKU/分类/品牌） | **public-read** | `https://img.mall.com` | 商品详情页、列表页直接展示 |
| `mall-member` | 用户头像 | **public-read** | `https://avatar.mall.com` | 前端直接展示 |
| `mall-comment` | 评论图片/视频 | **public-read** | `https://comment.mall.com` | 前端直接展示 |
| `mall-private` | 导入文件、报表、备份 | **private** | —（仅 Presigned GET） | 仅服务端读写 |

> 使用多 Bucket 而非单 Bucket + 前缀，便于独立设置生命周期策略、访问权限、容量统计。
>
> **公网访问域名**：public bucket 的 `fileUrl` 必须走 CDN / 独立公网域名（`oss.minio.public-base-url`），**绝不暴露 MinIO 内网地址**。CDN 回源到 MinIO，前端只感知公网域名。

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
│   ├── OssApplication.java                    # 启动类（@EnableScheduling，驱动 PENDING 清理）
│   ├── controller/
│   │   └── OssController.java                 # REST 接口
│   ├── service/
│   │   ├── IOssService.java                   # 业务接口
│   │   └── impl/OssServiceImpl.java           # 业务实现（含 PENDING 定时清理）
│   ├── entity/
│   │   └── OssFileMeta.java                   # 文件元数据实体
│   ├── mapper/
│   │   └── OssFileMetaMapper.java             # MyBatis-Plus Mapper
│   ├── config/
│   │   ├── MyBatisConfig.java                 # @MapperScan（独立，避免切片测试启动失败）
│   │   └── UserContextFilter.java             # 解析 X-User-Id 写入 UserContext
│   ├── dto/
│   │   ├── UploadPolicyDTO.java               # 上传策略请求
│   │   └── CallbackDTO.java                   # 上传回调请求
│   └── vo/
│       ├── PresignedUrlVO.java                # Presigned URL 响应
│       ├── FileMetaVO.java                    # 文件元数据响应
│       └── DownloadUrlVO.java                # 下载 URL 响应
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
    /** MinIO 服务端地址（内部/服务端访问） */
    private String endpoint = "http://127.0.0.1:9000";
    /** 公网访问基础地址（CDN/独立域名），留空回退到 endpoint */
    private String publicBaseUrl;
    /** Region：MinIO 本地可不填；迁移 AWS S3 / OSS S3 兼容模式必填 */
    private String region;
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin123";
}
```

**OssTemplate**（操作模板）：

```java
public class OssTemplate {

    private final MinioClient minioClient;
    private final OssProperties properties;

    /** 上传 InputStream（size 已知传字节数，未知传 -1 走分片，避免用 stream.available() 误判） */
    public String upload(String bucket, String objectName,
                         InputStream stream, long size, String contentType) { ... }

    /** 生成 Presigned PUT URL（前端直传） */
    public String getPresignedPutUrl(String bucket, String objectName,
                                     int expirySeconds) { ... }

    /** 生成 Presigned GET URL（私有文件下载） */
    public String getPresignedGetUrl(String bucket, String objectName,
                                     int expirySeconds) { ... }

    /** 删除文件（NoSuchKey 视为成功，幂等删除） */
    public void delete(String bucket, String objectName) { ... }

    /** 批量删除 */
    public void batchDelete(String bucket, List<String> objectNames) { ... }

    /** statObject：文件不存在返回 null */
    public StatObjectResponse statObject(String bucket, String objectName) { ... }

    /** 拼 public bucket 访问 URL：优先 publicBaseUrl，回退 endpoint */
    public String buildPublicUrl(String bucket, String objectName) { ... }
}
```

**OssAutoConfiguration**：构建 `MinioClient` 时，仅当配置了 `region` 才设置（MinIO 本地无需 region；AWS S3 / OSS S3 兼容模式必填，否则签名失败）。

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
    public-base-url: https://img.mall.com
    region:
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}
```

```java
// 服务端直传（如批量导入）
@Autowired
private OssTemplate ossTemplate;

public void importProducts(MultipartFile file) {
    ossTemplate.upload("mall-private",
        "import/" + UUID.randomUUID() + ".xlsx",
        file.getInputStream(),
        file.getSize(),                        // 已知大小
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
        "uploadId": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
        "uploadUrl": "http://127.0.0.1:9000/mall-product/spu/2026/06/23/a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4.jpg?X-Amz-Algorithm=...",
        "objectName": "spu/2026/06/23/a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4.jpg",
        "bucket": "mall-product",
        "fileUrl": "https://img.mall.com/mall-product/spu/2026/06/23/a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4.jpg",
        "expiresIn": 300
    }
}
```

| 字段 | 说明 |
|------|------|
| uploadId | 上传记录 ID（32 位无横线 UUID，回调时使用） |
| uploadUrl | Presigned PUT URL（前端用此 URL 直传，PUT 时 Content-Type 必须与本接口声明的 contentType 完全一致） |
| objectName | MinIO 中的对象路径 |
| bucket | Bucket 名称 |
| fileUrl | 上传完成后的访问 URL（基于 `public-base-url`，public bucket 可直接访问） |
| expiresIn | URL 有效期（秒），默认 300 |

> **Content-Type 一致性**：前端 PUT 直传时携带的 `Content-Type` 必须与 `/policy` 请求中声明的 `contentType` 完全一致。回调阶段后端通过 `statObject` 读取 MinIO 实际存储的 Content-Type 二次校验，不一致则拒绝（弥补 Presigned PUT URL 未把 Content-Type 钉入签名的安全缺口）。

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
1. 按 `uploadId` 查记录（uploadId 全局唯一）
2. 记录不存在 → `OSS_UPLOAD_ID_NOT_FOUND`；状态非 PENDING/ACTIVE → `OSS_UPLOAD_ID_EXPIRED`
3. **幂等**：记录已 ACTIVE 直接返回（前端重试安全，不重复校验 MinIO）
4. 校验回调的 bucket/objectName 与签发记录一致，防伪造回调
5. `statObject` 确认文件已上传，并校验实际 Content-Type 与声明一致（不一致 → `OSS_UPLOAD_VERIFY_FAILED`）
6. 更新状态为 ACTIVE，用 MinIO 实际大小覆盖声明的 fileSize
7. 返回文件信息

> **回调必须幂等**：前端网络抖动重试回调是常态，重复回调已 ACTIVE 的记录应直接返回，不能报错。

### 6.3 文件删除

```
DELETE /api/oss/file?bucket=mall-product&objectName=spu/2026/06/23/a1b2.jpg
```

**逻辑**：
1. 按 bucket + objectName 查未删除记录
2. **越权校验**：普通用户只能删自己上传的文件（`uploader_id` 与当前登录用户比对）；后台/服务账号（无登录态）放行，由网关侧 RBAC 控制
3. 删除 MinIO 中的对象（NoSuchKey 视为成功）
4. 更新 `oss_file_meta` 表状态为已删除
5. 返回成功

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
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-已删除 2-待确认(PENDING)',
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

        // 2. 直传到 MinIO（PUT 请求，Content-Type 必须与 /policy 声明完全一致，否则回调校验失败）
        const putResp = await fetch(policy.uploadUrl, {
            method: 'PUT',
            headers: { 'Content-Type': file.type },
            body: file
        });
        if (!putResp.ok) {
            throw new Error('上传到 MinIO 失败: ' + putResp.status);
        }

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

> 可选：提供批量接口 `POST /api/oss/policy/batch` 一次签发多个 URL，减少请求数（P2 演进，非 v1.1 范围）。

---

## 九、安全设计

### 9.1 上传安全

| 措施 | 说明 |
|------|------|
| **Bucket 白名单** | 后端只允许签发预定义 Bucket 的 URL |
| **文件大小校验** | 后端校验 fileSize，超限直接拒绝 |
| **ContentType 白名单** | 只允许指定 MIME 类型，防止可执行文件 |
| **Content-Type 一致性校验** | 回调时 `statObject` 读取 MinIO 实际存储类型，与声明不一致则拒绝（防止客户端用其它类型直传绕过白名单） |
| **回调对象一致性** | 回调的 bucket/objectName 必须与签发记录一致，防伪造回调 |
| **URL 有效期** | Presigned URL 默认 5 分钟过期 |
| **PENDING 清理** | 超时未回调的 PENDING 记录由定时任务（`@Scheduled`，默认 1h）清理，并删除 MinIO 孤儿对象 |
| **MinIO Policy** | public bucket 设为只读（匿名 GET），写入仅通过 Presigned URL |

> **关于 Content-Type 钉签名**：minio-java 的 `getPresignedObjectUrl` 不直接支持把 `Content-Type` 纳入签名头，因此采用**回调阶段 statObject 二次校验**达成同等安全目标——客户端若用其它类型直传，回调时被拒绝，文件不会被标记为 ACTIVE，且会被清理任务删除。

### 9.2 上传者身份传递

```
Gateway（解析 JWT）──X-User-Id 请求头──> mall-oss
                                              │
                                  UserContextFilter（读 X-User-Id）
                                              │
                                  UserContext（ThreadLocal）
                                              │
                              OssServiceImpl.createUploadPolicy 写入 uploader_id
```

- 网关解析 JWT 后通过 `X-User-Id` 请求头透传登录用户 ID（待 mall-auth / 网关鉴权过滤器落地后对接）
- `UserContextFilter`（mall-oss/config）解析请求头写入 `com.mymall.common.util.UserContext`，请求结束清理 ThreadLocal
- Service 层 `UserContext.getUserId()` 获取上传者，写入 `oss_file_meta.uploader_id`
- 删除接口据此做越权校验

### 9.3 删除越权校验

- 普通用户只能删除 `uploader_id` 等于自己的文件，否则返回 `FORBIDDEN(403)`
- 后台/服务账号（无登录态，`UserContext.getUserId()` 为 null）放行，由网关侧 RBAC 控制权限

### 9.4 MinIO Bucket Policy 配置

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

### 9.5 Gateway 路由与鉴权限流

```yaml
- id: oss-route
  uri: lb://mall-oss
  predicates:
    - Path=/api/oss/**
  filters:
    - StripPrefix=1
```

**安全要求**（待 mall-auth / 网关鉴权过滤器落地后对接）：
1. `/api/oss/**` 必须经过鉴权，禁止匿名刷 Presigned URL（否则未登录即可把 MinIO 当免费存储）
2. 网关解析 JWT 后通过 `X-User-Id` 透传登录用户 ID
3. 对 `/api/oss/policy` 配置限流（Sentinel / RequestRateLimiter），防止单用户高频刷签名

### 9.6 事务边界

MinIO 网络调用（签发 / 删除 / statObject）一律放在 `@Transactional` 事务外，避免长时间持有 DB 连接。方法内 DB 操作均为单条 insert/update，天然原子，无需声明事务。

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
    public-base-url: https://img.mall.com   # 生产环境 CDN/公网域名；本地留空回退 endpoint
    region:                                   # MinIO 本地可不填；迁移 S3 兼容存储必填
    access-key: minioadmin
    secret-key: minioadmin123
  upload:
    max-image-size: 10485760      # 图片 10MB
    max-video-size: 524288000     # 视频 500MB
    max-other-size: 52428800      # 其他 50MB
    url-expiry: 300               # Presigned PUT URL 有效期（秒）
    download-expiry: 600          # Presigned GET URL 有效期（秒）
    callback-expiry: 1800         # PENDING 记录回调有效期（秒）：超时由定时任务清理
    cleanup-interval: 3600000     # PENDING 清理任务执行间隔（毫秒，默认 1h）
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

## 十四、选型与迁移

**核心原则**（与谷粒商城等业界方案一致）：后端只负责签名鉴权，前端直接上传到对象存储，文件不经过后端。后端带宽压力为零、内存占用极低、大文件不受超时限制。

**选择 S3 Presigned PUT URL（而非阿里云 OSS PostPolicy）的原因**：
1. 前端更简单 —— 一个 `PUT` 请求 vs 构造多字段 FormData
2. S3 标准协议 —— 迁移到 AWS S3 / 腾讯云 COS / Cloudflare R2 / 阿里云 OSS S3 兼容模式零代码改动
3. Presigned URL 可精确限定对象名、原生支持大文件分片上传

**架构隔离**：所有 MinIO 特有代码隔离在 `OssTemplate` + `OssAutoConfiguration`，业务服务永不直接引用 `io.minio.*`。迁移时：

| 目标存储 | 改动范围 |
|---------|---------|
| AWS S3 / 腾讯云 COS / Cloudflare R2 / 阿里云 OSS(S3 模式) | 仅改 Nacos 配置的 endpoint + region + 凭据 |
| 阿里云 OSS(原生 SDK) | 替换 `OssTemplate` 内部实现，方法签名不变，业务无感知 |

迁移示例（MinIO → 阿里云 OSS S3 兼容模式），仅改配置：

```yaml
oss:
  minio:
    endpoint: https://oss-cn-hangzhou.aliyuncs.com
    region: oss-cn-hangzhou
    access-key: LTAI5t...
    secret-key: xxx...
```

> 谷粒商城直接引入 `com.aliyun.oss.*` 专有 SDK，切换存储需重写所有 OSS 代码；本方案基于 S3 标准协议，从源头避免厂商锁定。

> 详细的对比分析（OSS PostPolicy vs S3 Presigned URL 的签名载体、前端复杂度等）见 `docs/other/` 归档文档。

---

## 十五、大文件分片上传（后续演进）

当前实现为单次 PUT 直传，视频上限 500MB。Presigned URL 协议原生支持分片上传（`CreateMultipartUpload` + 分片 Presigned URL + `CompleteMultipartUpload`），可应对超大文件 / 弱网场景。

**取舍**：5 分钟 URL 有效期内单次 PUT 大视频可能超时。短期可通过调长 `url-expiry` 缓解；超大文件场景再补分片上传接口（属 P2 演进，不在 v1.1 范围）。

---

## 十六、实现清单

| 序号 | 任务 | 优先级 | 状态 |
|------|------|--------|------|
| 1 | 启动 MinIO 容器，创建 4 个 Bucket，配置 Policy | P0 | 待执行 |
| 2 | 创建 `mall_oss` 数据库 + `oss_file_meta` 表 | P0 | ✅ |
| 3 | mall-common 新增 `oss` 包（OssProperties + OssAutoConfiguration + OssTemplate） | P0 | ✅ |
| 4 | 创建 mall-oss 服务骨架（启动类 + application.yml + Nacos 注册） | P0 | ✅ |
| 5 | 实现 OssController：签发 Presigned URL | P0 | ✅ |
| 6 | 实现 OssController：上传回调 + 元数据入库（含幂等、Content-Type 校验） | P0 | ✅ |
| 7 | 实现 OssController：文件删除（含越权校验） | P1 | ✅ |
| 8 | 实现 OssController：私有文件下载 URL | P1 | ✅ |
| 9 | Gateway 添加 `/api/oss/**` 路由 | P1 | ✅ |
| 10 | 编写 HTTP 调试文件（http/oss-demo.http） | P1 | ✅ |
| 11 | 编写 Service 纯单元测试 + Controller 切片测试 | P1 | ✅ |
| 12 | ResultCode 新增 OSS 错误码 | P1 | ✅ |
| 13 | 上传者身份透传（UserContext + UserContextFilter + uploader_id） | P1 | ✅ |
| 14 | 超时 PENDING 记录定时清理 | P1 | ✅ |
| 15 | publicBaseUrl / region 配置，fileUrl 走公网域名 | P1 | ✅ |
| 16 | 网关鉴权 + 限流（对接 mall-auth） | P1 | ⏳待 mall-auth |
| 17 | 大文件分片上传接口 | P2 | 待排期 |
| 18 | 前端 useOssUpload composable 封装 | P2 | 待排期 |
