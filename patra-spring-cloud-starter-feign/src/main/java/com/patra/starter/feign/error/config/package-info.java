/**
 * Feign 错误处理自动配置包。
 *
 * <p>提供 Feign 错误处理组件的自动配置和属性绑定。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>自动配置 {@link com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder}
 *   <li>注册 {@link com.patra.starter.feign.error.interceptor.TraceIdRequestInterceptor}
 *   <li>初始化 {@link com.patra.starter.feign.error.observation.FeignErrorObservationRecorder}
 *   <li>加载并验证 {@code patra.feign.error.*} 配置属性
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link FeignErrorAutoConfiguration} - 错误处理自动配置类
 *   <li>{@link FeignErrorProperties} - 绑定 {@code patra.feign.error.*} 属性
 * </ul>
 *
 * <h2>配置属性</h2>
 *
 * <pre>{@code
 * patra:
 *   feign:
 *     error:
 *       enabled: true              # 启用错误处理（默认）
 *       tolerant: true             # 宽容模式（推荐）
 *       max-error-body-size: 8192  # 最大错误响应体大小（字节）
 * }</pre>
 *
 * <h2>条件配置</h2>
 *
 * <ul>
 *   <li>{@code @ConditionalOnProperty} - 通过 {@code patra.feign.error.enabled} 控制
 *   <li>{@code @ConditionalOnClass} - 需要 {@code feign.Feign} 类存在
 *   <li>{@code @ConditionalOnMissingBean} - 允许用户自定义 ErrorDecoder
 * </ul>
 *
 * <h2>Bean 注册顺序</h2>
 *
 * <ol>
 *   <li>{@link FeignErrorProperties} - 配置属性绑定
 *   <li>{@link com.patra.starter.feign.error.observation.FeignErrorObservationRecorder} -
 *       指标记录器（可选，依赖 MeterRegistry）
 *   <li>{@link com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder} - 错误解码器
 *   <li>{@link com.patra.starter.feign.error.interceptor.TraceIdRequestInterceptor} -
 *       TraceId 拦截器
 * </ol>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.feign.error.config;
