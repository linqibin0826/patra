package dev.patra.analytics.simulator.truth;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import dev.patra.analytics.simulator.event.BeijingDays;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/// 对账（spec §7.3）：按天用 PAP-170「看过人数」的同一口径查 ClickHouse 汇总表，与真值逐行比对。
///
/// 每天一条查询，避开 `patra` 账号每条查询 1 GiB 的内存上限。`session_quality` 看前一天到次日：会话特征每次写入各一行、
/// 合并前不汇总，跨午夜的会话在次日那次写入里才被标为爬虫，只看到当天会把它误算成真人。
public final class Reconciler {

  /// 误差上限（D10）
  static final double TOLERANCE = 0.01;

  private static final DateTimeFormatter UTC_SECOND =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private static final String ACTUAL_SQL =
      """
      SELECT article_id,
             toUInt32(uniqExactIf(person_id, v = 1)) AS viewers,
             toUInt32(uniqExactIf(person_id, ft = 1)) AS fulltext_readers
      FROM (
          SELECT article_id, session_id, person_id, max(viewed) AS v, max(fulltext) AS ft
          FROM article_session_reads
          WHERE day = {d:Date} AND is_synthetic = 1 AND {run}
          GROUP BY article_id, session_id, person_id)
      WHERE session_id IN (
          SELECT session_id FROM session_quality
          WHERE first_day BETWEEN {d:Date} - 1 AND {d:Date} + 1
          GROUP BY session_id HAVING max(has_client) = 1 AND max(has_bot) = 0)
      GROUP BY article_id
      HAVING viewers > 0
      """;

  private static final String UNIQUE_SQL =
      """
      SELECT uniqExact(event_id) AS n FROM events
      WHERE derived_at >= {from:DateTime64(3, 'UTC')} AND derived_at < {to:DateTime64(3, 'UTC')}
        AND is_synthetic AND {run}
      """;

  private final Client client;
  private final RunScope scope;

  /// 创建对账器。
  ///
  /// @param client ClickHouse 客户端（默认库 `patra_analytics`）
  /// @param scope 本次运行的范围
  public Reconciler(Client client, RunScope scope) {
    this.client = client;
    this.scope = scope;
  }

  /// ClickHouse 里某天各篇的看过、读过全文人数。
  ///
  /// @param day 北京时间的日期
  /// @return 行
  public List<TruthRow> actual(LocalDate day) {
    List<TruthRow> rows = new ArrayList<>();
    for (GenericRecord record :
        client.queryAll(
            ACTUAL_SQL.replace("{run}", scope.personFilter()), Map.of("d", day.toString()))) {
      rows.add(
          new TruthRow(
              day,
              record.getLong("article_id"),
              (int) record.getLong("viewers"),
              (int) record.getLong("fulltext_readers")));
    }
    return rows;
  }

  /// ClickHouse 里某天本运行的去重事件数（每天一个分区，同一事件的重发落在同一天）。
  ///
  /// @param day 北京时间的日期
  /// @return 条数
  public long uniqueEvents(LocalDate day) {
    Map<String, Object> params =
        Map.of(
            "from", UTC_SECOND.format(day.atStartOfDay(BeijingDays.ZONE).toInstant()),
            "to", UTC_SECOND.format(day.plusDays(1).atStartOfDay(BeijingDays.ZONE).toInstant()));
    return client
        .queryAll(UNIQUE_SQL.replace("{run}", scope.eventsFilter()), params)
        .getFirst()
        .getLong("n");
  }

  /// 对账：真值文件一天一天读、一天一天查一遍比对（不把整份真值放进内存）；摘要里有、真值里没有的天也查。
  ///
  /// @param truthFile 真值文件
  /// @param summary 运行摘要
  /// @return 结果
  public ReconcileResult reconcile(Path truthFile, RunSummary summary) {
    TreeSet<LocalDate> pending = new TreeSet<>();
    summary.eventsByDay().keySet().forEach(day -> pending.add(LocalDate.parse(day)));
    Comparison comparison = new Comparison();
    long[] unique = new long[1];
    TruthFile.forEachDay(
        truthFile,
        (day, rows) -> {
          comparison.add(rows, actual(day));
          unique[0] += uniqueEvents(day);
          pending.remove(day);
        });
    for (LocalDate day : pending) {
      comparison.add(List.of(), actual(day));
      unique[0] += uniqueEvents(day);
    }
    return judge(
        comparison.result(),
        summary.deliveredEvents(),
        summary.uncertainEvents(),
        unique[0],
        summary.deliveredEvents() + summary.resentEvents());
  }

  /// 加上条数判定：「看过人数」按人去重，丢了整类事件或 1% 的查看事件也几乎不动它，所以还要求
  /// ClickHouse 的去重条数不少于送达数、不多于「送达 + 结果不明」。
  ///
  /// @param rows 逐行比对的结果
  /// @param delivered 送达的事件数
  /// @param uncertain 结果不明的事件数
  /// @param unique ClickHouse 里按天去重的条数
  /// @param written 写入的行数（含重发）
  /// @return 结果
  static ReconcileResult judge(
      ReconcileResult rows, long delivered, long uncertain, long unique, long written) {
    boolean countsMatch = unique >= delivered && unique <= delivered + uncertain;
    return rows.toBuilder()
        .writtenRows(written)
        .deliveredEvents(delivered)
        .uncertainEvents(uncertain)
        .uniqueEvents(unique)
        .passed(rows.passed() && countsMatch)
        .build();
  }

  /// 逐行比对（一次给全）。
  ///
  /// @param truth 真值
  /// @param actual ClickHouse 算出的
  /// @return 结果（不含事件条数；`passed` 只看两个误差）
  public static ReconcileResult compare(List<TruthRow> truth, List<TruthRow> actual) {
    return new Comparison().add(truth, actual).result();
  }
}
