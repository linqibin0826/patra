/**
 * 文献事件发布器包。
 *
 * <p>本包提供将采集的文献数据发布到 Outbox 的实现。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>将 LiteratureReadyEvent 发布到 Outbox 表
 *   <li>构建文献消息的负载（{@code LiteratureReadyPayload}）和消息头（{@code LiteratureReadyHeaders}）
 *   <li>定义文献消息的分区策略和幂等键
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code LiteratureEventPublisher} - 文献事件发布器
 *       <ul>
 *         <li>继承自 {@link com.patra.ingest.app.outbox.core.AbstractOutboxPublisher}
 *         <li>发布到 {@code INGEST_LITERATURE_READY} 通道
 *       </ul>
 *   <li>{@code LiteratureReadyPayload} - 文献消息负载
 *       <ul>
 *         <li>{@code externalId}: 外部 ID（如 PMID、DOI）
 *         <li>{@code provenanceCode}: 数据源代码
 *         <li>{@code metadata}: 文献元数据（JSON）
 *       </ul>
 *   <li>{@code LiteratureReadyHeaders} - 文献消息头
 *       <ul>
 *         <li>{@code taskId}: 任务 ID
 *         <li>{@code batchSeq}: 批次序号
 *         <li>{@code publishDate}: 发布日期
 *       </ul>
 * </ul>
 *
 * <h2>分区策略</h2>
 * <ul>
 *   <li>按 {@code externalId} 的哈希值分区
 *   <li>确保同一文献的消息路由到同一分区（便于消费者幂等处理）
 * </ul>
 *
 * <h2>幂等键策略</h2>
 * <ul>
 *   <li>使用 {@code provenanceCode + ":" + externalId} 作为幂等键
 *   <li>防止同一文献的重复发布
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class LiteratureEventPublisher
 *     extends AbstractOutboxPublisher<LiteratureReadyEvent, LiteratureReadyPayload, LiteratureReadyHeaders> {
 *
 *     @Override
 *     protected OutboxChannels getChannel() {
 *         return OutboxChannels.INGEST_LITERATURE_READY;
 *     }
 *
 *     @Override
 *     protected LiteratureReadyPayload buildPayload(LiteratureReadyEvent event) {
 *         return LiteratureReadyPayload.builder()
 *             .externalId(event.getExternalId())
 *             .provenanceCode(event.getProvenanceCode())
 *             .metadata(event.getLiterature().toJson())
 *             .build();
 *     }
 *
 *     @Override
 *     protected LiteratureReadyHeaders buildHeaders(LiteratureReadyEvent event) {
 *         return LiteratureReadyHeaders.builder()
 *             .taskId(event.getTaskId())
 *             .batchSeq(event.getBatchSeq())
 *             .publishDate(event.getLiterature().getPublishDate())
 *             .build();
 *     }
 *
 *     @Override
 *     protected String buildPartitionKey(LiteratureReadyEvent event) {
 *         // 按 externalId 的哈希值分区
 *         return String.valueOf(event.getExternalId().hashCode());
 *     }
 *
 *     @Override
 *     protected String buildDedupKey(LiteratureReadyEvent event) {
 *         return event.getProvenanceCode() + ":" + event.getExternalId();
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.publisher;
