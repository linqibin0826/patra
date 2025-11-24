/**
 * MeterFilter 实现。
 *
 * <p>本包提供了 Micrometer MeterFilter 的实现，用于在 Meter 注册阶段进行自定义处理。
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link com.patra.starter.observability.filter.CommonTagsMeterFilter} - 公共标签过滤器</li>
 *   <li>{@link com.patra.starter.observability.filter.MetricNamingMeterFilter} - 指标命名规范过滤器</li>
 *   <li>{@link com.patra.starter.observability.filter.HighCardinalityMeterFilter} - 高基数标签过滤器</li>
 * </ul>
 *
 * <h2>MeterFilter vs ObservationFilter</h2>
 * <p>两者的区别：
 * <ul>
 *   <li><strong>ObservationFilter</strong>: 在 Observation 创建阶段执行，可以修改 Context（如脱敏、添加标签）</li>
 *   <li><strong>MeterFilter</strong>: 在 Meter 注册阶段执行，可以修改 Meter.Id（如重命名、添加标签、过滤）</li>
 * </ul>
 *
 * <h2>Filter 执行顺序</h2>
 * <p>MeterFilter 按注册顺序执行，建议顺序：
 * <ol>
 *   <li>HighCardinalityMeterFilter - 优先移除高基数标签（防止后续处理无效标签）</li>
 *   <li>MetricNamingMeterFilter - 规范化指标名称（确保命名一致性）</li>
 *   <li>CommonTagsMeterFilter - 添加公共标签（在名称和标签都规范后添加）</li>
 * </ol>
 *
 * <h2>MeterFilter 的作用</h2>
 * <ul>
 *   <li><strong>命名规范</strong>: 强制执行统一的指标命名规范（patra.{module}.{metric}）</li>
 *   <li><strong>标签管理</strong>: 自动添加公共标签，过滤高基数标签</li>
 *   <li><strong>性能保护</strong>: 防止高基数标签导致时序数据库性能问题</li>
 *   <li><strong>指标过滤</strong>: 可以选择性地接受或拒绝指标（如过滤 JMX 指标）</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li><strong>开发环境</strong>: 启用全部 MeterFilter，严格检查命名和标签规范</li>
 *   <li><strong>生产环境</strong>: 必须启用 HighCardinalityMeterFilter，保护时序数据库</li>
 * </ul>
 *
 * <h2>与 ObservationHandler 的协作</h2>
 * <p>完整的可观测性流程：
 * <pre>
 * 1. Observation 创建 → ObservationFilter 修改 Context（脱敏、添加 Observation 标签）
 * 2. Observation 执行 → ObservationHandler 处理生命周期事件（日志、性能监控）
 * 3. Observation 转换为 Meter → MeterFilter 修改 Meter.Id（命名规范、公共标签、过滤高基数标签）
 * 4. Meter 注册 → MeterRegistry 收集指标
 * 5. 指标导出 → SkyWalking/Prometheus
 * </pre>
 *
 * <h2>配置示例</h2>
 * <pre>
 * patra:
 *   observability:
 *     metrics:
 *       enabled: true
 *       prefix: ""  # 可选前缀（在 "patra." 之后添加）
 *       common-tags:  # 公共标签
 *         team: backend
 *         project: patra
 * </pre>
 *
 * @author Jobs
 * @since 1.0.0
 */
package com.patra.starter.observability.filter;
