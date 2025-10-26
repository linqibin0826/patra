# MinIO 对象存储集成方案（架构修正版）

## 📋 方案概述

基于深度架构审查，采用**职责分离、依赖正确**的三层架构：

### 核心原则
✅ **Starter 保持纯技术特性**（不依赖业务服务 API）
✅ **业务集成在调用方完成**（遵循现有 patra-registry 模式）
✅ **接受适度重复**（Rule of Three，等 3 个使用方再抽象）

---

## 🏗️ 架构设计

### 组件关系图
```
┌──────────────────────────────────────────────────────┐
│  patra-ingest (业务服务)                              │
│  └─ patra-ingest-infra/                              │
│     └─ adapter/                                      │
│        └─ LiteraturePublisherAdapter.java            │
│           ├─ 注入 ObjectStorageTemplate (技术)       │
│           ├─ 注入 StorageClient (业务)               │
│           └─ 协调上传 + 元数据记录 (10-15行)         │
└──────────────────────────────────────────────────────┘
           ↓ 依赖                      ↓ 依赖
┌─────────────────────────┐   ┌──────────────────────┐
│ patra-spring-boot-      │   │ patra-storage-api    │
│ starter-object-storage  │   │                      │
│                         │   │ - StorageClient      │
│ - ObjectStorageTemplate │   │ - DTO                │
│ - MinIO/S3 Provider     │   │                      │
│ - 重试/监控              │   │ (业务契约)            │
│                         │   │                      │
│ (纯技术基础设施)         │   └──────────────────────┘
└─────────────────────────┘              ↓ Feign 调用
           ↓                   ┌──────────────────────┐
┌─────────────────────────┐   │ patra-storage        │
│ MinIO / S3              │   │ (独立微服务)          │
│ (对象存储服务)           │   │                      │
└─────────────────────────┘   │ - 元数据 CRUD        │
                               │ - 生命周期管理        │
                               │ - 独立数据库          │
                               └──────────────────────┘
```

---

## 📦 第一部分：patra-spring-boot-starter-object-storage

### 1.1 职责定位

**仅负责对象存储技术抽象，不涉及任何业务逻辑：**
- ✅ 提供 MinIO/S3/OSS 统一接口
- ✅ 重试、超时、异常转换
- ✅ 监控指标（上传耗时、失败率）
- ❌ **不包含**元数据管理
- ❌ **不依赖**任何业务服务 API

### 1.2 模块结构
```
patra-spring-boot-starter-object-storage/
├─ pom.xml
├─ src/main/java/com/patra/starter/objectstorage/
│  ├─ core/                                    # 核心抽象
│  │  ├─ ObjectStorageOperations.java         # 基础操作接口
│  │  ├─ ObjectStorageTemplate.java           # 模板实现（重试/监控）
│  │  ├─ ObjectMetadata.java                  # 文件元数据 VO
│  │  ├─ UploadRequest.java                   # 上传请求
│  │  └─ UploadResult.java                    # 上传结果
│  │
│  ├─ provider/                                # 提供商抽象（Strategy 模式）
│  │  ├─ ObjectStorageProvider.java           # 提供商 SPI
│  │  ├─ ProviderType.java                    # MINIO/S3/OSS/COS
│  │  ├─ minio/
│  │  │  ├─ MinioStorageProvider.java
│  │  │  └─ MinioProviderProperties.java
│  │  └─ s3/
│  │     ├─ S3StorageProvider.java
│  │     └─ S3ProviderProperties.java
│  │
│  ├─ config/
│  │  └─ ObjectStorageProperties.java         # 配置绑定
│  │
│  ├─ autoconfig/                              # 自动配置
│  │  ├─ ObjectStorageAutoConfiguration.java  # 主配置
│  │  ├─ MinioAutoConfiguration.java
│  │  └─ S3AutoConfiguration.java
│  │
│  ├─ error/                                   # 异常体系
│  │  ├─ ObjectStorageException.java
│  │  ├─ UploadFailedException.java
│  │  └─ ProviderUnavailableException.java
│  │
│  └─ metrics/                                 # 监控
│     └─ ObjectStorageMetrics.java
│
└─ src/main/resources/
   └─ META-INF/spring/
      └─ org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 1.3 核心接口设计

#### ObjectStorageOperations.java
```java
/**
 * 对象存储基础操作接口（类似 Spring 的 S3Operations）
 */
public interface ObjectStorageOperations {

    /**
     * 上传文件
     */
    UploadResult upload(String bucket, String key, InputStream inputStream, ObjectMetadata metadata);

    /**
     * 下载文件
     */
    InputStream download(String bucket, String key);

    /**
     * 删除文件
     */
    void delete(String bucket, String key);

    /**
     * 检查文件是否存在
     */
    boolean exists(String bucket, String key);

    /**
     * 获取文件元数据
     */
    ObjectMetadata getMetadata(String bucket, String key);
}
```

#### ObjectStorageTemplate.java（核心实现）
```java
/**
 * 对象存储模板类，提供重试、监控、异常转换。
 */
@Slf4j
@RequiredArgsConstructor
public class ObjectStorageTemplate implements ObjectStorageOperations {

    private final ObjectStorageProvider provider;  // 委托给具体提供商
    private final RetryTemplate retryTemplate;     // Resilience4j 重试
    private final ObjectStorageMetrics metrics;    // Micrometer 监控

    @Override
    public UploadResult upload(String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
        return retryTemplate.execute(context -> {
            long startTime = System.currentTimeMillis();
            try {
                // 委托给提供商实现
                UploadResult result = provider.upload(bucket, key, inputStream, metadata);

                // 记录监控指标
                metrics.recordUploadSuccess(
                    bucket,
                    System.currentTimeMillis() - startTime,
                    result.getFileSize()
                );

                log.info("File uploaded successfully: bucket={}, key={}, size={} bytes, etag={}",
                    bucket, key, result.getFileSize(), result.getEtag());

                return result;

            } catch (Exception e) {
                metrics.recordUploadFailure(bucket, e.getClass().getSimpleName());
                log.error("File upload failed: bucket={}, key={}", bucket, key, e);
                throw new UploadFailedException("Failed to upload file", e);
            }
        });
    }

    @Override
    public InputStream download(String bucket, String key) {
        // 类似实现
    }

    @Override
    public void delete(String bucket, String key) {
        // 类似实现
    }
}
```

#### ObjectStorageProvider.java（提供商 SPI）
```java
/**
 * 对象存储提供商抽象（Strategy 模式）
 */
public interface ObjectStorageProvider {

    /**
     * 提供商类型
     */
    ProviderType getProviderType();

    /**
     * 上传文件（具体实现）
     */
    UploadResult upload(String bucket, String key, InputStream inputStream, ObjectMetadata metadata);

    InputStream download(String bucket, String key);

    void delete(String bucket, String key);

    boolean exists(String bucket, String key);
}
```

#### MinioStorageProvider.java（MinIO 实现）
```java
@Slf4j
@RequiredArgsConstructor
public class MinioStorageProvider implements ObjectStorageProvider {

    private final MinioClient minioClient;

    @Override
    public ProviderType getProviderType() {
        return ProviderType.MINIO;
    }

    @Override
    public UploadResult upload(String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
        try {
            // 确保 bucket 存在
            ensureBucketExists(bucket);

            // 上传对象
            ObjectWriteResponse response = minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(inputStream, metadata.getContentLength(), -1)
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
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Created MinIO bucket: {}", bucket);
        }
    }
}
```

### 1.4 自动配置

#### ObjectStorageAutoConfiguration.java
```java
@AutoConfiguration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageAutoConfiguration {

    /**
     * 根据配置创建 ObjectStorageTemplate
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectStorageTemplate objectStorageTemplate(
            List<ObjectStorageProvider> providers,
            ObjectStorageProperties properties,
            RetryTemplate retryTemplate,
            ObjectStorageMetrics metrics) {

        // 根据 active-provider 选择提供商
        ObjectStorageProvider activeProvider = providers.stream()
            .filter(p -> p.getProviderType().name().equalsIgnoreCase(properties.getActiveProvider()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No provider found for: " + properties.getActiveProvider()));

        log.info("Configured ObjectStorageTemplate with provider: {}",
            activeProvider.getProviderType());

        return new ObjectStorageTemplate(activeProvider, retryTemplate, metrics);
    }

    /**
     * 配置重试模板
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryTemplate retryTemplate(ObjectStorageProperties properties) {
        // Resilience4j 重试配置
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(properties.getRetry().getMaxAttempts())
            .waitDuration(Duration.ofMillis(properties.getRetry().getWaitDuration()))
            .retryExceptions(IOException.class, TimeoutException.class)
            .build();

        return new RetryTemplate(Retry.of("object-storage", config));
    }

    /**
     * 配置监控指标
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectStorageMetrics objectStorageMetrics(MeterRegistry registry) {
        return new ObjectStorageMetrics(registry);
    }
}
```

### 1.5 pom.xml
```xml
<dependencies>
    <!-- patra-common -->
    <dependency>
        <groupId>com.papertrace</groupId>
        <artifactId>patra-common</artifactId>
    </dependency>

    <!-- Spring Boot 自动配置 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>

    <!-- MinIO SDK -->
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>8.5.7</version>
    </dependency>

    <!-- AWS S3 SDK（可选） -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>s3</artifactId>
        <version>2.20.26</version>
        <optional>true</optional>
    </dependency>

    <!-- Resilience4j 重试 -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-retry</artifactId>
    </dependency>

    <!-- Micrometer 监控 -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-core</artifactId>
    </dependency>

    <!-- ❌ 注意：不依赖 patra-storage-api！-->
</dependencies>
```

### 1.6 配置示例
```yaml
patra:
  object-storage:
    active-provider: minio  # 当前激活的提供商
    providers:
      minio:
        endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
        access-key: ${MINIO_ACCESS_KEY}
        secret-key: ${MINIO_SECRET_KEY}
      s3:
        region: ap-southeast-1
        access-key: ${AWS_ACCESS_KEY}
        secret-key: ${AWS_SECRET_KEY}
    retry:
      max-attempts: 3
      wait-duration: 1000  # ms
```

---

## 🏢 第二部分：patra-storage 独立微服务

### 2.1 模块结构（标准 Hexagonal）
```
patra-storage/
├─ patra-storage-api/                    # 外部契约
│  └─ src/main/java/.../storage/api/
│     ├─ StorageClient.java              # Feign 客户端接口
│     └─ dto/
│        ├─ UploadRecordRequest.java
│        ├─ UploadRecordResponse.java
│        └─ FileMetadataDTO.java
│
├─ patra-storage-domain/                 # 纯 Java 领域层
│  └─ src/main/java/.../storage/domain/
│     ├─ model/aggregate/
│     │  └─ FileMetadata.java            # 文件元数据聚合根
│     ├─ model/vo/
│     │  ├─ StorageKey.java
│     │  └─ FileChecksum.java
│     ├─ model/enums/
│     │  ├─ StorageProvider.java         # MINIO/S3/OSS
│     │  └─ FileStatus.java              # ACTIVE/EXPIRED/DELETED
│     └─ port/
│        └─ FileMetadataRepository.java  # Repository 端口
│
├─ patra-storage-app/                    # 应用层
│  └─ src/main/java/.../storage/app/
│     └─ usecase/
│        ├─ RecordUploadOrchestrator.java
│        ├─ QueryFileMetadataOrchestrator.java
│        └─ DeleteFileOrchestrator.java
│
├─ patra-storage-infra/                  # 基础设施层
│  └─ src/main/java/.../storage/infra/
│     └─ persistence/
│        ├─ entity/FileMetadataDO.java
│        ├─ mapper/FileMetadataMapper.java
│        ├─ converter/FileMetadataConverter.java
│        └─ repository/FileMetadataRepositoryMpImpl.java
│
├─ patra-storage-adapter/                # 适配器层
│  └─ src/main/java/.../storage/adapter/
│     └─ inbound/rest/
│        └─ StorageEndpointImpl.java     # REST API 实现
│
└─ patra-storage-boot/                   # 启动模块
   └─ src/main/java/.../PatraStorageApplication.java
```

### 2.2 patra-storage-api（Feign 契约）

#### StorageClient.java
```java
@FeignClient(name = "patra-storage", path = "/api/storage/files")
public interface StorageClient {

    /**
     * 记录文件上传元数据
     */
    @PostMapping("/record")
    UploadRecordResponse recordUpload(@RequestBody UploadRecordRequest request);

    /**
     * 查询文件元数据
     */
    @GetMapping("/{id}")
    FileMetadataDTO getById(@PathVariable Long id);

    /**
     * 按存储键查询
     */
    @GetMapping("/by-storage-key")
    FileMetadataDTO getByStorageKey(@RequestParam String storageKey);

    /**
     * 按业务上下文查询
     */
    @GetMapping("/by-business")
    List<FileMetadataDTO> getByBusiness(
        @RequestParam String serviceName,
        @RequestParam String businessType,
        @RequestParam String businessId
    );

    /**
     * 软删除文件
     */
    @DeleteMapping("/{id}")
    void delete(@PathVariable Long id);
}
```

#### UploadRecordRequest.java
```java
@Data
@Builder
public class UploadRecordRequest {
    // 存储信息
    private String storageKey;
    private String bucketName;
    private String objectKey;

    // 文件信息
    private Long fileSize;
    private String contentType;
    private String md5Hash;

    // 业务上下文
    private String serviceName;      // "patra-ingest"
    private String businessType;     // "literature_batch"
    private String businessId;       // "runId-12345"
    private Map<String, Object> correlationData;

    // 提供商
    private String providerType;     // "MINIO"

    // 生命周期
    private Instant expiresAt;
}
```

### 2.3 数据库设计

#### DDL（Flyway 迁移脚本）
```sql
-- V1__create_storage_file_metadata.sql
CREATE DATABASE IF NOT EXISTS patra_storage
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE patra_storage;

CREATE TABLE storage_file_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- 存储标识
    storage_key VARCHAR(512) NOT NULL UNIQUE COMMENT '完整存储键（bucket/key）',
    bucket_name VARCHAR(128) NOT NULL,
    object_key VARCHAR(512) NOT NULL,

    -- 文件信息
    file_size BIGINT NOT NULL COMMENT '文件大小（字节）',
    content_type VARCHAR(128),

    -- 校验信息
    md5_hash VARCHAR(64),
    sha256_hash VARCHAR(128),

    -- 业务上下文
    service_name VARCHAR(64) NOT NULL COMMENT '调用服务（如 patra-ingest）',
    business_type VARCHAR(64) COMMENT '业务类型（如 literature_batch）',
    business_id VARCHAR(128) COMMENT '业务 ID（如 runId）',
    correlation_data JSON COMMENT '关联数据（JSON）',

    -- 存储提供商
    provider_type VARCHAR(32) NOT NULL COMMENT 'MINIO/S3/OSS/COS',

    -- 状态
    file_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/DELETED',

    -- 生命周期
    uploaded_at DATETIME NOT NULL,
    expires_at DATETIME COMMENT '过期时间',
    deleted_at DATETIME COMMENT '删除时间（软删除）',

    -- 审计字段（统一标准）
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),

    -- 索引
    INDEX idx_service_business(service_name, business_type, business_id),
    INDEX idx_uploaded_at(uploaded_at),
    INDEX idx_expires_at(expires_at),
    INDEX idx_file_status(file_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象存储文件元数据';
```

---

## 🔗 第三部分：patra-ingest 集成（关键）

### 3.1 LiteraturePublisherAdapter（协调层）

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class LiteraturePublisherAdapter implements LiteraturePublisherPort {

  private final ObjectMapper objectMapper;
  private final ObjectStorageTemplate storageTemplate;  // 技术依赖
  private final StorageClient storageClient;            // 业务依赖

  @Value("${patra.ingest.storage.bucket}")
  private String bucket;

  @Value("${patra.ingest.storage.provider:MINIO}")
  private String providerType;

  @Override
  public PublishResult publish(List<StandardLiterature> literature, PublishContext context) {
    try {
      // 1. 序列化数据
      List<LiteratureDTO> payload = literature.stream()
          .map(this::toDto)
          .collect(Collectors.toList());
      byte[] serialized = objectMapper.writeValueAsBytes(payload);

      // 2. 生成存储键
      String objectKey = generateStorageKey(context);

      // 3. 上传到对象存储（使用 Starter）
      ObjectMetadata metadata = ObjectMetadata.builder()
          .contentType("application/json")
          .contentLength((long) serialized.length)
          .build();

      UploadResult uploadResult = storageTemplate.upload(
          bucket,
          objectKey,
          new ByteArrayInputStream(serialized),
          metadata
      );

      log.info("File uploaded to object storage: storageKey={}, size={} bytes",
          uploadResult.getStorageKey(), uploadResult.getFileSize());

      // 4. 记录元数据到 patra-storage（协调逻辑，10-15行）
      recordMetadata(uploadResult, context);

      return PublishResult.builder()
          .storageKey(uploadResult.getStorageKey())
          .publishedCount(payload.size())
          .build();

    } catch (Exception e) {
      log.error("Failed to publish literature", e);
      throw new LiteraturePublishException("Failed to publish literature", e);
    }
  }

  /**
   * 记录元数据（业务协调逻辑）
   */
  private void recordMetadata(UploadResult uploadResult, PublishContext context) {
    try {
      UploadRecordRequest recordRequest = UploadRecordRequest.builder()
          .storageKey(uploadResult.getStorageKey())
          .bucketName(uploadResult.getBucketName())
          .objectKey(uploadResult.getObjectKey())
          .fileSize(uploadResult.getFileSize())
          .contentType("application/json")
          .md5Hash(uploadResult.getEtag())
          // 业务上下文（领域特定）
          .serviceName("patra-ingest")
          .businessType("literature_batch")
          .businessId(String.valueOf(context.runId()))
          .correlationData(Map.of(
              "runId", context.runId(),
              "batchNo", context.batchNo(),
              "provenanceCode", context.provenanceCode()
          ))
          .providerType(providerType)
          .build();

      UploadRecordResponse response = storageClient.recordUpload(recordRequest);

      log.info("Metadata recorded: metadataId={}, storageKey={}",
          response.getMetadataId(), uploadResult.getStorageKey());

    } catch (Exception e) {
      // 元数据记录失败不影响文件上传成功
      // 可以选择：1. 记录到本地补偿表  2. 发送告警  3. 降级处理
      log.warn("Failed to record metadata, file upload succeeded but metadata not recorded: {}",
          e.getMessage());

      // TODO: 实现补偿机制（可选）
      // outboxRepository.save(createMetadataRecordEvent(uploadResult, context));
    }
  }

  private String generateStorageKey(PublishContext context) {
    return String.format("%s/%d/batch-%d.json",
        context.provenanceCode().toLowerCase(),
        context.runId(),
        context.batchNo());
  }

  // toDto() 方法保持不变...
}
```

### 3.2 patra-ingest 依赖配置

#### pom.xml
```xml
<dependencies>
    <!-- Object Storage Starter（纯技术） -->
    <dependency>
        <groupId>com.papertrace</groupId>
        <artifactId>patra-spring-boot-starter-object-storage</artifactId>
    </dependency>

    <!-- patra-storage API（业务集成） -->
    <dependency>
        <groupId>com.papertrace</groupId>
        <artifactId>patra-storage-api</artifactId>
    </dependency>

    <!-- Spring Cloud OpenFeign -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
</dependencies>
```

#### 配置（Nacos: patra-ingest.yml）
```yaml
patra:
  ingest:
    storage:
      bucket: ${ENV}-ingest        # dev-ingest / prod-ingest
      provider: ${STORAGE_PROVIDER:MINIO}

# Object Storage 配置（纯技术）
patra:
  object-storage:
    active-provider: ${STORAGE_PROVIDER:minio}
    providers:
      minio:
        endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
        access-key: ${MINIO_ACCESS_KEY}
        secret-key: ${MINIO_SECRET_KEY}
      s3:
        region: ap-southeast-1
        access-key: ${AWS_ACCESS_KEY}
        secret-key: ${AWS_SECRET_KEY}
    retry:
      max-attempts: 3
      wait-duration: 1000

# Feign Client 配置（业务集成）
feign:
  client:
    config:
      patra-storage:
        url: ${STORAGE_SERVICE_URL:http://localhost:8085}
        connect-timeout: 5000
        read-timeout: 10000
```

---

## 📊 架构对比分析

### ❌ 错误方案（原设计）
```
patra-ingest
  ↓ 依赖
ObjectStorageFacade (在 Starter 内)
  ↓ 内部调用
StorageClient (patra-storage-api)
```
**问题：** Starter 依赖业务 API，违反架构原则

### ✅ 正确方案（修正后）
```
patra-ingest
  ├─ ObjectStorageTemplate (技术)
  └─ StorageClient (业务)
  └─ 在 Adapter 协调两者 (10-15行)
```
**优势：** 职责清晰，依赖正确，易测试

---

## 🚀 实施步骤

### Phase 1: Object Storage Starter（2天）
1. ✅ 创建模块结构
2. ✅ 实现核心抽象（ObjectStorageOperations + Template）
3. ✅ 实现 MinioStorageProvider
4. ✅ 实现 S3StorageProvider
5. ✅ 配置 AutoConfiguration
6. ✅ 单元测试（不依赖外部服务）
7. ✅ 集成测试（TestContainers + MinIO）

### Phase 2: patra-storage 服务（2-3天）
1. ✅ 创建 5 层模块结构
2. ✅ 设计数据库表（Flyway）
3. ✅ 实现 patra-storage-api（Feign Client + DTO）
4. ✅ 实现 Domain 层（FileMetadata Aggregate）
5. ✅ 实现 Application 层（RecordUploadOrchestrator）
6. ✅ 实现 Infrastructure 层（MyBatis-Plus Repository）
7. ✅ 实现 Adapter 层（REST Endpoint）
8. ✅ 集成测试

### Phase 3: patra-ingest 集成（1天）
1. ✅ 添加依赖（starter + storage-api）
2. ✅ 修改 LiteraturePublisherAdapter（协调逻辑）
3. ✅ 配置 Nacos
4. ✅ 端到端测试

### Phase 4: Docker 环境（0.5天）
1. ✅ docker-compose.yml 添加 MinIO
2. ✅ 添加 patra_storage 数据库初始化
3. ✅ 本地环境验证

---

## ✅ 验收标准

1. **架构合规性**：Starter 不依赖任何业务服务 API ✅
2. **功能完整性**：文件上传成功 + 元数据正确记录 ✅
3. **多提供商支持**：可通过配置切换 MinIO/S3 ✅
4. **监控指标**：上传成功率、耗时、失败类型分布 ✅
5. **可测试性**：单元测试独立，集成测试使用 TestContainers ✅
6. **代码质量**：遵循 Google Java Style，编译通过 ✅

---

## 🎯 关键设计优势

✅ **依赖方向正确**：Starter 不依赖业务服务，符合分层原则
✅ **职责单一**：技术抽象 vs 业务能力，清晰分离
✅ **遵循现有模式**：与 patra-registry 集成方式一致
✅ **易于测试**：测试边界清晰，mock 简单
✅ **可扩展性强**：新增服务无需修改 Starter
✅ **接受适度重复**：10-15 行协调代码，可读性高于过度抽象

---

这是经过深度架构审查后的**正确方案**，符合项目架构风格和最佳实践！
