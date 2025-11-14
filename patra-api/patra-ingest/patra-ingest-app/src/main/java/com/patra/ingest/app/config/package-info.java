/**
 * 应用层配置包。
 *
 * <p>本包提供 patra-ingest 应用层的 Spring 配置类和属性配置。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>注册应用层 Bean（Orchestrator、Coordinator、UseCase）
 *   <li>配置外部服务集成（Provenance、Expression Compiler）
 *   <li>配置 Outbox 发布器和中继任务
 *   <li>配置事件监听器和处理器
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code IngestAppConfig} - 应用层主配置类
 * </ul>
 *
 * <h2>配置示例</h2>
 *
 * <pre>{@code
 * @Configuration
 * public class IngestAppConfig {
 *
 *     @Bean
 *     public PlanIngestionOrchestrator planIngestionOrchestrator(
 *         PlanRepository planRepository,
 *         PatraRegistryPort registryPort,
 *         // ... 其他依赖
 *     ) {
 *         return new PlanIngestionOrchestrator(planRepository, registryPort, ...);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.config;
