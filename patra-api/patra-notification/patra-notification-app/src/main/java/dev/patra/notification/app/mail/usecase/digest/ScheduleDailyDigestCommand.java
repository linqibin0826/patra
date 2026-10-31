package dev.patra.notification.app.mail.usecase.digest;

import dev.patra.tool.cqrs.Command;
import java.util.Set;

/// 建每日汇总的投递（spec 5.2、7.4）：定时按北京时间当天 8:00 的窗口给设为汇总的人各建一条；手动验收时只给列出的人建，
/// 窗口右端是此刻。
///
/// @param manualUsers 手动验收时列出的用户 ID；定时为空
public record ScheduleDailyDigestCommand(Set<Long> manualUsers)
    implements Command<DigestScheduleResult> {

  /// 防御性拷贝。
  public ScheduleDailyDigestCommand {
    manualUsers = Set.copyOf(manualUsers);
  }

  /// 定时的一次。
  ///
  /// @return 命令
  public static ScheduleDailyDigestCommand scheduled() {
    return new ScheduleDailyDigestCommand(Set.of());
  }

  /// 手动验收的一次。
  ///
  /// @param users 用户 ID，至少一个
  /// @return 命令
  /// @throws IllegalArgumentException 一个用户都没有
  public static ScheduleDailyDigestCommand manual(Set<Long> users) {
    if (users.isEmpty()) {
      throw new IllegalArgumentException("手动汇总至少要列出一个用户");
    }
    return new ScheduleDailyDigestCommand(users);
  }

  /// 是否手动。
  ///
  /// @return 列出了用户为真
  public boolean manual() {
    return !manualUsers.isEmpty();
  }
}
