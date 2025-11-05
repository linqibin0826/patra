/**
 * Feign 错误处理包（根包）。
 *
 * <p>提供基于 RFC 7807 ProblemDetail 的 Feign 错误处理框架，支持智能解码、优雅降级和可观测性。
 *
 * <h2>核心能力</h2>
 *
 * <ul>
 *   <li><b>ProblemDetail 解码</b> - 优先解析 RFC 7807 格式的错误响应
 *   <li><b>宽容模式</b> - 非 ProblemDetail 响应时优雅降级，避免 FeignException
 *   <li><b>跟踪传播</b> - 自动提取和传播分布式跟踪标识符
 *   <li><b>可观测性</b> - 集成 Micrometer 记录解码性能和错误指标
 * </ul>
 *
 * <h2>子包结构</h2>
 *
 * <ul>
 *   <li>{@code config} - 自动配置和属性绑定
 *   <li>{@code decoder} - ErrorDecoder 实现
 *   <li>{@code exception} - 自定义异常类型
 *   <li>{@code interceptor} - 请求拦截器（TraceId 传播）
 *   <li>{@code observation} - 可观测性和指标记录
 *   <li>{@code util} - 错误处理辅助工具
 * </ul>
 *
 * <h2>工作流程</h2>
 *
 * <pre>
 * Feign 调用远程服务
 * ├── 成功 → 正常返回
 * └── 失败（4xx/5xx）
 *     ├── ProblemDetailErrorDecoder 解码
 *     │   ├── Content-Type: application/problem+json？
 *     │   │   ├── 是 → 解析为 ProblemDetail
 *     │   │   │   ├── 提取 errorCode、traceId、extensions
 *     │   │   │   └── 抛出 RemoteCallException(problemDetail)
 *     │   │   └── 否 → 检查宽容模式
 *     │   │       ├── tolerant=true → 包装为 RemoteCallException
 *     │   │       └── tolerant=false → 抛出 FeignException
 *     │   └── 记录指标：解码耗时、成功率、响应体大小
 *     └── 适配器层处理 RemoteCallException
 *         └── 转换为领域特定异常
 * </pre>
 *
 * <h2>配置示例</h2>
 *
 * <pre>{@code
 * patra:
 *   feign:
 *     error:
 *       enabled: true
 *       tolerant: true              # 宽容模式（推荐）
 *       max-error-body-size: 8192   # 最大错误响应体大小
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * try {
 *     ProvenanceResponse response = registryClient.getProvenance(code);
 * } catch (RemoteCallException ex) {
 *     // 检查业务错误代码
 *     if (ex.hasErrorCode() && "REG-1001".equals(ex.getErrorCode())) {
 *         throw new ProvenanceNotFoundException(code);
 *     }
 *
 *     // 获取跟踪 ID 用于日志关联
 *     if (ex.hasTraceId()) {
 *         log.error("Remote call failed, traceId={}", ex.getTraceId());
 *     }
 *
 *     // 检查 HTTP 状态
 *     if (ex.getHttpStatus() == 503) {
 *         throw new ServiceUnavailableException();
 *     }
 *
 *     // 访问 ProblemDetail 扩展字段
 *     String retryAfter = ex.getExtension("retryAfter", String.class);
 * }
 * }</pre>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><b>优雅降级</b> - 宽容模式确保非标准响应不会导致解析失败
 *   <li><b>可观测性优先</b> - 记录解码耗时、TraceId 提取成功率等关键指标
 *   <li><b>上下文传递</b> - 保留下游错误的完整上下文（errorCode、extensions）
 *   <li><b>适配器隔离</b> - 适配器层负责转换，领域层不直接依赖 Feign 异常
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.feign.error;
