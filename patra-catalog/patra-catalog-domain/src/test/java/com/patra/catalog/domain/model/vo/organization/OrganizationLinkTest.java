package com.patra.catalog.domain.model.vo.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.LinkType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// OrganizationLink 值对象单元测试。
///
/// 基于 ROR Schema v2.0 的 links 字段定义。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationLink 值对象")
class OrganizationLinkTest {

  @Nested
  @DisplayName("创建测试")
  class CreationTest {

    @Test
    @DisplayName("应正确创建官方网站链接")
    void shouldCreateWebsiteLink() {
      OrganizationLink link = OrganizationLink.website("https://www.harvard.edu");

      assertThat(link.type()).isEqualTo(LinkType.WEBSITE);
      assertThat(link.value()).isEqualTo("https://www.harvard.edu");
    }

    @Test
    @DisplayName("应正确创建 Wikipedia 链接")
    void shouldCreateWikipediaLink() {
      OrganizationLink link =
          OrganizationLink.wikipedia("https://en.wikipedia.org/wiki/Harvard_University");

      assertThat(link.type()).isEqualTo(LinkType.WIKIPEDIA);
      assertThat(link.value()).isEqualTo("https://en.wikipedia.org/wiki/Harvard_University");
    }

    @Test
    @DisplayName("应通过通用工厂方法创建链接")
    void shouldCreateLinkWithFactory() {
      OrganizationLink link = OrganizationLink.of(LinkType.WEBSITE, "https://www.mit.edu");

      assertThat(link.type()).isEqualTo(LinkType.WEBSITE);
      assertThat(link.value()).isEqualTo("https://www.mit.edu");
    }

    @Test
    @DisplayName("null 类型应抛出异常")
    void shouldThrowWhenTypeIsNull() {
      assertThatThrownBy(() -> OrganizationLink.of(null, "https://example.com"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("链接类型不能为空");
    }

    @Test
    @DisplayName("空白值应抛出异常")
    void shouldThrowWhenValueIsBlank() {
      assertThatThrownBy(() -> OrganizationLink.website(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("链接值不能为空");

      assertThatThrownBy(() -> OrganizationLink.website(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("链接值不能为空");

      assertThatThrownBy(() -> OrganizationLink.website("  "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("链接值不能为空");
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("isWebsite() 应正确识别官方网站")
    void shouldIdentifyWebsite() {
      OrganizationLink website = OrganizationLink.website("https://www.harvard.edu");
      OrganizationLink wikipedia = OrganizationLink.wikipedia("https://en.wikipedia.org/wiki/X");

      assertThat(website.isWebsite()).isTrue();
      assertThat(wikipedia.isWebsite()).isFalse();
    }

    @Test
    @DisplayName("isWikipedia() 应正确识别 Wikipedia 链接")
    void shouldIdentifyWikipedia() {
      OrganizationLink wikipedia = OrganizationLink.wikipedia("https://en.wikipedia.org/wiki/X");
      OrganizationLink website = OrganizationLink.website("https://www.harvard.edu");

      assertThat(wikipedia.isWikipedia()).isTrue();
      assertThat(website.isWikipedia()).isFalse();
    }

    @Test
    @DisplayName("isExternal() 应正确识别外部链接")
    void shouldIdentifyExternal() {
      OrganizationLink wikipedia = OrganizationLink.wikipedia("https://en.wikipedia.org/wiki/X");
      OrganizationLink website = OrganizationLink.website("https://www.harvard.edu");

      assertThat(wikipedia.isExternal()).isTrue();
      assertThat(website.isExternal()).isFalse();
    }
  }

  @Nested
  @DisplayName("相等性测试")
  class EqualityTest {

    @Test
    @DisplayName("相同类型和值的链接应相等")
    void shouldBeEqualWhenTypeAndValueSame() {
      OrganizationLink link1 = OrganizationLink.website("https://www.harvard.edu");
      OrganizationLink link2 = OrganizationLink.website("https://www.harvard.edu");

      assertThat(link1).isEqualTo(link2);
      assertThat(link1.hashCode()).isEqualTo(link2.hashCode());
    }

    @Test
    @DisplayName("不同类型的链接应不相等")
    void shouldNotBeEqualWhenTypeDifferent() {
      OrganizationLink link1 = OrganizationLink.website("https://www.harvard.edu");
      OrganizationLink link2 = OrganizationLink.wikipedia("https://www.harvard.edu");

      assertThat(link1).isNotEqualTo(link2);
    }

    @Test
    @DisplayName("不同值的链接应不相等")
    void shouldNotBeEqualWhenValueDifferent() {
      OrganizationLink link1 = OrganizationLink.website("https://www.harvard.edu");
      OrganizationLink link2 = OrganizationLink.website("https://www.mit.edu");

      assertThat(link1).isNotEqualTo(link2);
    }
  }
}
