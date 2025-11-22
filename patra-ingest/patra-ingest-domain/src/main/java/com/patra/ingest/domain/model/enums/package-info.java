/// Ingest 领域模型 - 枚举包。
/// 
/// 本包包含数据采集领域的所有枚举类型,用于定义状态机、业务代码和分类常量。枚举类型是类型安全的常量集合, 封装了领域特定的业务逻辑和状态转换规则。
/// 
/// ## 状态机枚举
/// 
/// - {@link com.patra.ingest.domain.model.enums.PlanStatus} - 计划状态机
///       
/// - DRAFT(草稿) → SLICING(切片中) → READY(就绪) → COMPLETED/PARTIAL/FAILED
///         - 用于追踪计划从创建到执行完成的完整生命周期
/// 
///   - {@link com.patra.ingest.domain.model.enums.TaskStatus} - 任务状态机
///       
/// - QUEUED(已排队) → RUNNING(运行中) → SUCCEEDED/FAILED/PARTIAL
///         - 支持 isTerminal() 判断是否为终态
/// 
///   - {@link com.patra.ingest.domain.model.enums.TaskRunStatus} - 任务执行状态机
///       
/// - RUNNING(运行中) → SUCCEEDED(成功)/FAILED(失败)/PARTIAL(部分成功)
///         - 用于 TaskRun 实体的执行追踪
/// 
///   - {@link com.patra.ingest.domain.model.enums.SliceStatus} - 切片状态机
///       
/// - PENDING(待处理) → ASSIGNED(已分配) → FINISHED(已完成)
///         - 用于追踪计划切片的分配和执行
/// 
///   - {@link com.patra.ingest.domain.model.enums.OutboxStatus} - Outbox 消息状态机
///       
/// - PENDING(待发布) → PUBLISHED(已发布)/FAILED(失败)/DEFERRED(延迟)
///         - 用于事务性消息发布模式
/// 
///   - {@link com.patra.ingest.domain.model.enums.RelayStatus} - 中继状态机
///       
/// - PENDING(待中继) → SUCCESS(成功)/FAIL(失败)
///         - 用于 Outbox Relay 模式的补偿机制
/// 
///   - {@link com.patra.ingest.domain.model.enums.BatchStatus} - 批次状态机
///       
/// - PROCESSING(处理中) → SUCCESS(成功)/PARTIAL(部分成功)/FAIL(失败)
///         - 用于批量处理的状态追踪
/// 
/// ## 业务代码枚举
/// 
/// - {@link com.patra.ingest.domain.model.enums.OperationCode} - 操作代码
///       
/// - HARVEST(采集), UPDATE(更新), COMPENSATION(补偿)
///         - 标识采集任务的业务语义
/// 
///   - {@link com.patra.ingest.domain.model.enums.Scheduler} - 调度器类型
///       
/// - XXL_JOB, MANUAL, INTERNAL
///         - 标识任务触发来源
/// 
///   - {@link com.patra.ingest.domain.model.enums.TriggerType} - 触发类型
///       
/// - CRON(定时), MANUAL(手动), EVENT(事件驱动)
///         - 标识计划触发方式
/// 
/// ## 策略与分类枚举
/// 
/// - {@link com.patra.ingest.domain.model.enums.SliceStrategy} - 切片策略
///       
/// - SINGLE(单切片), TIME(时间切片), DATE(日期切片), VOLUME(容量切片)
///         - 决定计划如何拆分为可并行执行的切片
/// 
///   - {@link com.patra.ingest.domain.model.enums.CursorType} - 游标类型
///       
/// - FORWARD(前向), BACKWARD(后向)
///         - 标识增量采集的游标方向
/// 
///   - {@link com.patra.ingest.domain.model.enums.CursorDirection} - 游标推进方向
///       
/// - NEXT(向前), PREV(向后)
///         - 控制游标推进的方向
/// 
///   - {@link com.patra.ingest.domain.model.enums.NamespaceScope} - 命名空间范围
///       
/// - GLOBAL(全局), PROVENANCE(数据源), PLAN(计划), TASK(任务)
///         - 定义幂等性键和游标的作用域
/// 
/// ## 枚举设计模式
/// 
/// ### 1. 富枚举模式 (Rich Enum)
/// 
/// 枚举包含业务逻辑方法,而不仅仅是常量:
/// 
/// ```java
/// public enum TaskStatus {
///     QUEUED,
///     RUNNING,
///     SUCCEEDED,
///     FAILED;
/// 
///     // 业务逻辑方法
///     public boolean isTerminal() {
///         return this == SUCCEEDED || this == FAILED;
/// 
///     public boolean canTransitionTo(TaskStatus target) {
///         return switch (this) {
///             case QUEUED -> target == RUNNING;
///             case RUNNING -> target == SUCCEEDED || target == FAILED;
///             default -> false;;
/// ```
/// 
/// ### 2. 状态转换验证
/// 
/// 枚举封装状态机转换规则:
/// 
/// ```java
/// // 在聚合根中使用
/// public void markRunning() {
///     if (!this.status.canTransitionTo(TaskStatus.RUNNING)) {
///         throw new IllegalStateException(
///             "Cannot transition from " + this.status + " to RUNNING"
///         );
///     this.status = TaskStatus.RUNNING;
/// ```
/// 
/// ### 3. 枚举策略模式
/// 
/// 不同枚举值执行不同策略:
/// 
/// ```java
/// public enum SliceStrategy {
///     SINGLE {
///         @Override
///         public List<SliceSpec> generateSlices(WindowSpec window) {
///             return List.of(new SliceSpec(window));,
///     TIME {
///         @Override
///         public List<SliceSpec> generateSlices(WindowSpec window) {
///             // 按时间切片逻辑
///             return ...;;
/// 
///     public abstract List<SliceSpec> generateSlices(WindowSpec window);
/// ```
/// 
/// ## 使用示例
/// 
/// ```java
/// // 状态机验证
/// if (task.getStatus().isTerminal()) {
///     logger.info("Task completed: {", task.getId());
/// 
/// // 状态转换
/// if (plan.getStatus().canTransitionTo(PlanStatus.READY)) {
///     plan.markReady();
/// 
/// // 枚举作为策略
/// SliceStrategy strategy = SliceStrategy.valueOf(config.getStrategy());
/// List<SliceSpec> slices = strategy.generateSlices(window);
/// 
/// // 类型安全的业务代码
/// if (operationCode == OperationCode.HARVEST) {
///     // 执行采集逻辑
/// ```
/// 
/// ## 命名约定
/// 
/// - 枚举类名使用单数名词,如 `TaskStatus` 而不是 `TaskStatuses`
///   - 状态枚举以 `Status` 结尾
///   - 代码枚举以 `Code` 结尾
///   - 策略枚举以 `Strategy` 结尾
///   - 枚举常量使用全大写下划线命名,如 `TASK_READY`
/// 
/// ## 最佳实践
/// 
/// - 枚举优于魔法字符串:使用枚举而不是 `String` 常量
///   - 封装业务逻辑:将状态判断逻辑放在枚举内部
///   - 不可变性:枚举天然不可变,线程安全
///   - 单例保证:JVM 保证枚举实例唯一性,可用 `==` 比较
///   - 序列化安全:枚举序列化自动处理,避免反序列化攻击
/// 
/// @see com.patra.ingest.domain.model.aggregate 聚合根使用枚举定义状态机
/// @author linqibin
/// @since 0.1.0
package com.patra.ingest.domain.model.enums;
