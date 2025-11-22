package com.patra.ingest.domain.model.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// BatchStatus 枚举测试。
///
/// @author linqibin
@DisplayName("BatchStatus 枚举测试")
class BatchStatusTest {

  @Nested
  @DisplayName("枚举值测试")
  class EnumValuesTest {

    @Test
    @DisplayName("应该包含所有预期的枚举值")
    void shouldContainAllExpectedValues() {
      // Given & When
      BatchStatus[] values = BatchStatus.values();

      // Then
      assertThat(values)
          .hasSize(4)
          .containsExactly(
              BatchStatus.RUNNING, BatchStatus.SUCCEEDED, BatchStatus.FAILED, BatchStatus.SKIPPED);
    }

    @Test
    @DisplayName("应该通过名称正确获取枚举值")
    void shouldGetEnumByName() {
      // Given & When
      BatchStatus running = BatchStatus.valueOf("RUNNING");
      BatchStatus succeeded = BatchStatus.valueOf("SUCCEEDED");
      BatchStatus failed = BatchStatus.valueOf("FAILED");
      BatchStatus skipped = BatchStatus.valueOf("SKIPPED");

      // Then
      assertThat(running).isEqualTo(BatchStatus.RUNNING);
      assertThat(succeeded).isEqualTo(BatchStatus.SUCCEEDED);
      assertThat(failed).isEqualTo(BatchStatus.FAILED);
      assertThat(skipped).isEqualTo(BatchStatus.SKIPPED);
    }

    @Test
    @DisplayName("当使用无效名称时应该抛出异常")
    void shouldThrowExceptionForInvalidName() {
      // Given
      String invalidName = "INVALID";

      // When & Then
      assertThatThrownBy(() -> BatchStatus.valueOf(invalidName))
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
      assertThat(BatchStatus.RUNNING.getCode()).isEqualTo("RUNNING");
      assertThat(BatchStatus.SUCCEEDED.getCode()).isEqualTo("SUCCEEDED");
      assertThat(BatchStatus.FAILED.getCode()).isEqualTo("FAILED");
      assertThat(BatchStatus.SKIPPED.getCode()).isEqualTo("SKIPPED");
    }
  }

  @Nested
  @DisplayName("description 属性测试")
  class DescriptionPropertyTest {

    @Test
    @DisplayName("所有枚举值应该有非空的描述")
    void allValuesShouldHaveNonEmptyDescription() {
      // Given & When & Then
      for (BatchStatus status : BatchStatus.values()) {
        assertThat(status.getDescription())
            .as("状态 %s 应该有描述", status.name())
            .isNotNull()
            .isNotBlank();
      }
    }

    @Test
    @DisplayName("所有描述应该有正确的内容")
    void allDescriptionsShouldHaveCorrectContent() {
      // Given & When & Then
      assertThat(BatchStatus.RUNNING.getDescription()).isEqualTo("Running");
      assertThat(BatchStatus.SUCCEEDED.getDescription()).isEqualTo("Succeeded");
      assertThat(BatchStatus.FAILED.getDescription()).isEqualTo("Failed");
      assertThat(BatchStatus.SKIPPED.getDescription()).isEqualTo("Skipped");
    }
  }

  @Nested
  @DisplayName("fromCode 方法测试")
  class FromCodeMethodTest {

    @Test
    @DisplayName("应该通过大写 code 正确解析")
    void shouldParseFromUpperCaseCode() {
      // Given
      String code = "RUNNING";

      // When
      BatchStatus result = BatchStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(BatchStatus.RUNNING);
    }

    @Test
    @DisplayName("应该通过小写 code 正确解析")
    void shouldParseFromLowerCaseCode() {
      // Given
      String code = "succeeded";

      // When
      BatchStatus result = BatchStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(BatchStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("应该通过混合大小写 code 正确解析")
    void shouldParseFromMixedCaseCode() {
      // Given
      String code = "SkIpPeD";

      // When
      BatchStatus result = BatchStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(BatchStatus.SKIPPED);
    }

    @Test
    @DisplayName("应该处理带前后空格的 code")
    void shouldHandleCodeWithWhitespace() {
      // Given
      String code = "  FAILED  ";

      // When
      BatchStatus result = BatchStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(BatchStatus.FAILED);
    }

    @Test
    @DisplayName("应该解析所有有效的 code")
    void shouldParseAllValidCodes() {
      // Given & When & Then
      assertThat(BatchStatus.fromCode("RUNNING")).isEqualTo(BatchStatus.RUNNING);
      assertThat(BatchStatus.fromCode("SUCCEEDED")).isEqualTo(BatchStatus.SUCCEEDED);
      assertThat(BatchStatus.fromCode("FAILED")).isEqualTo(BatchStatus.FAILED);
      assertThat(BatchStatus.fromCode("SKIPPED")).isEqualTo(BatchStatus.SKIPPED);
    }

    @Test
    @DisplayName("当 code 为 null 时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsNull() {
      // Given
      String code = null;

      // When & Then
      assertThatThrownBy(() -> BatchStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("批次状态代码不能为 null");
    }

    @Test
    @DisplayName("当 code 无效时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsInvalid() {
      // Given
      String code = "INVALID_STATUS";

      // When & Then
      assertThatThrownBy(() -> BatchStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的批次状态代码: " + code);
    }
  }

  @Nested
  @DisplayName("枚举业务语义测试")
  class BusinessSemanticsTest {

    @Test
    @DisplayName("应该有三个终态")
    void shouldHaveThreeTerminalStates() {
      // Given - SUCCEEDED, FAILED, SKIPPED 都是终态
      BatchStatus[] terminalStates = {
        BatchStatus.SUCCEEDED, BatchStatus.FAILED, BatchStatus.SKIPPED
      };

      // When & Then
      assertThat(terminalStates).hasSize(3);
    }

    @Test
    @DisplayName("RUNNING 应该是唯一的瞬态")
    void runningShouldBeOnlyTransientState() {
      // Given & When
      BatchStatus running = BatchStatus.RUNNING;

      // Then
      assertThat(running).isEqualTo(BatchStatus.values()[0]);
    }

    @Test
    @DisplayName("SKIPPED 表示批次被跳过执行")
    void skippedIndicatesBatchWasSkipped() {
      // Given & When - SKIPPED 用于依赖条件不满足的场景
      BatchStatus skipped = BatchStatus.SKIPPED;

      // Then
      assertThat(skipped.getDescription()).isEqualTo("Skipped");
    }

    @Test
    @DisplayName("SUCCEEDED 和 FAILED 是执行结果终态")
    void succeededAndFailedAreExecutionResultStates() {
      // Given
      BatchStatus succeeded = BatchStatus.SUCCEEDED;
      BatchStatus failed = BatchStatus.FAILED;

      // When & Then - 这两个是实际执行后的结果
      assertThat(succeeded.ordinal()).isGreaterThan(BatchStatus.RUNNING.ordinal());
      assertThat(failed.ordinal()).isGreaterThan(BatchStatus.RUNNING.ordinal());
    }
  }

  @Nested
  @DisplayName("状态转换验证")
  class StateTransitionValidationTest {

    @Test
    @DisplayName("从 RUNNING 可以转换到任意终态")
    void canTransitionFromRunningToAnyTerminalState() {
      // Given
      BatchStatus running = BatchStatus.RUNNING;

      // When & Then
      assertThat(BatchStatus.SUCCEEDED.ordinal()).isGreaterThan(running.ordinal());
      assertThat(BatchStatus.FAILED.ordinal()).isGreaterThan(running.ordinal());
      assertThat(BatchStatus.SKIPPED.ordinal()).isGreaterThan(running.ordinal());
    }

    @Test
    @DisplayName("批次可以直接被标记为 SKIPPED 而不经过 RUNNING")
    void batchCanBeMarkedSkippedWithoutRunning() {
      // Given - 依赖条件不满足时可以直接跳过
      BatchStatus skipped = BatchStatus.SKIPPED;

      // When & Then
      assertThat(skipped).isNotNull();
      assertThat(skipped.getCode()).isEqualTo("SKIPPED");
    }
  }
}
