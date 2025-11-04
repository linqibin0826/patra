# patra-storage-app — 应用层

## 概述

**patra-storage-app** 是 patra-storage 服务的应用层,负责用例编排和事务管理。本模块作为领域层和外部世界的桥梁,将来自适配器层的请求转换为领域操作,并协调多个领域对象完成业务用例。

在六边形架构中,应用层扮演**用例编排器**(Use Case Orchestrator)的角色,封装应用特定的业务流程,但不包含核心业务规则(业务规则在领域层)。

**核心原则**:
- **薄应用层**: 专注于编排和协调,不包含业务逻辑
- **事务边界**: 定义事务边界,确保数据一致性
- **领域驱动**: 调用领域对象执行业务操作
- **无状态**: 用例编排器本身无状态,仅依赖领域对象和仓储

## 核心职责

- **用例编排**: 协调领域对象和仓储完成业务用例
- **事务管理**: 通过 `@Transactional` 注解定义事务边界
- **命令转换**: 将适配器层的命令对象转换为领域操作
- **结果封装**: 将领域操作结果封装为应用层响应对象
- **异常处理**: 捕获并转换领域层异常为应用层异常

## 模块结构

```
patra-storage-app/
└── src/main/java/com/patra/storage/app/
    └── recordupload/
        ├── RecordUploadOrchestrator.java   # 记录上传用例编排器
        ├── RecordUploadCommand.java        # 记录上传命令对象
        └── RecordUploadResult.java         # 记录上传结果对象
```

## 主要组件

### RecordUploadOrchestrator (记录上传编排器)

核心用例编排器,负责将文件上传记录持久化到数据库。

```java
@Service
@RequiredArgsConstructor
public class RecordUploadOrchestrator {

    private final FileMetadataRepository repository;

    @Transactional
    public RecordUploadResult execute(RecordUploadCommand command) {
        // 1. 构造领域值对象
        StorageKey storageKey = new StorageKey(command.bucketName(), command.objectKey());
        FileSize fileSize = new FileSize(command.fileSize());
        FileChecksum checksum = new FileChecksum(command.md5Hash(), command.sha256Hash());
        BusinessContext context = new BusinessContext(
            command.serviceName(),
            command.businessType(),
            command.businessId(),
            command.correlationData()
        );
        StorageProvider provider = StorageProvider.fromName(command.providerType());

        // 2. 创建聚合根
        FileMetadata metadata = FileMetadata.create(storageKey, fileSize, checksum, context, provider)
            .withContentType(command.contentType())
            .withExpiresAt(command.expiresAt())
            .withRecordRemarks(command.recordRemarks())
            .withIpAddress(command.ipAddress());

        // 3. 持久化聚合根
        FileMetadata saved = repository.save(metadata);

        // 4. 返回结果
        return new RecordUploadResult(saved.getId(), saved.getUploadedAt());
    }
}
```

**设计要点**:
- **@Transactional**: 定义事务边界,确保数据一致性
- **命令驱动**: 接受 `RecordUploadCommand` 命令对象
- **领域编排**: 调用领域对象的工厂方法和行为方法
- **结果封装**: 返回 `RecordUploadResult` 而非领域对象

**典型流程**:
1. **验证命令**: 命令对象已由适配器层验证
2. **构造值对象**: 将命令字段转换为领域值对象
3. **创建聚合根**: 调用聚合根的静态工厂方法
4. **配置可选属性**: 通过 Fluent API 配置可选属性
5. **持久化**: 调用仓储保存聚合根
6. **返回结果**: 封装结果对象并返回

### RecordUploadCommand (记录上传命令)

应用层命令对象,封装用例所需的所有参数。

```java
public record RecordUploadCommand(
    String bucketName,
    String objectKey,
    long fileSize,
    String contentType,
    String md5Hash,
    String sha256Hash,
    String serviceName,
    String businessType,
    String businessId,
    Map<String, Object> correlationData,
    String providerType,
    Instant expiresAt,
    byte[] ipAddress,
    String recordRemarks
) {
    public RecordUploadCommand {
        correlationData = correlationData == null ? Map.of() : Map.copyOf(correlationData);
        ipAddress = ipAddress == null ? null : ipAddress.clone();
    }
}
```

**设计要点**:
- **不可变对象**: 使用 `record` 类型
- **防御性复制**: 对可变字段进行防御性复制
- **扁平结构**: 所有字段扁平化,便于适配器层构造

**与 DTO 的区别**:
- **DTO**: 跨服务边界,包含验证注解,面向外部调用者
- **Command**: 应用层内部使用,已验证,面向用例编排器

### RecordUploadResult (记录上传结果)

应用层结果对象,封装用例执行结果。

```java
public record RecordUploadResult(
    Long metadataId,
    Instant recordedAt
) {}
```

**设计要点**:
- **最小化信息**: 仅包含必要信息,不暴露完整聚合根
- **不可变对象**: 使用 `record` 类型
- **适配器友好**: 适配器层可直接转换为响应 DTO

## 依赖关系

### 上游依赖

- **patra-storage-domain**: 领域层(聚合根、值对象、仓储端口)
- **patra-common-core**: 通用工具类
- **patra-spring-boot-starter-core**: Patra Spring Boot 核心 Starter
- **spring-tx**: Spring 事务管理
- **spring-boot-starter-aop**: Spring AOP(用于 @Transactional)

### 下游消费者

- **patra-storage-adapter**: 适配器层调用应用层编排器

## 事务管理

### 事务边界

应用层定义事务边界,确保数据一致性:

```java
@Transactional
public RecordUploadResult execute(RecordUploadCommand command) {
    // 整个用例在一个事务中执行
}
```

**事务传播行为**:
- 默认使用 `REQUIRED` 传播行为
- 如果外部已有事务,则加入现有事务
- 如果外部无事务,则创建新事务

### 事务回滚

**自动回滚**:
- 运行时异常(`RuntimeException`)自动回滚
- 受检异常(Checked Exception)不自动回滚(需显式配置)

**显式回滚配置**:
```java
@Transactional(rollbackFor = Exception.class)
public RecordUploadResult execute(RecordUploadCommand command) {
    // ...
}
```

## 使用示例

### 在适配器层调用编排器

```java
@RestController
@RequiredArgsConstructor
public class StorageEndpointImpl implements StorageEndpoint {

    private final RecordUploadOrchestrator orchestrator;

    @Override
    public RecordUploadResponse recordUpload(@Valid UploadRecordRequest request) {
        // 1. 构造命令对象
        RecordUploadCommand command = new RecordUploadCommand(
            request.bucketName(),
            request.objectKey(),
            request.fileSize(),
            request.contentType(),
            request.md5Hash(),
            request.sha256Hash(),
            request.serviceName(),
            request.businessType(),
            request.businessId(),
            request.correlationData(),
            request.providerType(),
            request.expiresAt(),
            extractIpAddress(),
            request.recordRemarks()
        );

        // 2. 调用编排器
        RecordUploadResult result = orchestrator.execute(command);

        // 3. 转换为响应 DTO
        return new RecordUploadResponse(result.metadataId(), result.recordedAt());
    }
}
```

### 异常处理

```java
@Transactional
public RecordUploadResult execute(RecordUploadCommand command) {
    try {
        FileMetadata metadata = FileMetadata.create(...);
        FileMetadata saved = repository.save(metadata);
        return new RecordUploadResult(saved.getId(), saved.getUploadedAt());
    } catch (IllegalArgumentException ex) {
        // 领域验证失败,抛出应用层异常
        throw new InvalidUploadRequestException("无效的上传请求", ex);
    } catch (DuplicateKeyException ex) {
        // 数据库唯一约束冲突,抛出幂等性异常
        throw new DuplicateUploadException("文件已记录", ex);
    }
}
```

## 设计模式与实践

### 命令模式

应用层使用**命令模式**封装用例参数:

- **Command 对象**: 封装用例所需的所有参数
- **Orchestrator**: 命令处理器,执行业务逻辑
- **Result 对象**: 封装用例执行结果

**优势**:
- 解耦适配器层和应用层
- 便于测试(直接构造命令对象)
- 支持命令日志、审计、重放

### 编排模式

应用层编排器协调多个领域对象:

```java
@Transactional
public ComplexResult executeComplexUseCase(ComplexCommand command) {
    // 1. 查询聚合根
    FileMetadata metadata = repository.findByStorageKey(storageKey)
        .orElseThrow(() -> new MetadataNotFoundException());

    // 2. 调用领域行为
    metadata.markAsDeleted(command.operatorId(), command.operatorName());

    // 3. 保存聚合根
    repository.save(metadata);

    // 4. 发送领域事件(如需要)
    eventPublisher.publish(new FileDeletedEvent(metadata.getId()));

    // 5. 返回结果
    return new ComplexResult(...);
}
```

### 薄应用层原则

应用层保持薄层,业务逻辑在领域层:

**✅ 应该做**:
- 编排多个领域对象
- 定义事务边界
- 转换命令为领域操作
- 发送领域事件

**❌ 不应该做**:
- 包含业务规则(应在领域层)
- 直接操作数据库(应通过仓储)
- 包含复杂计算逻辑(应在领域层)

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Framework | 6.2.5+ | IoC 容器和事务管理 |
| Spring Boot | 3.5.7 | 自动配置 |
| patra-storage-domain | 0.1.0-SNAPSHOT | 领域层依赖 |
| Lombok | 1.18.38+ | 代码生成 |

## 最佳实践

### 应用层开发原则

1. **薄应用层**: 保持应用层简洁,业务逻辑放在领域层
2. **事务边界**: 用例编排器方法定义事务边界
3. **命令驱动**: 使用命令对象封装参数,便于扩展和测试
4. **结果封装**: 返回结果对象而非领域对象,避免泄露领域模型
5. **异常转换**: 将领域异常转换为应用层异常

### 测试策略

1. **集成测试**: 测试用例编排器的完整流程
2. **Mock 仓储**: 使用 Mockito Mock 仓储接口
3. **事务测试**: 使用 `@Transactional` 和 `@Rollback` 测试事务行为
4. **异常测试**: 测试各种异常场景和事务回滚

**测试示例**:
```java
@SpringBootTest
class RecordUploadOrchestratorTest {

    @MockBean
    private FileMetadataRepository repository;

    @Autowired
    private RecordUploadOrchestrator orchestrator;

    @Test
    void execute_shouldSaveMetadata() {
        // Given
        RecordUploadCommand command = new RecordUploadCommand(...);
        FileMetadata saved = FileMetadata.restore(...);
        when(repository.save(any())).thenReturn(saved);

        // When
        RecordUploadResult result = orchestrator.execute(command);

        // Then
        assertThat(result.metadataId()).isEqualTo(saved.getId());
        verify(repository).save(any(FileMetadata.class));
    }
}
```

## 相关文档

- **领域层**: 参见 `patra-storage-domain/README.md` 了解聚合根和仓储端口
- **适配器层**: 参见 `patra-storage-adapter/README.md` 了解如何调用编排器
- **基础设施层**: 参见 `patra-storage-infra/README.md` 了解仓储实现
