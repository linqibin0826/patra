/**
 * 可观测性拦截器（插件式架构）。
 *
 * <p>本包提供了可观测性功能的拦截器实现，遵循插件式架构设计原则。
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link com.patra.starter.observability.interceptor.ObservationResolutionInterceptor} - 错误解析可观测性拦截器</li>
 *   <li>{@link com.patra.starter.observability.interceptor.RestClientObservationInterceptor} - REST 客户端可观测性拦截器</li>
 *   <li>{@link com.patra.starter.observability.interceptor.BatchObservationJobListener} - 批处理任务可观测性监听器</li>
 * </ul>
 *
 * <h2>插件式架构设计</h2>
 * <p>遵循依赖倒置原则（DIP），patra-starter-observability 通过实现其他 Starter 提供的扩展点来集成可观测性功能：
 * <pre>
 * ┌────────────────────────────────────────┐
 * │   patra-starter-core（扩展点定义）    │
 * │   ├─ ResolutionInterceptor             │
 * │   └─ ErrorResolutionPipeline           │
 * └────────────────────────────────────────┘
 *              ▲
 *              │ 实现
 *              │
 * ┌────────────────────────────────────────┐
 * │ patra-starter-observability（插件）    │
 * │   └─ ObservationResolutionInterceptor  │
 * └────────────────────────────────────────┘
 *
 * ┌────────────────────────────────────────┐
 * │ patra-starter-rest-client（扩展点）    │
 * │   └─ ClientHttpRequestInterceptor      │
 * └────────────────────────────────────────┘
 *              ▲
 *              │ 实现
 *              │
 * ┌────────────────────────────────────────┐
 * │ patra-starter-observability（插件）    │
 * │   └─ RestClientObservationInterceptor  │
 * └────────────────────────────────────────┘
 *
 * ┌────────────────────────────────────────┐
 * │ patra-starter-batch（扩展点）          │
 * │   └─ JobExecutionListener              │
 * └────────────────────────────────────────┘
 *              ▲
 *              │ 实现
 *              │
 * ┌────────────────────────────────────────┐
 * │ patra-starter-observability（插件）    │
 * │   └─ BatchObservationJobListener       │
 * └────────────────────────────────────────┘
 * </pre>
 *
 * <h2>设计优势</h2>
 * <ul>
 *   <li><strong>依赖倒置</strong>: 核心 Starter 不依赖可观测性 Starter，保持纯净</li>
 *   <li><strong>可插拔</strong>: 可以独立启用/禁用可观测性功能</li>
 *   <li><strong>解耦</strong>: 各个 Starter 之间没有直接依赖，通过接口通信</li>
 *   <li><strong>扩展性</strong>: 未来可以添加更多可观测性插件（如安全、审计等）</li>
 * </ul>
 *
 * <h2>拦截器功能</h2>
 *
 * <h3>ObservationResolutionInterceptor</h3>
 * <p>为错误解析流程添加可观测性支持：
 * <ul>
 *   <li>记录错误类型、错误类</li>
 *   <li>记录解析是否成功</li>
 *   <li>自动集成分布式追踪</li>
 *   <li>生成错误解析指标</li>
 * </ul>
 *
 * <h3>RestClientObservationInterceptor</h3>
 * <p>为 REST 客户端请求添加可观测性支持：
 * <ul>
 *   <li>记录 HTTP 方法、URI、状态码</li>
 *   <li>记录请求结果（SUCCESS、CLIENT_ERROR、SERVER_ERROR）</li>
 *   <li>自动集成分布式追踪（传播 TraceId/SpanId）</li>
 *   <li>生成 HTTP 客户端指标</li>
 * </ul>
 *
 * <h3>BatchObservationJobListener</h3>
 * <p>为批处理任务添加可观测性支持：
 * <ul>
 *   <li>记录任务名称、执行 ID、状态</li>
 *   <li>记录任务执行时间</li>
 *   <li>自动集成分布式追踪（跨步骤追踪）</li>
 *   <li>生成批处理任务指标</li>
 * </ul>
 *
 * <h2>Observation 生命周期</h2>
 * <p>所有拦截器遵循统一的 Observation 生命周期管理模式：
 * <pre>
 * 1. 创建 Observation: Observation.createNotStarted(name, registry)
 * 2. 添加标签: observation.lowCardinalityKeyValue(key, value)
 * 3. 启动 Observation: observation.start()
 * 4. 执行业务逻辑: invocation.proceed() / execution.execute() / job.run()
 * 5. 记录结果: observation.lowCardinalityKeyValue("outcome", result)
 * 6. 异常处理: observation.error(exception)
 * 7. 停止 Observation: observation.stop()（finally 块中）
 * </pre>
 *
 * <h2>配置示例</h2>
 * <pre>
 * patra:
 *   observability:
 *     enabled: true
 *     # 拦截器会自动注册到对应的扩展点
 *     # 无需额外配置
 * </pre>
 *
 * <h2>标签规范</h2>
 * <ul>
 *   <li><strong>低基数标签</strong>: 使用 lowCardinalityKeyValue() 添加（如 http.method、job.status）</li>
 *   <li><strong>高基数标签</strong>: 避免使用（如 userId、requestId），已由 HighCardinalityMeterFilter 过滤</li>
 *   <li><strong>命名规范</strong>: 使用点分隔符（如 http.method、job.name）</li>
 * </ul>
 *
 * <h2>与 ObservationHandler 的协作</h2>
 * <p>完整的可观测性流程：
 * <pre>
 * 1. Interceptor 创建 Observation → 添加标签 → 启动
 * 2. Observation 事件触发 → ObservationFilter 处理（脱敏、添加公共标签）
 * 3. Observation 事件触发 → ObservationHandler 处理（日志、性能监控）
 * 4. Observation 转换为 Meter → MeterFilter 处理（命名规范、过滤高基数标签）
 * 5. Meter 注册 → MeterRegistry 收集指标
 * 6. 指标导出 → SkyWalking/Prometheus
 * </pre>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li><strong>开发环境</strong>: 调试和理解应用行为</li>
 *   <li><strong>生产环境</strong>: 监控关键业务流程和性能</li>
 *   <li><strong>故障排查</strong>: 通过分布式追踪定位问题</li>
 *   <li><strong>性能优化</strong>: 识别慢操作和瓶颈</li>
 * </ul>
 *
 * @author Jobs
 * @since 1.0.0
 */
package com.patra.starter.observability.interceptor;
