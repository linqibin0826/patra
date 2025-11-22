/// Ingest 领域层 - 端口接口包(六边形架构 Ports)。
///
/// 本包定义了领域层与外部系统交互的所有端口(Ports)接口,遵循六边形架构(Hexagonal Architecture)
/// 的依赖倒置原则。端口由领域层定义,基础设施层(Infrastructure)提供适配器(Adapters)实现。
///
/// ## 端口分类
///
/// ### 1. 仓储端口(Repository Ports) - 被驱动端口
///
/// 用于持久化聚合根和实体,由基础设施层的 MyBatis-Plus 实现:
///
/// - {@link com.patra.ingest.domain.port.PlanRepository} - 计划聚合根仓储
///   - {@link com.patra.ingest.domain.port.PlanSliceRepository} - 计划切片聚合根仓储
///   - {@link com.patra.ingest.domain.port.TaskRepository} - 任务聚合根仓储
///   - {@link com.patra.ingest.domain.port.TaskRunRepository} - 任务执行记录仓储
///   - {@link com.patra.ingest.domain.port.TaskRunBatchRepository} - 任务批次仓储
///   - {@link com.patra.ingest.domain.port.ScheduleInstanceRepository} - 调度实例仓储
///   - {@link com.patra.ingest.domain.port.CursorRepository} - 游标仓储
///   - {@link com.patra.ingest.domain.port.CursorEventRepository} - 游标事件仓储
///   - {@link com.patra.ingest.domain.port.OutboxMessageRepository} - Outbox 消息仓储
///   - {@link com.patra.ingest.domain.port.OutboxRelayLogRepository} - Outbox 中继日志仓储
///   - {@link com.patra.ingest.domain.port.OutboxRelayRepository} - Outbox 中继存储(复合操作)
///
/// ### 2. 外部服务端口(External Service Ports) - 被驱动端口
///
/// 用于调用外部系统,由基础设施层的 HTTP 客户端或 SDK 实现:
///
/// - {@link com.patra.ingest.domain.port.ProvenanceDataPort} - 数据源端口
///
/// - 从外部数据源获取标准化数据（出版物、期刊、药品等）
///         - 支持多种数据源(PubMed, EPMC 等)和多种数据类型
///         - 处理分页、游标和错误重试
///         - 通过 `ProvenanceDataAdapter` (Infrastructure) 调用 `ProvenanceDataProvider`
///             (Framework)
///
///   - {@link com.patra.ingest.domain.port.PatraRegistryPort} - Patra Registry 配置服务端口
///
/// - 获取 Provenance 配置快照
///         - 查询数据字典和元数据
///
///   - {@link com.patra.ingest.domain.port.PublicationStoragePort} - 出版物存储服务端口
///
/// - 持久化采集到的出版物数据
///         - 支持批量写入和事务性提交
///
///   - {@link com.patra.ingest.domain.port.StorageMetadataPort} - 存储元数据服务端口
///
/// - 查询和更新出版物元数据
///
///   - {@link com.patra.ingest.domain.port.StoragePort} - 通用存储适配器端口
///
/// - 文件上传和下载
///         - 支持多种存储后端(OSS, S3等)
///
/// ### 3. 技术服务端口(Technical Service Ports) - 被驱动端口
///
/// 用于调用技术基础设施,由基础设施层实现:
///
/// - {@link com.patra.ingest.domain.port.ExpressionCompilerPort} - 表达式编译器端口
///
/// - 编译和验证 patra-expr 表达式
///         - 用于计划窗口的动态表达式解析
///
///   - {@link com.patra.ingest.domain.port.OutboxPublisherPort} - Outbox 发布器端口
///
/// - 发布 Outbox 消息到 RocketMQ
///         - 实现事务性消息发布模式
///
///   - {@link com.patra.ingest.domain.port.TechnicalRetryPort} - 技术重试端口
///
/// - 执行技术层面的重试逻辑(网络故障等)
///         - 区别于业务补偿
///
/// ## 六边形架构依赖方向
///
/// ```
///
/// ┌─────────────────────────────────────────────────────────┐
/// │                    Adapter Layer                        │
/// │  (Driving: REST, RocketMQ Listener, XXL-Job)            │
/// └──────────────────────┬──────────────────────────────────┘
///                        │ 调用
///                        ↓
/// ┌─────────────────────────────────────────────────────────┐
/// │                  Application Layer                      │
/// │           (Orchestrators, Use Cases)                    │
/// └──────────────────────┬──────────────────────────────────┘
///                        │ 调用
///                        ↓
/// ┌─────────────────────────────────────────────────────────┐
/// │                    Domain Layer                         │
/// │  ┌──────────────┐       ┌───────────────────────┐       │
/// │  │  Aggregates  │       │  Ports (Interfaces)   │       │
/// │  │  Entities    │       │  - Repository         │       │
/// │  │  Value Objs  │       │  - External Service   │       │
/// │  └──────────────┘       └───────────────────────┘       │
/// └──────────────────────┬──────────────────────────────────┘
///                        │ 实现
///                        ↓
/// ┌─────────────────────────────────────────────────────────┐
/// │                Infrastructure Layer                     │
/// │  (Driven: MyBatis, HTTP Client, RocketMQ Producer)      │
/// └─────────────────────────────────────────────────────────┘
///
/// ```
///
/// **关键原则**:
///
/// - 领域层定义端口接口,不依赖具体实现
///   - 基础设施层实现端口,依赖领域层接口
///   - 依赖箭头从外层指向内层(依赖倒置)
///
/// ## 端口命名约定
///
/// - **Repository**: 仓储端口,用于聚合根持久化(如 `PlanRepository`)
///   - **Port**: 外部服务端口,用于调用外部系统(如 `ProvenanceDataPort`)
///   - **Adapter**: 通用适配器端口(如 `StoragePort`)
///   - **Store**: 复合存储端口,组合多个仓储操作(如 `OutboxRelayRepository`)
///
/// ## 端口方法设计规范
///
/// ### 1. 仓储端口方法
///
/// ```java
/// public interface PlanRepository {
///     // 保存或更新
///     void save(PlanAggregate plan);
///
///     // 根据 ID 查询(Optional 避免 null)
///     Optional<PlanAggregate> findById(Long id);
///
///     // 业务查询(清晰语义)
///     Optional<PlanAggregate> findByPlanKey(String planKey);
///     List<PlanAggregate> findByStatus(PlanStatus status);
///
///     // 分页查询
///     Page<PlanAggregate> findAll(Pageable pageable);
///
///     // 删除(慎用,通常使用软删除)
///     void deleteById(Long id);
/// ```
///
/// ### 2. 外部服务端口方法
///
/// ```java
/// public interface ProvenanceDataPort {
///     // 领域语义方法名
///     PlanMetadata prepareQuerySession(ExecutionContext context, DataType dataType);
///
///     // 泛型支持多种数据类型（出版物、期刊、药品等）
///     <T> DataFetchResult<T> fetchData(
///         ExecutionContext context,
///         DataType dataType,
///         TypeReference<T> typeRef,
///         Batch batch
///     );
///
///     // 能力查询
///     boolean supports(String provenanceCode, DataType dataType);
///     Set<DataType> getSupportedTypes(String provenanceCode);
/// ```
///
/// ## 使用示例
///
/// ### 领域层定义端口
///
/// ```java
/// // domain/port/PlanRepository.java
/// package com.patra.ingest.domain.port;
///
/// public interface PlanRepository {
///     void save(PlanAggregate plan);
///     Optional<PlanAggregate> findById(Long id);
/// ```
///
/// ### 基础设施层实现端口
///
/// ```java
/// // infra/persistence/repository/PlanRepositoryMpImpl.java
/// package com.patra.ingest.infra.persistence.repository;
///
/// import com.patra.ingest.domain.port.PlanRepository;
///
/// @Repository
/// public class PlanRepositoryMpImpl implements PlanRepository {
///     @Autowired
///     private PlanMapper planMapper;
///
///     @Override
///     public void save(PlanAggregate plan) {
///         PlanDO planDO = converter.toDataObject(plan);
///         if (plan.getId() == null) {
///             planMapper.insert(planDO); else {
///             planMapper.updateById(planDO);
/// ```
///
/// ### 应用层使用端口
///
/// ```java
/// // app/usecase/PlanIngestionUseCase.java
/// package com.patra.ingest.app.usecase;
///
/// import com.patra.ingest.domain.port.PlanRepository;
///
/// @Service
/// public class PlanIngestionUseCase {
///     private final PlanRepository planRepository;  // 依赖接口
///
///     public PlanIngestionResult execute(PlanIngestionCommand command) {
///         PlanAggregate plan = PlanAggregate.create(...);
///         planRepository.save(plan);  // 调用端口方法
///         return ...;
/// ```
///
/// ## 端口测试策略
///
/// - **契约测试**: 验证端口实现满足接口契约
///   - **Mock 测试**: 使用 Mockito Mock 端口进行单元测试
///   - **集成测试**: 使用真实实现进行端到端测试
///
/// ## 最佳实践
///
/// - 端口接口应放在领域层,体现领域需求
///   - 端口方法命名应符合领域语义,而非技术实现
///   - 端口返回领域对象,不返回 DTO 或 DO
///   - 端口方法应保持职责单一,避免上帝接口
///   - 端口接口不应有默认实现(除非 Java 8 默认方法)
///   - 端口应避免循环依赖,保持依赖单向性
///
/// @see com.patra.ingest.domain.model.aggregate 聚合根定义
/// @see com.patra.ingest.infra.persistence.repository 仓储端口实现
/// @see com.patra.ingest.infra.integration 外部服务端口实现
/// @author linqibin
/// @since 0.1.0
package com.patra.ingest.domain.port;
