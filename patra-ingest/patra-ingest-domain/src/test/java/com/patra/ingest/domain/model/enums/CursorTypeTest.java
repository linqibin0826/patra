package com.patra.ingest.domain.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * CursorType 枚举测试。
 *
 * @author Patra Team
 */
@DisplayName("CursorType 枚举测试")
class CursorTypeTest {

  @Nested
  @DisplayName("枚举值测试")
  class EnumValuesTest {

    @Test
    @DisplayName("应该包含所有预期的枚举值")
    void shouldContainAllExpectedValues() {
      // Given & When
      CursorType[] values = CursorType.values();

      // Then
      assertThat(values)
          .hasSize(3)
          .containsExactly(CursorType.TIME, CursorType.ID, CursorType.TOKEN);
    }

    @Test
    @DisplayName("应该通过名称正确获取枚举值")
    void shouldGetEnumByName() {
      // Given & When
      CursorType time = CursorType.valueOf("TIME");
      CursorType id = CursorType.valueOf("ID");
      CursorType token = CursorType.valueOf("TOKEN");

      // Then
      assertThat(time).isEqualTo(CursorType.TIME);
      assertThat(id).isEqualTo(CursorType.ID);
      assertThat(token).isEqualTo(CursorType.TOKEN);
    }

    @Test
    @DisplayName("当使用无效名称时应该抛出异常")
    void shouldThrowExceptionForInvalidName() {
      // Given
      String invalidName = "INVALID";

      // When & Then
      assertThatThrownBy(() -> CursorType.valueOf(invalidName))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("code 属性测试")
  class CodePropertyTest {

    @Test
    @DisplayName("TIME 应该有正确的 code")
    void timeShouldHaveCorrectCode() {
      // Given & When
      String code = CursorType.TIME.getCode();

      // Then
      assertThat(code).isEqualTo("TIME");
    }

    @Test
    @DisplayName("ID 应该有正确的 code")
    void idShouldHaveCorrectCode() {
      // Given & When
      String code = CursorType.ID.getCode();

      // Then
      assertThat(code).isEqualTo("ID");
    }

    @Test
    @DisplayName("TOKEN 应该有正确的 code")
    void tokenShouldHaveCorrectCode() {
      // Given & When
      String code = CursorType.TOKEN.getCode();

      // Then
      assertThat(code).isEqualTo("TOKEN");
    }
  }

  @Nested
  @DisplayName("description 属性测试")
  class DescriptionPropertyTest {

    @Test
    @DisplayName("所有枚举值应该有非空的描述")
    void allValuesShouldHaveNonEmptyDescription() {
      // Given & When & Then
      for (CursorType type : CursorType.values()) {
        assertThat(type.getDescription())
            .as("类型 %s 应该有描述", type.name())
            .isNotNull()
            .isNotBlank();
      }
    }

    @Test
    @DisplayName("TIME 应该有正确的描述")
    void timeShouldHaveCorrectDescription() {
      // Given & When
      String description = CursorType.TIME.getDescription();

      // Then
      assertThat(description).isEqualTo("Time-based");
    }

    @Test
    @DisplayName("ID 应该有正确的描述")
    void idShouldHaveCorrectDescription() {
      // Given & When
      String description = CursorType.ID.getDescription();

      // Then
      assertThat(description).isEqualTo("Identifier-based");
    }

    @Test
    @DisplayName("TOKEN 应该有正确的描述")
    void tokenShouldHaveCorrectDescription() {
      // Given & When
      String description = CursorType.TOKEN.getDescription();

      // Then
      assertThat(description).isEqualTo("Token-based");
    }
  }

  @Nested
  @DisplayName("fromCode 方法测试")
  class FromCodeMethodTest {

    @Test
    @DisplayName("应该通过大写 code 正确解析")
    void shouldParseFromUpperCaseCode() {
      // Given
      String code = "TIME";

      // When
      CursorType result = CursorType.fromCode(code);

      // Then
      assertThat(result).isEqualTo(CursorType.TIME);
    }

    @Test
    @DisplayName("应该通过小写 code 正确解析")
    void shouldParseFromLowerCaseCode() {
      // Given
      String code = "id";

      // When
      CursorType result = CursorType.fromCode(code);

      // Then
      assertThat(result).isEqualTo(CursorType.ID);
    }

    @Test
    @DisplayName("应该通过混合大小写 code 正确解析")
    void shouldParseFromMixedCaseCode() {
      // Given
      String code = "ToKeN";

      // When
      CursorType result = CursorType.fromCode(code);

      // Then
      assertThat(result).isEqualTo(CursorType.TOKEN);
    }

    @Test
    @DisplayName("应该处理带前后空格的 code")
    void shouldHandleCodeWithWhitespace() {
      // Given
      String code = "  TIME  ";

      // When
      CursorType result = CursorType.fromCode(code);

      // Then
      assertThat(result).isEqualTo(CursorType.TIME);
    }

    @Test
    @DisplayName("应该解析所有有效的 code")
    void shouldParseAllValidCodes() {
      // Given & When & Then
      assertThat(CursorType.fromCode("TIME")).isEqualTo(CursorType.TIME);
      assertThat(CursorType.fromCode("ID")).isEqualTo(CursorType.ID);
      assertThat(CursorType.fromCode("TOKEN")).isEqualTo(CursorType.TOKEN);
    }

    @Test
    @DisplayName("当 code 为 null 时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsNull() {
      // Given
      String code = null;

      // When & Then
      assertThatThrownBy(() -> CursorType.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("游标类型代码不能为 null");
    }

    @Test
    @DisplayName("当 code 无效时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsInvalid() {
      // Given
      String code = "INVALID_TYPE";

      // When & Then
      assertThatThrownBy(() -> CursorType.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的游标类型代码: " + code);
    }

    @Test
    @DisplayName("当 code 为空字符串时应该抛出异常")
    void shouldThrowExceptionWhenCodeIsEmpty() {
      // Given
      String code = "";

      // When & Then
      assertThatThrownBy(() -> CursorType.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的游标类型代码");
    }
  }

  @Nested
  @DisplayName("枚举业务语义测试")
  class BusinessSemanticsTest {

    @Test
    @DisplayName("TIME 应该表示基于时间戳的游标")
    void timeShouldRepresentTimestampBasedCursor() {
      // Given & When
      CursorType time = CursorType.TIME;

      // Then
      assertThat(time.getCode()).isEqualTo("TIME");
      assertThat(time.getDescription()).contains("Time");
    }

    @Test
    @DisplayName("ID 应该表示基于数值 ID 的游标")
    void idShouldRepresentNumericIdBasedCursor() {
      // Given & When
      CursorType id = CursorType.ID;

      // Then
      assertThat(id.getCode()).isEqualTo("ID");
      assertThat(id.getDescription()).contains("Identifier");
    }

    @Test
    @DisplayName("TOKEN 应该表示基于不透明令牌的游标")
    void tokenShouldRepresentOpaqueTokenBasedCursor() {
      // Given & When
      CursorType token = CursorType.TOKEN;

      // Then
      assertThat(token.getCode()).isEqualTo("TOKEN");
      assertThat(token.getDescription()).contains("Token");
    }

    @Test
    @DisplayName("应该覆盖常见的游标类型")
    void shouldCoverCommonCursorTypes() {
      // Given - 时间、ID、令牌是最常见的游标类型
      CursorType[] commonTypes = {CursorType.TIME, CursorType.ID, CursorType.TOKEN};

      // When & Then
      assertThat(commonTypes).hasSize(3);
      assertThat(CursorType.values()).containsExactly(commonTypes);
    }
  }

  @Nested
  @DisplayName("使用场景测试")
  class UseCaseTest {

    @Test
    @DisplayName("TIME 类型适用于时间序列数据")
    void timeTypeIsApplicableForTimeSeriesData() {
      // Given
      CursorType time = CursorType.TIME;

      // When & Then - 时间戳游标用于按时间顺序获取数据
      assertThat(time).isEqualTo(CursorType.TIME);
    }

    @Test
    @DisplayName("ID 类型适用于自增 ID 场景")
    void idTypeIsApplicableForAutoIncrementIdScenario() {
      // Given
      CursorType id = CursorType.ID;

      // When & Then - 数值 ID 游标用于自增主键场景
      assertThat(id).isEqualTo(CursorType.ID);
    }

    @Test
    @DisplayName("TOKEN 类型适用于第三方 API 分页")
    void tokenTypeIsApplicableForThirdPartyApiPagination() {
      // Given
      CursorType token = CursorType.TOKEN;

      // When & Then - 不透明令牌用于第三方 API(如 PubMed)
      assertThat(token).isEqualTo(CursorType.TOKEN);
    }
  }

  @Nested
  @DisplayName("枚举顺序测试")
  class EnumOrderTest {

    @Test
    @DisplayName("枚举值应该按声明顺序排序")
    void enumValuesShouldBeOrderedByDeclaration() {
      // Given & When
      int timeOrdinal = CursorType.TIME.ordinal();
      int idOrdinal = CursorType.ID.ordinal();
      int tokenOrdinal = CursorType.TOKEN.ordinal();

      // Then
      assertThat(timeOrdinal).isEqualTo(0);
      assertThat(idOrdinal).isEqualTo(1);
      assertThat(tokenOrdinal).isEqualTo(2);
    }
  }
}
