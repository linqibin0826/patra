package com.patra.ingest.domain.model.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// PlanStatus 枚举测试。
///
/// @author Patra Team
@DisplayName("PlanStatus 枚举测试")
class PlanStatusTest {

  @Nested
  @DisplayName("枚举值测试")
  class EnumValuesTest {

    @Test
    @DisplayName("应该包含所有预期的枚举值")
    void shouldContainAllExpectedValues() {
      // Given & When
      PlanStatus[] values = PlanStatus.values();

      // Then
      assertThat(values)
          .hasSize(4)
          .containsExactly(
              PlanStatus.DRAFT, PlanStatus.SLICING, PlanStatus.READY, PlanStatus.ARCHIVED);
    }

    @Test
    @DisplayName("应该通过名称正确获取枚举值")
    void shouldGetEnumByName() {
      // Given & When
      PlanStatus draft = PlanStatus.valueOf("DRAFT");
      PlanStatus slicing = PlanStatus.valueOf("SLICING");
      PlanStatus ready = PlanStatus.valueOf("READY");
      PlanStatus archived = PlanStatus.valueOf("ARCHIVED");

      // Then
      assertThat(draft).isEqualTo(PlanStatus.DRAFT);
      assertThat(slicing).isEqualTo(PlanStatus.SLICING);
      assertThat(ready).isEqualTo(PlanStatus.READY);
      assertThat(archived).isEqualTo(PlanStatus.ARCHIVED);
    }

    @Test
    @DisplayName("当使用无效名称时应该抛出异常")
    void shouldThrowExceptionForInvalidName() {
      // Given
      String invalidName = "INVALID";

      // When & Then
      assertThatThrownBy(() -> PlanStatus.valueOf(invalidName))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("code 属性测试")
  class CodePropertyTest {

    @Test
    @DisplayName("DRAFT 应该有正确的 code")
    void draftShouldHaveCorrectCode() {
      // Given & When
      String code = PlanStatus.DRAFT.getCode();

      // Then
      assertThat(code).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("SLICING 应该有正确的 code")
    void slicingShouldHaveCorrectCode() {
      // Given & When
      String code = PlanStatus.SLICING.getCode();

      // Then
      assertThat(code).isEqualTo("SLICING");
    }

    @Test
    @DisplayName("READY 应该有正确的 code")
    void readyShouldHaveCorrectCode() {
      // Given & When
      String code = PlanStatus.READY.getCode();

      // Then
      assertThat(code).isEqualTo("READY");
    }

    @Test
    @DisplayName("ARCHIVED 应该有正确的 code")
    void archivedShouldHaveCorrectCode() {
      // Given & When
      String code = PlanStatus.ARCHIVED.getCode();

      // Then
      assertThat(code).isEqualTo("ARCHIVED");
    }
  }

  @Nested
  @DisplayName("description 属性测试")
  class DescriptionPropertyTest {

    @Test
    @DisplayName("所有枚举值应该有非空的描述")
    void allValuesShouldHaveNonEmptyDescription() {
      // Given & When & Then
      for (PlanStatus status : PlanStatus.values()) {
        assertThat(status.getDescription())
            .as("状态 %s 应该有描述", status.name())
            .isNotNull()
            .isNotBlank();
      }
    }

    @Test
    @DisplayName("DRAFT 应该有正确的描述")
    void draftShouldHaveCorrectDescription() {
      // Given & When
      String description = PlanStatus.DRAFT.getDescription();

      // Then
      assertThat(description).isEqualTo("Draft");
    }

    @Test
    @DisplayName("ARCHIVED 应该有正确的描述")
    void archivedShouldHaveCorrectDescription() {
      // Given & When
      String description = PlanStatus.ARCHIVED.getDescription();

      // Then
      assertThat(description).isEqualTo("Archived");
    }
  }

  @Nested
  @DisplayName("fromCode 方法测试")
  class FromCodeMethodTest {

    @Test
    @DisplayName("应该通过大写 code 正确解析")
    void shouldParseFromUpperCaseCode() {
      // Given
      String code = "DRAFT";

      // When
      PlanStatus result = PlanStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    @DisplayName("应该通过小写 code 正确解析")
    void shouldParseFromLowerCaseCode() {
      // Given
      String code = "slicing";

      // When
      PlanStatus result = PlanStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(PlanStatus.SLICING);
    }

    @Test
    @DisplayName("应该通过混合大小写 code 正确解析")
    void shouldParseFromMixedCaseCode() {
      // Given
      String code = "ReAdY";

      // When
      PlanStatus result = PlanStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(PlanStatus.READY);
    }

    @Test
    @DisplayName("应该处理带前后空格的 code")
    void shouldHandleCodeWithWhitespace() {
      // Given
      String code = "  ARCHIVED  ";

      // When
      PlanStatus result = PlanStatus.fromCode(code);

      // Then
      assertThat(result).isEqualTo(PlanStatus.ARCHIVED);
    }

    @Test
    @DisplayName("应该解析所有有效的 code")
    void shouldParseAllValidCodes() {
      // Given & When & Then
      assertThat(PlanStatus.fromCode("DRAFT")).isEqualTo(PlanStatus.DRAFT);
      assertThat(PlanStatus.fromCode("SLICING")).isEqualTo(PlanStatus.SLICING);
      assertThat(PlanStatus.fromCode("READY")).isEqualTo(PlanStatus.READY);
      assertThat(PlanStatus.fromCode("ARCHIVED")).isEqualTo(PlanStatus.ARCHIVED);
    }

    @Test
    @DisplayName("当 code 为 null 时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsNull() {
      // Given
      String code = null;

      // When & Then
      assertThatThrownBy(() -> PlanStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("计划状态代码不能为 null");
    }

    @Test
    @DisplayName("当 code 无效时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsInvalid() {
      // Given
      String code = "INVALID_STATUS";

      // When & Then
      assertThatThrownBy(() -> PlanStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的计划状态代码: " + code);
    }

    @Test
    @DisplayName("当 code 为空字符串时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsEmpty() {
      // Given
      String code = "";

      // When & Then
      assertThatThrownBy(() -> PlanStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的计划状态代码");
    }
  }

  @Nested
  @DisplayName("JsonCreator 注解测试")
  class JsonCreatorAnnotationTest {

    @Test
    @DisplayName("fromCode 方法应该支持 JSON 反序列化")
    void fromCodeMethodShouldSupportJsonDeserialization() {
      // Given - JSON 反序列化会调用 @JsonCreator 标注的方法
      String jsonCode = "READY";

      // When
      PlanStatus result = PlanStatus.fromCode(jsonCode);

      // Then
      assertThat(result).isEqualTo(PlanStatus.READY);
    }
  }

  @Nested
  @DisplayName("枚举业务语义测试")
  class BusinessSemanticsTest {

    @Test
    @DisplayName("状态机流转序列应该合理")
    void statusMachineTransitionShouldMakeSense() {
      // Given - 按状态机顺序排列
      PlanStatus[] expectedOrder = {
        PlanStatus.DRAFT, // 初始状态
        PlanStatus.SLICING, // 切片中
        PlanStatus.READY, // 就绪
        PlanStatus.ARCHIVED // 归档终态
      };

      // When
      PlanStatus[] actualOrder = PlanStatus.values();

      // Then
      assertThat(actualOrder).containsExactly(expectedOrder);
    }

    @Test
    @DisplayName("ARCHIVED 应该是唯一的终态")
    void archivedShouldBeOnlyTerminalState() {
      // Given & When
      PlanStatus terminalState = PlanStatus.ARCHIVED;

      // Then
      assertThat(terminalState).isEqualTo(PlanStatus.values()[PlanStatus.values().length - 1]);
    }

    @Test
    @DisplayName("DRAFT 应该是初始状态")
    void draftShouldBeInitialState() {
      // Given & When
      PlanStatus initialState = PlanStatus.values()[0];

      // Then
      assertThat(initialState).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    @DisplayName("SLICING 状态应该不可重复转换")
    void slicingShouldBeNonRepeatableTransition() {
      // Given - 根据文档,SLICING 状态是单向且不可重复的
      // When
      PlanStatus slicing = PlanStatus.SLICING;

      // Then
      assertThat(slicing.getCode()).isEqualTo("SLICING");
      assertThat(slicing.getDescription()).contains("Slicing");
    }
  }
}
