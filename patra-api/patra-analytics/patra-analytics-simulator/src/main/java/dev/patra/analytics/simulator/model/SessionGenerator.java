package dev.patra.analytics.simulator.model;

import dev.patra.analytics.simulator.event.EventSource;
import dev.patra.analytics.simulator.event.PageType;
import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.event.SimEventName;
import dev.patra.analytics.simulator.event.UuidV7;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.UUID;

/// 按页面跳转链生成会话（spec §5.4）：读者在首页、检索、文献页、读全文、文献库之间按转移概率走（马尔可夫链），
/// 每到一页按埋点方案 §3.2 发对应事件，浏览器事件再按门户的攒批规则定发出时刻。
public final class SessionGenerator {

  /// 同一会话两次文献浏览的最小间隔：保证任意 5 分钟内至多 15 条（采集端点的频率规则阈值是 30 条），
  /// 加上服务端发出的 50 毫秒抖动后 15 个间隔仍超过 5 分钟
  static final Duration MIN_ARTICLE_GAP = Duration.ofSeconds(21);

  /// 浏览器攒批：最长等待
  static final Duration CLIENT_FLUSH = Duration.ofSeconds(5);

  /// 浏览器攒批：一批最多几条
  static final int CLIENT_BATCH_LIMIT = 50;

  /// 检索结果每页几条（position 跨页连续）
  static final int RESULTS_PER_PAGE = 20;

  private static final Duration MIN_DWELL = Duration.ofSeconds(4);
  private static final String EXIT = "exit";
  private static final String ARTICLE = "article";
  private static final String SEARCH = "search";
  private static final String FULLTEXT = "fulltext";
  private static final List<String> FEED_TYPES = List.of("feed_latest", "feed_cited", "feed_hot");
  private static final WeightedSampler FEED_TYPE_SAMPLER =
      new WeightedSampler(new double[] {0.5, 0.2, 0.3});
  private static final List<String> CITATION_STYLES =
      List.of("gb-t-7714-2015", "vancouver", "apa-7", "ama-11");
  private static final List<String> EXPORT_FORMATS = List.of("ris", "bibtex", "csv", "csl-json");
  private static final List<String> EXPORT_SCOPES =
      List.of("items", "collection", "library_all", "library_unfiled");
  private static final int MEAN_SAMPLE_SESSIONS = 10_000;

  private final TrafficModel model;
  private final ArticlePool pool;
  private final Map<String, Choice> transitions;
  private final Choice entryLoggedIn;
  private final Choice entryAnonymous;

  /// 按模型参数与文献池建生成器。
  ///
  /// @param model 流量模型
  /// @param pool 文献池
  public SessionGenerator(TrafficModel model, ArticlePool pool) {
    this.model = model;
    this.pool = pool;
    Map<String, Choice> rows = new TreeMap<>();
    model.transitions().forEach((page, row) -> rows.put(page, Choice.of(row)));
    this.transitions = rows;
    this.entryLoggedIn = Choice.of(model.entry());
    Map<String, Double> anonymous = new TreeMap<>(model.entry());
    anonymous.remove("library");
    this.entryAnonymous = Choice.of(anonymous);
  }

  /// 生成一个真人会话。
  ///
  /// @param person 人
  /// @param start 开始时刻
  /// @param random 随机源
  /// @return 会话
  public SimSession human(Person person, Instant start, SplittableRandom random) {
    Draft draft = new Draft(person, start, random);
    boolean loggedIn = person.userId() != null;
    String state = (loggedIn ? entryLoggedIn : entryAnonymous).pick(random);
    String previous = null;
    int pages = 0;
    while (!EXIT.equals(state) && pages < model.maxPages()) {
      switch (state) {
        case "home" -> home(draft);
        case SEARCH -> search(draft, SEARCH.equals(previous));
        case ARTICLE -> article(draft);
        case FULLTEXT -> fulltext(draft);
        case "library" -> library(draft);
        default -> throw new IllegalStateException("未知页面：" + state);
      }
      if (!FULLTEXT.equals(state)) {
        pages++;
      }
      String next = transitions.get(state).pick(random);
      if (ARTICLE.equals(next) && draft.cards.isEmpty()) {
        next = EXIT;
      }
      previous = state;
      state = next;
    }
    return draft.finish(SessionKind.HUMAN);
  }

  /// 生成一个不跑脚本的访客会话：只有服务端事件，浏览 1～3 页（spec §5.5）。
  ///
  /// @param person 人（一次性设备）
  /// @param start 开始时刻
  /// @param random 随机源
  /// @return 会话
  public SimSession noscript(Person person, Instant start, SplittableRandom random) {
    TrafficModel.CrawlerSpec spec = model.crawlers().noscript();
    Draft draft = new Draft(person, start, random);
    int pages = random.nextInt(spec.minPages(), spec.maxPages() + 1);
    for (int i = 0; i < pages; i++) {
      if (random.nextDouble() < 0.7) {
        int article = pool.sampleGlobal(random);
        draft.newPage(articlePath(article), PageType.ARTICLE);
        draft.add(SimEventName.ARTICLE_VIEWED, draft.t, articleProperties(article), article);
      } else {
        draft.newPage("/papers", PageType.SEARCH);
        draft.add(SimEventName.SEARCH_PERFORMED, draft.t, searchProperties(draft, 1, 120), -1);
      }
      draft.advance(seconds(random, spec.minIntervalSeconds(), spec.maxIntervalSeconds()));
    }
    return draft.finish(SessionKind.NOSCRIPT);
  }

  /// 抽一批真人会话求平均事件数，实时压测与灌历史据此把「条/秒」「条/天」换算成会话数（spec §5.4）。
  ///
  /// @param population 人群
  /// @param seed 种子
  /// @return 平均每会话事件数
  public double meanEventsPerHumanSession(Population population, long seed) {
    SplittableRandom random = new SplittableRandom(seed);
    Instant start = Instant.parse("2026-01-07T02:00:00Z");
    long events = 0;
    for (int i = 0; i < MEAN_SAMPLE_SESSIONS; i++) {
      events += human(population.sample(random), start, random).events().size();
    }
    return events / (double) MEAN_SAMPLE_SESSIONS;
  }

  /// 首页：页面浏览 + 文献流卡片曝光。
  ///
  /// @param draft 草稿
  private void home(Draft draft) {
    SplittableRandom random = draft.random;
    draft.newPage("/", PageType.HOME);
    draft.add(SimEventName.PAGE_VIEWED, draft.t.plusMillis(300), Map.of(), -1);
    String listType = FEED_TYPES.get(FEED_TYPE_SAMPLER.sample(random));
    int count = random.nextInt(model.lists().feedMin(), model.lists().feedMax() + 1);
    impress(draft, listType, uuid(draft), 1, count, false);
    draft.advance(dwell(random, model.dwellMedianSeconds()));
  }

  /// 检索页：服务端检索事件 + 页面浏览 + 结果曝光；连着检索时一半是翻页（新的检索 ID、同样的检索词）。
  ///
  /// @param draft 草稿
  /// @param afterSearch 上一页也是检索
  private void search(Draft draft, boolean afterSearch) {
    SplittableRandom random = draft.random;
    draft.newPage("/papers", PageType.SEARCH);
    boolean nextPage =
        afterSearch
            && draft.resultCount > draft.searchPage * RESULTS_PER_PAGE
            && random.nextBoolean();
    if (nextPage) {
      draft.searchPage++;
    } else {
      draft.searchPage = 1;
      draft.query = model.searchTerms().get(random.nextInt(model.searchTerms().size()));
      draft.resultCount =
          random.nextDouble() < model.zeroResultRate() ? 0 : random.nextInt(20, 5_001);
    }
    Map<String, Object> properties = searchProperties(draft, draft.searchPage, draft.resultCount);
    draft.add(SimEventName.SEARCH_PERFORMED, draft.t, properties, -1);
    draft.add(SimEventName.PAGE_VIEWED, draft.t.plusMillis(300), Map.of(), -1);
    int shown = draft.resultCount - (draft.searchPage - 1) * RESULTS_PER_PAGE;
    int count =
        Math.min(shown, random.nextInt(model.lists().searchMin(), model.lists().searchMax() + 1));
    draft.cards = List.of();
    if (count > 0) {
      int firstPosition = (draft.searchPage - 1) * RESULTS_PER_PAGE + 1;
      impress(draft, "search", (String) properties.get("search_id"), firstPosition, count, true);
    }
    draft.advance(dwell(random, model.dwellMedianSeconds()));
  }

  /// 文献页：从上一页点卡片进来（先发点击）或从站外直达；服务端浏览 + 页面浏览 +「看过还看了」曝光。
  ///
  /// @param draft 草稿
  private void article(Draft draft) {
    SplittableRandom random = draft.random;
    if (draft.lastArticleView != null
        && draft.t.isBefore(draft.lastArticleView.plus(MIN_ARTICLE_GAP))) {
      draft.t = draft.lastArticleView.plus(MIN_ARTICLE_GAP);
    }
    int article;
    String referrer = null;
    if (draft.cards.isEmpty()) {
      article = pool.sampleGlobal(random);
      referrer = model.referrers().get(random.nextInt(model.referrers().size()));
    } else {
      Card card = draft.cards.get(random.nextInt(draft.cards.size()));
      article = card.articleIndex();
      Map<String, Object> click = cardProperties(card);
      draft.add(SimEventName.ARTICLE_CLICKED, draft.t.minusMillis(50), click, article);
    }
    draft.newPage(articlePath(article), PageType.ARTICLE);
    draft.referrer = referrer;
    draft.add(SimEventName.ARTICLE_VIEWED, draft.t, articleProperties(article), article);
    draft.add(SimEventName.PAGE_VIEWED, draft.t.plusMillis(300), Map.of(), -1);
    draft.referrer = null;
    draft.lastArticleView = draft.t;
    draft.currentArticle = article;
    if (random.nextDouble() < model.lists().relatedSeenRate()) {
      impress(draft, "related", uuid(draft), 1, model.lists().related(), true);
    } else {
      draft.cards = List.of();
    }
    draft.advance(dwell(random, model.dwellMedianSeconds()));
  }

  /// 读全文：仍在当前文献页（不是新页面）；全文停留达标发 `fulltext_read`，之后可能下载 PDF、复制引用、收藏。
  ///
  /// @param draft 草稿
  private void fulltext(Draft draft) {
    SplittableRandom random = draft.random;
    TrafficModel.Actions actions = model.actions();
    int article = draft.currentArticle;
    Instant start = draft.t;
    int visible = (int) Math.clamp(Math.round(40 * Math.exp(0.8 * random.nextGaussian())), 10, 300);
    Instant at = start.plusSeconds(visible);
    Map<String, Object> read = articleProperties(article);
    read.put("visible_seconds", visible);
    draft.add(SimEventName.FULLTEXT_READ, at, read, article);
    if (random.nextDouble() < actions.pdfAfterFulltext()) {
      at = at.plusSeconds(5);
      draft.add(SimEventName.PDF_DOWNLOADED, at, articleProperties(article), article);
    }
    if (random.nextDouble() < actions.citationAfterFulltext()) {
      at = at.plusSeconds(5);
      Map<String, Object> citation = new LinkedHashMap<>();
      citation.put("article_ids", List.of(Long.toString(pool.idAt(article))));
      citation.put("style", CITATION_STYLES.get(random.nextInt(CITATION_STYLES.size())));
      draft.add(SimEventName.CITATION_COPIED, at, citation, article);
    }
    if (draft.person.userId() != null && random.nextDouble() < actions.collectAfterFulltext()) {
      at = at.plusSeconds(5);
      draft.add(SimEventName.ARTICLE_COLLECTED, at, articleProperties(article), article);
    }
    Instant afterDwell = start.plus(dwell(random, model.fulltextDwellMedianSeconds()));
    Instant afterActions = at.plusSeconds(2);
    draft.t = afterDwell.isAfter(afterActions) ? afterDwell : afterActions;
  }

  /// 文献库（只有登录用户）：页面浏览，部分会话在这里导出。
  ///
  /// @param draft 草稿
  private void library(Draft draft) {
    SplittableRandom random = draft.random;
    draft.newPage("/library", PageType.LIBRARY);
    draft.add(SimEventName.PAGE_VIEWED, draft.t.plusMillis(300), Map.of(), -1);
    draft.cards = List.of();
    if (random.nextDouble() < model.actions().exportInLibrary()) {
      Map<String, Object> export = new LinkedHashMap<>();
      export.put("format", EXPORT_FORMATS.get(random.nextInt(EXPORT_FORMATS.size())));
      export.put("scope", EXPORT_SCOPES.get(random.nextInt(EXPORT_SCOPES.size())));
      export.put("article_count", random.nextInt(1, 51));
      draft.add(SimEventName.EXPORT_COMPLETED, draft.t.plusSeconds(5), export, -1);
    }
    draft.advance(dwell(random, model.dwellMedianSeconds()));
  }

  /// 渲染一个列表并发曝光：同一列表里文献不重复；`affinity` 时按兴趣主题偏好挑文献。
  ///
  /// @param draft 草稿
  /// @param listType 列表类型
  /// @param listId 列表实例 ID
  /// @param firstPosition 第一张卡片的位置
  /// @param count 卡片数
  /// @param affinity 是否按兴趣主题挑
  private void impress(
      Draft draft, String listType, String listId, int firstPosition, int count, boolean affinity) {
    List<Card> cards = new ArrayList<>();
    Set<Integer> used = new HashSet<>();
    for (int i = 0; i < count; i++) {
      int article = pickArticle(draft, affinity);
      for (int attempt = 0; attempt < 5 && !used.add(article); attempt++) {
        article = pickArticle(draft, affinity);
      }
      Card card = new Card(article, listType, listId, firstPosition + i);
      cards.add(card);
      draft.add(
          SimEventName.ARTICLE_IMPRESSED,
          draft.t.plusMillis(1_000 + i * 100L),
          cardProperties(card),
          article);
    }
    draft.cards = List.copyOf(cards);
  }

  /// 挑一篇文献：按兴趣主题（`topicAffinity` 的概率）或全局热度。
  ///
  /// @param draft 草稿
  /// @param affinity 是否考虑兴趣主题
  /// @return 文献排名
  private int pickArticle(Draft draft, boolean affinity) {
    if (affinity && draft.random.nextDouble() < model.topicAffinity()) {
      return pool.sampleInTopic(draft.interestTopic, draft.random);
    }
    return pool.sampleGlobal(draft.random);
  }

  /// 卡片的曝光 / 点击属性。
  ///
  /// @param card 卡片
  /// @return 属性
  private Map<String, Object> cardProperties(Card card) {
    Map<String, Object> properties = articleProperties(card.articleIndex());
    properties.put("list_type", card.listType());
    properties.put("list_id", card.listId());
    properties.put("position", card.position());
    return properties;
  }

  /// 只含 `article_id` 的属性。
  ///
  /// @param article 文献排名
  /// @return 属性（可继续追加）
  private Map<String, Object> articleProperties(int article) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("article_id", Long.toString(pool.idAt(article)));
    return properties;
  }

  /// 检索事件的属性。
  ///
  /// @param draft 草稿（取检索词）
  /// @param page 页码
  /// @param resultCount 结果数
  /// @return 属性
  private Map<String, Object> searchProperties(Draft draft, int page, int resultCount) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("search_id", uuid(draft));
    properties.put("mode", "keyword");
    properties.put(
        "query",
        draft.query != null
            ? draft.query
            : model.searchTerms().get(draft.random.nextInt(model.searchTerms().size())));
    properties.put("filters", Map.of());
    properties.put("sort", "latest");
    properties.put("page", page);
    properties.put("result_count", resultCount);
    return properties;
  }

  /// 文献页路径。
  ///
  /// @param article 文献排名
  /// @return 路径
  private String articlePath(int article) {
    return "/papers/" + pool.idAt(article);
  }

  /// 页面停留：对数正态，至少 4 秒（本页的曝光在头 3 秒内发完）。
  ///
  /// @param random 随机源
  /// @param medianSeconds 中位数
  /// @return 停留时长
  private Duration dwell(SplittableRandom random, double medianSeconds) {
    double seconds = medianSeconds * Math.exp(model.dwellSigma() * random.nextGaussian());
    Duration dwell = Duration.ofMillis(Math.round(seconds * 1_000));
    return dwell.compareTo(MIN_DWELL) < 0 ? MIN_DWELL : dwell;
  }

  /// 区间内均匀的秒数。
  ///
  /// @param random 随机源
  /// @param min 下限
  /// @param max 上限
  /// @return 时长
  static Duration seconds(SplittableRandom random, double min, double max) {
    return Duration.ofMillis(Math.round((min + random.nextDouble() * (max - min)) * 1_000));
  }

  /// 用随机源生成小写 UUID 字符串（列表实例 ID、检索 ID）。
  ///
  /// @param draft 草稿
  /// @return UUID
  private static String uuid(Draft draft) {
    return new UUID(draft.random.nextLong(), draft.random.nextLong()).toString();
  }

  /// 一张曝光过的卡片。
  ///
  /// @param articleIndex 文献排名
  /// @param listType 列表类型
  /// @param listId 列表实例 ID
  /// @param position 位置
  private record Card(int articleIndex, String listType, String listId, int position) {}

  /// 按概率表选下一步；键按字母序固定，同种子同结果（不受 Map 迭代顺序影响）。
  ///
  /// @param keys 选项
  /// @param sampler 分布
  private record Choice(List<String> keys, WeightedSampler sampler) {

    /// 从概率表建。
    ///
    /// @param row 选项 → 概率
    /// @return 选择器
    static Choice of(Map<String, Double> row) {
      TreeMap<String, Double> sorted = new TreeMap<>(row);
      return new Choice(
          List.copyOf(sorted.keySet()),
          new WeightedSampler(sorted.values().stream().mapToDouble(Double::doubleValue).toArray()));
    }

    /// 选一个。
    ///
    /// @param random 随机源
    /// @return 选项
    String pick(SplittableRandom random) {
      return keys.get(sampler.sample(random));
    }
  }

  /// 生成中的会话：当前时刻、当前页、可点的卡片，以及已产生的事件。
  private final class Draft {

    private final Person person;
    private final SplittableRandom random;
    private final UUID sessionId;
    private final int interestTopic;
    private final List<Pending> pending = new ArrayList<>();
    private final List<Instant> pageLeaves = new ArrayList<>();
    private final List<String> pagePaths = new ArrayList<>();
    private final List<PageType> pageTypes = new ArrayList<>();
    private Instant t;
    private List<Card> cards = List.of();
    private Instant lastArticleView;
    private int currentArticle = -1;
    private String query;
    private int searchPage;
    private int resultCount;
    private String referrer;

    Draft(Person person, Instant start, SplittableRandom random) {
      this.person = person;
      this.random = random;
      this.t = start;
      this.sessionId = UuidV7.of(start, random);
      this.interestTopic = pool.sampleTopic(random);
    }

    /// 进入新页面：上一页在此刻离开。
    ///
    /// @param path 路径
    /// @param type 页面类型
    void newPage(String path, PageType type) {
      if (!pageLeaves.isEmpty()) {
        pageLeaves.set(pageLeaves.size() - 1, t);
      }
      pageLeaves.add(null);
      pagePaths.add(path);
      pageTypes.add(type);
    }

    /// 在当前页记一条事件。
    ///
    /// @param name 事件名
    /// @param at 发生时刻
    /// @param properties 属性
    /// @param article 文献排名，无则 -1
    void add(SimEventName name, Instant at, Map<String, Object> properties, int article) {
      pending.add(new Pending(name, at, pageLeaves.size() - 1, properties, article, referrer));
    }

    /// 时间往前走。
    ///
    /// @param duration 时长
    void advance(Duration duration) {
      t = t.plus(duration);
    }

    /// 收尾：最后一页在当前时刻离开；定发出时刻、事件 ID，按发生时刻排序。
    ///
    /// @param kind 会话身份
    /// @return 会话
    SimSession finish(SessionKind kind) {
      if (!pageLeaves.isEmpty()) {
        pageLeaves.set(pageLeaves.size() - 1, t);
      }
      List<Pending> ordered = new ArrayList<>(pending);
      ordered.sort(Comparator.comparing(Pending::at));
      Map<Pending, Instant> sentAt = sendTimes(ordered);
      List<SimEvent> events = new ArrayList<>(ordered.size());
      for (Pending p : ordered) {
        events.add(
            SimEvent.builder()
                .eventId(UuidV7.of(p.at(), random))
                .name(p.name())
                .createdAt(p.at())
                .sentAt(sentAt.get(p))
                .person(person)
                .sessionId(sessionId)
                .pagePath(pagePaths.get(p.page()))
                .pageType(pageTypes.get(p.page()))
                .referrerHost(p.referrer())
                .properties(p.properties())
                .articleIndex(p.article())
                .build());
      }
      return new SimSession(sessionId, person, kind, events);
    }

    /// 发出时刻：服务端事件处理请求时立即发（5～50 毫秒）；浏览器事件按 5 秒、50 条、离开页面三者先到攒批。
    ///
    /// @param ordered 按发生时刻排好的事件
    /// @return 每条事件的发出时刻
    private Map<Pending, Instant> sendTimes(List<Pending> ordered) {
      Map<Pending, Instant> sent = new IdentityHashMap<>();
      Map<Integer, List<Pending>> clientByPage = new TreeMap<>();
      for (Pending p : ordered) {
        if (p.name().source() == EventSource.SERVER) {
          sent.put(p, p.at().plusMillis(random.nextLong(5, 51)));
        } else {
          clientByPage.computeIfAbsent(p.page(), page -> new ArrayList<>()).add(p);
        }
      }
      clientByPage.forEach(
          (page, events) -> {
            Instant leave = pageLeaves.get(page);
            int i = 0;
            while (i < events.size()) {
              Instant deadline = events.get(i).at().plus(CLIENT_FLUSH);
              int j = i;
              while (j < events.size()
                  && j - i < CLIENT_BATCH_LIMIT
                  && !events.get(j).at().isAfter(deadline)) {
                j++;
              }
              Instant flush;
              if (j - i == CLIENT_BATCH_LIMIT) {
                flush = events.get(j - 1).at();
              } else {
                flush = deadline.isBefore(leave) ? deadline : leave;
              }
              for (int k = i; k < j; k++) {
                sent.put(events.get(k), flush);
              }
              i = j;
            }
          });
      return sent;
    }
  }

  /// 还没定发出时刻的事件。
  ///
  /// @param name 事件名
  /// @param at 发生时刻
  /// @param page 页面序号
  /// @param properties 属性
  /// @param article 文献排名
  /// @param referrer 来源域名
  private record Pending(
      SimEventName name,
      Instant at,
      int page,
      Map<String, Object> properties,
      int article,
      String referrer) {}
}
