package dev.linqibin.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/// LinkType 枚举测试。
///
/// 基于 ROR Schema v2.0 的链接类型定义：website, wikipedia
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LinkType 枚举测试")
class LinkTypeTest {

  @Nested
  @DisplayName("枚举值验证")
  class EnumValuesTest {

    @Test
    @DisplayName("应包含 ROR 定义的 2 种链接类型")
    void shouldContainAllRorLinkTypes() {
      assertThat(LinkType.values()).hasSize(2);
      assertThat(LinkType.values())
          .extracting(LinkType::name)
          .containsExactlyInAnyOrder("WEBSITE", "WIKIPEDIA");
    }

    @ParameterizedTest
    @CsvSource({"WEBSITE, website, 官方网站", "WIKIPEDIA, wikipedia, Wikipedia"})
    @DisplayName("每个枚举值应有正确的 code 和 description")
    void shouldHaveCorrectCodeAndDescription(
        String enumName, String expectedCode, String expectedDescription) {
      LinkType type = LinkType.valueOf(enumName);
      assertThat(type.getCode()).isEqualTo(expectedCode);
      assertThat(type.getDescription()).isEqualTo(expectedDescription);
    }
  }

  @Nested
  @DisplayName("fromCode() 方法测试")
  class FromCodeTest {

    @ParameterizedTest
    @CsvSource({
      "website, WEBSITE",
      "WEBSITE, WEBSITE",
      "Website, WEBSITE",
      "wikipedia, WIKIPEDIA",
      "WIKIPEDIA, WIKIPEDIA"
    })
    @DisplayName("应支持大小写不敏感的代码解析")
    void shouldParseCodeCaseInsensitively(String code, String expectedEnum) {
      assertThat(LinkType.fromCode(code).name()).isEqualTo(expectedEnum);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("空白值应抛出 IllegalArgumentException")
    void shouldThrowExceptionForBlankValue(String code) {
      assertThatThrownBy(() -> LinkType.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("未知代码应抛出 IllegalArgumentException")
    void shouldThrowExceptionForUnknownCode() {
      assertThatThrownBy(() -> LinkType.fromCode("unknown"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的链接类型");
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("isWebsite() 应正确识别官方网站")
    void shouldIdentifyWebsite() {
      assertThat(LinkType.WEBSITE.isWebsite()).isTrue();
      assertThat(LinkType.WIKIPEDIA.isWebsite()).isFalse();
    }

    @Test
    @DisplayName("isWikipedia() 应正确识别 Wikipedia 链接")
    void shouldIdentifyWikipedia() {
      assertThat(LinkType.WIKIPEDIA.isWikipedia()).isTrue();
      assertThat(LinkType.WEBSITE.isWikipedia()).isFalse();
    }

    @Test
    @DisplayName("isExternal() 应正确识别外部链接")
    void shouldIdentifyExternal() {
      // Wikipedia 是外部参考链接
      assertThat(LinkType.WIKIPEDIA.isExternal()).isTrue();
      // 官方网站不算外部链接
      assertThat(LinkType.WEBSITE.isExternal()).isFalse();
    }
  }
}
