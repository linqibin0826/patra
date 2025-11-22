/// 核心基础设施自动配置包。
/// 
/// 本包提供 Patra 平台的核心基础设施 Bean 自动配置,是所有微服务的基石。 所有配置遵循"约定优于配置"原则,提供开箱即用的合理默认值,同时支持灵活覆盖。
/// 
/// ## 职责
/// 
/// - 注册核心基础设施 Bean(如 {@link java.time.Clock})
///   - 提供统一的时间源,确保整个平台的时间戳一致性
///   - 支持测试环境下的 Bean 覆盖(如使用 Clock.fixed() 实现确定性测试)
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.starter.core.config.CoreAutoConfiguration} - 核心基础设施 Bean 自动配置类
/// 
/// ## 设计原则
/// 
/// - **集中化** - 基础设施 Bean 的单一真实来源(Single Source of Truth)
///   - **可覆盖性** - 使用 `@ConditionalOnMissingBean` 允许用户自定义实现
///   - **可测试性** - 所有 Bean 可在测试中轻松替换和 Mock
/// 
/// ## 使用示例
/// 
/// ```java
/// // 注入统一的时钟 Bean
/// @Service
/// public class PlanIngestionOrchestrator {
///     private final Clock clock;
/// 
///     public void processPlan(PlanAggregate plan) {
///         Instant ingestedAt = Instant.now(clock);  // 使用统一时钟
///         plan.recordIngestion(ingestedAt);
/// 
/// // 测试中覆盖时钟 Bean
/// @TestConfiguration
/// class TestConfig {
///     @Bean
///     @Primary
///     public Clock clock() {
///         return Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.core.config;
