package dev.patra.analytics.simulator.truth;

import dev.patra.analytics.simulator.event.BeijingDays;
import dev.patra.analytics.simulator.event.EventSource;
import dev.patra.analytics.simulator.event.SimEvent;
import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.SimSession;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/// 真值账本（spec §7）：只记已确认送达的事件，口径与 PAP-170 的「看过人数」查询一致——
/// 每个（北京日期，文献）的独立读者，只数真人会话（送达的事件里至少一条浏览器事件、且没有一条被判为爬虫），
/// 查看类算看过、全文类算读过全文。真人与否按同一套判定规则看送达的事件，不用模拟器私下知道的身份：
/// 没跨过频率阈值就结束的伪装爬虫，管线认不出，真值也照算。
///
/// 一对（文献，人）打包成一个 `long` 存进当天的缓冲，关天时排序去重计数。线程安全：实时压测由多个虚拟线程写入。
public final class TruthLedger {

  private final ArticlePool pool;
  private final Function<SimEvent, Instant> dayInstant;
  private final Map<LocalDate, DayBuffer> days = new TreeMap<>();

  /// 创建账本。
  ///
  /// @param pool 文献池（把排名换回文献 ID）
  /// @param dayInstant 决定事件落在哪天的时刻（灌历史用 `derived_at`，实时压测用 `created_at`）
  public TruthLedger(ArticlePool pool, Function<SimEvent, Instant> dayInstant) {
    this.pool = pool;
    this.dayInstant = dayInstant;
  }

  /// 记一个会话里已送达的事件。
  ///
  /// @param session 会话
  /// @param delivered 其中已确认送达的事件（已按采集端点的规则打好爬虫标记）
  public synchronized void accept(SimSession session, List<SimEvent> delivered) {
    if (delivered.stream().noneMatch(e -> e.name().source() == EventSource.CLIENT)
        || delivered.stream().anyMatch(e -> e.botReason() != null)) {
      return;
    }
    int person = session.person().index();
    for (SimEvent event : delivered) {
      if (!event.name().isView()) {
        continue;
      }
      long pair = ((long) event.articleIndex() << 32) | (person & 0xFFFF_FFFFL);
      DayBuffer buffer =
          days.computeIfAbsent(BeijingDays.of(dayInstant.apply(event)), d -> new DayBuffer());
      buffer.viewed.add(pair);
      if (event.name().isFulltext()) {
        buffer.fulltext.add(pair);
      }
    }
  }

  /// 输出并释放早于某天的各天。
  ///
  /// @param day 这一天（不含）
  /// @return 真值行，按日期、文献 ID 升序
  public synchronized List<TruthRow> closeDaysBefore(LocalDate day) {
    List<TruthRow> rows = new ArrayList<>();
    Iterator<Map.Entry<LocalDate, DayBuffer>> it = days.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<LocalDate, DayBuffer> entry = it.next();
      if (!entry.getKey().isBefore(day)) {
        break;
      }
      rows.addAll(rowsOf(entry.getKey(), entry.getValue()));
      it.remove();
    }
    return rows;
  }

  /// 输出并释放全部。
  ///
  /// @return 真值行
  public synchronized List<TruthRow> closeAll() {
    return closeDaysBefore(LocalDate.MAX);
  }

  /// 一天的真值行。
  ///
  /// @param day 日期
  /// @param buffer 缓冲
  /// @return 行
  private List<TruthRow> rowsOf(LocalDate day, DayBuffer buffer) {
    Map<Integer, Integer> viewers = countPeople(buffer.viewed);
    Map<Integer, Integer> readers = countPeople(buffer.fulltext);
    List<TruthRow> rows = new ArrayList<>(viewers.size());
    viewers.forEach(
        (article, count) ->
            rows.add(
                new TruthRow(day, pool.idAt(article), count, readers.getOrDefault(article, 0))));
    rows.sort(Comparator.comparingLong(TruthRow::articleId));
    return rows;
  }

  /// 每篇文献有多少个不同的人。
  ///
  /// @param pairs 打包的（文献，人）
  /// @return 文献排名 → 人数
  private static Map<Integer, Integer> countPeople(LongBuffer pairs) {
    long[] sorted = pairs.toSortedArray();
    Map<Integer, Integer> counts = new TreeMap<>();
    for (int i = 0; i < sorted.length; i++) {
      if (i > 0 && sorted[i] == sorted[i - 1]) {
        continue;
      }
      counts.merge((int) (sorted[i] >>> 32), 1, Integer::sum);
    }
    return counts;
  }

  /// 一天的两个缓冲。
  private static final class DayBuffer {
    private final LongBuffer viewed = new LongBuffer();
    private final LongBuffer fulltext = new LongBuffer();
  }

  /// 可增长的 `long` 数组。
  private static final class LongBuffer {
    private long[] values = new long[1_024];
    private int size;

    /// 追加。
    ///
    /// @param value 值
    void add(long value) {
      if (size == values.length) {
        values = Arrays.copyOf(values, size * 2);
      }
      values[size++] = value;
    }

    /// 排好序的副本。
    ///
    /// @return 数组
    long[] toSortedArray() {
      long[] copy = Arrays.copyOf(values, size);
      Arrays.sort(copy);
      return copy;
    }
  }
}
