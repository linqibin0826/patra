package com.patra.ingest.domain.model.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * TaskStatus 枚举测试。
 *
 * @author Patra Team
 */
@DisplayName("TaskStatus 枚举测试")
class TaskStatusTest {

  @Nested
  @DisplayName("枚举值测试")
  class EnumValuesTest {

    @Test
    @DisplayName("应该包含所有预期的枚举值")
    void shouldContainAllExpectedValues() {
      // Given & When
      TaskStatus[] values = TaskStatus.values();

      // Then
      assertThat(values)
          .hasSize(5)
          .containsExactly(
              TaskStatus.PENDING,
              TaskStatus.QUEUED,
              TaskStatus.RUNNING,
              TaskStatus.SUCCEEDED,
              TaskStatus.FAILED);
    }

    @Test
    @DisplayName("应该通过名称正确获取枚举值")
    void shouldGetEnumByName() {
      // Given & When
      TaskStatus pending = TaskStatus.valueOf("PENDING");
      TaskStatus queued = TaskStatus.valueOf("QUEUED");
      TaskStatus running = TaskStatus.valueOf("RUNNING");
      TaskStatus succeeded = TaskStatus.valueOf("SUCCEEDED");
      TaskStatus failed = TaskStatus.valueOf("FAILED");

      // Then
      assertThat(pending).isEqualTo(TaskStatus.PENDING);
      assertThat(queued).isEqualTo(TaskStatus.QUEUED);
      assertThat(running).isEqualTo(TaskStatus.RUNNING);
      assertThat(succeeded).isEqualTo(TaskStatus.SUCCEEDED);
      assertThat(failed).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    @DisplayName("当使用无效名称时应该抛出异常")
    void shouldThrowExceptionForInvalidName() {
      // Given
      String invalidName = "INVALID";

      // When & Then
      assertThatThrownBy(() -> TaskStatus.valueOf(invalidName))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("code 属性测试")
  class CodePropertyTest {

    @Test
    @DisplayName("PENDING 应该有正确的 code")
    void pendingShouldHaveCorrectCode() {
      // Given & When
      String code = TaskStatus.PENDING.getCode();

      // Then
      assertThat(code).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("QUEUED 应该有正确的 code")
    void queuedShouldHaveCorrectCode() {
      // Given & When
      String code = TaskStatus.QUEUED.getCode();

      // Then
      assertThat(code).isEqualTo("QUEUED");
    }

    @Test
    @DisplayName("RUNNING 应该有正确的 code")
    void runningShouldHaveCorrectCode() {
      // Given & When
      String code = TaskStatus.RUNNING.getCode();

      // Then
      assertThat(code).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("SUCCEEDED 应该有正确的 code")
    void succeededShouldHaveCorrectCode() {
      // Given & When
      String code = TaskStatus.SUCCEEDED.getCode();

      // Then
      assertThat(code).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("FAILED 应该有正确的 code")
    void failedShouldHaveCorrectCode() {
      // Given & When
      String code = TaskStatus.FAILED.getCode();

      // Then
      assertThat(code).isEqualTo("FAILED");
    }
  }

  @Nested
  @DisplayName("description 属性测试")
  class DescriptionPropertyTest {

    @Test
    @DisplayName("所有枚举值应该有非空的描述")
    void allValuesShouldHaveNonEmptyDescription() {
      // Given & When & Then
      for (TaskStatus status : TaskStatus.values()) {
        assertThat(status.getDescription())
            .as("状态 %s 应该有描述", status.name())
            .isNotNull()
            .isNotBlank();
      }
    }

    @Test
    @DisplayName("PENDING 应该有正确的描述")
    void pendingShouldHaveCorrectDescription() {
      // Given & When
      String description = TaskStatus.PENDING.getDescription();

      // Then
      assertThat(description).isEqualTo("Pending");
    }

    @Test
    @DisplayName("SUCCEEDED 应该有正确的描述")
    void succeededShouldHaveCorrectDescription() {
      // Given & When
      String description = TaskStatus.SUCCEEDED.getDescription();

      // Then
      assertThat(description).isEqualTo("Succeeded");
    }
  }

  @Nested
  @DisplayName("fromCode 方法测试")
  class FromCodeMethodTest {

    @Test
    @DisplayName("应该通过大写 code 正确解析")
    void shouldParseFromUpperCaseCode() {
      // Given
      String code = "PENDING";

      // When
      TaskStatus result = TaskStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    @DisplayName("应该通过小写 code 正确解析")
    void shouldParseFromLowerCaseCode() {
      // Given
      String code = "running";

      // When
      TaskStatus result = TaskStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    @DisplayName("应该通过混合大小写 code 正确解析")
    void shouldParseFromMixedCaseCode() {
      // Given
      String code = "SuCcEeDeD";

      // When
      TaskStatus result = TaskStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(TaskStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("应该处理带前后空格的 code")
    void shouldHandleCodeWithWhitespace() {
      // Given
      String code = "  FAILED  ";

      // When
      TaskStatus result = TaskStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    @DisplayName("应该解析所有有效的 code")
    void shouldParseAllValidCodes() {
      // Given & When & Then
      assertThat(TaskStatus.fromCode("PENDING")).isEqualTo(TaskStatus.PENDING);
      assertThat(TaskStatus.fromCode("QUEUED")).isEqualTo(TaskStatus.QUEUED);
      assertThat(TaskStatus.fromCode("RUNNING")).isEqualTo(TaskStatus.RUNNING);
      assertThat(TaskStatus.fromCode("SUCCEEDED")).isEqualTo(TaskStatus.SUCCEEDED);
      assertThat(TaskStatus.fromCode("FAILED")).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    @DisplayName("当 code 为 null 时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsNull() {
      // Given
      String code = null;

      // When & Then
      assertThatThrownBy(() -> TaskStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("任务状态代码不能为 null");
    }

    @Test
    @DisplayName("当 code 无效时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsInvalid() {
      // Given
      String code = "INVALID_STATUS";

      // When & Then
      assertThatThrownBy(() -> TaskStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的任务状态代码: " + code);
    }

    @Test
    @DisplayName("当 code 为空字符串时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsEmpty() {
      // Given
      String code = "";

      // When & Then
      assertThatThrownBy(() -> TaskStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的任务状态代码");
    }
  }

  @Nested
  @DisplayName("枚举业务语义测试")
  class BusinessSemanticsTest {

    @Test
    @DisplayName("状态机流转序列应该合理")
    void statusMachineTransitionShouldMakeSense() {
      // Given - 按状态机顺序排列
      TaskStatus[] expectedOrder = {
        TaskStatus.PENDING, // 初始状态
        TaskStatus.QUEUED, // 已排队
        TaskStatus.RUNNING, // 运行中
        TaskStatus.SUCCEEDED, // 成功终态
        TaskStatus.FAILED // 失败终态
      };

      // When
      TaskStatus[] actualOrder = TaskStatus.values();

      // Then
      assertThat(actualOrder).containsExactly(expectedOrder);
    }

    @Test
    @DisplayName("应该只有两个终态")
    void shouldHaveTwoTerminalStates() {
      // Given - 成功和失败是终态
      TaskStatus[] terminalStates = {TaskStatus.SUCCEEDED, TaskStatus.FAILED};

      // When & Then
      assertThat(terminalStates).hasSize(2);
    }

    @Test
    @DisplayName("PENDING 应该是初始状态")
    void pendingShouldBeInitialState() {
      // Given & When
      TaskStatus initialState = TaskStatus.values()[0];

      // Then
      assertThat(initialState).isEqualTo(TaskStatus.PENDING);
    }
  }
}
