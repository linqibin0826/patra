package dev.patra.analytics.domain.collect.policy;

import dev.patra.analytics.domain.collect.model.enums.BotReason;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/// 爬虫判定的前两条规则与频率规则的阈值（埋点方案 §6.7）。
///
/// 先看 COUNTER-Robots 列表，再看 Yauaa 的设备类别；都没命中才轮到频率规则（同一设备 5 分钟内的文献浏览
/// 超过 30 条），频率由 Redis 计数，在用例里组合。
public final class BotClassification {

  /// 频率规则的阈值：窗口内的文献浏览超过这个数就判为爬虫
  public static final int RATE_LIMIT = 30;

  /// 频率规则的窗口
  public static final Duration RATE_WINDOW = Duration.ofMinutes(5);

  /// Yauaa 归为非人类的设备类别
  private static final Set<String> ROBOT_CLASSES =
      Set.of("Robot", "Robot Mobile", "Robot Imitator", "Spy", "Hacker");

  private BotClassification() {}

  /// 按 UA 判定。
  ///
  /// @param listedInRobots UA 是否匹配 COUNTER-Robots 列表
  /// @param deviceClass Yauaa 的设备类别
  /// @return 判为爬虫的原因；没命中为空
  public static Optional<BotReason> fromUserAgent(boolean listedInRobots, String deviceClass) {
    if (listedInRobots) {
      return Optional.of(BotReason.UA_LIST);
    }
    return ROBOT_CLASSES.contains(deviceClass) ? Optional.of(BotReason.UA_CLASS) : Optional.empty();
  }

  /// 频率规则是否命中。
  ///
  /// @param windowCount 窗口内这个设备的文献浏览条数
  /// @return 超过阈值时为 `true`
  public static boolean overRate(int windowCount) {
    return windowCount > RATE_LIMIT;
  }
}
