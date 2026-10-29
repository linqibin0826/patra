package dev.patra.identity.domain.session.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/// 再认证规则：敏感操作要求会话里的最近一次密码验证在窗口内；再认证换令牌带一个宽限期。
///
/// 宽限期解决并发再认证：两个标签页几乎同时提交，第二次换令牌会让第一次响应里的令牌作废。
/// `auth_at` 距今不足宽限期时不再换令牌，响应里没有新令牌，门户保持现有 Cookie（它最终是第一次换出的那个）。
///
/// @param window 窗口长度（10 分钟）
/// @param rotationGrace 换令牌的宽限期（10 秒）；为零则每次都换
public record ReauthenticationPolicy(Duration window, Duration rotationGrace) {
  /// 窗口必须为正，宽限期不能为负。
  public ReauthenticationPolicy {
    Objects.requireNonNull(window, "window 不能为 null");
    Objects.requireNonNull(rotationGrace, "rotationGrace 不能为 null");
    if (window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window 必须是正数，实际值: " + window);
    }
    if (rotationGrace.isNegative()) {
      throw new IllegalArgumentException("rotationGrace 不能为负，实际值: " + rotationGrace);
    }
  }

  /// 创建规则。
  ///
  /// @param window 窗口长度
  /// @param rotationGrace 换令牌的宽限期
  /// @return 规则
  public static ReauthenticationPolicy of(Duration window, Duration rotationGrace) {
    return new ReauthenticationPolicy(window, rotationGrace);
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
