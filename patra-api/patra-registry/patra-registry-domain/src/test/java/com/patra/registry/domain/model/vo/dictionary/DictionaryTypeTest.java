package com.patra.registry.domain.model.vo.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.registry.domain.exception.DomainValidationException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// DictionaryType 值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DictionaryType 值对象")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class DictionaryTypeTest {

  @Nested
  @DisplayName("构造函数验证")
  class ConstructorTests {

    @Test
    @DisplayName("应能正常构造有效的字典类型")
    void shouldConstructValidType() {
      DictionaryType type = new DictionaryType(1L, "country");

      assertThat(type.id()).isEqualTo(1L);
      assertThat(type.typeCode()).isEqualTo("country");
    }

    @Test
    @DisplayName("typeCode 应自动修剪空白")
    void shouldTrimTypeCode() {
      DictionaryType type = new DictionaryType(1L, "  country  ");

      assertThat(type.typeCode()).isEqualTo("country");
    }
  }

  @Nested
  @DisplayName("边界条件验证")
  class ValidationTests {

    @Test
    @DisplayName("id 为 null 时应抛出异常")
    void shouldThrowWhenIdIsNull() {
      assertThatThrownBy(() -> new DictionaryType(null, "country"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary type id");
    }

    @Test
    @DisplayName("id 为 0 时应抛出异常")
    void shouldThrowWhenIdIsZero() {
      assertThatThrownBy(() -> new DictionaryType(0L, "country"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary type id");
    }

    @Test
    @DisplayName("id 为负数时应抛出异常")
    void shouldThrowWhenIdIsNegative() {
      assertThatThrownBy(() -> new DictionaryType(-1L, "country"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary type id");
    }

    @Test
    @DisplayName("typeCode 为 null 时应抛出异常")
    void shouldThrowWhenTypeCodeIsNull() {
      assertThatThrownBy(() -> new DictionaryType(1L, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary type code");
    }

    @Test
    @DisplayName("typeCode 为空白时应抛出异常")
    void shouldThrowWhenTypeCodeIsBlank() {
      assertThatThrownBy(() -> new DictionaryType(1L, "   "))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Dictionary type code");
    }
  }

  @Nested
  @DisplayName("Record 特性")
  class RecordTests {

    @Test
    @DisplayName("相同值的对象应相等")
    void shouldBeEqualForSameValues() {
      DictionaryType type1 = new DictionaryType(1L, "country");
      DictionaryType type2 = new DictionaryType(1L, "country");

      assertThat(type1).isEqualTo(type2);
      assertThat(type1.hashCode()).isEqualTo(type2.hashCode());
    }

    @Test
    @DisplayName("不同值的对象应不相等")
    void shouldNotBeEqualForDifferentValues() {
      DictionaryType type1 = new DictionaryType(1L, "country");
      DictionaryType type2 = new DictionaryType(2L, "language");

      assertThat(type1).isNotEqualTo(type2);
    }
  }
}
