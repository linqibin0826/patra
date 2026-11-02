package dev.patra.analytics.simulator.backfill;

import dev.patra.analytics.simulator.model.ArticlePool;
import dev.patra.analytics.simulator.model.TrafficModel;
import dev.patra.analytics.simulator.support.SmallArticlePoolFixture;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import tools.jackson.databind.json.JsonMapper;

/// 灌历史单元测试的公共参数与假写入端。
final class BackfillFixture {

  static final JsonMapper MAPPER = JsonMapper.builder().build();
  static final TrafficModel MODEL =
      TrafficModel.load(null, MAPPER).toBuilder().devices(5_000).registeredUsers(500).build();
  static final Path ARTICLES = smallPool();
  static final ArticlePool POOL = ArticlePool.load(ARTICLES, 200_000);

  private BackfillFixture() {}

  static BackfillParams params() {
    return new BackfillParams(
        3, 42L, 60_000L, 3, LocalDate.of(2026, 11, 1), null, null, ARTICLES, null);
  }

  static Path smallPool() {
    return SmallArticlePoolFixture.path();
  }

  /// 记下每次写入的令牌与行；可设前几次抛可重试错误。
  static final class RecordingSink implements EventSink {

    final List<String> tokens = new ArrayList<>();
    final List<List<String>> bodies = new ArrayList<>();
    int calls;
    int failuresLeft;
    boolean diskFull;

    @Override
    public void insert(Supplier<Iterator<String>> lines, String token) {
      calls++;
      List<String> body = new ArrayList<>();
      lines.get().forEachRemaining(body::add);
      if (diskFull) {
        throw new DiskFullException("NOT_ENOUGH_SPACE");
      }
      if (failuresLeft > 0) {
        failuresLeft--;
        throw new IllegalStateException("ClickHouse 暂时不可用");
      }
      tokens.add(token);
      bodies.add(body);
    }
  }
}
