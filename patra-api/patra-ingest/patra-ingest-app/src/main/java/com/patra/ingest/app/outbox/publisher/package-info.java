/**
 * Outbox 发布器实现包。
 *
 * <p>本包提供具体的 Outbox 发布器实现，继承自 {@link com.patra.ingest.app.outbox.core.AbstractOutboxPublisher}。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>实现特定领域事件的 Outbox 发布逻辑
 *   <li>定义消息负载和消息头的构建规则
 *   <li>定义分区键和幂等键的生成策略
 *   <li>配置消息通道和聚合类型
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code MetadataRecordRetryPublisher} - 元数据重试事件发布器
 *       <ul>
 *         <li>处理 {@code MetadataRecordRetryEvent}
 *         <li>发布到 {@code METADATA_RECORD_RETRY} 通道
 *         <li>用于重试失败的元数据记录采集
 *       </ul>
 * </ul>
 *
 * <h2>发布器清单</h2>
 * <table border="1">
 *   <tr>
 *     <th>发布器</th>
 *     <th>领域事件</th>
 *     <th>目标通道</th>
 *     <th>消费者</th>
 *   </tr>
 *   <tr>
 *     <td>TaskOutboxPublisher</td>
 *     <td>TaskQueuedEvent</td>
 *     <td>INGEST_TASK_READY</td>
 *     <td>TaskReadyMessageListener</td>
 *   </tr>
 *   <tr>
 *     <td>MetadataRecordRetryPublisher</td>
 *     <td>MetadataRecordRetryEvent</td>
 *     <td>METADATA_RECORD_RETRY</td>
 *     <td>MetadataRetryListener</td>
 *   </tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <h3>定义发布器</h3>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class MetadataRecordRetryPublisher
 *     extends AbstractOutboxPublisher<MetadataRecordRetryEvent, RetryPayload, RetryHeaders> {
 *
 *     private final ObjectMapper objectMapper;
 *
 *     @Override
 *     protected OutboxAggregateTypes getAggregateType() {
 *         return OutboxAggregateTypes.METADATA_RECORD;
 *     }
 *
 *     @Override
 *     protected OutboxChannels getChannel() {
 *         return OutboxChannels.METADATA_RECORD_RETRY;
 *     }
 *
 *     @Override
 *     protected RetryPayload buildPayload(MetadataRecordRetryEvent event) {
 *         return RetryPayload.builder()
 *             .recordId(event.getRecordId())
 *             .failureReason(event.getFailureReason())
 *             .retryCount(event.getRetryCount())
 *             .build();
 *     }
 *
 *     @Override
 *     protected String buildPartitionKey(MetadataRecordRetryEvent event) {
 *         // 按 recordId 分区，确保同一记录的重试消息顺序消费
 *         return String.valueOf(event.getRecordId());
 *     }
 *
 *     @Override
 *     protected String buildDedupKey(MetadataRecordRetryEvent event) {
 *         // recordId + retryCount 保证幂等性
 *         return event.getRecordId() + ":" + event.getRetryCount();
 *     }
 * }
 * }</pre>
 *
 * <h3>在编排器中使用</h3>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * @Transactional
 * public class MetadataFailureHandler {
 *     private final MetadataRecordRetryPublisher retryPublisher;
 *
 *     public void handleFailure(Long recordId, String reason) {
 *         // 1. 标记记录为失败状态
 *         markRecordAsFailed(recordId, reason);
 *
 *         // 2. 发布重试事件到 Outbox
 *         var event = new MetadataRecordRetryEvent(recordId, reason, 1);
 *         retryPublisher.publish(List.of(event));
 *
 *         // 3. 事务提交后，Outbox 中继会自动发布到 MQ
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.outbox.publisher;
