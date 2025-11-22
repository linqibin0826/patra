/// Feign 错误处理工具包。
///
/// 提供处理 {@link com.patra.starter.feign.error.exception.RemoteCallException} 的辅助工具。
///
/// ## 职责
///
/// - 简化 {@link com.patra.starter.feign.error.exception.RemoteCallException} 的检查和处理
///   - 提供错误代码匹配、HTTP 状态判断等常用方法
///   - 支持 ProblemDetail 扩展字段的类型安全访问
///
/// ## 核心组件
///
/// - {@link RemoteErrorHelper} - 远程错误处理辅助类
///
/// ## 使用示例
///
/// ```java
/// try {
///     ProvenanceResponse response = registryClient.getProvenance(code); catch (RemoteCallException
// ex) {
///     // 检查错误代码
///     if (RemoteErrorHelper.hasErrorCode(ex, "REG-1001")) {
///         throw new ProvenanceNotFoundException(code);
///
///     // 检查 HTTP 状态
///     if (RemoteErrorHelper.isClientError(ex)) {
///         log.warn("Client error: {", ex.getMessage());
///
///     // 获取扩展字段
///     String retryAfter = RemoteErrorHelper.getExtension(ex, "retryAfter", String.class);
///
///     // 格式化错误信息
///     String errorInfo = RemoteErrorHelper.formatError(ex);
///     log.error("Remote call failed: {", errorInfo);
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.feign.error.util;
