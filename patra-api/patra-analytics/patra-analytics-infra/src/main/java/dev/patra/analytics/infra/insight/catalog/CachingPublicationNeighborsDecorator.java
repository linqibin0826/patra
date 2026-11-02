package dev.patra.analytics.infra.insight.catalog;

import dev.patra.analytics.domain.insight.exception.InsightUnavailableException;
import dev.patra.analytics.domain.insight.model.vo.AlsoReadItem;
import dev.patra.analytics.domain.insight.port.catalog.PublicationCatalogPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/// 同主题邻居的 Redis 缓存（insight spec §6.3）：按文献 ID 缓存 6 小时，主题词、期刊几乎不变。
///
/// catalog 失败时不写缓存、原样抛出（下次再试）；Redis 出错时直接调 catalog。其余两个方法只给任务用，不缓存。
@Slf4j
public class CachingPublicationNeighborsDecorator implements PublicationCatalogPort {

  /// 缓存时长。
  static final Duration TTL = Duration.ofHours(6);

  private static final String KEY_PREFIX = "analytics:insight:neighbors:";
  private static final String METER = "analytics.insight.neighbors";
  private static final TypeReference<List<AlsoReadItem>> ITEMS = new TypeReference<>() {};

  private final PublicationCatalogPort delegate;
  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final MeterRegistry meters;

  /// 创建装饰器。
  ///
  /// @param delegate 真正调 catalog 的适配器
  /// @param redis Redis 客户端
  /// @param json JSON 映射器
  /// @param meters 指标
  public CachingPublicationNeighborsDecorator(
      PublicationCatalogPort delegate,
      StringRedisTemplate redis,
      JsonMapper json,
      MeterRegistry meters) {
    this.delegate = delegate;
    this.redis = redis;
    this.json = json;
    this.meters = meters;
  }

  @Override
  public Set<Long> eligibleAmong(Collection<Long> ids) {
    return delegate.eligibleAmong(ids);
  }

  @Override
  public List<Long> latestIds(int limit) {
    return delegate.latestIds(limit);
  }

  /// 先读缓存，没有再调 catalog 并写回。
  ///
  /// @param articleId 文献 ID
  /// @param limit 条数（调用方固定用同一个值，键里不带）
  /// @return 邻居
  @Override
  public List<AlsoReadItem> subjectNeighbors(long articleId, int limit) {
    String key = KEY_PREFIX + articleId;
    String cached = read(key);
    if (cached != null) {
      try {
        List<AlsoReadItem> items = json.readValue(cached, ITEMS);
        meters.counter(METER, "outcome", "hit").increment();
        return items;
      } catch (JacksonException e) {
        log.warn("邻居缓存解析失败，重新调 catalog：{}", e.getMessage());
      }
    }
    List<AlsoReadItem> items;
    try {
      items = delegate.subjectNeighbors(articleId, limit);
    } catch (InsightUnavailableException e) {
      meters.counter(METER, "outcome", "unavailable").increment();
      throw e;
    }
    meters.counter(METER, "outcome", "fetched").increment();
    write(key, items);
    return items;
  }

  /// 读缓存；Redis 出错时当作没有。
  ///
  /// @param key 键
  /// @return 缓存值，没有为 null
  private String read(String key) {
    try {
      return redis.opsForValue().get(key);
    } catch (DataAccessException e) {
      log.warn("读邻居缓存失败：{}", e.getMessage());
      return null;
    }
  }

  /// 写缓存；失败只记日志。
  ///
  /// @param key 键
  /// @param items 邻居
  private void write(String key, List<AlsoReadItem> items) {
    try {
      redis.opsForValue().set(key, json.writeValueAsString(items), TTL);
    } catch (DataAccessException | JacksonException e) {
      log.warn("写邻居缓存失败：{}", e.getMessage());
    }
  }
}
