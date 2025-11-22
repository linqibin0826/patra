/// Outbox 指标收集包。
///
/// 本包提供 Outbox 发布和中继的监控指标收集，集成 Micrometer。
///
/// ## 职责
///
/// - 记录 Outbox 消息发布成功/失败数量
///   - 记录 Outbox 中继批次的成功/失败统计
///   - 记录消息发布延迟（从创建到发布的时间）
///   - 记录租约获取和释放的次数
///   - 提供 Prometheus 格式的指标暴露
///
/// ## 核心组件
///
/// - `OutboxMetrics` - Outbox 指标收集器
///
/// - `outbox.publish.success`: 发布成功计数器
///         - `outbox.publish.failure`: 发布失败计数器
///         - `outbox.publish.latency`: 发布延迟分布
///         - `outbox.relay.batch.size`: 中继批次大小分布
///
/// ## 指标清单
///
/// <table border="1">
///   <tr>
///     <th>指标名称</th>
///     <th>类型</th>
///     <th>标签</th>
///     <th>说明</th>
///   </tr>
///   <tr>
///     <td>outbox.publish.success</td>
///     <td>Counter</td>
///     <td>channel, aggregateType</td>
///     <td>Outbox 写入成功数</td>
///   </tr>
///   <tr>
///     <td>outbox.publish.failure</td>
///     <td>Counter</td>
///     <td>channel, aggregateType, errorType</td>
///     <td>Outbox 写入失败数</td>
///   </tr>
///   <tr>
///     <td>outbox.relay.published</td>
///     <td>Counter</td>
///     <td>channel</td>
///     <td>中继发布到 MQ 成功数</td>
///   </tr>
///   <tr>
///     <td>outbox.relay.failed</td>
///     <td>Counter</td>
///     <td>channel, errorType</td>
///     <td>中继发布失败数</td>
///   </tr>
///   <tr>
///     <td>outbox.message.latency</td>
///     <td>Timer</td>
///     <td>channel</td>
///     <td>消息从创建到发布的延迟</td>
///   </tr>
/// </table>
///
/// ## 使用示例
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class OutboxMetrics {
///     private final MeterRegistry meterRegistry;
///
///     public void recordPublishSuccess(String channel, String aggregateType) {
///         Counter.builder("outbox.publish.success")
///             .tag("channel", channel)
///             .tag("aggregateType", aggregateType)
///             .register(meterRegistry)
///             .increment();
///
///     public void recordPublishFailure(String channel, String errorType) {
///         Counter.builder("outbox.publish.failure")
///             .tag("channel", channel)
///             .tag("errorType", errorType)
///             .register(meterRegistry)
///             .increment();
///
///     public void recordMessageLatency(String channel, Duration latency) {
///         Timer.builder("outbox.message.latency")
///             .tag("channel", channel)
///             .register(meterRegistry)
///             .record(latency);
/// ```
///
/// ## 监控示例
///
/// ### Prometheus 查询
///
/// ```
///
/// # Outbox 发布成功率
/// rate(outbox_publish_success_total[5m])
/// / (rate(outbox_publish_success_total[5m]) + rate(outbox_publish_failure_total[5m]))
///
/// # Outbox 消息延迟 P99
/// histogram_quantile(0.99, rate(outbox_message_latency_bucket[5m]))
///
/// # 中继吞吐量（每秒发布消息数）
/// rate(outbox_relay_published_total[5m])
///
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.outbox.metrics;
