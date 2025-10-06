package com.patra.egress.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RateLimitStatus unit tests
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("RateLimitStatus 值对象测试")
class RateLimitStatusTest {

    @Test
    @DisplayName("isLimited() - 应该在remaining为0时返回true")
    void isLimited_shouldReturnTrue_whenRemainingIsZero() {
        // Given
        RateLimitStatus status = new RateLimitStatus(
            100,
            0,
            Duration.ofSeconds(60),
            null
        );

        // When & Then
        assertThat(status.isLimited()).isTrue();
    }

    @Test
    @DisplayName("isLimited() - 应该在remaining为负数时返回true")
    void isLimited_shouldReturnTrue_whenRemainingIsNegative() {
        // Given
        RateLimitStatus status = new RateLimitStatus(
            100,
            0, // 不能为负数，构造函数会校验
            Duration.ofSeconds(60),
            null
        );

        // When & Then
        assertThat(status.isLimited()).isTrue();
    }

    @Test
    @DisplayName("isLimited() - 应该在remaining大于0时返回false")
    void isLimited_shouldReturnFalse_whenRemainingIsPositive() {
        // Given
        RateLimitStatus status = new RateLimitStatus(
            100,
            50,
            Duration.ofSeconds(60),
            null
        );

        // When & Then
        assertThat(status.isLimited()).isFalse();
    }

    @Test
    @DisplayName("构造函数 - 应该在limit为负数时抛出异常")
    void constructor_shouldThrowException_whenLimitIsNegative() {
        // When & Then
        assertThatThrownBy(() -> new RateLimitStatus(
            -1,
            50,
            Duration.ofSeconds(60),
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Limit cannot be negative");
    }

    @Test
    @DisplayName("构造函数 - 应该在remaining为负数时抛出异常")
    void constructor_shouldThrowException_whenRemainingIsNegative() {
        // When & Then
        assertThatThrownBy(() -> new RateLimitStatus(
            100,
            -1,
            Duration.ofSeconds(60),
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Remaining cannot be negative");
    }

    @Test
    @DisplayName("构造函数 - 应该在resetAfter为null时抛出异常")
    void constructor_shouldThrowException_whenResetAfterIsNull() {
        // When & Then
        assertThatThrownBy(() -> new RateLimitStatus(
            100,
            50,
            null,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ResetAfter must be non-null and non-negative");
    }

    @Test
    @DisplayName("构造函数 - 应该在resetAfter为负数时抛出异常")
    void constructor_shouldThrowException_whenResetAfterIsNegative() {
        // When & Then
        assertThatThrownBy(() -> new RateLimitStatus(
            100,
            50,
            Duration.ofSeconds(-1),
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ResetAfter must be non-null and non-negative");
    }

    @Test
    @DisplayName("构造函数 - 应该在所有参数有效时创建成功")
    void constructor_shouldCreateSuccessfully_whenAllParametersValid() {
        // Given
        ExternalRateLimitInfo externalInfo = new ExternalRateLimitInfo(200, 150, 1672531200L);

        // When
        RateLimitStatus status = new RateLimitStatus(
            100,
            50,
            Duration.ofSeconds(60),
            externalInfo
        );

        // Then
        assertThat(status.limit()).isEqualTo(100);
        assertThat(status.remaining()).isEqualTo(50);
        assertThat(status.resetAfter()).isEqualTo(Duration.ofSeconds(60));
        assertThat(status.externalInfo()).isEqualTo(externalInfo);
    }
}
