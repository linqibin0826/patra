/// Feign 错误处理包（根包）。
///
/// 提供基于 RFC 7807 ProblemDetail 的 Feign 错误处理框架，支持智能解码、优雅降级和可观测性。
///
/// ## 核心能力
///
/// - **ProblemDetail 解码** - 优先解析 RFC 7807 格式的错误响应
///   - **宽容模式** - 非 ProblemDetail 响应时优雅降级，避免 FeignException
///   - **跟踪传播** - 自动提取和传播分布式跟踪标识符
///   - **可观测性** - 集成 Micrometer 记录解码性能和错误指标
///
/// ## 子包结构
///
/// - `config` - 自动配置和属性绑定
///   - `decoder` - ErrorDecoder 实现
///   - `exception` - 自定义异常类型
///   - `interceptor` - 请求拦截器（TraceId 传播）
///   - `observation` - 可观测性和指标记录
///   - `util` - 错误处理辅助工具
///
/// ## 工作流程
///
/// ```
///
/// Feign 调用远程服务
/// ├── 成功 → 正常返回
/// └── 失败（4xx/5xx）
///     ├── ProblemDetailErrorDecoder 解码
///     │   ├── Content-Type: application/problem+json？
///     │   │   ├── 是 → 解析为 ProblemDetail
///     │   │   │   ├── 提取 errorCode、traceId、extensions
///     │   │   │   └── 抛出 RemoteCallException(problemDetail)
///     │   │   └── 否 → 检查宽容模式
///     │   │       ├── tolerant=true → 包装为 RemoteCallException
///     │   │       └── tolerant=false → 抛出 FeignException
///     │   └── 记录指标：解码耗时、成功率、响应体大小
///     └── 适配器层处理 RemoteCallException
///         └── 转换为领域特定异常
///
/// ```
///
/// ## 配置示例
///
/// ```java
/// patra:
///   feign:
///     error:
///       enabled: true
///       tolerant: true              # 宽容模式（推荐）
///       max-error-body-size: 8192   # 最大错误响应体大小
/// ```
///
/// ## 使用示例
///
/// ```java
/// try {
///     ProvenanceResponse response = registryClient.getProvenance(code); catch (RemoteCallException
// ex) {
///     // 检查业务错误代码
///     if (ex.hasErrorCode() && "REG-1001".equals(ex.getErrorCode())) {
///         throw new ProvenanceNotFoundException(code);
///
///     // 获取跟踪 ID 用于日志关联
///     if (ex.hasTraceId()) {
///         log.error("Remote call failed, traceId={", ex.getTraceId());
///
///     // 检查 HTTP 状态
///     if (ex.getHttpStatus() == 503) {
///         throw new ServiceUnavailableException();
///
///     // 访问 ProblemDetail 扩展字段
///     String retryAfter = ex.getExtension("retryAfter", String.class);
/// ```
///
/// ## 设计原则
///
/// - **优雅降级** - 宽容模式确保非标准响应不会导致解析失败
///   - **可观测性优先** - 记录解码耗时、TraceId 提取成功率等关键指标
///   - **上下文传递** - 保留下游错误的完整上下文（errorCode、extensions）
///   - **适配器隔离** - 适配器层负责转换，领域层不直接依赖 Feign 异常
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.feign.error;
