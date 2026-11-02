package dev.patra.analytics.infra.insight.persistence;

import dev.patra.analytics.domain.insight.port.read.CoReadingReadPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `CoReadingReadPort` 的原生 SQL 实现：按主键 `(article_id, position)` 取前几行。
@Component
@RequiredArgsConstructor
public class CoReadingReadAdapter implements CoReadingReadPort {

  private final JdbcClient jdbc;

  /// 按名次取前几篇。
  ///
  /// @param articleId 文献 ID
  /// @param limit 最多几篇
  /// @return 文献 ID
  @Override
  public List<Long> relatedOf(long articleId, int limit) {
    return jdbc.sql(
            "SELECT related_article_id FROM insight.co_reading"
                + " WHERE article_id = :articleId ORDER BY position LIMIT :limit")
        .param("articleId", articleId)
        .param("limit", limit)
        .query(Long.class)
        .list();
  }
}
