package dev.patra.analytics.infra.history.persistence;

import dev.patra.analytics.domain.history.model.vo.HistoryCursor;
import dev.patra.analytics.domain.history.model.vo.ReadingEntry;
import dev.patra.analytics.domain.history.port.read.ReadingHistoryReadPort;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `ReadingHistoryReadPort` 的原生 SQL 实现：键集翻页，走 `idx_reading_history_user_time`。
@Component
@RequiredArgsConstructor
public class ReadingHistoryReadAdapter implements ReadingHistoryReadPort {

  private final JdbcClient jdbc;

  /// 有游标时用行比较 `(last_read_at, article_id) < (游标)` 接着上一页往下取。
  ///
  /// @param userId 用户 ID
  /// @param since 只要不早于它的
  /// @param after 上一页最后一行，可空
  /// @param limit 条数
  /// @return 这一页
  @Override
  public List<ReadingEntry> findPage(long userId, Instant since, HistoryCursor after, int limit) {
    String afterClause =
        after == null ? "" : " AND (last_read_at, article_id) < (:afterAt, :afterArticle)";
    JdbcClient.StatementSpec statement =
        jdbc.sql(
                "SELECT article_id, last_read_at, fulltext_read FROM history.reading_history"
                    + " WHERE user_id = :userId AND last_read_at >= :since"
                    + afterClause
                    + " ORDER BY last_read_at DESC, article_id DESC LIMIT :limit")
            .param("userId", userId)
            .param("since", since.atOffset(ZoneOffset.UTC))
            .param("limit", limit);
    if (after != null) {
      statement =
          statement
              .param("afterAt", after.lastReadAt().atOffset(ZoneOffset.UTC))
              .param("afterArticle", after.articleId());
    }
    return statement
        .query(
            (rs, rowNum) ->
                ReadingEntry.of(
                    rs.getLong("article_id"),
                    rs.getObject("last_read_at", OffsetDateTime.class).toInstant(),
                    rs.getBoolean("fulltext_read")))
        .list();
  }
}
