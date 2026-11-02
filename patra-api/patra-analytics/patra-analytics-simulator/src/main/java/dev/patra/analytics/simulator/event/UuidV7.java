package dev.patra.analytics.simulator.event;

import java.time.Instant;
import java.util.SplittableRandom;
import java.util.UUID;

/// UUIDv7（RFC 9562）：高 48 位是毫秒时间戳，按时间有序；其余随机位来自给定的随机源，同种子生成结果相同。
public final class UuidV7 {

  private UuidV7() {}

  /// 生成一个 UUIDv7。
  ///
  /// @param at 时间戳
  /// @param random 随机源
  /// @return UUID（`toString()` 为小写）
  public static UUID of(Instant at, SplittableRandom random) {
    long millis = at.toEpochMilli();
    long most = (millis << 16) | 0x7000L | (random.nextLong() & 0x0FFFL);
    long least = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
    return new UUID(most, least);
  }
}
