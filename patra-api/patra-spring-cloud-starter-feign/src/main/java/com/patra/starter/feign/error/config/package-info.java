/// Feign 错误处理自动配置包。
///
/// 提供 Feign 错误处理组件的自动配置和属性绑定。
///
/// ## 职责
///
/// - 自动配置 {@link com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder}
///   - 注册 {@link com.patra.starter.feign.error.interceptor.TraceIdRequestInterceptor}
///   - 初始化 {@link com.patra.starter.feign.error.observation.FeignErrorObservationRecorder}
///   - 加载并验证 `patra.feign.error.*` 配置属性
///
/// ## 核心组件
///
/// - {@link FeignErrorAutoConfiguration} - 错误处理自动配置类
///   - {@link FeignErrorProperties} - 绑定 `patra.feign.error.*` 属性
///
/// ## 配置属性
///
/// ```java
/// patra:
///   feign:
///     error:
///       enabled: true              # 启用错误处理（默认）
///       tolerant: true             # 宽容模式（推荐）
///       max-error-body-size: 8192  # 最大错误响应体大小（字节）
/// ```
///
/// ## 条件配置
///
/// - `@ConditionalOnProperty` - 通过 `patra.feign.error.enabled` 控制
///   - `@ConditionalOnClass` - 需要 `feign.Feign` 类存在
///   - `@ConditionalOnMissingBean` - 允许用户自定义 ErrorDecoder
///
/// ## Bean 注册顺序
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.feign.error.config;
