package dev.linqibin.patra.catalog.domain.model.vo.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// AuthorNameVariant 值对象测试。
///
/// 测试场景覆盖 PubMed Computed Authors 的 names 数组格式。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("AuthorNameVariant 值对象测试")
class AuthorNameVariantTest {

  @Nested
  @DisplayName("parse() 方法测试")
  class ParseTests {

    @Test
    @DisplayName("应能解析完整格式：姓,名,缩写")
    void shouldParseFullFormat() {
      // Given
      String rawString = "Lu,Zhiyong,Z";

      // When
      AuthorNameVariant variant = AuthorNameVariant.parse(rawString);

      // Then
      assertThat(variant.lastName()).isEqualTo("Lu");
      assertThat(variant.foreName()).isEqualTo("Zhiyong");
      assertThat(variant.initials()).isEqualTo("Z");
      assertThat(variant.fullString()).isEqualTo(rawString);
    }

    @Test
    @DisplayName("应能解析两部分格式：姓,缩写")
    void shouldParseTwoPartFormat() {
      // Given
      String rawString = "Smith,JK";

      // When
      AuthorNameVariant variant = AuthorNameVariant.parse(rawString);

      // Then
      assertThat(variant.lastName()).isEqualTo("Smith");
      assertThat(variant.foreName()).isNull();
      assertThat(variant.initials()).isEqualTo("JK");
      assertThat(variant.fullString()).isEqualTo(rawString);
    }

    @Test
    @DisplayName("应能解析单部分格式：仅姓")
    void shouldParseSinglePartFormat() {
      // Given
      String rawString = "Einstein";

      // When
      AuthorNameVariant variant = AuthorNameVariant.parse(rawString);

      // Then
      assertThat(variant.lastName()).isEqualTo("Einstein");
      assertThat(variant.foreName()).isNull();
      assertThat(variant.initials()).isNull();
      assertThat(variant.fullString()).isEqualTo(rawString);
    }

    @Test
    @DisplayName("应能解析带空格的复杂姓名")
    void shouldParseComplexNameWithSpaces() {
      // Given: 带有空格的复杂姓氏
      String rawString = "Van der Berg,Jan,J";

      // When
      AuthorNameVariant variant = AuthorNameVariant.parse(rawString);

      // Then
      assertThat(variant.lastName()).isEqualTo("Van der Berg");
      assertThat(variant.foreName()).isEqualTo("Jan");
      assertThat(variant.initials()).isEqualTo("J");
    }

    @Test
    @DisplayName("应拒绝空字符串")
    void shouldRejectEmptyString() {
      assertThatThrownBy(() -> AuthorNameVariant.parse(""))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("应拒绝 null 值")
    void shouldRejectNull() {
      assertThatThrownBy(() -> AuthorNameVariant.parse(null))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("of() 工厂方法测试")
  class OfTests {

    @Test
    @DisplayName("应能创建完整的名字变体")
    void shouldCreateFullVariant() {
      // When
      AuthorNameVariant variant = AuthorNameVariant.of("Lu", "Zhiyong", "Z", "Lu,Zhiyong,Z");

      // Then
      assertThat(variant.lastName()).isEqualTo("Lu");
      assertThat(variant.foreName()).isEqualTo("Zhiyong");
      assertThat(variant.initials()).isEqualTo("Z");
      assertThat(variant.fullString()).isEqualTo("Lu,Zhiyong,Z");
    }

    @Test
    @DisplayName("应能创建仅有姓和缩写的变体")
    void shouldCreateVariantWithLastNameAndInitials() {
      // When
      AuthorNameVariant variant = AuthorNameVariant.of("Smith", null, "JK", "Smith,JK");

      // Then
      assertThat(variant.lastName()).isEqualTo("Smith");
      assertThat(variant.foreName()).isNull();
      assertThat(variant.initials()).isEqualTo("JK");
    }

    @Test
    @DisplayName("fullString 为必填字段")
    void shouldRequireFullString() {
      assertThatThrownBy(() -> AuthorNameVariant.of("Smith", "John", "J", null))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("toDisplayString() 方法测试")
  class ToDisplayStringTests {

    @Test
    @DisplayName("应返回完整名字的显示格式")
    void shouldReturnDisplayStringForFullName() {
      // Given
      AuthorNameVariant variant = AuthorNameVariant.parse("Lu,Zhiyong,Z");

      // When
      String display = variant.toDisplayString();

      // Then
      assertThat(display).isEqualTo("Zhiyong Lu");
    }

    @Test
    @DisplayName("应返回仅有姓和缩写的显示格式")
    void shouldReturnDisplayStringForShortName() {
      // Given
      AuthorNameVariant variant = AuthorNameVariant.parse("Smith,JK");

      // When
      String display = variant.toDisplayString();

      // Then
      assertThat(display).isEqualTo("Smith, JK");
    }

    @Test
    @DisplayName("应返回仅有姓的显示格式")
    void shouldReturnDisplayStringForLastNameOnly() {
      // Given
      AuthorNameVariant variant = AuthorNameVariant.parse("Einstein");

      // When
      String display = variant.toDisplayString();

      // Then
      assertThat(display).isEqualTo("Einstein");
    }
  }

  @Nested
  @DisplayName("toNormalizedForm() 方法测试")
  class ToNormalizedFormTests {

    @Test
    @DisplayName("应返回标准化的姓名形式")
    void shouldReturnNormalizedForm() {
      // Given
      AuthorNameVariant variant = AuthorNameVariant.parse("Lu,Zhiyong,Z");

      // When
      String normalized = variant.toNormalizedForm();

      // Then: 小写，无空格
      assertThat(normalized).isEqualTo("lu+z");
    }

    @Test
    @DisplayName("应处理复杂姓名的标准化")
    void shouldNormalizeComplexName() {
      // Given
      AuthorNameVariant variant = AuthorNameVariant.parse("Van der Berg,Jan,J");

      // When
      String normalized = variant.toNormalizedForm();

      // Then
      assertThat(normalized).isEqualTo("vanderberg+j");
    }
  }

  @Nested
  @DisplayName("equals 和 hashCode 测试")
  class EqualsHashCodeTests {

    @Test
    @DisplayName("相同内容的变体应相等")
    void shouldBeEqualForSameContent() {
      // Given
      AuthorNameVariant variant1 = AuthorNameVariant.parse("Lu,Zhiyong,Z");
      AuthorNameVariant variant2 = AuthorNameVariant.parse("Lu,Zhiyong,Z");

      // Then
      assertThat(variant1).isEqualTo(variant2);
      assertThat(variant1.hashCode()).isEqualTo(variant2.hashCode());
    }

    @Test
    @DisplayName("不同内容的变体应不相等")
    void shouldNotBeEqualForDifferentContent() {
      // Given
      AuthorNameVariant variant1 = AuthorNameVariant.parse("Lu,Zhiyong,Z");
      AuthorNameVariant variant2 = AuthorNameVariant.parse("Smith,John,J");

      // Then
      assertThat(variant1).isNotEqualTo(variant2);
    }
  }
}
