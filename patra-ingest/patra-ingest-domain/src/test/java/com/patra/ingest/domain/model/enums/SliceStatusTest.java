package com.patra.ingest.domain.model.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// SliceStatus 枚举测试。
///
/// @author linqibin
@DisplayName("SliceStatus 枚举测试")
class SliceStatusTest {

  @Nested
  @DisplayName("枚举值测试")
  class EnumValuesTest {

    @Test
    @DisplayName("应该包含所有预期的枚举值")
    void shouldContainAllExpectedValues() {
      // Given & When
      SliceStatus[] values = SliceStatus.values();

      // Then
      assertThat(values)
          .hasSize(3)
          .containsExactly(SliceStatus.PENDING, SliceStatus.ASSIGNED, SliceStatus.FINISHED);
    }

    @Test
    @DisplayName("应该通过名称正确获取枚举值")
    void shouldGetEnumByName() {
      // Given & When
      SliceStatus pending = SliceStatus.valueOf("PENDING");
      SliceStatus assigned = SliceStatus.valueOf("ASSIGNED");
      SliceStatus finished = SliceStatus.valueOf("FINISHED");

      // Then
      assertThat(pending).isEqualTo(SliceStatus.PENDING);
      assertThat(assigned).isEqualTo(SliceStatus.ASSIGNED);
      assertThat(finished).isEqualTo(SliceStatus.FINISHED);
    }

    @Test
    @DisplayName("当使用无效名称时应该抛出异常")
    void shouldThrowExceptionForInvalidName() {
      // Given
      String invalidName = "INVALID";

      // When & Then
      assertThatThrownBy(() -> SliceStatus.valueOf(invalidName))
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
      String code = SliceStatus.PENDING.getCode();

      // Then
      assertThat(code).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("ASSIGNED 应该有正确的 code")
    void assignedShouldHaveCorrectCode() {
      // Given & When
      String code = SliceStatus.ASSIGNED.getCode();

      // Then
      assertThat(code).isEqualTo("ASSIGNED");
    }

    @Test
    @DisplayName("FINISHED 应该有正确的 code")
    void finishedShouldHaveCorrectCode() {
      // Given & When
      String code = SliceStatus.FINISHED.getCode();

      // Then
      assertThat(code).isEqualTo("FINISHED");
    }
  }

  @Nested
  @DisplayName("description 属性测试")
  class DescriptionPropertyTest {

    @Test
    @DisplayName("所有枚举值应该有非空的描述")
    void allValuesShouldHaveNonEmptyDescription() {
      // Given & When & Then
      for (SliceStatus status : SliceStatus.values()) {
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
      String description = SliceStatus.PENDING.getDescription();

      // Then
      assertThat(description).isEqualTo("Pending");
    }

    @Test
    @DisplayName("ASSIGNED 应该有正确的描述")
    void assignedShouldHaveCorrectDescription() {
      // Given & When
      String description = SliceStatus.ASSIGNED.getDescription();

      // Then
      assertThat(description).isEqualTo("Assigned");
    }

    @Test
    @DisplayName("FINISHED 应该有正确的描述")
    void finishedShouldHaveCorrectDescription() {
      // Given & When
      String description = SliceStatus.FINISHED.getDescription();

      // Then
      assertThat(description).isEqualTo("Finished");
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
      SliceStatus result = SliceStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(SliceStatus.PENDING);
    }

    @Test
    @DisplayName("应该通过小写 code 正确解析")
    void shouldParseFromLowerCaseCode() {
      // Given
      String code = "assigned";

      // When
      SliceStatus result = SliceStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(SliceStatus.ASSIGNED);
    }

    @Test
    @DisplayName("应该通过混合大小写 code 正确解析")
    void shouldParseFromMixedCaseCode() {
      // Given
      String code = "FiNiShEd";

      // When
      SliceStatus result = SliceStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(SliceStatus.FINISHED);
    }

    @Test
    @DisplayName("应该处理带前后空格的 code")
    void shouldHandleCodeWithWhitespace() {
      // Given
      String code = "  ASSIGNED  ";

      // When
      SliceStatus result = SliceStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(SliceStatus.ASSIGNED);
    }

    @Test
    @DisplayName("应该解析所有有效的 code")
    void shouldParseAllValidCodes() {
      // Given & When & Then
      assertThat(SliceStatus.fromCode("PENDING")).isEqualTo(SliceStatus.PENDING);
      assertThat(SliceStatus.fromCode("ASSIGNED")).isEqualTo(SliceStatus.ASSIGNED);
      assertThat(SliceStatus.fromCode("FINISHED")).isEqualTo(SliceStatus.FINISHED);
    }

    @Test
    @DisplayName("当 code 为 null 时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsNull() {
      // Given
      String code = null;

      // When & Then
      assertThatThrownBy(() -> SliceStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("切片状态代码不能为 null");
    }

    @Test
    @DisplayName("当 code 无效时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsInvalid() {
      // Given
      String code = "INVALID_STATUS";

      // When & Then
      assertThatThrownBy(() -> SliceStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的切片状态代码: " + code);
    }

    @Test
    @DisplayName("当 code 为空字符串时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsEmpty() {
      // Given
      String code = "";

      // When & Then
      assertThatThrownBy(() -> SliceStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的切片状态代码");
    }
  }

  @Nested
  @DisplayName("枚举业务语义测试")
  class BusinessSemanticsTest {

    @Test
    @DisplayName("状态机流转序列应该合理")
    void statusMachineTransitionShouldMakeSense() {
      // Given - 按状态机顺序排列
      SliceStatus[] expectedOrder = {
        SliceStatus.PENDING, // 等待 Task 生成
        SliceStatus.ASSIGNED, // Task 已创建(1:1 映射)
        SliceStatus.FINISHED // Task 达到终态
      };

      // When
      SliceStatus[] actualOrder = SliceStatus.values();

      // Then
      assertThat(actualOrder).containsExactly(expectedOrder);
    }

    @Test
    @DisplayName("FINISHED 应该是唯一的终态")
    void finishedShouldBeOnlyTerminalState() {
      // Given & When
      SliceStatus terminalState = SliceStatus.FINISHED;

      // Then
      assertThat(terminalState).isEqualTo(SliceStatus.values()[SliceStatus.values().length - 1]);
    }

    @Test
    @DisplayName("PENDING 应该是初始状态")
    void pendingShouldBeInitialState() {
      // Given & When
      SliceStatus initialState = SliceStatus.values()[0];

      // Then
      assertThat(initialState).isEqualTo(SliceStatus.PENDING);
    }

    @Test
    @DisplayName("ASSIGNED 表示 1:1 Slice-Task 映射已建立")
    void assignedIndicatesOneToOneSliceTaskMapping() {
      // Given & When - ASSIGNED 状态表示对应的 Task 已创建
      SliceStatus assigned = SliceStatus.ASSIGNED;

      // Then
      assertThat(assigned.getCode()).isEqualTo("ASSIGNED");
      assertThat(assigned.ordinal()).isGreaterThan(SliceStatus.PENDING.ordinal());
      assertThat(assigned.ordinal()).isLessThan(SliceStatus.FINISHED.ordinal());
    }

    @Test
    @DisplayName("FINISHED 不区分成功或失败")
    void finishedDoesNotDistinguishSuccessOrFailure() {
      // Given & When - FINISHED 仅表示终态,不区分成功/失败
      SliceStatus finished = SliceStatus.FINISHED;

      // Then - 需要查询关联的 Task 获取执行结果
      assertThat(finished.getDescription()).isEqualTo("Finished");
    }
  }

  @Nested
  @DisplayName("状态转换验证")
  class StateTransitionValidationTest {

    @Test
    @DisplayName("从 PENDING 只能转换到 ASSIGNED")
    void canOnlyTransitionFromPendingToAssigned() {
      // Given
      SliceStatus from = SliceStatus.PENDING;
      SliceStatus to = SliceStatus.ASSIGNED;

      // When & Then
      assertThat(from.ordinal()).isLessThan(to.ordinal());
      assertThat(SliceStatus.values()).hasSize(3); // 严格的 3 状态流转
    }

    @Test
    @DisplayName("从 ASSIGNED 只能转换到 FINISHED")
    void canOnlyTransitionFromAssignedToFinished() {
      // Given
      SliceStatus from = SliceStatus.ASSIGNED;
      SliceStatus to = SliceStatus.FINISHED;

      // When & Then
      assertThat(from.ordinal()).isLessThan(to.ordinal());
    }

    @Test
    @DisplayName("状态转换应该是单向的")
    void stateTransitionShouldBeUnidirectional() {
      // Given - 状态只能向前流转,不能回退
      SliceStatus[] states = SliceStatus.values();

      // When & Then - 验证 ordinal 递增
      for (int i = 1; i < states.length; i++) {
        assertThat(states[i].ordinal()).isGreaterThan(states[i - 1].ordinal());
      }
    }
  }

  @Nested
  @DisplayName("1:1 关系验证")
  class OneToOneRelationshipTest {

    @Test
    @DisplayName("每个 Slice 应该只对应一个 Task")
    void eachSliceShouldCorrespondToOnlyOneTask() {
      // Given - 1:1 Slice-Task 关系是业务约束
      SliceStatus assigned = SliceStatus.ASSIGNED;

      // When & Then - ASSIGNED 状态强制 1:1 映射
      assertThat(assigned.getDescription()).isEqualTo("Assigned");
    }

    @Test
    @DisplayName("Slice 不包含执行结果信息")
    void sliceDoesNotContainExecutionResult() {
      // Given - Slice 不区分成功/失败
      SliceStatus finished = SliceStatus.FINISHED;

      // When & Then - 只有 FINISHED 终态,没有 SUCCEEDED/FAILED
      assertThat(SliceStatus.values()).hasSize(3);
      assertThat(finished).isEqualTo(SliceStatus.FINISHED);
    }
  }
}
