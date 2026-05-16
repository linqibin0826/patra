package dev.linqibin.patra.registry.domain.model.vo.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// ReferenceStandard 值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ReferenceStandard 值对象")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class ReferenceStandardTest {

  @Nested
  @DisplayName("构造函数验证")
  class ConstructorTests {

    @Test
    @DisplayName("应能正常构造有效的来源标准")
    void shouldConstructValidStandard() {
      ReferenceStandard standard =
          new ReferenceStandard(
              1L, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1 alpha-2", true, true);

      assertThat(standard.id()).isEqualTo(1L);
      assertThat(standard.dictTypeCode()).isEqualTo("country");
      assertThat(standard.standardCode()).isEqualTo("ISO_3166_1_ALPHA2");
      assertThat(standard.standardName()).isEqualTo("ISO 3166-1 alpha-2");
      assertThat(standard.canonical()).isTrue();
      assertThat(standard.enabled()).isTrue();
    }

    @Test
    @DisplayName("standardName 为 null 时应正常构造")
    void shouldAllowNullStandardName() {
      ReferenceStandard standard =
          new ReferenceStandard(1L, "country", "ISO_3166_1_ALPHA2", null, true, true);

      assertThat(standard.standardName()).isNull();
    }

    @Test
    @DisplayName("非规范标准应正常构造")
    void shouldConstructNonCanonicalStandard() {
      ReferenceStandard standard =
          new ReferenceStandard(2L, "country", "NAME_EN", "English Name", false, true);

      assertThat(standard.canonical()).isFalse();
      assertThat(standard.enabled()).isTrue();
    }

    @Test
    @DisplayName("dictTypeCode 应自动修剪空白")
    void shouldTrimDictTypeCode() {
      ReferenceStandard standard =
          new ReferenceStandard(
              1L, "  country  ", "ISO_3166_1_ALPHA2", "ISO 3166-1 alpha-2", true, true);

      assertThat(standard.dictTypeCode()).isEqualTo("country");
    }

    @Test
    @DisplayName("standardCode 应自动修剪空白")
    void shouldTrimStandardCode() {
      ReferenceStandard standard =
          new ReferenceStandard(
              1L, "country", "  ISO_3166_1_ALPHA2  ", "ISO 3166-1 alpha-2", true, true);

      assertThat(standard.standardCode()).isEqualTo("ISO_3166_1_ALPHA2");
    }

    @Test
    @DisplayName("standardName 应自动修剪空白")
    void shouldTrimStandardName() {
      ReferenceStandard standard =
          new ReferenceStandard(
              1L, "country", "ISO_3166_1_ALPHA2", "  ISO 3166-1 alpha-2  ", true, true);

      assertThat(standard.standardName()).isEqualTo("ISO 3166-1 alpha-2");
    }
  }

  @Nested
  @DisplayName("边界条件验证")
  class ValidationTests {

    @Test
    @DisplayName("id 为 null 时应抛出异常")
    void shouldThrowWhenIdIsNull() {
      assertThatThrownBy(
              () ->
                  new ReferenceStandard(
                      null, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1", true, true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Reference standard id");
    }

    @Test
    @DisplayName("id 为 0 时应抛出异常")
    void shouldThrowWhenIdIsZero() {
      assertThatThrownBy(
              () ->
                  new ReferenceStandard(
                      0L, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1", true, true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Reference standard id");
    }

    @Test
    @DisplayName("id 为负数时应抛出异常")
    void shouldThrowWhenIdIsNegative() {
      assertThatThrownBy(
              () ->
                  new ReferenceStandard(
                      -1L, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1", true, true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Reference standard id");
    }

    @Test
    @DisplayName("dictTypeCode 为 null 时应抛出异常")
    void shouldThrowWhenDictTypeCodeIsNull() {
      assertThatThrownBy(
              () -> new ReferenceStandard(1L, null, "ISO_3166_1_ALPHA2", "ISO 3166-1", true, true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dict type code");
    }

    @Test
    @DisplayName("dictTypeCode 为空白时应抛出异常")
    void shouldThrowWhenDictTypeCodeIsBlank() {
      assertThatThrownBy(
              () -> new ReferenceStandard(1L, "   ", "ISO_3166_1_ALPHA2", "ISO 3166-1", true, true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dict type code");
    }

    @Test
    @DisplayName("standardCode 为 null 时应抛出异常")
    void shouldThrowWhenStandardCodeIsNull() {
      assertThatThrownBy(() -> new ReferenceStandard(1L, "country", null, "ISO 3166-1", true, true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Reference standard code");
    }

    @Test
    @DisplayName("standardCode 为空白时应抛出异常")
    void shouldThrowWhenStandardCodeIsBlank() {
      assertThatThrownBy(
              () -> new ReferenceStandard(1L, "country", "   ", "ISO 3166-1", true, true))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Reference standard code");
    }
  }

  @Nested
  @DisplayName("Record 特性")
  class RecordTests {

    @Test
    @DisplayName("相同值的对象应相等")
    void shouldBeEqualForSameValues() {
      ReferenceStandard std1 =
          new ReferenceStandard(1L, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1", true, true);
      ReferenceStandard std2 =
          new ReferenceStandard(1L, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1", true, true);

      assertThat(std1).isEqualTo(std2);
      assertThat(std1.hashCode()).isEqualTo(std2.hashCode());
    }

    @Test
    @DisplayName("不同值的对象应不相等")
    void shouldNotBeEqualForDifferentValues() {
      ReferenceStandard std1 =
          new ReferenceStandard(1L, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1", true, true);
      ReferenceStandard std2 =
          new ReferenceStandard(2L, "country", "NAME_EN", "English Name", false, true);

      assertThat(std1).isNotEqualTo(std2);
    }
  }
}
