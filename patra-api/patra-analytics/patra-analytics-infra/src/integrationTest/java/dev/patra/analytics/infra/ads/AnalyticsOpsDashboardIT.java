package dev.patra.analytics.infra.ads;

import static dev.patra.analytics.infra.ads.AdsITFixture.at;
import static dev.patra.analytics.infra.ads.AdsITFixture.backfill;
import static dev.patra.analytics.infra.ads.AdsITFixture.execute;
import static dev.patra.analytics.infra.ads.AdsITFixture.migrated;
import static dev.patra.analytics.infra.ads.AdsITFixture.rows;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.insert;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// 运营分析看板（运营分析面板 spec §5）：仓库里的看板 JSON 的每条 SQL，替换掉 Grafana 的宏和变量后都能在迁移过的库上跑，
/// 只读白名单里的表；「含合成数据」切换后数字会变（PAP-172 的 AC）。
class AnalyticsOpsDashboardIT {

  private static final String DASHBOARD =
      "patra-infra/docker/grafana/provisioning/dashboards/patra/analytics-ops.json";

  private static final Set<String> ALLOWED_TABLES =
      Set.of(
          "ads_daily_active_latest",
          "ads_cohort_retention_latest",
          "ads_daily_sessions_latest",
          "ads_daily_search_latest",
          "ads_daily_zero_result_terms_latest",
          "ads_daily_counter_items_latest",
          "ads_daily_quality_latest",
          "ads_daily_duplicates",
          "dlq_events",
          "reconcile_results");

  private static final Pattern TABLE_REF =
      Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+([a-z_][a-z0-9_]*)");

  private static final LocalDate D1 = LocalDate.of(2026, 10, 1);

  private static final LocalDate D3 = LocalDate.of(2026, 10, 3);

  private final Client client = migrated();

  @BeforeEach
  void seed() {
    List<ObjectNode> events = new ArrayList<>();
    for (LocalDate day = D1; !day.isAfter(D3); day = day.plusDays(1)) {
      events.addAll(visit("Real", false, day));
    }
    events.addAll(visit("sim001", true, D3));
    ObjectNode bot = at("article_viewed", "server", uuid(), device("Bot"), 0, D3 + " 12:00:00");
    bot.put("is_bot", true);
    bot.put("bot_reason", "ua_list");
    events.add(bot);
    insert(client, "T|dash", events);
    execute(
        client,
        "INSERT INTO dlq_events (received_at, reason_code, reason_detail, event_name, raw, raw_truncated)"
            + " VALUES ('2026-10-02 02:00:00', 'schema_invalid', '/x', 'page_viewed', '{}', false)");
    execute(
        client,
        "INSERT INTO reconcile_results (run, day, delivered, distinct_events, viewers_truth, viewers_ch,"
            + " viewers_abs_diff, fulltext_truth, fulltext_ch, fulltext_abs_diff)"
            + " VALUES (1, '2026-10-02', 100, 99, 10, 10, 0, 5, 5, 0)");
    execute(client, "SYSTEM FLUSH LOGS");
    for (String table :
        List.of(
            "device_user_links",
            "ads_daily_active",
            "ads_person_first_day",
            "ads_cohort_retention",
            "ads_daily_sessions",
            "ads_daily_search",
            "ads_daily_zero_result_terms",
            "ads_daily_counter_items",
            "ads_daily_quality",
            "ads_daily_duplicates")) {
      backfill(client, table, D1, D3);
    }
  }

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void every_panel_query_runs() {
    Map<String, String> queries = queries();
    assertThat(queries).hasSizeGreaterThanOrEqualTo(20);
    queries.forEach((panel, sql) -> assertThat(run(sql, "0,1")).as("面板「%s」", panel).isNotNull());
  }

  @Test
  void reads_only_allowed_tables() {
    queries()
        .forEach(
            (panel, sql) -> {
              Matcher matcher = TABLE_REF.matcher(sql);
              while (matcher.find()) {
                assertThat(matcher.group(1)).as("面板「%s」读的表", panel).isIn(ALLOWED_TABLES);
              }
            });
  }

  @Test
  void top_article_ids_are_strings() {
    String top = queries().get("Unique Item Requests 前 20 篇");

    assertThat(
            rows(client, "SELECT toTypeName(article_id) AS t FROM (" + resolve(top, "0,1") + ")")
                .getFirst()
                .getString("t"))
        .as("雪花 ID 超过 2^53，交给 Grafana 前端前要转成字符串")
        .isEqualTo("String");
  }

  @Test
  void empty_selection_yields_no_nan_or_inf() {
    queries()
        .forEach(
            (panel, sql) ->
                run(sql, "2")
                    .forEach(
                        row -> {
                          for (int i = 1; i <= row.getSchema().getColumns().size(); i++) {
                            assertThat(String.valueOf(row.getString(i)).toLowerCase())
                                .as("面板「%s」第 %d 列", panel, i)
                                .doesNotContain("nan")
                                .doesNotContain("inf");
                          }
                        }));
  }

  @Test
  void synthetic_toggle_changes_numbers() {
    String dau = queries().get("DAU / WAU / MAU");

    assertThat(lastDau(run(dau, "0"))).isNotEqualTo(lastDau(run(dau, "0,1")));
  }

  @Test
  void dau_panel_matches_ads_table() {
    String dau = queries().get("DAU / WAU / MAU");

    long expected =
        rows(
                client,
                "SELECT sum(dau) AS n FROM ads_daily_active_latest WHERE day = '%s' AND traffic = 'human'"
                    .formatted(D3))
            .getFirst()
            .getLong("n");
    assertThat(lastDau(run(dau, "0,1"))).isEqualTo(expected);
  }

  /// 看板里全部面板的 SQL，键是面板标题（含折叠行里的面板）。
  private static Map<String, String> queries() {
    JsonNode dashboard = readDashboard();
    Map<String, String> queries = new LinkedHashMap<>();
    collect(dashboard.get("panels"), queries);
    return queries;
  }

  private static void collect(JsonNode panels, Map<String, String> queries) {
    for (JsonNode panel : panels) {
      if (panel.has("panels")) {
        collect(panel.get("panels"), queries);
      }
      if (!panel.has("targets")) {
        continue;
      }
      for (JsonNode target : panel.get("targets")) {
        if (target.has("rawSql")) {
          String title = panel.get("title").asString();
          String key =
              queries.containsKey(title) ? title + "#" + target.get("refId").asString() : title;
          queries.put(key, target.get("rawSql").asString());
        }
      }
    }
  }

  /// 替换宏与变量后执行。
  private List<GenericRecord> run(String sql, String synthetic) {
    return rows(client, resolve(sql, synthetic));
  }

  /// 替换宏与变量；替换后还剩 `$` 说明漏了宏，直接失败。
  private static String resolve(String sql, String synthetic) {
    String resolved =
        sql.replace("$__fromTime", "toDateTime('2026-10-01 00:00:00', 'Asia/Shanghai')")
            .replace("$__toTime", "toDateTime('2026-10-03 23:59:59', 'Asia/Shanghai')")
            .replace("${synthetic:raw}", synthetic)
            .replace("${traffic}", "human")
            .replace("${mode:singlequote}", "'keyword'");
    assertThat(resolved).as("替换后的 SQL").doesNotContain("$");
    return resolved;
  }

  private static long lastDau(List<GenericRecord> rows) {
    return rows.getLast().getLong("DAU");
  }

  private static JsonNode readDashboard() {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (dir != null && !Files.isDirectory(dir.resolve("patra-infra"))) {
      dir = dir.getParent();
    }
    assertThat(dir).as("找不到仓库根目录").isNotNull();
    try {
      return JsonMapper.builder().build().readTree(Files.readString(dir.resolve(DASHBOARD)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 一个人一天的完整路径：访问、无结果检索、点结果、看文献、读全文。
  private static List<ObjectNode> visit(String name, boolean synthetic, LocalDate day) {
    String session = uuid();
    String device = device(name);
    ObjectNode page = at("page_viewed", "client", session, device, 0, day + " 10:00:00");
    ObjectNode search = at("search_performed", "server", session, device, 0, day + " 10:00:05");
    search
        .putObject("properties")
        .put("search_id", session)
        .put("mode", "keyword")
        .put("query", "nothing here")
        .put("result_count", 0);
    ObjectNode click = at("article_clicked", "client", session, device, 0, day + " 10:00:10");
    click.putObject("properties").put("list_type", "search").put("list_id", session);
    ObjectNode view = at("article_viewed", "server", session, device, 0, day + " 10:00:11");
    view.putObject("properties").put("article_id", "1001");
    ObjectNode read = at("fulltext_read", "client", session, device, 0, day + " 10:01:00");
    read.putObject("properties").put("article_id", "1001").put("visible_seconds", 10);
    List<ObjectNode> events = List.of(page, search, click, view, read);
    events.forEach(event -> event.put("is_synthetic", synthetic));
    return events;
  }

  private static String device(String name) {
    return name + "x".repeat(43 - name.length());
  }

  private static String uuid() {
    return UUID.randomUUID().toString();
  }
}
