# 对象存储系统详细设计文档

**文档版本**: v1.0
**创建日期**: 2025-01-26
**作者**: Jobs (Claude Code)
**状态**: 待审核

---

## 目录

1. [项目背景](#1-项目背景)
2. [需求分析](#2-需求分析)
3. [架构决策记录](#3-架构决策记录-adr)
4. [系统架构设计](#4-系统架构设计)
5. [详细设计](#5-详细设计)
6. [数据库设计](#6-数据库设计)
7. [接口设计](#7-接口设计)
8. [部署架构](#8-部署架构)
9. [监控与运维](#9-监控与运维)
10. [安全设计](#10-安全设计)
11. [性能优化](#11-性能优化)
12. [故障处理与降级](#12-故障处理与降级)
13. [测试策略](#13-测试策略)
14. [扩展性设计](#14-扩展性设计)
15. [迁移方案](#15-迁移方案)
16. [FAQ](#16-faq)

---

## 1. 项目背景

### 1.1 业务背景

Papertrace 平台需要存储大量的医疗文献数据（JSON 格式），当前 `LiteraturePublisherPort` 仅完成了数据序列化，但缺乏实际的存储实现。随着数据量增长，需要一个可靠、可扩展的对象存储解决方案。

### 1.2 技术背景

- **现有架构**: Hexagonal Architecture + DDD
- **微服务**: patra-ingest, patra-registry, patra-catalog（规划中）
- **技术栈**: Java 21, Spring Boot 3.2.4, MyBatis-Plus
- **配置中心**: Nacos
- **网关**: Spring Cloud Gateway

### 1.3 痛点分析

| 痛点 | 当前状态 | 影响 |
|-----|---------|------|
| 文件存储缺失 | 序列化后未实际存储 | 数据丢失风险 |
| 无元数据管理 | 无法追踪文件上传历史 | 审计困难 |
| 无多云支持 | - | 供应商锁定风险 |
| 无统一抽象 | 每个服务自己实现 | 代码重复 |

---

## 2. 需求分析

### 2.1 功能性需求

#### FR-1: 对象存储抽象
- **优先级**: P0（必须）
- **描述**: 提供统一的对象存储接口，支持文件上传、下载、删除操作
- **验收标准**:
  - 支持 MinIO 和 AWS S3
  - 接口响应时间 < 5s（100MB 文件）
  - 上传成功率 > 99.9%

#### FR-2: 多提供商支持
- **优先级**: P0（必须）
- **描述**: 支持通过配置切换对象存储提供商
- **验收标准**:
  - 配置切换不需要修改代码
  - 支持 MinIO, S3, OSS, COS
  - 提供商切换时间 < 1分钟

#### FR-3: 文件元数据管理
- **优先级**: P0（必须）
- **描述**: 记录文件上传历史、业务上下文、生命周期
- **验收标准**:
  - 元数据记录成功率 > 99%
  - 支持按业务上下文查询
  - 支持软删除

#### FR-4: 重试机制
- **优先级**: P1（重要）
- **描述**: 网络抖动时自动重试
- **验收标准**:
  - 默认重试 3 次
  - 支持指数退避
  - 可配置重试策略

#### FR-5: 监控指标
- **优先级**: P1（重要）
- **描述**: 提供上传成功率、耗时等监控指标
- **验收标准**:
  - 指标暴露到 Prometheus
  - 支持按 bucket/提供商维度聚合
  - 告警规则配置

#### FR-6: 生命周期管理
- **优先级**: P2（可选）
- **描述**: 支持文件过期清理
- **验收标准**:
  - 支持设置过期时间
  - 自动清理过期文件
  - 清理日志可审计

### 2.2 非功能性需求

#### NFR-1: 性能
- **吞吐量**: 单实例支持 100 QPS 上传请求
- **延迟**: P99 延迟 < 10s（100MB 文件）
- **并发**: 支持 50 并发上传

#### NFR-2: 可用性
- **目标**: 99.9% 可用性（年停机时间 < 8.76 小时）
- **降级**: 元数据服务不可用时，文件上传仍可正常工作

#### NFR-3: 可扩展性
- **水平扩展**: 支持多实例部署
- **提供商扩展**: 新增提供商无需修改核心代码

#### NFR-4: 可维护性
- **代码质量**: 遵循 Google Java Style
- **测试覆盖率**: 单元测试 > 80%，集成测试覆盖核心流程

#### NFR-5: 安全性
- **访问控制**: 支持基于 IAM 的权限控制
- **数据加密**: 支持服务端加密（SSE）
- **审计日志**: 所有上传/删除操作可追溯

---

## 3. 架构决策记录 (ADR)

### ADR-001: Starter 不应依赖业务服务 API

**日期**: 2025-01-26
**状态**: 已接受

#### 背景

最初设计中，`patra-spring-boot-starter-object-storage` 依赖 `patra-storage-api` 以实现自动元数据记录，但这引入了架构问题。

#### 决策

Starter 仅提供纯技术抽象（ObjectStorageTemplate），不依赖任何业务服务 API。元数据记录由调用方（patra-ingest）自行协调。

#### 理由

1. **分层原则**: 框架层不应依赖应用层
2. **现有模式**: 项目中其他 Starter（mybatis, web）均为纯技术抽象
3. **可测试性**: 避免单元测试变成集成测试
4. **扩展性**: 不同业务服务的上下文差异大，强行统一会造成抽象泄漏

#### 后果

- ✅ 优点: 架构清晰，职责单一，易于测试
- ❌ 缺点: 调用方需要额外编写 10-15 行协调代码

#### 替代方案

**方案A**: 在 Starter 中集成 MetadataRecorder
**评估**: 违反依赖方向，不符合项目架构风格，已否决

**方案B**: 在 patra-common 提供工具类
**评估**: 工具类会依赖 patra-storage-api，同样不合适

---

### ADR-002: 采用 Strategy 模式实现多提供商支持

**日期**: 2025-01-26
**状态**: 已接受

#### 背景

需要支持 MinIO, S3, OSS, COS 等多个对象存储提供商。

#### 决策

定义 `ObjectStorageProvider` 接口（Strategy），每个提供商实现该接口。运行时根据配置选择激活的 Provider。

#### 理由

1. **开闭原则**: 新增提供商无需修改现有代码
2. **运行时切换**: 通过配置即可切换提供商
3. **隔离性**: 每个提供商的实现互不影响

#### 实现示例

```java
public interface ObjectStorageProvider {
    ProviderType getProviderType();
    UploadResult upload(...);
}

@Component
public class MinioStorageProvider implements ObjectStorageProvider {
    // MinIO 特定实现
}

@Component
public class S3StorageProvider implements ObjectStorageProvider {
    // S3 特定实现
}
```

---

### ADR-003: 元数据管理采用独立微服务

**日期**: 2025-01-26
**状态**: 已接受

#### 背景

文件元数据需要跨服务共享（patra-ingest, patra-catalog），且需要集中式管理和查询。

#### 决策

创建 `patra-storage` 独立微服务，负责文件元数据的 CRUD、生命周期管理。

#### 理由

1. **DDD 视角**: 文件元数据是独立的业务能力
2. **集中管理**: 统一的元数据查询和审计
3. **独立演进**: 元数据服务可以独立升级和扩展

#### 数据库

独立数据库 `patra_storage`，避免与 patra_ingest/patra_registry 耦合。

---

### ADR-004: 接受适度代码重复（Rule of Three）

**日期**: 2025-01-26
**状态**: 已接受

#### 背景

用户担心每个服务都需要编写协调上传和元数据记录的代码（约 10-15 行）。

#### 决策

接受适度重复，等有 3 个使用方时再考虑抽象。

#### 理由

1. **YAGNI**: 当前只有 patra-ingest 一个使用方
2. **业务差异**: 不同服务的业务上下文差异大
3. **可读性**: 10-15 行代码的可读性优于复杂的抽象

#### 未来计划

当有 3 个服务都需要类似逻辑时，可考虑：
- 在各服务的 Infrastructure 层提供适配器基类
- 或通过代码生成工具减少样板代码

---

### ADR-005: 元数据记录失败不影响文件上传

**日期**: 2025-01-26
**状态**: 已接受

#### 背景

文件上传和元数据记录是两个独立的操作，可能出现上传成功但元数据记录失败的情况。

#### 决策

元数据记录失败时，记录告警日志，但不回滚文件上传。可选实现补偿机制（通过 Outbox 表）。

#### 理由

1. **可用性优先**: 文件上传是核心功能，元数据是辅助
2. **最终一致性**: 通过补偿机制最终保证一致性
3. **降级友好**: patra-storage 不可用时仍可上传文件

#### 补偿机制（可选）

```java
private void recordMetadata(UploadResult result, PublishContext context) {
    try {
        storageClient.recordUpload(request);
    } catch (Exception e) {
        log.warn("Metadata recording failed, will retry later");
        // 保存到本地 Outbox 表，定时任务重试
        outboxRepository.save(createMetadataRecordEvent(result, context));
    }
}
```

---

## 4. 系统架构设计

### 4.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     API Gateway (Spring Cloud Gateway)       │
└─────────────────────────────────────────────────────────────┘
                │                                │
                ↓                                ↓
┌───────────────────────────┐      ┌──────────────────────────┐
│   patra-ingest            │      │   patra-storage          │
│                           │      │                          │
│  ┌──────────────────────┐ │      │  ┌─────────────────────┐ │
│  │ LiteraturePublisher  │ │──────┼──│ StorageEndpoint     │ │
│  │ Adapter (Infra)      │ │Feign │  │ (REST API)          │ │
│  └──────────────────────┘ │      │  └─────────────────────┘ │
│          │                │      │          │               │
│          ├─ ObjectStorage │      │  ┌─────────────────────┐ │
│          │  Template      │      │  │ RecordUpload        │ │
│          │  (Starter)     │      │  │ Orchestrator (App)  │ │
│          │                │      │  └─────────────────────┘ │
│          └─ StorageClient │      │          │               │
│             (Feign)       │      │  ┌─────────────────────┐ │
└───────────────────────────┘      │  │ FileMetadata        │ │
          │                        │  │ Repository (Infra)  │ │
          ↓                        │  └─────────────────────┘ │
┌───────────────────────────┐      │          │               │
│  patra-spring-boot-       │      └──────────┼───────────────┘
│  starter-object-storage   │                 ↓
│                           │      ┌──────────────────────────┐
│  ┌──────────────────────┐ │      │  MySQL (patra_storage)   │
│  │ ObjectStorageTemplate│ │      │  - storage_file_metadata │
│  └──────────────────────┘ │      └──────────────────────────┘
│          │                │
│          ├─ MinioProvider │
│          └─ S3Provider    │
└───────────────────────────┘
          │
          ↓
┌──────────────────────────┐
│  MinIO / AWS S3          │
│  (对象存储服务)           │
└──────────────────────────┘
```

### 4.2 层次架构

#### 4.2.1 技术基础设施层
- **patra-spring-boot-starter-object-storage**
- 职责: 提供对象存储客户端抽象
- 依赖: 仅依赖技术框架（Spring Boot, MinIO SDK, S3 SDK）

#### 4.2.2 业务服务层
- **patra-storage**: 文件元数据管理服务
- **patra-ingest**: 文献摄取服务
- 职责: 实现各自的业务逻辑

#### 4.2.3 集成层
- **patra-ingest-infra/LiteraturePublisherAdapter**
- 职责: 协调对象存储和元数据服务

---

## 5. 详细设计

### 5.1 patra-spring-boot-starter-object-storage

#### 5.1.1 类图

```
┌─────────────────────────────────┐
│ <<interface>>                   │
│ ObjectStorageOperations         │
│ - upload(...)                   │
│ - download(...)                 │
│ - delete(...)                   │
└─────────────────────────────────┘
           △
           │ implements
┌─────────────────────────────────┐
│ ObjectStorageTemplate           │
│ - provider: Provider            │
│ - retryTemplate: RetryTemplate  │
│ - metrics: Metrics              │
│ + upload(...): UploadResult     │
└─────────────────────────────────┘
           │ delegates to
           ↓
┌─────────────────────────────────┐
│ <<interface>>                   │
│ ObjectStorageProvider           │
│ + getProviderType(): Type       │
│ + upload(...): UploadResult     │
└─────────────────────────────────┘
           △
           │ implements
           ├─────────────┬─────────────┐
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ MinioStorage │ │ S3Storage    │ │ OssStorage   │
│ Provider     │ │ Provider     │ │ Provider     │
└──────────────┘ └──────────────┘ └──────────────┘
```

#### 5.1.2 时序图：文件上传流程

```
Client -> ObjectStorageTemplate: upload(bucket, key, stream, metadata)
ObjectStorageTemplate -> RetryTemplate: execute(uploadTask)
RetryTemplate -> MinioStorageProvider: upload(...)
MinioStorageProvider -> MinIO: putObject(...)
MinIO --> MinioStorageProvider: ObjectWriteResponse
MinioStorageProvider --> RetryTemplate: UploadResult
RetryTemplate --> ObjectStorageTemplate: UploadResult
ObjectStorageTemplate -> Metrics: recordUploadSuccess(...)
ObjectStorageTemplate --> Client: UploadResult
```

#### 5.1.3 核心类设计

##### ObjectStorageTemplate

```java
public class ObjectStorageTemplate implements ObjectStorageOperations {

    private final ObjectStorageProvider provider;
    private final RetryTemplate retryTemplate;
    private final ObjectStorageMetrics metrics;

    /**
     * 上传文件，带重试和监控
     */
    @Override
    public UploadResult upload(
            String bucket,
            String key,
            InputStream inputStream,
            ObjectMetadata metadata) {

        return retryTemplate.execute(context -> {
            long start = System.currentTimeMillis();

            try {
                // 委托给具体提供商
                UploadResult result = provider.upload(bucket, key, inputStream, metadata);

                // 记录成功指标
                metrics.recordSuccess(bucket, System.currentTimeMillis() - start, result.getFileSize());

                return result;

            } catch (Exception e) {
                // 记录失败指标
                metrics.recordFailure(bucket, e.getClass().getSimpleName());
                throw new UploadFailedException("Upload failed", e);
            }
        });
    }
}
```

##### MinioStorageProvider

```java
@RequiredArgsConstructor
public class MinioStorageProvider implements ObjectStorageProvider {

    private final MinioClient minioClient;

    @Override
    public ProviderType getProviderType() {
        return ProviderType.MINIO;
    }

    @Override
    public UploadResult upload(String bucket, String key, InputStream stream, ObjectMetadata metadata) {
        try {
            // 确保 bucket 存在
            ensureBucketExists(bucket);

            // 上传对象
            ObjectWriteResponse response = minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(stream, metadata.getContentLength(), -1)
                    .contentType(metadata.getContentType())
                    .build()
            );

            return UploadResult.builder()
                .storageKey(bucket + "/" + key)
                .bucketName(bucket)
                .objectKey(key)
                .etag(response.etag())
                .fileSize(metadata.getContentLength())
                .build();

        } catch (Exception e) {
            throw new UploadFailedException("MinIO upload failed", e);
        }
    }

    private void ensureBucketExists(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(
            BucketExistsArgs.builder().bucket(bucket).build()
        );

        if (!exists) {
            minioClient.makeBucket(
                MakeBucketArgs.builder().bucket(bucket).build()
            );
            log.info("Created MinIO bucket: {}", bucket);
        }
    }
}
```

#### 5.1.4 配置属性

```java
@ConfigurationProperties("patra.object-storage")
@Data
public class ObjectStorageProperties {

    /**
     * 当前激活的提供商（minio/s3/oss）
     */
    private String activeProvider = "minio";

    /**
     * 提供商配置
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * 重试配置
     */
    private RetryConfig retry = new RetryConfig();

    @Data
    public static class ProviderConfig {
        private String endpoint;
        private String region;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }

    @Data
    public static class RetryConfig {
        private int maxAttempts = 3;
        private long waitDuration = 1000; // ms
    }
}
```

---

### 5.2 patra-storage 服务

#### 5.2.1 领域模型

##### FileMetadata (Aggregate Root)

```java
/**
 * 文件元数据聚合根
 */
@Getter
public class FileMetadata {

    private Long id;
    private StorageKey storageKey;     // VO: bucket + objectKey
    private FileSize fileSize;         // VO: 文件大小
    private FileChecksum checksum;     // VO: MD5/SHA256
    private BusinessContext context;   // VO: 业务上下文
    private StorageProvider provider;  // ENUM: MINIO/S3/OSS
    private FileStatus status;         // ENUM: ACTIVE/EXPIRED/DELETED
    private Instant uploadedAt;
    private Instant expiresAt;
    private Instant deletedAt;

    // 审计字段
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    /**
     * 工厂方法：创建新的文件元数据
     */
    public static FileMetadata create(
            StorageKey storageKey,
            FileSize fileSize,
            FileChecksum checksum,
            BusinessContext context,
            StorageProvider provider) {

        FileMetadata metadata = new FileMetadata();
        metadata.storageKey = storageKey;
        metadata.fileSize = fileSize;
        metadata.checksum = checksum;
        metadata.context = context;
        metadata.provider = provider;
        metadata.status = FileStatus.ACTIVE;
        metadata.uploadedAt = Instant.now();
        metadata.createdAt = Instant.now();

        return metadata;
    }

    /**
     * 标记为已删除（软删除）
     */
    public void markAsDeleted(String operator) {
        if (this.status == FileStatus.DELETED) {
            throw new IllegalStateException("File already deleted");
        }

        this.status = FileStatus.DELETED;
        this.deletedAt = Instant.now();
        this.updatedBy = operator;
        this.updatedAt = Instant.now();
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
```

##### Value Objects

```java
/**
 * 存储键值对象
 */
public record StorageKey(String bucket, String objectKey) {
    public StorageKey {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Bucket cannot be empty");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key cannot be empty");
        }
    }

    public String fullKey() {
        return bucket + "/" + objectKey;
    }
}

/**
 * 文件大小值对象
 */
public record FileSize(Long bytes) {
    public FileSize {
        if (bytes == null || bytes < 0) {
            throw new IllegalArgumentException("File size must be >= 0");
        }
    }

    public String humanReadable() {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

/**
 * 业务上下文值对象
 */
public record BusinessContext(
    String serviceName,
    String businessType,
    String businessId,
    Map<String, Object> correlationData
) {
    public BusinessContext {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Service name cannot be empty");
        }
    }
}
```

#### 5.2.2 应用层服务

```java
/**
 * 记录上传元数据用例
 */
@RequiredArgsConstructor
@Transactional
public class RecordUploadOrchestrator {

    private final FileMetadataRepository repository;

    public RecordUploadResult execute(RecordUploadCommand command) {

        // 1. 构建领域对象
        FileMetadata metadata = FileMetadata.create(
            new StorageKey(command.bucketName(), command.objectKey()),
            new FileSize(command.fileSize()),
            new FileChecksum(command.md5Hash(), null),
            new BusinessContext(
                command.serviceName(),
                command.businessType(),
                command.businessId(),
                command.correlationData()
            ),
            StorageProvider.valueOf(command.providerType())
        );

        // 2. 持久化
        FileMetadata saved = repository.save(metadata);

        log.info("File metadata recorded: id={}, storageKey={}",
            saved.getId(), saved.getStorageKey().fullKey());

        // 3. 返回结果
        return new RecordUploadResult(saved.getId(), saved.getUploadedAt());
    }
}
```

---

## 6. 数据库设计

### 6.1 ER 图

```
┌─────────────────────────────────────┐
│ storage_file_metadata               │
├─────────────────────────────────────┤
│ PK: id (BIGINT)                     │
│ UK: storage_key (VARCHAR 512)       │
│     bucket_name (VARCHAR 128)       │
│     object_key (VARCHAR 512)        │
│     file_size (BIGINT)              │
│     content_type (VARCHAR 128)      │
│     md5_hash (VARCHAR 64)           │
│     sha256_hash (VARCHAR 128)       │
│     service_name (VARCHAR 64)       │
│     business_type (VARCHAR 64)      │
│     business_id (VARCHAR 128)       │
│     correlation_data (JSON)         │
│     provider_type (VARCHAR 32)      │
│     file_status (VARCHAR 32)        │
│     uploaded_at (DATETIME)          │
│     expires_at (DATETIME)           │
│     deleted_at (DATETIME)           │
│     created_at (DATETIME)           │
│     created_by (VARCHAR 64)         │
│     updated_at (DATETIME)           │
│     updated_by (VARCHAR 64)         │
├─────────────────────────────────────┤
│ IDX: idx_service_business           │
│ IDX: idx_uploaded_at                │
│ IDX: idx_expires_at                 │
│ IDX: idx_file_status                │
└─────────────────────────────────────┘
```

### 6.2 索引策略

| 索引名 | 列 | 类型 | 用途 |
|-------|-----|------|------|
| PRIMARY | id | 主键 | 唯一标识 |
| UNIQUE | storage_key | 唯一索引 | 防止重复记录 |
| idx_service_business | (service_name, business_type, business_id) | 组合索引 | 按业务上下文查询 |
| idx_uploaded_at | uploaded_at | 普通索引 | 按时间范围查询 |
| idx_expires_at | expires_at | 普通索引 | 查询过期文件 |
| idx_file_status | file_status | 普通索引 | 按状态筛选 |

### 6.3 分区策略（可选，未来扩展）

当数据量超过 1000万 行时，可考虑按时间分区：

```sql
ALTER TABLE storage_file_metadata
PARTITION BY RANGE (YEAR(uploaded_at)) (
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p2025 VALUES LESS THAN (2026),
    PARTITION p2026 VALUES LESS THAN (2027),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

---

## 7. 接口设计

### 7.1 patra-storage REST API

#### 7.1.1 记录上传元数据

**Endpoint**: `POST /api/storage/files/record`

**Request**:
```json
{
  "storageKey": "dev-ingest/12345/batch-1.json",
  "bucketName": "dev-ingest",
  "objectKey": "12345/batch-1.json",
  "fileSize": 1048576,
  "contentType": "application/json",
  "md5Hash": "abc123...",
  "serviceName": "patra-ingest",
  "businessType": "literature_batch",
  "businessId": "12345",
  "correlationData": {
    "runId": 12345,
    "batchNo": 1,
    "provenanceCode": "pubmed"
  },
  "providerType": "MINIO",
  "expiresAt": "2025-12-31T23:59:59Z"
}
```

**Response** (201 Created):
```json
{
  "metadataId": 1001,
  "recordedAt": "2025-01-26T10:30:00Z"
}
```

**Error Response** (400 Bad Request):
```json
{
  "type": "https://api.papertrace.com/problems/invalid-request",
  "title": "Invalid Request",
  "status": 400,
  "detail": "File size must be greater than 0",
  "instance": "/api/storage/files/record"
}
```

#### 7.1.2 查询文件元数据

**Endpoint**: `GET /api/storage/files/{id}`

**Response** (200 OK):
```json
{
  "id": 1001,
  "storageKey": "dev-ingest/12345/batch-1.json",
  "bucketName": "dev-ingest",
  "objectKey": "12345/batch-1.json",
  "fileSize": 1048576,
  "fileSizeHumanReadable": "1.00 MB",
  "contentType": "application/json",
  "md5Hash": "abc123...",
  "serviceName": "patra-ingest",
  "businessType": "literature_batch",
  "businessId": "12345",
  "correlationData": {...},
  "providerType": "MINIO",
  "fileStatus": "ACTIVE",
  "uploadedAt": "2025-01-26T10:30:00Z",
  "expiresAt": "2025-12-31T23:59:59Z",
  "createdAt": "2025-01-26T10:30:00Z",
  "createdBy": "system"
}
```

#### 7.1.3 按业务上下文查询

**Endpoint**: `GET /api/storage/files/by-business?serviceName=patra-ingest&businessType=literature_batch&businessId=12345`

**Response** (200 OK):
```json
{
  "total": 5,
  "files": [
    {
      "id": 1001,
      "storageKey": "dev-ingest/12345/batch-1.json",
      "fileSize": 1048576,
      "uploadedAt": "2025-01-26T10:30:00Z"
    },
    // ...
  ]
}
```

#### 7.1.4 软删除文件

**Endpoint**: `DELETE /api/storage/files/{id}`

**Response** (204 No Content)

---

## 8. 部署架构

### 8.1 开发环境

```
┌─────────────────────────────────────────┐
│ Docker Compose (本地开发)                │
├─────────────────────────────────────────┤
│ - MySQL (patra_ingest, patra_storage)  │
│ - MinIO (localhost:9000)                │
│ - Nacos (localhost:8848)                │
│ - patra-ingest (localhost:8082)         │
│ - patra-storage (localhost:8085)        │
└─────────────────────────────────────────┘
```

**docker-compose.yml** (新增 MinIO):
```yaml
services:
  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data

  patra-storage:
    build: ./patra-storage/patra-storage-boot
    ports:
      - "8085:8085"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/patra_storage
    depends_on:
      - mysql

volumes:
  minio_data:
```

### 8.2 生产环境

```
┌─────────────────────────────────────────┐
│ Kubernetes Cluster                      │
├─────────────────────────────────────────┤
│ Namespace: papertrace-prod              │
│                                         │
│ ┌─────────────────┐ ┌─────────────────┐│
│ │ patra-ingest    │ │ patra-storage   ││
│ │ (Deployment)    │ │ (Deployment)    ││
│ │ replicas: 3     │ │ replicas: 2     ││
│ └─────────────────┘ └─────────────────┘│
│         │                   │           │
│         └───────┬───────────┘           │
│                 ↓                       │
│ ┌─────────────────────────────────────┐│
│ │ Ingress (NGINX)                     ││
│ │ TLS: *.papertrace.com               ││
│ └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
              ↓                 ↓
   ┌──────────────────┐ ┌───────────────┐
   │ AWS S3           │ │ RDS MySQL     │
   │ (生产对象存储)    │ │ (元数据数据库) │
   └──────────────────┘ └───────────────┘
```

### 8.3 部署清单

#### 8.3.1 patra-storage Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: patra-storage
  namespace: papertrace-prod
spec:
  replicas: 2
  selector:
    matchLabels:
      app: patra-storage
  template:
    metadata:
      labels:
        app: patra-storage
    spec:
      containers:
      - name: patra-storage
        image: registry.papertrace.com/patra-storage:1.0.0
        ports:
        - containerPort: 8085
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: patra-storage-secret
              key: database-url
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8085
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8085
          initialDelaySeconds: 30
          periodSeconds: 5
```

---

## 9. 监控与运维

### 9.1 监控指标

#### 9.1.1 对象存储 Starter 指标

| 指标名 | 类型 | 标签 | 描述 |
|-------|------|------|------|
| `patra.object_storage.upload.total` | Counter | provider, bucket, status | 上传总次数 |
| `patra.object_storage.upload.duration` | Histogram | provider, bucket | 上传耗时（秒）|
| `patra.object_storage.upload.size` | Histogram | provider, bucket | 文件大小（字节）|
| `patra.object_storage.download.total` | Counter | provider, bucket | 下载总次数 |
| `patra.object_storage.retry.count` | Counter | provider, bucket | 重试次数 |

#### 9.1.2 patra-storage 服务指标

| 指标名 | 类型 | 标签 | 描述 |
|-------|------|------|------|
| `patra.storage.metadata.record.total` | Counter | service, type, status | 元数据记录总次数 |
| `patra.storage.metadata.query.total` | Counter | type | 查询总次数 |
| `patra.storage.metadata.query.duration` | Histogram | type | 查询耗时 |
| `patra.storage.metadata.total` | Gauge | provider, status | 元数据总数 |

### 9.2 告警规则

#### 9.2.1 Prometheus 告警规则

```yaml
groups:
- name: object_storage_alerts
  rules:
  - alert: ObjectStorageUploadFailureRateHigh
    expr: |
      rate(patra_object_storage_upload_total{status="failure"}[5m])
      /
      rate(patra_object_storage_upload_total[5m])
      > 0.05
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Object storage upload failure rate > 5%"
      description: "Provider {{ $labels.provider }}, Bucket {{ $labels.bucket }}"

  - alert: ObjectStorageUploadLatencyHigh
    expr: |
      histogram_quantile(0.99,
        rate(patra_object_storage_upload_duration_bucket[5m])
      ) > 30
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Object storage P99 upload latency > 30s"

  - alert: MetadataRecordFailureRateHigh
    expr: |
      rate(patra_storage_metadata_record_total{status="failure"}[5m])
      /
      rate(patra_storage_metadata_record_total[5m])
      > 0.10
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "Metadata recording failure rate > 10%"
```

### 9.3 日志规范

#### 9.3.1 结构化日志格式

```json
{
  "timestamp": "2025-01-26T10:30:00.123Z",
  "level": "INFO",
  "logger": "com.patra.starter.objectstorage.ObjectStorageTemplate",
  "message": "File uploaded successfully",
  "traceId": "abc123",
  "spanId": "def456",
  "context": {
    "bucket": "dev-ingest",
    "objectKey": "12345/batch-1.json",
    "fileSize": 1048576,
    "provider": "MINIO",
    "duration": 1234
  }
}
```

#### 9.3.2 日志级别使用

- **ERROR**: 上传失败（重试后仍失败）、元数据记录失败
- **WARN**: 重试触发、降级处理、元数据记录失败（但文件上传成功）
- **INFO**: 上传成功、bucket 创建、提供商切换
- **DEBUG**: 重试详情、请求参数

### 9.4 链路追踪

集成 SkyWalking Agent，追踪完整链路：

```
patra-ingest
  └─ LiteraturePublisherAdapter.publish()
     ├─ ObjectStorageTemplate.upload()
     │  └─ MinioClient.putObject()
     └─ StorageClient.recordUpload()  (Feign)
        └─ patra-storage
           └─ RecordUploadOrchestrator.execute()
              └─ FileMetadataRepository.save()
```

---

## 10. 安全设计

### 10.1 访问控制

#### 10.1.1 MinIO IAM 策略

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": [
        "arn:aws:s3:::dev-ingest/*",
        "arn:aws:s3:::prod-ingest/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::dev-ingest",
        "arn:aws:s3:::prod-ingest"
      ]
    }
  ]
}
```

#### 10.1.2 API 鉴权

patra-storage REST API 通过 Spring Security + JWT 保护：

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/api/storage/**").authenticated()
            )
            .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt);

        return http.build();
    }
}
```

### 10.2 数据加密

#### 10.2.1 传输加密

- **HTTPS**: 所有 API 通信强制 TLS 1.2+
- **MinIO TLS**: 生产环境启用 MinIO TLS

#### 10.2.2 存储加密

- **SSE-S3**: AWS S3 服务端加密
- **SSE-KMS**: 使用 AWS KMS 密钥加密敏感数据

```yaml
# MinIO 配置（SSE）
patra:
  object-storage:
    providers:
      minio:
        encryption:
          enabled: true
          type: SSE-S3
```

### 10.3 敏感信息管理

- **密钥管理**: 使用 Nacos 加密配置或 Kubernetes Secret
- **审计日志**: 记录所有文件删除操作，保留 90 天

```java
@Aspect
@Component
public class FileOperationAuditAspect {

    @AfterReturning("execution(* com.patra.storage.adapter..*(..)) && @annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void auditDelete(JoinPoint jp) {
        // 记录删除操作到审计日志
        auditLogger.info("File deleted: user={}, file={}",
            SecurityContextHolder.getContext().getAuthentication().getName(),
            jp.getArgs());
    }
}
```

---

## 11. 性能优化

### 11.1 上传优化

#### 11.1.1 分片上传（大文件）

对于 > 100MB 的文件，使用分片上传：

```java
public UploadResult uploadLargeFile(String bucket, String key, File file) {
    long partSize = 5 * 1024 * 1024; // 5MB per part
    long fileSize = file.length();
    int partCount = (int) Math.ceil((double) fileSize / partSize);

    // 初始化分片上传
    String uploadId = minioClient.initiate MultipartUpload(...);

    // 并发上传分片
    List<CompletableFuture<PartETag>> futures = new ArrayList<>();
    for (int i = 0; i < partCount; i++) {
        int partNumber = i + 1;
        long offset = i * partSize;
        long size = Math.min(partSize, fileSize - offset);

        futures.add(CompletableFuture.supplyAsync(() ->
            uploadPart(bucket, key, uploadId, partNumber, file, offset, size)
        ));
    }

    // 等待所有分片完成
    List<PartETag> parts = futures.stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toList());

    // 完成分片上传
    minioClient.completeMultipartUpload(bucket, key, uploadId, parts);
}
```

#### 11.1.2 连接池优化

```yaml
patra:
  object-storage:
    providers:
      minio:
        connection-pool:
          max-connections: 100
          connection-timeout: 10000
          socket-timeout: 30000
```

### 11.2 查询优化

#### 11.2.1 数据库索引优化

参考 [6.2 索引策略](#62-索引策略)

#### 11.2.2 缓存策略

对热点查询（按 storageKey 查询）引入 Redis 缓存：

```java
@Cacheable(value = "file-metadata", key = "#storageKey")
public FileMetadataDTO getByStorageKey(String storageKey) {
    return repository.findByStorageKey(storageKey)
        .map(converter::toDTO)
        .orElseThrow(() -> new FileNotFoundException(storageKey));
}
```

**缓存配置**:
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1 hour
```

### 11.3 批量操作

支持批量记录元数据（减少网络往返）：

```java
@PostMapping("/files/record-batch")
public List<UploadRecordResponse> recordBatch(
        @RequestBody List<UploadRecordRequest> requests) {
    return orchestrator.executeBatch(requests);
}
```

---

## 12. 故障处理与降级

### 12.1 故障场景分析

| 故障场景 | 影响 | 处理策略 |
|---------|------|---------|
| MinIO 不可用 | 文件上传失败 | 重试 3 次 → 切换到 S3（如果配置）→ 返回错误 |
| patra-storage 不可用 | 元数据记录失败 | 降级：文件上传成功但元数据未记录，记录告警日志 |
| 数据库连接池耗尽 | 元数据查询慢 | 熔断器触发，返回默认响应 |
| 网络抖动 | 上传超时 | 自动重试（指数退避）|

### 12.2 降级策略

#### 12.2.1 元数据记录降级

```java
private void recordMetadata(UploadResult result, PublishContext context) {
    try {
        storageClient.recordUpload(request);
    } catch (FeignException e) {
        if (e.status() == 503) {
            // 服务不可用，降级处理
            log.warn("patra-storage unavailable, metadata not recorded");
            metrics.recordMetadataFailure("service_unavailable");

            // 可选：保存到本地 Outbox 表，稍后重试
            outboxRepository.save(createMetadataEvent(result, context));
        } else {
            throw e;
        }
    }
}
```

#### 12.2.2 熔断器配置

```yaml
resilience4j:
  circuitbreaker:
    instances:
      storage-client:
        failure-rate-threshold: 50  # 失败率 > 50% 时打开熔断器
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3
```

### 12.3 灾难恢复

#### 12.3.1 数据备份

- **对象存储**: MinIO 启用版本控制 + 异地复制
- **元数据库**: MySQL 主从复制 + 每日全量备份

#### 12.3.2 恢复流程

1. **对象存储恢复**: 从备份 Bucket 恢复
2. **元数据恢复**: 从 Binlog 重放事务
3. **一致性校验**: 对比对象存储和元数据库，修复不一致

---

## 13. 测试策略

### 13.1 测试金字塔

```
        /\
       /  \     E2E Tests (10%)
      /────\    - 端到端文件上传流程
     /      \   - 多服务集成测试
    /────────\
   /          \ Integration Tests (20%)
  /────────────\ - Repository 测试（TestContainers + MySQL）
 /              \- REST API 测试（MockMvc）
/────────────────\ Unit Tests (70%)
                  - ObjectStorageTemplate 测试（Mock Provider）
                  - Domain 模型测试（纯 Java）
```

### 13.2 单元测试示例

#### 13.2.1 ObjectStorageTemplate 测试

```java
class ObjectStorageTemplateTest {

    @Mock
    private ObjectStorageProvider provider;

    @Mock
    private RetryTemplate retryTemplate;

    @Mock
    private ObjectStorageMetrics metrics;

    @InjectMocks
    private ObjectStorageTemplate template;

    @Test
    void upload_should_delegate_to_provider() {
        // Given
        String bucket = "test-bucket";
        String key = "test.txt";
        InputStream stream = new ByteArrayInputStream("test".getBytes());
        ObjectMetadata metadata = ObjectMetadata.builder().contentLength(4L).build();

        UploadResult expected = UploadResult.builder()
            .storageKey(bucket + "/" + key)
            .fileSize(4L)
            .build();

        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            RetryCallback callback = invocation.getArgument(0);
            return callback.doWithRetry(null);
        });
        when(provider.upload(bucket, key, stream, metadata)).thenReturn(expected);

        // When
        UploadResult result = template.upload(bucket, key, stream, metadata);

        // Then
        assertThat(result).isEqualTo(expected);
        verify(provider).upload(bucket, key, stream, metadata);
        verify(metrics).recordSuccess(eq(bucket), anyLong(), eq(4L));
    }
}
```

#### 13.2.2 FileMetadata 聚合根测试

```java
class FileMetadataTest {

    @Test
    void create_should_initialize_with_active_status() {
        // When
        FileMetadata metadata = FileMetadata.create(
            new StorageKey("bucket", "key"),
            new FileSize(1024L),
            new FileChecksum("md5", null),
            new BusinessContext("service", "type", "id", Map.of()),
            StorageProvider.MINIO
        );

        // Then
        assertThat(metadata.getStatus()).isEqualTo(FileStatus.ACTIVE);
        assertThat(metadata.getUploadedAt()).isNotNull();
    }

    @Test
    void markAsDeleted_should_update_status_and_timestamp() {
        // Given
        FileMetadata metadata = createActiveMetadata();

        // When
        metadata.markAsDeleted("operator");

        // Then
        assertThat(metadata.getStatus()).isEqualTo(FileStatus.DELETED);
        assertThat(metadata.getDeletedAt()).isNotNull();
        assertThat(metadata.getUpdatedBy()).isEqualTo("operator");
    }

    @Test
    void markAsDeleted_should_throw_if_already_deleted() {
        // Given
        FileMetadata metadata = createActiveMetadata();
        metadata.markAsDeleted("operator");

        // When/Then
        assertThatThrownBy(() -> metadata.markAsDeleted("operator"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("File already deleted");
    }
}
```

### 13.3 集成测试示例

#### 13.3.1 Repository 测试（TestContainers）

```java
@SpringBootTest
@Testcontainers
class FileMetadataRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("testdb");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private FileMetadataRepository repository;

    @Test
    void save_and_find_by_storage_key() {
        // Given
        FileMetadata metadata = FileMetadata.create(...);

        // When
        FileMetadata saved = repository.save(metadata);
        Optional<FileMetadata> found = repository.findByStorageKey(
            new StorageKey("bucket", "key")
        );

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }
}
```

#### 13.3.2 REST API 测试（MockMvc）

```java
@WebMvcTest(StorageEndpointImpl.class)
class StorageEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecordUploadOrchestrator orchestrator;

    @Test
    void recordUpload_should_return_201() throws Exception {
        // Given
        UploadRecordRequest request = UploadRecordRequest.builder()
            .storageKey("bucket/key")
            .fileSize(1024L)
            .build();

        when(orchestrator.execute(any())).thenReturn(
            new RecordUploadResult(1L, Instant.now())
        );

        // When/Then
        mockMvc.perform(post("/api/storage/files/record")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.metadataId").value(1));
    }
}
```

### 13.4 E2E 测试

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class EndToEndTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Container
    static GenericContainer<?> minio = new GenericContainer<>("minio/minio")
        .withExposedPorts(9000)
        .withEnv("MINIO_ROOT_USER", "minioadmin")
        .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
        .withCommand("server /data");

    @Autowired
    private ObjectStorageTemplate storageTemplate;

    @Autowired
    private StorageClient storageClient;

    @Test
    void complete_upload_and_metadata_recording_flow() {
        // 1. 上传文件
        UploadResult uploadResult = storageTemplate.upload(
            "test-bucket",
            "test.txt",
            new ByteArrayInputStream("test".getBytes()),
            ObjectMetadata.builder().contentLength(4L).build()
        );

        assertThat(uploadResult.getStorageKey()).isNotNull();

        // 2. 记录元数据
        UploadRecordResponse metadataResponse = storageClient.recordUpload(
            UploadRecordRequest.builder()
                .storageKey(uploadResult.getStorageKey())
                .fileSize(4L)
                .serviceName("test")
                .build()
        );

        assertThat(metadataResponse.getMetadataId()).isNotNull();

        // 3. 查询元数据
        FileMetadataDTO metadata = storageClient.getByStorageKey(uploadResult.getStorageKey());
        assertThat(metadata.getFileSize()).isEqualTo(4L);
    }
}
```

---

## 14. 扩展性设计

### 14.1 新增对象存储提供商

以阿里云 OSS 为例：

#### 14.1.1 添加依赖

```xml
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.17.1</version>
</dependency>
```

#### 14.1.2 实现 Provider

```java
@Component
@RequiredArgsConstructor
public class OssStorageProvider implements ObjectStorageProvider {

    private final OSS ossClient;

    @Override
    public ProviderType getProviderType() {
        return ProviderType.OSS;
    }

    @Override
    public UploadResult upload(String bucket, String key, InputStream stream, ObjectMetadata metadata) {
        try {
            ObjectMetadata ossMetadata = new ObjectMetadata();
            ossMetadata.setContentLength(metadata.getContentLength());
            ossMetadata.setContentType(metadata.getContentType());

            PutObjectResult result = ossClient.putObject(bucket, key, stream, ossMetadata);

            return UploadResult.builder()
                .storageKey(bucket + "/" + key)
                .bucketName(bucket)
                .objectKey(key)
                .etag(result.getETag())
                .fileSize(metadata.getContentLength())
                .build();

        } catch (Exception e) {
            throw new UploadFailedException("OSS upload failed", e);
        }
    }
}
```

#### 14.1.3 添加配置

```java
@Configuration
@ConditionalOnProperty(name = "patra.object-storage.active-provider", havingValue = "oss")
public class OssAutoConfiguration {

    @Bean
    public OSS ossClient(ObjectStorageProperties properties) {
        var ossConfig = properties.getProviders().get("oss");
        return new OSSClientBuilder().build(
            ossConfig.getEndpoint(),
            ossConfig.getAccessKey(),
            ossConfig.getSecretKey()
        );
    }

    @Bean
    public OssStorageProvider ossStorageProvider(OSS ossClient) {
        return new OssStorageProvider(ossClient);
    }
}
```

#### 14.1.4 更新配置文件

```yaml
patra:
  object-storage:
    active-provider: oss
    providers:
      oss:
        endpoint: oss-cn-hangzhou.aliyuncs.com
        access-key: ${OSS_ACCESS_KEY}
        secret-key: ${OSS_SECRET_KEY}
```

### 14.2 扩展元数据字段

当需要新增业务字段时（如视频时长、图片分辨率）：

1. **数据库迁移**（Flyway）:
```sql
-- V2__add_video_duration.sql
ALTER TABLE storage_file_metadata
ADD COLUMN video_duration INT COMMENT '视频时长（秒）';
```

2. **更新 Domain 模型**:
```java
public class FileMetadata {
    private Integer videoDuration;  // 新增字段
}
```

3. **更新 DTO**:
```java
public class UploadRecordRequest {
    private Integer videoDuration;  // 新增字段
}
```

### 14.3 支持多租户

当需要支持多租户时：

1. **数据隔离**: 在 correlation_data 中添加 tenantId
2. **查询过滤**: Repository 自动添加 tenantId 过滤
3. **权限控制**: 只能查询本租户的文件

```java
@Repository
public class FileMetadataRepositoryImpl implements FileMetadataRepository {

    @Override
    public List<FileMetadata> findByBusiness(String service, String type, String id) {
        String tenantId = TenantContext.getCurrentTenantId();

        return baseMapper.selectList(
            new LambdaQueryWrapper<FileMetadataDO>()
                .eq(FileMetadataDO::getServiceName, service)
                .eq(FileMetadataDO::getBusinessType, type)
                .eq(FileMetadataDO::getBusinessId, id)
                .apply("JSON_EXTRACT(correlation_data, '$.tenantId') = {0}", tenantId)
        );
    }
}
```

---

## 15. 迁移方案

### 15.1 迁移策略

假设现有系统已有文件存储实现（如存储在本地文件系统），迁移到新系统的步骤：

#### 15.1.1 Phase 1: 双写（1-2 周）

```java
public PublishResult publish(List<StandardLiterature> literature, PublishContext context) {
    byte[] data = serialize(literature);

    // 1. 写入旧系统（保留）
    String oldPath = legacyFileStorage.save(data, context);

    // 2. 写入新系统（对象存储）
    UploadResult newResult = storageTemplate.upload(bucket, key, new ByteArrayInputStream(data), metadata);

    // 3. 记录元数据
    storageClient.recordUpload(...);

    return PublishResult.builder()
        .storageKey(newResult.getStorageKey())  // 返回新系统的 key
        .publishedCount(literature.size())
        .build();
}
```

#### 15.1.2 Phase 2: 灰度验证（1 周）

- 10% 流量：读取新系统
- 90% 流量：读取旧系统
- 对比数据一致性

#### 15.1.3 Phase 3: 全量切换（1 天）

- 100% 流量：读取新系统
- 停止写入旧系统
- 保留旧数据 3 个月后删除

### 15.2 数据回填

对于历史数据，编写迁移脚本：

```java
@Component
@Slf4j
public class DataMigrationJob {

    @Autowired
    private LegacyFileStorage legacyStorage;

    @Autowired
    private ObjectStorageTemplate storageTemplate;

    @Autowired
    private StorageClient storageClient;

    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点
    public void migrateHistoricalData() {
        List<LegacyFile> files = legacyStorage.findUnmigrated(1000);

        for (LegacyFile file : files) {
            try {
                // 1. 上传到对象存储
                byte[] data = legacyStorage.readBytes(file.getPath());
                UploadResult result = storageTemplate.upload(
                    bucket,
                    generateKey(file),
                    new ByteArrayInputStream(data),
                    buildMetadata(file)
                );

                // 2. 记录元数据
                storageClient.recordUpload(buildRequest(result, file));

                // 3. 标记为已迁移
                legacyStorage.markAsMigrated(file.getId());

                log.info("Migrated file: {}", file.getId());

            } catch (Exception e) {
                log.error("Migration failed for file: {}", file.getId(), e);
            }
        }
    }
}
```

---

## 16. FAQ

### Q1: 为什么 Starter 不依赖 patra-storage-api？

**A**: 基于以下架构原则：
1. **分层原则**: 框架层不应依赖应用层
2. **现有模式**: 项目中其他 Starter 均为纯技术抽象
3. **可测试性**: 避免单元测试变成集成测试
4. **扩展性**: 不同业务服务的上下文差异大，强行统一会造成抽象泄漏

详见 [ADR-001](#adr-001-starter-不应依赖业务服务-api)

### Q2: 元数据记录失败会影响文件上传吗？

**A**: 不会。元数据记录失败时：
1. 文件上传已成功
2. 记录 WARN 级别日志
3. 可选实现补偿机制（Outbox 表）

详见 [ADR-005](#adr-005-元数据记录失败不影响文件上传)

### Q3: 如何切换对象存储提供商？

**A**: 修改配置文件中的 `active-provider` 即可：

```yaml
patra:
  object-storage:
    active-provider: s3  # 从 minio 切换到 s3
```

无需修改代码，重启服务即生效。

### Q4: 如何支持新的对象存储提供商（如腾讯云 COS）？

**A**: 参考 [14.1 新增对象存储提供商](#141-新增对象存储提供商)，步骤：
1. 添加 SDK 依赖
2. 实现 `ObjectStorageProvider` 接口
3. 添加 AutoConfiguration
4. 更新配置文件

### Q5: 文件上传失败后会自动重试吗？

**A**: 会。默认重试 3 次，采用指数退避策略。可通过配置调整：

```yaml
patra:
  object-storage:
    retry:
      max-attempts: 5
      wait-duration: 2000
```

### Q6: 如何查询某个业务任务上传的所有文件？

**A**: 调用 patra-storage API：

```http
GET /api/storage/files/by-business?serviceName=patra-ingest&businessType=literature_batch&businessId=12345
```

### Q7: 元数据表会不会无限增长？

**A**: 可通过以下策略控制：
1. **软删除**: 定期清理已删除文件的元数据
2. **归档**: 将 1 年前的数据迁移到归档表
3. **分区**: 按时间分区（参考 [6.3](#63-分区策略可选未来扩展)）

### Q8: 如何监控上传成功率？

**A**: 通过 Prometheus 查询：

```promql
rate(patra_object_storage_upload_total{status="success"}[5m])
/
rate(patra_object_storage_upload_total[5m])
```

详见 [9.1 监控指标](#91-监控指标)

### Q9: 是否支持大文件上传？

**A**: 支持。对于 > 100MB 的文件，建议使用分片上传（参考 [11.1.1](#1111-分片上传大文件)）。

### Q10: 如何保证元数据和对象存储的一致性？

**A**: 通过以下机制：
1. **最终一致性**: 元数据记录失败时，通过 Outbox 表重试
2. **定期校验**: 定时任务对比对象存储和元数据库
3. **手动修复**: 提供管理 API 手动修复不一致

---

## 附录

### A. 术语表

| 术语 | 说明 |
|-----|------|
| Object Storage | 对象存储，用于存储非结构化数据（文件、图片等）|
| MinIO | 开源的 S3 兼容对象存储服务 |
| Starter | Spring Boot 自动配置模块 |
| Aggregate Root | DDD 中的聚合根，代表一组相关对象的根实体 |
| Feign Client | Spring Cloud 的声明式 HTTP 客户端 |
| Outbox Pattern | 保证消息发送和数据库事务一致性的模式 |

### B. 参考资料

1. [MinIO 官方文档](https://min.io/docs/)
2. [AWS S3 API 参考](https://docs.aws.amazon.com/AmazonS3/latest/API/)
3. [Spring Cloud AWS 文档](https://docs.awspring.io/spring-cloud-aws/)
4. [Domain-Driven Design (Eric Evans)](https://www.domainlanguage.com/ddd/)
5. [Hexagonal Architecture (Alistair Cockburn)](https://alistair.cockburn.us/hexagonal-architecture/)
6. [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)

### C. 变更历史

| 版本 | 日期 | 作者 | 变更说明 |
|-----|------|------|---------|
| v1.0 | 2025-01-26 | Jobs | 初始版本，完整设计方案 |

---

**文档结束**
