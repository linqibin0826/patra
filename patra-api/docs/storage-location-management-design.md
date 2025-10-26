# 存储位置管理设计方案

> **作者**: Claude (Jobs)
> **日期**: 2025-10-26
> **版本**: v1.0
> **状态**: 设计定稿

---

## 1. 背景与问题

### 1.1 当前实现的问题

在Papertrace项目中，对象存储（MinIO/S3）的bucket和objectKey管理存在以下问题：

#### 问题1: 硬编码泛滥
各个调用方（如`LiteraturePublisherAdapter`）都自行管理bucket和objectKey的生成逻辑，导致代码重复且难以维护：

```java
// patra-ingest/infra/adapter/LiteraturePublisherAdapter.java
private String resolveBucket() {
    String active = storageProperties.getActiveProvider();
    ObjectStorageProperties.ProviderConfig config = storageProperties.getProviders().get(active);
    if (config == null || !StringUtils.hasText(config.getBucket())) {
        throw new IllegalStateException("No bucket configured for provider " + active);
    }
    return config.getBucket();  // 返回 "dev-ingest" 这样的硬编码值
}

private String generateObjectKey(PublishContext context) {
    String provenance = safeProvenance(context.provenanceCode());
    long runId = context.runId() != null ? context.runId() : 0L;
    String businessId = String.format("%s-%d-batch-%03d", provenance, runId, context.batchNo());
    return ObjectKeyTemplate.generateDailyKey(
        "ingest", "literature-batch", businessId, LocalDate.now(), "json");
}
```

#### 问题2: 缺乏统一规范
- 不同服务使用不同的命名规则
- businessId的生成方式不一致（如`pubmed-1-1982409827952177154`难以理解）
- bucket配置分散在各处，难以统一管理

#### 问题3: 职责混淆
- ObjectKey中混入了businessId信息，但实际上businessId应该存储在数据库字段中
- 文件路径（ObjectKey）与业务标识（businessId）没有明确分离

### 1.2 改进目标

设计一套**规范化、自动化、解耦**的存储位置管理系统，满足以下要求：

1. **通用性**: 不针对特定业务类型，适用于所有服务
2. **简单性**: 使用固定格式规则，无需复杂的模板引擎
3. **自动化**: 从运行时环境自动获取environment、serviceName、date等信息
4. **分离关注点**:
   - ObjectKey负责文件路径/位置
   - businessId负责业务关联（存储在数据库）
   - correlationData负责业务上下文（存储在数据库JSON字段）
5. **场景覆盖**: 同时支持程序生成文件和用户直接上传文件

---

## 2. 设计原则

### 2.1 固定格式（No Template Engine）

用户明确反馈："不需要模版这么复杂的东西吧，直接固定就好了"。

**决策**: 不引入模板引擎（如`{env}`, `{service}`变量 + `hash()`, `seq()`函数），而是使用**固定字符串拼接**规则。

### 2.2 自动注入运行时信息

用户反馈："environment/serviceName/date/这些都不应该再让用户传递。可以直接从运行环境中解析出来"。

**决策**: 使用Spring `@Value`注解自动注入：
- `environment` ← `${spring.profiles.active:dev}`
- `serviceName` ← `${spring.application.name}`
- `date` ← `LocalDate.now()`（可选覆盖）

### 2.3 职责分离

用户澄清："object_key存储的应该是文件名，不应该保存businessId的，在数据库storage_file_metadata中，存储了business_id和business_type字段"。

**决策**:
- **ObjectKey**: 仅包含文件路径 `{businessType}/{yyyy}/{MM}/{dd}/{filename}`
- **businessId**: 作为独立字段存储到数据库
- **businessType**: 作为独立字段存储到数据库，同时用于ObjectKey路径分类
- **correlationData**: 业务上下文存储到数据库JSON字段

### 2.4 业务标识由调用方提供

用户反馈："business_id还是让用户自己传递吧。比如直接传snowFlakId"。

**决策**: 框架不生成businessId，由调用方根据业务逻辑提供（如Snowflake ID、runId等）。

---

## 3. 核心组件设计

### 3.1 StorageContext（输入）

调用方提供的存储上下文，包含最少必要信息：

```java
package com.patra.common.objectstorage;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Storage context provided by caller.
 *
 * <p>Contains minimal required information for storage location resolution:
 * <ul>
 *   <li><b>Path information</b>: businessType, filename
 *   <li><b>Business identifier</b>: businessId (stored in DB, not in path)
 *   <li><b>Business context</b>: correlationData (stored in DB JSON field)
 *   <li><b>Optional fields</b>: date (defaults to today)
 * </ul>
 *
 * <p><b>Usage Example</b>:
 * <pre>{@code
 * StorageContext context = StorageContext.builder()
 *     .businessType("literature-batch")
 *     .filename("batch-001.json")
 *     .businessId(String.valueOf(runId))
 *     .addCorrelation("provenance", "pubmed")
 *     .addCorrelation("runId", runId)
 *     .build();
 * }</pre>
 */
@Getter
@Builder(toBuilder = true)
public class StorageContext {

    // ========== Path information ==========

    /**
     * Business type for directory classification (required).
     * Used in ObjectKey path: {businessType}/{yyyy}/{MM}/{dd}/{filename}
     *
     * <p>Examples: "literature-batch", "contract-upload", "invoice-record"
     */
    private final String businessType;

    /**
     * Filename to store (required).
     * Can be:
     * <ul>
     *   <li>Generated: "batch-001.json", "report-20251026.pdf"
     *   <li>Original upload: "contract-v2.pdf" (sanitized)
     * </ul>
     */
    private final String filename;

    // ========== Business identifier ==========

    /**
     * Unique business identifier (required, stored in DB).
     * NOT included in ObjectKey path.
     *
     * <p>Examples: Snowflake ID, runId, orderId
     * <p>Purpose: Linking storage record back to business entity
     */
    private final String businessId;

    // ========== Business context ==========

    /**
     * Business correlation data (optional, stored in DB JSON field).
     *
     * <p>Use for business-specific fields like:
     * <ul>
     *   <li>provenance (for literature ingestion)
     *   <li>runId, batchNo (for batch processing)
     *   <li>userId, tenantId (for user uploads)
     * </ul>
     */
    @Builder.Default
    private final Map<String, Object> correlationData = new LinkedHashMap<>();

    // ========== Optional fields ==========

    /**
     * Partition date (optional, defaults to LocalDate.now()).
     * Used in ObjectKey path: {businessType}/{yyyy}/{MM}/{dd}/{filename}
     */
    @Builder.Default
    private final LocalDate date = LocalDate.now();

    // ========== Builder helper methods ==========

    public static class StorageContextBuilder {
        /**
         * Add a single correlation data entry.
         */
        public StorageContextBuilder addCorrelation(String key, Object value) {
            if (this.correlationData == null) {
                this.correlationData = new LinkedHashMap<>();
            }
            this.correlationData.put(key, value);
            return this;
        }

        /**
         * Add multiple correlation data entries.
         */
        public StorageContextBuilder addAllCorrelation(Map<String, Object> data) {
            if (this.correlationData == null) {
                this.correlationData = new LinkedHashMap<>();
            }
            if (data != null) {
                this.correlationData.putAll(data);
            }
            return this;
        }
    }

    // ========== Validation ==========

    /**
     * Validate required fields on build.
     */
    public void validate() {
        if (businessType == null || businessType.isBlank()) {
            throw new IllegalArgumentException("businessType is required");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename is required");
        }
        if (businessId == null || businessId.isBlank()) {
            throw new IllegalArgumentException("businessId is required");
        }
    }
}
```

### 3.2 StorageLocation（输出）

存储位置解析结果，包含完整的存储路径和业务标识：

```java
package com.patra.common.objectstorage;

import java.util.Collections;
import java.util.Map;

/**
 * Resolved storage location result.
 *
 * <p>Contains:
 * <ul>
 *   <li><b>bucket</b>: Storage bucket name
 *   <li><b>objectKey</b>: Object key within bucket (file path)
 *   <li><b>businessId</b>: Business identifier (for DB storage)
 *   <li><b>storageKey</b>: Canonical key in "bucket/objectKey" format
 *   <li><b>correlationData</b>: Business context (for DB JSON field)
 * </ul>
 */
public record StorageLocation(
    String bucket,
    String objectKey,
    String businessId,
    String storageKey,
    Map<String, Object> correlationData
) {

    /**
     * Primary constructor.
     */
    public StorageLocation(
        String bucket,
        String objectKey,
        String businessId,
        Map<String, Object> correlationData
    ) {
        this(
            bucket,
            objectKey,
            businessId,
            bucket + "/" + objectKey,
            correlationData != null ? Map.copyOf(correlationData) : Collections.emptyMap()
        );
    }

    /**
     * Compact constructor for validation.
     */
    public StorageLocation {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket cannot be blank");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey cannot be blank");
        }
        if (businessId == null || businessId.isBlank()) {
            throw new IllegalArgumentException("businessId cannot be blank");
        }
    }
}
```

### 3.3 StorageLocationResolver（核心解析器）

自动解析存储位置的核心组件：

```java
package com.patra.common.objectstorage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Core resolver for storage location (bucket + objectKey).
 *
 * <p><b>Fixed Format Rules</b>:
 * <ul>
 *   <li><b>Bucket</b>: {@code {env}-{service}} (e.g., "dev-ingest", "prod-storage")
 *   <li><b>ObjectKey</b>: {@code {businessType}/{yyyy}/{MM}/{dd}/{filename}}
 *       (e.g., "literature-batch/2025/10/26/batch-001.json")
 * </ul>
 *
 * <p><b>Auto-injection</b>:
 * <ul>
 *   <li>{@code environment} ← {@code spring.profiles.active}
 *   <li>{@code serviceName} ← {@code spring.application.name}
 *   <li>{@code date} ← {@code LocalDate.now()} (can be overridden in context)
 * </ul>
 *
 * <p><b>Separation of Concerns</b>:
 * <ul>
 *   <li><b>ObjectKey</b>: File location/path (stored in DB object_key field)
 *   <li><b>businessId</b>: Business association (stored in DB business_id field)
 *   <li><b>correlationData</b>: Business context (stored in DB correlation_data JSON field)
 * </ul>
 */
@Slf4j
@Component
public class StorageLocationResolver {

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd");

    @Value("${spring.profiles.active:dev}")
    private String environment;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * Resolve storage location from context.
     *
     * @param context storage context provided by caller
     * @return resolved storage location
     * @throws IllegalArgumentException if context is invalid
     */
    public StorageLocation resolve(StorageContext context) {
        context.validate();

        String bucket = resolveBucket();
        String objectKey = generateObjectKey(context);

        log.debug(
            "Resolved storage location: bucket={}, objectKey={}, businessId={}, businessType={}",
            bucket, objectKey, context.getBusinessId(), context.getBusinessType()
        );

        return new StorageLocation(
            bucket,
            objectKey,
            context.getBusinessId(),
            context.getCorrelationData()
        );
    }

    /**
     * Resolve bucket name using fixed format: {env}-{service}.
     *
     * <p><b>Examples</b>:
     * <ul>
     *   <li>dev-ingest (development environment, ingest service)
     *   <li>prod-storage (production environment, storage service)
     * </ul>
     *
     * @return bucket name
     */
    private String resolveBucket() {
        String bucket = environment + "-" + serviceName;
        log.trace("Resolved bucket: {} (env={}, service={})", bucket, environment, serviceName);
        return bucket;
    }

    /**
     * Generate object key using fixed format: {businessType}/{yyyy}/{MM}/{dd}/{filename}.
     *
     * <p><b>Note</b>: Does NOT include businessId - that's stored separately in database.
     *
     * <p><b>Examples</b>:
     * <ul>
     *   <li>literature-batch/2025/10/26/batch-001.json
     *   <li>contract-upload/2025/10/26/contract-v2.pdf
     * </ul>
     *
     * @param context storage context
     * @return object key (file path)
     */
    private String generateObjectKey(StorageContext context) {
        LocalDate date = context.getDate();
        String year = date.format(YEAR_FORMATTER);
        String month = date.format(MONTH_FORMATTER);
        String day = date.format(DAY_FORMATTER);

        String objectKey = String.format(
            "%s/%s/%s/%s/%s",
            context.getBusinessType(),
            year,
            month,
            day,
            context.getFilename()
        );

        log.trace(
            "Generated objectKey: {} (businessType={}, date={}, filename={})",
            objectKey, context.getBusinessType(), date, context.getFilename()
        );

        return objectKey;
    }
}
```

---

## 4. 固定格式规则

### 4.1 Bucket格式

**规则**: `{env}-{service}`

| 环境 | 服务 | Bucket示例 |
|------|------|-----------|
| dev | ingest | `dev-ingest` |
| test | ingest | `test-ingest` |
| prod | ingest | `prod-ingest` |
| dev | storage | `dev-storage` |
| prod | storage | `prod-storage` |

**注意**:
- 不在ObjectKey中重复service名称（用户反馈："不应该出现两个service"）
- 服务名称来自`spring.application.name`配置

### 4.2 ObjectKey格式

**规则**: `{businessType}/{yyyy}/{MM}/{dd}/{filename}`

**示例**:

| 场景 | businessType | 日期 | filename | ObjectKey |
|------|-------------|------|----------|-----------|
| Literature批量导入 | literature-batch | 2025-10-26 | batch-001.json | `literature-batch/2025/10/26/batch-001.json` |
| 用户上传合同 | contract-upload | 2025-10-26 | contract-v2.pdf | `contract-upload/2025/10/26/contract-v2.pdf` |
| 发票记录 | invoice-record | 2025-10-26 | INV-20251026.xml | `invoice-record/2025/10/26/INV-20251026.xml` |

**关键点**:
- **不包含businessId**: businessId存储在数据库`business_id`字段中，不混入ObjectKey路径
- **filename由调用方提供**: 可以是生成的（batch-001.json）或原始的（contract-v2.pdf，需清理）
- **businessType用于目录分类**: 不同业务类型存储在不同目录下

### 4.3 数据库存储结构

存储元数据记录到`storage_file_metadata`表：

| 字段 | 值示例 | 说明 |
|------|--------|------|
| `storage_key` | `dev-ingest/literature-batch/2025/10/26/batch-001.json` | 完整存储路径 |
| `bucket_name` | `dev-ingest` | Bucket名称 |
| `object_key` | `literature-batch/2025/10/26/batch-001.json` | 对象路径 |
| `business_id` | `1982409827952177154` | 业务标识（Snowflake ID / runId等） |
| `business_type` | `literature-batch` | 业务类型 |
| `correlation_data` | `{"provenance":"pubmed","runId":123,"batchNo":1}` | 业务上下文（JSON） |

**关键分离**:
- `object_key`: 文件位置（路径）
- `business_id`: 业务关联（用于反查业务记录）
- `business_type`: 业务分类
- `correlation_data`: 业务上下文元数据

---

## 5. 使用示例

### 5.1 场景1: Literature批量发布（生成文件）

**业务场景**: `patra-ingest`服务批量发布literature数据到对象存储。

#### 旧实现（硬编码）

```java
@Component
@RequiredArgsConstructor
public class LiteraturePublisherAdapter {

    private final ObjectStorageTemplate objectStorageTemplate;
    private final ObjectStorageProperties storageProperties;

    public PublishResult publish(List<StandardLiterature> literature, PublishContext context) {
        // 硬编码获取bucket
        String bucket = resolveBucket();

        // 硬编码生成objectKey
        String objectKey = generateObjectKey(context);

        // 上传文件
        UploadResult uploadResult = uploadPayload(bucket, objectKey, serialized, context);

        // ...
    }

    private String resolveBucket() {
        // 硬编码逻辑
        String active = storageProperties.getActiveProvider();
        ObjectStorageProperties.ProviderConfig config = storageProperties.getProviders().get(active);
        return config.getBucket();  // 返回 "dev-ingest"
    }

    private String generateObjectKey(PublishContext context) {
        // 硬编码业务逻辑
        String provenance = context.provenanceCode().toLowerCase();
        long runId = context.runId();
        String businessId = String.format("%s-%d-batch-%03d", provenance, runId, context.batchNo());

        return ObjectKeyTemplate.generateDailyKey(
            "ingest", "literature-batch", businessId, LocalDate.now(), "json");
        // 返回: ingest/literature-batch/2025/10/26/{businessId}.json
    }
}
```

**问题**:
- bucket和objectKey生成逻辑分散在调用方
- businessId混入ObjectKey路径
- 每个服务都要重复这些逻辑

#### 新实现（使用StorageLocationResolver）

```java
@Component
@RequiredArgsConstructor
public class LiteraturePublisherAdapter {

    private final ObjectStorageTemplate objectStorageTemplate;
    private final StorageLocationResolver storageLocationResolver;  // 注入解析器

    public PublishResult publish(List<StandardLiterature> literature, PublishContext context) {
        byte[] serialized = serializePayload(literature);

        // 1. 构建存储上下文（只提供业务信息）
        StorageContext storageContext = StorageContext.builder()
            .businessType("literature-batch")
            .filename(generateFilename(context))  // 例如: "batch-001.json"
            .businessId(String.valueOf(context.runId()))
            .addCorrelation("provenance", context.provenanceCode())
            .addCorrelation("runId", context.runId())
            .addCorrelation("batchNo", context.batchNo())
            .build();

        // 2. 自动解析存储位置
        StorageLocation location = storageLocationResolver.resolve(storageContext);

        // 3. 上传文件（使用解析后的bucket和objectKey）
        UploadResult uploadResult = uploadPayload(
            location.bucket(),
            location.objectKey(),
            serialized,
            context
        );

        // 4. 记录元数据到数据库
        recordMetadata(uploadResult, location);

        return PublishResult.builder()
            .storageKey(uploadResult.getStorageKey())
            .publishedCount(literature.size())
            .build();
    }

    private String generateFilename(PublishContext context) {
        // 业务逻辑：生成文件名
        return String.format("batch-%03d.json", context.batchNo());
    }

    private void recordMetadata(UploadResult uploadResult, StorageLocation location) {
        UploadRecordRequest request = new UploadRecordRequest(
            uploadResult.getBucketName(),
            uploadResult.getObjectKey(),
            uploadResult.getFileSize(),
            "application/json",
            uploadResult.getMd5Hash(),
            uploadResult.getSha256Hash(),
            "patra-ingest",                    // serviceName
            location.businessId(),              // businessId (从location获取)
            "literature-batch",                 // businessType
            location.correlationData(),         // correlationData (包含provenance等)
            "MINIO",
            null,
            null
        );
        storageClient.recordUpload(request);
    }
}
```

**改进点**:
- ✅ 无硬编码：bucket和objectKey由`StorageLocationResolver`自动生成
- ✅ 职责清晰：调用方只提供业务信息，不关心路径规则
- ✅ businessId分离：businessId存储在location中，不混入ObjectKey路径
- ✅ 可维护：修改路径规则只需改`StorageLocationResolver`

### 5.2 场景2: 用户上传文件（直接上传）

**业务场景**: 用户通过Web界面上传合同文件。

```java
@Service
@RequiredArgsConstructor
public class ContractUploadService {

    private final ObjectStorageTemplate objectStorageTemplate;
    private final StorageLocationResolver storageLocationResolver;
    private final ContractRepository contractRepository;

    @Transactional
    public ContractUploadResult uploadContract(
        MultipartFile file,
        Long contractId,
        Long userId
    ) {
        // 1. 清理原始文件名（防止路径注入攻击）
        String sanitizedFilename = sanitizeFilename(file.getOriginalFilename());

        // 2. 构建存储上下文
        StorageContext storageContext = StorageContext.builder()
            .businessType("contract-upload")
            .filename(sanitizedFilename)         // 使用原始文件名（已清理）
            .businessId(String.valueOf(contractId))
            .addCorrelation("userId", userId)
            .addCorrelation("originalFilename", file.getOriginalFilename())
            .addCorrelation("contentType", file.getContentType())
            .build();

        // 3. 自动解析存储位置
        StorageLocation location = storageLocationResolver.resolve(storageContext);

        // 4. 上传文件到对象存储
        ObjectMetadata metadata = ObjectMetadata.builder()
            .contentLength(file.getSize())
            .contentType(file.getContentType())
            .userMetadata(Map.of(
                "userId", String.valueOf(userId),
                "contractId", String.valueOf(contractId)
            ))
            .build();

        UploadResult uploadResult = objectStorageTemplate.upload(
            location.bucket(),
            location.objectKey(),
            file.getInputStream(),
            metadata
        );

        // 5. 更新合同记录
        Contract contract = contractRepository.findById(contractId)
            .orElseThrow(() -> new ContractNotFoundException(contractId));
        contract.setFileStorageKey(uploadResult.getStorageKey());
        contractRepository.save(contract);

        // 6. 记录存储元数据
        recordUploadMetadata(uploadResult, location, userId);

        return new ContractUploadResult(
            uploadResult.getStorageKey(),
            uploadResult.getFileSize()
        );
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be blank");
        }

        // 移除路径分隔符，防止路径遍历攻击
        String sanitized = filename.replaceAll("[/\\\\]", "_");

        // 限制文件名长度
        if (sanitized.length() > 200) {
            String extension = sanitized.substring(sanitized.lastIndexOf('.'));
            sanitized = sanitized.substring(0, 200 - extension.length()) + extension;
        }

        return sanitized;
    }

    private void recordUploadMetadata(
        UploadResult uploadResult,
        StorageLocation location,
        Long userId
    ) {
        UploadRecordRequest request = new UploadRecordRequest(
            uploadResult.getBucketName(),
            uploadResult.getObjectKey(),
            uploadResult.getFileSize(),
            uploadResult.getContentType(),
            uploadResult.getMd5Hash(),
            uploadResult.getSha256Hash(),
            "patra-contract",                  // serviceName
            location.businessId(),              // businessId (contractId)
            "contract-upload",                  // businessType
            location.correlationData(),         // correlationData (包含userId等)
            "MINIO",
            null,
            null
        );
        storageClient.recordUpload(request);
    }
}
```

**关键点**:
- ✅ 文件名清理：防止路径遍历攻击（如`../../etc/passwd`）
- ✅ 原始文件名保留：存储在`correlationData`中
- ✅ 统一路径规则：即使是用户上传，也遵循相同的目录结构
- ✅ 业务关联：通过`businessId`（contractId）关联到业务实体

---

## 6. 实施步骤

### 阶段1: 创建基础组件

**位置**: `patra-common/src/main/java/com/patra/common/objectstorage/`

**任务**:
1. 创建`StorageContext.java`（带Builder和验证）
2. 创建`StorageLocation.java`（record类型）
3. 创建`StorageLocationResolver.java`（核心解析器）
4. 编写单元测试

**验证**: 单元测试通过，编译成功

### 阶段2: 重构现有调用方

**重点文件**: `LiteraturePublisherAdapter.java`

**任务**:
1. 注入`StorageLocationResolver`
2. 替换`resolveBucket()`和`generateObjectKey()`方法调用
3. 修改`recordMetadata()`方法，使用`StorageLocation`中的`businessId`和`correlationData`
4. 删除旧的硬编码方法

**验证**: 集成测试通过，上传文件到MinIO成功，数据库记录正确

### 阶段3: 测试验证

**测试项**:
1. **单元测试**:
   - `StorageContextTest`: 验证Builder和验证逻辑
   - `StorageLocationResolverTest`: Mock environment和serviceName，验证路径生成

2. **集成测试**:
   - 使用TestContainers启动MinIO
   - 测试完整上传流程（StorageContext → StorageLocation → 上传 → 数据库记录）
   - 验证不同businessType的目录隔离

3. **手动测试**:
   - 启动`patra-ingest-boot`服务
   - 触发literature批量发布
   - 检查MinIO中的文件路径和数据库记录

### 阶段4: 推广到其他服务

**其他潜在调用方**:
- `patra-storage`: 文件上传服务
- `patra-archive`: 归档服务
- 未来的其他服务

**任务**: 逐步迁移各服务的硬编码逻辑到`StorageLocationResolver`

---

## 7. 迁移指南

### 7.1 代码对比

#### Before（硬编码）

```java
// 分散的逻辑
String bucket = resolveBucket();  // 硬编码读取配置
String objectKey = generateObjectKey(context);  // 硬编码拼接路径
String businessId = buildBusinessId(context);  // 硬编码生成businessId

UploadResult result = objectStorageTemplate.upload(bucket, objectKey, input, metadata);

UploadRecordRequest request = new UploadRecordRequest(
    result.getBucketName(),
    result.getObjectKey(),
    result.getFileSize(),
    "application/json",
    result.getMd5Hash(),
    result.getSha256Hash(),
    "patra-ingest",
    "literature-batch",
    businessId,  // 手动传递
    Map.of("provenance", context.provenanceCode()),  // 手动构建
    "MINIO",
    null,
    null
);
```

#### After（使用StorageLocationResolver）

```java
// 统一的解析流程
StorageContext storageContext = StorageContext.builder()
    .businessType("literature-batch")
    .filename("batch-001.json")
    .businessId(String.valueOf(context.runId()))
    .addCorrelation("provenance", context.provenanceCode())
    .addCorrelation("runId", context.runId())
    .build();

StorageLocation location = storageLocationResolver.resolve(storageContext);

UploadResult result = objectStorageTemplate.upload(
    location.bucket(),
    location.objectKey(),
    input,
    metadata
);

UploadRecordRequest request = new UploadRecordRequest(
    result.getBucketName(),
    result.getObjectKey(),
    result.getFileSize(),
    "application/json",
    result.getMd5Hash(),
    result.getSha256Hash(),
    "patra-ingest",
    "literature-batch",
    location.businessId(),           // 从location获取
    location.correlationData(),      // 从location获取
    "MINIO",
    null,
    null
);
```

### 7.2 迁移检查清单

- [ ] 识别所有硬编码的bucket/objectKey生成逻辑
- [ ] 识别所有硬编码的businessId生成逻辑
- [ ] 将业务信息整理到`StorageContext`
- [ ] 使用`StorageLocationResolver.resolve()`替换硬编码
- [ ] 更新数据库记录调用（使用`location.businessId()`和`location.correlationData()`）
- [ ] 删除旧的硬编码方法（`resolveBucket()`, `generateObjectKey()`, `buildBusinessId()`）
- [ ] 编写/更新单元测试
- [ ] 编写/更新集成测试
- [ ] 验证MinIO文件路径正确
- [ ] 验证数据库记录正确

---

## 8. FAQ

### Q1: 为什么不使用模板引擎？

**A**: 用户明确反馈："不需要模版这么复杂的东西吧，直接固定就好了"。模板引擎会增加复杂度，而固定格式规则足够满足需求。

### Q2: 为什么businessId不包含在ObjectKey中？

**A**: 用户澄清："object_key存储的应该是文件名，不应该保存businessId的"。ObjectKey负责文件路径，businessId负责业务关联，两者存储在数据库的不同字段中，职责分离更清晰。

### Q3: 如何支持不同业务类型？

**A**: 通过`businessType`字段实现目录隔离。例如：
- `literature-batch` → `literature-batch/2025/10/26/...`
- `contract-upload` → `contract-upload/2025/10/26/...`

每个业务类型有独立的目录结构。

### Q4: 如何处理文件名冲突？

**A**:
- **生成文件**: 由调用方生成唯一文件名（如加入batchNo、timestamp等）
- **用户上传**: 可以在文件名前加时间戳或UUID前缀，例如：`20251026143000_contract-v2.pdf`

### Q5: 如何支持多环境（dev/test/prod）？

**A**: 通过`spring.profiles.active`自动注入环境标识到bucket名称中：
- dev环境 → `dev-ingest`
- prod环境 → `prod-ingest`

无需调用方手动传递。

### Q6: 如何支持跨服务查询文件？

**A**: 通过数据库`storage_file_metadata`表查询：
- 通过`business_id`反查：`SELECT * FROM storage_file_metadata WHERE business_id = '1982409827952177154'`
- 通过`business_type`查询：`SELECT * FROM storage_file_metadata WHERE business_type = 'literature-batch'`
- 通过`correlation_data` JSON查询：`SELECT * FROM storage_file_metadata WHERE JSON_EXTRACT(correlation_data, '$.provenance') = 'pubmed'`

---

## 9. 附录

### 9.1 设计历程

本方案经过多轮迭代优化：

1. **第一版**: 针对literature-batch的专用生成器（被拒绝："不能单单针对这个业务设计"）
2. **第二版**: 引入模板引擎支持变量和函数（被拒绝："不需要模版这么复杂的东西"）
3. **第三版**: 固定格式 + 自动注入，但businessId在ObjectKey中（被纠正："object_key不应该保存businessId"）
4. **最终版**: 固定格式 + 职责分离（ObjectKey仅包含路径，businessId独立存储）

### 9.2 相关文档

- [对象存储设计文档](./object-storage-design.md)
- [对象存储集成方案](./object-storage-integration-plan.md)
- [架构设计文档](./ARCHITECTURE.md)
- [开发指南](./DEV-GUIDE.md)

### 9.3 数据库Schema参考

```sql
CREATE TABLE IF NOT EXISTS `storage_file_metadata` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
    `storage_key`      VARCHAR(768)    NOT NULL COMMENT 'Full storage key: bucket/objectKey',
    `bucket_name`      VARCHAR(128)    NOT NULL COMMENT 'Bucket name',
    `object_key`       VARCHAR(512)    NOT NULL COMMENT 'Object key within bucket',
    `file_size`        BIGINT          NOT NULL COMMENT 'File size in bytes',
    `content_type`     VARCHAR(128)    NULL COMMENT 'MIME type',
    `md5_hash`         VARCHAR(64)     NOT NULL COMMENT 'MD5 checksum',
    `sha256_hash`      VARCHAR(128)    NULL COMMENT 'SHA-256 checksum',
    `service_name`     VARCHAR(64)     NOT NULL COMMENT 'Calling service name',
    `business_type`    VARCHAR(64)     NOT NULL COMMENT 'Business classification',
    `business_id`      VARCHAR(128)    NOT NULL COMMENT 'Business identifier',
    `correlation_data` JSON            NULL COMMENT 'Structured correlation metadata',
    `provider_type`    VARCHAR(32)     NOT NULL COMMENT 'Storage provider type',
    `file_status`      VARCHAR(32)     NOT NULL COMMENT 'Lifecycle status',
    `uploaded_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- ... (audit fields omitted)
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_storage_key` (`storage_key`),
    KEY `idx_business` (`business_type`, `business_id`),
    KEY `idx_uploaded_at` (`uploaded_at`)
) ENGINE = InnoDB COMMENT = 'File metadata for object storage';
```

---

**文档结束**
