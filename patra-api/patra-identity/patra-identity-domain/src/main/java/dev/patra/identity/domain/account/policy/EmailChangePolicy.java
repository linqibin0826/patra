package dev.patra.identity.domain.account.policy;

import java.time.Duration;

/// 改邮箱的限流参数：每账号每天的申请上限。
///
/// @param perAccountDaily 每账号每天的申请上限
public record EmailChangePolicy(int perAccountDaily) {

  /// 天窗口的长度。
  public static final Duration DAY_WINDOW = Duration.ofHours(24);

  /// 上限至少为 1。
  public EmailChangePolicy {
    if (perAccountDaily < 1) {
      throw new IllegalArgumentException("perAccountDaily 至少为 1，实际值: " + perAccountDaily);
    }
  }

  /// 创建规则。
  ///
  /// @param perAccountDaily 每账号每天的申请上限
  /// @return 规则
  public static EmailChangePolicy of(int perAccountDaily) {
    return new EmailChangePolicy(perAccountDaily);
  }
}
