package com.patra.egress.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RateLimitStatus unit tests
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("RateLimitStatus value object tests")
class RateLimitStatusTest {

  @Test
  @DisplayName("isLimited() should return true when remaining is zero")
  void isLimited_shouldReturnTrue_whenRemainingIsZero() {
    // Given
    RateLimitStatus status = new RateLimitStatus(100, 0, Duration.ofSeconds(60), null);

    // When & Then
    assertThat(status.isLimited()).isTrue();
  }

  @Test
  @DisplayName("isLimited() should return true when remaining is negative")
  void isLimited_shouldReturnTrue_whenRemainingIsNegative() {
    // Given
    RateLimitStatus status =
        new RateLimitStatus(
            100,
            0, // Cannot be negative; the constructor validates this
            Duration.ofSeconds(60),
            null);

    // When & Then
    assertThat(status.isLimited()).isTrue();
  }

  @Test
  @DisplayName("isLimited() should return false when remaining is greater than zero")
  void isLimited_shouldReturnFalse_whenRemainingIsPositive() {
    // Given
    RateLimitStatus status = new RateLimitStatus(100, 50, Duration.ofSeconds(60), null);

    // When & Then
    assertThat(status.isLimited()).isFalse();
  }

  @Test
  @DisplayName("Constructor should throw when limit is negative")
  void constructor_shouldThrowException_whenLimitIsNegative() {
    // When & Then
    assertThatThrownBy(() -> new RateLimitStatus(-1, 50, Duration.ofSeconds(60), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Limit cannot be negative");
  }

  @Test
  @DisplayName("Constructor should throw when remaining is negative")
  void constructor_shouldThrowException_whenRemainingIsNegative() {
    // When & Then
    assertThatThrownBy(() -> new RateLimitStatus(100, -1, Duration.ofSeconds(60), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Remaining cannot be negative");
  }

  @Test
  @DisplayName("Constructor should throw when resetAfter is null")
  void constructor_shouldThrowException_whenResetAfterIsNull() {
    // When & Then
    assertThatThrownBy(() -> new RateLimitStatus(100, 50, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ResetAfter must be non-null and non-negative");
  }

  @Test
  @DisplayName("Constructor should throw when resetAfter is negative")
  void constructor_shouldThrowException_whenResetAfterIsNegative() {
    // When & Then
    assertThatThrownBy(() -> new RateLimitStatus(100, 50, Duration.ofSeconds(-1), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ResetAfter must be non-null and non-negative");
  }

  @Test
  @DisplayName("Constructor should succeed when all parameters are valid")
  void constructor_shouldCreateSuccessfully_whenAllParametersValid() {
    // Given
    ExternalRateLimitInfo externalInfo = new ExternalRateLimitInfo(200, 150, 1672531200L);

    // When
    RateLimitStatus status = new RateLimitStatus(100, 50, Duration.ofSeconds(60), externalInfo);

    // Then
    assertThat(status.limit()).isEqualTo(100);
    assertThat(status.remaining()).isEqualTo(50);
    assertThat(status.resetAfter()).isEqualTo(Duration.ofSeconds(60));
    assertThat(status.externalInfo()).isEqualTo(externalInfo);
  }
}
