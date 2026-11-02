package dev.patra.analytics.adapter.history.rest.response;

/// 阅读记录开关。
///
/// @param enabled 是否记录
public record RecordingResponse(boolean enabled) {

  /// 构造。
  ///
  /// @param enabled 是否记录
  /// @return 响应
  public static RecordingResponse of(boolean enabled) {
    return new RecordingResponse(enabled);
  }
}
