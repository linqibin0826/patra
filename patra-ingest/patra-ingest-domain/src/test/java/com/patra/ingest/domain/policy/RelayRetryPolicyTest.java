package com.patra.ingest.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RelayRetryPolicy} 的单元测试，覆盖所有分支与边界条件。
 */
class RelayRetryPolicyTest {

    @Test
    @DisplayName("构造参数校验：base/max 必须为正，multiplier >= 1")
    void ctor_shouldValidateArguments() {
        assertThrows(IllegalArgumentException.class, () -> new RelayRetryPolicy(null, 2.0, Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> new RelayRetryPolicy(Duration.ZERO, 2.0, Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> new RelayRetryPolicy(Duration.ofSeconds(-1), 2.0, Duration.ofSeconds(10)));

        assertThrows(IllegalArgumentException.class, () -> new RelayRetryPolicy(Duration.ofMillis(1), 2.0, null));
        assertThrows(IllegalArgumentException.class, () -> new RelayRetryPolicy(Duration.ofMillis(1), 2.0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RelayRetryPolicy(Duration.ofMillis(1), 2.0, Duration.ofSeconds(-1)));

        assertThrows(IllegalArgumentException.class, () -> new RelayRetryPolicy(Duration.ofMillis(1), 0.9, Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("attempt<=1 直接返回 base，并受 max 限制")
    void computeDelay_shouldReturnBaseWhenFirstAttempt() {
        RelayRetryPolicy policy = new RelayRetryPolicy(Duration.ofSeconds(2), 2.0, Duration.ofSeconds(10));
        assertEquals(Duration.ofSeconds(2), policy.computeDelay(1));

        // base 超过 max 时应 clamp 到 max
        policy = new RelayRetryPolicy(Duration.ofSeconds(20), 2.0, Duration.ofSeconds(10));
        assertEquals(Duration.ofSeconds(10), policy.computeDelay(1));
    }

    @Test
    @DisplayName("指数退避：按 multiplier 放大并在超过 max 时 clamp")
    void computeDelay_shouldExponentialBackoffAndClamp() {
        RelayRetryPolicy policy = new RelayRetryPolicy(Duration.ofSeconds(1), 2.0, Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(2), policy.computeDelay(2));
        assertEquals(Duration.ofSeconds(4), policy.computeDelay(3));
        // 超过 max → clamp
        assertEquals(Duration.ofSeconds(5), policy.computeDelay(4));
    }

    @Test
    @DisplayName("double 溢出/超过 Long.MAX 时直接返回 max")
    void computeDelay_shouldReturnMaxWhenOverflow() {
        RelayRetryPolicy policy = new RelayRetryPolicy(Duration.ofMillis(1), 2.0, Duration.ofSeconds(10));
        // 让 scaledMillis 极大（2^(attempt-1)），触发 > Long.MAX 分支
        assertEquals(Duration.ofSeconds(10), policy.computeDelay(70));
    }
}
