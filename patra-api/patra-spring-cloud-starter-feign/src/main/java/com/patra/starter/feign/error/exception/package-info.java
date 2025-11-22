/// Feign 错误异常包。
///
/// 定义 Feign 客户端调用失败时抛出的异常类型。
///
/// ## 职责
///
/// - 封装下游服务返回的错误信息
///   - 传递业务错误代码和 HTTP 状态
///   - 保留 ProblemDetail 扩展属性
///   - 支持跟踪标识符传播
///
/// ## 核心组件
///
/// - {@link RemoteCallException} - 远程调用异常
///
/// ## 异常结构
///
/// ```
///
/// RemoteCallException
/// ├── errorCode: String           # 业务错误代码（如 "REG-1001"）
/// ├── httpStatus: int             # HTTP 状态码（如 404）
/// ├── methodKey: String           # Feign 方法键（如 "RegistryClient#getProvenance"）
/// ├── traceId: String             # 分布式跟踪 ID
/// ├── extensions: Map             # ProblemDetail 扩展字段
/// └── message: String             # 错误消息
///
/// ```
///
/// ## 构造方式
///
/// - **从 ProblemDetail** - 完整的错误上下文（推荐）
///   - **从 HTTP 响应** - 宽容模式降级时使用
///   - **手动构造** - 测试或特殊场景
///
/// ## 使用示例
///
/// ```java
/// try {
///     ProvenanceResponse response = registryClient.getProvenance(code); catch (RemoteCallException
// ex) {
///     // 检查业务错误代码
///     if (ex.hasErrorCode()) {
///         String errorCode = ex.getErrorCode(); // "REG-1001"
///         switch (errorCode) {
///             case "REG-1001" -> throw new ProvenanceNotFoundException(code);
///             case "REG-1002" -> throw new InvalidProvenanceException(code);
///
///     // 获取 HTTP 状态
///     int status = ex.getHttpStatus(); // 404
///
///     // 获取跟踪 ID
///     if (ex.hasTraceId()) {
///         log.error("Remote call failed, traceId={", ex.getTraceId());
///
///     // 访问扩展字段
///     String retryAfter = ex.getExtension("retryAfter", String.class);
///     Map<String, Object> allExtensions = ex.getAllExtensions();
/// ```
///
/// ## 适配器层转换示例
///
/// ```java
/// @Component
/// public class RegistryRpcAdapter {
///     private final RegistryRpcClient client;
///
///     public Provenance getProvenance(String code) {
///         try {
///             ProvenanceResponse response = client.getProvenance(code);
///             return converter.toDomain(response); catch (RemoteCallException ex) {
///             // 转换为领域特定异常
///             if (ex.getHttpStatus() == 404 || "REG-1001".equals(ex.getErrorCode())) {
///                 throw new ProvenanceNotFoundException(code);
///             throw new RegistryClientException("Failed to fetch provenance", ex);
/// ```
///
/// ## 设计原则
///
/// - **仅用于适配器层** - 不暴露到领域层
///   - **保留完整上下文** - 包含所有 ProblemDetail 信息
///   - **支持类型安全访问** - 提供泛型方法访问扩展字段
///   - **不可变对象** - 扩展字段返回不可变副本
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.feign.error.exception;
