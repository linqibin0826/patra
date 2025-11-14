/**
 * Ingest 领域模型 - 枚举包。
 *
 * <p>本包包含数据采集领域的所有枚举类型,用于定义状态机、业务代码和分类常量。枚举类型是类型安全的常量集合, 封装了领域特定的业务逻辑和状态转换规则。
 *
 * <h2>状态机枚举</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.PlanStatus} - 计划状态机
 *       <ul>
 *         <li>DRAFT(草稿) → SLICING(切片中) → READY(就绪) → COMPLETED/PARTIAL/FAILED
 *         <li>用于追踪计划从创建到执行完成的完整生命周期
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.TaskStatus} - 任务状态机
 *       <ul>
 *         <li>QUEUED(已排队) → RUNNING(运行中) → SUCCEEDED/FAILED/PARTIAL
 *         <li>支持 isTerminal() 判断是否为终态
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.TaskRunStatus} - 任务执行状态机
 *       <ul>
 *         <li>RUNNING(运行中) → SUCCEEDED(成功)/FAILED(失败)/PARTIAL(部分成功)
 *         <li>用于 TaskRun 实体的执行追踪
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.SliceStatus} - 切片状态机
 *       <ul>
 *         <li>PENDING(待处理) → ASSIGNED(已分配) → FINISHED(已完成)
 *         <li>用于追踪计划切片的分配和执行
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.OutboxStatus} - Outbox 消息状态机
 *       <ul>
 *         <li>PENDING(待发布) → PUBLISHED(已发布)/FAILED(失败)/DEFERRED(延迟)
 *         <li>用于事务性消息发布模式
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.RelayStatus} - 中继状态机
 *       <ul>
 *         <li>PENDING(待中继) → SUCCESS(成功)/FAIL(失败)
 *         <li>用于 Outbox Relay 模式的补偿机制
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.BatchStatus} - 批次状态机
 *       <ul>
 *         <li>PROCESSING(处理中) → SUCCESS(成功)/PARTIAL(部分成功)/FAIL(失败)
 *         <li>用于批量处理的状态追踪
 *       </ul>
 * </ul>
 *
 * <h2>业务代码枚举</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.OperationCode} - 操作代码
 *       <ul>
 *         <li>HARVEST(采集), UPDATE(更新), COMPENSATION(补偿)
 *         <li>标识采集任务的业务语义
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.Scheduler} - 调度器类型
 *       <ul>
 *         <li>XXL_JOB, MANUAL, INTERNAL
 *         <li>标识任务触发来源
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.TriggerType} - 触发类型
 *       <ul>
 *         <li>CRON(定时), MANUAL(手动), EVENT(事件驱动)
 *         <li>标识计划触发方式
 *       </ul>
 * </ul>
 *
 * <h2>策略与分类枚举</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.SliceStrategy} - 切片策略
 *       <ul>
 *         <li>SINGLE(单切片), TIME(时间切片), DATE(日期切片), VOLUME(容量切片)
 *         <li>决定计划如何拆分为可并行执行的切片
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.CursorType} - 游标类型
 *       <ul>
 *         <li>FORWARD(前向), BACKWARD(后向)
 *         <li>标识增量采集的游标方向
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.CursorDirection} - 游标推进方向
 *       <ul>
 *         <li>NEXT(向前), PREV(向后)
 *         <li>控制游标推进的方向
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.enums.NamespaceScope} - 命名空间范围
 *       <ul>
 *         <li>GLOBAL(全局), PROVENANCE(数据源), PLAN(计划), TASK(任务)
 *         <li>定义幂等性键和游标的作用域
 *       </ul>
 * </ul>
 *
 * <h2>枚举设计模式</h2>
 *
 * <h3>1. 富枚举模式 (Rich Enum)</h3>
 *
 * <p>枚举包含业务逻辑方法,而不仅仅是常量:
 *
 * <pre>{@code
 * public enum TaskStatus {
 *     QUEUED,
 *     RUNNING,
 *     SUCCEEDED,
 *     FAILED;
 *
 *     // 业务逻辑方法
 *     public boolean isTerminal() {
 *         return this == SUCCEEDED || this == FAILED;
 *     }
 *
 *     public boolean canTransitionTo(TaskStatus target) {
 *         return switch (this) {
 *             case QUEUED -> target == RUNNING;
 *             case RUNNING -> target == SUCCEEDED || target == FAILED;
 *             default -> false;
 *         };
 *     }
 * }
 * }</pre>
 *
 * <h3>2. 状态转换验证</h3>
 *
 * <p>枚举封装状态机转换规则:
 *
 * <pre>{@code
 * // 在聚合根中使用
 * public void markRunning() {
 *     if (!this.status.canTransitionTo(TaskStatus.RUNNING)) {
 *         throw new IllegalStateException(
 *             "Cannot transition from " + this.status + " to RUNNING"
 *         );
 *     }
 *     this.status = TaskStatus.RUNNING;
 * }
 * }</pre>
 *
 * <h3>3. 枚举策略模式</h3>
 *
 * <p>不同枚举值执行不同策略:
 *
 * <pre>{@code
 * public enum SliceStrategy {
 *     SINGLE {
 *         @Override
 *         public List<SliceSpec> generateSlices(WindowSpec window) {
 *             return List.of(new SliceSpec(window));
 *         }
 *     },
 *     TIME {
 *         @Override
 *         public List<SliceSpec> generateSlices(WindowSpec window) {
 *             // 按时间切片逻辑
 *             return ...;
 *         }
 *     };
 *
 *     public abstract List<SliceSpec> generateSlices(WindowSpec window);
 * }
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 状态机验证
 * if (task.getStatus().isTerminal()) {
 *     logger.info("Task completed: {}", task.getId());
 * }
 *
 * // 状态转换
 * if (plan.getStatus().canTransitionTo(PlanStatus.READY)) {
 *     plan.markReady();
 * }
 *
 * // 枚举作为策略
 * SliceStrategy strategy = SliceStrategy.valueOf(config.getStrategy());
 * List<SliceSpec> slices = strategy.generateSlices(window);
 *
 * // 类型安全的业务代码
 * if (operationCode == OperationCode.HARVEST) {
 *     // 执行采集逻辑
 * }
 * }</pre>
 *
 * <h2>命名约定</h2>
 *
 * <ul>
 *   <li>枚举类名使用单数名词,如 {@code TaskStatus} 而不是 {@code TaskStatuses}
 *   <li>状态枚举以 {@code Status} 结尾
 *   <li>代码枚举以 {@code Code} 结尾
 *   <li>策略枚举以 {@code Strategy} 结尾
 *   <li>枚举常量使用全大写下划线命名,如 {@code TASK_READY}
 * </ul>
 *
 * <h2>最佳实践</h2>
 *
 * <ul>
 *   <li>枚举优于魔法字符串:使用枚举而不是 {@code String} 常量
 *   <li>封装业务逻辑:将状态判断逻辑放在枚举内部
 *   <li>不可变性:枚举天然不可变,线程安全
 *   <li>单例保证:JVM 保证枚举实例唯一性,可用 {@code ==} 比较
 *   <li>序列化安全:枚举序列化自动处理,避免反序列化攻击
 * </ul>
 *
 * @see com.patra.ingest.domain.model.aggregate 聚合根使用枚举定义状态机
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.domain.model.enums;
