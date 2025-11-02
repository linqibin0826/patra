# Papertrace-api Port 接口完整清单

## 查找范围
- patra-ingest-domain
- patra-registry-domain  
- patra-storage-domain (额外发现)

---

## 一、patra-ingest-domain Port 接口 (7个)

### 1. PatraRegistryPort
**位置**: `/patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/PatraRegistryPort.java`

**职责**: 
- 为应用服务提供访问 Patra Registry 的单一入口点
- 隐藏协议/客户端细节（Feign/HTTP/gRPC）

**方法签名**:
```java
// 获取指定 provenance/operation 的配置快照
ProvenanceConfigSnapshot fetchConfig(ProvenanceCode provenanceCode, OperationCode operationCode);
```

---

### 2. StorageMetadataPort
**位置**: `/patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/StorageMetadataPort.java`

**职责**: 
- 将上传元数据记录到 patra-storage 服务
- 与 patra-storage 微服务集成

**方法签名**:
```java
// 记录存储文件的上传元数据
MetadataResult recordUpload(MetadataRequest request);
```

**嵌套类型**:
```java
// 元数据请求
@Builder
record MetadataRequest(
    String storageKey,        // 完整存储标识符（bucket/key 组合）
    String bucketName,        // 对象存储桶名称
    String objectKey,         // 桶内对象密钥
    long fileSize,            // 文件大小（字节）
    String contentType,       // MIME 内容类型
    String md5,               // MD5 校验和（十六进制格式）
    String sha256,            // SHA-256 校验和（十六进制格式）
    String serviceName,       // 源服务名称
    String businessType,      // 业务类型分类
    String businessId,        // 用于关联的业务标识符
    Map<String, Object> correlation, // 额外相关元数据
    String providerType,      // 存储提供商类型（MINIO、S3 等）
    String remarks            // 审计备注
) {}

// 元数据结果
@Builder
record MetadataResult(
    Long metadataId,          // patra-storage 的目录记录标识符
    Instant recordedAt        // 记录时间戳
) {}
```

---

### 3. LiteratureStoragePort
**位置**: `/patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/LiteratureStoragePort.java`

**职责**:
- 将标准化的文献存储到对象存储
- 抽象 S3/MinIO 上传细节
- 序列化、校验和计算、物理上传

**方法签名**:
```java
// 将一批标准化文献存储到对象存储
StorageResult store(List<StandardLiterature> literature, StorageContext context);
```

**嵌套类型**:
```java
// 存储结果
@Builder
record StorageResult(
    String storageKey,        // 完整存储标识符（bucket/key 组合）
    String bucketName,        // 对象存储桶名称
    String objectKey,         // 桶内对象密钥
    long fileSize,            // 文件大小（字节）
    String md5,               // MD5 校验和（十六进制格式）
    String sha256,            // SHA-256 校验和（十六进制格式）
    int literatureCount       // 存储的文献项数
) {}

// 存储上下文
@Builder
record StorageContext(
    Long runId,               // 任务运行标识符
    int batchNo,              // 执行批次号
    String provenanceCode     // 规范化的源标识符
) {}
```

---

### 4. ExpressionCompilerPort
**位置**: `/patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/ExpressionCompilerPort.java`

**职责**:
- 将摄入表达式编译为可执行的查询和参数
- 委托给 patra-spring-boot-starter-expr 模块

**方法签名**:
```java
// 编译表达式
ExprCompilationResult compile(ExprCompilationRequest request);
```

---

### 5. OutboxPublisherPort
**位置**: `/patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/OutboxPublisherPort.java`

**职责**:
- 发布 Outbox 消息
- 抽象底层通道（MQ、webhook、S3 等）

**方法签名**:
```java
// 发布单个 Outbox 消息
void publish(OutboxMessage message, RelayPlan plan) throws Exception;
```

---

### 6. PubmedSearchPort
**位置**: `/patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/PubmedSearchPort.java`

**职责**:
- 为 PubMed 搜索元数据提供领域端口
- 为规划阶段提供编译的 PubMed 查询的元数据

**方法签名**:
```java
// 准备规划阶段所需的元数据
PlanMetadata preparePlanMetadata(
    String query,                                    // 编译后的布尔查询字符串
    JsonNode params,                                 // 编译后的参数 JSON
    ProvenanceConfigSnapshot provenanceConfigSnapshot // 当前执行的配置快照
);
```

---

### 7. TechnicalRetryPort
**位置**: `/patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/TechnicalRetryPort.java`

**职责**:
- 为失败操作的技术重试
- 将失败操作持久化到 Outbox
- 基础设施层通过此端口委托重试逻辑

**方法签名**:
```java
// 发布技术重试请求到 Outbox
void publishRetry(RetryContext context);
```

**嵌套类型**:
```java
// 技术重试上下文
@Builder
record RetryContext(
    String operationType,              // 操作类型标识符（如 "METADATA_RECORD"）
    Long aggregateId,                  // 聚合根标识符（用于分区和关联）
    String payload,                    // 序列化的操作负载（建议 JSON）
    Map<String, Object> metadata       // 额外元数据用于头部（traceId、provenanceCode 等）
) {}
```

---

## 二、patra-registry-domain Port 接口 (2个)

### 1. ExprRepository
**位置**: `/patra-registry/patra-registry-domain/src/main/java/com/patra/registry/domain/port/ExprRepository.java`

**职责**:
- 表达式相关领域对象的存储库端口
- 抽象表达式字段、能力、渲染规则和 API 参数映射的持久化

**方法签名**:
```java
// 为指定的 provenance 和 operation 范围加载聚合表达式快照
ExprSnapshot loadSnapshot(
    ProvenanceCode provenanceCode,  // 识别数据源的 provenance 代码
    String operationType,            // 操作类型（HARVEST/UPDATE/BACKFILL），可空用于跨操作查询
    String endpointName,             // 作用域配置的端点名称，可空用于端点无关查询
    Instant at                       // 用于时间过滤的有效时刻
);
```

---

### 2. ProvenanceConfigRepository
**位置**: `/patra-registry/patra-registry-domain/src/main/java/com/patra/registry/domain/port/ProvenanceConfigRepository.java`

**职责**:
- Provenance 配置领域对象的存储库端口
- 抽象 Provenance 元数据和相关操作配置的持久化
- 支持多维度配置查询（HTTP、重试、速率限制、分页、批处理、窗口偏移）

**方法签名**:
```java
// 1. 按唯一代码查找 provenance
Optional<Provenance> findProvenanceByCode(ProvenanceCode provenanceCode);

// 2. 检索所有注册的 provenance
List<Provenance> findAllProvenances();

// 3. 找到有效的窗口偏移配置
Optional<WindowOffsetConfig> findActiveWindowOffset(
    Long provenanceId,      // Provenance 标识符
    String operationType,   // 操作类型，可空用于操作无关查询
    Instant at             // 有效时刻用于时间过滤
);

// 4. 找到有效的分页配置
Optional<PaginationConfig> findActivePagination(
    Long provenanceId,      
    String operationType,   
    Instant at             
);

// 5. 找到有效的 HTTP 配置
Optional<HttpConfig> findActiveHttpConfig(
    Long provenanceId,      
    String operationType,   
    Instant at             
);

// 6. 找到有效的批处理配置
Optional<BatchingConfig> findActiveBatching(
    Long provenanceId,      
    String operationType,   
    Instant at             
);

// 7. 找到有效的重试配置
Optional<RetryConfig> findActiveRetry(
    Long provenanceId,      
    String operationType,   
    Instant at             
);

// 8. 找到有效的速率限制配置
Optional<RateLimitConfig> findActiveRateLimit(
    Long provenanceId,      
    String operationType,   
    Instant at             
);

// 9. 加载完整的 provenance 配置聚合根
Optional<ProvenanceConfiguration> loadConfiguration(
    Long provenanceId,      // Provenance 标识符
    String operationType,   // 操作类型
    Instant at             // 有效时刻
);
```

---

## 三、patra-storage-domain Port 接口 (1个) 

### 1. FileMetadataRepository
**位置**: `/patra-storage/patra-storage-domain/src/main/java/com/patra/storage/domain/port/FileMetadataRepository.java`

**职责**:
- 用于持久化 FileMetadata 聚合根的存储库抽象

**方法签名**:
```java
// 持久化提供的聚合根
FileMetadata save(FileMetadata metadata);

// 按规范存储密钥加载元数据
Optional<FileMetadata> findByStorageKey(StorageKey storageKey);
```

---

## 总结统计

| 模块 | Port 接口数量 | 主要职责 |
|------|-------------|---------|
| **patra-ingest-domain** | 7 | 数据摄入、存储、编译、重试管理 |
| **patra-registry-domain** | 2 | 配置存储库、表达式管理 |
| **patra-storage-domain** | 1 | 文件元数据管理 |
| **总计** | **10** | - |

### 按功能分类

**配置与注册表**:
- PatraRegistryPort
- ProvenanceConfigRepository
- ExprRepository

**存储与持久化**:
- LiteratureStoragePort
- StorageMetadataPort
- FileMetadataRepository

**处理与编译**:
- ExpressionCompilerPort
- PubmedSearchPort

**消息与重试**:
- OutboxPublisherPort
- TechnicalRetryPort

