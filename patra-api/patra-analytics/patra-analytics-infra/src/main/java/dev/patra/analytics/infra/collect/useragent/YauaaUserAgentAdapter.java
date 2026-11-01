package dev.patra.analytics.infra.collect.useragent;

import dev.patra.analytics.domain.collect.model.vo.UserAgentInfo;
import dev.patra.analytics.domain.collect.port.useragent.UserAgentPort;
import java.util.Set;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Component;

/// 用 Yauaa 解析 UA：浏览器名、操作系统名、设备类别（埋点方案 §4.3）。
///
/// 分析器常驻约 100 MB 堆，整个 JVM 只建一个（静态持有类懒加载）。只取三个字段、缓存 10,000 个 UA。
@Component
public class YauaaUserAgentAdapter implements UserAgentPort {

  /// Yauaa 表示「不知道」的取值，写出时当成 `null`
  private static final Set<String> UNKNOWN_VALUES = Set.of("Unknown", "??", "Hacker");

  /// 浏览器名、系统名的上限（字符，`enriched-event` 的 `maxLength`）
  static final int NAME_LIMIT = 64;

  private final UserAgentAnalyzer analyzer;

  /// 创建适配器，触发（JVM 内只一次的）分析器初始化。
  public YauaaUserAgentAdapter() {
    this.analyzer = AnalyzerHolder.ANALYZER;
  }

  /// 解析一个 UA。
  ///
  /// @param userAgent 原始 UA，可为空字符串
  /// @return 解析结果；设备类别总有值（解析不出为 `Unknown`）
  @Override
  public UserAgentInfo parse(String userAgent) {
    UserAgent parsed = analyzer.parse(userAgent == null ? "" : userAgent);
    String deviceClass = parsed.getValue(UserAgent.DEVICE_CLASS);
    return UserAgentInfo.of(
        known(parsed.getValue(UserAgent.AGENT_NAME)),
        known(parsed.getValue(UserAgent.OPERATING_SYSTEM_NAME)),
        deviceClass == null || deviceClass.isBlank() ? "Unknown" : deviceClass);
  }

  /// Yauaa 的「不知道」换成 `null`；超长的截到富化事件的上限（不认识的 UA 会把整段产品标识当成浏览器名）。
  ///
  /// @param value 字段值
  /// @return 值本身（最多 64 字符），或 `null`
  static String known(String value) {
    if (value == null || value.isBlank() || UNKNOWN_VALUES.contains(value)) {
      return null;
    }
    return value.length() > NAME_LIMIT ? value.substring(0, NAME_LIMIT) : value;
  }

  /// 分析器的持有类：第一次用到适配器时才初始化。
  private static final class AnalyzerHolder {

    /// 只取三个字段的分析器。
    static final UserAgentAnalyzer ANALYZER =
        UserAgentAnalyzer.newBuilder()
            .hideMatcherLoadStats()
            .withFields(
                UserAgent.DEVICE_CLASS, UserAgent.AGENT_NAME, UserAgent.OPERATING_SYSTEM_NAME)
            .withCache(10_000)
            .immediateInitialization()
            .dropTests()
            .build();

    /// 只有静态字段。
    private AnalyzerHolder() {}
  }
}
