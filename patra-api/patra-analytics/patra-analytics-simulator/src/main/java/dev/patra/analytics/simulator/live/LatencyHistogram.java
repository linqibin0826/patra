package dev.patra.analytics.simulator.live;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLongArray;

/// 延迟直方图：对数分桶（相邻桶边界相差 4%），百分位误差不超过 5%；线程安全。
public final class LatencyHistogram {

  private static final double RATIO = 1.04;
  private static final double LOG_RATIO = Math.log(RATIO);
  private static final long MIN_NANOS = 10_000;
  private static final int BUCKETS = 512;

  private final AtomicLongArray counts = new AtomicLongArray(BUCKETS);

  /// 记一次。
  ///
  /// @param nanos 纳秒
  public void record(long nanos) {
    counts.incrementAndGet(bucket(nanos));
  }

  /// 次数。
  ///
  /// @return 次数
  public long count() {
    long total = 0;
    for (int i = 0; i < BUCKETS; i++) {
      total += counts.get(i);
    }
    return total;
  }

  /// 百分位。
  ///
  /// @param quantile 0～1
  /// @return 所在桶的上界；没有数据时为 0
  public Duration percentile(double quantile) {
    long total = count();
    if (total == 0) {
      return Duration.ZERO;
    }
    long target = Math.max(1, (long) Math.ceil(quantile * total));
    long seen = 0;
    for (int i = 0; i < BUCKETS; i++) {
      seen += counts.get(i);
      if (seen >= target) {
        return Duration.ofNanos(upperBound(i));
      }
    }
    return Duration.ofNanos(upperBound(BUCKETS - 1));
  }

  /// 落在哪个桶：第 i 个桶是 (MIN·1.04^(i−1), MIN·1.04^i]，第 0 个桶收 ≤ MIN 的。
  ///
  /// @param nanos 纳秒
  /// @return 桶号
  private static int bucket(long nanos) {
    if (nanos <= MIN_NANOS) {
      return 0;
    }
    int index = (int) Math.ceil(Math.log(nanos / (double) MIN_NANOS) / LOG_RATIO);
    return Math.min(index, BUCKETS - 1);
  }

  /// 桶的上界。
  ///
  /// @param bucket 桶号
  /// @return 纳秒
  private static long upperBound(int bucket) {
    return (long) (MIN_NANOS * Math.pow(RATIO, bucket));
  }
}
