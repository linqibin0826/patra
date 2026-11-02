package dev.patra.analytics.simulator.command;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.simulator.live.CollectorClient;
import dev.patra.analytics.simulator.live.LagExporter;
import dev.patra.analytics.simulator.live.LiveParams;
import dev.patra.analytics.simulator.live.LiveRunner;
import dev.patra.analytics.simulator.live.LiveStats;
import dev.patra.analytics.simulator.live.PartsSampler;
import dev.patra.analytics.simulator.live.RateStep;
import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.TrafficModel;
import dev.patra.analytics.simulator.support.Sleeper;
import dev.patra.analytics.simulator.truth.RunScope;
import dev.patra.analytics.simulator.truth.RunSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tools.jackson.databind.json.JsonMapper;

/// `live`：实时压测，虚拟线程模拟并发读者经 HTTP 打采集端点（spec §9）。
@Command(name = "live", mixinStandardHelpOptions = true, description = "实时压测：开环发压打采集端点")
public final class LiveCommand implements Callable<Integer> {

  /// 门户调采集端点的超时（采集设计 §4.5）
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(6);
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration PROBE_PERIOD = Duration.ofSeconds(30);

  @Mixin RunOptions run;
  @Mixin ClickHouseOptions clickhouse;

  @Option(names = "--rate", defaultValue = "1200", description = "目标条/秒，默认 ${DEFAULT-VALUE}")
  double rate;

  @Option(
      names = "--duration",
      defaultValue = "30m",
      converter = DurationConverter.class,
      description = "时长，默认 ${DEFAULT-VALUE}")
  Duration duration;

  @Option(
      names = "--ramp",
      split = ",",
      description = "分级加压，如 500,1000,1500,2000（给了就不用 --rate/--duration）")
  List<Double> ramp;

  @Option(
      names = "--step-length",
      defaultValue = "5m",
      converter = DurationConverter.class,
      description = "每级时长，默认 ${DEFAULT-VALUE}")
  Duration stepLength;

  @Option(
      names = "--collector-url",
      required = true,
      description = "采集端点，如 http://100.103.73.27:9528/patra-analytics/events")
  URI collector;

  @Option(
      names = "--max-in-flight",
      defaultValue = "512",
      description = "同时在途的请求上限，默认 ${DEFAULT-VALUE}")
  int maxInFlight;

  @Option(
      names = "--drain-timeout",
      defaultValue = "5m",
      converter = DurationConverter.class,
      description = "停发后等在线会话走完的上限，默认 ${DEFAULT-VALUE}")
  Duration drainTimeout;

  @Option(
      names = "--warm-up",
      negatable = true,
      defaultValue = "true",
      fallbackValue = "true",
      description = "开跑前补上已在进行的会话，一开始就是稳态速率，默认开（--no-warm-up 关）")
  boolean warmUp;

  @Option(names = "--prometheus-url", description = "Prometheus 地址；给了就在结束后导出消费滞后曲线")
  URI prometheus;

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
    JsonMapper mapper = JsonMapper.builder().build();
    Path dir = run.runDirectory();
    OutputDirectory.writeParams(dir, params(), mapper);
    TrafficModel trafficModel = TrafficModel.load(model, mapper);
    ArticlePool pool =
        ArticlePool.load(articles, trafficModel.maxArticles(), trafficModel.zipfExponent());
    LiveStats stats = new LiveStats(Clock.systemUTC());
    CollectorClient client =
        new CollectorClient(
            collector, maxInFlight, MAX_ATTEMPTS, REQUEST_TIMEOUT, stats, Sleeper.REAL);
    LiveRunner runner =
        new LiveRunner(
            new LiveParams(
                run.run, run.seed, steps(), collector, maxInFlight, warmUp, drainTimeout),
            trafficModel,
            pool,
            client,
            stats,
            Clock.systemUTC(),
            Sleeper.REAL,
            mapper,
            dir);
    Instant start = Instant.now();
    RunSummary summary;
    if (clickhouse.present()) {
      try (Client ch = clickhouse.client();
          Writer parts =
              Files.newBufferedWriter(dir.resolve("parts.csv"), StandardCharsets.UTF_8)) {
        PartsSampler sampler = new PartsSampler(ch, new RunScope(run.run), Clock.systemUTC());
        runner.addProbe(PROBE_PERIOD, () -> sampler.sampleInto(parts));
        summary = runner.run();
      } catch (IOException e) {
        throw new UncheckedIOException("写 parts.csv 失败", e);
      }
    } else {
      summary = runner.run();
    }
    if (prometheus != null) {
      try (Writer lag = Files.newBufferedWriter(dir.resolve("lag.csv"), StandardCharsets.UTF_8)) {
        new LagExporter(prometheus, LagExporter.DEFAULT_QUERY).export(start, Instant.now(), lag);
      } catch (IOException e) {
        throw new UncheckedIOException("写 lag.csv 失败", e);
      }
    }
    System.out.printf(
        "压测完成：送达 %,d 条，失败 %,d 条，重发 %,d 条，输出在 %s%n",
        summary.deliveredEvents(), summary.failedEvents(), summary.resentEvents(), dir);
    return 0;
  }

  /// 各级速率：给了 `--ramp` 就每级 `--step-length`，否则 `--rate` 持续 `--duration`。
  ///
  /// @return 速率级
  List<RateStep> steps() {
    if (ramp != null && !ramp.isEmpty()) {
      return ramp.stream().map(r -> new RateStep(r, stepLength)).toList();
    }
    return List.of(new RateStep(rate, duration));
  }

  /// 写进 params.json 的参数（不含密码）。
  ///
  /// @return 参数
  private Map<String, Object> params() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("mode", "live");
    params.put("run", run.run);
    params.put("seed", run.seed);
    params.put(
        "steps", steps().stream().map(s -> s.eventsPerSecond() + "/s × " + s.length()).toList());
    params.put("collector", collector.toString());
    params.put("maxInFlight", maxInFlight);
    params.put("warmUp", warmUp);
    params.put("articles", articles.toString());
    params.put("model", model != null ? model.toString() : null);
    params.put("clickhouseUrl", clickhouse.url);
    params.put("prometheus", prometheus != null ? prometheus.toString() : null);
    return params;
  }
}
