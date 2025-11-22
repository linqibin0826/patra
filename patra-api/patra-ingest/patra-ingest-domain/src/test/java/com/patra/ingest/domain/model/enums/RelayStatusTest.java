package com.patra.ingest.domain.model.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// RelayStatus 枚举测试。
///
/// @author Patra Team
@DisplayName("RelayStatus 枚举测试")
class RelayStatusTest {

  @Nested
  @DisplayName("枚举值测试")
  class EnumValuesTest {

    @Test
    @DisplayName("应该包含所有预期的枚举值")
    void shouldContainAllExpectedValues() {
      // Given & When
      RelayStatus[] values = RelayStatus.values();

      // Then
      assertThat(values)
          .hasSize(4)
          .containsExactly(
              RelayStatus.PUBLISHED,
              RelayStatus.DEFERRED,
              RelayStatus.FAILED,
              RelayStatus.LEASE_MISSED);
    }

    @Test
    @DisplayName("应该通过名称正确获取枚举值")
    void shouldGetEnumByName() {
      // Given & When
      RelayStatus published = RelayStatus.valueOf("PUBLISHED");
      RelayStatus deferred = RelayStatus.valueOf("DEFERRED");
      RelayStatus failed = RelayStatus.valueOf("FAILED");
      RelayStatus leaseMissed = RelayStatus.valueOf("LEASE_MISSED");

      // Then
      assertThat(published).isEqualTo(RelayStatus.PUBLISHED);
      assertThat(deferred).isEqualTo(RelayStatus.DEFERRED);
      assertThat(failed).isEqualTo(RelayStatus.FAILED);
      assertThat(leaseMissed).isEqualTo(RelayStatus.LEASE_MISSED);
    }

    @Test
    @DisplayName("当使用无效名称时应该抛出异常")
    void shouldThrowExceptionForInvalidName() {
      // Given
      String invalidName = "INVALID";

      // When & Then
      assertThatThrownBy(() -> RelayStatus.valueOf(invalidName))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("code 属性测试")
  class CodePropertyTest {

    @Test
    @DisplayName("所有枚举值应该有正确的 code")
    void allValuesShouldHaveCorrectCode() {
      // Given & When & Then
      assertThat(RelayStatus.PUBLISHED.getCode()).isEqualTo("PUBLISHED");
      assertThat(RelayStatus.DEFERRED.getCode()).isEqualTo("DEFERRED");
      assertThat(RelayStatus.FAILED.getCode()).isEqualTo("FAILED");
      assertThat(RelayStatus.LEASE_MISSED.getCode()).isEqualTo("LEASE_MISSED");
    }
  }

  @Nested
  @DisplayName("description 属性测试")
  class DescriptionPropertyTest {

    @Test
    @DisplayName("所有枚举值应该有非空的描述")
    void allValuesShouldHaveNonEmptyDescription() {
      // Given & When & Then
      for (RelayStatus status : RelayStatus.values()) {
        assertThat(status.getDescription())
            .as("状态 %s 应该有描述", status.name())
            .isNotNull()
            .isNotBlank();
      }
    }

    @Test
    @DisplayName("所有描述应该使用中文")
    void allDescriptionsShouldBeInChinese() {
      // Given & When & Then
      assertThat(RelayStatus.PUBLISHED.getDescription()).isEqualTo("发布成功");
      assertThat(RelayStatus.DEFERRED.getDescription()).isEqualTo("延迟重试");
      assertThat(RelayStatus.FAILED.getDescription()).isEqualTo("永久失败");
      assertThat(RelayStatus.LEASE_MISSED.getDescription()).isEqualTo("租约竞争失败");
    }
  }

  @Nested
  @DisplayName("terminal 属性测试")
  class TerminalPropertyTest {

    @Test
    @DisplayName("PUBLISHED 应该是终态")
    void publishedShouldBeTerminal() {
      // Given & When
      boolean isTerminal = RelayStatus.PUBLISHED.isTerminal();

      // Then
      assertThat(isTerminal).isTrue();
    }

    @Test
    @DisplayName("FAILED 应该是终态")
    void failedShouldBeTerminal() {
      // Given & When
      boolean isTerminal = RelayStatus.FAILED.isTerminal();

      // Then
      assertThat(isTerminal).isTrue();
    }

    @Test
    @DisplayName("DEFERRED 不应该是终态")
    void deferredShouldNotBeTerminal() {
      // Given & When
      boolean isTerminal = RelayStatus.DEFERRED.isTerminal();

      // Then
      assertThat(isTerminal).isFalse();
    }

    @Test
    @DisplayName("LEASE_MISSED 不应该是终态")
    void leaseMissedShouldNotBeTerminal() {
      // Given & When
      boolean isTerminal = RelayStatus.LEASE_MISSED.isTerminal();

      // Then
      assertThat(isTerminal).isFalse();
    }

    @Test
    @DisplayName("应该只有两个终态")
    void shouldHaveExactlyTwoTerminalStates() {
      // Given & When
      long terminalCount =
          java.util.Arrays.stream(RelayStatus.values()).filter(RelayStatus::isTerminal).count();

      // Then
      assertThat(terminalCount).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("retryable 属性测试")
  class RetryablePropertyTest {

    @Test
    @DisplayName("DEFERRED 应该可以重试")
    void deferredShouldBeRetryable() {
      // Given & When
      boolean isRetryable = RelayStatus.DEFERRED.isRetryable();

      // Then
      assertThat(isRetryable).isTrue();
    }

    @Test
    @DisplayName("LEASE_MISSED 应该可以重试")
    void leaseMissedShouldBeRetryable() {
      // Given & When
      boolean isRetryable = RelayStatus.LEASE_MISSED.isRetryable();

      // Then
      assertThat(isRetryable).isTrue();
    }

    @Test
    @DisplayName("PUBLISHED 不应该可以重试")
    void publishedShouldNotBeRetryable() {
      // Given & When
      boolean isRetryable = RelayStatus.PUBLISHED.isRetryable();

      // Then
      assertThat(isRetryable).isFalse();
    }

    @Test
    @DisplayName("FAILED 不应该可以重试")
    void failedShouldNotBeRetryable() {
      // Given & When
      boolean isRetryable = RelayStatus.FAILED.isRetryable();

      // Then
      assertThat(isRetryable).isFalse();
    }

    @Test
    @DisplayName("应该只有两个可重试的状态")
    void shouldHaveExactlyTwoRetryableStates() {
      // Given & When
      long retryableCount =
          java.util.Arrays.stream(RelayStatus.values()).filter(RelayStatus::isRetryable).count();

      // Then
      assertThat(retryableCount).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("terminal 和 retryable 互斥性测试")
  class TerminalAndRetryableMutualExclusivityTest {

    @Test
    @DisplayName("终态不应该可以重试")
    void terminalStatesShouldNotBeRetryable() {
      // Given & When & Then
      for (RelayStatus status : RelayStatus.values()) {
        if (status.isTerminal()) {
          assertThat(status.isRetryable()).as("终态 %s 不应该可以重试", status.name()).isFalse();
        }
      }
    }

    @Test
    @DisplayName("可重试状态不应该是终态")
    void retryableStatesShouldNotBeTerminal() {
      // Given & When & Then
      for (RelayStatus status : RelayStatus.values()) {
        if (status.isRetryable()) {
          assertThat(status.isTerminal()).as("可重试状态 %s 不应该是终态", status.name()).isFalse();
        }
      }
    }
  }

  @Nested
  @DisplayName("fromCode 方法测试")
  class FromCodeMethodTest {

    @Test
    @DisplayName("应该通过精确 code 正确解析")
    void shouldParseFromExactCode() {
      // Given
      String code = "PUBLISHED";

      // When
      RelayStatus result = RelayStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(RelayStatus.PUBLISHED);
    }

    @Test
    @DisplayName("应该解析所有有效的 code")
    void shouldParseAllValidCodes() {
      // Given & When & Then
      assertThat(RelayStatus.fromCode("PUBLISHED")).isEqualTo(RelayStatus.PUBLISHED);
      assertThat(RelayStatus.fromCode("DEFERRED")).isEqualTo(RelayStatus.DEFERRED);
      assertThat(RelayStatus.fromCode("FAILED")).isEqualTo(RelayStatus.FAILED);
      assertThat(RelayStatus.fromCode("LEASE_MISSED")).isEqualTo(RelayStatus.LEASE_MISSED);
    }

    @Test
    @DisplayName("当 code 为 null 时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsNull() {
      // Given
      String code = null;

      // When & Then
      assertThatThrownBy(() -> RelayStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("RelayStatus 代码不能为 null 或空白");
    }

    @Test
    @DisplayName("当 code 为空字符串时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsEmpty() {
      // Given
      String code = "";

      // When & Then
      assertThatThrownBy(() -> RelayStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("RelayStatus 代码不能为 null 或空白");
    }

    @Test
    @DisplayName("当 code 为空白字符串时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsBlank() {
      // Given
      String code = "   ";

      // When & Then
      assertThatThrownBy(() -> RelayStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("RelayStatus 代码不能为 null 或空白");
    }

    @Test
    @DisplayName("当 code 无效时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsInvalid() {
      // Given
      String code = "INVALID_STATUS";

      // When & Then
      assertThatThrownBy(() -> RelayStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("无效的 RelayStatus 代码: " + code);
    }
  }

  @Nested
  @DisplayName("枚举业务语义测试")
  class BusinessSemanticsTest {

    @Test
    @DisplayName("PUBLISHED 表示成功终态")
    void publishedIndicatesSuccessTerminalState() {
      // Given & When
      RelayStatus published = RelayStatus.PUBLISHED;

      // Then
      assertThat(published.isTerminal()).isTrue();
      assertThat(published.isRetryable()).isFalse();
      assertThat(published.getDescription()).isEqualTo("发布成功");
    }

    @Test
    @DisplayName("DEFERRED 表示瞬态错误可重试")
    void deferredIndicatesTransientRetryableError() {
      // Given & When
      RelayStatus deferred = RelayStatus.DEFERRED;

      // Then
      assertThat(deferred.isTerminal()).isFalse();
      assertThat(deferred.isRetryable()).isTrue();
      assertThat(deferred.getDescription()).isEqualTo("延迟重试");
    }

    @Test
    @DisplayName("FAILED 表示永久失败终态")
    void failedIndicatesPermanentFailureTerminalState() {
      // Given & When
      RelayStatus failed = RelayStatus.FAILED;

      // Then
      assertThat(failed.isTerminal()).isTrue();
      assertThat(failed.isRetryable()).isFalse();
      assertThat(failed.getDescription()).isEqualTo("永久失败");
    }

    @Test
    @DisplayName("LEASE_MISSED 表示乐观锁失败可重试")
    void leaseMissedIndicatesOptimisticLockFailure() {
      // Given & When
      RelayStatus leaseMissed = RelayStatus.LEASE_MISSED;

      // Then
      assertThat(leaseMissed.isTerminal()).isFalse();
      assertThat(leaseMissed.isRetryable()).isTrue();
      assertThat(leaseMissed.getDescription()).isEqualTo("租约竞争失败");
    }
  }

  @Nested
  @DisplayName("状态分类测试")
  class StateClassificationTest {

    @Test
    @DisplayName("应该正确分类成功状态")
    void shouldCorrectlyClassifySuccessState() {
      // Given & When
      RelayStatus[] successStates = {RelayStatus.PUBLISHED};

      // Then
      for (RelayStatus status : successStates) {
        assertThat(status.isTerminal()).isTrue();
        assertThat(status.getDescription()).contains("成功");
      }
    }

    @Test
    @DisplayName("应该正确分类失败终态")
    void shouldCorrectlyClassifyFailureTerminalState() {
      // Given & When
      RelayStatus[] failureTerminalStates = {RelayStatus.FAILED};

      // Then
      for (RelayStatus status : failureTerminalStates) {
        assertThat(status.isTerminal()).isTrue();
        assertThat(status.isRetryable()).isFalse();
      }
    }

    @Test
    @DisplayName("应该正确分类可重试状态")
    void shouldCorrectlyClassifyRetryableStates() {
      // Given & When
      RelayStatus[] retryableStates = {RelayStatus.DEFERRED, RelayStatus.LEASE_MISSED};

      // Then
      for (RelayStatus status : retryableStates) {
        assertThat(status.isRetryable()).isTrue();
        assertThat(status.isTerminal()).isFalse();
      }
    }
  }
}
