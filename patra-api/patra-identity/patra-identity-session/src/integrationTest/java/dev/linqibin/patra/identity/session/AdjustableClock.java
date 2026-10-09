package dev.linqibin.patra.identity.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/// 测试用的时钟：从固定时刻开始，手动往前拨。
final class AdjustableClock extends Clock {

  private Instant now;

  /// 从指定时刻开始。
  ///
  /// @param start 起始时刻
  AdjustableClock(Instant start) {
    this.now = start;
  }

  /// 往前拨。
  ///
  /// @param duration 拨多久
  void advance(Duration duration) {
    now = now.plus(duration);
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
