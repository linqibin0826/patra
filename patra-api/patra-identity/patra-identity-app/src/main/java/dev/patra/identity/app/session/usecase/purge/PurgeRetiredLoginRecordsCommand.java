package dev.patra.identity.app.session.usecase.purge;

import dev.patra.tool.cqrs.Command;
import java.time.Duration;
import java.util.Objects;

/// 删掉结束或过期满保留期的登录记录（PAP-99 设计第 14 节；《网络安全法》日志留存 ≥ 6 个月，默认 190 天）。
///
/// @param retention 结束或过期多久之后才删（保留期），由调度任务从配置读出带过来
public record PurgeRetiredLoginRecordsCommand(Duration retention) implements Command<Integer> {

  /// 保留期不能为 `null` 或负数。
  public PurgeRetiredLoginRecordsCommand {
    Objects.requireNonNull(retention, "retention 不能为 null");
    if (retention.isNegative()) {
      throw new IllegalArgumentException("retention 不能为负数，实际值: " + retention);
    }
  }

  /// 创建命令。
  ///
  /// @param retention 保留期
  /// @return 命令
  public static PurgeRetiredLoginRecordsCommand of(Duration retention) {
    return new PurgeRetiredLoginRecordsCommand(retention);
  }
}
