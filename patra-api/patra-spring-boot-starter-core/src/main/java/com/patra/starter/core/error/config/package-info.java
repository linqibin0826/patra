/**
 * 错误处理框架配置包。
 *
 * <p>本包提供 Patra 平台统一错误处理框架的自动配置和属性定义,
 * 包括错误解析引擎、拦截器管道、观测能力和可选的熔断保护。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>配置错误解析引擎和拦截器管道
 *   <li>注册追踪传播、指标收集、熔断器等拦截器
 *   <li>提供错误处理相关的配置属性绑定
 *   <li>管理条件化自动配置(如 Resilience4j 熔断器)
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.core.error.config.CoreErrorAutoConfiguration} - 核心错误处理自动配置
 *   <li>{@link com.patra.starter.core.error.config.CircuitBreakerErrorAutoConfiguration} - 熔断器自动配置(可选)
 *   <li>{@link com.patra.starter.core.error.config.ErrorProperties} - 错误处理配置属性
 *   <li>{@link com.patra.starter.core.error.config.TracingProperties} - 追踪配置属性
 * </ul>
 *
 * <h2>配置属性</h2>
 *
 * <h3>错误处理配置 ({@code patra.error})</h3>
 * <pre>{@code
 * patra:
 *   error:
 *     enabled: true                                # 是否启用错误处理框架
 *     context-prefix: PATRA                        # 错误代码上下文前缀
 *     engine:
 *       max-cause-depth: 10                        # 原因链遍历最大深度
 *       enable-trait-mapping: true                 # 是否启用特征映射
 *       enable-naming-heuristic: true              # 是否启用类名启发式
 *     observation:
 *       enabled: true                              # 是否启用错误观测
 *       slow-threshold-ms: 200                     # 慢解析阈值(毫秒)
 *       log-slow-resolution: true                  # 是否记录慢解析警告
 * }</pre>
 *
 * <h3>熔断器配置 ({@code patra.error.circuit-breaker})</h3>
 * <pre>{@code
 * patra:
 *   error:
 *     circuit-breaker:
 *       enabled: true                              # 是否启用熔断器
 *       failure-rate-threshold: 50.0               # 失败率阈值(百分比)
 *       minimum-number-of-calls: 20                # 最小调用次数
 *       sliding-window-size: 50                    # 滑动窗口大小
 *       permitted-calls-in-half-open-state: 5      # 半开状态允许调用数
 *       wait-duration-in-open-state: 30s           # 断路器打开等待时长
 * }</pre>
 *
 * <h3>追踪配置 ({@code patra.tracing})</h3>
 * <pre>{@code
 * patra:
 *   tracing:
 *     header-names:                                # 追踪 ID 的 HTTP 头名称列表
 *       - X-Trace-ID
 *       - X-B3-TraceId
 * }</pre>
 *
 * <h2>自动配置流程</h2>
 *
 * <ol>
 *   <li>{@code CoreErrorAutoConfiguration} 注册核心错误处理组件(引擎、管道、拦截器)
 *   <li>{@code CircuitBreakerErrorAutoConfiguration} 条件性注册熔断器拦截器(需 Resilience4j)
 *   <li>所有拦截器按 {@code @Order} 排序组装成责任链
 *   <li>错误解析引擎加载所有 SPI 实现({@code ErrorMappingContributor})
 * </ol>
 *
 * <h2>扩展点</h2>
 *
 * <ul>
 *   <li>自定义拦截器 - 实现 {@link com.patra.starter.core.error.pipeline.ResolutionInterceptor}
 *   <li>自定义映射 - 实现 {@link com.patra.starter.core.error.spi.ErrorMappingContributor}
 *   <li>自定义追踪提取 - 实现 {@link com.patra.starter.core.error.spi.TraceProvider}
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.core.error.config;
