package dev.patra.notification.domain.mail.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// 文献标题的内联标记转成纯文字（spec 8.3）。
class PublicationTitleTextTest {

  /// 排版标签去掉，常见实体解码。
  @Test
  void strips_formatting_tags_and_decodes_entities() {
    assertThat(PublicationTitleText.plain("Effect of <i>E. coli</i> &amp; CO<sub>2</sub>"))
        .isEqualTo("Effect of E. coli & CO2");
    assertThat(
            PublicationTitleText.plain("&lt;5 &#945;&#x3B2; &nbsp;x &quot;y&quot; &apos;z&apos;"))
        .isEqualTo("<5 αβ  x \"y\" 'z'");
  }

  /// 白名单外的标签与裸 `<` 原样留着。
  @Test
  void keeps_unknown_tags_and_bare_lt_literally() {
    assertThat(PublicationTitleText.plain("p < 0.05 <custom>x</custom>"))
        .isEqualTo("p < 0.05 <custom>x</custom>");
  }

  /// MathML 只留文字，`annotation` 连内容一起去掉。
  @Test
  void drops_math_annotation() {
    assertThat(
            PublicationTitleText.plain(
                "A <math><mi>x</mi><annotation encoding=\"TeX\">x</annotation></math> B"))
        .isEqualTo("A x B");
  }

  /// 按字素截断，截了加省略号，emoji 不切半。
  @Test
  void truncates_by_grapheme_and_adds_ellipsis() {
    assertThat(PublicationTitleText.truncate("a".repeat(65), 64)).isEqualTo("a".repeat(64) + "…");
    assertThat(PublicationTitleText.truncate("a".repeat(64), 64)).isEqualTo("a".repeat(64));
    assertThat(PublicationTitleText.truncate("ab👨‍👩‍👧c", 3)).isEqualTo("ab👨‍👩‍👧…");
  }

  /// 标题为空写「一篇文献的讨论」。
  @Test
  void blank_title_falls_back() {
    assertThat(PublicationTitleText.plain(null)).isEqualTo("一篇文献的讨论");
    assertThat(PublicationTitleText.plain("  ")).isEqualTo("一篇文献的讨论");
  }
}
