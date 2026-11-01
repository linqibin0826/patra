package dev.patra.analytics.infra.collect.robot;

import dev.patra.analytics.domain.collect.port.robot.RobotListPort;
import dev.patra.analytics.infra.collect.support.AnalyticsResources;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// COUNTER-Robots 爬虫 UA 列表（埋点方案 §6.7 规则 `ua_list`）。
///
/// 列表是 `patra-analytics-api` 里的资源，运行时不联网；启动时把每个 `pattern` 编译为不区分大小写的正则，按顺序匹配。
@Component
public final class CounterRobotListAdapter implements RobotListPort {

  private final List<Pattern> patterns;

  /// 读入并编译列表。
  ///
  /// @param jsonMapper 解析列表
  public CounterRobotListAdapter(JsonMapper jsonMapper) {
    this.patterns = List.copyOf(load(jsonMapper));
  }

  /// UA 是否匹配列表里的任一模式。
  ///
  /// @param userAgent 原始 UA
  /// @return 匹配时为 `true`
  @Override
  public boolean listed(String userAgent) {
    if (userAgent == null || userAgent.isEmpty()) {
      return false;
    }
    return patterns.stream().anyMatch(pattern -> pattern.matcher(userAgent).find());
  }

  /// 列表里的模式数。
  ///
  /// @return 模式数
  int size() {
    return patterns.size();
  }

  /// 读列表并编译。
  ///
  /// @param jsonMapper 解析列表
  /// @return 编译好的模式
  private static List<Pattern> load(JsonMapper jsonMapper) {
    try (InputStream in =
        CounterRobotListAdapter.class
            .getClassLoader()
            .getResourceAsStream(AnalyticsResources.ROBOTS)) {
      if (in == null) {
        throw new IllegalStateException(
            "COUNTER-Robots 列表不在 classpath 上：" + AnalyticsResources.ROBOTS);
      }
      List<Pattern> compiled = new ArrayList<>();
      for (JsonNode entry : jsonMapper.readTree(in)) {
        compiled.add(Pattern.compile(entry.get("pattern").asString(), Pattern.CASE_INSENSITIVE));
      }
      return compiled;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
