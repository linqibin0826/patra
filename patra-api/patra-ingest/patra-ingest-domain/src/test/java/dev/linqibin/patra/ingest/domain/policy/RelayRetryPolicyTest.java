package dev.linqibin.patra.ingest.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// RelayRetryPolicy 单元测试
///
/// 测试重点：
///
/// - 参数验证（边界条件）
///   - 指数退避算法正确性
///   - 最大延迟限制（clamp）
///   - 边缘情况（溢出、极端值）
///
/// @author linqibin
@DisplayName("RelayRetryPolicy 单元测试")
class RelayRetryPolicyTest {

  @Nested
  @DisplayName("构造函数验证")
  class ConstructorValidation {

    @Test
    @DisplayName("应该在 base 为 null 时抛出 IllegalArgumentException")
    void shouldThrowWhenBaseIsNull() {
      // Given-When-Then
      assertThatThrownBy(() -> new RelayRetryPolicy(null, 2.0, Duration.ofMinutes(5)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("base backoff must be positive");
    }

    @Test
    @DisplayName("应该在 base 为零时抛出 IllegalArgumentException")
    void shouldThrowWhenBaseIsZero() {
      // Given-When-Then
      assertThatThrownBy(() -> new RelayRetryPolicy(Duration.ZERO, 2.0, Duration.ofMinutes(5)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("base backoff must be positive");
    }

    @Test
    @DisplayName("应该在 base 为负数时抛出 IllegalArgumentException")
    void shouldThrowWhenBaseIsNegative() {
      // Given-When-Then
      assertThatThrownBy(
              () -> new RelayRetryPolicy(Duration.ofSeconds(-1), 2.0, Duration.ofMinutes(5)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("base backoff must be positive");
    }

    @Test
    @DisplayName("应该在 max 为 null 时抛出 IllegalArgumentException")
    void shouldThrowWhenMaxIsNull() {
      // Given-When-Then
      assertThatThrownBy(() -> new RelayRetryPolicy(Duration.ofSeconds(5), 2.0, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("max backoff must be positive");
    }

    @Test
    @DisplayName("应该在 max 为零时抛出 IllegalArgumentException")
    void shouldThrowWhenMaxIsZero() {
      // Given-When-Then
      assertThatThrownBy(() -> new RelayRetryPolicy(Duration.ofSeconds(5), 2.0, Duration.ZERO))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("max backoff must be positive");
    }

    @Test
    @DisplayName("应该在 max 为负数时抛出 IllegalArgumentException")
    void shouldThrowWhenMaxIsNegative() {
      // Given-When-Then
      assertThatThrownBy(
              () -> new RelayRetryPolicy(Duration.ofSeconds(5), 2.0, Duration.ofSeconds(-10)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("max backoff must be positive");
    }

    @Test
    @DisplayName("应该在 multiplier 小于 1.0 时抛出 IllegalArgumentException")
    void shouldThrowWhenMultiplierLessThanOne() {
      // Given-When-Then
      assertThatThrownBy(
              () -> new RelayRetryPolicy(Duration.ofSeconds(5), 0.5, Duration.ofMinutes(5)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("multiplier must not be less than 1");
    }

    @Test
    @DisplayName("应该允许 multiplier 等于 1.0（常量退避）")
    void shouldAllowMultiplierEqualToOne() {
      // Given-When-Then
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(5), 1.0, Duration.ofMinutes(5));

      assertThat(policy).isNotNull();
    }

    @Test
    @DisplayName("应该成功构造有效的重试策略")
    void shouldConstructSuccessfullyWithValidParameters() {
      // Given
      Duration base = Duration.ofSeconds(5);
      double multiplier = 2.0;
      Duration max = Duration.ofMinutes(5);

      // When
      RelayRetryPolicy policy = new RelayRetryPolicy(base, multiplier, max);

      // Then
      assertThat(policy).isNotNull();
    }
  }

  @Nested
  @DisplayName("指数退避延迟计算")
  class ExponentialBackoffComputation {

    @Test
    @DisplayName("应该在第 1 次尝试时返回基础延迟")
    void shouldReturnBaseDelayForFirstAttempt() {
      // Given
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(5), 2.0, Duration.ofMinutes(5));

      // When
      Duration delay = policy.computeDelay(1);

      // Then
      assertThat(delay).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("应该在 attempt <= 1 时返回基础延迟")
    void shouldReturnBaseDelayForAttemptZeroOrNegative() {
      // Given
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(5), 2.0, Duration.ofMinutes(5));

      // When & Then
      assertThat(policy.computeDelay(0)).isEqualTo(Duration.ofSeconds(5));
      assertThat(policy.computeDelay(-1)).isEqualTo(Duration.ofSeconds(5));
      assertThat(policy.computeDelay(-100)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("应该在第 2 次尝试时返回 base * multiplier")
    void shouldReturnCorrectDelayForSecondAttempt() {
      // Given
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(5), 2.0, Duration.ofMinutes(5));

      // When
      Duration delay = policy.computeDelay(2);

      // Then
      assertThat(delay).isEqualTo(Duration.ofSeconds(10)); // 5 * 2^1 = 10
    }

    @Test
    @DisplayName("应该在第 3 次尝试时返回 base * multiplier^2")
    void shouldReturnCorrectDelayForThirdAttempt() {
      // Given
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(5), 2.0, Duration.ofMinutes(5));

      // When
      Duration delay = policy.computeDelay(3);

      // Then
      assertThat(delay).isEqualTo(Duration.ofSeconds(20)); // 5 * 2^2 = 20
    }

    @Test
    @DisplayName("应该正确计算完整的指数退避序列")
    void shouldComputeCompleteExponentialSequence() {
      // Given
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(5), 2.0, Duration.ofMinutes(5));

      // When & Then - 测试序列: 5s → 10s → 20s → 40s → 80s → 160s → 300s(达到上限)
      assertThat(policy.computeDelay(1)).isEqualTo(Duration.ofSeconds(5)); // 5 * 2^0 = 5
      assertThat(policy.computeDelay(2)).isEqualTo(Duration.ofSeconds(10)); // 5 * 2^1 = 10
      assertThat(policy.computeDelay(3)).isEqualTo(Duration.ofSeconds(20)); // 5 * 2^2 = 20
      assertThat(policy.computeDelay(4)).isEqualTo(Duration.ofSeconds(40)); // 5 * 2^3 = 40
      assertThat(policy.computeDelay(5)).isEqualTo(Duration.ofSeconds(80)); // 5 * 2^4 = 80
      assertThat(policy.computeDelay(6)).isEqualTo(Duration.ofSeconds(160)); // 5 * 2^5 = 160
      assertThat(policy.computeDelay(7))
          .isEqualTo(Duration.ofSeconds(300)); // 5 * 2^6 = 320 → 300(max)
    }
  }

  @Nested
  @DisplayName("最大延迟限制（Clamp）")
  class MaxDelayClamp {

    @Test
    @DisplayName("应该在计算延迟超过最大值时返回最大延迟")
    void shouldClampToMaxWhenComputedDelayExceedsMax() {
      // Given
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(5), 2.0, Duration.ofMinutes(5));

      // When - 第 7 次尝试: 5 * 2^6 = 320 秒 > 300 秒(最大值)
      Duration delay = policy.computeDelay(7);

      // Then
      assertThat(delay).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("应该在所有后续尝试中持续返回最大延迟")
    void shouldKeepReturningMaxDelayForAllSubsequentAttempts() {
      // Given
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(5), 2.0, Duration.ofMinutes(5));

      // When & Then - 第 7 次及以后都返回最大值
      assertThat(policy.computeDelay(7)).isEqualTo(Duration.ofMinutes(5));
      assertThat(policy.computeDelay(8)).isEqualTo(Duration.ofMinutes(5));
      assertThat(policy.computeDelay(10)).isEqualTo(Duration.ofMinutes(5));
      assertThat(policy.computeDelay(100)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("应该在基础延迟大于最大延迟时立即返回最大延迟")
    void shouldReturnMaxImmediatelyWhenBaseExceedsMax() {
      // Given - base = 10 秒, max = 5 秒
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(10), 2.0, Duration.ofSeconds(5));

      // When
      Duration delay = policy.computeDelay(1);

      // Then
      assertThat(delay).isEqualTo(Duration.ofSeconds(5));
    }
  }

  @Nested
  @DisplayName("边缘情况")
  class EdgeCases {

    @Test
    @DisplayName("应该在 multiplier = 1.0 时返回常量延迟（不增长）")
    void shouldReturnConstantDelayWhenMultiplierIsOne() {
      // Given - 常量退避策略
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(5), 1.0, Duration.ofMinutes(5));

      // When & Then - 所有尝试都返回相同的延迟
      assertThat(policy.computeDelay(1)).isEqualTo(Duration.ofSeconds(5));
      assertThat(policy.computeDelay(2)).isEqualTo(Duration.ofSeconds(5));
      assertThat(policy.computeDelay(5)).isEqualTo(Duration.ofSeconds(5));
      assertThat(policy.computeDelay(100)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("应该在延迟计算溢出时返回最大延迟")
    void shouldReturnMaxWhenComputationOverflows() {
      // Given - 使用较大的 multiplier 和较多的尝试次数
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(1), 10.0, Duration.ofMinutes(10));

      // When - 第 20 次尝试: 1 * 10^19 会溢出
      Duration delay = policy.computeDelay(20);

      // Then
      assertThat(delay).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("应该在延迟计算达到 Long.MAX_VALUE 时返回最大延迟")
    void shouldReturnMaxWhenComputationReachesLongMaxValue() {
      // Given
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofMillis(1), 2.0, Duration.ofMinutes(1));

      // When - 尝试非常大的次数
      Duration delay = policy.computeDelay(100);

      // Then
      assertThat(delay).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("应该正确处理毫秒级基础延迟")
    void shouldHandleMillisecondBaseDelay() {
      // Given
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofMillis(100), 2.0, Duration.ofSeconds(10));

      // When & Then
      assertThat(policy.computeDelay(1)).isEqualTo(Duration.ofMillis(100)); // 100ms
      assertThat(policy.computeDelay(2)).isEqualTo(Duration.ofMillis(200)); // 200ms
      assertThat(policy.computeDelay(3)).isEqualTo(Duration.ofMillis(400)); // 400ms
      assertThat(policy.computeDelay(4)).isEqualTo(Duration.ofMillis(800)); // 800ms
    }

    @Test
    @DisplayName("应该正确处理较小的 multiplier（如 1.5）")
    void shouldHandleSmallerMultiplier() {
      // Given - 使用 1.5 倍增长
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(2), 1.5, Duration.ofMinutes(5));

      // When & Then
      assertThat(policy.computeDelay(1)).isEqualTo(Duration.ofSeconds(2)); // 2 * 1.5^0 = 2
      assertThat(policy.computeDelay(2)).isEqualTo(Duration.ofSeconds(3)); // 2 * 1.5^1 = 3
      assertThat(policy.computeDelay(3)).isEqualTo(Duration.ofMillis(4500)); // 2 * 1.5^2 = 4.5
      assertThat(policy.computeDelay(4)).isEqualTo(Duration.ofMillis(6750)); // 2 * 1.5^3 = 6.75
    }

    @Test
    @DisplayName("应该正确处理较大的 multiplier（如 3.0）")
    void shouldHandleLargerMultiplier() {
      // Given - 使用 3.0 倍增长
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(1), 3.0, Duration.ofMinutes(5));

      // When & Then
      assertThat(policy.computeDelay(1)).isEqualTo(Duration.ofSeconds(1)); // 1 * 3^0 = 1
      assertThat(policy.computeDelay(2)).isEqualTo(Duration.ofSeconds(3)); // 1 * 3^1 = 3
      assertThat(policy.computeDelay(3)).isEqualTo(Duration.ofSeconds(9)); // 1 * 3^2 = 9
      assertThat(policy.computeDelay(4)).isEqualTo(Duration.ofSeconds(27)); // 1 * 3^3 = 27
      assertThat(policy.computeDelay(5)).isEqualTo(Duration.ofSeconds(81)); // 1 * 3^4 = 81
      assertThat(policy.computeDelay(6)).isEqualTo(Duration.ofSeconds(243)); // 1 * 3^5 = 243
      assertThat(policy.computeDelay(7))
          .isEqualTo(Duration.ofSeconds(300)); // 1 * 3^6 = 729 → 300(max)
    }
  }

  @Nested
  @DisplayName("真实场景示例")
  class RealWorldScenarios {

    @Test
    @DisplayName("应该模拟 PubMed API 重试策略（5s, 2.0, 5min）")
    void shouldSimulatePubmedApiRetryStrategy() {
      // Given - 典型的 API 重试策略
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(5), 2.0, Duration.ofMinutes(5));

      // When & Then - 模拟 7 次重试
      assertThat(policy.computeDelay(1)).isEqualTo(Duration.ofSeconds(5));
      assertThat(policy.computeDelay(2)).isEqualTo(Duration.ofSeconds(10));
      assertThat(policy.computeDelay(3)).isEqualTo(Duration.ofSeconds(20));
      assertThat(policy.computeDelay(4)).isEqualTo(Duration.ofSeconds(40));
      assertThat(policy.computeDelay(5)).isEqualTo(Duration.ofSeconds(80));
      assertThat(policy.computeDelay(6)).isEqualTo(Duration.ofSeconds(160));
      assertThat(policy.computeDelay(7)).isEqualTo(Duration.ofSeconds(300)); // 达到上限
    }

    @Test
    @DisplayName("应该模拟快速重试策略（1s, 2.0, 30s）")
    void shouldSimulateFastRetryStrategy() {
      // Given - 快速重试策略
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30));

      // When & Then
      assertThat(policy.computeDelay(1)).isEqualTo(Duration.ofSeconds(1));
      assertThat(policy.computeDelay(2)).isEqualTo(Duration.ofSeconds(2));
      assertThat(policy.computeDelay(3)).isEqualTo(Duration.ofSeconds(4));
      assertThat(policy.computeDelay(4)).isEqualTo(Duration.ofSeconds(8));
      assertThat(policy.computeDelay(5)).isEqualTo(Duration.ofSeconds(16));
      assertThat(policy.computeDelay(6)).isEqualTo(Duration.ofSeconds(30)); // 达到上限
    }

    @Test
    @DisplayName("应该模拟保守重试策略（10s, 1.5, 10min）")
    void shouldSimulateConservativeRetryStrategy() {
      // Given - 保守的重试策略
      RelayRetryPolicy policy =
          new RelayRetryPolicy(Duration.ofSeconds(10), 1.5, Duration.ofMinutes(10));

      // When & Then
      assertThat(policy.computeDelay(1)).isEqualTo(Duration.ofSeconds(10));
      assertThat(policy.computeDelay(2)).isEqualTo(Duration.ofSeconds(15));
      assertThat(policy.computeDelay(3)).isEqualTo(Duration.ofMillis(22500)); // 22.5s
      assertThat(policy.computeDelay(4)).isEqualTo(Duration.ofMillis(33750)); // 33.75s
    }
  }
}
