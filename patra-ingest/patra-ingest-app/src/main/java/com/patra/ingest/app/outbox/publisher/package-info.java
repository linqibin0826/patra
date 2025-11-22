/// Outbox 发布器实现包。
/// 
/// 本包提供具体的 Outbox 发布器实现，继承自 {@link com.patra.ingest.app.outbox.core.AbstractOutboxPublisher}。
/// 
/// ## 职责
/// 
/// - 实现特定领域事件的 Outbox 发布逻辑
///   - 定义消息负载和消息头的构建规则
///   - 定义分区键和幂等键的生成策略
///   - 配置消息通道和聚合类型
/// 
/// ## 核心组件
/// 
/// - `TaskOutboxPublisher` - 任务事件发布器
///       
/// - 处理 `TaskQueuedEvent`
///         - 发布到 `INGEST_TASK_READY` 通道
///         - 用于触发任务执行流程
/// 
///   - `PublicationEventPublisher` - 出版物事件发布器
///       
/// - 处理 `PublicationDataReadyEvent`
///         - 发布到 `PUBLICATION_DATA_READY` 通道
///         - 用于通知出版物数据已就绪
/// 
///   - `RelayEventPublisher` - 中继事件发布器
///       
/// - 处理 Outbox 中继事件
///         - 用于发布消息到 RocketMQ
/// 
/// ## 发布器清单
/// 
/// <table border="1">
///   <tr>
///     <th>发布器</th>
///     <th>领域事件</th>
///     <th>目标通道</th>
///     <th>消费者</th>
///   </tr>
///   <tr>
///     <td>TaskOutboxPublisher</td>
///     <td>TaskQueuedEvent</td>
///     <td>INGEST_TASK_READY</td>
///     <td>TaskReadyMessageListener</td>
///   </tr>
///   <tr>
///     <td>PublicationEventPublisher</td>
///     <td>PublicationDataReadyEvent</td>
///     <td>PUBLICATION_DATA_READY</td>
///     <td>PublicationReadyMessageListener</td>
///   </tr>
///   <tr>
///     <td>RelayEventPublisher</td>
///     <td>OutboxMessage</td>
///     <td>动态（根据消息通道）</td>
///     <td>各消费者</td>
///   </tr>
/// </table>
/// 
/// ## 使用示例
/// 
/// ### 定义发布器
/// 
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class PublicationEventPublisher
///     extends AbstractOutboxPublisher<PublicationDataReadyEvent, PublicationReadyPayload, PublicationReadyHeaders> {
/// 
///     private final ObjectMapper objectMapper;
/// 
///     @Override
///     protected OutboxAggregateTypes getAggregateType() {
///         return OutboxAggregateTypes.TASK;
/// 
///     @Override
///     protected OutboxChannels getChannel() {
///         return OutboxChannels.PUBLICATION_DATA_READY;
/// 
///     @Override
///     protected PublicationReadyPayload buildPayload(PublicationDataReadyEvent event) {
///         return PublicationReadyPayload.builder()
///             .taskId(event.getTaskId())
///             .runId(event.getRunId())
///             .storageKeys(event.getStorageKeys())
///             .totalCount(event.getTotalPublicationCount())
///             .build();
/// 
///     @Override
///     protected String buildPartitionKey(PublicationDataReadyEvent event) {
///         // 按 provenanceCode 分区，确保同一数据源的消息顺序消费
///         return event.getProvenanceCode();
/// 
///     @Override
///     protected String buildDedupKey(PublicationDataReadyEvent event) {
///         // taskId + runId 保证幂等性
///         return String.format("task:%d:run:%d:publication", event.getTaskId(), event.getRunId());
/// ```
/// 
/// ### 在编排器中使用
/// 
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// @Transactional
/// public class PublicationPublisherOrchestrator {
///     private final PublicationEventPublisher publicationPublisher;
/// 
///     public void publishPublicationData(Long taskId, Long runId, List<String> storageKeys) {
///         // 1. 构建领域事件
///         var event = new PublicationDataReadyEvent(
///             taskId,
///             runId,
///             "pubmed",
///             storageKeys,
///             storageKeys.size(),
///             Instant.now()
///         );
/// 
///         // 2. 发布到 Outbox（同一事务内）
///         publicationPublisher.publish(event);
/// 
///         // 3. 事务提交后，Outbox 中继会自动发布到 MQ
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.outbox.publisher;
