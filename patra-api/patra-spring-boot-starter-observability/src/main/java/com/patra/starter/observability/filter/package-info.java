///
/// MeterFilter 实现。
///
/// 本包提供了 Micrometer MeterFilter 的实现，用于在 Meter 注册阶段进行自定义处理。
///
/// ## 核心组件
///
/// - {@link com.patra.starter.observability.filter.CommonTagsMeterFilter} - 公共标签过滤器
/// - {@link com.patra.starter.observability.filter.MetricNamingMeterFilter} - 指标命名规范过滤器
/// - {@link com.patra.starter.observability.filter.HighCardinalityMeterFilter} - 高基数标签过滤器
///
/// ## MeterFilter vs ObservationFilter
///
/// 两者的区别：
///
/// - **ObservationFilter**: 在 Observation 创建阶段执行，可以修改 Context（如脱敏、添加标签）
/// - **MeterFilter**: 在 Meter 注册阶段执行，可以修改 Meter.Id（如重命名、添加标签、过滤）
///
/// ## Filter 执行顺序
///
/// MeterFilter 按注册顺序执行，建议顺序：
///
/// - HighCardinalityMeterFilter - 优先移除高基数标签（防止后续处理无效标签）
/// - MetricNamingMeterFilter - 规范化指标名称（确保命名一致性）
/// - CommonTagsMeterFilter - 添加公共标签（在名称和标签都规范后添加）
///
/// ## MeterFilter 的作用
///
/// - **命名规范**: 强制执行统一的指标命名规范（patra.{module}.{metric}）
/// - **标签管理**: 自动添加公共标签，过滤高基数标签
/// - **性能保护**: 防止高基数标签导致时序数据库性能问题
/// - **指标过滤**: 可以选择性地接受或拒绝指标（如过滤 JMX 指标）
///
/// ## 使用场景
///
/// - **开发环境**: 启用全部 MeterFilter，严格检查命名和标签规范
/// - **生产环境**: 必须启用 HighCardinalityMeterFilter，保护时序数据库
///
/// ## 与 ObservationHandler 的协作
///
/// 完整的可观测性流程：
///
/// ```
/// 1. Observation 创建 → ObservationFilter 修改 Context（脱敏、添加 Observation 标签）
/// 2. Observation 执行 → ObservationHandler 处理生命周期事件（日志、性能监控）
/// 3. Observation 转换为 Meter → MeterFilter 修改 Meter.Id（命名规范、公共标签、过滤高基数标签）
/// 4. Meter 注册 → MeterRegistry 收集指标
/// 5. 指标导出 → SkyWalking/Prometheus
/// ```
///
/// ## 配置示例
///
/// ```yaml
/// patra:
///   observability:
///     metrics:
///       enabled: true
///       prefix: ""  # 可选前缀（在 "patra." 之后添加）
///       common-tags:  # 公共标签
///         team: backend
///         project: patra
/// ```
///
/// @author Jobs
/// @since 1.0.0
 */
package com.patra.starter.observability.filter;
