package dev.patra.analytics.simulator.live;

import dev.patra.analytics.simulator.event.BeijingDays;
import dev.patra.analytics.simulator.event.CollectEnvelopeWriter;
import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.event.SyntheticIds;
import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.BotLabeler;
import dev.patra.analytics.simulator.model.Person;
import dev.patra.analytics.simulator.model.Population;
import dev.patra.analytics.simulator.model.SessionKind;
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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import tools.jackson.databind.json.JsonMapper;

/// 实时压测（spec §9）：开环发压——会话按泊松过程到达（速率 λ = 目标条/秒 ÷ 平均每会话事件数），每个会话一个虚拟线程，
/// 按真实停留时间走完跳转链；不论采集端点快慢都按设定速率产生新会话，服务跟不上时请求排队、延迟上升，问题暴露出来。
public final class LiveRunner {

  private static final System.Logger LOG = System.getLogger(LiveRunner.class.getName());
  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  /// 预热补会话的时间窗（比最长的会话略长）
  private static final Duration WARMUP_WINDOW = Duration.ofMinutes(30);

  /// 截断后等在途请求结束的上限（单次请求超时 × 重试）
  private static final Duration STOP_TIMEOUT = Duration.ofSeconds(60);

  private final LiveParams params;
  private final LiveStats stats;
  private final Clock clock;
  private final JsonMapper jsonMapper;
  private final Path outDir;
  private final SessionMix mix;
  private final OnlinePeople online;
  private final SessionReplayer replayer;
  private final TruthLedger ledger;
  private final double meanEvents;
  private final RunTally tally = new RunTally();
  private final AtomicInteger onlineSessions = new AtomicInteger();
  private final List<Probe> probes = new ArrayList<>();
  private Instant arrivalStart;
  private Instant arrivalEnd;

  /// 装配一次实时压测。
  ///
  /// @param params 参数
  /// @param model 流量模型
  /// @param pool 文献池
  /// @param client 发送端
  /// @param stats 统计（与发送端同一个）
  /// @param clock 时钟
  /// @param replaySleeper 会话重放的等待（测试换成不睡的）
  /// @param jsonMapper JSON
  /// @param outDir 输出目录
  public LiveRunner(
      LiveParams params,
      TrafficModel model,
      ArticlePool pool,
      CollectorClient client,
      LiveStats stats,
      Clock clock,
      Sleeper replaySleeper,
      JsonMapper jsonMapper,
      Path outDir) {
    this.params = params;
    this.stats = stats;
    this.clock = clock;
    this.jsonMapper = jsonMapper;
    this.outDir = outDir;
    Population population = Population.build(model, new SyntheticIds(params.run()), params.seed());
    this.mix = new SessionMix(model, pool, population);
    this.online = new OnlinePeople(population);
    this.replayer =
        new SessionReplayer(
            new CollectEnvelopeWriter(jsonMapper),
            client,
            clock,
            replaySleeper,
            task -> Thread.ofVirtual().start(task));
    this.ledger = new TruthLedger(pool, SimEvent::createdAt);
    this.meanEvents = mix.meanEvents(params.seed());
    stats.onlineSessions(onlineSessions::get);
  }

  /// 平均每会话事件数（含爬虫）。
  ///
  /// @return 条数
  public double meanEvents() {
    return meanEvents;
  }

  /// 压测期间按周期执行的采样（parts、新鲜度）。
  ///
  /// @param period 周期
  /// @param task 采样
  public void addProbe(Duration period, Runnable task) {
    probes.add(new Probe(period, task));
  }

  /// 跑完各级速率，等在线会话走完，写出每秒统计、真值、摘要。
  ///
  /// @return 摘要
  public RunSummary run() {
    createDirectories();
    ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    try (Writer csv =
        Files.newBufferedWriter(outDir.resolve("live-stats.csv"), StandardCharsets.UTF_8)) {
      ticker.scheduleAtFixedRate(() -> stats.flushSecond(csv), 1, 1, TimeUnit.SECONDS);
      for (Probe probe : probes) {
        ticker.scheduleAtFixedRate(
            probe.task(), 0, probe.period().toMillis(), TimeUnit.MILLISECONDS);
      }
      SplittableRandom random = new SplittableRandom(params.seed());
      if (params.warmUp()) {
        warmUp(params.ramp().getFirst(), random);
      }
      arrivalStart = clock.instant();
      for (RateStep step : params.ramp()) {
        LOG.log(
            System.Logger.Level.INFO, "速率 {0} 条/秒，持续 {1}", step.eventsPerSecond(), step.length());
        arrive(step, random);
      }
      arrivalEnd = clock.instant();
      drain();
      ticker.shutdown();
      ticker.awaitTermination(5, TimeUnit.SECONDS);
      stats.flushSecond(csv);
    } catch (IOException e) {
      throw new UncheckedIOException("写每秒统计失败", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("压测被中断", e);
    } finally {
      ticker.shutdownNow();
    }
    try (TruthFile.Appender truth = TruthFile.open(outDir.resolve("truth.csv.gz"))) {
      truth.append(ledger.closeAll());
    }
    tally.resent(replayer.resent());
    tally.failed(stats.totals().failed());
    tally.uncertain(stats.totals().uncertain());
    RunSummary summary = tally.summary("live", params.run(), params.seed(), "");
    summary.write(outDir.resolve("summary.json"), jsonMapper);
    LatencySummary.of(stats.overall(), arrivalStart, arrivalEnd)
        .write(outDir.resolve("latency.json"), jsonMapper);
    return summary;
  }

  /// 一级速率：按指数分布的间隔产生会话，到点就起一个虚拟线程。
  ///
  /// @param step 速率与时长
  /// @param random 随机源
  private void arrive(RateStep step, SplittableRandom random) {
    double lambda = step.eventsPerSecond() / meanEvents;
    long end = System.nanoTime() + step.length().toNanos();
    long next = System.nanoTime();
    while (true) {
      next += (long) (-Math.log(1 - random.nextDouble()) / lambda * NANOS_PER_SECOND);
      if (next >= end) {
        return;
      }
      long wait = next - System.nanoTime();
      if (wait > 0) {
        LockSupport.parkNanos(wait);
      }
      start(random.split());
    }
  }

  /// 预热（spec §9）：开环从零在线起步，要等最长的会话（声明爬虫最长约 25 分钟）走完才到稳态，前几分钟速率不够。
  /// 所以开跑前按第一级速率，在过去 30 分钟里按泊松过程补上「已经开始」的会话，各自只发还没到期的请求——
  /// 一开始在线会话的数量与进度就和稳态一样。已过去的请求不发，也不进真值。
  ///
  /// @param first 第一级速率
  /// @param random 随机源
  private void warmUp(RateStep first, SplittableRandom random) {
    Instant now = clock.instant();
    long count = Math.round(first.eventsPerSecond() / meanEvents * WARMUP_WINDOW.toSeconds());
    int alive = 0;
    for (long i = 0; i < count; i++) {
      SplittableRandom child = random.split();
      Instant start = now.minusMillis(child.nextLong(WARMUP_WINDOW.toMillis()));
      Person human = online.acquire(child);
      SimSession session = mix.next(start, child, online.nextFreshIndex(), human);
      if (session.events().stream().noneMatch(e -> !e.sentAt().isBefore(now))) {
        online.release(human);
        continue;
      }
      alive++;
      launch(session, human, child, true);
    }
    LOG.log(System.Logger.Level.INFO, "预热：补上 {0} 个进行中的会话", alive);
  }

  /// 产生一个会话并在虚拟线程里重放。
  ///
  /// @param random 这个会话专用的随机源
  private void start(SplittableRandom random) {
    Person human = online.acquire(random);
    launch(mix.next(clock.instant(), random, online.nextFreshIndex(), human), human, random, false);
  }

  /// 在虚拟线程里重放会话，结束时把送达的部分（按采集端点的规则打好爬虫标记）记进真值。
  ///
  /// @param session 会话
  /// @param human 为它占用的人（不是真人会话就立即释放）
  /// @param random 随机源
  /// @param warm 是否预热会话
  private void launch(SimSession session, Person human, SplittableRandom random, boolean warm) {
    if (session.kind() != SessionKind.HUMAN) {
      online.release(human);
    }
    tally.session(session.kind().name());
    onlineSessions.incrementAndGet();
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                List<SimEvent> delivered =
                    warm ? replayer.replayWarm(session, random) : replayer.replay(session, random);
                List<SimEvent> labeled = BotLabeler.label(session.withEvents(delivered)).events();
                ledger.accept(session, labeled);
                for (SimEvent event : labeled) {
                  tally.delivered(BeijingDays.of(event.createdAt()).toString(), 1);
                  if (event.botReason() != null) {
                    tally.botEvent(event.botReason());
                  }
                }
              } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "会话重放失败：" + e.getMessage(), e);
              } finally {
                if (session.kind() == SessionKind.HUMAN) {
                  online.release(human);
                }
                onlineSessions.decrementAndGet();
              }
            });
  }

  /// 停止产生新会话后，等在线会话与待发的重发走完；到上限还没走完的会话就地截断（不再发），
  /// 等它们把已送达的部分记进真值后再返回——否则它们会在摘要写完后继续往管线里送事件，真值对不上。
  private void drain() {
    long deadline = System.nanoTime() + params.drainTimeout().toNanos();
    while ((onlineSessions.get() > 0 || replayer.pendingResends() > 0)
        && System.nanoTime() < deadline) {
      LockSupport.parkNanos(NANOS_PER_SECOND / 10);
    }
    if (onlineSessions.get() == 0 && replayer.pendingResends() == 0) {
      return;
    }
    LOG.log(System.Logger.Level.WARNING, "到等待上限，截断还在线的 {0} 个会话", onlineSessions.get());
    replayer.stop();
    long stopDeadline = System.nanoTime() + STOP_TIMEOUT.toNanos();
    while ((onlineSessions.get() > 0 || replayer.pendingResends() > 0)
        && System.nanoTime() < stopDeadline) {
      LockSupport.parkNanos(NANOS_PER_SECOND / 10);
    }
  }

  /// 建输出目录。
  private void createDirectories() {
    try {
      Files.createDirectories(outDir);
    } catch (IOException e) {
      throw new UncheckedIOException("建输出目录失败：" + outDir, e);
    }
  }

  /// 周期采样。
  ///
  /// @param period 周期
  /// @param task 采样
  private record Probe(Duration period, Runnable task) {}
}
