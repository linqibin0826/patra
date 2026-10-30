package dev.patra.interaction.domain.subject.model.enums;

import java.time.Duration;

/// 发表守卫按作者计数的种类与频率规则（决策 E）。「每天」是北京时间的自然日；编辑不算发表，删除不退还额度。
/// 有帮助（v0.15 PAP-131）也走这里：点一次算一次，取消不退。
public enum PublishKind {
  /// 评论（根评论与回复，文献下与经验下都算）：每天 100 条，两条至少间隔 10 秒。
  COMMENT("发表了 %d 条评论", 100, Duration.ofSeconds(10)),

  /// 投稿经验（帖子）：每天 10 条，不限间隔。
  POST("发表了 %d 条投稿经验", 10, Duration.ZERO),

  /// 有帮助：每天 300 次，不限间隔。
  HELPFUL("点了 %d 次有帮助", 300, Duration.ZERO);

  private final String dailyLimitFormat;
  private final int dailyLimit;
  private final Duration minInterval;

  /// 定义一种发表。
  ///
  /// @param dailyLimitFormat 当天用完时的说法，`%d` 是上限
  /// @param dailyLimit 每天最多几条
  /// @param minInterval 两条之间至少隔多久，`Duration.ZERO` 为不限
  PublishKind(String dailyLimitFormat, int dailyLimit, Duration minInterval) {
    this.dailyLimitFormat = dailyLimitFormat;
    this.dailyLimit = dailyLimit;
    this.minInterval = minInterval;
  }

  /// 当天用完时的说法，如「发表了 100 条评论」。
  ///
  /// @return 说法
  public String dailyLimitPhrase() {
    return dailyLimitFormat.formatted(dailyLimit);
  }

  /// 每天最多几条。
  ///
  /// @return 上限
  public int dailyLimit() {
    return dailyLimit;
  }

  /// 两条之间至少隔多久。
  ///
  /// @return 间隔；不限时为 `Duration.ZERO`
  public Duration minInterval() {
    return minInterval;
  }
}
