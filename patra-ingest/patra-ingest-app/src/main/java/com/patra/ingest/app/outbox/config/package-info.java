/// Outbox 配置包。
/// 
/// 本包提供 Outbox 发布器和中继任务的 Spring Boot 配置属性。
/// 
/// ## 职责
/// 
/// - 定义 Outbox 发布器配置属性（批大小、超时、启用开关）
///   - 定义 Outbox 中继配置属性（轮询间隔、租约时长、重试策略）
///   - 提供配置默认值和验证规则
/// 
/// ## 核心组件
/// 
/// - `OutboxPublisherProperties` - Outbox 发布器属性
///       
/// - `enabled`: 是否启用 Outbox 发布（默认 true）
///         - `batchSize`: 批量发布大小（默认 50）
///         - `timeout`: 发布超时时间（默认 5s）
/// 
/// ## 配置示例
/// 
/// ```
/// 
/// # application.yml
/// patra:
///   ingest:
///     outbox:
///       publisher:
///         enabled: true
///         batch-size: 50
///         timeout: 5s
/// 
/// ```
/// 
/// ## 使用示例
/// 
/// ```java
/// @Configuration
/// @EnableConfigurationProperties(OutboxPublisherProperties.class)
/// public class OutboxConfig {
/// 
///     @Bean
///     public AbstractOutboxPublisher outboxPublisher(
///         OutboxPublisherProperties properties,
///         OutboxMessageRepository repository
///     ) {
///         return new TaskOutboxPublisher(properties, repository);
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.outbox.config;
