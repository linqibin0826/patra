package dev.patra.analytics.infra.insight.signal;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.vo.CoReadingPair;
import dev.patra.analytics.domain.insight.model.vo.HotReadingCandidate;
import dev.patra.analytics.domain.insight.policy.CoReadingRule;
import dev.patra.analytics.domain.insight.policy.HotReadingRule;
import dev.patra.analytics.domain.insight.port.signal.ReadingSignalPort;
import dev.patra.analytics.infra.insight.config.InsightProperties;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/// `ReadingSignalPort` 的 ClickHouse 实现（insight spec §5、§6）：从明细表 `events` 算，按北京时间的天过滤，
/// 14 天正好裁到 14 个分区；只数真人会话，`session_quality` 先按 `first_day` 过滤再分组。
///
/// 「天」「今天」都按北京时间：分区键与排序键里的日期也是这么切的。
public class ClickHouseReadingSignalAdapter implements ReadingSignalPort {

  /// 窗口内的查看类事件的条件：`reads` 与「有阅读的会话」两处共用。
  private static final String WINDOW_READS =
      """
      event_name IN ('article_viewed', 'fulltext_read', 'pdf_downloaded', 'article_collected')
            AND toDate(derived_at, 'Asia/Shanghai') BETWEEN toDate({from:String}) AND toDate({today:String})
            AND article_id != 0{synthetic}""";

  /// 真人会话与窗口内的查看类事件，两个查询共用。`{aggregate}` 为 `reads` 的聚合列。
  ///
  /// 真人会话只在「窗口内有阅读（且按开关过滤了合成）」的会话里判定：`session_quality` 没有合成标记，
  /// 直接对窗口内全部会话分组的话，合成流量里只逛首页、检索的会话也会算进来，内存随合成数据涨。
  private static final String READS =
      """
      WITH real_sessions AS (
          SELECT session_id FROM session_quality
          WHERE first_day >= toDate({from:String}) - 1
            AND session_id IN (SELECT session_id FROM events WHERE {window})
          GROUP BY session_id HAVING max(has_client) = 1 AND max(has_bot) = 0),
      reads AS (
          SELECT person_id, article_id, toDate(derived_at, 'Asia/Shanghai') AS day, {aggregate}
          FROM events
          WHERE {window}
            AND session_id IN real_sessions
          GROUP BY person_id, article_id, day)
      """
          .replace("{window}", WINDOW_READS);

  /// 热读：每人每天每篇一行，分量 =（全文 ? 3 : 1）× 0.5 ^ (距今天数 / 3)；近 7 天不同人数 ≥ 3 才上榜。
  private static final String HOT =
      READS.replace(
              "{aggregate}", "max(event_name IN ('fulltext_read', 'pdf_downloaded')) AS fulltext")
          + """
          SELECT article_id,
                 sum(if(fulltext, {weight:UInt8}, 1)
                     * pow(0.5, dateDiff('day', day, toDate({today:String})) / {halfLife:UInt8})) AS score,
                 toUInt32(uniqExactIf(person_id, day >= toDate({today:String}) - {eligibleBack:UInt8}))
                   AS readers_7d
          FROM reads
          GROUP BY article_id
          HAVING readers_7d >= {minReaders:UInt8}
          ORDER BY score DESC, readers_7d DESC, article_id ASC
          LIMIT {limit:UInt32}
          """;

  /// 共现（一片）：同一人同一天为一组、取当天最先读的 20 篇；只展开 X 落在这一片的有序配对；
  /// 相似度 = 一起读的人数 / √(读 X 的人数 × 读 Y 的人数)，人数都在截断后的组里数。
  private static final String CO_READING =
      READS.replace("{aggregate}", "min(derived_at) AS first_at")
          + """
          , baskets AS (
              SELECT person_id,
                     arraySlice(arrayMap(t -> t.2, arraySort(groupArray((first_at, article_id)))),
                                1, {groupLimit:UInt8}) AS items
              FROM reads GROUP BY person_id, day),
          readers AS (
              SELECT a AS article_id, uniqExact(person_id) AS n
              FROM baskets ARRAY JOIN items AS a GROUP BY a),
          pairs AS (
              SELECT p.1 AS x, p.2 AS y, uniqExact(person_id) AS co
              FROM baskets
              ARRAY JOIN arrayFlatten(arrayMap(a -> arrayMap(b -> (a, b), arrayFilter(b -> b != a, items)),
                         arrayFilter(a -> cityHash64(a) % {shards:UInt16} = {shard:UInt16}, items))) AS p
              GROUP BY x, y HAVING co >= {minCoReaders:UInt8})
          SELECT x, y, toUInt32(co) AS co, co / sqrt(rx.n * ry.n) AS similarity
          FROM pairs
          JOIN readers AS rx ON rx.article_id = pairs.x
          JOIN readers AS ry ON ry.article_id = pairs.y
          ORDER BY x, similarity DESC, co DESC, y ASC
          LIMIT {perArticle:UInt32} BY x
          """;

  private final Client client;
  private final InsightProperties properties;

  /// 创建适配器。
  ///
  /// @param client ClickHouse 客户端
  /// @param properties insight 配置（合成事件开关、共现分片数）
  public ClickHouseReadingSignalAdapter(Client client, InsightProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  /// 一条查询算出候选。
  ///
  /// @param today 北京时间的今天
  /// @param limit 最多几篇
  /// @return 候选，按名次
  @Override
  public List<HotReadingCandidate> hotCandidates(LocalDate today, int limit) {
    Map<String, Object> params =
        Map.of(
            "from",
            today.minusDays(HotReadingRule.WINDOW_DAYS - 1L).toString(),
            "today",
            today.toString(),
            "weight",
            HotReadingRule.FULLTEXT_WEIGHT,
            "halfLife",
            HotReadingRule.HALF_LIFE_DAYS,
            "eligibleBack",
            HotReadingRule.ELIGIBLE_DAYS - 1,
            "minReaders",
            HotReadingRule.MIN_READERS,
            "limit",
            limit);
    return query(HOT, params).stream()
        .map(
            row ->
                HotReadingCandidate.of(
                    row.getBigInteger("article_id").longValueExact(),
                    row.getDouble("score"),
                    (int) row.getLong("readers_7d")))
        .toList();
  }

  /// 按 X 分片逐片查询再拼接：每片只有这片的 X，结果天然按 X 分组。
  ///
  /// @param today 北京时间的今天
  /// @param perArticle 每篇 X 最多几篇
  /// @return 配对
  @Override
  public List<CoReadingPair> coReadingPairs(LocalDate today, int perArticle) {
    int shards = properties.insight().coReadingShards();
    List<CoReadingPair> pairs = new ArrayList<>();
    for (int shard = 0; shard < shards; shard++) {
      Map<String, Object> params =
          Map.of(
              "from",
              today.minusDays(CoReadingRule.WINDOW_DAYS - 1L).toString(),
              "today",
              today.toString(),
              "groupLimit",
              CoReadingRule.GROUP_LIMIT,
              "shards",
              shards,
              "shard",
              shard,
              "minCoReaders",
              CoReadingRule.MIN_CO_READERS,
              "perArticle",
              perArticle);
      query(CO_READING, params).stream()
          .map(
              row ->
                  CoReadingPair.of(
                      row.getBigInteger("x").longValueExact(),
                      row.getBigInteger("y").longValueExact(),
                      row.getDouble("similarity"),
                      (int) row.getLong("co")))
          .forEach(pairs::add);
    }
    // 稳定排序：同一篇 X 只在一片里，片内顺序（名次）保持不变
    pairs.sort(Comparator.comparingLong(CoReadingPair::articleId));
    return List.copyOf(pairs);
  }

  /// 执行查询；客户端异常一律算 ClickHouse 不可用。
  ///
  /// @param template 带 `{synthetic}` 占位的 SQL
  /// @param params 查询参数
  /// @return 结果行
  private List<GenericRecord> query(String template, Map<String, Object> params) {
    String sql = template.replace("{synthetic}", properties.syntheticFilter("is_synthetic"));
    try {
      return client.queryAll(sql, params);
    } catch (RuntimeException e) {
      throw new InsightUnavailableException("查 ClickHouse 排名信号失败：" + e.getMessage(), e);
    }
  }
}
