///
/// 可观测性自动配置包
///
/// ## 职责
///
/// 提供可观测性的自动配置类，根据条件自动激活 Metrics、Tracing、Logging 集成。
///
/// ## 核心组件
///
/// - `ObservabilityAutoConfiguration`：主配置类，配置 Observation Handlers 和 Filters
/// - `MicrometerAutoConfiguration`：Micrometer 配置类，配置 ObservationRegistry 和 MeterRegistry
/// - `PrometheusAutoConfiguration`：Prometheus 配置类（条件激活）
/// - `SkyWalkingMeterAutoConfiguration`：SkyWalking Meter 配置类（条件激活）
/// - `ObservationInterceptorsAutoConfiguration`：可观测性拦截器配置类
///
/// ## 自动配置机制
///
/// ### ObservabilityAutoConfiguration
///
/// **激活条件**：`patra.observability.enabled=true`（默认启用）
///
/// **配置内容**：
/// - `SensitiveDataObservationFilter`：敏感数据脱敏 Filter（P0）
/// - `CommonTagsObservationFilter`：公共标签 Filter
/// - `LoggingObservationHandler`：日志 Handler
/// - `PerformanceObservationHandler`：性能 Handler
///
/// ### MicrometerAutoConfiguration
///
/// **激活条件**：`patra.observability.metrics.enabled=true`（默认启用）
///
/// **配置内容**：
/// - `ObservationRegistry`：Observation API 核心
/// - `MeterRegistry`：Metrics 注册器
/// - `MeterFilter` 链：命名规范、公共标签、高基数过滤
///
/// ### PrometheusAutoConfiguration
///
/// **激活条件**：
/// - `patra.observability.metrics.export.prometheus=true`
/// - 类路径存在 `PrometheusMeterRegistry.class`
///
/// **配置内容**：
/// - `PrometheusMeterRegistry`：Prometheus 指标注册器
/// - Actuator `/prometheus` 端点自动暴露
///
/// ### SkyWalkingMeterAutoConfiguration
///
/// **激活条件**：
/// - `patra.observability.metrics.export.skywalking=true`
/// - 类路径存在 SkyWalking Meter 依赖
///
/// **配置内容**：
/// - SkyWalking Meter Reporter
/// - 自动上报指标到 SkyWalking OAP
///
/// ### ObservationInterceptorsAutoConfiguration
///
/// **激活条件**：
/// - 依赖对应的 Starter（core/rest-client/batch）
/// - 对应功能启用（tracing/metrics/logging）
///
/// **配置内容**：
/// - `ObservationResolutionInterceptor`：错误解析管道可观测性（依赖 patra-starter-core）
/// - `RestClientObservationInterceptor`：REST 客户端可观测性（依赖 patra-starter-rest-client）
/// - `BatchObservationJobListener`：Batch 任务可观测性（依赖 patra-starter-batch）
///
/// ## 使用示例
///
/// ### 自定义配置
///
/// ```yaml
/// patra:
///   observability:
///     enabled: true  # 启用可观测性
///
///     # Metrics 配置
///     metrics:
///       enabled: true
///       export:
///         prometheus: true  # 启用 Prometheus 导出
///         skywalking: true  # 启用 SkyWalking Meter
///
///     # Tracing 配置
///     tracing:
///       enabled: true
///       sampling-rate: 1.0  # 采样率（0.0-1.0）
///
///     # Logging 配置
///     logging:
///       enabled: true
///       performance-threshold-ms: 1000  # 慢操作阈值（毫秒）
///
///     # 安全配置（P0）
///     security:
///       sensitive-data-masking:
///         enabled: true  # 生产环境强制启用
/// ```
///
/// ### 禁用自动配置
///
/// ```yaml
/// patra:
///   observability:
///     enabled: false  # 禁用所有可观测性功能
/// ```
///
/// 或单独禁用某个功能：
///
/// ```yaml
/// patra:
///   observability:
///     metrics:
///       export:
///         prometheus: false  # 禁用 Prometheus 导出
/// ```
///
/// ## 扩展配置
///
/// ### 自定义 ObservationHandler
///
/// ```java
/// @Component
/// public class CustomObservationHandler implements ObservationHandler<Observation.Context> {
///
///     @Override
///     public void onStart(Observation.Context context) {
///         // 观察开始时的逻辑
///     }
///
///     @Override
///     public void onStop(Observation.Context context) {
///         // 观察结束时的逻辑
///     }
///
///     @Override
///     public boolean supportsContext(Observation.Context context) {
///         return true;
///     }
/// }
/// ```
///
/// ### 自定义 MeterFilter
///
/// ```java
/// @Component
/// public class CustomMeterFilter implements MeterFilter {
///
///     @Override
///     public Meter.Id map(Meter.Id id) {
///         // 自定义指标命名或标签
///         return id;
///     }
/// }
/// ```
///
/// ### 自定义敏感数据模式
///
/// ```yaml
/// patra:
///   observability:
///     security:
///       sensitive-data-masking:
///         custom-patterns:
///           - "creditCard"
///           - "ssn"
///           - "bankAccount"
/// ```
///
/// ## 配置优先级
///
/// 1. **代码配置** > YAML 配置
/// 2. **环境特定配置** > 通用配置
/// 3. **自定义 Bean** > 自动配置 Bean
///
/// ## 条件激活逻辑
///
/// ### ObservationInterceptorsAutoConfiguration
///
/// ```java
/// @Configuration
/// @ConditionalOnClass(ResolutionInterceptor.class)  // 依赖 patra-starter-core
/// public class ObservationInterceptorsAutoConfiguration {
///
///     @Bean
///     @ConditionalOnProperty(prefix = "patra.observability.tracing", name = "enabled", matchIfMissing = true)
///     public ObservationResolutionInterceptor observationResolutionInterceptor() {
///         return new ObservationResolutionInterceptor();
///     }
/// }
/// ```
///
/// ### PrometheusAutoConfiguration
///
/// ```java
/// @Configuration
/// @ConditionalOnClass(PrometheusMeterRegistry.class)  // 类路径存在 Prometheus
/// @ConditionalOnProperty(prefix = "patra.observability.metrics.export", name = "prometheus", havingValue = "true")
/// public class PrometheusAutoConfiguration {
///     // ...
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.observability.autoconfigure;
