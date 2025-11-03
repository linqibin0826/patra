package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import java.util.EnumSet;
import java.util.Set;

/**
 * Outbox 发布异常。
 *
 * <p>触发场景:在 Outbox 消息发布到消息中间件(如 RocketMQ)时失败。
 *
 * <p>包含 {@link Reason} 枚举以区分可重试和不可重试的场景:
 *
 * <ul>
 *   <li>{@link Reason#CHANNEL_NOT_ALLOWED}: 通道不允许(配置错误,<b>致命错误</b>,不可重试)
 *   <li>{@link Reason#HEADERS_INVALID}: 消息头无效(<b>致命错误</b>,不可重试)
 *   <li>{@link Reason#SEND_FAILED}: 发送失败(网络问题等,<b>可重试</b>)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class OutboxPublishException extends OutboxRelayExecutionException {

  private final Reason reason;

  /**
   * 构造 Outbox 发布异常。
   *
   * @param reason 失败原因
   * @param message 描述性消息
   */
  public OutboxPublishException(Reason reason, String message) {
    super(message, null);
    this.reason = reason;
  }

  /**
   * 构造 Outbox 发布异常并附带底层原因。
   *
   * @param reason 失败原因
   * @param message 描述性消息
   * @param cause 底层异常
   */
  public OutboxPublishException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  /**
   * 获取失败原因。
   *
   * @return 失败原因枚举
   */
  public Reason getReason() {
    return reason;
  }

  @Override
  public Set<ErrorTrait> getErrorTraits() {
    if (reason == Reason.CHANNEL_NOT_ALLOWED || reason == Reason.HEADERS_INVALID) {
      return EnumSet.of(ErrorTrait.RULE_VIOLATION);
    }
    return EnumSet.of(ErrorTrait.DEP_UNAVAILABLE);
  }

  /**
   * 发布失败的根因分类。
   *
   * <p>用于区分致命错误(不可重试)和临时性错误(可重试)。
   */
  public enum Reason {
    /** 通道不允许(配置错误,致命)。 */
    CHANNEL_NOT_ALLOWED(true),
    /** 消息头无效(格式错误,致命)。 */
    HEADERS_INVALID(true),
    /** 发送失败(网络问题等,可重试)。 */
    SEND_FAILED(false);

    private final boolean fatal;

    Reason(boolean fatal) {
      this.fatal = fatal;
    }

    /**
     * 判断是否为致命错误(不可重试)。
     *
     * @return 如果是致命错误返回 true
     */
    public boolean isFatal() {
      return fatal;
    }
  }
}
