/**
 * 核心基础设施自动配置包。
 *
 * <p>本包提供 Patra 平台的核心基础设施 Bean 自动配置,是所有微服务的基石。 所有配置遵循"约定优于配置"原则,提供开箱即用的合理默认值,同时支持灵活覆盖。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>注册核心基础设施 Bean(如 {@link java.time.Clock})
 *   <li>提供统一的时间源,确保整个平台的时间戳一致性
 *   <li>支持测试环境下的 Bean 覆盖(如使用 Clock.fixed() 实现确定性测试)
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.core.config.CoreAutoConfiguration} - 核心基础设施 Bean 自动配置类
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>集中化</strong> - 基础设施 Bean 的单一真实来源(Single Source of Truth)
 *   <li><strong>可覆盖性</strong> - 使用 {@code @ConditionalOnMissingBean} 允许用户自定义实现
 *   <li><strong>可测试性</strong> - 所有 Bean 可在测试中轻松替换和 Mock
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 注入统一的时钟 Bean
 * @Service
 * public class PlanIngestionOrchestrator {
 *     private final Clock clock;
 *
 *     public void processPlan(PlanAggregate plan) {
 *         Instant ingestedAt = Instant.now(clock);  // 使用统一时钟
 *         plan.recordIngestion(ingestedAt);
 *     }
 * }
 *
 * // 测试中覆盖时钟 Bean
 * @TestConfiguration
 * class TestConfig {
 *     @Bean
 *     @Primary
 *     public Clock clock() {
 *         return Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.core.config;
