/// 中继错误分类器包。
///
/// 本包提供中继失败错误的分类逻辑，区分可重试错误和不可重试错误。
///
/// ## 职责
///
/// - 分析中继失败的异常类型
///   - 判断错误是否可重试
///   - 提供错误处理策略（重试、放弃）
///
/// ## 核心组件
///
/// - `RelayErrorClassifierImpl` - 错误分类器实现
///
/// ## 错误分类
///
/// ### 可重试错误
///
/// - **网络超时**: `RemotingTooMuchRequestException`、`RemotingTimeoutException`
///   - **Broker 繁忙**: `MQBrokerException`（特定错误码）
///   - **系统繁忙**: `SystemBusyException`
///   - **处理策略**: 恢复为 PENDING，等待下次中继重试
///
/// ### 不可重试错误
///
/// - **消息格式错误**: `InvalidMessageException`
///   - **Topic 不存在**: `TopicNotExistException`
///   - **权限错误**: `UnauthorizedException`
///   - **处理策略**: 标记为 FAILED，不再重试，记录错误详情
///
/// ## 使用示例
///
/// ```java
/// @Component
/// public class RelayErrorClassifierImpl {
///
///     public boolean isRetryable(Exception exception) {
///         // 1. 网络超时（可重试）
///         if (exception instanceof RemotingTimeoutException) {
///             return true;
///
///         // 2. Broker 繁忙（可重试）
///         if (exception instanceof MQBrokerException) {
///             var mqEx = (MQBrokerException) exception;
///             var errorCode = mqEx.getResponseCode();
///             return errorCode == ResponseCode.SYSTEM_BUSY
///                 || errorCode == ResponseCode.TOO_MANY_REQUESTS;
///
///         // 3. 消息格式错误（不可重试）
///         if (exception instanceof InvalidMessageException) {
///             return false;
///
///         // 4. Topic 不存在（不可重试）
///         if (exception instanceof TopicNotExistException) {
///             return false;
///
///         // 5. 未知错误（默认可重试）
///         return true;
///
///     public String classifyError(Exception exception) {
///         if (exception instanceof RemotingTimeoutException) {
///             return "NETWORK_TIMEOUT";
///         if (exception instanceof MQBrokerException) {
///             return "BROKER_ERROR";
///         if (exception instanceof InvalidMessageException) {
///             return "INVALID_MESSAGE";
///         return "UNKNOWN";
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.relay.classifier;
