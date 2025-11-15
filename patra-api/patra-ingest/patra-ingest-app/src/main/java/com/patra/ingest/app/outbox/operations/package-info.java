/**
 * Outbox 操作类型枚举包。
 *
 * <p><b>职责</b>：
 *
 * <ul>
 *   <li>定义各业务领域的操作类型枚举（实现 {@link com.patra.ingest.domain.messaging.OperationType}）
 *   <li>提供业务语义标签，描述对资源的具体操作（如 READY、FAILED、COMPLETED）
 *   <li>配合 {@link com.patra.ingest.domain.messaging.IngestPublishingChannels} 实现 Channel + OpType
 *       模式
 * </ul>
 *
 * <p><b>设计理念</b>：
 *
 * <ul>
 *   <li><b>粗粒度 Channel</b>：一个资源一个 Topic（如 INGEST_TASK）
 *   <li><b>细粒度 OpType</b>：用 RocketMQ Tags 区分不同操作（如 READY、FAILED）
 *   <li><b>面向业务</b>：操作类型描述业务语义，而非技术 CRUD
 * </ul>
 *
 * <p><b>包含的枚举</b>：
 *
 * <ul>
 *   <li>{@link com.patra.ingest.app.outbox.operations.TaskOperations} -
 *       任务操作类型（READY、FAILED、COMPLETED）
 *   <li>{@link com.patra.ingest.app.outbox.operations.LiteratureOperations} - 文献操作类型（DATA_READY）
 * </ul>
 *
 * <p><b>使用示例</b>：
 *
 * <pre>{@code
 * // 在 Publisher 中指定操作类型
 * @Override
 * protected OperationType getOperationType(TaskQueuedEvent event) {
 *   return TaskOperations.READY;
 * }
 *
 * // 最终 RocketMQ 消息
 * // Topic: INGEST_TASK (来自 IngestPublishingChannels.TASK)
 * // Tags: READY (来自 TaskOperations.READY)
 * // Destination: "INGEST_TASK:READY"
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
package com.patra.ingest.app.outbox.operations;
