/**
 * Ingest 领域模型 - 实体包。
 *
 * <p>本包包含领域实体(Entities),它们拥有唯一标识和生命周期,但不是聚合根。实体通常作为聚合根的一部分存在, 由聚合根管理其一致性边界。
 *
 * <h2>核心实体</h2>
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.model.entity.OutboxMessage} - Outbox 消息实体
 *       <ul>
 *         <li>实现事务性消息发布模式(Transactional Outbox Pattern)
 *         <li>保证业务操作与消息发送的原子性
 *         <li>状态: PENDING → PUBLISHED/FAILED/DEFERRED
 *         <li>支持租约机制防止并发发布
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.entity.OutboxRelayLog} - Outbox 中继日志实体
 *       <ul>
 *         <li>记录消息中继(Relay)的执行历史
 *         <li>用于补偿机制和监控
 *         <li>关联批次 ID、执行结果和重试次数
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.entity.Cursor} - 游标实体
 *       <ul>
 *         <li>增量采集的断点续传游标
 *         <li>追踪数据源的消费位置(水位线)
 *         <li>支持前向和后向游标
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.entity.CursorEvent} - 游标事件实体
 *       <ul>
 *         <li>记录游标变更的审计日志
 *         <li>支持游标血缘追踪和回溯
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.entity.TaskRun} - 任务执行记录实体
 *       <ul>
 *         <li>任务的单次执行实例
 *         <li>包含执行上下文、统计数据和时间线
 *         <li>支持断点续传和批次追踪
 *       </ul>
 *   <li>{@link com.patra.ingest.domain.model.entity.TaskRunBatch} - 任务批次实体
 *       <ul>
 *         <li>任务执行的批次记录
 *         <li>追踪批次范围、处理结果和性能指标
 *       </ul>
 * </ul>
 *
 * <h2>实体与聚合根的区别</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>特征</th>
 *     <th>聚合根</th>
 *     <th>实体</th>
 *   </tr>
 *   <tr>
 *     <td>一致性边界</td>
 *     <td>定义事务边界</td>
 *     <td>依附于聚合根</td>
 *   </tr>
 *   <tr>
 *     <td>外部访问</td>
 *     <td>可通过仓储直接访问</td>
 *     <td>仅通过聚合根访问</td>
 *   </tr>
 *   <tr>
 *     <td>生命周期</td>
 *     <td>独立管理</td>
 *     <td>由聚合根管理</td>
 *   </tr>
 *   <tr>
 *     <td>领域事件</td>
 *     <td>可发布领域事件</td>
 *     <td>通过聚合根发布</td>
 *   </tr>
 * </table>
 *
 * <h2>实体特征</h2>
 *
 * <ul>
 *   <li><b>唯一标识</b>: 通过 ID 区分,即使属性相同
 *   <li><b>可变性</b>: 实体状态可变,通过业务方法修改
 *   <li><b>相等性</b>: 基于 ID 判断,不是值相等
 *   <li><b>生命周期</b>: 创建、修改、销毁的完整生命周期
 * </ul>
 *
 * <h2>Outbox Pattern 设计</h2>
 *
 * <p>OutboxMessage 实现了事务性消息发布模式,保证业务操作与消息发送的原子性:
 *
 * <pre>{@code
 * // 在同一事务中
 * @Transactional
 * public void handleBusinessLogic() {
 *     // 1. 执行业务逻辑
 *     TaskAggregate task = taskRepository.findById(taskId);
 *     task.markCompleted();
 *     taskRepository.save(task);
 *
 *     // 2. 保存 Outbox 消息
 *     OutboxMessage message = OutboxMessage.create(
 *         "TASK_COMPLETED",
 *         payload,
 *         headers
 *     );
 *     outboxRepository.save(message);
 * }
 *
 * // 后台异步发布
 * @Scheduled
 * public void relayOutboxMessages() {
 *     List<OutboxMessage> pending = outboxRepository.findPending();
 *     for (OutboxMessage msg : pending) {
 *         if (msg.tryAcquireLease()) {
 *             publisher.publish(msg);
 *             msg.markPublished();
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>游标管理设计</h2>
 *
 * <p>Cursor 实体支持增量采集的断点续传:
 *
 * <pre>{@code
 * // 初始化游标
 * Cursor cursor = Cursor.create(
 *     namespaceKey,
 *     CursorType.FORWARD,
 *     initialValue
 * );
 *
 * // 推进游标
 * cursor.advance(newValue, watermark);
 * cursorRepository.save(cursor);
 *
 * // 记录游标事件
 * CursorEvent event = CursorEvent.of(cursor, operation);
 * cursorEventRepository.save(event);
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // TaskRun 实体生命周期
 * TaskRun run = TaskRun.create(taskId, executionContext);
 * run.start(Instant.now());
 * run.processBatch(batch);
 * run.complete(stats, checkpoint);
 *
 * // OutboxMessage 发布流程
 * OutboxMessage msg = OutboxMessage.create(channel, payload, headers);
 * if (msg.tryAcquireLease(workerId)) {
 *     publisher.send(msg);
 *     msg.markPublished();
 * }
 * }</pre>
 *
 * <h2>命名约定</h2>
 *
 * <ul>
 *   <li>实体类名使用名词,清晰表达领域概念
 *   <li>状态字段使用枚举类型,避免魔法字符串
 *   <li>行为方法使用动词,表达业务操作
 * </ul>
 *
 * @see com.patra.ingest.domain.model.aggregate 聚合根定义
 * @see com.patra.ingest.domain.model.vo 值对象定义
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.ingest.domain.model.entity;
