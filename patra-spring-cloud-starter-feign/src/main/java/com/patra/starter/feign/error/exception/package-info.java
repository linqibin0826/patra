/**
 * Feign 错误异常包。
 *
 * <p>定义 Feign 客户端调用失败时抛出的异常类型。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>封装下游服务返回的错误信息
 *   <li>传递业务错误代码和 HTTP 状态
 *   <li>保留 ProblemDetail 扩展属性
 *   <li>支持跟踪标识符传播
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link RemoteCallException} - 远程调用异常
 * </ul>
 *
 * <h2>异常结构</h2>
 *
 * <pre>
 * RemoteCallException
 * ├── errorCode: String           # 业务错误代码（如 "REG-1001"）
 * ├── httpStatus: int             # HTTP 状态码（如 404）
 * ├── methodKey: String           # Feign 方法键（如 "RegistryClient#getProvenance"）
 * ├── traceId: String             # 分布式跟踪 ID
 * ├── extensions: Map             # ProblemDetail 扩展字段
 * └── message: String             # 错误消息
 * </pre>
 *
 * <h2>构造方式</h2>
 *
 * <ul>
 *   <li><b>从 ProblemDetail</b> - 完整的错误上下文（推荐）
 *   <li><b>从 HTTP 响应</b> - 宽容模式降级时使用
 *   <li><b>手动构造</b> - 测试或特殊场景
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * try {
 *     ProvenanceResponse response = registryClient.getProvenance(code);
 * } catch (RemoteCallException ex) {
 *     // 检查业务错误代码
 *     if (ex.hasErrorCode()) {
 *         String errorCode = ex.getErrorCode(); // "REG-1001"
 *         switch (errorCode) {
 *             case "REG-1001" -> throw new ProvenanceNotFoundException(code);
 *             case "REG-1002" -> throw new InvalidProvenanceException(code);
 *         }
 *     }
 *
 *     // 获取 HTTP 状态
 *     int status = ex.getHttpStatus(); // 404
 *
 *     // 获取跟踪 ID
 *     if (ex.hasTraceId()) {
 *         log.error("Remote call failed, traceId={}", ex.getTraceId());
 *     }
 *
 *     // 访问扩展字段
 *     String retryAfter = ex.getExtension("retryAfter", String.class);
 *     Map<String, Object> allExtensions = ex.getAllExtensions();
 * }
 * }</pre>
 *
 * <h2>适配器层转换示例</h2>
 *
 * <pre>{@code
 * @Component
 * public class RegistryRpcAdapter {
 *     private final RegistryRpcClient client;
 *
 *     public Provenance getProvenance(String code) {
 *         try {
 *             ProvenanceResponse response = client.getProvenance(code);
 *             return converter.toDomain(response);
 *         } catch (RemoteCallException ex) {
 *             // 转换为领域特定异常
 *             if (ex.getHttpStatus() == 404 || "REG-1001".equals(ex.getErrorCode())) {
 *                 throw new ProvenanceNotFoundException(code);
 *             }
 *             throw new RegistryClientException("Failed to fetch provenance", ex);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><b>仅用于适配器层</b> - 不暴露到领域层
 *   <li><b>保留完整上下文</b> - 包含所有 ProblemDetail 信息
 *   <li><b>支持类型安全访问</b> - 提供泛型方法访问扩展字段
 *   <li><b>不可变对象</b> - 扩展字段返回不可变副本
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.feign.error.exception;
