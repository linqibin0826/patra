/// 中继配置包。
/// 
/// 本包提供 Outbox 中继的 Spring Boot 配置属性。
/// 
/// ## 职责
/// 
/// - 定义中继配置属性（批大小、租约时长、轮询间隔）
///   - 提供配置默认值和验证规则
///   - 支持通过配置文件动态调整中继参数
/// 
/// ## 核心组件
/// 
/// - `OutboxRelayProperties` - Outbox 中继属性
///       
/// - `enabled`: 是否启用中继（默认 true）
///         - `batchSize`: 批次大小（默认 100）
///         - `leaseDuration`: 租约时长（默认 5 分钟）
///         - `pollingInterval`: 轮询间隔（默认 10 秒）
///         - `retryMaxAttempts`: 最大重试次数（默认 3）
///         - `retryBackoff`: 重试退避时间（默认 2 秒）
/// 
///   - `OutboxRelayConfiguration` - Outbox 中继配置类
/// 
/// ## 配置示例
/// 
/// ```
/// 
/// # application.yml
/// patra:
///   ingest:
///     outbox:
///       relay:
///         enabled: true
///         batch-size: 100
///         lease-duration: 5m
///         polling-interval: 10s
///         retry-max-attempts: 3
///         retry-backoff: 2s
/// 
/// ```
/// 
/// ## 使用示例
/// 
/// ```java
/// @Configuration
/// @EnableConfigurationProperties(OutboxRelayProperties.class)
/// public class OutboxRelayConfiguration {
/// 
///     @Bean
///     @ConditionalOnProperty(prefix = "patra.ingest.outbox.relay", name = "enabled", havingValue = "true")
///     public OutboxRelayScheduler outboxRelayScheduler(
///         OutboxRelayUseCase relayUseCase,
///         OutboxRelayProperties properties
///     ) {
///         return new OutboxRelayScheduler(relayUseCase, properties);
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.relay.config;
