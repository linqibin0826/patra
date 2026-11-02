package dev.patra.analytics.domain.collect.event;

import dev.patra.analytics.domain.collect.model.vo.CollectedEnvelope;
import dev.patra.analytics.domain.kernel.model.vo.EventName;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// 一次文献阅读：登录用户打开过一篇文献，`fulltext` 表示读了全文或下载了 PDF（阅读历史 spec §4.1）。
///
/// @param userId 用户 ID
/// @param articleId 文献 ID
/// @param readAt 校正后的事件时间
/// @param fulltext 是否读了全文
public record ArticleRead(long userId, long articleId, Instant readAt, boolean fulltext) {

  /// 算作全文的事件：全文阅读与下载 PDF（埋点方案 §6.8）
  private static final Set<EventName> FULLTEXT =
      Set.of(EventName.FULLTEXT_READ, EventName.PDF_DOWNLOADED);

  /// 时间必填。
  public ArticleRead {
    Objects.requireNonNull(readAt, "readAt");
  }

  /// 创建一次阅读。
  ///
  /// @param userId 用户 ID
  /// @param articleId 文献 ID
  /// @param readAt 校正后的事件时间
  /// @param fulltext 是否读了全文
  /// @return 阅读
  public static ArticleRead of(long userId, long articleId, Instant readAt, boolean fulltext) {
    return new ArticleRead(userId, articleId, readAt, fulltext);
  }

  /// 从一条合格事件挑出阅读：要登录、非合成、带文献 ID 的打开文献、全文阅读或下载 PDF。爬虫标记不影响（那是这个账号
  /// 自己的历史）。时间截到毫秒：与 Kafka 里的 `derived_at` 一致，翻页游标也只到毫秒，存了微秒会让同一毫秒里的行翻页时漏掉。
  ///
  /// @param event 合格的事件
  /// @param readAt 校正后的事件时间（时钟可能带微秒）
  /// @return 阅读；不算阅读或用户 ID 超出 `long` 时为空
  public static Optional<ArticleRead> from(CollectedEnvelope event, Instant readAt) {
    boolean read =
        event.eventName() == EventName.ARTICLE_VIEWED || FULLTEXT.contains(event.eventName());
    if (!read || event.synthetic() || event.userId() == null || event.articleId() == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          of(
              Long.parseLong(event.userId()),
              event.articleId(),
              readAt.truncatedTo(ChronoUnit.MILLIS),
              FULLTEXT.contains(event.eventName())));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
