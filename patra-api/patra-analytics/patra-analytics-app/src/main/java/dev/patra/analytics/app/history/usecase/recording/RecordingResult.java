package dev.patra.analytics.app.history.usecase.recording;

/// 保存后的开关。
///
/// @param enabled 是否记录
public record RecordingResult(boolean enabled) {

  /// 构造。
  ///
  /// @param enabled 是否记录
  /// @return 结果
  public static RecordingResult of(boolean enabled) {
    return new RecordingResult(enabled);
  }
}
