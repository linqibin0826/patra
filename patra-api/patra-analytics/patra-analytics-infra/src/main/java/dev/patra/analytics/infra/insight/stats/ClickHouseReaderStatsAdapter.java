package dev.patra.analytics.infra.insight.stats;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.client.api.query.QuerySettings;
import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.vo.ReaderStats;
import dev.patra.analytics.domain.insight.port.stats.ReaderStatsPort;
import dev.patra.analytics.infra.insight.config.InsightProperties;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/// `ReaderStatsPort` 的 ClickHouse 实现（insight spec §4.2）：在 `article_session_reads` 上按文献主键取，
/// 只数真人会话（`session_quality` 有浏览器事件、没有爬虫事件），按人去重。
///
/// 汇总表合并前同一个键可能有多行，先按（文献，会话，人）取 `max`。查询最多跑 3 秒：门户对计数的超时很短。
public class ClickHouseReaderStatsAdapter implements ReaderStatsPort {

  /// 查询超时（秒）。
  private static final int MAX_EXECUTION_SECONDS = 3;

  private static final String SQL =
      """
      SELECT article_id,
             uniqExactIf(person_id, viewed = 1) AS viewers,
             uniqExactIf(person_id, fulltext = 1) AS fulltext_readers
      FROM (
          SELECT article_id, session_id, person_id, max(viewed) AS viewed, max(fulltext) AS fulltext
          FROM article_session_reads
          WHERE article_id IN ({ids}){synthetic}
          GROUP BY article_id, session_id, person_id)
      WHERE session_id IN (
          SELECT session_id FROM session_quality
          WHERE session_id IN (SELECT session_id FROM article_session_reads WHERE article_id IN ({ids}))
          GROUP BY session_id
          HAVING max(has_client) = 1 AND max(has_bot) = 0)
      GROUP BY article_id
      """;

  private final Client client;
  private final InsightProperties properties;

  /// 创建适配器。
  ///
  /// @param client ClickHouse 客户端
  /// @param properties insight 配置
  public ClickHouseReaderStatsAdapter(Client client, InsightProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  /// 一条查询取这批文献；文献 ID 是数字，直接拼进 `IN (...)`。
  ///
  /// @param articleIds 文献 ID
  /// @return 有人看过的文献
  @Override
  public Map<Long, ReaderStats> statsOf(Collection<Long> articleIds) {
    if (articleIds.isEmpty()) {
      return Map.of();
    }
    String ids = articleIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    String sql =
        SQL.replace("{ids}", ids)
            .replace("{synthetic}", properties.syntheticFilter("is_synthetic"));
    try {
      return client
          .queryAll(sql, new QuerySettings().setMaxExecutionTime(MAX_EXECUTION_SECONDS))
          .stream()
          .map(ClickHouseReaderStatsAdapter::toStats)
          .collect(Collectors.toMap(ReaderStats::articleId, Function.identity()));
    } catch (RuntimeException e) {
      throw new InsightUnavailableException("查 ClickHouse 计数失败：" + e.getMessage(), e);
    }
  }

  /// 一行 → 读者数。
  ///
  /// @param row 查询结果行
  /// @return 读者数
  private static ReaderStats toStats(GenericRecord row) {
    return ReaderStats.of(
        row.getBigInteger("article_id").longValueExact(),
        row.getLong("viewers"),
        row.getLong("fulltext_readers"));
  }
}
