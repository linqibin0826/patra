package dev.patra.identity.app.account.usecase.email;

import dev.patra.tool.cqrs.Command;
import java.time.Duration;
import java.util.Objects;

/// 删掉过期够久的一次性邮件令牌。
///
/// @param retention 过期多久之后才删（保留期），由调度任务从配置读出带过来
public record PurgeExpiredEmailTokensCommand(Duration retention) implements Command<Integer> {

  /// 保留期不能为 `null` 或负数。
  public PurgeExpiredEmailTokensCommand {
    Objects.requireNonNull(retention, "retention 不能为 null");
    if (retention.isNegative()) {
      throw new IllegalArgumentException("retention 不能为负数，实际值: " + retention);
    }
  }

  /// 创建命令。
  ///
  /// @param retention 保留期
  /// @return 命令
  public static PurgeExpiredEmailTokensCommand of(Duration retention) {
    return new PurgeExpiredEmailTokensCommand(retention);
  }
}
