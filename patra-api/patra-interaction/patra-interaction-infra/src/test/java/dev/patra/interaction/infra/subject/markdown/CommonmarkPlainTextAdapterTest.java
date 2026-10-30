package dev.patra.interaction.infra.subject.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/// 正文 Markdown 转纯文本（PAP-128 spec 第 3、4 节）：每种元素一组用例。
class CommonmarkPlainTextAdapterTest {

  /// 断开同形文字用的零宽连接符。
  private static final String WJ = "⁠";

  private final CommonmarkPlainTextAdapter adapter = new CommonmarkPlainTextAdapter();

  /// 名单里的 `<@ID>` 原样保留成提及，不换成名字。
  @Test
  void should_keep_listed_mention() {
    assertThat(m("问 <@42> 一下")).isEqualTo("问 <@42> 一下");
  }

  /// 不在名单里的 `<@ID>` 被断开：`<` 与 `@` 之间插 U+2060，看起来一样，但认不成提及。
  @Test
  void should_break_unlisted_token() {
    assertThat(m("<@7> 看")).isEqualTo("<" + WJ + "@7> 看");
  }

  /// 行内代码里的 `<@ID>` 即使在名单里也是字面文字（门户不当提及），被断开。
  @Test
  void should_break_token_in_inline_code() {
    assertThat(m("`<@42>`")).isEqualTo("<" + WJ + "@42>");
  }

  /// 整条只有一个提及：去首尾空白不吃掉标记。
  @Test
  void should_keep_lone_mention() {
    assertThat(m("<@42>")).isEqualTo("<@42>");
  }

  /// 转义的 `\<@ID>` 是字面文字（门户先处理转义），即使 ID 在名单里。
  @Test
  void should_break_escaped_token() {
    assertThat(m("\\<@42>")).isEqualTo("<" + WJ + "@42>");
  }

  /// 超出 long 范围的 19 位数也匹配提及正则，同样断开，消费方不会拿去转 long。
  @Test
  void should_break_out_of_range_token() {
    assertThat(p("<@9999999999999999999>")).isEqualTo("<" + WJ + "@9999999999999999999>");
  }

  /// 拆在几个节点里、拼起来像提及的文字也断开。
  @Test
  void should_break_token_assembled_across_nodes() {
    assertThat(p("<@*5*>")).isEqualTo("<" + WJ + "@5>");
  }

  /// 同形文字紧挨着真提及：只断开字面的那个。
  @Test
  void should_break_lookalike_next_to_real_mention() {
    assertThat(adapter.toPlainText("`<@5>`<@5>", Set.of(5L)).value())
        .isEqualTo("<" + WJ + "@5><@5>");
  }

  /// 投稿经验不解析 @（名单为空）：正文里的 `<@ID>` 一律断开。
  @Test
  void should_break_every_token_in_post_body() {
    assertThat(p("见 <@42>")).isEqualTo("见 <" + WJ + "@42>");
  }

  /// PMID、DOI、网址原样，一个字不改（门户只是把它们做成链接）。
  @Test
  void should_keep_pmid_doi_and_url_as_typed() {
    String body = "PMID: 27940189，doi:10.1038/nature12373，https://doi.org/10.1/abc";
    assertThat(p(body)).isEqualTo(body);
  }

  /// 链接只留文字。
  @Test
  void should_keep_link_text_only() {
    assertThat(p("见 [这篇](https://x.org)")).isEqualTo("见 这篇");
  }

  /// 粗体、斜体只留文字；配不成对的星号原样（与门户按同一规范判断）。
  @Test
  void should_strip_emphasis_but_keep_unpaired_stars() {
    assertThat(p("**粗** *斜*")).isEqualTo("粗 斜");
    assertThat(p("2 * 3 = 6，n * m")).isEqualTo("2 * 3 = 6，n * m");
    assertThat(p("p*0.05 与 q*2")).isEqualTo("p0.05 与 q2");
  }

  /// 尖括号网址：门户关掉了这个写法，显示原文，纯文本也给原文。
  @Test
  void should_keep_angle_bracket_url_as_source() {
    assertThat(p("<https://x.org>")).isEqualTo("<https://x.org>");
  }

  /// 软换行、硬换行（行尾两空格、行尾反斜杠）、段落之间都变成 `\n`，不留空行。
  @Test
  void should_turn_breaks_and_paragraphs_into_newlines() {
    assertThat(p("一\n二  \n三\\\n四\n\n五")).isEqualTo("一\n二\n三\n四\n五");
  }

  /// 每行里连续的空白（含制表符、全角空格、不换行空格）合成一个空格；行首尾空白、空行去掉。
  @Test
  void should_collapse_whitespace_and_drop_blank_lines() {
    assertThat(p("  a  \t b　　c d  \n\n\n  e  ")).isEqualTo("a b c d\ne");
  }

  /// 空正文、只有空白：空串。
  @Test
  void should_return_empty_for_blank() {
    assertThat(p("")).isEmpty();
    assertThat(p("  \n ")).isEmpty();
  }

  /// 标题（两种写法）只留文字，各占一行（门户降成粗体段落）。
  @Test
  void should_keep_heading_text_on_its_own_line() {
    assertThat(p("# 结论\n## 方法\n正文")).isEqualTo("结论\n方法\n正文");
    assertThat(p("结论\n---")).isEqualTo("结论");
  }

  /// 分隔线：门户关掉了，显示原文。
  @Test
  void should_keep_thematic_break_as_text() {
    assertThat(p("上\n\n***\n\n下")).isEqualTo("上\n***\n下");
  }

  /// HTML：门户不渲染，显示原文；行内与块都一样。
  @Test
  void should_keep_html_as_text() {
    assertThat(p("<b>x</b>")).isEqualTo("<b>x</b>");
    assertThat(p("<div>\nx\n</div>")).isEqualTo("<div>\nx\n</div>");
  }

  /// 缩进四格的行：门户关掉了缩进代码块，是普通段落，里面的提及照常。
  @Test
  void should_treat_indented_lines_as_text() {
    assertThat(m("看\n\n    问 <@42>\n\n完")).isEqualTo("看\n问 <@42>\n完");
    assertThat(p("段落\n    续行")).isEqualTo("段落\n续行");
  }

  /// 图片：门户不加载图片，显示原文；整条只有图片时也不为空。
  @Test
  void should_keep_image_as_source() {
    assertThat(p("图 ![说明](u.png)")).isEqualTo("图 ![说明](u.png)");
    assertThat(p("![说明](u.png)")).isEqualTo("![说明](u.png)");
  }

  /// 单行的行内代码只留代码文字。
  @Test
  void should_keep_inline_code_content() {
    assertThat(p("`a*b` 与 ```x```")).isEqualTo("a*b 与 x");
  }

  /// 跨行的三反引号：门户关掉了围栏代码块，按原文逐行显示，纯文本也给原文。
  @Test
  void should_keep_multiline_fence_as_source() {
    assertThat(p("```java\nint a;\n```")).isEqualTo("```java\nint a;\n```");
  }

  /// 引用式链接与链接定义：门户关掉了链接定义，两处都显示原文。
  @Test
  void should_keep_reference_link_and_definition_as_source() {
    assertThat(p("看 [x]\n\n[x]: https://y.org")).isEqualTo("看 [x]\n[x]: https://y.org");
  }

  /// 无序列表：每项一行，前缀 `• `；提及照常。
  @Test
  void should_prefix_bullet_items() {
    assertThat(m("- 一\n- 问 <@42>")).isEqualTo("• 一\n• 问 <@42>");
  }

  /// 有序列表：每项一行，前缀「编号. 」，从列表的起始编号数起（门户的 `ol start`）。
  @Test
  void should_number_ordered_items_from_start() {
    assertThat(p("3. 甲\n4. 乙\n9. 丙")).isEqualTo("3. 甲\n4. 乙\n5. 丙");
    assertThat(p("1) 一")).isEqualTo("1. 一");
  }

  /// 列表项里套的列表：门户不支持嵌套，按原文显示，纯文本也给原文。
  @Test
  void should_keep_nested_list_as_source() {
    assertThat(p("- a\n  - b\n  - c")).isEqualTo("• a\n- b\n- c");
  }

  /// 列表项里缩进很深的行也是普通文字。
  @Test
  void should_treat_indented_lines_in_list_item_as_text() {
    assertThat(p("- a\n\n      b")).isEqualTo("• a\nb");
  }

  /// 引用块：保留内容，外面加中文引号，分得清哪句是引用；多行留在一对引号里，多层各加一对，空引用省略。
  @Test
  void should_wrap_quote_in_quotation_marks() {
    assertThat(p("> 文中说 OR=1.8\n\n我觉得不够")).isEqualTo("“文中说 OR=1.8”\n我觉得不够");
    assertThat(p("> 一\n> 二")).isEqualTo("“一\n二”");
    assertThat(p("> 外\n>> 内")).isEqualTo("“外\n“内””");
    assertThat(p(">\n\n正文")).isEqualTo("正文");
  }

  /// 引用块里的图片与提及：源位置不含开头的 `>`，提及照常。
  @Test
  void should_handle_image_and_mention_inside_quote() {
    assertThat(m("> 看 ![图](u.png)\n> 问 <@42>")).isEqualTo("“看 ![图](u.png)\n问 <@42>”");
  }

  /// 转义与 HTML 实体按门户显示的字输出；实体拼出来的 `<@ID>` 也是同形文字，被断开。
  @Test
  void should_resolve_escapes_and_entities() {
    assertThat(p("\\*不是强调\\* &amp; &lt;b&gt;")).isEqualTo("*不是强调* & <b>");
    assertThat(m("&lt;@42&gt;")).isEqualTo("<" + WJ + "@42>");
  }

  /// 评论：提及名单里只有 42。
  ///
  /// @param markdown 正文
  /// @return 纯文本
  private String m(String markdown) {
    return adapter.toPlainText(markdown, Set.of(42L)).value();
  }

  /// 投稿经验：不解析 @，名单为空。
  ///
  /// @param markdown 正文
  /// @return 纯文本
  private String p(String markdown) {
    return adapter.toPlainText(markdown, Set.of()).value();
  }
}
