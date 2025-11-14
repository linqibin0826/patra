/**
 * 中继错误分类器包。
 *
 * <p>本包提供中继失败错误的分类逻辑，区分可重试错误和不可重试错误。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>分析中继失败的异常类型
 *   <li>判断错误是否可重试
 *   <li>提供错误处理策略（重试、放弃）
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code RelayErrorClassifierImpl} - 错误分类器实现
 * </ul>
 *
 * <h2>错误分类</h2>
 *
 * <h3>可重试错误</h3>
 *
 * <ul>
 *   <li><strong>网络超时</strong>: {@code RemotingTooMuchRequestException}、{@code
 *       RemotingTimeoutException}
 *   <li><strong>Broker 繁忙</strong>: {@code MQBrokerException}（特定错误码）
 *   <li><strong>系统繁忙</strong>: {@code SystemBusyException}
 *   <li><strong>处理策略</strong>: 恢复为 PENDING，等待下次中继重试
 * </ul>
 *
 * <h3>不可重试错误</h3>
 *
 * <ul>
 *   <li><strong>消息格式错误</strong>: {@code InvalidMessageException}
 *   <li><strong>Topic 不存在</strong>: {@code TopicNotExistException}
 *   <li><strong>权限错误</strong>: {@code UnauthorizedException}
 *   <li><strong>处理策略</strong>: 标记为 FAILED，不再重试，记录错误详情
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Component
 * public class RelayErrorClassifierImpl {
 *
 *     public boolean isRetryable(Exception exception) {
 *         // 1. 网络超时（可重试）
 *         if (exception instanceof RemotingTimeoutException) {
 *             return true;
 *         }
 *
 *         // 2. Broker 繁忙（可重试）
 *         if (exception instanceof MQBrokerException) {
 *             var mqEx = (MQBrokerException) exception;
 *             var errorCode = mqEx.getResponseCode();
 *             return errorCode == ResponseCode.SYSTEM_BUSY
 *                 || errorCode == ResponseCode.TOO_MANY_REQUESTS;
 *         }
 *
 *         // 3. 消息格式错误（不可重试）
 *         if (exception instanceof InvalidMessageException) {
 *             return false;
 *         }
 *
 *         // 4. Topic 不存在（不可重试）
 *         if (exception instanceof TopicNotExistException) {
 *             return false;
 *         }
 *
 *         // 5. 未知错误（默认可重试）
 *         return true;
 *     }
 *
 *     public String classifyError(Exception exception) {
 *         if (exception instanceof RemotingTimeoutException) {
 *             return "NETWORK_TIMEOUT";
 *         }
 *         if (exception instanceof MQBrokerException) {
 *             return "BROKER_ERROR";
 *         }
 *         if (exception instanceof InvalidMessageException) {
 *             return "INVALID_MESSAGE";
 *         }
 *         return "UNKNOWN";
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.relay.classifier;
