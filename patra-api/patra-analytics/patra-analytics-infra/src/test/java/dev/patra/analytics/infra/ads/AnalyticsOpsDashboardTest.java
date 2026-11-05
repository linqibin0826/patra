package dev.patra.analytics.infra.ads;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 运营分析看板 JSON 的显示配置（运营分析面板 spec §5）：在 mini 上截图发现的三处显示问题不再回来。
class AnalyticsOpsDashboardTest {

  private static final String DASHBOARD =
      "patra-infra/docker/grafana/provisioning/dashboards/patra/analytics-ops.json";

  private final List<JsonNode> panels = panels();

  /// 文献 ID 约 3.5×10¹⁷，SQL 已转成字符串，但面板默认单位 short 会把数字样的字符串缩写成「352 Quadr」；
  /// 检索词也可能是纯数字。这两列按文本显示。
  @Test
  void id_and_term_columns_render_as_text() {
    assertThat(unitOverride("Unique Item Requests 前 20 篇", "article_id")).isEqualTo("string");
    assertThat(unitOverride("无结果词排行（前 50）", "检索词")).isEqualTo("string");
  }

  /// 纵轴从数据的最小值起画时，9 条看着比 3 条还矮；所有折线 / 柱状图的纵轴都从 0 起。
  @Test
  void timeseries_axes_start_at_zero() {
    List<JsonNode> timeseries =
        panels.stream().filter(panel -> "timeseries".equals(panel.get("type").asString())).toList();

    assertThat(timeseries).isNotEmpty();
    assertThat(timeseries)
        .allSatisfy(
            panel ->
                assertThat(panel.at("/fieldConfig/defaults/custom/axisSoftMin").asInt(-1))
                    .as("面板「%s」的纵轴下限", panel.get("title").asString())
                    .isZero());
  }

  /// 第 N 天还没过完的格子是 NULL，不能按 0 涂成红色：空值映射成透明。
  @Test
  void null_retention_cells_are_not_colored() {
    JsonNode override = override("同期群留存", "byRegexp", ".*%$");

    JsonNode nullMapping = null;
    for (JsonNode property : override.get("properties")) {
      if ("mappings".equals(property.get("id").asString())) {
        for (JsonNode mapping : property.get("value")) {
          if ("special".equals(mapping.get("type").asString())) {
            nullMapping = mapping;
          }
        }
      }
    }
    assertThat(nullMapping).as("百分比列要有空值映射").isNotNull();
    assertThat(nullMapping.at("/options/match").asString()).isEqualTo("null+nan");
    assertThat(nullMapping.at("/options/result/color").asString()).isEqualTo("transparent");
  }

  /// 某面板里按列名覆盖的单位，没有时返回 `null`。
  private String unitOverride(String title, String column) {
    for (JsonNode property : override(title, "byName", column).get("properties")) {
      if ("unit".equals(property.get("id").asString())) {
        return property.get("value").asString();
      }
    }
    return null;
  }

  private JsonNode override(String title, String matcher, String options) {
    JsonNode panel =
        panels.stream()
            .filter(p -> title.equals(p.get("title").asString()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("看板里没有面板「" + title + "」"));
    for (JsonNode override : panel.at("/fieldConfig/overrides")) {
      if (matcher.equals(override.at("/matcher/id").asString())
          && options.equals(override.at("/matcher/options").asString())) {
        return override;
      }
    }
    throw new AssertionError("面板「" + title + "」没有对「" + options + "」的覆盖");
  }

  /// 看板里全部面板（展开折叠行）。
  private static List<JsonNode> panels() {
    List<JsonNode> all = new ArrayList<>();
    collect(readDashboard().get("panels"), all);
    return all;
  }

  private static void collect(JsonNode panels, List<JsonNode> all) {
    for (JsonNode panel : panels) {
      all.add(panel);
      if (panel.has("panels")) {
        collect(panel.get("panels"), all);
      }
    }
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
}
