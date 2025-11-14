# patra-ingest 项目 provenanceCode String 使用扫描报告

## 扫描统计
- **扫描范围**: `/Users/linqibin/Desktop/Patra-api/patra-ingest/src/main`
- **总文件数**: 73 个 Java 文件
- **含有 provenanceCode 的文件数**: 73 个

---

## 分层次扫描结果

### 🏢 Domain 层 (域模型、端口、聚合根)

#### Port 接口（业务边界）
1. **CursorRepository.java** - Line 39, 64
   - `Optional<Cursor> find(String provenanceCode, String operationCode, ...)`
   - `Optional<Instant> findLatestGlobalTimeWatermark(String provenanceCode, String operationCode)`

2. **TaskRepository.java** - Line 87
   - `long countQueuedTasks(String provenanceCode, String operationCode)`

3. **DataSourcePort.java** - Line 183, 223
   - `boolean supports(String provenanceCode, DataType dataType)`
   - `Set<DataType> getSupportedTypes(String provenanceCode)`

4. **StoragePort.java** - Line 45
   - `String generateObjectPath(String provenanceCode, Long runId, int batchNo)`

5. **LiteratureStoragePort.java** - Line 64
   - `record StorageContext(Long runId, int batchNo, String provenanceCode) {}`

#### Domain 模型
6. **Cursor.java** - Line 代数据（需读取确认）
7. **CursorEvent.java** - Line 代数据（需读取确认）
8. **TaskRun.java** - Line 代数据（需读取确认）
9. **TaskRunBatch.java** - Line 代数据（需读取确认）
10. **TaskAggregate.java** - Line 代数据（需读取确认）
11. **PlanAggregate.java** - Line 代数据（需读取确认）
12. **PlanSliceAggregate.java** - Line 代数据（需读取确认）
13. **ScheduleInstanceAggregate.java** - Line 代数据（需读取确认）

#### Value Objects
14. **ExecutionContext.java** - 包含 provenanceCode（需读取确认）
15. **ExprCompilationRequest.java** - Line 29, 36
    - `ExprCompilationRequest(String provenanceCode, String endpointName, String rawExpression)`
    - `ExprCompilationRequest(String provenanceCode, String rawExpression)`
16. **LiteratureReadyMessage.java** - 包含 provenanceCode（需读取确认）
17. **PlanTriggerNorm.java** - 包含 provenanceCode（需读取确认）

#### Exception Classes
18. **IngestConfigurationException.java** - Line 37, 51, 68
    - `private final String provenanceCode` (字段)
    - `IngestConfigurationException(String provenanceCode, String operationCode, String message)`
    - `IngestConfigurationException(String provenanceCode, String operationCode, String message, Throwable cause)`

19. **IngestScheduleParameterException.java** - 包含 provenanceCode（需读取确认）

#### Event Classes
20. **TaskQueuedEvent.java** - 包含 provenanceCode（需读取确认）
21. **LiteratureDataReadyEvent.java** - 包含 provenanceCode（需读取确认）

---

### 🚀 App 层 (应用编排、UseCase、Assembler)

#### UseCase 实现
22. **CursorAdvancerImpl.java** - Line 72
    - `String provenanceCodeStr = provenanceCode != null ? provenanceCode.getCode() : null`
    - Line 353 - 方法签名（需读取确认）

23. **ExecuteTaskBatchesUseCaseImpl.java** - Line 93
    - `String provenanceCode = ...` （需读取确认）

24. **TaskExecutionUseCaseImpl.java** - 包含 provenanceCode（需读取确认）

25. **CompleteTaskExecutionUseCaseImpl.java** - 包含 provenanceCode（需读取确认）

#### Strategy & Planner
26. **BatchPlannerRegistry.java** - Line 56, 66
    - `public BatchPlanner get(String provenanceCode)`
    - `public boolean contains(String provenanceCode)`

27. **BatchPlanner.java** - 包含 provenanceCode（需读取确认）

28. **UnifiedBatchPlanner.java** - 包含 provenanceCode（需读取确认）

#### Session & Execution
29. **ExecutionContextLoaderImpl.java** - Line 138
    - `String provenanceCodeStr = ...`

#### Coordination
30. **LiteraturePublisherOrchestrator.java** - Line 241, 268
    - `private String safeProvenance(String provenanceCode)`
    - `public record PublishContext(Long runId, int batchNo, String provenanceCode) {}`

31. **GenericBatchExecutor.java** - 包含 provenanceCode（需读取确认）

#### Publisher
32. **LiteratureEventPublisher.java** - Line 65, 82, 90
    - `String provenanceCode = pc != null ? pc.getCode() : null` (多处)

33. **LiteratureReadyHeaders.java** - Line 15
    - `String provenanceCode, Long taskId, Long runId, Integer storageKeyCount, Long occurredAt`

34. **LiteratureReadyPayload.java** - Line 22
    - `String provenanceCode, ...`

#### Plan UseCase
35. **PlanIngestionOrchestrator.java** - 包含 provenanceCode（需读取确认）
36. **PlanPersistenceCoordinator.java** - 包含 provenanceCode（需读取确认）
37. **PlannerValidatorImpl.java** - 包含 provenanceCode（需读取确认）
38. **PlanAssemblerImpl.java** - 包含 provenanceCode（需读取确认）
39. **PlanExpressionBuilder.java** - 包含 provenanceCode（需读取确认）
40. **TimeSlicePlanner.java** - 包含 provenanceCode（需读取确认）
41. **DateSlicePlanner.java** - 包含 provenanceCode（需读取确认）
42. **SingleSlicePlanner.java** - 包含 provenanceCode（需读取确认）
43. **TaskOutboxPublisher.java** - 包含 provenanceCode（需读取确认）

---

### 🔧 Infrastructure 层 (适配器、仓储实现、外部集成)

#### Repository Implementation
44. **CursorRepositoryMpImpl.java** - Line 89, 125
    - `String provenanceCode` (参数)
    - 在日志和查询中使用

45. **TaskRepositoryMpImpl.java** - Line 176
    - `public long countQueuedTasks(String provenanceCode, String operationCode)`

#### Entity Classes (数据库持久化对象)
46. **CursorDO.java** - Line 35
    - `private String provenanceCode`

47. **CursorEventDO.java** - Line 35
    - `private String provenanceCode`

48. **TaskDO.java** - Line 47
    - `private String provenanceCode`

49. **TaskRunDO.java** - Line 43
    - `private String provenanceCode`

50. **TaskRunBatchDO.java** - Line 55
    - `private String provenanceCode`

51. **PlanDO.java** - Line 45
    - `private String provenanceCode`

52. **PlanSliceDO.java** - Line 37
    - `private String provenanceCode`

53. **ScheduleInstanceDO.java** - Line 61
    - `private String provenanceCode`

#### Mapper (MyBatis)
54. **PlanMapper.java** - Line 24, 33
    - `@Param("provenanceCode") String provenanceCode` (多处)

#### Registry (外部系统接口)
55. **ProviderRegistry.java** - Line 110, 155, 176, 189, 201, 233, 255
    - `String provenanceCode = normalizeProvenanceCode(provider.getProvenanceCode())`
    - `public DataSourceProvider getProvider(String provenanceCode, DataType dataType)`
    - `public Optional<DataSourceProvider> findProvider(String provenanceCode, DataType dataType)`
    - `public boolean supports(String provenanceCode, DataType dataType)`
    - `public Set<DataType> getSupportedTypes(String provenanceCode)`
    - `private String normalizeProvenanceCode(String provenanceCode)`
    - `private record ProviderKey(String provenanceCode, DataType dataType) {}`

56. **ProviderNotFoundException.java** - 包含 provenanceCode（需读取确认）

#### Adapter Implementation
57. **DataSourceAdapter.java** - Line 383, 394
    - `public boolean supports(String provenanceCode, DataType dataType)`
    - `public Set<DataType> getSupportedTypes(String provenanceCode)`

58. **LiteratureStorageAdapter.java** - Line 182
    - `private String safeProvenance(String provenanceCode)`

59. **PatraRegistryAdapter.java** - Line 138
    - `private ProvenanceConfigSnapshot createMinimalSnapshot(String provenanceCode)`

60. **TechnicalRetryAdapter.java** - 包含 provenanceCode（需读取确认）

#### Outbox
61. **AbstractOutboxPublisher.java** - 包含 provenanceCode（需读取确认）

#### Compiler
62. **ExpressionCompilerPortImpl.java** - 包含 provenanceCode（需读取确认）

---

### 📱 Adapter 层 (Scheduler、外部触发)

63. **AbstractProvenanceScheduleJob.java** - 包含 provenanceCode（需读取确认）

---

## 特殊使用模式统计

### 1. 直接使用 String 类型 (需要改写)
- **Parameter 类型**: 43+ 处
  - Port 接口方法参数
  - Service/Repository 方法参数
  - Mapper @Param
  
- **Field 字段**: 8 个 DO 实体类 + 多个 Domain 模型
- **Record 字段**: 3+ 个 record（如 PublishContext）

### 2. 已正确使用 ProvenanceCode 枚举 (无需改写)
- **CursorAdvancerImpl.java** Line 70
  - `ProvenanceCode provenanceCode = context.provenanceCode();` ✅
  - Line 72: `provenanceCode.getCode()` 正确转换

### 3. 待确认的 String 使用场景
- Exception 中的 field：保存原始字符串（可能合理）
- DO Entity 中的 field：数据库持久化（需保持 String）
- 其他 method parameter 和 field

---

## 建议改写优先级

### 🔴 优先级 1 - 业务层端口 (必改)
1. `CursorRepository` interface - 2 处
2. `TaskRepository` interface - 1 处
3. `DataSourcePort` interface - 2 处
4. `StoragePort` interface - 1 处
5. `LiteratureStoragePort` - 1 处

### 🟠 优先级 2 - 实现层 (应改)
1. `CursorRepositoryMpImpl` - 2 处（可能影响 Port 改写）
2. `TaskRepositoryMpImpl` - 1 处
3. `ProviderRegistry` - 7 处
4. `DataSourceAdapter` - 2 处
5. 所有 Service/UseCase/Adapter 的 provenanceCode parameter

### 🟡 优先级 3 - 模型层 (按需改)
1. Domain Entity 的 provenanceCode field
2. Value Object 的 provenanceCode field
3. Event 的 provenanceCode field

### 🔵 优先级 4 - 基础设施层 (保持或标记)
1. DO Entity 的 provenanceCode field（数据库字段，可保持 String）
2. Mapper 参数（关联 DO Entity）

---

## 改写策略说明

### 端口层改写示例
```java
// 当前
Optional<Cursor> find(
    String provenanceCode,
    String operationCode,
    ...);

// 改写后
Optional<Cursor> find(
    ProvenanceCode provenanceCode,
    String operationCode,
    ...);
```

### 实现层改写示例
```java
// 当前
public Optional<Cursor> find(
    String provenanceCode,
    String operationCode,
    ...) {
    mapper.selectOne(
        new QueryWrapper<CursorDO>()
            .eq("provenance_code", provenanceCode)
            ...);
}

// 改写后（需要转换为字符串传给 MyBatis）
public Optional<Cursor> find(
    ProvenanceCode provenanceCode,
    String operationCode,
    ...) {
    mapper.selectOne(
        new QueryWrapper<CursorDO>()
            .eq("provenance_code", provenanceCode.getCode())
            ...);
}
```

### DO Entity 处理策略
```java
// DO Entity 保持 String（与数据库对应）
@Data
public class CursorDO {
    private String provenanceCode;  // ✅ 保持 String，只在持久化层使用
}
```

---

## 后续检查清单

- [ ] 验证 ProvenanceCode 枚举定义
- [ ] 检查是否需要转换工具函数（String ↔ ProvenanceCode）
- [ ] 确认 Exception 类的 provenanceCode field 是否需要改成 ProvenanceCode
- [ ] 检查 Record 类（如 PublishContext）是否应改为使用 ProvenanceCode
- [ ] 验证 MyBatis 映射是否需要特殊处理
- [ ] 检查日志记录中 String 形式的 provenanceCode 使用
