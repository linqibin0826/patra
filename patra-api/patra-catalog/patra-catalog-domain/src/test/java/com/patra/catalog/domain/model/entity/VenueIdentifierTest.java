package com.patra.catalog.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueIdentifier 实体单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 覆盖工厂方法、ISSN 格式验证、业务方法和 equals/hashCode
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
  @DisplayName("create() 工厂方法测试")
  class CreateTests {

    @Test
    @DisplayName("应该正确创建标识符（含首选标记）")
    void shouldCreateIdentifierWithPrimary() {
      // When
      VenueIdentifier identifier =
          VenueIdentifier.create(VenueIdentifierType.OPENALEX, OPENALEX_ID, true);

      // Then
      assertThat(identifier.getId()).isNull(); // 新建时无 ID
      assertThat(identifier.getType()).isEqualTo(VenueIdentifierType.OPENALEX);
      assertThat(identifier.getValue()).isEqualTo(OPENALEX_ID);
      assertThat(identifier.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("应该正确创建标识符（非首选）")
    void shouldCreateNonPrimaryIdentifier() {
      // When
      VenueIdentifier identifier = VenueIdentifier.create(VenueIdentifierType.MAG, "12345");

      // Then
      assertThat(identifier.isPrimary()).isFalse();
    }

    @Test
    @DisplayName("类型为 null 时应该抛出异常")
    void shouldThrowWhenTypeIsNull() {
      assertThatThrownBy(() -> VenueIdentifier.create(null, "value", false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("标识符类型不能为空");
    }

    @Test
    @DisplayName("值为空时应该抛出异常")
    void shouldThrowWhenValueIsBlank() {
      assertThatThrownBy(() -> VenueIdentifier.create(VenueIdentifierType.OPENALEX, "", false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("标识符值不能为空");

      assertThatThrownBy(() -> VenueIdentifier.create(VenueIdentifierType.OPENALEX, "  ", false))
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
      VenueIdentifier identifier =
          VenueIdentifier.create(VenueIdentifierType.ISSN, VALID_ISSN, false);

      // Then
      assertThat(identifier.getValue()).isEqualTo(VALID_ISSN);
    }

    @Test
    @DisplayName("ISSN 带 X 校验位应该通过验证")
    void shouldAcceptIssnWithCheckDigitX() {
      // When
      VenueIdentifier identifier =
          VenueIdentifier.create(VenueIdentifierType.ISSN, VALID_ISSN_WITH_X, false);

      // Then
      assertThat(identifier.getValue()).isEqualTo(VALID_ISSN_WITH_X);
    }

    @Test
    @DisplayName("小写 x 应该被标准化为大写 X")
    void shouldNormalizeLowercaseXToUppercase() {
      // When
      VenueIdentifier identifier =
          VenueIdentifier.create(VenueIdentifierType.ISSN, VALID_ISSN_LOWERCASE_X, false);

      // Then
      assertThat(identifier.getValue()).isEqualTo("1234-567X");
    }

    @Test
    @DisplayName("无效 ISSN 格式应该抛出异常（缺少连字符）")
    void shouldRejectInvalidIssnWithoutHyphen() {
      assertThatThrownBy(
              () -> VenueIdentifier.create(VenueIdentifierType.ISSN, INVALID_ISSN_FORMAT, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ISSN 格式无效");
    }

    @Test
    @DisplayName("无效 ISSN 格式应该抛出异常（含字母）")
    void shouldRejectInvalidIssnWithLetters() {
      assertThatThrownBy(
              () -> VenueIdentifier.create(VenueIdentifierType.ISSN, INVALID_ISSN_LETTERS, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ISSN 格式无效");
    }

    @Test
    @DisplayName("ISSN_L 类型也应该验证 ISSN 格式")
    void shouldValidateIssnFormatForIssnLType() {
      // When - 有效格式
      VenueIdentifier identifier =
          VenueIdentifier.create(VenueIdentifierType.ISSN_L, VALID_ISSN, true);
      assertThat(identifier.getValue()).isEqualTo(VALID_ISSN);

      // Then - 无效格式
      assertThatThrownBy(
              () -> VenueIdentifier.create(VenueIdentifierType.ISSN_L, INVALID_ISSN_FORMAT, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ISSN 格式无效");
    }

    @Test
    @DisplayName("非 ISSN 类型不应验证 ISSN 格式")
    void shouldNotValidateIssnFormatForNonIssnTypes() {
      // 应该允许任意格式
      VenueIdentifier identifier =
          VenueIdentifier.create(VenueIdentifierType.OPENALEX, INVALID_ISSN_FORMAT, false);
      assertThat(identifier.getValue()).isEqualTo(INVALID_ISSN_FORMAT);
    }
  }

  @Nested
  @DisplayName("便捷工厂方法测试")
  class ConvenienceFactoryMethodTests {

    @Test
    @DisplayName("forOpenAlex() 应该创建首选 OpenAlex 标识符")
    void forOpenAlexShouldCreatePrimaryIdentifier() {
      // When
      VenueIdentifier identifier = VenueIdentifier.forOpenAlex(OPENALEX_ID);

      // Then
      assertThat(identifier.getType()).isEqualTo(VenueIdentifierType.OPENALEX);
      assertThat(identifier.getValue()).isEqualTo(OPENALEX_ID);
      assertThat(identifier.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("forIssn() 应该创建 ISSN 标识符")
    void forIssnShouldCreateIdentifier() {
      // When
      VenueIdentifier primary = VenueIdentifier.forIssn(VALID_ISSN, true);
      VenueIdentifier nonPrimary = VenueIdentifier.forIssn(VALID_ISSN_WITH_X, false);

      // Then
      assertThat(primary.getType()).isEqualTo(VenueIdentifierType.ISSN);
      assertThat(primary.isPrimary()).isTrue();
      assertThat(nonPrimary.isPrimary()).isFalse();
    }

    @Test
    @DisplayName("forIssnL() 应该创建首选 Linking ISSN 标识符")
    void forIssnLShouldCreatePrimaryIdentifier() {
      // When
      VenueIdentifier identifier = VenueIdentifier.forIssnL(VALID_ISSN);

      // Then
      assertThat(identifier.getType()).isEqualTo(VenueIdentifierType.ISSN_L);
      assertThat(identifier.getValue()).isEqualTo(VALID_ISSN);
      assertThat(identifier.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("forNlm() 应该创建首选 NLM 标识符")
    void forNlmShouldCreatePrimaryIdentifier() {
      // When
      VenueIdentifier identifier = VenueIdentifier.forNlm(NLM_ID);

      // Then
      assertThat(identifier.getType()).isEqualTo(VenueIdentifierType.NLM);
      assertThat(identifier.getValue()).isEqualTo(NLM_ID);
      assertThat(identifier.isPrimary()).isTrue();
    }
  }

  @Nested
  @DisplayName("restore() 方法测试")
  class RestoreTests {

    @Test
    @DisplayName("应该正确从持久化状态重建实体")
    void shouldRestoreFromPersistedState() {
      // Given
      Long id = 123L;

      // When
      VenueIdentifier identifier =
          VenueIdentifier.restore(id, VenueIdentifierType.OPENALEX, OPENALEX_ID, true);

      // Then
      assertThat(identifier.getId()).isEqualTo(id);
      assertThat(identifier.getType()).isEqualTo(VenueIdentifierType.OPENALEX);
      assertThat(identifier.getValue()).isEqualTo(OPENALEX_ID);
      assertThat(identifier.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("restore 时也应验证 ISSN 格式")
    void shouldValidateIssnFormatOnRestore() {
      assertThatThrownBy(
              () ->
                  VenueIdentifier.restore(1L, VenueIdentifierType.ISSN, INVALID_ISSN_FORMAT, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ISSN 格式无效");
    }
  }

  @Nested
  @DisplayName("业务方法测试")
  class BusinessMethodTests {

    @Test
    @DisplayName("assignId() 应该设置 ID")
    void assignIdShouldSetId() {
      // Given
      VenueIdentifier identifier = VenueIdentifier.forOpenAlex(OPENALEX_ID);
      assertThat(identifier.getId()).isNull();

      // When
      identifier.assignId(456L);

      // Then
      assertThat(identifier.getId()).isEqualTo(456L);
    }

    @Test
    @DisplayName("markAsPrimary() 应该设置为首选")
    void markAsPrimaryShouldSetPrimaryTrue() {
      // Given
      VenueIdentifier identifier =
          VenueIdentifier.create(VenueIdentifierType.ISSN, VALID_ISSN, false);
      assertThat(identifier.isPrimary()).isFalse();

      // When
      identifier.markAsPrimary();

      // Then
      assertThat(identifier.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("unmarkAsPrimary() 应该取消首选")
    void unmarkAsPrimaryShouldSetPrimaryFalse() {
      // Given
      VenueIdentifier identifier = VenueIdentifier.forOpenAlex(OPENALEX_ID);
      assertThat(identifier.isPrimary()).isTrue();

      // When
      identifier.unmarkAsPrimary();

      // Then
      assertThat(identifier.isPrimary()).isFalse();
    }

    @Test
    @DisplayName("isOpenAlexId() 应该返回正确结果")
    void isOpenAlexIdShouldReturnCorrectly() {
      VenueIdentifier openalex = VenueIdentifier.forOpenAlex(OPENALEX_ID);
      VenueIdentifier issn = VenueIdentifier.forIssn(VALID_ISSN, true);

      assertThat(openalex.isOpenAlexId()).isTrue();
      assertThat(issn.isOpenAlexId()).isFalse();
    }

    @Test
    @DisplayName("isIssnId() 应该返回正确结果")
    void isIssnIdShouldReturnCorrectly() {
      VenueIdentifier issn = VenueIdentifier.forIssn(VALID_ISSN, true);
      VenueIdentifier issnL = VenueIdentifier.forIssnL(VALID_ISSN);
      VenueIdentifier openalex = VenueIdentifier.forOpenAlex(OPENALEX_ID);

      assertThat(issn.isIssnId()).isTrue();
      assertThat(issnL.isIssnId()).isTrue();
      assertThat(openalex.isIssnId()).isFalse();
    }

    @Test
    @DisplayName("isStandardPublishingId() 应该返回正确结果")
    void isStandardPublishingIdShouldReturnCorrectly() {
      VenueIdentifier issn = VenueIdentifier.forIssn(VALID_ISSN, true);
      VenueIdentifier isbn =
          VenueIdentifier.create(VenueIdentifierType.ISBN, "978-3-16-148410-0", false);
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
    @DisplayName("相同类型和值应该相等")
    void shouldBeEqualForSameTypeAndValue() {
      // Given
      VenueIdentifier id1 = VenueIdentifier.create(VenueIdentifierType.OPENALEX, OPENALEX_ID, true);
      VenueIdentifier id2 =
          VenueIdentifier.create(VenueIdentifierType.OPENALEX, OPENALEX_ID, false);

      // Then - isPrimary 不影响相等性
      assertThat(id1).isEqualTo(id2);
      assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    @DisplayName("不同类型应该不相等")
    void shouldNotBeEqualForDifferentType() {
      // Given
      VenueIdentifier id1 = VenueIdentifier.create(VenueIdentifierType.MAG, "12345", false);
      VenueIdentifier id2 = VenueIdentifier.create(VenueIdentifierType.FATCAT, "12345", false);

      // Then
      assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("不同值应该不相等")
    void shouldNotBeEqualForDifferentValue() {
      // Given
      VenueIdentifier id1 = VenueIdentifier.create(VenueIdentifierType.OPENALEX, "S111", false);
      VenueIdentifier id2 = VenueIdentifier.create(VenueIdentifierType.OPENALEX, "S222", false);

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
      assertThat(result).contains("primary=true");
    }
  }
}
