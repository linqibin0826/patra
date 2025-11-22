/// 应用层配置包。
///
/// 本包提供 patra-ingest 应用层的 Spring 配置类和属性配置。
///
/// ## 职责
///
/// - 注册应用层 Bean（Orchestrator、Coordinator、UseCase）
///   - 配置外部服务集成（Provenance、Expression Compiler）
///   - 配置 Outbox 发布器和中继任务
///   - 配置事件监听器和处理器
///
/// ## 核心组件
///
/// - `IngestAppConfig` - 应用层主配置类
///
/// ## 配置示例
///
/// ```java
/// @Configuration
/// public class IngestAppConfig {
///
///     @Bean
///     public PlanIngestionOrchestrator planIngestionOrchestrator(
///         PlanRepository planRepository,
///         PatraRegistryPort registryPort,
///         // ... 其他依赖
///     ) {
///         return new PlanIngestionOrchestrator(planRepository, registryPort, ...);
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.config;
