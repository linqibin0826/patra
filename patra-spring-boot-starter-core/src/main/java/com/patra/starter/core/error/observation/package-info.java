/// 错误处理可观测性包。
///
/// 本包提供错误处理过程的可观测能力,集成 Micrometer 指标收集, 记录错误解析性能指标和慢解析警告。
///
/// ## 职责
///
/// - 记录错误解析耗时指标(计时器)
///   - 记录错误类型分布(计数器)
///   - 检测并警告慢解析操作
///   - 集成 Micrometer Observation API
///
/// ## 核心组件
///
/// - {@link com.patra.starter.core.error.observation.ErrorObservationRecorder} - 错误观测记录器接口
///   - {@link com.patra.starter.core.error.observation.MicrometerErrorObservationRecorder} - 基于
///       Micrometer 的实现
///
/// ## 指标类型
///
/// ### 计时器指标
///
/// - **名称**: `patra.error.resolution.time`
///   - **类型**: Timer
///   - **描述**: 错误解析耗时
///   - **标签**: `exception_type`(异常类名), `error_code`(错误码)
///
/// ### 计数器指标
///
/// - **名称**: `patra.error.resolution.count`
///   - **类型**: Counter
///   - **描述**: 错误解析次数
///   - **标签**: `exception_type`, `error_code`, `http_status`
///
/// ## 慢解析检测
///
/// 当错误解析耗时超过阈值时,记录 WARN 级别日志:
///
/// ```java
/// patra:
///   error:
///     observation:
///       enabled: true
///       slow-threshold-ms: 200          # 慢解析阈值(毫秒)
///       log-slow-resolution: true       # 是否记录慢解析警告
/// ```
///
/// ## 使用示例
///
/// ### 通过拦截器自动记录
///
/// ```java
/// // MetricsInterceptor 自动使用 ErrorObservationRecorder
/// @Component
/// @Order(20)
/// public class MetricsInterceptor implements ResolutionInterceptor {
///     private final ErrorObservationRecorder recorder;
///
///     @Override
///     public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
///         long startTime = System.nanoTime();
///         ErrorResolution resolution = invocation.proceed(exception);
///         long duration = System.nanoTime() - startTime;
///
///         recorder.recordResolution(exception, resolution, duration);
///         return resolution;
/// ```
///
/// ### 查询指标(Prometheus 格式)
///
/// ```
///
/// # 错误解析平均耗时(按异常类型)
/// patra_error_resolution_time_seconds_sum / patra_error_resolution_time_seconds_count
///
/// # 错误类型分布
/// patra_error_resolution_count{exception_type="PlanNotFoundException"}
///
/// # 慢解析次数(超过 200ms)
/// patra_error_resolution_time_seconds{quantile="0.99"} > 0.2
///
/// ```
///
/// ## 集成 Grafana Dashboard
///
/// 推荐监控面板:
///
/// - **错误率趋势** - 按时间统计错误解析次数
///   - **解析耗时分布** - P50/P95/P99 延迟
///   - **错误类型 Top N** - 最常见的异常类型
///   - **慢解析警告** - 超过阈值的解析操作
///
/// ## 配置选项
///
/// ```java
/// patra:
///   error:
///     observation:
///       enabled: true                    # 是否启用观测
///       slow-threshold-ms: 200           # 慢解析阈值(毫秒)
///       log-slow-resolution: true        # 是否记录慢解析日志
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.core.error.observation;
