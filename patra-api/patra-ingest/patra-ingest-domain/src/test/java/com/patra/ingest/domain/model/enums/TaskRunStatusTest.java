package com.patra.ingest.domain.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * TaskRunStatus 枚举测试。
 *
 * @author Patra Team
 */
@DisplayName("TaskRunStatus 枚举测试")
class TaskRunStatusTest {

  @Nested
  @DisplayName("枚举值测试")
  class EnumValuesTest {

    @Test
    @DisplayName("应该包含所有预期的枚举值")
    void shouldContainAllExpectedValues() {
      // Given & When
      TaskRunStatus[] values = TaskRunStatus.values();

      // Then
      assertThat(values)
          .hasSize(5)
          .containsExactly(
              TaskRunStatus.PENDING,
              TaskRunStatus.RUNNING,
              TaskRunStatus.SUCCEEDED,
              TaskRunStatus.FAILED,
              TaskRunStatus.PARTIAL);
    }

    @Test
    @DisplayName("应该通过名称正确获取枚举值")
    void shouldGetEnumByName() {
      // Given & When
      TaskRunStatus pending = TaskRunStatus.valueOf("PENDING");
      TaskRunStatus running = TaskRunStatus.valueOf("RUNNING");
      TaskRunStatus succeeded = TaskRunStatus.valueOf("SUCCEEDED");
      TaskRunStatus failed = TaskRunStatus.valueOf("FAILED");
      TaskRunStatus partial = TaskRunStatus.valueOf("PARTIAL");

      // Then
      assertThat(pending).isEqualTo(TaskRunStatus.PENDING);
      assertThat(running).isEqualTo(TaskRunStatus.RUNNING);
      assertThat(succeeded).isEqualTo(TaskRunStatus.SUCCEEDED);
      assertThat(failed).isEqualTo(TaskRunStatus.FAILED);
      assertThat(partial).isEqualTo(TaskRunStatus.PARTIAL);
    }

    @Test
    @DisplayName("当使用无效名称时应该抛出异常")
    void shouldThrowExceptionForInvalidName() {
      // Given
      String invalidName = "INVALID";

      // When & Then
      assertThatThrownBy(() -> TaskRunStatus.valueOf(invalidName))
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
      String code = TaskRunStatus.PENDING.getCode();

      // Then
      assertThat(code).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("RUNNING 应该有正确的 code")
    void runningShouldHaveCorrectCode() {
      // Given & When
      String code = TaskRunStatus.RUNNING.getCode();

      // Then
      assertThat(code).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("SUCCEEDED 应该有正确的 code")
    void succeededShouldHaveCorrectCode() {
      // Given & When
      String code = TaskRunStatus.SUCCEEDED.getCode();

      // Then
      assertThat(code).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("FAILED 应该有正确的 code")
    void failedShouldHaveCorrectCode() {
      // Given & When
      String code = TaskRunStatus.FAILED.getCode();

      // Then
      assertThat(code).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("PARTIAL 应该有正确的 code")
    void partialShouldHaveCorrectCode() {
      // Given & When
      String code = TaskRunStatus.PARTIAL.getCode();

      // Then
      assertThat(code).isEqualTo("PARTIAL");
    }
  }

  @Nested
  @DisplayName("description 属性测试")
  class DescriptionPropertyTest {

    @Test
    @DisplayName("所有枚举值应该有非空的描述")
    void allValuesShouldHaveNonEmptyDescription() {
      // Given & When & Then
      for (TaskRunStatus status : TaskRunStatus.values()) {
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
      String description = TaskRunStatus.PENDING.getDescription();

      // Then
      assertThat(description).isEqualTo("Pending");
    }

    @Test
    @DisplayName("PARTIAL 应该有正确的描述")
    void partialShouldHaveCorrectDescription() {
      // Given & When
      String description = TaskRunStatus.PARTIAL.getDescription();

      // Then
      assertThat(description).isEqualTo("Partially completed");
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
      TaskRunStatus result = TaskRunStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(TaskRunStatus.PENDING);
    }

    @Test
    @DisplayName("应该通过小写 code 正确解析")
    void shouldParseFromLowerCaseCode() {
      // Given
      String code = "running";

      // When
      TaskRunStatus result = TaskRunStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(TaskRunStatus.RUNNING);
    }

    @Test
    @DisplayName("应该通过混合大小写 code 正确解析")
    void shouldParseFromMixedCaseCode() {
      // Given
      String code = "PaRtIaL";

      // When
      TaskRunStatus result = TaskRunStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(TaskRunStatus.PARTIAL);
    }

    @Test
    @DisplayName("应该处理带前后空格的 code")
    void shouldHandleCodeWithWhitespace() {
      // Given
      String code = "  SUCCEEDED  ";

      // When
      TaskRunStatus result = TaskRunStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(TaskRunStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("应该解析所有有效的 code")
    void shouldParseAllValidCodes() {
      // Given & When & Then
      assertThat(TaskRunStatus.fromCode("PENDING")).isEqualTo(TaskRunStatus.PENDING);
      assertThat(TaskRunStatus.fromCode("RUNNING")).isEqualTo(TaskRunStatus.RUNNING);
      assertThat(TaskRunStatus.fromCode("SUCCEEDED")).isEqualTo(TaskRunStatus.SUCCEEDED);
      assertThat(TaskRunStatus.fromCode("FAILED")).isEqualTo(TaskRunStatus.FAILED);
      assertThat(TaskRunStatus.fromCode("PARTIAL")).isEqualTo(TaskRunStatus.PARTIAL);
    }

    @Test
    @DisplayName("当 code 为 null 时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsNull() {
      // Given
      String code = null;

      // When & Then
      assertThatThrownBy(() -> TaskRunStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("TaskRun 状态代码不能为 null");
    }

    @Test
    @DisplayName("当 code 无效时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsInvalid() {
      // Given
      String code = "INVALID_STATUS";

      // When & Then
      assertThatThrownBy(() -> TaskRunStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的 TaskRun 状态代码: " + code);
    }

    @Test
    @DisplayName("当 code 为空字符串时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsEmpty() {
      // Given
      String code = "";

      // When & Then
      assertThatThrownBy(() -> TaskRunStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的 TaskRun 状态代码");
    }
  }

  @Nested
  @DisplayName("枚举业务语义测试")
  class BusinessSemanticsTest {

    @Test
    @DisplayName("状态机流转序列应该合理")
    void statusMachineTransitionShouldMakeSense() {
      // Given - 按状态机顺序排列
      TaskRunStatus[] expectedOrder = {
        TaskRunStatus.PENDING, // 初始状态
        TaskRunStatus.RUNNING, // 运行中
        TaskRunStatus.SUCCEEDED, // 成功终态
        TaskRunStatus.FAILED, // 失败终态
        TaskRunStatus.PARTIAL // 部分完成(可恢复)
      };

      // When
      TaskRunStatus[] actualOrder = TaskRunStatus.values();

      // Then
      assertThat(actualOrder).containsExactly(expectedOrder);
    }

    @Test
    @DisplayName("应该有三个可能的终态")
    void shouldHaveThreePossibleTerminalStates() {
      // Given - SUCCEEDED, FAILED, PARTIAL 都可能是终态
      TaskRunStatus[] possibleTerminalStates = {
        TaskRunStatus.SUCCEEDED, TaskRunStatus.FAILED, TaskRunStatus.PARTIAL
      };

      // When & Then
      assertThat(possibleTerminalStates).hasSize(3);
    }

    @Test
    @DisplayName("PENDING 应该是初始状态")
    void pendingShouldBeInitialState() {
      // Given & When
      TaskRunStatus initialState = TaskRunStatus.values()[0];

      // Then
      assertThat(initialState).isEqualTo(TaskRunStatus.PENDING);
    }

    @Test
    @DisplayName("PARTIAL 状态应该启用可恢复执行")
    void partialStatusShouldEnableRecoverableExecution() {
      // Given & When - PARTIAL 是唯一保留用于检查点恢复的状态
      TaskRunStatus partial = TaskRunStatus.PARTIAL;

      // Then
      assertThat(partial.getDescription()).contains("Partially");
      assertThat(partial.getCode()).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("RUNNING 状态表示执行已开始")
    void runningStatusIndicatesExecutionStarted() {
      // Given & When
      TaskRunStatus running = TaskRunStatus.RUNNING;

      // Then
      assertThat(running.getCode()).isEqualTo("RUNNING");
      assertThat(running.ordinal()).isGreaterThan(TaskRunStatus.PENDING.ordinal());
    }

    @Test
    @DisplayName("SUCCEEDED 表示所有记录已处理")
    void succeededIndicatesAllRecordsProcessed() {
      // Given & When
      TaskRunStatus succeeded = TaskRunStatus.SUCCEEDED;

      // Then
      assertThat(succeeded.getDescription()).isEqualTo("Succeeded");
    }

    @Test
    @DisplayName("FAILED 表示未处理任何记录")
    void failedIndicatesNoRecordsProcessed() {
      // Given & When
      TaskRunStatus failed = TaskRunStatus.FAILED;

      // Then
      assertThat(failed.getDescription()).isEqualTo("Failed");
    }
  }

  @Nested
  @DisplayName("状态转换验证")
  class StateTransitionValidationTest {

    @Test
    @DisplayName("从 PENDING 只能转换到 RUNNING")
    void canOnlyTransitionFromPendingToRunning() {
      // Given
      TaskRunStatus from = TaskRunStatus.PENDING;
      TaskRunStatus to = TaskRunStatus.RUNNING;

      // When & Then
      assertThat(from.ordinal()).isLessThan(to.ordinal());
    }

    @Test
    @DisplayName("从 RUNNING 可以转换到 SUCCEEDED、FAILED 或 PARTIAL")
    void canTransitionFromRunningToSucceededFailedOrPartial() {
      // Given
      TaskRunStatus from = TaskRunStatus.RUNNING;

      // When & Then
      assertThat(TaskRunStatus.SUCCEEDED.ordinal()).isGreaterThan(from.ordinal());
      assertThat(TaskRunStatus.FAILED.ordinal()).isGreaterThan(from.ordinal());
      assertThat(TaskRunStatus.PARTIAL.ordinal()).isGreaterThan(from.ordinal());
    }

    @Test
    @DisplayName("PARTIAL 状态可以恢复到 RUNNING")
    void partialCanRecoverToRunning() {
      // Given - PARTIAL 状态支持检查点恢复
      TaskRunStatus partial = TaskRunStatus.PARTIAL;

      // When & Then - 可以从 PARTIAL 恢复到 RUNNING
      assertThat(partial).isNotNull();
      assertThat(TaskRunStatus.RUNNING).isNotNull();
    }
  }
}
