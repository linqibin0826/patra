package dev.patra.analytics.infra.collect.rate;

import dev.patra.analytics.domain.collect.policy.BotClassification;
import dev.patra.analytics.domain.collect.port.rate.DeviceRatePort;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/// 用 Redis 有序集合做爬虫频率规则的滑动窗口（采集 spec §4.3）。
///
/// 一段 Lua 脚本先清掉窗口外的成员，计入时以 `event_id` 为成员、收到时刻为分数加入，再返回条数；同一事件重发只占
/// 一个位置。Redis 出任何错都返回空，由用例跳过这条规则：频率规则不能让 Redis 故障拖垮采集。
@Slf4j
@Component
public class RedisDeviceRateAdapter implements DeviceRatePort {

  /// 窗口键前缀
  static final String KEY_PREFIX = "ana:bot-rate:device:";

  private static final RedisScript<Long> SCRIPT =
      RedisScript.of(new ClassPathResource("redis/device-rate.lua"), Long.class);

  private final StringRedisTemplate redis;

  /// 创建适配器。
  ///
  /// @param redis Redis 客户端
  public RedisDeviceRateAdapter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /// 记录一条事件并返回窗口内的条数。
  ///
  /// @param deviceId 设备 ID
  /// @param eventId 事件 ID
  /// @param counted 是否计入窗口
  /// @param at 收到的时刻
  /// @return 窗口内的条数；Redis 出错时为空
  @Override
  public OptionalInt observe(String deviceId, String eventId, boolean counted, Instant at) {
    try {
      Long count =
          redis.execute(
              SCRIPT,
              List.of(KEY_PREFIX + deviceId),
              Long.toString(at.toEpochMilli()),
              Long.toString(BotClassification.RATE_WINDOW.toMillis()),
              eventId,
              counted ? "1" : "0");
      return count == null ? OptionalInt.empty() : OptionalInt.of(count.intValue());
    } catch (RuntimeException e) {
      log.warn("爬虫频率窗口读写失败，本条跳过频率规则: {}", e.getMessage());
      return OptionalInt.empty();
    }
  }
}
