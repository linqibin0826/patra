# Papertrace Port 接口索引

快速导航和查找 Papertrace 项目中所有的 Port 接口定义。

---

## 文档导航

本索引包含以下文档，按使用场景选择：

| 文档 | 用途 | 适合场景 |
|------|------|---------|
| **PORT_QUICK_REFERENCE.md** | 快速查询表 | 快速找接口、查方法签名 |
| **PORT_INTERFACES_SUMMARY.md** | 完整文档 | 深入理解每个接口的细节 |
| **PORT_DEPENDENCY_ANALYSIS.md** | 依赖分析 | 理解接口如何调用、如何协作 |
| **PORT_INTERFACES_INDEX.md** | 本文件 | 快速导航、找到正确文档 |

---

## 接口总览

### 全部 10 个 Port 接口列表

#### patra-ingest-domain (7 个)

1. **PatraRegistryPort** - 获取 Provenance 配置快照
2. **StorageMetadataPort** - 记录文件上传元数据  
3. **LiteratureStoragePort** - 存储文献到对象存储
4. **ExpressionCompilerPort** - 编译表达式为可执行查询
5. **OutboxPublisherPort** - 发布 Outbox 消息
6. **PubmedSearchPort** - 准备 PubMed 查询规划元数据
7. **TechnicalRetryPort** - 技术错误重试管理

#### patra-registry-domain (2 个)

1. **ProvenanceConfigRepository** - 查询多维度 Provenance 配置
2. **ExprRepository** - 加载表达式字段快照

#### patra-storage-domain (1 个)

1. **FileMetadataRepository** - 持久化文件元数据

---

## 快速查询

### 按功能查找

**配置管理相关**:
- `ProvenanceConfigRepository` - 查询各种配置（HTTP、重试、速率限制等）
- `ExprRepository` - 查询表达式字段和规则
- `PatraRegistryPort` - 获取完整配置快照

**存储相关**:
- `LiteratureStoragePort` - 存储文献到 S3/MinIO
- `StorageMetadataPort` - 注册文件元数据
- `FileMetadataRepository` - 持久化文件信息

**处理相关**:
- `ExpressionCompilerPort` - 编译表达式
- `PubmedSearchPort` - 查询规划元数据

**消息和重试**:
- `OutboxPublisherPort` - 可靠消息发送
- `TechnicalRetryPort` - 技术错误重试

### 按调用者查找

**应用服务 (Application Service)**:
```
需要注入的 Port:
├─ PatraRegistryPort (获取配置)
├─ ExpressionCompilerPort (编译)
├─ PubmedSearchPort (规划)
├─ LiteratureStoragePort (存储)
├─ StorageMetadataPort (记录元数据)
└─ OutboxPublisherPort (发送消息)
```

**存储库/查询服务 (Query Service)**:
```
需要注入的 Port:
├─ ProvenanceConfigRepository (查询配置)
└─ ExprRepository (查询表达式)
```

**基础设施适配器 (Infrastructure Adapter)**:
```
异常处理时调用:
└─ TechnicalRetryPort (处理失败重试)
```

### 按异常情况查找

**"找不到配置"**:
→ 检查 `ProvenanceConfigRepository.findActiveXxxConfig()` 返回 `Optional.empty()`

**"表达式编译失败"**:
→ 检查 `ExpressionCompilerPort.compile()` 返回的 `ExprCompilationResult`

**"文件上传到哪里"**:
→ 查看 `LiteratureStoragePort.store()` 返回的 `StorageResult`

**"消息发送失败"**:
→ 检查 `OutboxPublisherPort.publish()` 的异常处理

**"怎样处理技术错误"**:
→ 使用 `TechnicalRetryPort.publishRetry()` 将其持久化到 Outbox

---

## 文件位置快速查找

### patra-ingest-domain

```
patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/
├── PatraRegistryPort.java
├── StorageMetadataPort.java
├── LiteratureStoragePort.java
├── ExpressionCompilerPort.java
├── OutboxPublisherPort.java
├── PubmedSearchPort.java
└── TechnicalRetryPort.java
```

### patra-registry-domain

```
patra-registry/patra-registry-domain/src/main/java/com/patra/registry/domain/port/
├── ProvenanceConfigRepository.java
└── ExprRepository.java
```

### patra-storage-domain

```
patra-storage/patra-storage-domain/src/main/java/com/patra/storage/domain/port/
└── FileMetadataRepository.java
```

---

## 关键概念速查

### Port 是什么?

**Port** 是 Hexagonal Architecture (六边形架构) 中的核心概念：
- 定义在 **Domain 层** (纯 Java 接口，无框架依赖)
- 实现在 **Infrastructure 层** (具体的 Feign、MyBatis、S3 客户端等)
- 使用在 **Application 层** (应用服务、编排器)
- 主要目的: **依赖倒置**，让领域层独立于具体技术实现

### Port 的两种类型

1. **Query Port** - 读取数据的接口
   - `ProvenanceConfigRepository` 
   - `ExprRepository`
   - `FileMetadataRepository`

2. **Command Port** - 执行操作的接口
   - `PatraRegistryPort`
   - `StorageMetadataPort`
   - `LiteratureStoragePort`
   - `ExpressionCompilerPort`
   - `OutboxPublisherPort`
   - `PubmedSearchPort`
   - `TechnicalRetryPort`

### 时间维度查询

Registry 的 Port 支持时间过滤 (`Instant at`):
```java
// 查询在特定时刻有效的配置
Optional<HttpConfig> config = repo.findActiveHttpConfig(
    provenanceId, 
    operationType,
    Instant.now()  // 时间维度
);
```

这使得配置可以**按时间生效**，适应业务演变。

---

## 常见问题 (FAQ)

**Q: 如何注入 Port 到应用服务?**

A: 在应用服务中声明 Port 作为构造函数参数：
```java
@Service
public class IngestApplicationService {
    private final PatraRegistryPort registryPort;
    private final LiteratureStoragePort storagePort;
    
    public IngestApplicationService(
        PatraRegistryPort registryPort,
        LiteratureStoragePort storagePort) {
        this.registryPort = registryPort;
        this.storagePort = storagePort;
    }
}
```

**Q: Port 的实现在哪里?**

A: 在 `patra-*-infra` 或 `patra-*-adapter` 模块中，通常命名为 `*PortImpl` 或 `*Adapter`。

**Q: 如何添加新的 Port?**

A: 
1. 在相应的 `*-domain` 模块的 `port` 包中定义接口
2. 在相应的 `*-infra` 或 `*-adapter` 模块中实现
3. 在应用层的编排器中注入使用
4. 在 Spring 配置中注册为 Bean

**Q: Port 中为什么有嵌套的 record 类型?**

A: Record 类型（Java 14+）用于声明不可变的数据结构，包含：
- Request/Result/Context 等数据传输对象
- 通常使用 `@Builder` 提供构造器便利性

**Q: 如何处理 Port 调用的异常?**

A: 取决于 Port 设计：
- **抛出异常**: `OutboxPublisherPort`、`PatraRegistryPort` - 由调用者处理
- **返回 Result 对象**: `ExpressionCompilerPort` - 包含成功/失败标志
- **返回 Optional**: Repository ports - 查询不到时返回 empty

---

## 下一步

1. **快速了解**: 阅读 `PORT_QUICK_REFERENCE.md`
2. **深入学习**: 阅读 `PORT_INTERFACES_SUMMARY.md` 中感兴趣的接口
3. **理解协作**: 阅读 `PORT_DEPENDENCY_ANALYSIS.md` 看接口如何调用
4. **实战应用**: 打开对应的源文件，查看实际代码

---

## 相关资源

- **Java Backend Guidelines**: 查看 Hexagonal Architecture 和 DDD 的完整指南
- **源文件**: 所有接口都在上述文件位置
- **Implementation**: 检查 `*-infra` 或 `*-adapter` 模块了解实现细节

---

最后更新: 2025年11月2日

