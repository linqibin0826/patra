# Papertrace Port 接口依赖关系分析

## 一、分层架构中的 Port 位置

```
┌─────────────────────────────────────────────────────────────┐
│                   Adapter Layer (适配器层)                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ REST Controllers, Feign Clients, Job Adapters, etc.  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                          ↓ 实现 Port
┌─────────────────────────────────────────────────────────────┐
│                 Application Layer (应用层)                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │      Application Services (编排器、协调器)           │   │
│  │      依赖 → Port (定义)                              │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                          ↓ 使用 Port
┌─────────────────────────────────────────────────────────────┐
│                   Domain Layer (领域层)                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Domain Entities, Aggregates, Value Objects          │   │
│  │  Port Interfaces (定义)                              │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                          ↓ 依赖 Port
┌─────────────────────────────────────────────────────────────┐
│               Infrastructure Layer (基础设施层)              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Port 实现、Repository、RPC 客户端、数据库访问       │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、Port 接口依赖关系矩阵

### patra-ingest 相关的 Port 依赖

```
PatraRegistryPort (Domain Port)
  ├─ 调用方: IngestApplicationService (应用层)
  ├─ 实现方: PatraRegistryPortImpl (基础设施层 - Feign RPC)
  └─ 返回: ProvenanceConfigSnapshot
     └─ 使用方: 所有需要配置的应用服务

StorageMetadataPort (Domain Port)
  ├─ 调用方: StorageOrchestrator (应用层)
  ├─ 实现方: StorageMetadataPortImpl (基础设施层 - Feign RPC)
  ├─ 参数: MetadataRequest (bucket、object、checksum、业务信息)
  └─ 返回: MetadataResult (metadataId、recordedAt)

LiteratureStoragePort (Domain Port)
  ├─ 调用方: StorageOrchestrator (应用层)
  ├─ 实现方: LiteratureStoragePortImpl (基础设施层 - S3/MinIO 客户端)
  ├─ 参数: List<StandardLiterature>, StorageContext
  └─ 返回: StorageResult (storageKey、checksums、literatureCount)
     ├─ 触发: StorageMetadataPort.recordUpload()
     └─ 触发: OutboxPublisherPort.publish()

ExpressionCompilerPort (Domain Port)
  ├─ 调用方: ExpressionProcessingService (应用层)
  ├─ 实现方: ExpressionCompilerPortImpl (基础设施层)
  ├─ 依赖: patra-spring-boot-starter-expr
  └─ 参数: ExprCompilationRequest
     └─ 返回: ExprCompilationResult (query、params、errors)

PubmedSearchPort (Domain Port)
  ├─ 调用方: PlanningService (应用层)
  ├─ 实现方: PubmedSearchPortImpl (基础设施层 - PubMed API 客户端)
  ├─ 参数: query, params, ProvenanceConfigSnapshot
  └─ 返回: PlanMetadata (resultCount、webEnv handles)

OutboxPublisherPort (Domain Port)
  ├─ 调用方: 任何需要可靠消息发送的服务 (应用层)
  ├─ 实现方: OutboxPublisherImpl (基础设施层 - MQ/Webhook/S3)
  ├─ 参数: OutboxMessage, RelayPlan
  └─ 用途: 可靠事件发送、异步处理

TechnicalRetryPort (Domain Port)
  ├─ 调用方: 基础设施适配器 (异常处理)
  ├─ 实现方: TechnicalRetryPublisher (应用层编排)
  ├─ 参数: RetryContext
  └─ 用途: 技术错误持久化、Outbox 重试机制
```

### patra-registry 相关的 Port 依赖

```
ProvenanceConfigRepository (Repository Port)
  ├─ 定义位置: patra-registry-domain
  ├─ 调用方: 配置查询应用服务 (应用层)
  ├─ 实现方: MyBatis-Plus 实现 (基础设施层)
  ├─ 查询方法:
  │  ├─ findProvenanceByCode()
  │  ├─ findAllProvenances()
  │  ├─ findActiveWindowOffset()
  │  ├─ findActivePagination()
  │  ├─ findActiveHttpConfig()
  │  ├─ findActiveBatching()
  │  ├─ findActiveRetry()
  │  ├─ findActiveRateLimit()
  │  └─ loadConfiguration() [组合查询]
  └─ 特点: 时间维度查询、多维度配置管理

ExprRepository (Repository Port)
  ├─ 定义位置: patra-registry-domain
  ├─ 调用方: 表达式查询应用服务 (应用层)
  ├─ 实现方: MyBatis-Plus 实现 (基础设施层)
  ├─ 方法: loadSnapshot()
  │  ├─ 参数: provenanceCode, operationType, endpointName, at
  │  └─ 返回: ExprSnapshot (fields, capabilities, rules, mappings)
  └─ 特点: 聚合快照、时间有效性
```

### patra-storage 相关的 Port 依赖

```
FileMetadataRepository (Repository Port)
  ├─ 定义位置: patra-storage-domain
  ├─ 调用方: 存储应用服务 (应用层)
  ├─ 实现方: MyBatis-Plus 实现 (基础设施层)
  ├─ 方法:
  │  ├─ save(FileMetadata metadata) → FileMetadata
  │  └─ findByStorageKey(StorageKey) → Optional<FileMetadata>
  └─ 特点: 简单 CRUD 操作、聚合根持久化
```

---

## 三、调用流程示例

### 场景 1: 文献摄入和存储流程

```
IngestApplicationService
  │
  ├─→ PatraRegistryPort.fetchConfig(PUBMED, HARVEST)
  │   └─→ ProvenanceConfigSnapshot {
  │       ├─ httpConfig
  │       ├─ paginationConfig
  │       ├─ retryConfig
  │       └─ windowOffsetConfig
  │   }
  │
  ├─→ ExpressionCompilerPort.compile(expression)
  │   └─→ ExprCompilationResult {
  │       ├─ query
  │       └─ params
  │   }
  │
  ├─→ PubmedSearchPort.preparePlanMetadata(query, params, config)
  │   └─→ PlanMetadata {
  │       ├─ totalResults
  │       └─ webEnv
  │   }
  │
  └─→ LiteratureStoragePort.store(literature, context)
      │
      ├─→ StorageResult {
      │   ├─ storageKey
      │   ├─ md5/sha256
      │   └─ literatureCount
      │ }
      │
      ├─→ StorageMetadataPort.recordUpload(MetadataRequest)
      │   └─→ MetadataResult { metadataId, recordedAt }
      │
      └─→ OutboxPublisherPort.publish(outboxMessage, relayPlan)
          └─→ (异步消息处理)
```

### 场景 2: 配置查询流程

```
ConfigurationService
  │
  ├─→ ProvenanceConfigRepository.findProvenanceByCode(PUBMED)
  │   └─→ Optional<Provenance>
  │
  ├─→ ProvenanceConfigRepository.findActiveHttpConfig(provenanceId, HARVEST, now())
  │   └─→ Optional<HttpConfig>
  │
  ├─→ ProvenanceConfigRepository.findActivePagination(provenanceId, HARVEST, now())
  │   └─→ Optional<PaginationConfig>
  │
  └─→ ProvenanceConfigRepository.loadConfiguration(provenanceId, HARVEST, now())
      └─→ Optional<ProvenanceConfiguration> [完整聚合]
```

### 场景 3: 失败重试流程

```
ExternalServiceAdapter (Infrastructure)
  │
  ├─→ externalClient.call(request)  [失败]
  │
  └─→ TechnicalRetryPort.publishRetry(RetryContext {
      ├─ operationType: "EXTERNAL_CALL"
      ├─ aggregateId: requestId
      ├─ payload: serializedRequest
      └─ metadata: { traceId, provenanceCode }
    })
    │
    └─→ (应用层 OutboxPublisher 处理)
        └─→ OutboxPublisherPort.publish(message, plan)
```

---

## 四、Port 接口的关键特性

### 1. 方向性依赖 (Dependency Inversion)

```
✓ 正确的方向:
  Application Service → Domain Port ← Infrastructure Implementation

✗ 错误的方向:
  Application Service → Infrastructure directly (违反分层)
```

### 2. 无状态和线程安全

所有 Port 实现应该是:
- **无状态的**: 不持有会改变的字段
- **线程安全的**: 可以被并发调用
- **Spring 管理的**: @Component/@Bean，由 Spring 管理生命周期

### 3. 异常处理语义

```java
// PatraRegistryPort: 异常转换
if (httpStatus == 4xx) {
  throw new IngestConfigurationException(); // 不可恢复
} else if (httpStatus == 5xx) {
  return fallbackSnapshot(); // 可恢复，降级处理
}

// ExpressionCompilerPort: 直接委托
ExprCompilationResult result = compiler.compile(request);
if (!result.isSuccess()) {
  // 返回失败结果，让调用者处理
  return result;
}

// OutboxPublisherPort: 抛出异常
void publish(...) throws Exception {
  // 失败时抛出异常，让调用者决定重试或失败
}
```

### 4. 时间维度查询

Registry 相关的 Port 都支持时间查询:
```java
// 在指定时刻查询有效配置
Optional<HttpConfig> config = repo.findActiveHttpConfig(
    provenanceId,       // 实体标识
    operationType,      // 可选范围
    Instant.now()       // 时间维度
);
```

---

## 五、跨模块 Port 协作

```
patra-ingest-domain ──→ 调用 ──→ patra-registry-domain
                                     └─ PatraRegistryPort.fetchConfig()
                                        └─ 返回配置使用 Registry 的数据

patra-ingest-domain ──→ 调用 ──→ patra-storage-domain
                                     └─ StorageMetadataPort
                                        └─ 触发文件元数据记录

跨模块通信通过 Port 而不是直接依赖:
✓ patra-ingest-app 通过 PatraRegistryPort 调用 patra-registry-infra
✓ patra-ingest-infra 实现 StorageMetadataPort 调用 patra-storage-api (Feign)
```

