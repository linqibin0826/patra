package dev.patra.analytics.simulator.command;

import java.time.Duration;
import java.util.Locale;
import picocli.CommandLine;

/// 时长参数：`30m`、`90s`、`2h`，也认 ISO 8601（`PT30M`）。
public final class DurationConverter implements CommandLine.ITypeConverter<Duration> {

  /// 解析。
  ///
  /// @param value 文本
  /// @return 时长
  @Override
  public Duration convert(String value) {
    String text = value.trim();
    if (text.toUpperCase(Locale.ROOT).startsWith("PT")) {
      return Duration.parse(text);
    }
    long amount = Long.parseLong(text.substring(0, text.length() - 1));
    return switch (text.charAt(text.length() - 1)) {
      case 's' -> Duration.ofSeconds(amount);
      case 'm' -> Duration.ofMinutes(amount);
      case 'h' -> Duration.ofHours(amount);
      default -> throw new CommandLine.TypeConversionException("时长写成 30m、90s、2h：" + value);
    };
  }
}
