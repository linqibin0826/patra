package dev.patra.analytics.simulator.report;

import dev.patra.analytics.simulator.live.LatencySummary;
import dev.patra.analytics.simulator.truth.ReconcileResult;
import dev.patra.analytics.simulator.truth.RunSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.json.JsonMapper;

/// 压测报告（spec §10）：把各运行目录里的摘要、每秒统计、parts 采样、对账结果与 ClickHouse 存储汇成 Markdown；
/// 文件里没有的（环境、磁盘前后、容器内存、真实浏览的反映时间、瓶颈结论）留「（人工填写）」。
public final class ReportWriter {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final double GIB = 1024.0 * 1024 * 1024;
  private static final String MANUAL = "（人工填写）";
  private static final int FIVE_MINUTES_OF_SAMPLES = 10;

  private ReportWriter() {}

  /// 写报告。
  ///
  /// @param runDirs 各运行的输出目录
  /// @param storage ClickHouse 各表存储
  /// @param date 报告日期
  /// @param out 输出
  public static void write(
      List<Path> runDirs, List<StorageRow> storage, LocalDate date, Writer out) {
    List<Run> runs = runDirs.stream().map(ReportWriter::load).toList();
    StringBuilder md = new StringBuilder();
    md.append("# v0.18 行为数据压测报告（").append(date).append("）\n\n");
    environment(md, runs);
    backfill(md, runs, storage);
    live(md, runs);
    reconcile(md, runs);
    md.append("## 4. 发现的瓶颈与结论\n\n").append(MANUAL).append("\n");
    try {
      out.write(md.toString());
      out.flush();
    } catch (IOException e) {
      throw new UncheckedIOException("写报告失败", e);
    }
  }

  /// 环境与各运行的参数。
  ///
  /// @param md 输出
  /// @param runs 运行
  private static void environment(StringBuilder md, List<Run> runs) {
    md.append("## 环境\n\n")
        .append("- mini 配置、各容器内存上限、ClickHouse / Kafka 版本：")
        .append(MANUAL)
        .append("\n");
    for (Run run : runs) {
      RunSummary s = run.summary();
      md.append("- 运行 ")
          .append(s.run())
          .append("（")
          .append(s.mode())
          .append("）：种子 ")
          .append(s.seed());
      if (!s.fingerprint().isEmpty()) {
        md.append("，参数指纹 `").append(s.fingerprint()).append("`");
      }
      md.append("\n");
    }
    md.append("\n");
  }

  /// 第 1 节：灌历史与存储。
  ///
  /// @param md 输出
  /// @param runs 运行
  /// @param storage 存储
  private static void backfill(StringBuilder md, List<Run> runs, List<StorageRow> storage) {
    md.append("## 1. 灌历史（D8）\n\n");
    for (Run run : runs) {
      RunSummary s = run.summary();
      if (!"backfill".equals(s.mode())) {
        continue;
      }
      md.append("- 运行 ")
          .append(s.run())
          .append("：写入 ")
          .append(number(s.deliveredEvents()))
          .append(" 条（另重发 ")
          .append(number(s.resentEvents()))
          .append(" 条），")
          .append(s.eventsByDay().size())
          .append(" 天；用时")
          .append(MANUAL)
          .append("\n");
    }
    md.append("\n存储（`system.parts`，活跃片段）：\n\n")
        .append("| 表 | 行数 | 压缩后 | 未压缩 | 压缩比 | 每行字节 | 分区数 |\n")
        .append("|---|---|---|---|---|---|---|\n");
    long total = 0;
    for (StorageRow row : storage) {
      total += row.bytesOnDisk();
      md.append("| ")
          .append(row.table())
          .append(" | ")
          .append(number(row.rows()))
          .append(" | ")
          .append(gib(row.bytesOnDisk()))
          .append(" | ")
          .append(gib(row.uncompressedBytes()))
          .append(" | ")
          .append(format("%.1f", ratio(row.uncompressedBytes(), row.compressedBytes())))
          .append(" | ")
          .append(format("%.1f", ratio(row.bytesOnDisk(), row.rows())))
          .append(" | ")
          .append(row.partitions())
          .append(" |\n");
    }
    md.append("\n- 合计磁盘占用 ")
        .append(gib(total))
        .append("\n")
        .append("- 磁盘：灌前剩余 … GB → 灌后 … GB（要求 ≥ 20 GB）")
        .append(MANUAL)
        .append("\n\n");
  }

  /// 第 2 节：实时压测。
  ///
  /// @param md 输出
  /// @param runs 运行
  private static void live(StringBuilder md, List<Run> runs) {
    md.append("## 2. 实时压测（D9）\n\n");
    for (Run run : runs) {
      RunSummary s = run.summary();
      if (!"live".equals(s.mode())) {
        continue;
      }
      md.append("### 运行 ")
          .append(s.run())
          .append("\n\n")
          .append("- 送达 ")
          .append(number(s.deliveredEvents()))
          .append(" 条，失败 ")
          .append(number(s.failedEvents()))
          .append(" 条，结果不明 ")
          .append(number(s.uncertainEvents()))
          .append(" 条，重发 ")
          .append(number(s.resentEvents()))
          .append(" 条\n");
      liveStats(md, run.dir(), run.latency());
      if (run.latency() != null) {
        LatencySummary l = run.latency();
        md.append("- 请求 ")
            .append(number(l.requests()))
            .append(" 个；延迟 p50 ")
            .append(l.p50Millis())
            .append(" ms · p95 ")
            .append(l.p95Millis())
            .append(" ms · p99 ")
            .append(l.p99Millis())
            .append(" ms\n");
      }
      parts(md, run.dir());
      lag(md, run.dir());
      memory(md, run.dir());
      md.append("- 一次真实浏览多久反映到计数上：").append(MANUAL).append("\n\n");
    }
  }

  /// 每秒统计：平均速率、状态码、每分钟速率。只算到达窗口里的秒（不含停发后的收尾）。
  ///
  /// @param md 输出
  /// @param dir 运行目录
  /// @param window 到达窗口；没有 latency.json 时算全部
  private static void liveStats(StringBuilder md, Path dir, LatencySummary window) {
    List<String[]> rows =
        csv(dir.resolve("live-stats.csv")).stream()
            .filter(row -> inWindow(row[0], window))
            .toList();
    if (rows.isEmpty()) {
      return;
    }
    long delivered = 0;
    long ok = 0;
    long busy = 0;
    long other = 0;
    Map<String, long[]> perMinute = new TreeMap<>();
    for (String[] row : rows) {
      delivered += Long.parseLong(row[2]);
      ok += Long.parseLong(row[4]);
      busy += Long.parseLong(row[5]);
      other += Long.parseLong(row[6]);
      long[] minute = perMinute.computeIfAbsent(row[0].substring(0, 16), k -> new long[2]);
      minute[0] += Long.parseLong(row[2]);
      minute[1]++;
    }
    md.append("- 速率（到达窗口内）：平均 ")
        .append(number(Math.round(delivered / (double) rows.size())))
        .append(" 条/秒；状态码 202 ×")
        .append(number(ok))
        .append("、503 ×")
        .append(number(busy))
        .append("、其他 ×")
        .append(number(other))
        .append("\n\n")
        .append("| 分钟（UTC） | 送达条/秒 |\n|---|---|\n");
    perMinute.forEach(
        (minute, v) ->
            md.append("| ")
                .append(minute)
                .append(" | ")
                .append(number(Math.round(v[0] / (double) v[1])))
                .append(" |\n"));
    md.append("\n");
  }

  /// parts 采样：新鲜度与片段数。
  ///
  /// @param md 输出
  /// @param dir 运行目录
  private static void parts(StringBuilder md, Path dir) {
    List<String[]> rows = csv(dir.resolve("parts.csv"));
    if (rows.isEmpty()) {
      md.append("- 新鲜度与片段：没有 parts.csv（压测时没给 `--clickhouse-url`）\n");
      return;
    }
    List<Double> freshness = new ArrayList<>();
    long maxParts = 0;
    long maxInPartition = 0;
    long maxMerges = 0;
    for (String[] row : rows) {
      maxParts = Math.max(maxParts, Long.parseLong(row[1]));
      maxInPartition = Math.max(maxInPartition, Long.parseLong(row[2]));
      maxMerges = Math.max(maxMerges, Long.parseLong(row[6]));
      double value = Double.parseDouble(row[7]);
      if (value >= 0) {
        freshness.add(value);
      }
    }
    if (!freshness.isEmpty()) {
      int window = Math.min(FIVE_MINUTES_OF_SAMPLES, freshness.size());
      double head = mean(freshness.subList(0, window));
      double tail = mean(freshness.subList(freshness.size() - window, freshness.size()));
      boolean rising = tail > head * 1.5 && tail > 5;
      md.append("- 新鲜度峰值 ")
          .append(trim(freshness.stream().mapToDouble(d -> d).max().orElse(0)))
          .append(" 秒；前 5 分钟均值 ")
          .append(format("%.2f", head))
          .append(" 秒 → 后 5 分钟均值 ")
          .append(format("%.2f", tail))
          .append(" 秒（持续上涨：")
          .append(rising ? "是" : "否")
          .append("）\n");
    }
    md.append("- 片段：events 活跃片段最多 ")
        .append(maxParts)
        .append(" 个，单分区最多 ")
        .append(maxInPartition)
        .append(" 个；同时进行的合并最多 ")
        .append(maxMerges)
        .append(" 个\n");
  }

  /// Prometheus 导出的消费滞后（条）。
  ///
  /// @param md 输出
  /// @param dir 运行目录
  private static void lag(StringBuilder md, Path dir) {
    List<String[]> rows = csv(dir.resolve("lag.csv"));
    if (rows.isEmpty()) {
      md.append("- 消费滞后（条）：没有 lag.csv（压测时没给 `--prometheus-url`）\n");
      return;
    }
    double max = rows.stream().mapToDouble(r -> Double.parseDouble(r[2])).max().orElse(0);
    md.append("- 消费滞后（条）：峰值 ").append(number(Math.round(max))).append("\n");
  }

  /// 容器内存（mini 上 `docker stats` 采样的 `mem.csv`：`time,container,mem_mib,limit_mib`）。
  ///
  /// @param md 输出
  /// @param dir 运行目录
  private static void memory(StringBuilder md, Path dir) {
    List<String[]> rows = csv(dir.resolve("mem.csv"));
    if (rows.isEmpty()) {
      md.append("- 容器内存（人工填写：把 mini 上采样的 mem.csv 放进运行目录后重出报告）\n");
      return;
    }
    Map<String, double[]> peaks = new TreeMap<>();
    for (String[] row : rows) {
      double[] peak = peaks.computeIfAbsent(row[1], k -> new double[2]);
      peak[0] = Math.max(peak[0], Double.parseDouble(row[2]));
      peak[1] = Double.parseDouble(row[3]);
    }
    md.append("- 容器内存峰值：\n\n| 容器 | 峰值 MiB | 上限 MiB |\n|---|---|---|\n");
    peaks.forEach(
        (name, p) ->
            md.append("| ")
                .append(name)
                .append(" | ")
                .append(format("%.0f", p[0]))
                .append(" | ")
                .append(format("%.0f", p[1]))
                .append(" |\n"));
    md.append("\n");
  }

  /// 第 3 节：对账。
  ///
  /// @param md 输出
  /// @param runs 运行
  private static void reconcile(StringBuilder md, List<Run> runs) {
    md.append("## 3. 对账（D10）\n\n")
        .append("| 运行 | 看过误差 | 读过全文误差 | 看过：真值 / ClickHouse | 读过全文：真值 / ClickHouse")
        .append(" | 写入行数 / 去重后条数 | 通过 |\n|---|---|---|---|---|---|---|\n");
    for (Run run : runs) {
      ReconcileResult r = run.reconcile();
      if (r == null) {
        continue;
      }
      md.append("| ")
          .append(run.summary().run())
          .append(" | ")
          .append(percent(r.viewersError()))
          .append(" | ")
          .append(percent(r.fulltextError()))
          .append(" | ")
          .append(number(r.truthViewers()))
          .append(" / ")
          .append(number(r.actualViewers()))
          .append(" | ")
          .append(number(r.truthFulltext()))
          .append(" / ")
          .append(number(r.actualFulltext()))
          .append(" | ")
          .append(number(r.writtenRows()))
          .append(" / ")
          .append(number(r.uniqueEvents()))
          .append(" | ")
          .append(r.passed() ? "是" : "否")
          .append(" |\n");
    }
    md.append("\n");
  }

  /// 这一秒是否在到达窗口里。
  ///
  /// @param second 每秒统计的时间戳
  /// @param window 到达窗口；`null` 视为全在
  /// @return 是否
  private static boolean inWindow(String second, LatencySummary window) {
    if (window == null || window.arrivalStart() == null || window.arrivalEnd() == null) {
      return true;
    }
    Instant at = Instant.parse(second);
    return !at.isBefore(window.arrivalStart()) && at.isBefore(window.arrivalEnd());
  }

  /// 读一个运行目录。
  ///
  /// @param dir 目录
  /// @return 运行
  private static Run load(Path dir) {
    Path reconcile = dir.resolve("reconcile.json");
    Path latency = dir.resolve("latency.json");
    return new Run(
        dir,
        RunSummary.read(dir.resolve("summary.json"), MAPPER),
        Files.exists(reconcile) ? ReconcileResult.read(reconcile, MAPPER) : null,
        Files.exists(latency) ? LatencySummary.read(latency, MAPPER) : null);
  }

  /// 读 CSV（去掉表头）；文件不存在时为空。
  ///
  /// @param file 文件
  /// @return 行
  private static List<String[]> csv(Path file) {
    if (!Files.exists(file)) {
      return List.of();
    }
    try {
      List<String> lines = Files.readAllLines(file);
      return lines.stream().skip(1).filter(l -> !l.isBlank()).map(l -> l.split(",")).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("读 " + file + " 失败", e);
    }
  }

  private static String number(long value) {
    return String.format(Locale.ROOT, "%,d", value);
  }

  private static String gib(long bytes) {
    return format("%.2f", bytes / GIB) + " GiB";
  }

  private static String percent(double ratio) {
    return format("%.2f", ratio * 100) + "%";
  }

  private static String format(String pattern, double value) {
    return String.format(Locale.ROOT, pattern, value);
  }

  private static double ratio(long numerator, long denominator) {
    return denominator == 0 ? 0 : numerator / (double) denominator;
  }

  private static double mean(List<Double> values) {
    return values.stream().mapToDouble(d -> d).average().orElse(0);
  }

  /// 去掉多余的小数位（3.25 而不是 3.250）。
  ///
  /// @param value 值
  /// @return 文本
  private static String trim(double value) {
    String text = format("%.3f", value);
    return text.replaceAll("0+$", "").replaceAll("\\.$", "");
  }

  /// 一个运行目录的内容。
  ///
  /// @param dir 目录
  /// @param summary 摘要
  /// @param reconcile 对账结果；没对账为 `null`
  /// @param latency 全程延迟；灌历史为 `null`
  private record Run(
      Path dir, RunSummary summary, ReconcileResult reconcile, LatencySummary latency) {}
}
