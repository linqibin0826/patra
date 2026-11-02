package dev.patra.analytics.app.history.usecase.recording;

import dev.patra.tool.cqrs.Command;

/// 当前用户打开或关闭阅读记录。
///
/// @param enabled 是否记录
public record ChangeRecordingCommand(boolean enabled) implements Command<RecordingResult> {

  /// 构造。
  ///
  /// @param enabled 是否记录
  /// @return 命令
  public static ChangeRecordingCommand of(boolean enabled) {
    return new ChangeRecordingCommand(enabled);
  }
}
