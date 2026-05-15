package dev.linqibin.patra.ingest.app.usecase.relay.coordinator;

import dev.linqibin.patra.ingest.domain.model.entity.OutboxMessage;
import dev.linqibin.patra.ingest.domain.model.vo.relay.RelayPlan;
import dev.linqibin.patra.ingest.domain.policy.RelayErrorClassifier;
import dev.linqibin.patra.ingest.domain.policy.RelayErrorClassifier.RelayErrorKind;
import dev.linqibin.patra.ingest.domain.policy.RelayRetryPolicy;
import dev.linqibin.patra.ingest.domain.port.OutboxPublisherPort;
import dev.linqibin.patra.ingest.domain.port.OutboxRelayRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 中继发布协调器 - 管理消息发布和状态转换
///
/// ### 职责
///
/// - 通过 OutboxPublisherPort 将 Outbox 消息发布到下游代理
///   - 使用 RelayErrorClassifier 分类发布错误 (FATAL vs TRANSIENT)
///   - 使用带指数退避的 RelayRetryPolicy 计算重试时机
///   - 驱动状态转换 (PUBLISHING → PUBLISHED/FAILED)
///
/// ### 发布流程
///
/// ### 状态转换
///
/// ```
///
/// PUBLISHING → PUBLISHED  (成功,释放租约)
/// PUBLISHING → FAILED     (暂时性错误,已调度重试,释放租约)
/// PUBLISHING → DEAD       (致命错误或达到最大重试,释放租约)
///
/// ```
///
/// ### 重试逻辑
///
/// - 通过 RelayRetryPolicy 使用指数退避
///   - 遵守来自 RelayPlan 的 maxAttempts
///   - 计算 nextRetryAt = now + backoff(attemptNumber)
///
/// ### 日志策略
///
/// - DEBUG: 成功发布 (messageId, channel, duration)
///   - WARN: 暂时性错误并重试 (messageId, attemptNumber, nextRetryAt, errorCode)
///   - ERROR: 致命错误或达到最大重试 (messageId, totalAttempts, errorCode, exception)
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayPublishCoordinator {

  private final OutboxRelayRepository relayStore;
  private final OutboxPublisherPort publisherPort;
  private final RelayErrorClassifier errorClassifier;
  private final RelayRetryPolicy retryPolicy;

  /// 发布单条 Outbox 消息并处理结果
  ///
  /// 此方法编排整个发布生命周期:
  ///
  /// @param message 已获取租约的 Outbox 消息
  /// @param plan 包含重试策略参数的中继计划
  /// @return 指示成功/重试/失败结果的中继结果
  public RelayResult publish(OutboxMessage message, RelayPlan plan) {
    Instant startTime = Instant.now();
    int attemptNumber = message.computeNextAttempt();

    try {
      // 尝试发布到下游代理
      publisherPort.publish(message, plan);

      // 成功: 标记为 PUBLISHED 并释放租约
      handleSuccess(message);

      if (log.isDebugEnabled()) {
        long durationMs = Duration.between(startTime, Instant.now()).toMillis();
        log.debug(
            "发布成功: messageId={}, channel={}, attemptNumber={}, durationMs={}",
            message.getId(),
            message.getChannel(),
            attemptNumber,
            durationMs);
      }

      return RelayResult.success(message.getId(), attemptNumber);

    } catch (Exception exception) {
      // 分类错误以确定重试资格
      RelayErrorKind errorKind = errorClassifier.classify(exception);
      boolean canRetry = message.canRetry(plan.maxAttempts());
      String errorCode = extractErrorCode(exception);
      String errorMessage = truncateMessage(exception.getMessage(), 500);

      if (errorKind == RelayErrorKind.TRANSIENT && canRetry) {
        // 暂时性错误且剩余重试预算
        handleRetry(message, plan, exception);

        Duration backoff = retryPolicy.computeDelay(attemptNumber);
        Instant nextRetryAt = Instant.now().plus(backoff);

        log.warn(
            "发布失败,出现暂时性错误,将重试: messageId={}, channel={}, attemptNumber={}/{}, nextRetryAt={}, errorCode={}, errorMessage={}",
            message.getId(),
            message.getChannel(),
            attemptNumber,
            plan.maxAttempts(),
            nextRetryAt,
            errorCode,
            truncateMessage(exception.getMessage(), 200));

        return RelayResult.deferred(
            message.getId(), attemptNumber, nextRetryAt, errorCode, errorMessage);

      } else {
        // 致命错误或达到最大重试次数
        handleFailed(message, plan, exception);

        String reason =
            errorKind == RelayErrorKind.FATAL ? "致命错误" : "达到最大重试次数 (" + plan.maxAttempts() + ")";

        log.error(
            "发布永久失败: messageId={}, channel={}, attemptNumber={}, reason={}, errorCode={}, errorMessage={}",
            message.getId(),
            message.getChannel(),
            attemptNumber,
            reason,
            errorCode,
            truncateMessage(exception.getMessage(), 200),
            exception);

        return RelayResult.failed(message.getId(), attemptNumber, errorCode, errorMessage);
      }
    }
  }

  /// 通过将消息标记为 PUBLISHED 来处理成功发布
  ///
  /// 状态转换: PUBLISHING → PUBLISHED
  ///
  /// 数据库变更:
  ///
  /// - status_code = 'PUBLISHED'
  ///   - published_at = NOW(6)
  ///   - pub_lease_owner = NULL (租约已释放)
  ///   - pub_leased_until = NULL (租约已释放)
  ///   - error_code = NULL (已清除)
  ///   - error_msg = NULL (已清除)
  ///   - next_retry_at = NULL (已清除)
  ///   - version = version + 1
  ///
  /// @param message 成功发布的 Outbox 消息
  private void handleSuccess(OutboxMessage message) {
    relayStore.markPublished(message.getId(), message.getVersion());
  }

  /// 通过调度重试来处理暂时性发布错误
  ///
  /// 状态转换: PUBLISHING → FAILED (但设置了 next_retry_at)
  ///
  /// 数据库变更:
  ///
  /// - status_code = 'FAILED' (但将根据 next_retry_at 重试)
  ///   - retry_count = 递增
  ///   - next_retry_at = 使用指数退避计算
  ///   - error_code = 从异常中提取
  ///   - error_msg = 截断的异常消息
  ///   - pub_lease_owner = NULL (租约已释放)
  ///   - pub_leased_until = NULL (租约已释放)
  ///   - version = version + 1
  ///
  /// @param message 失败并出现暂时性错误的 Outbox 消息
  /// @param plan 包含重试策略参数的中继计划
  /// @param exception 发布期间发生的异常
  private void handleRetry(OutboxMessage message, RelayPlan plan, Exception exception) {
    int attemptNumber = message.computeNextAttempt();
    Duration backoff = retryPolicy.computeDelay(attemptNumber);
    Instant nextRetryAt = Instant.now().plus(backoff);

    relayStore.markDeferred(
        message.getId(),
        message.getVersion(),
        attemptNumber, // 已更新的重试计数
        nextRetryAt,
        extractErrorCode(exception),
        truncateMessage(exception.getMessage(), 500));
  }

  /// 处理致命发布错误或达到最大重试
  ///
  /// 状态转换: PUBLISHING → DEAD
  ///
  /// 数据库变更:
  ///
  /// - status_code = 'DEAD' (终止状态,不再重试)
  ///   - retry_count = 最终尝试计数
  ///   - error_code = 从异常中提取
  ///   - error_msg = 截断的异常消息
  ///   - next_retry_at = NULL (不重试)
  ///   - pub_lease_owner = NULL (租约已释放)
  ///   - pub_leased_until = NULL (租约已释放)
  ///   - version = version + 1
  ///
  /// @param message 永久失败的 Outbox 消息
  /// @param plan 中继计划 (用于上下文)
  /// @param exception 发布期间发生的异常
  private void handleFailed(OutboxMessage message, RelayPlan plan, Exception exception) {
    int attemptNumber = message.computeNextAttempt();

    relayStore.markFailed(
        message.getId(),
        message.getVersion(),
        attemptNumber, // 最终重试计数
        extractErrorCode(exception),
        truncateMessage(exception.getMessage(), 500));
  }

  /// 从异常类名提取简单的错误代码
  ///
  /// 示例:
  ///
  /// - TimeoutException → TIMEOUT_EXCEPTION
  ///   - BrokerUnavailableException → BROKER_UNAVAILABLE_EXCEPTION
  ///
  /// @param exception 要从中提取代码的异常
  /// @return 从异常类名派生的错误代码
  private String extractErrorCode(Exception exception) {
    return exception.getClass().getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }

  /// 将错误消息截断到指定的最大长度
  ///
  /// 防止数据库 error_msg 列中的过度存储使用
  ///
  /// @param message 要截断的错误消息
  /// @param maxLength 允许的最大长度
  /// @return 如果截断则带有 "..." 后缀的截断消息
  private String truncateMessage(String message, int maxLength) {
    if (message == null) {
      return null;
    }
    if (message.length() <= maxLength) {
      return message;
    }
    return message.substring(0, maxLength - 3) + "...";
  }

  /// 中继发布尝试的结果
  ///
  /// 三种可能的结果:
  ///
  /// - SUCCESS: 消息已发布并标记为 PUBLISHED
  ///   - DEFERRED: 暂时性错误,已调度重试
  ///   - FAILED: 致命错误或达到最大重试,标记为 DEAD
  ///
  public record RelayResult(
      Long messageId,
      RelayOutcome outcome,
      int attemptNumber,
      Instant nextRetryAt,
      String errorCode,
      String errorMessage) {

    public static RelayResult success(Long messageId, int attemptNumber) {
      return new RelayResult(messageId, RelayOutcome.SUCCESS, attemptNumber, null, null, null);
    }

    public static RelayResult deferred(
        Long messageId,
        int attemptNumber,
        Instant nextRetryAt,
        String errorCode,
        String errorMessage) {
      return new RelayResult(
          messageId, RelayOutcome.DEFERRED, attemptNumber, nextRetryAt, errorCode, errorMessage);
    }

    public static RelayResult failed(
        Long messageId, int attemptNumber, String errorCode, String errorMessage) {
      return new RelayResult(
          messageId, RelayOutcome.FAILED, attemptNumber, null, errorCode, errorMessage);
    }

    public boolean isSuccess() {
      return outcome == RelayOutcome.SUCCESS;
    }

    public boolean isDeferred() {
      return outcome == RelayOutcome.DEFERRED;
    }

    public boolean isFailed() {
      return outcome == RelayOutcome.FAILED;
    }

    public enum RelayOutcome {
      SUCCESS,
      DEFERRED,
      FAILED
    }
  }
}
