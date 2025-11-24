///
/// 可观测性拦截器（插件式架构）。
///
/// 本包提供了可观测性功能的拦截器实现，遵循插件式架构设计原则。
///
/// ## 核心组件
///
/// - {@link com.patra.starter.observability.interceptor.ObservationResolutionInterceptor} - 错误解析可观测性拦截器
/// - {@link com.patra.starter.observability.interceptor.RestClientObservationInterceptor} - REST 客户端可观测性拦截器
/// - {@link com.patra.starter.observability.interceptor.BatchObservationJobListener} - 批处理任务可观测性监听器
///
/// ## 插件式架构设计
///
/// 遵循依赖倒置原则（DIP），patra-starter-observability 通过实现其他 Starter 提供的扩展点来集成可观测性功能：
///
/// ```
/// ┌────────────────────────────────────────┐
/// │   patra-starter-core（扩展点定义）    │
/// │   ├─ ResolutionInterceptor             │
/// │   └─ ErrorResolutionPipeline           │
/// └────────────────────────────────────────┘
///              ▲
///              │ 实现
///              │
/// ┌────────────────────────────────────────┐
/// │ patra-starter-observability（插件）    │
/// │   └─ ObservationResolutionInterceptor  │
/// └────────────────────────────────────────┘
///
/// ┌────────────────────────────────────────┐
/// │ patra-starter-rest-client（扩展点）    │
/// │   └─ ClientHttpRequestInterceptor      │
/// └────────────────────────────────────────┘
///              ▲
///              │ 实现
///              │
/// ┌────────────────────────────────────────┐
/// │ patra-starter-observability（插件）    │
/// │   └─ RestClientObservationInterceptor  │
/// └────────────────────────────────────────┘
///
/// ┌────────────────────────────────────────┐
/// │ patra-starter-batch（扩展点）          │
/// │   └─ JobExecutionListener              │
/// └────────────────────────────────────────┘
///              ▲
///              │ 实现
///              │
/// ┌────────────────────────────────────────┐
/// │ patra-starter-observability（插件）    │
/// │   └─ BatchObservationJobListener       │
/// └────────────────────────────────────────┘
/// ```
///
/// ## 设计优势
///
/// - **依赖倒置**: 核心 Starter 不依赖可观测性 Starter，保持纯净
/// - **可插拔**: 可以独立启用/禁用可观测性功能
/// - **解耦**: 各个 Starter 之间没有直接依赖，通过接口通信
/// - **扩展性**: 未来可以添加更多可观测性插件（如安全、审计等）
///
/// ## 拦截器功能
///
/// ### ObservationResolutionInterceptor
///
/// 为错误解析流程添加可观测性支持：
///
/// - 记录错误类型、错误类
/// - 记录解析是否成功
/// - 自动集成分布式追踪
/// - 生成错误解析指标
///
/// ### RestClientObservationInterceptor
///
/// 为 REST 客户端请求添加可观测性支持：
///
/// - 记录 HTTP 方法、URI、状态码
/// - 记录请求结果（SUCCESS、CLIENT_ERROR、SERVER_ERROR）
/// - 自动集成分布式追踪（传播 TraceId/SpanId）
/// - 生成 HTTP 客户端指标
///
/// ### BatchObservationJobListener
///
/// 为批处理任务添加可观测性支持：
///
/// - 记录任务名称、执行 ID、状态
/// - 记录任务执行时间
/// - 自动集成分布式追踪（跨步骤追踪）
/// - 生成批处理任务指标
///
///
/// ## Observation 生命周期
///
/// 所有拦截器遵循统一的 Observation 生命周期管理模式：
///
/// ```
/// 1. 创建 Observation: Observation.createNotStarted(name, registry)
/// 2. 添加标签: observation.lowCardinalityKeyValue(key, value)
/// 3. 启动 Observation: observation.start()
/// 4. 执行业务逻辑: invocation.proceed() / execution.execute() / job.run()
/// 5. 记录结果: observation.lowCardinalityKeyValue("outcome", result)
/// 6. 异常处理: observation.error(exception)
/// 7. 停止 Observation: observation.stop()（finally 块中）
/// ```
///
/// ## 配置示例
///
/// ```yaml
/// patra:
///   observability:
///     enabled: true
///     # 拦截器会自动注册到对应的扩展点
///     # 无需额外配置
/// ```
///
/// ## 标签规范
///
/// - **低基数标签**: 使用 lowCardinalityKeyValue() 添加（如 http.method、job.status）
/// - **高基数标签**: 避免使用（如 userId、requestId），已由 HighCardinalityMeterFilter 过滤
/// - **命名规范**: 使用点分隔符（如 http.method、job.name）
///
/// ## 与 ObservationHandler 的协作
///
/// 完整的可观测性流程：
///
/// ```
/// 1. Interceptor 创建 Observation → 添加标签 → 启动
/// 2. Observation 事件触发 → ObservationFilter 处理（脱敏、添加公共标签）
/// 3. Observation 事件触发 → ObservationHandler 处理（日志、性能监控）
/// 4. Observation 转换为 Meter → MeterFilter 处理（命名规范、过滤高基数标签）
/// 5. Meter 注册 → MeterRegistry 收集指标
/// 6. 指标导出 → SkyWalking/Prometheus
/// ```
///
/// ## 使用场景
///
/// - **开发环境**: 调试和理解应用行为
/// - **生产环境**: 监控关键业务流程和性能
/// - **故障排查**: 通过分布式追踪定位问题
/// - **性能优化**: 识别慢操作和瓶颈
///
///
/// @author Jobs
/// @since 1.0.0
package com.patra.starter.observability.interceptor;
