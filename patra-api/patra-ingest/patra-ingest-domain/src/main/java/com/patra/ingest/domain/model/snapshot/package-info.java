/// Ingest 领域模型 - 快照包。
///
/// 本包包含领域对象的快照(Snapshot)实现,用于捕获特定时间点的配置或状态,确保数据一致性和可回溯性。 快照模式在分布式系统中广泛应用于配置版本管理、事件溯源和断点续传。
///
/// ## 核心快照
///
/// - {@link com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot} - 数据源配置快照
///
/// - 捕获 patra-registry 服务返回的 Provenance 配置在特定时间点的完整状态
///         - 保证任务执行过程中使用一致的配置,即使 registry 配置变更
///         - 包含配置内容的 JSON 表示和 SHA-256 哈希值
///         - 支持配置变更检测和版本管理
///
/// ## 快照模式动机
///
/// **问题场景**: 在长时间运行的数据采集任务中,外部配置(如 Provenance 的 API 端点、认证密钥) 可能在任务执行过程中被修改,导致以下问题:
///
/// - 任务执行不一致:同一计划的不同任务使用不同配置
///   - 断点续传失败:恢复执行时配置已变更,无法继续
///   - 审计追溯困难:无法确定历史任务使用了哪个版本的配置
///
/// **解决方案**: 在计划创建时捕获配置快照,任务执行期间使用快照而非实时查询:
///
/// ```java
/// // 计划创建时捕获快照
/// ProvenanceConfigSnapshot snapshot = registryPort
///     .getProvenanceConfig(provenanceCode)
///     .toSnapshot();
///
/// PlanAggregate plan = PlanAggregate.create(
///     metadata,
///     window,
///     snapshot  // 快照嵌入计划
/// );
///
/// // 任务执行时使用快照
/// ProvenanceConfig config = plan.getSnapshot().toConfig();
/// api.harvest(config.getEndpoint(), config.getApiKey());
/// ```
///
/// ## 快照设计原则
///
/// - **不可变性**: 快照创建后不可修改,保证历史数据完整性
///   - **自包含性**: 快照包含恢复对象所需的全部信息
///   - **版本标识**: 通过哈希值或版本号标识快照唯一性
///   - **序列化友好**: 快照应易于序列化和反序列化(通常为 JSON)
///
/// ## ProvenanceConfigSnapshot 结构
///
/// ```java
/// public class ProvenanceConfigSnapshot {
///     private final String configJson;       // 配置的 JSON 表示
///     private final String configHash;       // SHA-256 哈希
///     private final Instant capturedAt;      // 捕获时间
///     private final String provenanceCode;   // 关联的数据源代码
///
///     // 不可变构造
///     private ProvenanceConfigSnapshot(...) {
///
///     // 从配置创建快照
///     public static ProvenanceConfigSnapshot of(ProvenanceConfig config) {
///         String json = objectMapper.writeValueAsString(config);
///         String hash = DigestUtils.sha256Hex(json);
///         return new ProvenanceConfigSnapshot(json, hash, Instant.now(), ...);
///
///     // 恢复配置对象
///     public ProvenanceConfig toConfig() {
///         return objectMapper.readValue(configJson, ProvenanceConfig.class);
///
///     // 变更检测
///     public boolean hasChanged(ProvenanceConfig current) {
///         return !this.configHash.equals(current.computeHash());
/// ```
///
/// ## 快照生命周期
///
/// ```
///
/// 1. 捕获 (Capture)
///    ↓
///    [从外部系统读取配置] → [序列化为 JSON] → [计算哈希] → [创建快照对象]
///
/// 2. 存储 (Store)
///    ↓
///    [嵌入聚合根] → [持久化到数据库] → [长期保存]
///
/// 3. 使用 (Use)
///    ↓
///    [从聚合根读取] → [反序列化 JSON] → [恢复配置对象] → [执行业务逻辑]
///
/// 4. 验证 (Validate)
///    ↓
///    [读取当前配置] → [对比哈希] → [检测变更] → [触发告警或补偿]
///
/// ```
///
/// ## 使用示例
///
/// ### 场景 1: 创建计划时捕获配置快照
///
/// ```java
/// // 在 PlanAssembler 中
/// public PlanAggregate assemblePlan(PlanIngestionCommand command) {
///     // 查询实时配置
///     ProvenanceConfig liveConfig = registryPort.getConfig(command.getProvenanceCode());
///
///     // 捕获快照
///     ProvenanceConfigSnapshot snapshot = ProvenanceConfigSnapshot.of(liveConfig);
///
///     // 创建计划(快照嵌入聚合根)
///     return PlanAggregate.create(
///         metadata,
///         window,
///         sliceStrategy,
///         snapshot  // 快照在计划整个生命周期中保持不变
///     );
/// ```
///
/// ### 场景 2: 任务执行时使用快照配置
///
/// ```java
/// // 在 TaskExecutionUseCase 中
/// public void executeTask(Long taskId) {
///     TaskAggregate task = taskRepository.findById(taskId);
///     PlanAggregate plan = planRepository.findById(task.getPlanId());
///
///     // 从快照恢复配置
///     ProvenanceConfig config = plan.getSnapshot().toConfig();
///
///     // 使用快照配置执行采集
///     List<Publication> data = pubmedPort.harvest(
///         config.getEndpoint(),
///         config.getApiKey(),
///         task.getParams()
///     );
/// ```
///
/// ### 场景 3: 检测配置变更
///
/// ```java
/// // 在监控服务中
/// public void detectConfigDrift() {
///     List<PlanAggregate> runningPlans = planRepository.findByStatus(PlanStatus.READY);
///
///     for (PlanAggregate plan : runningPlans) {
///         ProvenanceConfig currentConfig = registryPort.getConfig(plan.getProvenanceCode());
///
///         if (plan.getSnapshot().hasChanged(currentConfig)) {
///             logger.warn("Config drift detected for plan {: expected hash {, got {",
///                 plan.getId(),
///                 plan.getSnapshot().getConfigHash(),
///                 currentConfig.computeHash());
///
///             // 触发告警或重新生成计划
/// ```
///
/// ## 快照与事件溯源
///
/// 快照模式是事件溯源(Event Sourcing)架构的重要组成部分:
///
/// - **聚合快照**: 定期保存聚合根的完整状态,避免重放全部事件
///   - **配置快照**: 记录外部依赖的历史版本,支持重现历史执行环境
///   - **断点快照**: 捕获任务执行的中间状态,支持断点续传
///
/// ## 性能考虑
///
/// - **序列化开销**: JSON 序列化可能较慢,考虑使用 Protobuf 或 Avro
///   - **存储空间**: 快照包含完整配置,需合理设置保留策略
///   - **哈希计算**: SHA-256 计算成本可接受,避免使用 MD5(不安全)
///
/// ## 最佳实践
///
/// - 快照应包含时间戳,标识捕获时间
///   - 使用强哈希算法(SHA-256)而非弱哈希(MD5)
///   - 快照不可变,避免提供 setter 方法
///   - 反序列化失败应有优雅降级机制
///   - 定期清理过期快照,避免存储膨胀
///
/// @see com.patra.ingest.domain.model.aggregate.PlanAggregate 使用配置快照
/// @see com.patra.ingest.domain.port.PatraRegistryPort 获取实时配置
/// @author linqibin
/// @since 0.1.0
package com.patra.ingest.domain.model.snapshot;
