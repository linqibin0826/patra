/// 中继指标包。
///
/// 本包提供 Outbox 中继的监控指标收集，集成 Micrometer。
///
/// ## 职责
///
/// - 记录中继批次的成功/失败统计
///   - 记录消息发布延迟（从创建到发布的时间）
///   - 记录租约获取成功/失败次数
///   - 提供 Prometheus 格式的指标暴露
///
/// ## 核心组件
///
/// - `OutboxRelayMetrics` - Outbox 中继指标收集器
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
///     <td>outbox.relay.published</td>
///     <td>Counter</td>
///     <td>channel</td>
///     <td>成功发布的消息数</td>
///   </tr>
///   <tr>
///     <td>outbox.relay.failed</td>
///     <td>Counter</td>
///     <td>channel, errorType</td>
///     <td>发布失败的消息数</td>
///   </tr>
///   <tr>
///     <td>outbox.relay.lease_lost</td>
///     <td>Counter</td>
///     <td>channel</td>
///     <td>租约丢失的消息数</td>
///   </tr>
///   <tr>
///     <td>outbox.relay.duration</td>
///     <td>Timer</td>
///     <td>channel</td>
///     <td>中继批次的执行时长</td>
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
/// public class OutboxRelayMetrics {
///     private final MeterRegistry meterRegistry;
///
///     public void recordPublished(String channel, int count) {
///         Counter.builder("outbox.relay.published")
///             .tag("channel", channel)
///             .register(meterRegistry)
///             .increment(count);
///
///     public void recordFailed(String channel, String errorType, int count) {
///         Counter.builder("outbox.relay.failed")
///             .tag("channel", channel)
///             .tag("errorType", errorType)
///             .register(meterRegistry)
///             .increment(count);
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
/// # 中继吞吐量（每秒发布消息数）
/// rate(outbox_relay_published_total[5m])
///
/// # 中继失败率
/// rate(outbox_relay_failed_total[5m])
/// / (rate(outbox_relay_published_total[5m]) + rate(outbox_relay_failed_total[5m]))
///
/// # 消息延迟 P99
/// histogram_quantile(0.99, rate(outbox_message_latency_bucket[5m]))
///
/// # 租约丢失率
/// rate(outbox_relay_lease_lost_total[5m])
///
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.relay.metrics;
