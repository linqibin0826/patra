# Papertrace Port 接口快速参考

## 按模块分类

### 1️⃣ patra-ingest-domain (7 个 Port)

| 接口名称 | 主要方法 | 职责 |
|---------|---------|------|
| **PatraRegistryPort** | `fetchConfig()` | 获取 Provenance/Operation 配置快照 |
| **StorageMetadataPort** | `recordUpload()` | 记录文件上传元数据到存储服务 |
| **LiteratureStoragePort** | `store()` | 存储文献到对象存储（S3/MinIO） |
| **ExpressionCompilerPort** | `compile()` | 编译摄入表达式为可执行查询 |
| **OutboxPublisherPort** | `publish()` | 发布 Outbox 消息到多种通道 |
| **PubmedSearchPort** | `preparePlanMetadata()` | 为规划阶段准备 PubMed 查询元数据 |
| **TechnicalRetryPort** | `publishRetry()` | 失败操作重试管理（Outbox 模式） |

### 2️⃣ patra-registry-domain (2 个 Port)

| 接口名称 | 主要方法 | 职责 |
|---------|---------|------|
| **ProvenanceConfigRepository** | `findActiveWindowOffset()` `findActivePagination()` `findActiveHttpConfig()` `loadConfiguration()` | 查询多维度 Provenance 配置 |
| **ExprRepository** | `loadSnapshot()` | 加载表达式字段/能力/渲染规则快照 |

### 3️⃣ patra-storage-domain (1 个 Port)

| 接口名称 | 主要方法 | 职责 |
|---------|---------|------|
| **FileMetadataRepository** | `save()` `findByStorageKey()` | 持久化文件元数据聚合根 |

---

## 调用关系图

```
patra-ingest-domain (应用服务)
  ├─→ PatraRegistryPort (获取配置)
  ├─→ StorageMetadataPort (记录元数据)
  ├─→ LiteratureStoragePort (存储文献)
  ├─→ ExpressionCompilerPort (编译表达式)
  ├─→ OutboxPublisherPort (发布消息)
  ├─→ PubmedSearchPort (查询元数据)
  └─→ TechnicalRetryPort (失败重试)

patra-registry-domain (领域模型)
  ├─→ ProvenanceConfigRepository (配置查询)
  └─→ ExprRepository (表达式快照)

patra-storage-domain (存储聚合)
  └─→ FileMetadataRepository (文件元数据)
```

---

## 核心模式

### 1. 配置管理港 (Port)
- **ProvenanceConfigRepository**: 9 个查询方法，支持多维度、时间维度配置
- **ExprRepository**: 1 个快照加载方法，时间有效

### 2. 存储港 (Port)
- **LiteratureStoragePort**: 单一 `store()` 方法处理序列化、校验和、上传
- **StorageMetadataPort**: 将上传元数据注册到存储目录
- **FileMetadataRepository**: 元数据持久化和查询

### 3. 处理港 (Port)
- **ExpressionCompilerPort**: 表达式编译为可执行形式
- **PubmedSearchPort**: 查询规划所需元数据

### 4. 消息港 (Port)
- **OutboxPublisherPort**: 可靠消息发送（多通道）
- **TechnicalRetryPort**: 技术错误重试（Outbox 模式）

---

## 最常用的三个 Port

### 1. PatraRegistryPort
```java
// 获取配置快照 - 应用服务必须调用
ProvenanceConfigSnapshot config = port.fetchConfig(
    ProvenanceCode.PUBMED,
    OperationCode.HARVEST
);
```

### 2. ProvenanceConfigRepository  
```java
// 查询多维度配置
Optional<HttpConfig> http = repo.findActiveHttpConfig(provenanceId, "HARVEST", Instant.now());
Optional<RetryConfig> retry = repo.findActiveRetry(provenanceId, "HARVEST", Instant.now());
Optional<ProvenanceConfiguration> full = repo.loadConfiguration(provenanceId, "HARVEST", Instant.now());
```

### 3. OutboxPublisherPort
```java
// 发布消息到 Outbox
port.publish(outboxMessage, relayPlan);
```

---

## 端口文件位置

```
/patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/
  ├── PatraRegistryPort.java
  ├── StorageMetadataPort.java
  ├── LiteratureStoragePort.java
  ├── ExpressionCompilerPort.java
  ├── OutboxPublisherPort.java
  ├── PubmedSearchPort.java
  └── TechnicalRetryPort.java

/patra-registry/patra-registry-domain/src/main/java/com/patra/registry/domain/port/
  ├── ProvenanceConfigRepository.java
  └── ExprRepository.java

/patra-storage/patra-storage-domain/src/main/java/com/patra/storage/domain/port/
  └── FileMetadataRepository.java
```

