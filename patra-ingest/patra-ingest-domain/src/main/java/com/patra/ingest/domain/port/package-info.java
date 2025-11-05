/**
 * Ingest 领域层 - 端口接口包(六边形架构 Ports)。
 *
 * <p>本包定义了领域层与外部系统交互的所有端口(Ports)接口,遵循六边形架构(Hexagonal Architecture)
 * 的依赖倒置原则。端口由领域层定义,基础设施层(Infrastructure)提供适配器(Adapters)实现。
 *
 * <h2>端口分类</h2>
 *
 * <h3>1. 仓储端口(Repository Ports) - 被驱动端口</h3>
 *
 * <p>用于持久化聚合根和实体,由基础设施层的 MyBatis-Plus 实现:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.port.PlanRepository} - 计划聚合根仓储</li>
 *   <li>{@link com.patra.ingest.domain.port.PlanSliceRepository} - 计划切片聚合根仓储</li>
 *   <li>{@link com.patra.ingest.domain.port.TaskRepository} - 任务聚合根仓储</li>
 *   <li>{@link com.patra.ingest.domain.port.TaskRunRepository} - 任务执行记录仓储</li>
 *   <li>{@link com.patra.ingest.domain.port.TaskRunBatchRepository} - 任务批次仓储</li>
 *   <li>{@link com.patra.ingest.domain.port.ScheduleInstanceRepository} - 调度实例仓储</li>
 *   <li>{@link com.patra.ingest.domain.port.CursorRepository} - 游标仓储</li>
 *   <li>{@link com.patra.ingest.domain.port.CursorEventRepository} - 游标事件仓储</li>
 *   <li>{@link com.patra.ingest.domain.port.OutboxMessageRepository} - Outbox 消息仓储</li>
 *   <li>{@link com.patra.ingest.domain.port.OutboxRelayLogRepository} - Outbox 中继日志仓储</li>
 *   <li>{@link com.patra.ingest.domain.port.OutboxRelayStore} - Outbox 中继存储(复合操作)</li>
 * </ul>
 *
 * <h3>2. 外部服务端口(External Service Ports) - 被驱动端口</h3>
 *
 * <p>用于调用外部系统,由基础设施层的 HTTP 客户端或 SDK 实现:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.port.PatraRegistryPort} - Patra Registry 配置服务端口
 *       <ul>
 *         <li>获取 Provenance 配置快照</li>
 *         <li>查询数据字典和元数据</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.ingest.domain.port.PubmedSearchPort} - PubMed 搜索 API 端口
 *       <ul>
 *         <li>执行全量和增量数据采集</li>
 *         <li>支持游标分页和批量查询</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.ingest.domain.port.LiteratureStoragePort} - 文献存储服务端口
 *       <ul>
 *         <li>持久化采集到的文献数据</li>
 *         <li>支持批量写入和事务性提交</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.ingest.domain.port.StorageMetadataPort} - 存储元数据服务端口
 *       <ul>
 *         <li>查询和更新文献元数据</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.ingest.domain.port.StorageAdapter} - 通用存储适配器端口
 *       <ul>
 *         <li>文件上传和下载</li>
 *         <li>支持多种存储后端(OSS, S3等)</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>3. 技术服务端口(Technical Service Ports) - 被驱动端口</h3>
 *
 * <p>用于调用技术基础设施,由基础设施层实现:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.port.ExpressionCompilerPort} - 表达式编译器端口
 *       <ul>
 *         <li>编译和验证 patra-expr 表达式</li>
 *         <li>用于计划窗口的动态表达式解析</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.ingest.domain.port.OutboxPublisherPort} - Outbox 发布器端口
 *       <ul>
 *         <li>发布 Outbox 消息到 RocketMQ</li>
 *         <li>实现事务性消息发布模式</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.ingest.domain.port.TechnicalRetryPort} - 技术重试端口
 *       <ul>
 *         <li>执行技术层面的重试逻辑(网络故障等)</li>
 *         <li>区别于业务补偿</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>六边形架构依赖方向</h2>
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │                    Adapter Layer                        │
 * │  (Driving: REST, RocketMQ Listener, XXL-Job)            │
 * └──────────────────────┬──────────────────────────────────┘
 *                        │ 调用
 *                        ↓
 * ┌─────────────────────────────────────────────────────────┐
 * │                  Application Layer                      │
 * │           (Orchestrators, Use Cases)                    │
 * └──────────────────────┬──────────────────────────────────┘
 *                        │ 调用
 *                        ↓
 * ┌─────────────────────────────────────────────────────────┐
 * │                    Domain Layer                         │
 * │  ┌──────────────┐       ┌───────────────────────┐       │
 * │  │  Aggregates  │       │  Ports (Interfaces)   │       │
 * │  │  Entities    │       │  - Repository         │       │
 * │  │  Value Objs  │       │  - External Service   │       │
 * │  └──────────────┘       └───────────────────────┘       │
 * └──────────────────────┬──────────────────────────────────┘
 *                        │ 实现
 *                        ↓
 * ┌─────────────────────────────────────────────────────────┐
 * │                Infrastructure Layer                     │
 * │  (Driven: MyBatis, HTTP Client, RocketMQ Producer)      │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>关键原则</b>:
 * <ul>
 *   <li>领域层定义端口接口,不依赖具体实现</li>
 *   <li>基础设施层实现端口,依赖领域层接口</li>
 *   <li>依赖箭头从外层指向内层(依赖倒置)</li>
 * </ul>
 *
 * <h2>端口命名约定</h2>
 *
 * <ul>
 *   <li><b>Repository</b>: 仓储端口,用于聚合根持久化(如 {@code PlanRepository})</li>
 *   <li><b>Port</b>: 外部服务端口,用于调用外部系统(如 {@code PubmedSearchPort})</li>
 *   <li><b>Adapter</b>: 通用适配器端口(如 {@code StorageAdapter})</li>
 *   <li><b>Store</b>: 复合存储端口,组合多个仓储操作(如 {@code OutboxRelayStore})</li>
 * </ul>
 *
 * <h2>端口方法设计规范</h2>
 *
 * <h3>1. 仓储端口方法</h3>
 *
 * <pre>{@code
 * public interface PlanRepository {
 *     // 保存或更新
 *     void save(PlanAggregate plan);
 *
 *     // 根据 ID 查询(Optional 避免 null)
 *     Optional<PlanAggregate> findById(Long id);
 *
 *     // 业务查询(清晰语义)
 *     Optional<PlanAggregate> findByPlanKey(String planKey);
 *     List<PlanAggregate> findByStatus(PlanStatus status);
 *
 *     // 分页查询
 *     Page<PlanAggregate> findAll(Pageable pageable);
 *
 *     // 删除(慎用,通常使用软删除)
 *     void deleteById(Long id);
 * }
 * }</pre>
 *
 * <h3>2. 外部服务端口方法</h3>
 *
 * <pre>{@code
 * public interface PubmedSearchPort {
 *     // 领域语义方法名
 *     List<LiteratureMetadata> harvest(
 *         ProvenanceConfig config,
 *         TaskParams params
 *     );
 *
 *     // 明确返回类型(避免泛型擦除)
 *     SearchResult<Literature> search(
 *         String query,
 *         CursorWatermark cursor,
 *         int batchSize
 *     );
 *
 *     // 异常语义明确(不吞异常)
 *     Optional<CursorWatermark> getLatestCursor()
 *         throws PubmedApiException;
 * }
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <h3>领域层定义端口</h3>
 *
 * <pre>{@code
 * // domain/port/PlanRepository.java
 * package com.patra.ingest.domain.port;
 *
 * public interface PlanRepository {
 *     void save(PlanAggregate plan);
 *     Optional<PlanAggregate> findById(Long id);
 * }
 * }</pre>
 *
 * <h3>基础设施层实现端口</h3>
 *
 * <pre>{@code
 * // infra/persistence/repository/PlanRepositoryMpImpl.java
 * package com.patra.ingest.infra.persistence.repository;
 *
 * import com.patra.ingest.domain.port.PlanRepository;
 *
 * @Repository
 * public class PlanRepositoryMpImpl implements PlanRepository {
 *     @Autowired
 *     private PlanMapper planMapper;
 *
 *     @Override
 *     public void save(PlanAggregate plan) {
 *         PlanDO planDO = converter.toDataObject(plan);
 *         if (plan.getId() == null) {
 *             planMapper.insert(planDO);
 *         } else {
 *             planMapper.updateById(planDO);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>应用层使用端口</h3>
 *
 * <pre>{@code
 * // app/usecase/PlanIngestionUseCase.java
 * package com.patra.ingest.app.usecase;
 *
 * import com.patra.ingest.domain.port.PlanRepository;
 *
 * @Service
 * public class PlanIngestionUseCase {
 *     private final PlanRepository planRepository;  // 依赖接口
 *
 *     public PlanIngestionResult execute(PlanIngestionCommand command) {
 *         PlanAggregate plan = PlanAggregate.create(...);
 *         planRepository.save(plan);  // 调用端口方法
 *         return ...;
 *     }
 * }
 * }</pre>
 *
 * <h2>端口测试策略</h2>
 *
 * <ul>
 *   <li><b>契约测试</b>: 验证端口实现满足接口契约</li>
 *   <li><b>Mock 测试</b>: 使用 Mockito Mock 端口进行单元测试</li>
 *   <li><b>集成测试</b>: 使用真实实现进行端到端测试</li>
 * </ul>
 *
 * <h2>最佳实践</h2>
 *
 * <ul>
 *   <li>端口接口应放在领域层,体现领域需求</li>
 *   <li>端口方法命名应符合领域语义,而非技术实现</li>
 *   <li>端口返回领域对象,不返回 DTO 或 DO</li>
 *   <li>端口方法应保持职责单一,避免上帝接口</li>
 *   <li>端口接口不应有默认实现(除非 Java 8 默认方法)</li>
 *   <li>端口应避免循环依赖,保持依赖单向性</li>
 * </ul>
 *
 * @see com.patra.ingest.domain.model.aggregate 聚合根定义
 * @see com.patra.ingest.infra.persistence.repository 仓储端口实现
 * @see com.patra.ingest.infra.integration 外部服务端口实现
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.domain.port;
