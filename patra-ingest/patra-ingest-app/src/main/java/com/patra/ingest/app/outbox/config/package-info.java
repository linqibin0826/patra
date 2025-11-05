/**
 * Outbox 配置包。
 *
 * <p>本包提供 Outbox 发布器和中继任务的 Spring Boot 配置属性。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>定义 Outbox 发布器配置属性（批大小、超时、启用开关）
 *   <li>定义 Outbox 中继配置属性（轮询间隔、租约时长、重试策略）
 *   <li>提供配置默认值和验证规则
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code OutboxPublisherProperties} - Outbox 发布器属性
 *       <ul>
 *         <li>{@code enabled}: 是否启用 Outbox 发布（默认 true）
 *         <li>{@code batchSize}: 批量发布大小（默认 50）
 *         <li>{@code timeout}: 发布超时时间（默认 5s）
 *       </ul>
 * </ul>
 *
 * <h2>配置示例</h2>
 * <pre>
 * # application.yml
 * patra:
 *   ingest:
 *     outbox:
 *       publisher:
 *         enabled: true
 *         batch-size: 50
 *         timeout: 5s
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Configuration
 * @EnableConfigurationProperties(OutboxPublisherProperties.class)
 * public class OutboxConfig {
 *
 *     @Bean
 *     public AbstractOutboxPublisher outboxPublisher(
 *         OutboxPublisherProperties properties,
 *         OutboxMessageRepository repository
 *     ) {
 *         return new TaskOutboxPublisher(properties, repository);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.outbox.config;
