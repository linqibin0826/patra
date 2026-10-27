package dev.patra.interaction.infra.notification.excerpt;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText.Literal;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText.Mention;
import java.util.Set;
import org.junit.jupiter.api.Test;

/// 评论 Markdown 转摘录纯文本（spec 6.2 第 1 步）。
class CommonmarkPlainTextAdapterTest {

  private final CommonmarkPlainTextAdapter adapter = new CommonmarkPlainTextAdapter();

  /// 粗体、斜体、引用、列表只留文字，块之间一个空格。
  @Test
  void should_strip_emphasis_quote_and_list() {
    assertThat(literal("**粗** *斜*\n\n> 引用\n\n- 一\n- 二")).isEqualTo("粗 斜 引用 一 二");
  }

  /// 链接取文字；自动链接（DOI 网址）取原文。
  @Test
  void should_keep_link_text_and_autolink() {
    assertThat(literal("见 [这篇](https://x.org) 与 https://doi.org/10.1/abc"))
        .isEqualTo("见 这篇 与 https://doi.org/10.1/abc");
  }

  /// 行内代码取内容，图片取替代文字。
  @Test
  void should_keep_inline_code_content_and_image_alt() {
    assertThat(literal("`a*b` ![图](u.png)")).isEqualTo("a*b 图");
  }

  /// HTML 不渲染，原样当文字。
  @Test
  void should_keep_inline_html_as_text() {
    assertThat(literal("<b>x</b>")).isEqualTo("<b>x</b>");
  }

  /// 连续空白、换行、制表符合成一个空格，去掉首尾空白。
  @Test
  void should_collapse_whitespace() {
    assertThat(literal("  a  \n\n\n  b\tc  ")).isEqualTo("a b c");
  }

  /// 普通文字里、名单里的 `<@ID>` 是提及。
  @Test
  void should_turn_listed_token_into_mention() {
    ExcerptText text = adapter.toExcerpt("问 <@42> 一下", Set.of(42L));

    assertThat(text.segments())
        .containsExactly(new Literal("问 "), new Mention(42), new Literal(" 一下"));
  }

  /// 名单外的、行内代码里的 `<@ID>` 是字面文字。
  @Test
  void should_keep_unlisted_or_code_token_literal() {
    ExcerptText text = adapter.toExcerpt("<@7> 与 `<@42>`", Set.of(42L));

    assertThat(text.mentionIds()).isEmpty();
    assertThat(text.segments()).containsExactly(new Literal("<@7> 与 <@42>"));
  }

  /// 星号按 CommonMark 的规则判断（与门户渲染一致）：两边有空格的不是强调，原样保留；能配成对的是强调，去掉标记。
  @Test
  void should_follow_commonmark_emphasis_rules() {
    assertThat(literal("2 * 3 = 6，n * m")).isEqualTo("2 * 3 = 6，n * m");
    assertThat(literal("p*0.05 与 q*2")).isEqualTo("p0.05 与 q2");
  }

  /// 超出 long 范围的数字标记是字面文字，不崩溃（终审 Important 1）。
  @Test
  void should_keep_out_of_range_token_literal() {
    assertThat(literal("看 <@9999999999999999999> 这个")).isEqualTo("看 <@9999999999999999999> 这个");
  }

  /// 转义的 `\<@数字>` 解析后是普通文字：不在名单里就是字面文字。
  @Test
  void should_keep_escaped_unlisted_token_literal() {
    assertThat(literal("\\<@7> 是字面")).isEqualTo("<@7> 是字面");
  }

  /// 代码块、HTML 块里名单内的标记也是提及（讨论只把反引号行内代码当字面，块里的 @ 照常解析并提醒）。
  @Test
  void should_turn_listed_token_in_blocks_into_mention() {
    ExcerptText text = adapter.toExcerpt("看\n\n    问 <@42>\n\n完", Set.of(42L));

    assertThat(text.segments())
        .containsExactly(new Literal("看 问 "), new Mention(42), new Literal(" 完"));
  }

  /// 全角空格、不换行空格也合并成一个空格。
  @Test
  void should_collapse_unicode_whitespace() {
    assertThat(literal("a\u3000\u3000b\u00a0c")).isEqualTo("a b c");
  }

  /// 没有提及时的纯文本。
  ///
  /// @param markdown 正文
  /// @return 唯一一段字面文字
  private String literal(String markdown) {
    ExcerptText text = adapter.toExcerpt(markdown, Set.of());
    assertThat(text.segments()).hasSize(1);
    return ((Literal) text.segments().getFirst()).text();
  }
}
