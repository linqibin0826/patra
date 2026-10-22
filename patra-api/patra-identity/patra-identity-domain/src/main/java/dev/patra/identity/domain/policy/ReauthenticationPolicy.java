package dev.patra.identity.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/// 再认证规则：敏感操作要求会话里的最近一次密码验证在窗口内。
///
/// @param window 窗口长度（10 分钟）
public record ReauthenticationPolicy(Duration window) {

  /// 窗口必须为正。
  public ReauthenticationPolicy {
    Objects.requireNonNull(window, "window 不能为 null");
    if (window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window 必须是正数，实际值: " + window);
    }
  }

  /// 创建规则。
  ///
  /// @param window 窗口长度
  /// @return 规则
  public static ReauthenticationPolicy of(Duration window) {
    return new ReauthenticationPolicy(window);
  }

  /// 最近一次密码验证是否还在窗口内：`now < authenticatedAt + window`，满窗口当刻不算。
  ///
  /// @param authenticatedAt 最近一次用密码证明身份的时间
  /// @param now 当前时间
  /// @return 在窗口内为 `true`
  public boolean isRecent(Instant authenticatedAt, Instant now) {
    Objects.requireNonNull(authenticatedAt, "authenticatedAt 不能为 null");
    Objects.requireNonNull(now, "now 不能为 null");
    return now.isBefore(authenticatedAt.plus(window));
  }
}
