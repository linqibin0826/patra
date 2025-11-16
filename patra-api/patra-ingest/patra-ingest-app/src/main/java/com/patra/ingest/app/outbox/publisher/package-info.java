/**
 * Outbox 发布器实现包。
 *
 * <p>本包提供具体的 Outbox 发布器实现，继承自 {@link com.patra.ingest.app.outbox.core.AbstractOutboxPublisher}。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>实现特定领域事件的 Outbox 发布逻辑
 *   <li>定义消息负载和消息头的构建规则
 *   <li>定义分区键和幂等键的生成策略
 *   <li>配置消息通道和聚合类型
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code TaskOutboxPublisher} - 任务事件发布器
 *       <ul>
 *         <li>处理 {@code TaskQueuedEvent}
 *         <li>发布到 {@code INGEST_TASK_READY} 通道
 *         <li>用于触发任务执行流程
 *       </ul>
 *   <li>{@code PublicationEventPublisher} - 出版物事件发布器
 *       <ul>
 *         <li>处理 {@code PublicationDataReadyEvent}
 *         <li>发布到 {@code PUBLICATION_DATA_READY} 通道
 *         <li>用于通知出版物数据已就绪
 *       </ul>
 *   <li>{@code RelayEventPublisher} - 中继事件发布器
 *       <ul>
 *         <li>处理 Outbox 中继事件
 *         <li>用于发布消息到 RocketMQ
 *       </ul>
 * </ul>
 *
 * <h2>发布器清单</h2>
 *
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
 *     <td>PublicationEventPublisher</td>
 *     <td>PublicationDataReadyEvent</td>
 *     <td>PUBLICATION_DATA_READY</td>
 *     <td>PublicationReadyMessageListener</td>
 *   </tr>
 *   <tr>
 *     <td>RelayEventPublisher</td>
 *     <td>OutboxMessage</td>
 *     <td>动态（根据消息通道）</td>
 *     <td>各消费者</td>
 *   </tr>
 * </table>
 *
 * <h2>使用示例</h2>
 *
 * <h3>定义发布器</h3>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class PublicationEventPublisher
 *     extends AbstractOutboxPublisher<PublicationDataReadyEvent, PublicationReadyPayload, PublicationReadyHeaders> {
 *
 *     private final ObjectMapper objectMapper;
 *
 *     @Override
 *     protected OutboxAggregateTypes getAggregateType() {
 *         return OutboxAggregateTypes.TASK;
 *     }
 *
 *     @Override
 *     protected OutboxChannels getChannel() {
 *         return OutboxChannels.PUBLICATION_DATA_READY;
 *     }
 *
 *     @Override
 *     protected PublicationReadyPayload buildPayload(PublicationDataReadyEvent event) {
 *         return PublicationReadyPayload.builder()
 *             .taskId(event.getTaskId())
 *             .runId(event.getRunId())
 *             .storageKeys(event.getStorageKeys())
 *             .totalCount(event.getTotalLiteratureCount())
 *             .build();
 *     }
 *
 *     @Override
 *     protected String buildPartitionKey(PublicationDataReadyEvent event) {
 *         // 按 provenanceCode 分区，确保同一数据源的消息顺序消费
 *         return event.getProvenanceCode();
 *     }
 *
 *     @Override
 *     protected String buildDedupKey(PublicationDataReadyEvent event) {
 *         // taskId + runId 保证幂等性
 *         return String.format("task:%d:run:%d:publication", event.getTaskId(), event.getRunId());
 *     }
 * }
 * }</pre>
 *
 * <h3>在编排器中使用</h3>
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * @Transactional
 * public class PublicationPublisherOrchestrator {
 *     private final PublicationEventPublisher literaturePublisher;
 *
 *     public void publishPublicationData(Long taskId, Long runId, List<String> storageKeys) {
 *         // 1. 构建领域事件
 *         var event = new PublicationDataReadyEvent(
 *             taskId,
 *             runId,
 *             "pubmed",
 *             storageKeys,
 *             storageKeys.size(),
 *             Instant.now()
 *         );
 *
 *         // 2. 发布到 Outbox（同一事务内）
 *         literaturePublisher.publish(event);
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
