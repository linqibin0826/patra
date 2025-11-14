/**
 * 中继配置包。
 *
 * <p>本包提供 Outbox 中继的 Spring Boot 配置属性。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义中继配置属性（批大小、租约时长、轮询间隔）
 *   <li>提供配置默认值和验证规则
 *   <li>支持通过配置文件动态调整中继参数
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code OutboxRelayProperties} - Outbox 中继属性
 *       <ul>
 *         <li>{@code enabled}: 是否启用中继（默认 true）
 *         <li>{@code batchSize}: 批次大小（默认 100）
 *         <li>{@code leaseDuration}: 租约时长（默认 5 分钟）
 *         <li>{@code pollingInterval}: 轮询间隔（默认 10 秒）
 *         <li>{@code retryMaxAttempts}: 最大重试次数（默认 3）
 *         <li>{@code retryBackoff}: 重试退避时间（默认 2 秒）
 *       </ul>
 *   <li>{@code OutboxRelayConfiguration} - Outbox 中继配置类
 * </ul>
 *
 * <h2>配置示例</h2>
 *
 * <pre>
 * # application.yml
 * patra:
 *   ingest:
 *     outbox:
 *       relay:
 *         enabled: true
 *         batch-size: 100
 *         lease-duration: 5m
 *         polling-interval: 10s
 *         retry-max-attempts: 3
 *         retry-backoff: 2s
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Configuration
 * @EnableConfigurationProperties(OutboxRelayProperties.class)
 * public class OutboxRelayConfiguration {
 *
 *     @Bean
 *     @ConditionalOnProperty(prefix = "patra.ingest.outbox.relay", name = "enabled", havingValue = "true")
 *     public OutboxRelayScheduler outboxRelayScheduler(
 *         OutboxRelayUseCase relayUseCase,
 *         OutboxRelayProperties properties
 *     ) {
 *         return new OutboxRelayScheduler(relayUseCase, properties);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.relay.config;
