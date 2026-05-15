package com.patra.starter.core.cqrs.interceptor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/// 命令拦截器自动配置类。
///
/// 扫描并注册本包下的所有拦截器组件。
/// 各拦截器通过 `@ConditionalOnProperty` 和 `@ConditionalOnBean` 控制是否启用。
///
/// ## 内置拦截器
///
/// | 拦截器 | Order | 条件 | 说明 |
/// |--------|-------|------|------|
/// | TracingCommandInterceptor | 50 | ObservationRegistry 存在 | 分布式追踪 |
/// | LoggingCommandInterceptor | 100 | 默认启用 | 日志记录 |
/// | MetricsCommandInterceptor | 200 | MeterRegistry 存在 | 指标采集 |
///
/// ## 执行顺序
///
/// ```
/// Tracing(50) → Logging(100) → Metrics(200) → Handler
/// ```
@AutoConfiguration
@ComponentScan(basePackageClasses = CommandInterceptorAutoConfiguration.class)
public class CommandInterceptorAutoConfiguration {}
