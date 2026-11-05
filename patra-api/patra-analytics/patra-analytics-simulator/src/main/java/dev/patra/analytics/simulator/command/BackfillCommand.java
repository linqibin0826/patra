package dev.patra.analytics.simulator.command;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.simulator.backfill.BackfillParams;
import dev.patra.analytics.simulator.backfill.BackfillRunner;
import dev.patra.analytics.simulator.backfill.ClickHouseEventSink;
import dev.patra.analytics.simulator.event.BeijingDays;
import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.TrafficModel;
import dev.patra.analytics.simulator.support.Sleeper;
import dev.patra.analytics.simulator.truth.RunSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tools.jackson.databind.json.JsonMapper;

/// `backfill`：灌历史，直接批量写 ClickHouse 的 `events`（spec §8）。
@Command(
    name = "backfill",
    mixinStandardHelpOptions = true,
    description = "灌历史：近若干天的合成事件直写 ClickHouse")
public final class BackfillCommand implements Callable<Integer> {

  @Mixin RunOptions run;
  @Mixin ClickHouseOptions clickhouse;

  @Option(names = "--events", required = true, description = "事件总数（不含 1% 重发）")
  long events;

  @Option(names = "--days", defaultValue = "88", description = "铺几天（昨天往前），默认 ${DEFAULT-VALUE}")
  int days;

  @Option(names = "--end-day", description = "最后一天（北京时间，含），默认昨天；试灌与正式灌要用同一个值（写在试灌的 params.json 里）")
  LocalDate endDay;

  @Option(names = "--only-days", description = "只写最早的 N 天（试灌）")
  Integer onlyDays;

  @Option(names = "--from-day", description = "从这天起才写，之前的天只生成（续跑），如 2026-10-01")
  LocalDate fromDay;

  @Option(names = "--articles", required = true, description = "文献池 CSV（id,venue_id）")
  Path articles;

  @Option(names = "--model", description = "流量模型 JSON，默认内置")
  Path model;

  /// 执行。
  ///
  /// @return 退出码
  @Override
  public Integer call() {
    run.validate();
    if (endDay == null) {
      endDay = BeijingDays.of(Instant.now()).minusDays(1);
    }
    JsonMapper mapper = JsonMapper.builder().build();
    Path dir = run.runDirectory();
    OutputDirectory.writeParams(dir, params(), mapper);
    TrafficModel trafficModel = TrafficModel.load(model, mapper);
    ArticlePool pool =
        ArticlePool.load(articles, trafficModel.maxArticles(), trafficModel.zipfExponent());
    BackfillParams backfill =
        new BackfillParams(
            run.run, run.seed, events, days, endDay, onlyDays, fromDay, articles, model);
    try (Client client = clickhouse.client()) {
      RunSummary summary =
          BackfillRunner.create(
                  backfill,
                  trafficModel,
                  pool,
                  new ClickHouseEventSink(client),
                  dir,
                  mapper,
                  Sleeper.REAL)
              .run();
      System.out.printf(
          "灌历史完成：送达 %,d 条，重发 %,d 条，输出在 %s%n",
          summary.deliveredEvents(), summary.resentEvents(), dir);
    }
    return 0;
  }

  /// 写进 params.json 的参数（不含密码）。
  ///
  /// @return 参数
  Map<String, Object> params() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("mode", "backfill");
    params.put("run", run.run);
    params.put("seed", run.seed);
    params.put("events", events);
    params.put("days", days);
    params.put("endDay", endDay != null ? endDay.toString() : null);
    params.put("onlyDays", onlyDays);
    params.put("fromDay", fromDay != null ? fromDay.toString() : null);
    params.put("articles", articles.toString());
    params.put("model", model != null ? model.toString() : null);
    params.put("clickhouseUrl", clickhouse.url);
    params.put("clickhouseUser", clickhouse.user);
    params.put("clickhouseDatabase", clickhouse.database);
    return params;
  }
}
