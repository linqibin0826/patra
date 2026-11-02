package dev.patra.analytics.simulator.backfill;

import dev.patra.analytics.simulator.event.BeijingDays;
import dev.patra.analytics.simulator.event.EnrichedEventWriter;
import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.event.SyntheticIds;
import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.Duplicates;
import dev.patra.analytics.simulator.model.Population;
import dev.patra.analytics.simulator.model.SessionMix;
import dev.patra.analytics.simulator.model.SimSession;
import dev.patra.analytics.simulator.model.TrafficModel;
import dev.patra.analytics.simulator.support.Sleeper;
import dev.patra.analytics.simulator.truth.RunSummary;
import dev.patra.analytics.simulator.truth.RunTally;
import dev.patra.analytics.simulator.truth.TruthFile;
import dev.patra.analytics.simulator.truth.TruthLedger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

/// 灌历史（spec §8）：按日程一天一天、一块一块地生成并写入，一次写入只含一天。
///
/// 同一天的写入顺序：前一天跨午夜落到今天的部分（`spill`）→ 各块（每块带上更早写入里被选中重发的副本）→
/// 最后一块的副本（`dup`）。真值只记「计入」的天（`onlyDays` 以内）；`fromDay` 之前的天视为已写过，只生成不写。
public final class BackfillRunner {

  private static final System.Logger LOG = System.getLogger(BackfillRunner.class.getName());
  private static final int MAX_ATTEMPTS = 20;
  private static final long MAX_BACKOFF_MILLIS = 30_000;

  private final BackfillParams params;
  private final BackfillPlan plan;
  private final ChunkGenerator generator;
  private final EventSink sink;
  private final TruthLedger ledger;
  private final EnrichedEventWriter writer;
  private final Path outDir;
  private final JsonMapper jsonMapper;
  private final Sleeper sleeper;
  private final String fingerprint;
  private final RunTally tally = new RunTally();
  private final Set<LocalDate> counted = new HashSet<>();
  private final Map<LocalDate, List<String>> pendingCopies = new HashMap<>();

  private BackfillRunner(
      BackfillParams params,
      BackfillPlan plan,
      ChunkGenerator generator,
      EventSink sink,
      TruthLedger ledger,
      Path outDir,
      JsonMapper jsonMapper,
      Sleeper sleeper) {
    this.params = params;
    this.plan = plan;
    this.generator = generator;
    this.sink = sink;
    this.ledger = ledger;
    this.writer = new EnrichedEventWriter(jsonMapper);
    this.outDir = outDir;
    this.jsonMapper = jsonMapper;
    this.sleeper = sleeper;
    this.fingerprint = params.fingerprint();
    int limit = params.onlyDays() != null ? params.onlyDays() : plan.days().size();
    counted.addAll(plan.days().subList(0, Math.min(limit, plan.days().size())));
  }

  /// 按参数装配一次灌历史。
  ///
  /// @param params 参数
  /// @param model 流量模型
  /// @param pool 文献池
  /// @param sink 写入端
  /// @param outDir 输出目录（真值、摘要）
  /// @param jsonMapper JSON
  /// @param sleeper 重试退避
  /// @return 运行器
  public static BackfillRunner create(
      BackfillParams params,
      TrafficModel model,
      ArticlePool pool,
      EventSink sink,
      Path outDir,
      JsonMapper jsonMapper,
      Sleeper sleeper) {
    Population population = Population.build(model, new SyntheticIds(params.run()), params.seed());
    SessionMix mix = new SessionMix(model, pool, population);
    BackfillPlan plan = BackfillPlan.of(params, model, mix.meanEvents(params.seed()));
    ChunkGenerator generator =
        new ChunkGenerator(plan, mix, model, population.size(), params.seed());
    TruthLedger ledger = new TruthLedger(pool, EnrichedEventWriter::derivedAt);
    return new BackfillRunner(params, plan, generator, sink, ledger, outDir, jsonMapper, sleeper);
  }

  /// 跑完整个日程，写出真值文件与摘要。
  ///
  /// @return 摘要
  /// @throws DiskFullException ClickHouse 磁盘到了红线
  public RunSummary run() {
    createDirectories();
    List<String> spill = List.of();
    try (TruthFile.Appender truth = TruthFile.open(outDir.resolve("truth.csv.gz"))) {
      for (LocalDate day : plan.days()) {
        if (!counted.contains(day)) {
          break;
        }
        boolean write = params.fromDay() == null || !day.isBefore(params.fromDay());
        if (!spill.isEmpty()) {
          List<String> lines = spill;
          insert(write, () -> lines.iterator(), token(day, "spill"));
        }
        List<String> nextSpill = new ArrayList<>();
        for (int chunk = 0; chunk < plan.chunks(day); chunk++) {
          writeChunk(day, chunk, write, nextSpill);
        }
        List<String> copies = pendingCopies.remove(day);
        if (copies != null) {
          tally.resent(copies.size());
          insert(write, copies::iterator, token(day, "dup"));
        }
        if (write) {
          sink.afterDay(day);
        }
        truth.append(ledger.closeDaysBefore(day.plusDays(1)));
        spill = counted.contains(day.plusDays(1)) ? nextSpill : List.of();
        LOG.log(System.Logger.Level.INFO, "{0} 完成，累计 {1} 条", day, tally.deliveredEvents());
      }
      truth.append(ledger.closeAll());
    }
    RunSummary summary = tally.summary("backfill", params.run(), params.seed(), fingerprint);
    summary.write(outDir.resolve("summary.json"), jsonMapper);
    return summary;
  }

  /// 生成一块并写入：当天的行 + 更早写入里选中重发的副本；跨到次日的行留给次日。
  ///
  /// @param day 日期
  /// @param chunk 块号
  /// @param write 是否真的写
  /// @param nextSpill 次日的行（追加）
  private void writeChunk(LocalDate day, int chunk, boolean write, List<String> nextSpill) {
    DayChunk generated = generator.generate(day, chunk);
    List<String> copies = pendingCopies.getOrDefault(day, List.of());
    pendingCopies.remove(day);
    List<SimEvent> today = new ArrayList<>();
    for (SimSession session : generated.sessions()) {
      tally.session(session.kind().name());
      List<SimEvent> countedEvents = new ArrayList<>();
      for (SimEvent event : session.events()) {
        LocalDate eventDay = BeijingDays.of(EnrichedEventWriter.derivedAt(event));
        if (!counted.contains(eventDay)) {
          continue;
        }
        countedEvents.add(event);
        if (event.botReason() != null) {
          tally.botEvent(event.botReason());
        }
        String line = null;
        if (eventDay.equals(day)) {
          today.add(event);
        } else {
          line = writer.write(event);
          nextSpill.add(line);
        }
        tally.delivered(eventDay.toString(), 1);
        if (Duplicates.chosen(event.eventId())) {
          pendingCopies
              .computeIfAbsent(eventDay, d -> new ArrayList<>())
              .add(line != null ? line : writer.write(event));
        }
      }
      ledger.accept(session, countedEvents);
    }
    tally.resent(copies.size());
    insert(
        write,
        () -> Stream.concat(copies.stream(), today.stream().map(writer::write)).iterator(),
        token(day, Integer.toString(chunk)));
  }

  /// 写一次，可重试错误按指数退避重试同一批（同一令牌）；磁盘红线立即抛出。
  ///
  /// @param write 是否真的写（续跑时之前的天只生成）
  /// @param lines 行
  /// @param token 令牌
  private void insert(boolean write, Supplier<Iterator<String>> lines, String token) {
    if (!write) {
      return;
    }
    for (int attempt = 1; ; attempt++) {
      try {
        sink.insert(lines, token);
        return;
      } catch (DiskFullException e) {
        throw e;
      } catch (RuntimeException e) {
        if (attempt >= MAX_ATTEMPTS) {
          throw new IllegalStateException("写入 " + token + " 重试 " + attempt + " 次仍失败", e);
        }
        long backoff = Math.min(MAX_BACKOFF_MILLIS, 1_000L << Math.min(attempt - 1, 10));
        LOG.log(
            System.Logger.Level.WARNING,
            "写入 {0} 失败（第 {1} 次），{2} 毫秒后重试：{3}",
            token,
            attempt,
            backoff,
            e.getMessage());
        sleeper.sleep(backoff);
      }
    }
  }

  /// 令牌。
  ///
  /// @param day 日期
  /// @param part 块号、`spill`、`dup`
  /// @return 令牌
  private String token(LocalDate day, String part) {
    return BackfillTokens.of(params.run(), fingerprint, day, part);
  }

  /// 建输出目录。
  private void createDirectories() {
    try {
      Files.createDirectories(outDir);
    } catch (IOException e) {
      throw new UncheckedIOException("建输出目录失败：" + outDir, e);
    }
  }
}
