/// 中继事件发布器包。
/// 
/// 本包提供中继完成事件的发布，用于监控和审计。
/// 
/// ## 职责
/// 
/// - 发布中继完成事件（RelayCompletedEvent）
///   - 记录中继执行结果（用于审计）
///   - 支持不同的发布器实现（如日志发布器、MQ 发布器）
/// 
/// ## 核心组件
/// 
/// - `RelayEventPublisher` - 中继事件发布器接口
///   - `LoggingRelayEventPublisher` - 日志发布器实现（测试用）
///       
/// - 将中继结果记录到日志（不发布到 MQ）
/// 
/// ## 使用示例
/// 
/// ### 日志发布器
/// 
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class LoggingRelayEventPublisher implements RelayEventPublisher {
/// 
///     @Override
///     public void publish(List<RelayCompletedEvent> events) {
///         events.forEach(event -> {
///             log.info("Relay completed event: channel={, published={, failed={",
///                 event.getChannel(),
///                 event.getPublishedCount(),
///                 event.getFailedCount()
///             ););
/// ```
/// 
/// ### MQ 发布器（生产环境）
/// 
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class RocketMQRelayEventPublisher implements RelayEventPublisher {
///     private final RocketMQTemplate rocketMQTemplate;
/// 
///     @Override
///     public void publish(List<RelayCompletedEvent> events) {
///         events.forEach(event -> {
///             rocketMQTemplate.send("relay-audit", event););
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.relay.publisher;
