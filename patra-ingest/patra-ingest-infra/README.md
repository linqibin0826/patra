# patra-ingest-infra — 摄入基础设施层

## 概述

`patra-ingest-infra` 是 **patra-ingest 服务的基础设施层(Infrastructure Layer)**,负责**技术实现细节**和**外部系统集成**。遵循**六边形架构**原则,实现 Domain 层定义的端口接口,提供具体的技术实现。

本模块在六边形架构中位于**外围层**,主要职责包括:
- **数据持久化**: 基于 MyBatis-Plus 实现仓储接口
- **外部服务集成**: 通过 Feign 调用 patra-registry、Provenance 数据源等外部服务
- **消息发布**: 通过 RocketMQ 发布领域事件
- **对象存储**: 集成 MinIO/S3 存储文献数据
- **DO ↔ Domain 转换**: 使用 MapStruct 进行对象转换

**架构约束**: Infra 层实现 Domain 层的端口接口,但不直接被 Domain 层依赖(依赖倒置)。

---

## 核心职责

- **仓储实现**: 实现 PlanRepository、TaskRepository 等端口接口
- **外部端口适配**: 实现 PatraRegistryPort、ExpressionCompilerPort 等外部服务端口
- **数据转换**: DO(Data Object) ↔ Domain Model 双向转换
- **MQ 消息发布**: 将 Outbox 消息发布到 RocketMQ
- **对象存储集成**: 上传文献数据到 MinIO/S3

---

## 模块结构

```
patra-ingest-infra/
└─ src/main/java/.../infra/
   ├─ persistence/                    # 持久化层
   │  ├─ entity/                       # MyBatis-Plus 实体(DO)
   │  │  ├─ PlanDO.java                    # 计划数据对象
   │  │  ├─ TaskDO.java                    # 任务数据对象
   │  │  ├─ PlanSliceDO.java               # 切片数据对象
   │  │  ├─ ScheduleInstanceDO.java        # 调度实例数据对象
   │  │  ├─ CursorDO.java                  # 游标数据对象
   │  │  ├─ CursorEventDO.java             # 游标事件数据对象
   │  │  ├─ OutboxMessageDO.java           # Outbox 消息数据对象
   │  │  ├─ OutboxRelayLogDO.java          # Outbox 中继日志数据对象
   │  │  ├─ TaskRunDO.java                 # 任务运行记录数据对象
   │  │  └─ TaskRunBatchDO.java            # 批次运行记录数据对象
   │  ├─ mapper/                       # MyBatis Mapper 接口
   │  │  ├─ PlanMapper.java                # 计划 Mapper
   │  │  ├─ TaskMapper.java                # 任务 Mapper
   │  │  ├─ PlanSliceMapper.java           # 切片 Mapper
   │  │  ├─ ScheduleInstanceMapper.java    # 调度实例 Mapper
   │  │  ├─ CursorMapper.java              # 游标 Mapper
   │  │  ├─ CursorEventMapper.java         # 游标事件 Mapper
   │  │  ├─ OutboxMessageMapper.java       # Outbox 消息 Mapper
   │  │  ├─ OutboxRelayLogMapper.java      # Outbox 中继日志 Mapper
   │  │  ├─ TaskRunMapper.java             # 任务运行 Mapper
   │  │  └─ TaskRunBatchMapper.java        # 批次运行 Mapper
   │  ├─ converter/                    # DO ↔ Domain 转换器
   │  │  ├─ PlanConverter.java             # 计划转换器(MapStruct)
   │  │  ├─ TaskConverter.java             # 任务转换器
   │  │  ├─ PlanSliceConverter.java        # 切片转换器
   │  │  ├─ ScheduleInstanceConverter.java # 调度实例转换器
   │  │  ├─ CursorConverter.java           # 游标转换器
   │  │  ├─ CursorEventConverter.java      # 游标事件转换器
   │  │  ├─ OutboxMessageConverter.java    # Outbox 消息转换器
   │  │  ├─ OutboxRelayLogConverter.java   # Outbox 中继日志转换器
   │  │  ├─ TaskRunConverter.java          # 任务运行转换器
   │  │  └─ TaskRunBatchConverter.java     # 批次运行转换器
   │  └─ repository/                   # 仓储实现
   │     ├─ PlanRepositoryMpImpl.java          # 计划仓储实现
   │     ├─ TaskRepositoryMpImpl.java          # 任务仓储实现
   │     ├─ PlanSliceRepositoryMpImpl.java     # 切片仓储实现
   │     ├─ ScheduleInstanceRepositoryMpImpl.java # 调度实例仓储实现
   │     ├─ CursorRepositoryMpImpl.java        # 游标仓储实现
   │     ├─ CursorEventRepositoryMpImpl.java   # 游标事件仓储实现
   │     ├─ OutboxMessageRepositoryMpImpl.java # Outbox 消息仓储实现
   │     ├─ OutboxRelayLogRepositoryMpImpl.java # Outbox 中继日志仓储实现
   │     ├─ TaskRunRepositoryMpImpl.java       # 任务运行仓储实现
   │     └─ TaskRunBatchRepositoryMpImpl.java  # 批次运行仓储实现
   ├─ integration/                    # 外部系统集成
   │  ├─ registry/                     # patra-registry 集成
   │  │  ├─ PatraRegistryAdapter.java      # Registry 适配器
   │  │  └─ converter/
   │  │     └─ ProvenanceConfigSnapshotConverter.java # 配置快照转换器
   │  ├─ datasource/                   # 数据源集成
   │  │  ├─ ProvenanceDataAdapter.java     # 数据源适配器(统一数据源访问)
   │  │  ├─ ProvenanceDataException.java   # 数据源异常
   │  │  ├─ TypeMismatchException.java     # 类型不匹配异常
   │  │  └─ acl/
   │  │     └─ QuerySessionTranslator.java # 查询会话转换器
   │  └─ storage/                      # 对象存储集成
   │     ├─ LiteratureStorageAdapter.java   # 文献存储适配器
   │     └─ StorageMetadataAdapter.java     # 存储元数据适配器
   ├─ compiler/                       # 表达式编译器集成
   │  └─ ExpressionCompilerPortImpl.java    # 表达式编译器端口实现
   ├─ messaging/                      # 消息发布
   │  └─ RocketMqOutboxPublisher.java      # RocketMQ Outbox 发布器
   └─ config/                         # 配置类
      ├─ OutboxMessagingConfiguration.java  # Outbox 消息配置
      └─ OutboxMqProperties.java            # Outbox MQ 属性
```

---

## 主要组件

### 仓储实现

#### PlanRepositoryMpImpl (计划仓储实现)

**职责**: 实现 `PlanRepository` 端口接口,基于 MyBatis-Plus 操作数据库。

**核心方法**:
```java
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

    private final PlanMapper planMapper;
    private final PlanConverter planConverter;

    @Override
    public PlanAggregate save(PlanAggregate plan) {
        PlanDO planDO = planConverter.toDataObject(plan);
        planMapper.insert(planDO);
        return planConverter.toDomain(planDO);
    }

    @Override
    public Optional<PlanAggregate> findByPlanKey(String planKey) {
        LambdaQueryWrapper<PlanDO> query = new LambdaQueryWrapper<>();
        query.eq(PlanDO::getPlanKey, planKey);
        PlanDO planDO = planMapper.selectOne(query);
        return Optional.ofNullable(planDO).map(planConverter::toDomain);
    }

    @Override
    public void update(PlanAggregate plan) {
        PlanDO planDO = planConverter.toDataObject(plan);
        planMapper.updateById(planDO);
    }
}
```

**文件**: `persistence/repository/PlanRepositoryMpImpl.java`

#### TaskRepositoryMpImpl (任务仓储实现)

**职责**: 实现 `TaskRepository` 端口接口,支持任务的 CRUD 和复杂查询。

**核心方法**:
```java
@Repository
@RequiredArgsConstructor
public class TaskRepositoryMpImpl implements TaskRepository {

    private final TaskMapper taskMapper;
    private final TaskConverter taskConverter;

    @Override
    public List<TaskAggregate> findQueuedTasks(String provenanceCode, int limit) {
        LambdaQueryWrapper<TaskDO> query = new LambdaQueryWrapper<>();
        query.eq(TaskDO::getProvenanceCode, provenanceCode)
             .eq(TaskDO::getStatus, TaskStatus.QUEUED.name())
             .orderByAsc(TaskDO::getScheduledAt)
             .last("LIMIT " + limit);

        List<TaskDO> taskDOs = taskMapper.selectList(query);
        return taskDOs.stream()
            .map(taskConverter::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countQueuedTasks(String provenanceCode, OperationCode operationCode) {
        LambdaQueryWrapper<TaskDO> query = new LambdaQueryWrapper<>();
        query.eq(TaskDO::getProvenanceCode, provenanceCode)
             .eq(TaskDO::getOperationCode, operationCode.getCode())
             .eq(TaskDO::getStatus, TaskStatus.QUEUED.name());

        return taskMapper.selectCount(query);
    }
}
```

**文件**: `persistence/repository/TaskRepositoryMpImpl.java`

### 外部服务集成

#### PatraRegistryAdapter (Registry 适配器)

**职责**: 实现 `PatraRegistryPort` 端口接口,通过 Feign 调用 patra-registry 服务。

**核心方法**:
```java
@Component
@RequiredArgsConstructor
public class PatraRegistryAdapter implements PatraRegistryPort {

    private final ProvenanceClient provenanceClient;  // Feign 客户端
    private final ProvenanceConfigSnapshotConverter converter;

    @Override
    public ProvenanceConfigSnapshot fetchConfig(
        ProvenanceCode code,
        OperationCode operation
    ) {
        ProvenanceConfigResp resp = provenanceClient.getConfiguration(
            code.getCode(),
            operation.getCode(),
            Instant.now()
        );

        return converter.toSnapshot(resp);
    }
}
```

**文件**: `integration/registry/PatraRegistryAdapter.java`

#### ProvenanceDataAdapter (数据源适配器)

**职责**: 实现 `ProvenanceDataPort` 端口接口,提供统一的数据源访问能力。通过 Framework 层的 `ProvenanceDataProvider` 调用外部数据源 API。

**核心特性**:
- **统一端口**: 同时支持查询会话准备(`prepareQuerySession`)和数据获取(`fetchData`)
- **参数转换**: 将 Ingest 特定的 `ExecutionContext` 转换为通用的 `ProviderRequest`
- **多数据源支持**: 通过 `ProviderRegistry` 自动选择对应的 `ProvenanceDataProvider`
- **类型安全**: 使用 `TypeReference` 确保类型安全的泛型数据获取

**核心方法**:
```java
@Component
@RequiredArgsConstructor
public class ProvenanceDataAdapter implements ProvenanceDataPort {

    private final ProviderRegistry providerRegistry;
    private final QuerySessionTranslator querySessionTranslator;

    @Override
    public QuerySession prepareQuerySession(ExecutionContext context, DataType dataType) {
        // 1. 获取对应的 Provider
        ProvenanceDataProvider provider = providerRegistry.getProvider(
            context.provenanceCode(),
            dataType
        );

        // 2. 构建提供者请求
        ProviderRequest request = buildProviderRequest(context);

        // 3. 调用 Provider 准备计划
        PlanMetadata planMetadata = provider.preparePlanMetadata(request);

        // 4. 转换为领域模型
        return querySessionTranslator.translate(planMetadata);
    }

    @Override
    public <T> DataFetchResult<T> fetchData(
        ExecutionContext context,
        DataType dataType,
        TypeReference<T> typeRef,
        Batch batch
    ) {
        // 1. 获取对应的 Provider
        ProvenanceDataProvider provider = providerRegistry.getProvider(
            context.provenanceCode(),
            dataType
        );

        // 2. 构建提供者请求（包含批次信息）
        ProviderRequest request = buildProviderRequest(context, batch);

        // 3. 调用 Provider 获取数据（类型安全）
        ProviderResult<T> result = provider.fetchData(request, dataType, typeRef);

        // 4. 转换为领域结果
        return convertToDataFetchResult(result);
    }

    @Override
    public boolean supports(String provenanceCode, DataType dataType) {
        return providerRegistry.supports(provenanceCode, dataType);
    }

    @Override
    public Set<DataType> getSupportedTypes(String provenanceCode) {
        return providerRegistry.getSupportedTypes(provenanceCode);
    }

    /**
     * 构建提供者请求
     */
    private ProviderRequest buildProviderRequest(ExecutionContext context) {
        return ProviderRequest.builder()
            .query(context.compiledQuery())
            .params(context.compiledParams())
            .configSnapshot(context.configSnapshot())
            .build();
    }

    /**
     * 构建包含批次信息的提供者请求
     */
    private ProviderRequest buildProviderRequest(ExecutionContext context, Batch batch) {
        return ProviderRequest.builder()
            .query(context.compiledQuery())
            .params(context.compiledParams())
            .configSnapshot(context.configSnapshot())
            .batchParams(batch.toExecutionParams())
            .build();
    }

    /**
     * 转换为领域数据获取结果
     */
    private <T> DataFetchResult<T> convertToDataFetchResult(ProviderResult<T> result) {
        return DataFetchResult.<T>builder()
            .data(result.data())
            .dataType(result.dataType())
            .recordCount(result.data().size())
            .nextCursor(result.nextCursor())
            .hasMore(result.hasMore())
            .build();
    }
}
```

**参数转换逻辑**:
- `ExecutionContext` (Ingest 层) → `ProviderRequest` (Framework 层)
- 提取编译后的查询字符串: `context.compiledQuery()`
- 提取编译后的参数: `context.compiledParams()`
- 提取配置快照: `context.configSnapshot()`
- 批次参数通过 `Batch.toExecutionParams()` 转换

**文件**: `integration/datasource/ProvenanceDataAdapter.java`

**⚠️ 架构说明**: 本适配器是六边形架构中的关键桥梁:
- 实现 **Domain 层**的 `ProvenanceDataPort` 接口
- 调用 **Framework 层**的 `ProvenanceDataProvider` 实现
- 负责 Domain 模型与 Framework 模型之间的转换（ACL - 防腐层）

### 数据转换器

#### PlanConverter (计划转换器 - MapStruct)

**职责**: DO ↔ Domain Model 双向转换,使用 MapStruct 自动生成转换代码。

**核心方法**:
```java
@Mapper(componentModel = "spring")
public interface PlanConverter {

    /**
     * Domain → DO
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "planKey", target = "planKey")
    @Mapping(source = "status", target = "status")
    PlanDO toDataObject(PlanAggregate plan);

    /**
     * DO → Domain
     */
    @Mapping(source = "id", target = "id")
    @Mapping(source = "planKey", target = "planKey")
    @Mapping(source = "status", target = "status")
    PlanAggregate toDomain(PlanDO planDO);
}
```

**文件**: `persistence/converter/PlanConverter.java`

### 消息发布

#### RocketMqOutboxPublisher (RocketMQ Outbox 发布器)

**职责**: 实现 `OutboxPublisherPort` 端口接口,将 Outbox 消息发布到 RocketMQ。

**核心方法**:
```java
@Component
@RequiredArgsConstructor
public class RocketMqOutboxPublisher implements OutboxPublisherPort {

    private final RocketMQTemplate rocketMQTemplate;
    private final OutboxMqProperties mqProperties;

    @Override
    public void publish(OutboxMessage message) {
        String topic = mqProperties.getTaskReadyTopic();
        String tag = message.getAggregateType();

        // 构建消息
        Message<String> mqMessage = MessageBuilder
            .withPayload(message.getPayload())
            .setHeader("messageId", message.getMessageId())
            .setHeader("aggregateType", message.getAggregateType())
            .setHeader("aggregateId", message.getAggregateId())
            .build();

        // 发送到 RocketMQ
        SendResult sendResult = rocketMQTemplate.syncSend(
            topic + ":" + tag,
            mqMessage,
            mqProperties.getSendTimeout()
        );

        if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new OutboxPublishException("Failed to publish message: " + message.getMessageId());
        }
    }
}
```

**文件**: `messaging/RocketMqOutboxPublisher.java`

### 对象存储集成

#### LiteratureStorageAdapter (文献存储适配器)

**职责**: 实现 `LiteratureStoragePort` 端口接口,直接存储 `CanonicalLiterature` 到 MinIO/S3。

**架构决策（2025-01-16）**：
- ✅ 直接存储共享内核模型 `CanonicalLiterature`
- ✅ 移除 ACL 转换层（无需 DTO 转换）
- ✅ 保证存储数据与业务模型完全一致

**核心方法**:
```java
@Component
@RequiredArgsConstructor
public class LiteratureStorageAdapter implements LiteratureStoragePort {

    private final ObjectMapper objectMapper;
    private final ObjectStorageTemplate objectStorageTemplate;
    private final StorageLocationResolver storageLocationResolver;

    @Override
    public StorageResult store(List<CanonicalLiterature> literature, StorageContext context) {
        // 步骤 1: 序列化为 JSON（直接存储 CanonicalLiterature,保证数据完整性）
        byte[] serialized = serializePayload(literature, context);

        // 步骤 2: 计算校验和
        Checksums checksums = calculateChecksums(serialized);

        // 步骤 3: 解析存储位置
        StorageLocation location = resolveStorageLocation(context);

        // 步骤 4: 上传到对象存储
        UploadResult uploadResult = uploadPayload(location, serialized, literature.size(), context);

        return StorageResult.builder()
            .storageKey(uploadResult.getStorageKey())
            .bucketName(uploadResult.getBucketName())
            .objectKey(uploadResult.getObjectKey())
            .fileSize(uploadResult.getFileSize())
            .md5(checksums.md5())
            .sha256(checksums.sha256())
            .literatureCount(literature.size())
            .build();
    }
}
```

**存储格式**: 直接序列化 `List<CanonicalLiterature>` 为 JSON,无需转换。

**文件**: `integration/storage/LiteratureStorageAdapter.java`

---

## 依赖关系

### 上游依赖
- `patra-ingest-domain`: 领域模型和端口接口
- `patra-common-model`: 通用模型（包含 CanonicalLiterature）
- `patra-registry-api`: Registry API(Feign 客户端)
- `patra-spring-boot-starter-mybatis`: MyBatis Starter
- `patra-spring-boot-starter-expr`: 表达式编译 Starter
- `patra-spring-boot-starter-provenance`: Provenance Starter(提供 ProvenanceDataProvider)
- `patra-spring-boot-starter-object-storage`: 对象存储 Starter
- `spring-cloud-starter-stream-rocketmq`: RocketMQ Starter

### 下游消费者
- `patra-ingest-boot`: 启动模块(组装所有依赖)

**依赖方向**: Infra → Domain (依赖倒置原则)

---

## 数据库表设计

### 核心表

| 表名 | 说明 | 主要字段 |
|------|------|----------|
| `ingest_plan` | 计划表 | `id`, `plan_key`, `provenance_code`, `operation_code`, `window_spec_json`, `status` |
| `ingest_task` | 任务表 | `id`, `idempotent_key`, `plan_id`, `slice_id`, `params_json`, `status`, `scheduled_at` |
| `ingest_plan_slice` | 切片表 | `id`, `plan_id`, `slice_index`, `window_spec_json`, `status` |
| `ingest_schedule_instance` | 调度实例表 | `id`, `schedule_key`, `provenance_code`, `operation_code`, `trigger_at`, `status` |
| `ingest_cursor` | 游标表 | `id`, `namespace_key`, `cursor_value`, `watermark_at` |
| `ingest_cursor_event` | 游标事件表 | `id`, `cursor_id`, `event_type`, `old_value`, `new_value` |
| `ingest_outbox_message` | Outbox 消息表 | `id`, `message_id`, `aggregate_type`, `aggregate_id`, `payload`, `status`, `seq` |
| `ingest_outbox_relay_log` | Outbox 中继日志表 | `id`, `batch_id`, `message_count`, `published_count`, `failed_count` |
| `ingest_task_run` | 任务运行记录表 | `id`, `task_id`, `run_id`, `started_at`, `completed_at`, `status` |
| `ingest_task_run_batch` | 批次运行记录表 | `id`, `run_id`, `batch_index`, `record_count`, `status` |

### 索引设计

```sql
-- 计划表索引
CREATE UNIQUE INDEX idx_plan_key ON ingest_plan(plan_key);
CREATE INDEX idx_plan_status ON ingest_plan(status);

-- 任务表索引
CREATE UNIQUE INDEX idx_task_idempotent_key ON ingest_task(idempotent_key);
CREATE INDEX idx_task_status_scheduled ON ingest_task(status, scheduled_at);
CREATE INDEX idx_task_plan_id ON ingest_task(plan_id);

-- Outbox 表索引
CREATE INDEX idx_outbox_status_seq ON ingest_outbox_message(status, seq);
CREATE INDEX idx_outbox_aggregate ON ingest_outbox_message(aggregate_type, aggregate_id);
```

---

## 配置说明

### MyBatis-Plus 配置

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

### RocketMQ 配置

```yaml
spring:
  cloud:
    stream:
      rocketmq:
        binder:
          name-server: localhost:9876
        bindings:
          task-ready-output:
            producer:
              group: patra-ingest-producer
              send-timeout: 3000
```

### MinIO 配置

```yaml
patra:
  storage:
    minio:
      endpoint: http://localhost:9000
      access-key: minioadmin
      secret-key: minioadmin
      bucket: patra-literature
```

---

## 使用示例

### 示例 1: 使用仓储保存计划

```java
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;

    public void createPlan() {
        PlanAggregate plan = PlanAggregate.builder()
            .planKey("plan_pubmed_harvest_20250101")
            .provenanceCode("pubmed")
            .operationCode(OperationCode.HARVEST)
            .status(PlanStatus.DRAFT)
            .build();

        PlanAggregate savedPlan = planRepository.save(plan);
        log.info("Plan saved: planId={}", savedPlan.getId());
    }
}
```

### 示例 2: 调用 Registry 服务

```java
@Service
@RequiredArgsConstructor
public class ConfigLoader {

    private final PatraRegistryPort patraRegistryPort;

    public ProvenanceConfigSnapshot loadConfig() {
        ProvenanceConfigSnapshot config = patraRegistryPort.fetchConfig(
            ProvenanceCode.PUBMED,
            OperationCode.HARVEST
        );

        log.info("Config loaded: provenanceCode={}, version={}",
            config.getProvenanceCode(), config.getVersion());

        return config;
    }
}
```

### 示例 3: 使用 ProvenanceDataAdapter 获取数据

```java
@Service
@RequiredArgsConstructor
public class DataFetchService {

    private final ProvenanceDataPort provenanceDataPort;

    public void fetchLiteratureData(ExecutionContext context, Batch batch) {
        // 准备查询会话
        QuerySession querySession = provenanceDataPort.prepareQuerySession(
            context,
            DataType.LITERATURE
        );
        log.info("Total count: {}", querySession.totalRecords());

        // 获取文献数据（类型安全）
        TypeReference<CanonicalLiterature> typeRef = new TypeReference<>() {};
        DataFetchResult<CanonicalLiterature> result = provenanceDataPort.fetchData(
            context,
            DataType.LITERATURE,
            typeRef,
            batch
        );

        log.info("Fetched {} records, hasMore: {}",
            result.recordCount(), result.hasMore());
    }
}
```

### 示例 4: 发布 Outbox 消息

```java
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxPublisherPort outboxPublisherPort;

    public void publishTaskReadyMessage(OutboxMessage message) {
        try {
            outboxPublisherPort.publish(message);
            log.info("Message published: messageId={}", message.getMessageId());
        } catch (OutboxPublishException ex) {
            log.error("Failed to publish message: messageId={}", message.getMessageId(), ex);
        }
    }
}
```

---

## 技术栈

- **Java**: 25
- **Spring Boot**: 3.5.7
- **MyBatis-Plus**: 3.5.x (ORM 框架)
- **MapStruct**: 1.5.x (对象转换)
- **Spring Cloud OpenFeign**: RPC 调用
- **RocketMQ**: 消息队列
- **MinIO/S3**: 对象存储

---

**最后更新**: 2025-01-16
**Maven 坐标**: `com.patra:patra-ingest-infra:0.1.0-SNAPSHOT`
