package dev.patra.analytics.simulator.truth;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.data.ClickHouseFormat;
import dev.patra.analytics.simulator.event.BeijingDays;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

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

  private static final String PARTITION_SQL =
      """
      SELECT count() AS n FROM system.parts
      WHERE database = currentDatabase() AND table = 'events' AND active AND partition_id = {p:String}
      """;

  private static final String DAILY_ROW =
      "{\"run\":%d,\"day\":\"%s\",\"delivered\":%d,\"distinct_events\":%d,"
          + "\"viewers_truth\":%d,\"viewers_ch\":%d,\"viewers_abs_diff\":%d,"
          + "\"fulltext_truth\":%d,\"fulltext_ch\":%d,\"fulltext_abs_diff\":%d}";

  /// `events` 保留 90 天、整个分区到期才删；到期前一天起就不对账这一天：分区里的数据片段按各自最晚一行到期，
  /// 会在一天里陆续被删，对出来的去重条数偏低
  private static final int TTL_SAFE_DAYS = 89;

  private final Client client;
  private final RunScope scope;
  private final Clock clock;
  private List<DailyReconcileRow> dailyRows = List.of();

  /// 创建对账器。
  ///
  /// @param client ClickHouse 客户端（默认库 `patra_analytics`）
  /// @param scope 本次运行的范围
  public Reconciler(Client client, RunScope scope) {
    this(client, scope, Clock.system(BeijingDays.ZONE));
  }

  /// 创建对账器（测试可换时钟）。
  ///
  /// @param client ClickHouse 客户端（默认库 `patra_analytics`）
  /// @param scope 本次运行的范围
  /// @param clock 判断哪些天临近 TTL 的时钟
  public Reconciler(Client client, RunScope scope, Clock clock) {
    this.client = client;
    this.scope = scope;
    this.clock = clock;
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
  /// `events` 里这一天的分区已被 90 天 TTL 删掉、或临近删除（[#TTL_SAFE_DAYS]）的，整天跳过：不比对、不计入送达数，
  /// 也不出逐天结果，否则去重条数偏低会被当成丢失（面板 spec §6）。
  ///
  /// @param truthFile 真值文件
  /// @param summary 运行摘要
  /// @return 结果；逐天结果见 [#dailyRows()]
  public ReconcileResult reconcile(Path truthFile, RunSummary summary) {
    TreeSet<LocalDate> pending = new TreeSet<>();
    summary.eventsByDay().keySet().forEach(day -> pending.add(LocalDate.parse(day)));
    Comparison comparison = new Comparison();
    List<DailyReconcileRow> daily = new ArrayList<>();
    long[] unique = new long[1];
    long[] skippedDelivered = new long[1];
    BiConsumer<LocalDate, List<TruthRow>> check =
        (day, truth) -> {
          pending.remove(day);
          long delivered = summary.eventsByDay().getOrDefault(day.toString(), 0L);
          if (nearTtl(day) || !partitionExists(day)) {
            skippedDelivered[0] += delivered;
            return;
          }
          List<TruthRow> actual = actual(day);
          long dayUnique = uniqueEvents(day);
          comparison.add(truth, actual);
          unique[0] += dayUnique;
          daily.add(
              new Comparison().add(truth, actual).daily(scope.run(), day, delivered, dayUnique));
        };
    TruthFile.forEachDay(truthFile, check);
    for (LocalDate day : List.copyOf(pending)) {
      check.accept(day, List.of());
    }
    dailyRows = List.copyOf(daily);
    long delivered = summary.deliveredEvents() - skippedDelivered[0];
    return judge(
        comparison.result(),
        delivered,
        summary.uncertainEvents(),
        unique[0],
        delivered + summary.resentEvents());
  }

  /// 最近一次 [#reconcile] 的逐天结果，按日期升序。
  ///
  /// @return 逐天结果
  public List<DailyReconcileRow> dailyRows() {
    return dailyRows;
  }

  /// 把逐天结果写进 `reconcile_results`；同一运行、同一天重写时读 `FINAL` 取最新一次。
  ///
  /// @param rows 逐天结果
  public void writeDailyRows(List<DailyReconcileRow> rows) {
    if (rows.isEmpty()) {
      return;
    }
    StringBuilder body = new StringBuilder();
    rows.forEach(
        row ->
            body.append(
                    DAILY_ROW.formatted(
                        row.run(),
                        row.day(),
                        row.delivered(),
                        row.distinctEvents(),
                        row.viewersTruth(),
                        row.viewersCh(),
                        row.viewersAbsDiff(),
                        row.fulltextTruth(),
                        row.fulltextCh(),
                        row.fulltextAbsDiff()))
                .append('\n'));
    try {
      client
          .insert(
              "reconcile_results",
              new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8)),
              ClickHouseFormat.JSONEachRow)
          .get(1, TimeUnit.MINUTES)
          .close();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("写对账结果被中断", e);
    } catch (Exception e) {
      throw new IllegalStateException("写对账结果到 ClickHouse 失败", e);
    }
  }

  /// 这一天的明细是否已到或临近 90 天 TTL。
  ///
  /// @param day 北京时间的日期
  /// @return 是为 `true`
  private boolean nearTtl(LocalDate day) {
    return !day.isAfter(LocalDate.now(clock).minusDays(TTL_SAFE_DAYS));
  }

  /// `events` 里这一天的分区还在不在。
  ///
  /// @param day 北京时间的日期
  /// @return 在为 `true`
  private boolean partitionExists(LocalDate day) {
    String partition = day.toString().replace("-", "");
    return client.queryAll(PARTITION_SQL, Map.of("p", partition)).getFirst().getLong("n") > 0;
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
