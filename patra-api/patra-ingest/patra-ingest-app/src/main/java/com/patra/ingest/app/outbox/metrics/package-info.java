/**
 * Outbox 指标收集包。
 *
 * <p>本包提供 Outbox 发布和中继的监控指标收集，集成 Micrometer。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>记录 Outbox 消息发布成功/失败数量
 *   <li>记录 Outbox 中继批次的成功/失败统计
 *   <li>记录消息发布延迟（从创建到发布的时间）
 *   <li>记录租约获取和释放的次数
 *   <li>提供 Prometheus 格式的指标暴露
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code OutboxMetrics} - Outbox 指标收集器
 *       <ul>
 *         <li>{@code outbox.publish.success}: 发布成功计数器
 *         <li>{@code outbox.publish.failure}: 发布失败计数器
 *         <li>{@code outbox.publish.latency}: 发布延迟分布
 *         <li>{@code outbox.relay.batch.size}: 中继批次大小分布
 *       </ul>
 * </ul>
 *
 * <h2>指标清单</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>指标名称</th>
 *     <th>类型</th>
 *     <th>标签</th>
 *     <th>说明</th>
 *   </tr>
 *   <tr>
 *     <td>outbox.publish.success</td>
 *     <td>Counter</td>
 *     <td>channel, aggregateType</td>
 *     <td>Outbox 写入成功数</td>
 *   </tr>
 *   <tr>
 *     <td>outbox.publish.failure</td>
 *     <td>Counter</td>
 *     <td>channel, aggregateType, errorType</td>
 *     <td>Outbox 写入失败数</td>
 *   </tr>
 *   <tr>
 *     <td>outbox.relay.published</td>
 *     <td>Counter</td>
 *     <td>channel</td>
 *     <td>中继发布到 MQ 成功数</td>
 *   </tr>
 *   <tr>
 *     <td>outbox.relay.failed</td>
 *     <td>Counter</td>
 *     <td>channel, errorType</td>
 *     <td>中继发布失败数</td>
 *   </tr>
 *   <tr>
 *     <td>outbox.message.latency</td>
 *     <td>Timer</td>
 *     <td>channel</td>
 *     <td>消息从创建到发布的延迟</td>
 *   </tr>
 * </table>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class OutboxMetrics {
 *     private final MeterRegistry meterRegistry;
 *
 *     public void recordPublishSuccess(String channel, String aggregateType) {
 *         Counter.builder("outbox.publish.success")
 *             .tag("channel", channel)
 *             .tag("aggregateType", aggregateType)
 *             .register(meterRegistry)
 *             .increment();
 *     }
 *
 *     public void recordPublishFailure(String channel, String errorType) {
 *         Counter.builder("outbox.publish.failure")
 *             .tag("channel", channel)
 *             .tag("errorType", errorType)
 *             .register(meterRegistry)
 *             .increment();
 *     }
 *
 *     public void recordMessageLatency(String channel, Duration latency) {
 *         Timer.builder("outbox.message.latency")
 *             .tag("channel", channel)
 *             .register(meterRegistry)
 *             .record(latency);
 *     }
 * }
 * }</pre>
 *
 * <h2>监控示例</h2>
 *
 * <h3>Prometheus 查询</h3>
 *
 * <pre>
 * # Outbox 发布成功率
 * rate(outbox_publish_success_total[5m])
 * / (rate(outbox_publish_success_total[5m]) + rate(outbox_publish_failure_total[5m]))
 *
 * # Outbox 消息延迟 P99
 * histogram_quantile(0.99, rate(outbox_message_latency_bucket[5m]))
 *
 * # 中继吞吐量（每秒发布消息数）
 * rate(outbox_relay_published_total[5m])
 * </pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.outbox.metrics;
