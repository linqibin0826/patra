package dev.patra.analytics.domain.kernel.model.vo;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 事件 schema 的版本号，格式 `MODEL-REVISION-ADDITION`（Snowplow 的 SchemaVer，埋点方案 §7）。
///
/// 采集端点接受与当前 `MODEL` 相同的任意版本：加可选属性（ADDITION）、改含义但兼容（REVISION）都兼容旧事件；
/// `MODEL` 变了的旧事件进 dlq。
///
/// @param model 不兼容改动的计数
/// @param revision 兼容的含义改动计数
/// @param addition 加可选属性的计数
public record SchemaVersion(int model, int revision, int addition) {

  /// 当前事件 schema 的 MODEL
  public static final int CURRENT_MODEL = 1;

  private static final Pattern FORMAT = Pattern.compile("([0-9]{1,6})-([0-9]{1,6})-([0-9]{1,6})");

  /// 解析版本号。
  ///
  /// @param text 事件 JSON 里的 `schema_version`，可为 `null`
  /// @return 版本；格式不对（含末尾换行）时为空
  public static Optional<SchemaVersion> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    Matcher matcher = FORMAT.matcher(text);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new SchemaVersion(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3))));
  }

  /// 这个版本采集端点收不收。
  ///
  /// @return `MODEL` 等于当前 MODEL 时为 `true`
  public boolean accepted() {
    return model == CURRENT_MODEL;
  }
}
