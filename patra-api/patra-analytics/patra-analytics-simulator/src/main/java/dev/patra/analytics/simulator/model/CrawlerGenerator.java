package dev.patra.analytics.simulator.model;

import dev.patra.analytics.simulator.event.PageType;
import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.event.SimEventName;
import dev.patra.analytics.simulator.event.UuidV7;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/// 爬虫会话（spec §5.5）：线性遍历文献池、高频；声明身份的不跑脚本，伪装的跑脚本（有页面浏览事件）。
///
/// 爬虫不存 Cookie，调用方每个会话给一台一次性设备（`Population.freshDevice`）。
public final class CrawlerGenerator {

  private static final Duration CLIENT_DELAY = Duration.ofMillis(300);
  private static final Duration CLIENT_FLUSH = Duration.ofSeconds(5);

  private final TrafficModel model;
  private final ArticlePool pool;

  /// 按模型参数与文献池建生成器。
  ///
  /// @param model 流量模型
  /// @param pool 文献池
  public CrawlerGenerator(TrafficModel model, ArticlePool pool) {
    this.model = model;
    this.pool = pool;
  }

  /// 声明身份的爬虫：只有服务端的文献浏览，每 2～5 秒一篇。
  ///
  /// @param person 一次性设备（爬虫 UA）
  /// @param start 开始时刻
  /// @param random 随机源
  /// @return 会话
  public SimSession declared(Person person, Instant start, SplittableRandom random) {
    return crawl(
        person, start, random, model.crawlers().declared(), false, SessionKind.DECLARED_CRAWLER);
  }

  /// 伪装成浏览器的爬虫：每篇一个服务端浏览 + 一个页面浏览，每 3～6 秒一篇。
  ///
  /// @param person 一次性设备（浏览器 UA）
  /// @param start 开始时刻
  /// @param random 随机源
  /// @return 会话
  public SimSession stealth(Person person, Instant start, SplittableRandom random) {
    return crawl(
        person, start, random, model.crawlers().stealth(), true, SessionKind.STEALTH_CRAWLER);
  }

  /// 按参数爬一串文献。
  ///
  /// @param person 设备
  /// @param start 开始时刻
  /// @param random 随机源
  /// @param spec 参数
  /// @param scripts 是否跑脚本（发页面浏览）
  /// @param kind 会话身份
  /// @return 会话
  private SimSession crawl(
      Person person,
      Instant start,
      SplittableRandom random,
      TrafficModel.CrawlerSpec spec,
      boolean scripts,
      SessionKind kind) {
    UUID sessionId = UuidV7.of(start, random);
    int pages = random.nextInt(spec.minPages(), spec.maxPages() + 1);
    int step = random.nextInt(pool.size());
    List<SimEvent> events = new ArrayList<>();
    Instant t = start;
    for (int i = 0; i < pages; i++) {
      int article = pool.linear(step + i);
      Instant next =
          t.plus(
              SessionGenerator.seconds(
                  random, spec.minIntervalSeconds(), spec.maxIntervalSeconds()));
      String path = "/papers/" + pool.idAt(article);
      Map<String, Object> properties = new LinkedHashMap<>();
      properties.put("article_id", Long.toString(pool.idAt(article)));
      events.add(
          event(
              sessionId,
              person,
              SimEventName.ARTICLE_VIEWED,
              t,
              t.plusMillis(random.nextLong(5, 51)),
              path,
              properties,
              article,
              random));
      if (scripts) {
        Instant created = t.plus(CLIENT_DELAY);
        Instant deadline = created.plus(CLIENT_FLUSH);
        events.add(
            event(
                sessionId,
                person,
                SimEventName.PAGE_VIEWED,
                created,
                deadline.isBefore(next) ? deadline : next,
                path,
                Map.of(),
                -1,
                random));
      }
      t = next;
    }
    return new SimSession(sessionId, person, kind, events);
  }

  /// 造一条文献页上的事件。
  ///
  /// @param sessionId 会话
  /// @param person 设备
  /// @param name 事件名
  /// @param created 发生时刻
  /// @param sent 发出时刻
  /// @param path 路径
  /// @param properties 属性
  /// @param article 文献排名
  /// @param random 随机源
  /// @return 事件
  private static SimEvent event(
      UUID sessionId,
      Person person,
      SimEventName name,
      Instant created,
      Instant sent,
      String path,
      Map<String, Object> properties,
      int article,
      SplittableRandom random) {
    return SimEvent.builder()
        .eventId(UuidV7.of(created, random))
        .name(name)
        .createdAt(created)
        .sentAt(sent)
        .person(person)
        .sessionId(sessionId)
        .pagePath(path)
        .pageType(PageType.ARTICLE)
        .properties(properties)
        .articleIndex(article)
        .build();
  }
}
