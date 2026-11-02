package dev.patra.analytics.infra.insight.stats;

import dev.patra.analytics.domain.insight.model.vo.ReaderStats;
import dev.patra.analytics.domain.insight.port.stats.ReaderStatsPort;
import dev.patra.analytics.infra.insight.config.InsightProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/// 计数的 Redis 缓存（cache-aside，insight spec §4.3）：一次 `MGET`，没命中的合成一次 ClickHouse 查询，结果用管道写回、
/// 60 秒过期；没人看过的也写 `0:0`，冷门文献不会每次都打到 ClickHouse。
///
/// Redis 出错时整次绕过缓存直接查 ClickHouse，记 `result=error`：缓存不能让 Redis 故障拖垮计数。
@Slf4j
public class CachingReaderStatsDecorator implements ReaderStatsPort {

  /// 缓存时长：「1 分钟内更新」的来源。
  static final Duration TTL = Duration.ofSeconds(60);

  private static final String METER = "analytics.insight.stats.cache";

  private final ReaderStatsPort delegate;
  private final StringRedisTemplate redis;
  private final String keyPrefix;
  private final MeterRegistry meters;

  /// 创建装饰器。
  ///
  /// @param delegate 真正查 ClickHouse 的适配器
  /// @param redis Redis 客户端
  /// @param properties insight 配置：键里带上合成事件开关，切换后不会读到另一种口径的旧缓存
  /// @param meters 指标
  public CachingReaderStatsDecorator(
      ReaderStatsPort delegate,
      StringRedisTemplate redis,
      InsightProperties properties,
      MeterRegistry meters) {
    this.delegate = delegate;
    this.redis = redis;
    this.keyPrefix =
        "analytics:insight:stats:" + (properties.includeSynthetic() ? "all" : "real") + ":";
    this.meters = meters;
  }

  /// 先读缓存，没命中的一次查完再写回。
  ///
  /// @param articleIds 文献 ID
  /// @return 有人看过的文献
  @Override
  public Map<Long, ReaderStats> statsOf(Collection<Long> articleIds) {
    if (articleIds.isEmpty()) {
      return Map.of();
    }
    List<Long> ids = List.copyOf(articleIds);
    List<String> cached;
    try {
      cached = redis.opsForValue().multiGet(ids.stream().map(this::key).toList());
    } catch (DataAccessException e) {
      log.warn("读计数缓存失败，直接查 ClickHouse：{}", e.getMessage());
      meters.counter(METER, "result", "error").increment();
      return delegate.statsOf(ids);
    }
    Map<Long, ReaderStats> stats = new HashMap<>();
    List<Long> misses = new ArrayList<>();
    for (int i = 0; i < ids.size(); i++) {
      String value = cached == null ? null : cached.get(i);
      if (value == null) {
        misses.add(ids.get(i));
      } else {
        ReaderStats hit = decode(ids.get(i), value);
        // 端口约定只返回有人看过的：缓存里的 0:0 不放进结果
        if (hit.viewers() > 0 || hit.fulltextReaders() > 0) {
          stats.put(hit.articleId(), hit);
        }
      }
    }
    meters.counter(METER, "result", "hit").increment(ids.size() - misses.size());
    meters.counter(METER, "result", "miss").increment(misses.size());
    if (!misses.isEmpty()) {
      Map<Long, ReaderStats> loaded = delegate.statsOf(misses);
      stats.putAll(loaded);
      writeBack(misses, loaded);
    }
    return Map.copyOf(stats);
  }

  /// 管道写回，没数据的写 `0:0`；写失败只记日志。
  ///
  /// @param misses 这次查过的文献
  /// @param loaded 查到的
  private void writeBack(List<Long> misses, Map<Long, ReaderStats> loaded) {
    try {
      redis.executePipelined(
          (RedisCallback<Object>)
              connection -> {
                for (Long id : misses) {
                  ReaderStats s = loaded.getOrDefault(id, ReaderStats.none(id));
                  connection
                      .stringCommands()
                      .setEx(
                          key(id).getBytes(StandardCharsets.UTF_8),
                          TTL.toSeconds(),
                          (s.viewers() + ":" + s.fulltextReaders())
                              .getBytes(StandardCharsets.UTF_8));
                }
                return null;
              });
    } catch (DataAccessException e) {
      log.warn("写计数缓存失败：{}", e.getMessage());
      meters.counter(METER, "result", "error").increment();
    }
  }

  /// 缓存键。
  ///
  /// @param articleId 文献 ID
  /// @return 键
  private String key(long articleId) {
    return keyPrefix + articleId;
  }

  /// 解析缓存值 `"{看过}:{读过全文}"`。
  ///
  /// @param articleId 文献 ID
  /// @param value 缓存值
  /// @return 读者数
  private static ReaderStats decode(long articleId, String value) {
    int colon = value.indexOf(':');
    return ReaderStats.of(
        articleId,
        Long.parseLong(value.substring(0, colon)),
        Long.parseLong(value.substring(colon + 1)));
  }
}
