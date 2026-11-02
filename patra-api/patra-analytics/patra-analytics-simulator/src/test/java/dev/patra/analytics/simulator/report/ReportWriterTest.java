package dev.patra.analytics.simulator.report;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.live.LatencySummary;
import dev.patra.analytics.simulator.truth.ReconcileResult;
import dev.patra.analytics.simulator.truth.RunSummary;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ReportWriterTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @TempDir Path temp;

  @Test
  void writes_template_sections_with_computed_numbers() throws IOException {
    Path backfill = temp.resolve("run-001");
    Path live = temp.resolve("run-002");
    Files.createDirectories(backfill);
    Files.createDirectories(live);
    summary("backfill", 1, 1_000_000, 10_000, Map.of("2026-11-01", 1_000_000L))
        .write(backfill.resolve("summary.json"), MAPPER);
    reconcile(0.0).write(backfill.resolve("reconcile.json"), MAPPER);
    summary("live", 2, 72_000, 700, Map.of("2026-11-02", 72_000L))
        .write(live.resolve("summary.json"), MAPPER);
    reconcile(0.004).write(live.resolve("reconcile.json"), MAPPER);
    new LatencySummary(
            3_000,
            12,
            40,
            95,
            Instant.parse("2026-11-02T02:00:00Z"),
            Instant.parse("2026-11-02T02:02:00Z"))
        .write(live.resolve("latency.json"), MAPPER);
    StringBuilder stats =
        new StringBuilder(
            "second,sent,delivered,failed,status_202,status_503,status_other,p50_ms,p95_ms,p99_ms,online,in_flight\n");
    for (int s = 0; s < 120; s++) {
      stats.append(
          "2026-11-02T02:%02d:%02dZ,1200,1200,0,500,0,0,12,40,95,3000,20\n"
              .formatted(s / 60, s % 60));
    }
    for (int s = 0; s < 30; s++) {
      // 停发后的收尾：不该算进速率
      stats.append("2026-11-02T02:02:%02dZ,10,10,0,5,0,0,12,40,95,30,1\n".formatted(s));
    }
    Files.writeString(live.resolve("live-stats.csv"), stats);
    Files.writeString(
        live.resolve("parts.csv"),
        """
        time,events_active_parts,max_parts_in_partition,asr_parts,sq_parts,dpa_parts,merges_running,freshness_seconds
        2026-11-02T02:00:30Z,40,12,30,30,20,1,1.5
        2026-11-02T02:01:00Z,55,18,31,31,21,2,3.25
        """);
    List<StorageRow> storage =
        List.of(
            new StorageRow(
                "events", 100_000_000, 4_000_000_000L, 3_900_000_000L, 39_000_000_000L, 88));
    StringWriter out = new StringWriter();

    ReportWriter.write(List.of(backfill, live), storage, LocalDate.of(2026, 11, 3), out);

    String report = out.toString();
    assertThat(report)
        .contains("# v0.18 行为数据压测报告（2026-11-03）")
        .contains("## 1. 灌历史（D8）")
        .contains("## 2. 实时压测（D9）")
        .contains("## 3. 对账（D10）")
        .contains("## 4. 发现的瓶颈与结论")
        .contains("| events | 100,000,000 | 3.73 GiB | 36.32 GiB | 10.0 | 40.0 | 88 |")
        .contains("1,000,000 条")
        .contains("1,200 条/秒")
        .contains("p50 12 ms · p95 40 ms · p99 95 ms")
        .contains("新鲜度峰值 3.25 秒")
        .contains("events 活跃片段最多 55 个，单分区最多 18 个")
        .contains("| 1 | 0.00% |")
        .contains("| 2 | 0.40% |")
        .contains("容器内存（人工填写")
        .contains("一次真实浏览多久反映到计数上：（人工填写）");
  }

  private static RunSummary summary(
      String mode, int run, long delivered, long resent, Map<String, Long> days) {
    return RunSummary.builder()
        .mode(mode)
        .run(run)
        .seed(42)
        .fingerprint("ab12cd34")
        .deliveredEvents(delivered)
        .resentEvents(resent)
        .failedEvents(0)
        .sessionsByKind(Map.of("HUMAN", 10L))
        .botEventsByReason(Map.of())
        .eventsByDay(days)
        .build();
  }

  private static ReconcileResult reconcile(double error) {
    return ReconcileResult.builder()
        .truthViewers(1_000)
        .actualViewers(1_000)
        .viewersError(error)
        .truthFulltext(300)
        .actualFulltext(300)
        .fulltextError(error)
        .writtenRows(1_010)
        .deliveredEvents(1_000)
        .uniqueEvents(1_000)
        .passed(true)
        .build();
  }
}
