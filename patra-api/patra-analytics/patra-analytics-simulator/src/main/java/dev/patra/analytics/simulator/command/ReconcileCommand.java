package dev.patra.analytics.simulator.command;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.simulator.truth.ReconcileResult;
import dev.patra.analytics.simulator.truth.Reconciler;
import dev.patra.analytics.simulator.truth.RunScope;
import dev.patra.analytics.simulator.truth.RunSummary;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import tools.jackson.databind.json.JsonMapper;

/// `reconcile`：拿运行目录里的真值与 ClickHouse 汇总表对账（spec §7.3），误差超过 1% 退出码为 1。
@Command(
    name = "reconcile",
    mixinStandardHelpOptions = true,
    description = "对账：真值 vs ClickHouse 汇总表")
public final class ReconcileCommand implements Callable<Integer> {

  @Mixin RunOptions run;
  @Mixin ClickHouseOptions clickhouse;

  /// 执行。
  ///
  /// @return 退出码：通过 0，未通过 1
  @Override
  public Integer call() {
    run.validate();
    JsonMapper mapper = JsonMapper.builder().build();
    Path dir = run.runDirectory();
    RunSummary summary = RunSummary.read(dir.resolve("summary.json"), mapper);
    try (Client client = clickhouse.client()) {
      ReconcileResult result =
          new Reconciler(client, new RunScope(run.run))
              .reconcile(dir.resolve("truth.csv.gz"), summary);
      result.write(dir.resolve("reconcile.json"), mapper);
      System.out.printf(
          "看过：真值 %,d / ClickHouse %,d，误差 %.3f%%；读过全文：真值 %,d / ClickHouse %,d，误差 %.3f%%%n"
              + "写入 %,d 行，去重后 %,d 条（送达 %,d 条，结果不明 %,d 条）；%s%n",
          result.truthViewers(),
          result.actualViewers(),
          result.viewersError() * 100,
          result.truthFulltext(),
          result.actualFulltext(),
          result.fulltextError() * 100,
          result.writtenRows(),
          result.uniqueEvents(),
          result.deliveredEvents(),
          result.uncertainEvents(),
          result.passed() ? "通过" : "未通过：" + result.sampleMismatches());
      return result.passed() ? 0 : 1;
    }
  }
}
