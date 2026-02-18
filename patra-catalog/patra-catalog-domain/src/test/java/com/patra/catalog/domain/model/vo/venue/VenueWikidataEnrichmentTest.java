package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueWikidataEnrichment 值对象单元测试。
///
/// **测试策略**：
///
/// - 工厂方法：验证 `of()` 正确构造
/// - 判断方法：`hasTitleZh()`、`hasImageUrl()`、`hasHomepageUrl()` 的 null/空/空白边界
/// - Record 等值语义：同值相等、异值不等
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueWikidataEnrichment 单元测试")
@Timeout(2)
class VenueWikidataEnrichmentTest {

  @Nested
  @DisplayName("of() 工厂方法测试")
  class OfFactoryMethodTests {

    @Test
    @DisplayName("应该创建包含所有字段的富化数据")
    void shouldCreateWithAllFields() {
      // When
      var enrichment =
          VenueWikidataEnrichment.of("自然", "http://example.com/img.jpg", "https://www.nature.com");

      // Then
      assertThat(enrichment.titleZh()).isEqualTo("自然");
      assertThat(enrichment.imageUrl()).isEqualTo("http://example.com/img.jpg");
      assertThat(enrichment.homepageUrl()).isEqualTo("https://www.nature.com");
    }

    @Test
    @DisplayName("应该允许所有字段为 null")
    void shouldAllowAllFieldsNull() {
      // When
      var enrichment = VenueWikidataEnrichment.of(null, null, null);

      // Then
      assertThat(enrichment.titleZh()).isNull();
      assertThat(enrichment.imageUrl()).isNull();
      assertThat(enrichment.homepageUrl()).isNull();
    }
  }

  @Nested
  @DisplayName("hasTitleZh() 测试")
  class HasTitleZhTests {

    @Test
    @DisplayName("有中文标题时返回 true")
    void shouldReturnTrueWhenHasTitleZh() {
      var enrichment = VenueWikidataEnrichment.of("自然", null, null);
      assertThat(enrichment.hasTitleZh()).isTrue();
    }

    @Test
    @DisplayName("null 时返回 false")
    void shouldReturnFalseWhenNull() {
      var enrichment = VenueWikidataEnrichment.of(null, null, null);
      assertThat(enrichment.hasTitleZh()).isFalse();
    }

    @Test
    @DisplayName("空字符串时返回 false")
    void shouldReturnFalseWhenEmpty() {
      var enrichment = VenueWikidataEnrichment.of("", null, null);
      assertThat(enrichment.hasTitleZh()).isFalse();
    }

    @Test
    @DisplayName("空白字符串时返回 false")
    void shouldReturnFalseWhenBlank() {
      var enrichment = VenueWikidataEnrichment.of("   ", null, null);
      assertThat(enrichment.hasTitleZh()).isFalse();
    }
  }

  @Nested
  @DisplayName("hasImageUrl() 测试")
  class HasImageUrlTests {

    @Test
    @DisplayName("有封面图片 URL 时返回 true")
    void shouldReturnTrueWhenHasImageUrl() {
      var enrichment = VenueWikidataEnrichment.of(null, "http://example.com/img.jpg", null);
      assertThat(enrichment.hasImageUrl()).isTrue();
    }

    @Test
    @DisplayName("null 时返回 false")
    void shouldReturnFalseWhenNull() {
      var enrichment = VenueWikidataEnrichment.of(null, null, null);
      assertThat(enrichment.hasImageUrl()).isFalse();
    }

    @Test
    @DisplayName("空字符串时返回 false")
    void shouldReturnFalseWhenEmpty() {
      var enrichment = VenueWikidataEnrichment.of(null, "", null);
      assertThat(enrichment.hasImageUrl()).isFalse();
    }

    @Test
    @DisplayName("空白字符串时返回 false")
    void shouldReturnFalseWhenBlank() {
      var enrichment = VenueWikidataEnrichment.of(null, "   ", null);
      assertThat(enrichment.hasImageUrl()).isFalse();
    }
  }

  @Nested
  @DisplayName("hasHomepageUrl() 测试")
  class HasHomepageUrlTests {

    @Test
    @DisplayName("有官方网站 URL 时返回 true")
    void shouldReturnTrueWhenHasHomepageUrl() {
      var enrichment = VenueWikidataEnrichment.of(null, null, "https://www.nature.com");
      assertThat(enrichment.hasHomepageUrl()).isTrue();
    }

    @Test
    @DisplayName("null 时返回 false")
    void shouldReturnFalseWhenNull() {
      var enrichment = VenueWikidataEnrichment.of(null, null, null);
      assertThat(enrichment.hasHomepageUrl()).isFalse();
    }

    @Test
    @DisplayName("空字符串时返回 false")
    void shouldReturnFalseWhenEmpty() {
      var enrichment = VenueWikidataEnrichment.of(null, null, "");
      assertThat(enrichment.hasHomepageUrl()).isFalse();
    }

    @Test
    @DisplayName("空白字符串时返回 false")
    void shouldReturnFalseWhenBlank() {
      var enrichment = VenueWikidataEnrichment.of(null, null, "   ");
      assertThat(enrichment.hasHomepageUrl()).isFalse();
    }
  }

  @Nested
  @DisplayName("Record 等值语义测试")
  class EqualityTests {

    @Test
    @DisplayName("同值对象应相等")
    void shouldBeEqualWhenSameValues() {
      var a = VenueWikidataEnrichment.of("自然", "http://img.jpg", "https://nature.com");
      var b = VenueWikidataEnrichment.of("自然", "http://img.jpg", "https://nature.com");
      assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("不同值对象不应相等")
    void shouldNotBeEqualWhenDifferentValues() {
      var a = VenueWikidataEnrichment.of("自然", null, null);
      var b = VenueWikidataEnrichment.of("柳叶刀", null, null);
      assertThat(a).isNotEqualTo(b);
    }
  }
}
