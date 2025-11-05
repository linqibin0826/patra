/**
 * Ingest 领域模型 - 聚合根包。
 *
 * <p>本包包含数据采集领域的所有聚合根(Aggregate Roots),每个聚合根代表一个事务一致性边界和状态机流转单元。
 * 聚合根是 DDD 战术设计中的核心概念,用于维护领域不变性和封装业务规则。
 *
 * <h2>核心聚合</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.aggregate.PlanAggregate} - 采集计划蓝图聚合根
 *       <ul>
 *         <li>封装数据采集计划的窗口规范、切片策略和配置快照</li>
 *         <li>状态机: DRAFT → SLICING → READY → COMPLETED/PARTIAL/FAILED</li>
 *         <li>通过 planKey 哈希保证幂等性</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.ingest.domain.model.aggregate.TaskAggregate} - 任务执行聚合根
 *       <ul>
 *         <li>原子工作单元,包含租约管理、执行追踪和断点续传</li>
 *         <li>状态机: QUEUED → RUNNING → SUCCEEDED/FAILED/PARTIAL</li>
 *         <li>支持技术重试和补偿机制</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.ingest.domain.model.aggregate.PlanSliceAggregate} - 计划切片聚合根
 *       <ul>
 *         <li>计划的分片单元,将大窗口拆分为可并行执行的切片</li>
 *         <li>状态机: PENDING → ASSIGNED → FINISHED</li>
 *         <li>追踪切片级别的执行进度</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate} - 调度实例聚合根
 *       <ul>
 *         <li>追踪外部调度器(XXL-Job)触发的单次执行</li>
 *         <li>关联 trigger 参数、执行结果和耗时</li>
 *         <li>支持调度器级别的幂等性</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>聚合根设计原则</h2>
 *
 * <ul>
 *   <li><b>一致性边界</b>: 聚合内部保证事务一致性,聚合之间最终一致</li>
 *   <li><b>封装状态</b>: 所有状态变更通过行为方法,禁止 public setter</li>
 *   <li><b>ID 引用</b>: 聚合之间仅通过 ID 引用,不持有对象引用</li>
 *   <li><b>领域事件</b>: 状态转换时发布领域事件,支持异步解耦</li>
 *   <li><b>乐观锁</b>: 继承 {@code AggregateRoot} 的版本控制机制</li>
 * </ul>
 *
 * <h2>状态机模式</h2>
 *
 * <p>所有聚合根通过枚举定义有限状态机:
 *
 * <pre>{@code
 * // 示例: TaskAggregate 状态转换
 * TaskAggregate task = TaskAggregate.create(...);
 * task.markRunning(Instant.now());          // QUEUED → RUNNING
 * task.markSucceeded(stats, checkpoint);     // RUNNING → SUCCEEDED
 *
 * // 状态转换会自动验证前置条件
 * if (!task.canTransitionTo(TaskStatus.RUNNING)) {
 *     throw new IllegalStateException("Invalid state transition");
 * }
 * }</pre>
 *
 * <h2>工厂方法模式</h2>
 *
 * <p>聚合根提供两种工厂方法:
 *
 * <ul>
 *   <li>{@code create(...)}: 创建全新聚合根,分配初始状态和 ID</li>
 *   <li>{@code restore(...)}: 从持久化存储恢复聚合根,携带版本号</li>
 * </ul>
 *
 * <pre>{@code
 * // 创建新聚合
 * PlanAggregate newPlan = PlanAggregate.create(metadata, window, ...);
 *
 * // 从 DB 恢复
 * PlanAggregate restored = PlanAggregate.restore(id, metadata, ..., version);
 * }</pre>
 *
 * <h2>领域事件发布</h2>
 *
 * <p>聚合根通过 {@code addDomainEvent()} 发布事件,由应用层统一拉取和发布:
 *
 * <pre>{@code
 * // 聚合根内部
 * public void markReady() {
 *     this.status = PlanStatus.READY;
 *     addDomainEvent(new PlanReadyEvent(getId()));
 * }
 *
 * // 应用层
 * List<DomainEvent> events = plan.pullDomainEvents();
 * eventBus.publish(events);
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 完整的计划生命周期
 * PlanAggregate plan = PlanAggregate.create(metadata, window, sliceStrategy);
 * planRepository.save(plan);  // DRAFT 状态
 *
 * plan.startSlicing();         // DRAFT → SLICING
 * planRepository.save(plan);
 *
 * // ... 切片生成完成 ...
 * plan.markReady();            // SLICING → READY
 * planRepository.save(plan);
 *
 * // ... 任务执行完成后 ...
 * plan.markCompleted();        // READY → COMPLETED
 * planRepository.save(plan);
 * }</pre>
 *
 * <h2>线程安全</h2>
 *
 * <p>聚合根不是线程安全的,应在单线程中操作。并发控制通过乐观锁版本号实现,
 * 由基础设施层的仓储在保存时检查版本冲突。
 *
 * @see com.patra.common.domain.AggregateRoot
 * @see com.patra.ingest.domain.model.enums 状态枚举定义
 * @see com.patra.ingest.domain.event 领域事件定义
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.domain.model.aggregate;
