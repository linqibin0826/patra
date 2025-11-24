///
/// 可观测性配置属性包
///
/// ## 职责
///
/// 定义可观测性 Starter 的配置属性类，支持通过 `application.yml` 自定义 Metrics、Tracing、Logging 行为。
///
/// ## 核心组件
///
/// - `ObservabilityProperties`：可观测性配置属性类（配置前缀：`patra.observability`）
///
/// ## 配置示例
///
/// ```yaml
/// patra:
///   observability:
///     enabled: true  # 是否启用可观测性
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
///         patterns:
///           - "password"
///           - "token"
///           - "apiKey"
///         custom-patterns:
///           - "creditCard"
///           - "ssn"
/// ```
///
/// ## ObservabilityProperties 属性说明
///
/// ### enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用可观测性自动配置
///
/// ### metrics.enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用 Micrometer 指标收集
///
/// ### metrics.export.prometheus
///
/// **类型**：`boolean`
/// **默认值**：`false`
/// **说明**：是否启用 Prometheus 导出（需添加 `micrometer-registry-prometheus` 依赖）
///
/// ### metrics.export.skywalking
///
/// **类型**：`boolean`
/// **默认值**：`false`
/// **说明**：是否启用 SkyWalking Meter（需添加 `skywalking-apm-toolkit-micrometer-1.5` 依赖）
///
/// ### tracing.enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用 SkyWalking 追踪集成
///
/// ### tracing.sampling-rate
///
/// **类型**：`double`
/// **默认值**：`1.0`
/// **说明**：追踪采样率（0.0-1.0），1.0 表示全量采样
///
/// ### logging.enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用结构化日志记录（LoggingObservationHandler）
///
/// ### logging.performance-threshold-ms
///
/// **类型**：`long`
/// **默认值**：`1000`
/// **说明**：慢操作阈值（毫秒），超过此阈值的操作会记录 WARN 日志
///
/// ### security.sensitive-data-masking.enabled
///
/// **类型**：`boolean`
/// **默认值**：`true`
/// **说明**：是否启用敏感数据脱敏（P0 级别，生产环境强制启用）
///
/// ### security.sensitive-data-masking.patterns
///
/// **类型**：`List<String>`
/// **默认值**：`["password", "token", "apiKey", "secret"]`
/// **说明**：内置的敏感数据模式，自动检测并脱敏
///
/// ### security.sensitive-data-masking.custom-patterns
///
/// **类型**：`List<String>`
/// **默认值**：`[]`
/// **说明**：自定义敏感数据模式，扩展内置模式
///
/// ## 使用示例
///
/// ### 在代码中读取配置
///
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class ObservabilityConfigurer {
///
///     private final ObservabilityProperties properties;
///
///     public void printConfig() {
///         log.info("Metrics 启用: {}", properties.getMetrics().isEnabled());
///         log.info("Prometheus 导出: {}", properties.getMetrics().getExport().isPrometheus());
///         log.info("采样率: {}", properties.getTracing().getSamplingRate());
///         log.info("慢操作阈值: {}ms", properties.getLogging().getPerformanceThresholdMs());
///     }
/// }
/// ```
///
/// ### 环境特定配置
///
/// ```yaml
/// # application-dev.yml
/// patra:
///   observability:
///     tracing:
///       sampling-rate: 0.1  # 开发环境低采样率
///     logging:
///       performance-threshold-ms: 5000  # 开发环境较宽松的阈值
///
/// # application-prod.yml
/// patra:
///   observability:
///     tracing:
///       sampling-rate: 1.0  # 生产环境全量采样
///     logging:
///       performance-threshold-ms: 1000  # 生产环境严格的阈值
///     security:
///       sensitive-data-masking:
///         enabled: true  # 生产环境强制启用
/// ```
///
/// ### 自定义敏感数据模式
///
/// ```yaml
/// patra:
///   observability:
///     security:
///       sensitive-data-masking:
///         enabled: true
///         patterns:  # 覆盖内置模式
///           - "password"
///           - "token"
///           - "apiKey"
///         custom-patterns:  # 扩展自定义模式
///           - "creditCard"
///           - "ssn"
///           - "bankAccount"
///           - "idCard"
/// ```
///
/// ## Actuator 配置
///
/// ### 健康检查端点
///
/// ```yaml
/// management:
///   endpoint:
///     health:
///       show-details: when-authorized  # 健康详情需认证
/// ```
///
/// ### Prometheus 端点
///
/// ```yaml
/// management:
///   endpoints:
///     web:
///       exposure:
///         include: health,prometheus  # 暴露 Prometheus 端点
/// ```
///
/// ### 访问控制
///
/// ```yaml
/// spring:
///   security:
///     user:
///       name: actuator
///       password: ${ACTUATOR_PASSWORD}  # 环境变量注入，禁止硬编码
/// ```
///
/// ## 注意事项
///
/// ### 生产环境必须启用敏感数据脱敏
///
/// ```yaml
/// patra:
///   observability:
///     security:
///       sensitive-data-masking:
///         enabled: true  # 强制启用
/// ```
///
/// ### Prometheus 导出需要额外依赖
///
/// ```xml
/// <dependency>
///     <groupId>io.micrometer</groupId>
///     <artifactId>micrometer-registry-prometheus</artifactId>
/// </dependency>
/// ```
///
/// ### SkyWalking Meter 需要额外依赖
///
/// ```xml
/// <dependency>
///     <groupId>org.apache.skywalking</groupId>
///     <artifactId>skywalking-apm-toolkit-micrometer-1.5</artifactId>
/// </dependency>
/// ```
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.observability.config;
