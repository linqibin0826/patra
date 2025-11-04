# patra-storage-domain — 领域层

## 概述

**patra-storage-domain** 是 patra-storage 服务的核心领域层,包含业务逻辑的核心抽象:聚合根、值对象、领域事件和仓储端口接口。

本模块严格遵循**领域驱动设计**(DDD)原则和**六边形架构**(Ports and Adapters)约束,是整个服务的业务核心,**完全独立于框架和基础设施**。所有业务规则、不变性约束和领域概念都在此模块中定义。

**核心原则**:
- **框架无关**: 禁止依赖 Spring、MyBatis 等框架,仅允许纯 Java 和 `patra-common-core`
- **业务聚焦**: 专注于业务概念和规则,不涉及技术实现细节
- **不可变性**: 值对象使用 `record` 类型,确保天然不可变
- **强制检查**: 通过 Maven Enforcer Plugin 强制检查依赖纯净性

## 核心职责

- **聚合根定义**: 定义 `FileMetadata` 聚合根,封装文件元数据的完整生命周期
- **值对象建模**: 定义 `StorageKey`、`FileSize`、`FileChecksum`、`BusinessContext` 等值对象
- **领域枚举**: 定义 `FileStatus`、`StorageProvider` 等领域枚举类型
- **端口接口**: 定义 `FileMetadataRepository` 仓储端口,供基础设施层实现
- **业务规则**: 封装幂等性检查、过期判断、软删除等业务逻辑

## 模块结构

```
patra-storage-domain/
└── src/main/java/com/patra/storage/domain/
    ├── model/
    │   ├── aggregate/
    │   │   └── FileMetadata.java           # 文件元数据聚合根
    │   ├── vo/
    │   │   ├── StorageKey.java             # 存储键值对象
    │   │   ├── FileSize.java               # 文件大小值对象
    │   │   ├── FileChecksum.java           # 校验和值对象
    │   │   └── BusinessContext.java        # 业务上下文值对象
    │   └── enums/
    │       ├── FileStatus.java             # 文件状态枚举
    │       └── StorageProvider.java        # 存储提供商枚举
    └── port/
        └── FileMetadataRepository.java     # 仓储端口接口
```

## 主要组件

### 聚合根

#### FileMetadata (文件元数据聚合根)

文件元数据的聚合根,管理文件的完整生命周期和业务逻辑。

**核心属性**:
```java
public class FileMetadata {
    private Long id;                        // 主键标识
    private StorageKey storageKey;          // 存储键(bucket/objectKey)
    private FileSize fileSize;              // 文件大小
    private String contentType;             // MIME 类型
    private FileChecksum checksum;          // 校验和(MD5/SHA256)
    private BusinessContext context;        // 业务上下文
    private StorageProvider provider;       // 存储提供商
    private FileStatus status;              // 文件状态
    private Instant uploadedAt;             // 上传时间
    private Instant expiresAt;              // 过期时间
    private Instant deletedAt;              // 删除时间
    private String recordRemarks;           // 审计备注
    private Long version;                   // 乐观锁版本
    private byte[] ipAddress;               // 请求者 IP
    // ... 审计字段
}
```

**工厂方法** - 创建新聚合:
```java
FileMetadata metadata = FileMetadata.create(
    new StorageKey("bucket", "key"),
    new FileSize(1024000),
    new FileChecksum("md5hash", "sha256hash"),
    new BusinessContext("patra-ingest", "literature_batch", "batch-001", Map.of()),
    StorageProvider.MINIO
);
```

**恢复方法** - 从数据库恢复:
```java
FileMetadata metadata = FileMetadata.restore(
    id, storageKey, fileSize, contentType, checksum, context,
    provider, status, uploadedAt, expiresAt, deletedAt,
    recordRemarks, version, ipAddress,
    createdAt, createdBy, createdByName,
    updatedAt, updatedBy, updatedByName, deleted
);
```

**核心行为**:
- `assignId(Long id)`: 分配数据库生成的 ID
- `updateVersion(Long version)`: 更新乐观锁版本
- `withContentType(String contentType)`: 配置 MIME 类型(Fluent API)
- `withExpiresAt(Instant expiresAt)`: 配置过期时间
- `withRecordRemarks(String remarks)`: 配置审计备注
- `withIpAddress(byte[] ip)`: 配置请求者 IP
- `markAsDeleted(Long operatorId, String operatorName)`: 软删除
- `isExpired()`: 判断是否过期
- `touchAudit(Long operatorId, String operatorName)`: 刷新审计信息

**设计模式**:
- **工厂方法**: `create()` 和 `restore()` 静态工厂,控制实例创建
- **不可变核心**: 核心属性(storageKey/fileSize/checksum/context)创建后不可变
- **Fluent API**: `with*()` 方法支持链式调用
- **防御性复制**: `ipAddress` 字段返回时进行防御性复制

### 值对象

#### StorageKey (存储键)

不可变的存储定位符,封装 bucket 和 objectKey。

```java
public record StorageKey(String bucket, String objectKey) {
    public StorageKey {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Bucket cannot be blank");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key cannot be blank");
        }
    }

    public String fullKey() {
        return bucket + '/' + objectKey;
    }

    public boolean matches(String otherBucket, String otherKey) {
        return Objects.equals(bucket, otherBucket) && Objects.equals(objectKey, otherKey);
    }
}
```

**特性**:
- **不可变性**: 使用 `record` 类型
- **规范化**: `fullKey()` 生成 `bucket/objectKey` 格式的规范键
- **验证**: 构造时检查 bucket 和 objectKey 非空

#### FileSize (文件大小)

封装文件大小的值对象,提供人类可读的格式化输出。

```java
public record FileSize(long bytes) {
    public FileSize {
        if (bytes < 0) {
            throw new IllegalArgumentException("File size must be >= 0 bytes");
        }
    }

    public String humanReadable() {
        // 返回 "1.23 MB" 格式
    }
}
```

**特性**:
- **验证**: 确保字节数非负
- **格式化**: `humanReadable()` 方法提供 "1.23 MB" 格式输出

#### FileChecksum (文件校验和)

封装文件完整性校验和的值对象。

```java
public record FileChecksum(String md5Hash, String sha256Hash) {
    public FileChecksum {
        if (md5Hash == null || md5Hash.isBlank()) {
            throw new IllegalArgumentException("MD5 hash cannot be blank");
        }
    }
}
```

**特性**:
- **必需 MD5**: MD5 校验和必填
- **可选 SHA256**: SHA256 校验和可选

#### BusinessContext (业务上下文)

封装文件关联的业务上下文信息。

```java
public record BusinessContext(
    String serviceName,
    String businessType,
    String businessId,
    Map<String, Object> correlationData
) {
    public BusinessContext {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Service name cannot be blank");
        }
        if (businessType == null || businessType.isBlank()) {
            throw new IllegalArgumentException("Business type cannot be blank");
        }
        if (businessId == null || businessId.isBlank()) {
            throw new IllegalArgumentException("Business ID cannot be blank");
        }
        correlationData = sanitize(correlationData);
    }
}
```

**字段说明**:
- `serviceName`: 调用服务名称(如 "patra-ingest")
- `businessType`: 业务分类(如 "literature_batch")
- `businessId`: 业务唯一标识(如 "batch-2024-01-15-001")
- `correlationData`: 关联元数据 JSON(如 `{"sourceId": "pubmed", "pmcId": "PMC12345678"}`)

**特性**:
- **强验证**: 所有核心字段必填
- **防御性复制**: `correlationData` 在构造时进行防御性复制和不可变包装

### 领域枚举

#### FileStatus (文件状态)

表示文件的生命周期状态。

```java
public enum FileStatus {
    ACTIVE,   // 活跃状态
    EXPIRED,  // 已过期(基于 expiresAt)
    DELETED   // 已软删除
}
```

#### StorageProvider (存储提供商)

表示对象存储提供商类型。

```java
public enum StorageProvider {
    MINIO("minio"),
    AWS_S3("s3"),
    ALIYUN_OSS("oss");

    private final String name;

    public static StorageProvider fromName(String name) {
        // 根据名称查找枚举
    }
}
```

### 仓储端口

#### FileMetadataRepository (仓储端口接口)

定义聚合根的持久化操作,由基础设施层实现。

```java
public interface FileMetadataRepository {
    FileMetadata save(FileMetadata metadata);
    Optional<FileMetadata> findByStorageKey(StorageKey storageKey);
}
```

**方法说明**:
- `save(FileMetadata)`: 保存或更新聚合根,返回持久化后的聚合(包含生成的 ID 和版本)
- `findByStorageKey(StorageKey)`: 通过存储键查询元数据,用于幂等性检查

**设计要点**:
- **端口接口**: 领域层定义接口,基础设施层提供实现(依赖倒置)
- **聚合操作**: 仅操作聚合根,不暴露内部实体
- **返回领域对象**: 返回类型为领域对象,而非 DO 或 DTO

## 依赖关系

### 上游依赖

- **patra-common-core**: Patra 通用基础库(仅限工具类,无框架依赖)
- **Lombok**: 代码生成工具(编译时依赖)
- **Hutool**: 纯 Java 工具库
- **JUnit/AssertJ**: 测试依赖

### 下游消费者

- **patra-storage-app**: 应用层用例编排器
- **patra-storage-infra**: 基础设施层仓储实现
- **patra-storage-adapter**: 适配器层(间接依赖)

### Maven Enforcer 检查

**强制禁止的依赖**:
```xml
<bannedDependencies>
    <excludes>
        <exclude>org.springframework:*</exclude>
        <exclude>org.springframework.boot:*</exclude>
        <exclude>org.springframework.cloud:*</exclude>
        <exclude>org.springframework.data:*</exclude>
        <exclude>jakarta.persistence:*</exclude>
        <exclude>jakarta.validation:*</exclude>
        <exclude>com.baomidou:*</exclude>
        <exclude>org.mybatis:*</exclude>
        <exclude>org.hibernate:*</exclude>
    </excludes>
    <searchTransitive>true</searchTransitive>
</bannedDependencies>
```

**检查时机**: Maven `validate` 阶段自动执行

## 设计模式与实践

### 聚合根设计模式

1. **私有构造器 + 静态工厂**: 控制实例创建,确保对象始终处于有效状态
2. **不可变核心**: 核心业务属性创建后不可变,仅允许配置可选属性
3. **行为丰富模型**: 聚合根封装业务行为(如 `markAsDeleted()`, `isExpired()`),而非贫血模型
4. **乐观锁**: 通过 `version` 字段支持并发控制

### 值对象设计模式

1. **record 类型**: 利用 Java 16+ record 特性,天然不可变
2. **规范化构造器**: 在构造器中进行验证和规范化
3. **防御性复制**: 对可变字段(如集合)进行防御性复制
4. **自包含验证**: 值对象自身保证有效性,外部无需二次验证

### 端口接口设计模式

1. **依赖倒置**: 领域层定义接口,基础设施层实现,实现依赖反转
2. **单一职责**: 每个仓储端口仅关注一个聚合根
3. **领域语言**: 接口方法名使用领域术语,而非技术术语(如 `save` 而非 `insert`)

## 使用示例

### 创建新聚合

```java
// 创建新的文件元数据
FileMetadata metadata = FileMetadata.create(
    new StorageKey("literature-files", "2024/01/pubmed/PMC12345678.pdf"),
    new FileSize(1024000),
    new FileChecksum("5d41402abc4b2a76b9719d911017c592", null),
    new BusinessContext(
        "patra-ingest",
        "literature_batch",
        "batch-2024-01-15-001",
        Map.of("sourceId", "pubmed", "pmcId", "PMC12345678")
    ),
    StorageProvider.MINIO
)
.withContentType("application/pdf")
.withExpiresAt(Instant.parse("2025-12-31T23:59:59Z"))
.withRecordRemarks("Initial literature batch upload");

// 保存到仓储(由基础设施层实现)
FileMetadata saved = repository.save(metadata);
```

### 查询与更新

```java
// 通过存储键查询
StorageKey key = new StorageKey("literature-files", "2024/01/pubmed/PMC12345678.pdf");
Optional<FileMetadata> existing = repository.findByStorageKey(key);

// 软删除
existing.ifPresent(metadata -> {
    metadata.markAsDeleted(1001L, "admin");
    repository.save(metadata);
});
```

### 过期检查

```java
if (metadata.isExpired()) {
    log.warn("文件已过期: {}, 过期时间: {}",
             metadata.getStorageKey().fullKey(),
             metadata.getExpiresAt());
}
```

## 业务规则

### 幂等性保证

- **唯一约束**: 通过 `storage_key` (bucket/objectKey) 确保数据库层面唯一性
- **领域检查**: 在保存前通过 `findByStorageKey()` 检查是否已存在

### 生命周期管理

1. **创建**: 初始状态为 `ACTIVE`, `uploadedAt` 设置为当前时间
2. **过期**: 当 `Instant.now() > expiresAt` 时,`isExpired()` 返回 true
3. **删除**: 调用 `markAsDeleted()` 后,状态变为 `DELETED`,`deletedAt` 记录删除时间

### 审计追溯

- **创建审计**: `createdAt`、`createdBy`、`createdByName` 在创建时设置
- **更新审计**: 每次修改后调用 `touchAudit()` 更新 `updatedAt`、`updatedBy`、`updatedByName`
- **IP 追踪**: 通过 `ipAddress` 记录请求者 IP(支持 IPv4/IPv6)

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 25 | 语言版本,使用 record 类型 |
| patra-common-core | 0.1.0-SNAPSHOT | Patra 通用基础库 |
| Lombok | 1.18.38+ | 代码生成(Getter/ToString) |
| Hutool | 5.8.36+ | 纯 Java 工具库 |
| JUnit 5 | 5.11.5+ | 单元测试框架 |
| AssertJ | 3.27.3+ | 流式断言库 |

## 最佳实践

### 领域层开发原则

1. **框架无关**: 绝不引入框架依赖,保持领域逻辑纯净
2. **业务聚焦**: 专注于业务概念和规则,不考虑技术实现
3. **自文档化**: 使用领域术语命名,代码即文档
4. **显式验证**: 在构造器中显式验证不变性,快速失败
5. **不可变优先**: 优先使用不可变对象,减少并发问题

### 测试策略

1. **单元测试**: 测试聚合根的业务行为和值对象的验证逻辑
2. **无需 Mock**: 领域层无外部依赖,测试无需 Mock 框架
3. **边界测试**: 重点测试验证逻辑和边界条件
4. **行为测试**: 测试业务行为(如 `markAsDeleted()`)而非 getter/setter

## 相关文档

- **应用层用例**: 参见 `patra-storage-app/README.md`
- **基础设施实现**: 参见 `patra-storage-infra/README.md`
- **API 契约**: 参见 `patra-storage-api/README.md`
