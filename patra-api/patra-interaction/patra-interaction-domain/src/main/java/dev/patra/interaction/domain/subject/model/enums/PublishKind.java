package dev.patra.interaction.domain.subject.model.enums;

import java.time.Duration;

/// 发表守卫按作者计数的种类与频率规则（决策 E）。「每天」是北京时间的自然日；编辑不算发表，删除不退还额度。
public enum PublishKind {
  /// 评论（根评论与回复，文献下与经验下都算）：每天 100 条，两条至少间隔 10 秒。
  COMMENT("评论", 100, Duration.ofSeconds(10)),

  /// 投稿经验（帖子）：每天 10 条，不限间隔。
  POST("投稿经验", 10, Duration.ZERO);

  private final String displayName;
  private final int dailyLimit;
  private final Duration minInterval;

  /// 定义一种发表。
  ///
  /// @param displayName 消息里的名词
  /// @param dailyLimit 每天最多几条
  /// @param minInterval 两条之间至少隔多久，`Duration.ZERO` 为不限
  PublishKind(String displayName, int dailyLimit, Duration minInterval) {
    this.displayName = displayName;
    this.dailyLimit = dailyLimit;
    this.minInterval = minInterval;
  }

  /// 消息里的名词，如「评论」。
  ///
  /// @return 名词
  public String displayName() {
    return displayName;
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
