/**
 * 中继事件发布器包。
 *
 * <p>本包提供中继完成事件的发布，用于监控和审计。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>发布中继完成事件（RelayCompletedEvent）
 *   <li>记录中继执行结果（用于审计）
 *   <li>支持不同的发布器实现（如日志发布器、MQ 发布器）
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code RelayEventPublisher} - 中继事件发布器接口
 *   <li>{@code LoggingRelayEventPublisher} - 日志发布器实现（测试用）
 *       <ul>
 *         <li>将中继结果记录到日志（不发布到 MQ）
 *       </ul>
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>日志发布器</h3>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class LoggingRelayEventPublisher implements RelayEventPublisher {
 *
 *     @Override
 *     public void publish(List<RelayCompletedEvent> events) {
 *         events.forEach(event -> {
 *             log.info("Relay completed event: channel={}, published={}, failed={}",
 *                 event.getChannel(),
 *                 event.getPublishedCount(),
 *                 event.getFailedCount()
 *             );
 *         });
 *     }
 * }
 * }</pre>
 *
 * <h3>MQ 发布器（生产环境）</h3>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class RocketMQRelayEventPublisher implements RelayEventPublisher {
 *     private final RocketMQTemplate rocketMQTemplate;
 *
 *     @Override
 *     public void publish(List<RelayCompletedEvent> events) {
 *         events.forEach(event -> {
 *             rocketMQTemplate.send("relay-audit", event);
 *         });
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.relay.publisher;
