/// 错误处理框架配置包。
///
/// 本包提供 Patra 平台统一错误处理框架的自动配置和属性定义, 包括错误解析引擎、拦截器管道、观测能力和可选的熔断保护。
///
/// ## 职责
///
/// - 配置错误解析引擎和拦截器管道
///   - 注册追踪传播、指标收集、熔断器等拦截器
///   - 提供错误处理相关的配置属性绑定
///   - 管理条件化自动配置(如 Resilience4j 熔断器)
///
/// ## 核心组件
///
/// - {@link com.patra.starter.core.error.config.CoreErrorAutoConfiguration} - 核心错误处理自动配置
///   - {@link com.patra.starter.core.error.config.CircuitBreakerErrorAutoConfiguration} -
///       熔断器自动配置(可选)
///   - {@link com.patra.starter.core.error.config.ErrorProperties} - 错误处理配置属性
///   - {@link com.patra.starter.core.error.config.TracingProperties} - 追踪配置属性
///
/// ## 配置属性
///
/// ### 错误处理配置 (`patra.error`)
///
/// ```java
/// patra:
///   error:
///     enabled: true                                # 是否启用错误处理框架
///     context-prefix: PATRA                        # 错误代码上下文前缀
///     engine:
///       max-cause-depth: 10                        # 原因链遍历最大深度
///       enable-trait-mapping: true                 # 是否启用特征映射
///       enable-naming-heuristic: true              # 是否启用类名启发式
///     observation:
///       enabled: true                              # 是否启用错误观测
///       slow-threshold-ms: 200                     # 慢解析阈值(毫秒)
///       log-slow-resolution: true                  # 是否记录慢解析警告
///     circuit-breaker:
///       enabled: true                              # 是否启用熔断器
///       failure-rate-threshold: 50.0               # 失败率阈值(百分比)
///       minimum-number-of-calls: 20                # 最小调用次数
///       sliding-window-size: 50                    # 滑动窗口大小
///       permitted-calls-in-half-open-state: 5      # 半开状态允许调用数
///       wait-duration-in-open-state: 30s           # 断路器打开等待时长
/// ```
///
/// ### 熔断器配置 (`patra.error.circuit-breaker`)
///
/// ```java
/// patra:
///   error:
///     circuit-breaker:
///       enabled: true                              # 是否启用熔断器
///       failure-rate-threshold: 50.0               # 失败率阈值(百分比)
///       minimum-number-of-calls: 20                # 最小调用次数
///       sliding-window-size: 50                    # 滑动窗口大小
///       permitted-calls-in-half-open-state: 5      # 半开状态允许调用数
///       wait-duration-in-open-state: 30s           # 断路器打开等待时长
/// ```
///
/// ### 追踪配置 (`patra.tracing`)
///
/// ```java
/// patra:
///   tracing:
///     header-names:                                # 追踪 ID 的 HTTP 头名称列表
///       - X-Trace-ID
///       - X-B3-TraceId
/// ```
///
/// ## Bean 命名说明
///
/// - `errorResolutionTracingInterceptor` - 本包中的追踪拦截器 Bean 名称明确标识其用途：
///   在错误解析管道中传播追踪上下文。此命名避免与 patra-spring-boot-starter-rest-client 中的
///   `restClientTracingInterceptor` 冲突（两个模块都使用 TracingInterceptor 但在不同上下文中）。
/// - 参考 troubleshooting-and-notes/configuration.md 中的"Bean 命名冲突解决"部分。
///
/// ## 自动配置流程
///
/// ## 扩展点
///
/// - 自定义拦截器 - 实现 {@link com.patra.starter.core.error.pipeline.ResolutionInterceptor}
///   - 自定义映射 - 实现 {@link com.patra.starter.core.error.spi.ErrorMappingContributor}
///   - 自定义追踪提取 - 实现 {@link com.patra.starter.core.error.spi.TraceProvider}
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.core.error.config;
