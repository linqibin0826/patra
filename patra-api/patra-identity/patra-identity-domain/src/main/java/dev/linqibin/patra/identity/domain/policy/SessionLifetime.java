package dev.linqibin.patra.identity.domain.policy;

import java.time.Duration;
import java.util.Objects;

/// 一种客户端的会话有效期：不活跃过期和绝对过期。
///
/// @param idle 不活跃多久过期，正数
/// @param absolute 从登录起多久必定过期，不短于 `idle`
public record SessionLifetime(Duration idle, Duration absolute) {

  /// 校验两个时长。
  public SessionLifetime {
    Objects.requireNonNull(idle, "idle 不能为 null");
    Objects.requireNonNull(absolute, "absolute 不能为 null");
    if (idle.isZero() || idle.isNegative()) {
      throw new IllegalArgumentException("idle 必须是正数");
    }
    if (absolute.isZero() || absolute.isNegative()) {
      throw new IllegalArgumentException("absolute 必须是正数");
    }
    if (idle.compareTo(absolute) > 0) {
      throw new IllegalArgumentException("idle 不能长于 absolute");
    }
  }

  /// 创建有效期。
  ///
  /// @param idle 不活跃过期
  /// @param absolute 绝对过期
  /// @return 有效期
  public static SessionLifetime of(Duration idle, Duration absolute) {
    return new SessionLifetime(idle, absolute);
  }
}
