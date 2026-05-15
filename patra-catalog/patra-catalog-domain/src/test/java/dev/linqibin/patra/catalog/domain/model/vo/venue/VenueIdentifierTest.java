package dev.linqibin.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.catalog.domain.model.enums.VenueIdentifierType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueIdentifier 值对象单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 覆盖构造器验证、ISSN 格式验证、工厂方法和 equals/hashCode
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueIdentifier 单元测试")
@Timeout(2)
class VenueIdentifierTest {

  // ========== 测试数据 ==========

  private static final String VALID_ISSN = "1234-5678";
  private static final String VALID_ISSN_WITH_X = "1234-567X";
  private static final String VALID_ISSN_LOWERCASE_X = "1234-567x";
  private static final String INVALID_ISSN_FORMAT = "12345678";
  private static final String INVALID_ISSN_LETTERS = "ABCD-EFGH";
  private static final String OPENALEX_ID = "S1234567890";
  private static final String NLM_ID = "0376374";

  @Nested
  @DisplayName("构造器验证测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该正确创建标识符")
    void shouldCreateIdentifier() {
      // When
      VenueIdentifier identifier = new VenueIdentifier(VenueIdentifierType.OPENALEX, OPENALEX_ID);

      // Then
      assertThat(identifier.type()).isEqualTo(VenueIdentifierType.OPENALEX);
      assertThat(identifier.value()).isEqualTo(OPENALEX_ID);
    }

    @Test
    @DisplayName("类型为 null 时应该抛出异常")
    void shouldThrowWhenTypeIsNull() {
      assertThatThrownBy(() -> new VenueIdentifier(null, "value"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("标识符类型不能为空");
    }

    @Test
    @DisplayName("值为空时应该抛出异常")
    void shouldThrowWhenValueIsBlank() {
      assertThatThrownBy(() -> new VenueIdentifier(VenueIdentifierType.OPENALEX, ""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("标识符值不能为空");

      assertThatThrownBy(() -> new VenueIdentifier(VenueIdentifierType.OPENALEX, "  "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("标识符值不能为空");
    }
  }

  @Nested
  @DisplayName("ISSN 格式验证测试")
  class IssnValidationTests {

    @Test
    @DisplayName("有效 ISSN 格式应该通过验证")
    void shouldAcceptValidIssnFormat() {
      // When
      VenueIdentifier identifier = new VenueIdentifier(VenueIdentifierType.ISSN, VALID_ISSN);

      // Then
      assertThat(identifier.value()).isEqualTo(VALID_ISSN);
    }

    @Test
    @DisplayName("ISSN 带 X 校验位应该通过验证")
    void shouldAcceptIssnWithCheckDigitX() {
      // When
      VenueIdentifier identifier = new VenueIdentifier(VenueIdentifierType.ISSN, VALID_ISSN_WITH_X);

      // Then
      assertThat(identifier.value()).isEqualTo(VALID_ISSN_WITH_X);
    }

    @Test
    @DisplayName("小写 x 应该被标准化为大写 X")
    void shouldNormalizeLowercaseXToUppercase() {
      // When
      VenueIdentifier identifier =
          new VenueIdentifier(VenueIdentifierType.ISSN, VALID_ISSN_LOWERCASE_X);

      // Then
      assertThat(identifier.value()).isEqualTo("1234-567X");
    }

    @Test
    @DisplayName("无效 ISSN 格式应该抛出异常（缺少连字符）")
    void shouldRejectInvalidIssnWithoutHyphen() {
      assertThatThrownBy(() -> new VenueIdentifier(VenueIdentifierType.ISSN, INVALID_ISSN_FORMAT))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ISSN 格式无效");
    }

    @Test
    @DisplayName("无效 ISSN 格式应该抛出异常（含字母）")
    void shouldRejectInvalidIssnWithLetters() {
      assertThatThrownBy(() -> new VenueIdentifier(VenueIdentifierType.ISSN, INVALID_ISSN_LETTERS))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ISSN 格式无效");
    }

    @Test
    @DisplayName("ISSN_L 类型也应该验证 ISSN 格式")
    void shouldValidateIssnFormatForIssnLType() {
      // When - 有效格式
      VenueIdentifier identifier = new VenueIdentifier(VenueIdentifierType.ISSN_L, VALID_ISSN);
      assertThat(identifier.value()).isEqualTo(VALID_ISSN);

      // Then - 无效格式
      assertThatThrownBy(() -> new VenueIdentifier(VenueIdentifierType.ISSN_L, INVALID_ISSN_FORMAT))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ISSN 格式无效");
    }

    @Test
    @DisplayName("非 ISSN 类型不应验证 ISSN 格式")
    void shouldNotValidateIssnFormatForNonIssnTypes() {
      // 应该允许任意格式
      VenueIdentifier identifier =
          new VenueIdentifier(VenueIdentifierType.OPENALEX, INVALID_ISSN_FORMAT);
      assertThat(identifier.value()).isEqualTo(INVALID_ISSN_FORMAT);
    }
  }

  @Nested
  @DisplayName("工厂方法测试")
  class FactoryMethodTests {

    @Test
    @DisplayName("forOpenAlex() 应该创建 OpenAlex 标识符")
    void forOpenAlexShouldCreateIdentifier() {
      // When
      VenueIdentifier identifier = VenueIdentifier.forOpenAlex(OPENALEX_ID);

      // Then
      assertThat(identifier.type()).isEqualTo(VenueIdentifierType.OPENALEX);
      assertThat(identifier.value()).isEqualTo(OPENALEX_ID);
    }

    @Test
    @DisplayName("forIssn() 应该创建 ISSN 标识符")
    void forIssnShouldCreateIdentifier() {
      // When
      VenueIdentifier identifier = VenueIdentifier.forIssn(VALID_ISSN);

      // Then
      assertThat(identifier.type()).isEqualTo(VenueIdentifierType.ISSN);
      assertThat(identifier.value()).isEqualTo(VALID_ISSN);
    }

    @Test
    @DisplayName("forIssnL() 应该创建 Linking ISSN 标识符")
    void forIssnLShouldCreateIdentifier() {
      // When
      VenueIdentifier identifier = VenueIdentifier.forIssnL(VALID_ISSN);

      // Then
      assertThat(identifier.type()).isEqualTo(VenueIdentifierType.ISSN_L);
      assertThat(identifier.value()).isEqualTo(VALID_ISSN);
    }

    @Test
    @DisplayName("forNlm() 应该创建 NLM 标识符")
    void forNlmShouldCreateIdentifier() {
      // When
      VenueIdentifier identifier = VenueIdentifier.forNlm(NLM_ID);

      // Then
      assertThat(identifier.type()).isEqualTo(VenueIdentifierType.NLM);
      assertThat(identifier.value()).isEqualTo(NLM_ID);
    }

    @Test
    @DisplayName("forCoden() 应该创建 CODEN 标识符")
    void forCodenShouldCreateIdentifier() {
      // When
      VenueIdentifier identifier = VenueIdentifier.forCoden("NATUAS");

      // Then
      assertThat(identifier.type()).isEqualTo(VenueIdentifierType.CODEN);
      assertThat(identifier.value()).isEqualTo("NATUAS");
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodTests {

    @Test
    @DisplayName("isOpenAlexId() 应该返回正确结果")
    void isOpenAlexIdShouldReturnCorrectly() {
      VenueIdentifier openalex = VenueIdentifier.forOpenAlex(OPENALEX_ID);
      VenueIdentifier issn = VenueIdentifier.forIssn(VALID_ISSN);

      assertThat(openalex.isOpenAlexId()).isTrue();
      assertThat(issn.isOpenAlexId()).isFalse();
    }

    @Test
    @DisplayName("isIssnId() 应该返回正确结果")
    void isIssnIdShouldReturnCorrectly() {
      VenueIdentifier issn = VenueIdentifier.forIssn(VALID_ISSN);
      VenueIdentifier issnL = VenueIdentifier.forIssnL(VALID_ISSN);
      VenueIdentifier openalex = VenueIdentifier.forOpenAlex(OPENALEX_ID);

      assertThat(issn.isIssnId()).isTrue();
      assertThat(issnL.isIssnId()).isTrue();
      assertThat(openalex.isIssnId()).isFalse();
    }

    @Test
    @DisplayName("isStandardPublishingId() 应该返回正确结果")
    void isStandardPublishingIdShouldReturnCorrectly() {
      VenueIdentifier issn = VenueIdentifier.forIssn(VALID_ISSN);
      VenueIdentifier isbn = new VenueIdentifier(VenueIdentifierType.ISBN, "978-3-16-148410-0");
      VenueIdentifier openalex = VenueIdentifier.forOpenAlex(OPENALEX_ID);

      assertThat(issn.isStandardPublishingId()).isTrue();
      assertThat(isbn.isStandardPublishingId()).isTrue();
      assertThat(openalex.isStandardPublishingId()).isFalse();
    }
  }

  @Nested
  @DisplayName("equals() 和 hashCode() 测试")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("相同类型和值应该相等（Record 自动生成）")
    void shouldBeEqualForSameTypeAndValue() {
      // Given
      VenueIdentifier id1 = new VenueIdentifier(VenueIdentifierType.OPENALEX, OPENALEX_ID);
      VenueIdentifier id2 = new VenueIdentifier(VenueIdentifierType.OPENALEX, OPENALEX_ID);

      // Then
      assertThat(id1).isEqualTo(id2);
      assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    @DisplayName("不同类型应该不相等")
    void shouldNotBeEqualForDifferentType() {
      // Given
      VenueIdentifier id1 = new VenueIdentifier(VenueIdentifierType.MAG, "12345");
      VenueIdentifier id2 = new VenueIdentifier(VenueIdentifierType.FATCAT, "12345");

      // Then
      assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("不同值应该不相等")
    void shouldNotBeEqualForDifferentValue() {
      // Given
      VenueIdentifier id1 = new VenueIdentifier(VenueIdentifierType.OPENALEX, "S111");
      VenueIdentifier id2 = new VenueIdentifier(VenueIdentifierType.OPENALEX, "S222");

      // Then
      assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("与 null 比较应该返回 false")
    void shouldNotBeEqualToNull() {
      VenueIdentifier identifier = VenueIdentifier.forOpenAlex(OPENALEX_ID);
      assertThat(identifier).isNotEqualTo(null);
    }

    @Test
    @DisplayName("与不同类型对象比较应该返回 false")
    void shouldNotBeEqualToDifferentType() {
      VenueIdentifier identifier = VenueIdentifier.forOpenAlex(OPENALEX_ID);
      assertThat(identifier).isNotEqualTo("not an identifier");
    }

    @Test
    @DisplayName("自反性：对象应该等于自身")
    void shouldBeEqualToItself() {
      VenueIdentifier identifier = VenueIdentifier.forOpenAlex(OPENALEX_ID);
      assertThat(identifier).isEqualTo(identifier);
    }
  }

  @Nested
  @DisplayName("toString() 测试")
  class ToStringTests {

    @Test
    @DisplayName("toString() 应该包含关键信息")
    void toStringShouldContainKeyInfo() {
      // Given
      VenueIdentifier identifier = VenueIdentifier.forOpenAlex(OPENALEX_ID);

      // When
      String result = identifier.toString();

      // Then
      assertThat(result).contains("OPENALEX");
      assertThat(result).contains(OPENALEX_ID);
    }
  }
}
