/**
 * Observation Handler 实现。
 *
 * <p>本包提供了 Micrometer Observation API 的处理器实现，用于在 Observation 执行阶段进行自定义处理。
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link com.patra.starter.observability.handler.PerformanceObservationHandler} - 性能观测处理器</li>
 *   <li>{@link com.patra.starter.observability.handler.LoggingObservationHandler} - 日志观测处理器</li>
 * </ul>
 *
 * <h2>ObservationHandler vs ObservationFilter</h2>
 * <p>两者的区别：
 * <ul>
 *   <li><strong>ObservationFilter</strong>: 在 Observation 创建阶段执行，可以修改 Context（如脱敏、添加标签）</li>
 *   <li><strong>ObservationHandler</strong>: 在 Observation 执行阶段执行，处理生命周期事件（如日志、性能监控）</li>
 * </ul>
 *
 * <h2>Handler 执行顺序</h2>
 * <p>ObservationHandler 按注册顺序执行，所有 Handler 都会接收到事件：
 * <ol>
 *   <li>LoggingObservationHandler - 记录生命周期日志</li>
 *   <li>PerformanceObservationHandler - 检测慢操作</li>
 *   <li>DefaultMeterObservationHandler - Spring Boot 自动配置，将 Observation 转换为 Metrics</li>
 * </ol>
 *
 * <h2>关于 MetricsObservationHandler</h2>
 * <p><strong>注意</strong>：本包<strong>不实现</strong> MetricsObservationHandler，原因如下：
 * <ul>
 *   <li>Spring Boot 已自动配置 {@code DefaultMeterObservationHandler}</li>
 *   <li>该 Handler 自动将 Observation 转换为 Timer 指标（count、duration）</li>
 *   <li>重复实现会导致指标重复收集</li>
 *   <li>如需自定义指标逻辑，应通过 {@code MeterFilter} 实现</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li><strong>开发环境</strong>: 启用 LoggingObservationHandler (DEBUG 级别) + PerformanceObservationHandler (1s 阈值)</li>
 *   <li><strong>生产环境</strong>: 禁用 LoggingObservationHandler + PerformanceObservationHandler (5s 阈值)</li>
 * </ul>
 *
 * @author Jobs
 * @since 1.0.0
 */
package com.patra.starter.observability.handler;
