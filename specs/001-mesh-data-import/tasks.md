# 任务: MeSH 数据首次导入

**输入**: 来自 `/specs/001-mesh-data-import/` 的设计文档
**前置条件**: plan.md（✅）、spec.md（✅）、research.md（✅）、data-model.md（✅）、contracts/（✅）

**测试策略**: TDD（Test-Driven Development）- 所有功能必须先编写测试，遵循 Red-Green-Refactor 循环

**组织原则**: 任务按用户故事分组，以实现每个故事的独立实施和测试

## 格式说明

- **[P]**: 可并行运行（不同文件，无依赖）
- **[Layer]**: 六边形架构层标签
  - `[Domain]`: Domain 层（纯 Java 领域模型）
  - `[App]`: Application 层（Orchestrator/Coordinator）
  - `[Infra]`: Infrastructure 层（Repository 实现/MyBatis Mapper）
  - `[Adapter]`: Adapter 层（Controller/Job）
  - `[API]`: API/Contract 层（DTO）
  - `[Config]`: 配置层（Configuration 类）
  - `[Boot]`: Boot 层（E2E 测试）
  - `[Doc]`: 文档任务（README、package-info.java）
  - `[Test]`: 测试任务（补充测试、架构测试）
  - `[Verify]`: 验证任务（quickstart 验证）
  - `[Refactor]`: 重构任务（代码清理）
- **[Story]**: 此任务属于哪个用户故事（US1、US2、US3）

---

## 阶段 1：设置与基础（共享基础设施）

**目的**: 数据库表结构和基础配置

**⚠️ 关键**: 在此阶段完成之前，不能开始任何用户故事工作

### 数据库设计任务

- [ ] T001 [Infra] 创建 MeSH 导入相关表 DDL in patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V0.6.0__create_mesh_import_tables.sql
  - 参考：patra-catalog/patra-catalog-infra/src/main/resources/db/migration/V0.1.0__create_core_entities.sql（多表迁移文件模式）
  - 说明：V0.2.0 已被占用，使用 V0.6.0（下一个可用版本）
  - 表1：cat_mesh_import_task（MeSH 导入任务表）
    * 字段：id, task_name, status, source_url, xml_file_hash, xml_file_size, total_records, processed_records, failed_batch_count, last_error_message, start_time, end_time + BaseDO 字段（10个审计字段）
    * 索引：idx_import_task_status, idx_import_task_created_at, idx_import_task_deleted
  - 表2：cat_mesh_table_progress（MeSH 表进度记录表）
    * 字段：id, import_id, table_name, total_count, processed_count, failed_count, status, last_batch_num + BaseDO 字段
    * 索引：idx_table_progress_import_id, idx_table_progress_table_name, uk_table_progress_import_table（唯一索引）
  - 表3：cat_mesh_batch_detail（MeSH 批次详情表）
    * 字段：id, import_id, table_name, batch_num, batch_size, status, retry_count, error_message, start_time, end_time + BaseDO 字段
    * 索引：idx_batch_detail_import_id, uk_batch_detail_import_table_batch（唯一索引）, idx_batch_detail_status
  - DDL 规范：
    * 字符集：utf8mb4 + utf8mb4_unicode_ci
    * 引擎：InnoDB
    * 所有表添加详细注释（表注释和列注释）
    * id 字段使用雪花ID（BIGINT UNSIGNED）
    * BaseDO 的10个审计字段全部包含

### 配置管理任务

- [ ] T002 [P] [Config] 创建 MeSH 导入配置类 in patra-catalog/patra-catalog-boot/src/main/java/com/patra/catalog/config/MeshImportConfig.java
  - 配置项：
    * sourceUrl（NLM URL）
    * defaultBatchSize（默认批次大小：1000）
    * batchSizeMap（表级别批次大小配置）：
      - descriptor: 1000
      - qualifier: 100（数据量小）
      - treeNumber: 1500
      - entryTerm: 2000（数据量大）
      - concept: 2000（数据量大）
    * downloadTimeout（下载超时）
    * retryMaxAttempts（重试次数）
  - 方法：getBatchSizeForTable(String tableName) - 返回特定表的批次大小
  - 使用 @ConfigurationProperties + Nacos Config

- [ ] T003 [Infra] 添加 Redisson 分布式锁依赖 in patra-catalog/patra-catalog-boot/pom.xml
  - 依赖：redisson-spring-boot-starter（版本由 patra-parent 管理）

- [ ] T004 [P] [Infra] 添加 spring-web 依赖（RestClient）in patra-catalog/patra-catalog-infra/pom.xml
  - 依赖：org.springframework:spring-web（仅用于 RestClient，不引入 Web 容器）

**检查点**: 基础设施就绪 - 现在可以开始并行实施用户故事

---

## 阶段 2：用户故事 1 - 管理员发起 MeSH 数据首次导入（优先级: P1）🎯 MVP

**目标**: 实现完整的 MeSH 数据导入流程（下载 → 解析 → 分批导入 → 断点续传 → 错误恢复）

**独立测试**: 可以通过调用"开始导入"接口完全测试并交付"MeSH 数据从零到全部导入完成"的完整价值

### 第 1 步：Domain 层 - 领域模型设计（TDD）

#### 测试先行（Red 阶段）🔴

- [ ] T005 [P] [Domain] [US1] 为 MeshImportAggregate 编写单元测试 in patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/model/aggregate/MeshImportAggregateTest.java
  - 测试场景：startImport()、updateTableProgress()、markAsCompleted()、markAsFailed()、retry()
  - 测试状态转换：PENDING → PROCESSING → SUCCESS/FAILED
  - 测试不变量：不允许从 SUCCESS 转到 PROCESSING
  - 测试框架：JUnit 5 + AssertJ（纯 Java，无框架依赖）

- [ ] T006 [P] [Domain] [US1] 为 TableProgress 编写单元测试 in patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/model/valueobject/TableProgressTest.java
  - 测试场景：updateProgress()、incrementFailedCount()、getProgressPercentage()
  - 测试不可变性：修改返回新实例
  - 测试框架：JUnit 5 + AssertJ

#### 实施（Green 阶段）🟢

- [ ] T007 [P] [Domain] [US1] 定义 MeshImportId 强类型 ID in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/valueobject/MeshImportId.java
  - 参考：patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/valueobject/DescriptorId.java（已存在强类型 ID）
  - 属性：Long value
  - 方法：of(Long value)

- [ ] T008 [P] [Domain] [US1] 定义 MeshImportTaskStatus 枚举 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/enums/MeshImportTaskStatus.java
  - 枚举值：PENDING, PROCESSING, SUCCESS, FAILED, CANCELLED
  - 属性：displayName（中文）, code（API 编码）

- [ ] T009 [P] [Domain] [US1] 定义 MeshTableImportStatus 枚举 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/enums/MeshTableImportStatus.java
  - 枚举值：NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED

- [ ] T010 [P] [Domain] [US1] 定义 MeshBatchStatus 枚举 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/enums/MeshBatchStatus.java
  - 枚举值：PENDING, PROCESSING, SUCCESS, FAILED

- [ ] T011 [P] [Domain] [US1] 定义 TableProgress 值对象 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/valueobject/TableProgress.java
  - 参考：data-model.md 第 185-280 行
  - 属性：tableName, totalCount, processedCount, failedCount, status, lastBatchNum, lastUpdateTime
  - 方法：getProgressPercentage(), updateProgress(), incrementFailedCount()
  - 不可变对象（使用 @Value）

- [ ] T012 [Domain] [US1] 定义 MeshImportAggregate 聚合根 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/aggregate/MeshImportAggregate.java
  - 参考：data-model.md 第 36-180 行
  - 属性：MeshImportId id, taskName, status, startTime, endTime, sourceUrl, xmlFileHash, xmlFileSize, List<TableProgress> tableProgressList, totalRecords, processedRecords, failedBatchCount, lastErrorMessage
  - 领域行为：startImport(), updateTableProgress(), markAsCompleted(), markAsFailed(), retry()
  - 依赖：T009-T013（聚合根依赖值对象和枚举）

- [ ] T013 [P] [Domain] [US1] 定义 MeshImportStarted 领域事件 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/event/MeshImportStarted.java
  - 参考：data-model.md 第 463-473 行
  - 属性：MeshImportId importId, String sourceUrl, Instant startTime

- [ ] T014 [P] [Domain] [US1] 定义 MeshImportCompleted 领域事件 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/event/MeshImportCompleted.java
  - 参考：data-model.md 第 483-497 行
  - 属性：MeshImportId importId, Integer totalRecords, Long elapsedSeconds, Instant completedTime

- [ ] T015 [P] [Domain] [US1] 定义 MeshImportFailed 领域事件 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/event/MeshImportFailed.java
  - 参考：data-model.md 第 507-522 行
  - 属性：MeshImportId importId, String failureReason, Integer processedRecords, Instant failedTime

- [ ] T016 [P] [Domain] [US1] 定义 MeshImportPort 仓储接口 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/MeshImportPort.java
  - 方法：save(MeshImportAggregate), findById(MeshImportId), findRunningTask(), existsRunningTask()

- [ ] T017 [P] [Domain] [US1] 定义 XmlParserPort 解析接口 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/XmlParserPort.java
  - 方法：parseDescriptors(InputStream), parseQualifiers(InputStream), parseTreeNumbers(InputStream), parseEntryTerms(InputStream), parseConcepts(InputStream)
  - 返回类型：Stream<MeshDescriptor>（流式处理）

- [ ] T018 [P] [Domain] [US1] 定义 MeshFileDownloadPort 下载接口 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/MeshFileDownloadPort.java
  - 方法：download(String sourceUrl), validateChecksum(File xmlFile, String expectedHash)

### 第 2 步：Infrastructure 层 - 技术实现（TDD）

#### 测试先行（Red 阶段）🔴

- [ ] T019 [P] [Infra] [US1] 为 MeshImportRepositoryImpl 编写集成测试 in patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/persistence/repository/MeshImportRepositoryImplIT.java
  - 测试场景：save()、findById()、findRunningTask()、existsRunningTask()
  - 测试框架：@MybatisTest + TestContainers（MySQL）
  - 参考：patra-ingest-infra 的 Repository 集成测试模式

- [ ] T020 [P] [Infra] [US1] 为 StaxXmlParserImpl 编写单元测试 in patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/parser/StaxXmlParserImplTest.java
  - 测试场景：parseDescriptors()、parseQualifiers()、边界情况（空文件、格式错误）
  - 测试框架：JUnit 5 + Mockito
  - 使用测试 XML 文件：src/test/resources/mesh-sample.xml

- [ ] T021 [P] [Infra] [US1] 为 RestClientMeshFileDownloadImpl 编写单元测试 in patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/download/RestClientMeshFileDownloadImplTest.java
  - 测试场景：download()、validateChecksum()、超时处理
  - 测试框架：JUnit 5 + WireMock（模拟 HTTP 响应）

- [ ] T022 [P] [Infra] [US1] 为 MeshImportConverter 编写单元测试 in patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/persistence/converter/MeshImportConverterTest.java
  - 测试场景：toDomain()、toTaskDO()、toProgressDOList()
  - 测试框架：JUnit 5 + MapStruct 生成器

#### 实施（Green 阶段）🟢

- [ ] T023 [P] [Infra] [US1] 创建 MeshImportTaskDO 数据库实体 in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/entity/MeshImportTaskDO.java
  - 参考：data-model.md 第 822-870 行
  - 继承：BaseDO
  - 映射表：cat_mesh_import_task

- [ ] T024 [P] [Infra] [US1] 创建 MeshTableProgressDO 数据库实体 in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/entity/MeshTableProgressDO.java
  - 参考：data-model.md 第 887-919 行
  - 继承：BaseDO
  - 映射表：cat_mesh_table_progress

- [ ] T025 [P] [Infra] [US1] 创建 MeshBatchDetailDO 数据库实体 in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/entity/MeshBatchDetailDO.java
  - 参考：data-model.md 第 937-978 行
  - 继承：BaseDO
  - 映射表：cat_mesh_batch_detail

- [ ] T026 [P] [Infra] [US1] 创建 MeshImportTaskMapper MyBatis 接口 in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/mapper/MeshImportTaskMapper.java
  - 继承：BaseMapper<MeshImportTaskDO>
  - 自定义方法：findRunningTask(), countRunningTasks()

- [ ] T027 [P] [Infra] [US1] 创建 MeshTableProgressMapper MyBatis 接口 in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/mapper/MeshTableProgressMapper.java
  - 继承：BaseMapper<MeshTableProgressDO>
  - 自定义方法：findByImportId(Long importId)

- [ ] T028 [P] [Infra] [US1] 创建 MeshBatchDetailMapper MyBatis 接口 in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/mapper/MeshBatchDetailMapper.java
  - 继承：BaseMapper<MeshBatchDetailDO>
  - 自定义方法：findFailedBatches(Long importId)

- [ ] T029 [Infra] [US1] 创建 MeshImportConverter MapStruct 转换器 in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/converter/MeshImportConverter.java
  - 参考：data-model.md 第 985-1006 行
  - 方法：toDomain(), toTaskDO(), toProgressDOList(), toTableProgress(), toProgressDO()
  - 依赖：T025-T027（转换器依赖 DO 对象）

- [ ] T030 [Infra] [US1] 实现 MeshImportRepositoryImpl in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/repository/MeshImportRepositoryImpl.java
  - 参考：patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/repository/MeshDescriptorRepositoryImpl.java:1-40
  - 实现：MeshImportPort
  - 依赖：MeshImportTaskMapper, MeshTableProgressMapper, MeshImportConverter
  - 依赖：T028-T031（Repository 依赖 Mapper 和 Converter）

- [ ] T031 [Infra] [US1] 实现 StaxXmlParserImpl in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/parser/StaxXmlParserImpl.java
  - 实现：XmlParserPort
  - 使用 JDK 内置 StAX（javax.xml.stream.XMLStreamReader）
  - 批次处理：
    * 注入 MeshImportConfig
    * 使用 config.getBatchSizeForTable(tableName) 获取动态批次大小
    * 返回 Stream 实现流式处理，避免内存溢出
  - 参考：research.md 第 23-35 行（StAX 流式解析方案）、data-model.md 第 530-790 行（XML 映射结构）
  - 依赖：T002（使用动态批次配置）

- [ ] T032 [Infra] [US1] 实现 RestClientMeshFileDownloadImpl in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/download/RestClientMeshFileDownloadImpl.java
  - 实现：MeshFileDownloadPort
  - 使用 Spring RestClient（底层 JDK 21 HttpClient）
  - 参考：patra-spring-boot-starter-provenance 的 RestClient 使用模式（EPMCClientImpl）
  - 参考：research.md 第 87-99 行（RestClient 方案）

### 第 3 步：Application 层 - 业务编排（TDD）

#### 测试先行（Red 阶段）🔴

- [ ] T033 [App] [US1] 为 MeshImportOrchestrator 编写单元测试 in patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/meshimport/MeshImportOrchestratorTest.java
  - 测试场景：startImport()、retryFailedTask()、clearAndRestart()
  - 测试编排逻辑：调用顺序、事务边界
  - Mock 所有 Port 接口（MeshImportPort、XmlParserPort、MeshFileDownloadPort、MeshDescriptorPort）
  - 测试框架：JUnit 5 + Mockito + InOrder（验证调用顺序）
  - 参考：patra-ingest/patra-ingest-app/src/test/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestratorTest.java（Orchestrator 测试模式）

#### 实施（Green 阶段）🟢

- [ ] T034 [API] [US1] 定义 StartImportCommand 命令对象 in patra-catalog/patra-catalog-api/src/main/java/com/patra/catalog/api/command/StartImportCommand.java
  - 属性：String sourceUrl（可选）, String taskName（可选）
  - 参数校验：@Valid

- [ ] T035 [API] [US1] 定义 MeshImportResultDTO 响应对象 in patra-catalog/patra-catalog-api/src/main/java/com/patra/catalog/api/dto/MeshImportResultDTO.java
  - 属性：String taskId, String taskName, String status, Instant startTime, String message

- [ ] T036 [App] [US1] 实现 MeshImportOrchestrator in patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/meshimport/MeshImportOrchestrator.java
  - 参考：patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestrator.java:1-100（Orchestrator 编排模式）
  - 方法：startImport(StartImportCommand)、retryFailedTask(MeshImportId)、clearAndRestart()
  - 编排流程：
    1. 调用 MeshFileDownloadPort.download(sourceUrl) 下载 XML 文件
    2. 验证文件校验和 validateChecksum()
    3. 使用 XmlParserPort 流式解析各类数据
    4. 按依赖顺序批量导入（Descriptor → Qualifier → TreeNumber/EntryTerm/Concept）
    5. 调用 MeshDataValidator.validateDataCounts() 验证数据量（差异>5%生成警告）
    6. 更新任务状态，发布完成/失败事件
  - 事务管理：每批次独立事务（@Transactional(propagation = REQUIRES_NEW)）
  - @Transactional（主事务边界在 Application 层）
  - 依赖：MeshImportPort, XmlParserPort, MeshFileDownloadPort, MeshDescriptorPort, MeshDataValidator
  - 依赖：T014, T018-T020, T032-T035, T036a（Orchestrator 依赖 Domain 层、Infra 层和验证器）

- [ ] T036a [App] [US1] 实现 MeshDataValidator 数据量验证器 in patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/validator/MeshDataValidator.java
  - 方法：validateDataCounts(Map<String, Integer> actualCounts) : ValidationResult
  - 预期数量配置（从 MeshImportConfig 读取）：
    * Descriptor: ~35000（允许误差 5%）
    * Qualifier: ~100（允许误差 5%）
    * TreeNumber: ~80000（允许误差 5%）
    * EntryTerm: ~250000（允许误差 5%）
    * Concept: ~180000（允许误差 5%）
  - 验证规则：
    * 计算实际数量与预期数量的差异百分比
    * 差异超过 5% 生成警告信息
    * 返回 ValidationResult（包含 isValid、warnings 列表）
  - 日志记录：
    * 每张表的验证结果记录 INFO 日志
    * 有警告时记录 WARN 日志
  - 依赖：T002（从 MeshImportConfig 读取预期数量配置）

### 第 4 步：Adapter 层 - 外部接口（TDD）

#### 测试先行（Red 阶段）🔴

- [ ] T037 [P] [Adapter] [US1] 为 MeshImportController 编写切片测试 in patra-catalog/patra-catalog-adapter/src/test/java/com/patra/catalog/adapter/rest/MeshImportControllerTest.java
  - 测试场景：POST /api/v1/mesh/import/start、POST /api/v1/mesh/import/retry/{taskId}、POST /api/v1/mesh/import/clear
  - 测试框架：@WebMvcTest + MockMvc
  - Mock 业务层依赖：MeshImportOrchestrator
  - 验证：HTTP 状态码、请求响应格式、参数校验
  - 注意：异常映射由 MeshImportErrorMappingContributor（SPI）处理，无需在 Controller 测试中验证

- [ ] T038 [Adapter] [US1] 为 MeshImportJob 编写单元测试 in patra-catalog/patra-catalog-adapter/src/test/java/com/patra/catalog/adapter/scheduler/job/MeshImportJobTest.java
  - 测试场景：run()、分布式锁获取失败
  - Mock 业务层依赖：MeshImportOrchestrator
  - 测试框架：JUnit 5 + Mockito

#### 实施（Green 阶段）🟢

- [ ] T036b [App] [US1] 实现 MeshImportErrorMappingContributor in patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/error/MeshImportErrorMappingContributor.java
  - 职责：将标准异常类型映射到特定的业务错误码和 HTTP 状态码
  - 实现：ErrorMappingContributor（patra-spring-boot-starter-core 的 SPI）
  - 映射规则：
    * IllegalStateException → 409 Conflict（业务状态冲突）
    * IllegalArgumentException（"任务不存在"）→ 404 Not Found
    * IllegalArgumentException（其他）→ 400 Bad Request
  - 错误码格式：CATALOG-{httpStatusCode}（如 CATALOG-0409）
  - 设计理念：复用 JDK 标准异常，通过消息内容区分业务语义，集中管理异常映射
  - 依赖：patra-common-core（ErrorCodeLike）、patra-starter-core（ErrorMappingContributor SPI）

- [ ] T039 [Adapter] [US1] 实现 MeshImportController in patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/MeshImportController.java
  - 参考：contracts/mesh-import-api.yaml
  - 端点：POST /api/v1/mesh/import/start、POST /api/v1/mesh/import/retry/{taskId}、POST /api/v1/mesh/import/clear
  - 依赖：MeshImportOrchestrator（通过构造器注入）
  - 异常处理：委托给全局异常处理器（GlobalRestExceptionHandler），异常映射由 MeshImportErrorMappingContributor 处理
  - @RestController + @RequestMapping("/api/v1/mesh/import")
  - 依赖：T038, T036b（Controller 依赖 Orchestrator 和 ErrorMappingContributor）

- [ ] T040 [Adapter] [US1] 实现 MeshImportJob in patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/scheduler/job/MeshImportJob.java
  - 参考：patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/scheduler/job/PubmedHarvestJob.java:1-50（XXL-Job 任务模式）
  - @XxlJob("meshImport")
  - 使用 Redisson 分布式锁（避免并发导入）
  - 依赖：MeshImportOrchestrator
  - 依赖：T038（Job 依赖 Orchestrator）

### 第 5 步：Boot 层 - 端到端测试（验证完整流程）

#### 测试先行（Red 阶段）🔴

- [ ] T041 [Boot] [US1] 为 MeSH 导入编写 E2E 测试 in patra-catalog/patra-catalog-boot/src/test/java/com/patra/catalog/integration/MeshImportE2ETest.java
  - 测试场景：完整业务流程（HTTP → 业务 → DB → 事件发布）
  - 测试框架：@SpringBootTest + TestContainers（MySQL + Redis）+ Awaitility
  - 验证：任务创建、状态转换、数据持久化、领域事件发布
  - 参考：patra-ingest/patra-ingest-boot/src/test/java/com/patra/ingest/integration/outbox/OutboxPatternE2E.java（E2E 测试模式）

**检查点**: 用户故事 1 完全可用且可以独立测试 ✅ MVP 就绪！

---

## 阶段 3：用户故事 2 - 管理员实时监控导入进度（优先级: P2）

**目标**: 提供导入进度查询接口，返回详细进度信息（已处理/总数、处理速度、预计剩余时间）

**独立测试**: 可以通过在导入过程中多次调用"查询导入进度"接口测试

### Domain 层 - 进度计算逻辑（TDD）

#### 测试先行（Red 阶段）🔴

- [ ] T042 [Domain] [US2] 为 MeshImportAggregate 添加进度计算测试 in patra-catalog/patra-catalog-domain/src/test/java/com/patra/catalog/domain/model/aggregate/MeshImportAggregateTest.java
  - 测试场景：calculateProcessSpeed()、estimateRemainingTime()
  - 边界情况：总数为 0、处理中断后恢复

#### 实施（Green 阶段）🟢

- [ ] T043 [Domain] [US2] 扩展 MeshImportAggregate 添加进度计算方法 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/aggregate/MeshImportAggregate.java
  - 新方法：calculateProcessSpeed(), estimateRemainingTime(), getOverallProgress()

### Infrastructure 层 - 批次查询（TDD）

#### 测试先行（Red 阶段）🔴

- [ ] T044 [Infra] [US2] 为 MeshBatchDetailRepositoryImpl 编写集成测试 in patra-catalog/patra-catalog-infra/src/test/java/com/patra/catalog/infra/persistence/repository/MeshBatchDetailRepositoryImplIT.java
  - 测试场景：findFailedBatches()、countByStatus()
  - 测试框架：@MybatisTest + TestContainers

#### 实施（Green 阶段）🟢

- [ ] T045 [Domain] [US2] 定义 MeshBatchDetailPort 接口 in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/MeshBatchDetailPort.java
  - 方法：findFailedBatches(MeshImportId), countByStatus(MeshImportId, MeshBatchStatus)

- [ ] T046 [Infra] [US2] 实现 MeshBatchDetailRepositoryImpl in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/persistence/repository/MeshBatchDetailRepositoryImpl.java
  - 实现：MeshBatchDetailPort
  - 依赖：MeshBatchDetailMapper

### Application 层 - 进度查询编排（TDD）

#### 测试先行（Red 阶段）🔴

- [ ] T047 [App] [US2] 为 MeshProgressQueryOrchestrator 编写单元测试 in patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/meshimport/MeshProgressQueryOrchestratorTest.java
  - 测试场景：queryProgress()
  - Mock 所有 Port 接口
  - 测试框架：JUnit 5 + Mockito

#### 实施（Green 阶段）🟢

- [ ] T048 [API] [US2] 定义 MeshProgressDTO 响应对象 in patra-catalog/patra-catalog-api/src/main/java/com/patra/catalog/api/dto/MeshProgressDTO.java
  - 参考：contracts/mesh-import-api.yaml 第 195-243 行
  - 属性：taskId, status, overallProgress, tableProgress, failedBatches, startTime, endTime, elapsedTime

- [ ] T049 [App] [US2] 实现 MeshProgressQueryOrchestrator in patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/meshimport/MeshProgressQueryOrchestrator.java
  - 职责：查询任务 → 计算进度 → 查询失败批次 → 组装响应
  - 依赖：MeshImportPort, MeshBatchDetailPort

### Adapter 层 - 进度查询接口（TDD）

#### 测试先行（Red 阶段）🔴

- [ ] T050 [Adapter] [US2] 为 MeshImportController 添加进度查询测试 in patra-catalog/patra-catalog-adapter/src/test/java/com/patra/catalog/adapter/rest/MeshImportControllerTest.java
  - 测试场景：GET /api/v1/mesh/import/progress/{taskId}、GET /api/v1/mesh/import/tasks
  - 测试框架：@WebMvcTest + MockMvc

#### 实施（Green 阶段）🟢

- [ ] T051 [Adapter] [US2] 扩展 MeshImportController 添加进度查询端点 in patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/MeshImportController.java
  - 参考：contracts/mesh-import-api.yaml 第 91-149 行
  - 端点：GET /api/v1/mesh/import/progress/{taskId}、GET /api/v1/mesh/import/tasks
  - 依赖：MeshProgressQueryOrchestrator

### Boot 层 - 集成测试

#### 测试先行（Red 阶段）🔴

- [ ] T052 [Boot] [US2] 为进度查询编写 E2E 测试 in patra-catalog/patra-catalog-boot/src/test/java/com/patra/catalog/integration/MeshProgressQueryE2ETest.java
  - 测试场景：启动导入 → 查询进度（多次）→ 验证进度递增
  - 测试框架：@SpringBootTest + TestContainers + Awaitility

**检查点**: 用户故事 2 完全可用且可以独立测试 ✅

---

## 阶段 4：用户故事 3 - 管理员处理失败任务和重新导入（优先级: P3）

**目标**: 提供重试失败任务和清除进度的管理接口

**独立测试**: 可以通过模拟导入失败场景，调用"重试失败任务"和"清除进度重新开始"API 验证

### Application 层 - 重试与清除逻辑（TDD）

#### 测试先行（Red 阶段）🔴

- [ ] T053 [App] [US3] 为 MeshImportOrchestrator 添加重试测试 in patra-catalog/patra-catalog-app/src/test/java/com/patra/catalog/app/usecase/meshimport/MeshImportOrchestratorTest.java
  - 测试场景：retryFailedTask()、clearAndRestart()
  - 验证：仅重新处理失败批次、清除数据完整性
  - Mock 所有 Port 接口

#### 实施（Green 阶段）🟢

- [ ] T054 [App] [US3] 扩展 MeshImportOrchestrator 添加重试方法 in patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/meshimport/MeshImportOrchestrator.java
  - 新方法：retryFailedTask(MeshImportId), clearAndRestart()
  - 重试逻辑：仅重新处理状态为 FAILED 的批次

### Adapter 层 - 重试与清除接口（TDD）

#### 测试先行（Red 阶段）🔴

- [ ] T055 [Adapter] [US3] 为 MeshImportController 添加重试测试 in patra-catalog/patra-catalog-adapter/src/test/java/com/patra/catalog/adapter/rest/MeshImportControllerTest.java
  - 测试场景：POST /api/v1/mesh/import/retry/{taskId}、POST /api/v1/mesh/import/clear
  - 验证：状态码、错误处理（任务不存在、状态不允许重试）

#### 实施（Green 阶段）🟢

- [ ] T056 [Adapter] [US3] 扩展 MeshImportController 添加重试端点 in patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/rest/MeshImportController.java
  - 参考：contracts/mesh-import-api.yaml 第 42-89 行
  - 端点：POST /api/v1/mesh/import/retry/{taskId}、POST /api/v1/mesh/import/clear

### Boot 层 - 集成测试

#### 测试先行（Red 阶段）🔴

- [ ] T057 [Boot] [US3] 为重试和清除编写 E2E 测试 in patra-catalog/patra-catalog-boot/src/test/java/com/patra/catalog/integration/MeshImportRetryE2ETest.java
  - 测试场景：模拟失败 → 重试 → 验证成功、清除 → 重新导入
  - 测试框架：@SpringBootTest + TestContainers

**检查点**: 用户故事 3 完全可用且可以独立测试 ✅

---

## 阶段 5：润色和跨领域关注点

**目的**: 影响多个用户故事的改进（文档、监控、架构验证）

### 监控与可观察性任务

- [ ] T058 [P] [Infra] 添加 Micrometer 自定义指标 in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/metrics/MeshImportMetrics.java
  - 指标：任务耗时、批次处理速度、失败率、表级别进度
  - 参考：spec.md 第 151-155 行（可观察性要求）

- [ ] T059 [P] [App] 添加详细日志和 SkyWalking 追踪 in patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/usecase/meshimport/MeshImportOrchestrator.java
  - 日志级别：INFO（批次处理）、ERROR（失败详情）
  - SkyWalking Span：每个批次操作

### 文档任务

#### package-info.java 生成

- [ ] T060 [P] [Doc] 生成 package-info.java for com.patra.catalog.domain in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/package-info.java
  - 描述：Catalog 领域模型包，包含 MeSH 导入聚合根和 MeSH 领域实体
  - 主要组件：MeshImportAggregate、MeshDescriptor、TableProgress

- [ ] T061 [P] [Doc] 生成 package-info.java for com.patra.catalog.domain.model.aggregate in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/aggregate/package-info.java
  - 描述：Catalog 聚合根包
  - 主要组件：MeshImportAggregate（MeSH 导入任务聚合根）

- [ ] T062 [P] [Doc] 生成 package-info.java for com.patra.catalog.domain.model.valueobject in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/model/valueobject/package-info.java
  - 描述：Catalog 值对象包
  - 主要组件：MeshImportId、TableProgress、DescriptorId

- [ ] T063 [P] [Doc] 生成 package-info.java for com.patra.catalog.domain.event in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/event/package-info.java
  - 描述：Catalog 领域事件包
  - 主要组件：MeshImportStarted、MeshImportCompleted、MeshImportFailed

- [ ] T064 [P] [Doc] 生成 package-info.java for com.patra.catalog.domain.port in patra-catalog/patra-catalog-domain/src/main/java/com/patra/catalog/domain/port/package-info.java
  - 描述：Catalog Port 接口包（依赖倒置）
  - 主要组件：MeshImportPort、XmlParserPort、MeshFileDownloadPort

- [ ] T065 [P] [Doc] 生成 package-info.java for com.patra.catalog.app in patra-catalog/patra-catalog-app/src/main/java/com/patra/catalog/app/package-info.java
  - 描述：Catalog 应用层用例编排包
  - 主要子包：
    * app.usecase.meshimport - MeSH 数据导入用例（MeshImportOrchestrator、MeshDataValidator）
    * app.error - 异常映射贡献者（MeshImportErrorMappingContributor）

- [ ] T066 [P] [Doc] 生成 package-info.java for com.patra.catalog.infra in patra-catalog/patra-catalog-infra/src/main/java/com/patra/catalog/infra/package-info.java
  - 描述：Catalog 基础设施层技术实现包
  - 主要组件：MeshImportRepositoryImpl、StaxXmlParserImpl、RestClientMeshFileDownloadImpl

- [ ] T067 [P] [Doc] 生成 package-info.java for com.patra.catalog.adapter in patra-catalog/patra-catalog-adapter/src/main/java/com/patra/catalog/adapter/package-info.java
  - 描述：Catalog 适配器层外部交互包
  - 子包：
    * adapter.rest - REST API 控制器（MeshImportController）
    * adapter.rocketmq - 消息监听器（TaskReadyMessageListener）
    * adapter.scheduler - 定时任务（MeshImportJob）

#### 模块文档更新

- [ ] T068 [Doc] 创建或更新模块 README.md in patra-catalog/README.md
  - 检查是否存在：patra-catalog/README.md
  - 如不存在：使用 plan.md 生成的骨架作为基础
  - 如存在：增量更新（不覆盖原有内容）
  - 新增内容：
    - 🎯 核心类说明（MeshImportAggregate、MeshImportOrchestrator、MeshImportRepositoryImpl、MeshImportController）
    - 📝 变更日志（v0.2.0 - 新增 MeSH 数据导入功能）

#### API 文档生成

- [ ] T069 [Doc] 补充 API 文档详细内容 in specs/001-mesh-data-import/contracts/mesh-import-api.yaml
  - 补充：请求示例、响应示例、错误码详细说明
  - 数据来源：spec.md 的用户场景 Given/When/Then

### 其他润色任务

- [ ] T070 [Refactor] 代码清理和重构
  - 检查：DRY 原则、命名规范、注释完整性
  - 运行 Spotless（代码格式化）

- [ ] T071 [P] [Test] 补充单元测试覆盖边界情况 in patra-catalog/patra-catalog-domain/src/test/java/
  - 边界情况：空文件、超大文件、网络中断、并发冲突

- [ ] T072 [Verify] 运行 quickstart.md 验证
  - 验证：按 quickstart.md 步骤操作，确保可以成功导入 MeSH 数据

- [ ] T073 [Test] 创建 ArchUnit 架构测试套件验证架构合规性
  - 参考：patra-ingest/patra-ingest-boot/src/test/java/com/patra/ingest/architecture/IngestArchitectureTest.java:1-247
  - 主测试类：patra-catalog/patra-catalog-boot/src/test/java/com/patra/catalog/architecture/CatalogArchitectureTest.java
  - 规则类目录：patra-catalog/patra-catalog-boot/src/test/java/com/patra/catalog/architecture/rules/
  - 需要创建的规则类（复用 patra-ingest 的实现，替换包名 ingest → catalog）：
    1. LayerDependencyRules.java - 层依赖方向规则（7条规则）
    2. DomainLayerRules.java - Domain 层纯净性规则（3条规则）
    3. NamingConventionRules.java - 命名约定规则（5条规则）
    4. EncapsulationRules.java - 封装规则（3条规则）
    5. TransactionBoundaryRules.java - 事务边界规则（2条规则）
    6. TestingRules.java - 测试规范规则（6条规则）
  - 验证规则总计：22条 ArchUnit 规则
  - 核心验证点：
    - 层依赖方向（Adapter → App → Domain ← Infra）
    - Domain 层零 Spring 依赖
    - Port 接口在 domain.port 包
    - DO 类不泄露到 infra 层外
    - @Transactional 仅在 App 层
    - 测试类命名规范（*Test.java, *IT.java, *E2E.java）

---

## 依赖和执行顺序

### 阶段依赖

- **阶段 1（设置）**: 无依赖 - 可以立即开始
- **阶段 2（US1）**: 依赖阶段 1 完成
- **阶段 3（US2）**: 依赖阶段 2 完成（扩展 US1 的功能）
- **阶段 4（US3）**: 依赖阶段 2 完成（扩展 US1 的功能）
- **阶段 5（润色）**: 依赖所有用户故事完成

### 用户故事内的依赖

在每个用户故事内，必须按以下顺序执行：

1. **Domain 层**：测试（Red）→ 实施（Green）
2. **Infrastructure 层**：测试（Red）→ 实施（Green）
3. **Application 层**：测试（Red）→ 实施（Green）（依赖 Domain + Infra）
4. **Adapter 层**：测试（Red）→ 实施（Green）（依赖 App）
5. **Boot 层**：E2E 测试（验证完整流程）

### 并行机会

- **阶段 1**: T001（数据库 DDL）、T002-T004（配置）可并行
- **阶段 2（US1）**:
  - Domain 层：T005-T006（测试）、T007-T015（实施）可并行（不同文件）
  - Infra 层：T019-T022（测试）、T023-T028（DO 和 Mapper）可并行
- **阶段 3（US2）**: 可与阶段 4（US3）并行开始（如果有多人协作）
- **阶段 5**: T060-T067（package-info.java）可并行

---

## 任务统计报告

### 总任务数

- **总计**: 74 个任务（原75个，3个数据库表DDL合并为1个后减少2个，新增1个ErrorMappingContributor任务）

### 各用户故事任务数

- **阶段 1（设置与基础）**: 4 个任务（原6个，3个数据库表DDL合并为1个）
- **阶段 2（US1 - 导入核心功能）**: 38 个任务（测试 7 + 实施 31，含 ErrorMappingContributor）
- **阶段 3（US2 - 进度监控）**: 11 个任务（测试 4 + 实施 7）
- **阶段 4（US3 - 重试与清除）**: 5 个任务（测试 3 + 实施 2）
- **阶段 5（润色与文档）**: 16 个任务（监控 2 + 文档 11 + 其他 3）

### 并行机会

- **阶段 1**: 4 个任务中有 4 个可并行（100%）
- **阶段 2**: 37 个任务中有 20 个可并行（54%）
- **阶段 3**: 11 个任务中有 5 个可并行（45%）
- **阶段 5**: 16 个任务中有 12 个可并行（75%）

### MVP 范围建议

**最小可行产品（MVP）= 阶段 1 + 阶段 2（US1）**
- 任务数：42 个任务（原43个，合并DDL后减少2个，新增ErrorMappingContributor后增加1个）
- 交付价值：完整的 MeSH 数据导入流程（下载 → 解析 → 导入 → 断点续传 + 统一异常处理）
- 独立测试：可以通过 E2E 测试验证完整业务流程

**增量交付路径**:
1. **MVP（US1）**: 核心导入功能 ✅
2. **MVP + US2**: 添加进度监控 ✅
3. **MVP + US2 + US3**: 添加重试和清除 ✅
4. **完整版本**: 添加文档和架构验证 ✅

---

## 实施策略

### MVP 优先（推荐）

1. 完成阶段 1：设置与基础（4 个任务）
2. 完成阶段 2：用户故事 1（37 个任务）
3. **停止并验证**: 独立测试用户故事 1（E2E 测试）
4. 如果准备好则部署/演示

### 增量交付

1. 完成设置 + US1 → 基础就绪 + MVP 就绪
2. 添加 US2 → 独立测试 → 部署/演示
3. 添加 US3 → 独立测试 → 部署/演示
4. 添加润色 → 最终版本

### 单人开发策略

由于本项目是单人开发，建议：
1. 严格按照 TDD 流程（Red → Green → Refactor）
2. 按阶段顺序执行（阶段 1 → 2 → 3 → 4 → 5）
3. 每个用户故事内按层次顺序（Domain → Infra → App → Adapter → Boot）
4. 每完成一个用户故事立即运行 E2E 测试验证

---

## 注意事项

- **[P] 任务** = 不同文件，无依赖，可并行执行
- **[Story] 标签** 将任务映射到特定用户故事以实现可追溯性
- **每个用户故事应该可以独立完成和测试**
- **在实施之前验证测试失败（Red 阶段）**
- **在每个任务或逻辑组之后提交**
- **在任何检查点停止以独立验证故事**
- **避免**：模糊任务、同一文件冲突、破坏独立性的跨故事依赖

---

**祝实施顺利！记住：测试驱动设计，架构指导实现，技术服务业务！🚀**
