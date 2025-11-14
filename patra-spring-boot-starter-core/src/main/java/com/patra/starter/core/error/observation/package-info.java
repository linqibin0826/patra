/**
 * 错误处理可观测性包。
 *
 * <p>本包提供错误处理过程的可观测能力,集成 Micrometer 指标收集, 记录错误解析性能指标和慢解析警告。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>记录错误解析耗时指标(计时器)
 *   <li>记录错误类型分布(计数器)
 *   <li>检测并警告慢解析操作
 *   <li>集成 Micrometer Observation API
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.core.error.observation.ErrorObservationRecorder} - 错误观测记录器接口
 *   <li>{@link com.patra.starter.core.error.observation.MicrometerErrorObservationRecorder} - 基于
 *       Micrometer 的实现
 * </ul>
 *
 * <h2>指标类型</h2>
 *
 * <h3>计时器指标</h3>
 *
 * <ul>
 *   <li><strong>名称</strong>: {@code patra.error.resolution.time}
 *   <li><strong>类型</strong>: Timer
 *   <li><strong>描述</strong>: 错误解析耗时
 *   <li><strong>标签</strong>: {@code exception_type}(异常类名), {@code error_code}(错误码)
 * </ul>
 *
 * <h3>计数器指标</h3>
 *
 * <ul>
 *   <li><strong>名称</strong>: {@code patra.error.resolution.count}
 *   <li><strong>类型</strong>: Counter
 *   <li><strong>描述</strong>: 错误解析次数
 *   <li><strong>标签</strong>: {@code exception_type}, {@code error_code}, {@code http_status}
 * </ul>
 *
 * <h2>慢解析检测</h2>
 *
 * <p>当错误解析耗时超过阈值时,记录 WARN 级别日志:
 *
 * <pre>{@code
 * patra:
 *   error:
 *     observation:
 *       enabled: true
 *       slow-threshold-ms: 200          # 慢解析阈值(毫秒)
 *       log-slow-resolution: true       # 是否记录慢解析警告
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <h3>通过拦截器自动记录</h3>
 *
 * <pre>{@code
 * // MetricsInterceptor 自动使用 ErrorObservationRecorder
 * @Component
 * @Order(20)
 * public class MetricsInterceptor implements ResolutionInterceptor {
 *     private final ErrorObservationRecorder recorder;
 *
 *     @Override
 *     public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
 *         long startTime = System.nanoTime();
 *         ErrorResolution resolution = invocation.proceed(exception);
 *         long duration = System.nanoTime() - startTime;
 *
 *         recorder.recordResolution(exception, resolution, duration);
 *         return resolution;
 *     }
 * }
 * }</pre>
 *
 * <h3>查询指标(Prometheus 格式)</h3>
 *
 * <pre>
 * # 错误解析平均耗时(按异常类型)
 * patra_error_resolution_time_seconds_sum / patra_error_resolution_time_seconds_count
 *
 * # 错误类型分布
 * patra_error_resolution_count{exception_type="PlanNotFoundException"}
 *
 * # 慢解析次数(超过 200ms)
 * patra_error_resolution_time_seconds{quantile="0.99"} > 0.2
 * </pre>
 *
 * <h2>集成 Grafana Dashboard</h2>
 *
 * <p>推荐监控面板:
 *
 * <ul>
 *   <li><strong>错误率趋势</strong> - 按时间统计错误解析次数
 *   <li><strong>解析耗时分布</strong> - P50/P95/P99 延迟
 *   <li><strong>错误类型 Top N</strong> - 最常见的异常类型
 *   <li><strong>慢解析警告</strong> - 超过阈值的解析操作
 * </ul>
 *
 * <h2>配置选项</h2>
 *
 * <pre>{@code
 * patra:
 *   error:
 *     observation:
 *       enabled: true                    # 是否启用观测
 *       slow-threshold-ms: 200           # 慢解析阈值(毫秒)
 *       log-slow-resolution: true        # 是否记录慢解析日志
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.core.error.observation;
