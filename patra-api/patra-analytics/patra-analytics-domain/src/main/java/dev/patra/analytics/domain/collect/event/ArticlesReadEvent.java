package dev.patra.analytics.domain.collect.event;

import java.util.List;

/// 一批采集写进 Kafka 之后，这批里的文献阅读（进程内同步事件，history 子域订阅后写阅读历史）。
///
/// @param reads 阅读，未合并
public record ArticlesReadEvent(List<ArticleRead> reads) {

  /// 防御性拷贝。
  public ArticlesReadEvent {
    reads = List.copyOf(reads);
  }

  /// 创建事件。
  ///
  /// @param reads 阅读
  /// @return 事件
  public static ArticlesReadEvent of(List<ArticleRead> reads) {
    return new ArticlesReadEvent(reads);
  }
}
