package dev.patra.analytics.simulator.model;

import java.time.Instant;
import java.util.SplittableRandom;

/// 按份额混合四类会话（spec §5.5）：声明身份的爬虫、伪装的爬虫、不跑脚本的访客，其余是真人。
public final class SessionMix {

  private static final int MEAN_SAMPLE_SESSIONS = 10_000;

  private final TrafficModel model;
  private final Population population;
  private final SessionGenerator humans;
  private final CrawlerGenerator crawlers;

  /// 创建混合器。
  ///
  /// @param model 流量模型
  /// @param pool 文献池
  /// @param population 人群
  public SessionMix(TrafficModel model, ArticlePool pool, Population population) {
    this.model = model;
    this.population = population;
    this.humans = new SessionGenerator(model, pool);
    this.crawlers = new CrawlerGenerator(model, pool);
  }

  /// 产生一个会话（未打爬虫标记）。
  ///
  /// @param start 开始时刻
  /// @param random 随机源
  /// @param freshIndex 若是爬虫或无脚本访客，一次性设备用这个下标
  /// @return 会话
  public SimSession next(Instant start, SplittableRandom random, int freshIndex) {
    return next(start, random, freshIndex, population.sample(random));
  }

  /// 产生一个会话，真人会话用给定的人。
  ///
  /// @param start 开始时刻
  /// @param random 随机源
  /// @param freshIndex 一次性设备的下标
  /// @param human 真人会话的人（调用方已抽好，例如避开正在线的人）
  /// @return 会话
  public SimSession next(Instant start, SplittableRandom random, int freshIndex, Person human) {
    TrafficModel.Crawlers shares = model.crawlers();
    double u = random.nextDouble();
    double declared = shares.declared().share();
    double stealth = declared + shares.stealth().share();
    double noscript = stealth + shares.noscript().share();
    if (u < declared) {
      return crawlers.declared(population.freshDevice(freshIndex, true, random), start, random);
    }
    if (u < stealth) {
      return crawlers.stealth(population.freshDevice(freshIndex, false, random), start, random);
    }
    if (u < noscript) {
      return humans.noscript(population.freshDevice(freshIndex, false, random), start, random);
    }
    return humans.human(human, start, random);
  }

  /// 抽一批混合会话求平均事件数（含爬虫），把「条」换算成「会话」。
  ///
  /// @param seed 种子
  /// @return 平均每会话事件数
  public double meanEvents(long seed) {
    SplittableRandom random = new SplittableRandom(seed);
    Instant start = Instant.parse("2026-01-07T02:00:00Z");
    long events = 0;
    for (int i = 0; i < MEAN_SAMPLE_SESSIONS; i++) {
      events += next(start, random, population.size() + i).events().size();
    }
    return events / (double) MEAN_SAMPLE_SESSIONS;
  }

  /// 人群。
  ///
  /// @return 人群
  public Population population() {
    return population;
  }
}
