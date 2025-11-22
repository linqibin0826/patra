/// 出版物事件发布器包。
/// 
/// 本包提供将采集的出版物数据发布到 Outbox 的实现。
/// 
/// ## 职责
/// 
/// - 将 PublicationReadyEvent 发布到 Outbox 表
///   - 构建出版物消息的负载（`PublicationReadyPayload`）和消息头（`PublicationReadyHeaders`）
///   - 定义出版物消息的分区策略和幂等键
/// 
/// ## 核心组件
/// 
/// - `PublicationEventPublisher` - 出版物事件发布器
///       
/// - 继承自 {@link com.patra.ingest.app.outbox.core.AbstractOutboxPublisher}
///         - 发布到 `INGEST_PUBLICATION_READY` 通道
/// 
///   - `PublicationReadyPayload` - 出版物消息负载
///       
/// - `externalId`: 外部 ID（如 PMID、DOI）
///         - `provenanceCode`: 数据源代码
///         - `metadata`: 出版物元数据（JSON）
/// 
///   - `PublicationReadyHeaders` - 出版物消息头
///       
/// - `taskId`: 任务 ID
///         - `batchSeq`: 批次序号
///         - `publishDate`: 发布日期
/// 
/// ## 分区策略
/// 
/// - 按 `externalId` 的哈希值分区
///   - 确保同一出版物的消息路由到同一分区（便于消费者幂等处理）
/// 
/// ## 幂等键策略
/// 
/// - 使用 `provenanceCode + ":" + externalId` 作为幂等键
///   - 防止同一出版物的重复发布
/// 
/// ## 使用示例
/// 
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class PublicationEventPublisher
///     extends AbstractOutboxPublisher<PublicationReadyEvent, PublicationReadyPayload, PublicationReadyHeaders> {
/// 
///     @Override
///     protected OutboxChannels getChannel() {
///         return OutboxChannels.INGEST_PUBLICATION_READY;
/// 
///     @Override
///     protected PublicationReadyPayload buildPayload(PublicationReadyEvent event) {
///         return PublicationReadyPayload.builder()
///             .externalId(event.getExternalId())
///             .provenanceCode(event.getProvenanceCode())
///             .metadata(event.getPublication().toJson())
///             .build();
/// 
///     @Override
///     protected PublicationReadyHeaders buildHeaders(PublicationReadyEvent event) {
///         return PublicationReadyHeaders.builder()
///             .taskId(event.getTaskId())
///             .batchSeq(event.getBatchSeq())
///             .publishDate(event.getPublication().getPublishDate())
///             .build();
/// 
///     @Override
///     protected String buildPartitionKey(PublicationReadyEvent event) {
///         // 按 externalId 的哈希值分区
///         return String.valueOf(event.getExternalId().hashCode());
/// 
///     @Override
///     protected String buildDedupKey(PublicationReadyEvent event) {
///         return event.getProvenanceCode() + ":" + event.getExternalId();
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.execution.publisher;
