package dev.patra.analytics.simulator.command;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.simulator.event.BeijingDays;
import dev.patra.analytics.simulator.report.ReportWriter;
import dev.patra.analytics.simulator.report.StorageRow;
import dev.patra.analytics.simulator.report.StorageStats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/// `report`：把若干运行的结果与 ClickHouse 存储汇成压测报告（spec §10）。
@Command(name = "report", mixinStandardHelpOptions = true, description = "压测报告：汇总各运行与存储")
public final class ReportCommand implements Callable<Integer> {

  @Mixin ClickHouseOptions clickhouse;

  @Option(names = "--run", required = true, description = "要汇总的运行号，可重复")
  List<Integer> runs;

  @Option(
      names = "--out",
      defaultValue = "simulator-out",
      description = "输出根目录，默认 ${DEFAULT-VALUE}")
  Path out;

  @Option(names = "--output", required = true, description = "报告 Markdown 的路径")
  Path output;

  /// 执行。
  ///
  /// @return 退出码
  @Override
  public Integer call() {
    List<Path> dirs = runs.stream().map(r -> OutputDirectory.prepare(out, r)).toList();
    List<StorageRow> storage;
    try (Client client = clickhouse.client()) {
      storage = StorageStats.query(client);
    }
    try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
      ReportWriter.write(dirs, storage, LocalDate.now(BeijingDays.ZONE), writer);
    } catch (IOException e) {
      throw new UncheckedIOException("写报告失败：" + output, e);
    }
    System.out.println("报告已写到 " + output);
    return 0;
  }
}
