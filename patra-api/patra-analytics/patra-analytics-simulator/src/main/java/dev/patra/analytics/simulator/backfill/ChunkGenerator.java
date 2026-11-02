package dev.patra.analytics.simulator.backfill;

import dev.patra.analytics.simulator.model.ArrivalCurve;
import dev.patra.analytics.simulator.model.BotLabeler;
import dev.patra.analytics.simulator.model.SessionMix;
import dev.patra.analytics.simulator.model.SimSession;
import dev.patra.analytics.simulator.model.TrafficModel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/// 确定性地生成一块：随机源由（种子，日期，块号）派生，所以同一块重新生成逐字节相同——写入失败重试、重跑、
/// 试灌后正式灌，令牌相同时内容也相同，ClickHouse 按令牌丢弃的就真是重复。
public final class ChunkGenerator {

  private final BackfillPlan plan;
  private final SessionMix mix;
  private final ArrivalCurve curve;
  private final int populationSize;
  private final long seed;

  /// 创建生成器。
  ///
  /// @param plan 日程
  /// @param mix 会话混合器
  /// @param model 流量模型
  /// @param populationSize 人群大小（一次性设备的下标从这里往后排）
  /// @param seed 运行种子
  public ChunkGenerator(
      BackfillPlan plan, SessionMix mix, TrafficModel model, int populationSize, long seed) {
    this.plan = plan;
    this.mix = mix;
    this.curve = new ArrivalCurve(model);
    this.populationSize = populationSize;
    this.seed = seed;
  }

  /// 生成某天的第几块。
  ///
  /// @param day 日期
  /// @param chunk 块号
  /// @return 块
  public DayChunk generate(LocalDate day, int chunk) {
    SplittableRandom random = new SplittableRandom(mix(seed, day.toEpochDay(), chunk));
    long remaining = plan.sessions(day) - (long) chunk * plan.sessionsPerChunk();
    int count = (int) Math.min(plan.sessionsPerChunk(), remaining);
    int freshBase = populationSize + plan.chunkOrdinal(day, chunk) * plan.sessionsPerChunk();
    List<SimSession> sessions = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      sessions.add(
          BotLabeler.label(mix.next(curve.sampleStart(day, random), random, freshBase + i)));
    }
    return new DayChunk(sessions);
  }

  /// 把三个数混成一个种子（SplitMix64 的终结函数，逐个吸收）。
  ///
  /// @param seed 运行种子
  /// @param day 日期
  /// @param chunk 块号
  /// @return 种子
  static long mix(long seed, long day, long chunk) {
    return finish(finish(finish(seed) ^ day) ^ chunk);
  }

  /// SplitMix64 的终结函数。
  ///
  /// @param z 输入
  /// @return 输出
  private static long finish(long z) {
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }
}
