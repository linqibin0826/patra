/// Observation Handler 实现。
///
/// 本包提供了 Micrometer Observation API 的处理器实现，用于在 Observation 执行阶段进行自定义处理。
///
/// ## 核心组件
///
/// - {@link com.patra.starter.observability.handler.PerformanceObservationHandler} - 性能观测处理器
/// - {@link com.patra.starter.observability.handler.LoggingObservationHandler} - 日志观测处理器
///
/// ## ObservationHandler vs ObservationFilter
///
/// 两者的区别：
///
/// - **ObservationFilter**: 在 Observation 创建阶段执行，可以修改 Context（如脱敏、添加标签）
/// - **ObservationHandler**: 在 Observation 执行阶段执行，处理生命周期事件（如日志、性能监控）
///
/// ## Handler 执行顺序
///
/// ObservationHandler 按注册顺序执行，所有 Handler 都会接收到事件：
///
/// - LoggingObservationHandler - 记录生命周期日志
/// - PerformanceObservationHandler - 检测慢操作
/// - DefaultMeterObservationHandler - Spring Boot 自动配置，将 Observation 转换为 Metrics
///
/// ## 关于 MetricsObservationHandler
///
/// **注意**：本包**不实现** MetricsObservationHandler，原因如下：
///
/// - Spring Boot 已自动配置 `DefaultMeterObservationHandler`
/// - 该 Handler 自动将 Observation 转换为 Timer 指标（count、duration）
/// - 重复实现会导致指标重复收集
/// - 如需自定义指标逻辑，应通过 `MeterFilter` 实现
///
/// ## 使用场景
///
/// - **开发环境**: 启用 LoggingObservationHandler (DEBUG 级别) + PerformanceObservationHandler (1s 阈值)
/// - **生产环境**: 禁用 LoggingObservationHandler + PerformanceObservationHandler (5s 阈值)
///
/// @author Jobs
/// @since 1.0.0
package com.patra.starter.observability.handler;
