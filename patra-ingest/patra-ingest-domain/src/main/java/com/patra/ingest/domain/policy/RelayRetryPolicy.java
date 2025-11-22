package com.patra.ingest.domain.policy;

import java.time.Duration;

/// Relay 重试策略 - 实现指数退避算法。
///
/// 纯领域策略,无框架依赖。
///
/// 退避算法: `delay = min(base * multiplier^(attempt-1), max)`
///
/// 示例配置:
///
/// - 基础延迟: 5秒
///   - 倍数: 2.0
///   - 最大延迟: 5分钟
///   - 重试序列: 5s → 10s → 20s → 40s → 80s → 160s → 300s(达到上限)
///
/// @author linqibin
/// @since 0.1.0
public class RelayRetryPolicy {

  private final Duration base;
  private final double multiplier;
  private final Duration max;

  /// 构造重试策略。
  ///
  /// @param base 基础退避延迟(必须为正数)
  /// @param multiplier 指数倍数(必须 >= 1.0)
  /// @param max 最大退避延迟(必须为正数)
  /// @throws IllegalArgumentException 如果参数无效
  public RelayRetryPolicy(Duration base, double multiplier, Duration max) {
    if (base == null || base.isZero() || base.isNegative()) {
      throw new IllegalArgumentException("base backoff must be positive");
    }
    if (max == null || max.isZero() || max.isNegative()) {
      throw new IllegalArgumentException("max backoff must be positive");
    }
    if (multiplier < 1.0d) {
      throw new IllegalArgumentException("multiplier must not be less than 1");
    }
    this.base = base;
    this.multiplier = multiplier;
    this.max = max;
  }

  /// 计算给定尝试次数后的退避延迟。
  ///
  /// 尝试次数从 1 开始计数。
  ///
  /// @param attempt 尝试次数(>= 1)
  /// @return 退避延迟时长
  public Duration computeDelay(int attempt) {
    if (attempt <= 1) {
      return clamp(base);
    }
    double factor = Math.pow(multiplier, attempt - 1);
    double scaledMillis = base.toMillis() * factor;
    if (Double.isInfinite(scaledMillis) || scaledMillis > Long.MAX_VALUE) {
      return max;
    }
    long millis = Math.max(base.toMillis(), (long) scaledMillis);
    return clamp(Duration.ofMillis(millis));
  }

  /// 将延迟限制在最大值范围内。
  ///
  /// @param candidate 候选延迟
  /// @return 限制后的延迟
  private Duration clamp(Duration candidate) {
    if (candidate.compareTo(max) > 0) {
      return max;
    }
    return candidate;
  }
}
