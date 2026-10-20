package dev.patra.tool.test.clock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/// 测试用的时钟：从固定时刻开始，手动往前拨或直接设到某个时刻，时区固定 UTC。
///
/// 集成测试把它声明成 `@Primary` 的 `Clock` Bean 就能替换启动器默认的系统时钟。
public final class MutableClock extends Clock {

  private Instant now;

  /// 只能经静态工厂创建。
  ///
  /// @param start 起始时刻
  private MutableClock(Instant start) {
    this.now = Objects.requireNonNull(start, "start 不能为 null");
  }

  /// 从指定时刻开始。
  ///
  /// @param start 起始时刻
  /// @return 时钟
  public static MutableClock startingAt(Instant start) {
    return new MutableClock(start);
  }

  /// 往前拨。
  ///
  /// @param duration 拨多久
  public void advance(Duration duration) {
    now = now.plus(Objects.requireNonNull(duration, "duration 不能为 null"));
  }

  /// 直接设到某个时刻。
  ///
  /// @param instant 新的当前时刻
  public void set(Instant instant) {
    now = Objects.requireNonNull(instant, "instant 不能为 null");
  }

  /// 固定 UTC。
  ///
  /// @return UTC
  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  /// 不支持换时区，原样返回。
  ///
  /// @param zone 忽略
  /// @return 自己
  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  /// 当前时刻。
  ///
  /// @return 当前时刻
  @Override
  public Instant instant() {
    return now;
  }
}
