package dev.patra.catalog.infra.parser.jats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.exception.XmlParseException;
import dev.patra.catalog.domain.model.vo.fulltext.FullTextMedia;
import dev.patra.catalog.domain.model.vo.fulltext.JatsParseResult;
import dev.patra.catalog.domain.model.vo.fulltext.body.Block;
import dev.patra.catalog.domain.model.vo.fulltext.body.Inline;
import dev.patra.catalog.domain.model.vo.fulltext.body.Section;
import dev.patra.catalog.domain.model.vo.fulltext.body.TableCell;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `JatsBodyParserAdapter` 单元测试：章节、块、行内节点。
@DisplayName("JatsBodyParserAdapter")
class JatsBodyParserAdapterTest {

  private final JatsBodyParserAdapter parser = new JatsBodyParserAdapter();

  @Test
  @DisplayName("解析多级章节，所有章节都有非空 id，补充材料节整节跳过")
  void should_parse_nested_sections_with_ids() {
    JatsParseResult result = parser.parse(fixture("PMC12754789.xml"), List.of());

    // 第 5 个顶层 sec 是 sec-type="supplementary-material"，整节不出现
    List<Section> sections = result.body().sections();
    assertThat(sections).hasSize(4);
    assertThat(sections).noneMatch(s -> text(s.title()).startsWith("Supplementary"));
    assertThat(text(sections.getFirst().title())).isEqualTo("Introduction");
    assertThat(sections.getFirst().sections()).hasSize(2);
    assertThat(allSections(sections)).allSatisfy(s -> assertThat(s.id()).isNotBlank());
  }

  @Test
  @DisplayName("图带图注、文件名与清单里的宽高")
  void should_parse_figure_with_caption_and_image() {
    JatsParseResult result =
        parser.parse(
            fixture("PMC13583816.xml"),
            List.of(FullTextMedia.of("bmjopen-16-9-g001.webp", 1600, 2159)));

    Block.Figure figure = figures(result).getFirst();
    assertThat(figure.id()).isEqualTo("F1");
    assertThat(figure.label()).isEqualTo("Figure 1");
    assertThat(figure.caption()).isNotEmpty();
    assertThat(figure.image()).isEqualTo("bmjopen-16-9-g001.webp");
    assertThat(figure.width()).isEqualTo(1600);
    assertThat(figure.height()).isEqualTo(2159);
  }

  @Test
  @DisplayName("href 不带扩展名时按去扩展名同名匹配；找不到则图片为空并计警告")
  void should_match_image_without_extension() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title>
              <fig id="f1"><label>Figure 1</label><graphic xlink:href="fig-a"/></fig>
              <fig id="f2"><label>Figure 2</label><graphic xlink:href="fig-missing"/></fig>
            </sec>
            """);

    JatsParseResult result =
        parser.parse(stream(xml), List.of(FullTextMedia.of("fig-a.webp", 3, 2)));

    List<Block.Figure> figures = figures(result);
    assertThat(figures.get(0).image()).isEqualTo("fig-a.webp");
    assertThat(figures.get(0).width()).isEqualTo(3);
    assertThat(figures.get(1).image()).isNull();
    assertThat(figures.get(1).width()).isNull();
    assertThat(result.warningCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("表格保留合并单元格与表头单元格")
  void should_parse_table_with_colspan_and_rowspan() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title>
              <table-wrap id="t1"><label>Table 1</label><caption><title>Cap</title></caption>
                <table>
                  <thead><tr><th colspan="2">H</th></tr></thead>
                  <tbody><tr><td rowspan="2">a</td><td>b</td></tr><tr><td>c</td></tr></tbody>
                </table>
                <table-wrap-foot><fn id="tf1"><p>note</p></fn></table-wrap-foot>
              </table-wrap>
            </sec>
            """);

    Block.Table table =
        (Block.Table)
            parser.parse(stream(xml), List.of()).body().sections().getFirst().blocks().getFirst();

    assertThat(table.label()).isEqualTo("Table 1");
    assertThat(table.head())
        .singleElement()
        .satisfies(
            row ->
                assertThat(row)
                    .singleElement()
                    .satisfies(
                        cell -> {
                          assertThat(cell.colspan()).isEqualTo(2);
                          assertThat(cell.header()).isTrue();
                        }));
    assertThat(table.body()).hasSize(2);
    TableCell first = table.body().getFirst().getFirst();
    assertThat(first.rowspan()).isEqualTo(2);
    assertThat(text(first.content())).isEqualTo("a");
    assertThat(table.footnotes()).hasSize(1);
  }

  @Test
  @DisplayName("段落里嵌着公式与列表时在两侧切开，不输出空段")
  void should_split_paragraph_around_nested_block() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title>
              <p>before <disp-formula id="e1"><label>(1)</label><mml:math><mml:mi>x</mml:mi></mml:math></disp-formula> after <list list-type="bullet"><list-item><p>i1</p></list-item></list></p>
            </sec>
            """);

    List<Block> blocks = parser.parse(stream(xml), List.of()).body().sections().getFirst().blocks();

    assertThat(blocks)
        .hasExactlyElementsOfTypes(
            Block.Paragraph.class,
            Block.Formula.class,
            Block.Paragraph.class,
            Block.ListBlock.class);
    assertThat(text(((Block.Paragraph) blocks.get(0)).content())).isEqualTo("before");
    assertThat(text(((Block.Paragraph) blocks.get(2)).content())).isEqualTo("after");
    assertThat(((Block.ListBlock) blocks.get(3)).ordered()).isFalse();
  }

  @Test
  @DisplayName("第一个 sec 之前的内容进顶层 blocks，sec 之后的直接内容进无标题章节")
  void should_put_blocks_before_first_section_into_top_level_blocks() {
    String xml =
        article(
            """
            <boxed-text id="bx1"><caption><title>Strengths</title></caption><list><list-item><p>a</p></list-item></list></boxed-text>
            <sec id="s1"><title>Intro</title><p>x</p></sec>
            <p>trailing</p>
            """);

    JatsParseResult result = parser.parse(stream(xml), List.of());

    assertThat(result.body().blocks())
        .singleElement()
        .isInstanceOfSatisfying(
            Block.Box.class,
            box -> {
              assertThat(box.id()).isEqualTo("bx1");
              assertThat(text(box.title())).isEqualTo("Strengths");
              assertThat(box.blocks()).hasSize(1);
            });
    assertThat(result.body().sections()).hasSize(2);
    Section trailing = result.body().sections().get(1);
    assertThat(trailing.title()).isEmpty();
    assertThat(trailing.id()).isNotBlank();
  }

  @Test
  @DisplayName("只取 article 直接子元素的 body，sub-article 里的不取")
  void should_ignore_sub_article_body() {
    String xml =
        """
        <article xmlns:xlink="http://www.w3.org/1999/xlink"><body><sec id="s1"><title>A</title><p>a</p></sec></body>
        <sub-article><body><sec id="s2"><title>B</title><p>b</p></sec></body></sub-article></article>
        """;

    assertThat(parser.parse(stream(xml), List.of()).body().sections())
        .extracting(s -> text(s.title()))
        .containsExactly("A");
  }

  @Test
  @DisplayName("白名单外的引用类型只保留子节点并计警告")
  void should_degrade_unknown_xref_type_to_children_with_warning() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title><p>see <xref ref-type="supplementary-material" rid="sup1">S1</xref></p></sec>
            """);

    JatsParseResult result = parser.parse(stream(xml), List.of());

    List<Inline> content = paragraph(result, 0).content();
    assertThat(content).noneMatch(i -> i instanceof Inline.Xref);
    assertThat(text(content)).isEqualTo("see S1");
    assertThat(result.warningCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("行内样式与引用：多个 rid 拆成 targets")
  void should_parse_inline_styles_and_xref() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title>
              <p><italic>it</italic> x<sup>2</sup> <xref ref-type="bibr" rid="B1 B2">1,2</xref></p>
            </sec>
            <back><ref-list><ref id="B1"><mixed-citation>r1</mixed-citation></ref><ref id="B2"><mixed-citation>r2</mixed-citation></ref></ref-list></back>
            """);

    List<Inline> content = paragraph(parser.parse(stream(xml), List.of()), 0).content();

    assertThat(content.get(0))
        .isEqualTo(new Inline.Styled("italic", List.of(new Inline.Text("it"))));
    assertThat(content).contains(new Inline.Styled("sup", List.of(new Inline.Text("2"))));
    assertThat(content)
        .filteredOn(i -> i instanceof Inline.Xref)
        .singleElement()
        .isEqualTo(new Inline.Xref("bibr", List.of("B1", "B2"), List.of(new Inline.Text("1,2"))));
  }

  @Test
  @DisplayName("只保留 http / https / doi 外链，其他降为纯文本")
  void should_keep_only_http_https_doi_links() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title>
              <p><ext-link ext-link-type="uri" xlink:href="https://example.org">web</ext-link> <ext-link ext-link-type="doi" xlink:href="10.1000/x">doi</ext-link> <ext-link ext-link-type="ftp" xlink:href="ftp://example.org/f">ftp</ext-link></p>
            </sec>
            """);

    List<Inline> content = paragraph(parser.parse(stream(xml), List.of()), 0).content();

    assertThat(content)
        .filteredOn(i -> i instanceof Inline.Link)
        .extracting(i -> ((Inline.Link) i).href())
        .containsExactly("https://example.org", "https://doi.org/10.1000/x");
    assertThat(text(content)).endsWith("ftp");
  }

  @Test
  @DisplayName("表格单元格里的列表降为行内内容，项之间换行")
  void should_degrade_list_in_table_cell_to_inline_breaks() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title><table-wrap id="t1"><table><tbody><tr>
              <td><list><list-item><p>a</p></list-item><list-item><p>b</p></list-item></list></td>
            </tr></tbody></table></table-wrap></sec>
            """);

    Block.Table table =
        (Block.Table)
            parser.parse(stream(xml), List.of()).body().sections().getFirst().blocks().getFirst();

    assertThat(table.body().getFirst().getFirst().content())
        .containsExactly(new Inline.Text("a"), new Inline.Break(), new Inline.Text("b"));
  }

  @Test
  @DisplayName("不认识的块级元素取纯文本成段落并计警告")
  void should_turn_unknown_block_into_paragraph_with_warning() {
    String xml =
        article("<sec id=\"s1\"><title>T</title><mystery><x>hello</x> world</mystery></sec>");

    JatsParseResult result = parser.parse(stream(xml), List.of());

    assertThat(text(paragraph(result, 0).content())).isEqualTo("hello world");
    assertThat(result.warningCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("跳过补充材料，整节都是补充材料时整节不出现")
  void should_skip_supplementary_material() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title><p>x</p><supplementary-material id="sm1"><label>S1</label></supplementary-material></sec>
            <sec id="s2" sec-type="supplementary-material"><title>Supplementary Material</title><supplementary-material id="sm2"/></sec>
            """);

    JatsParseResult result = parser.parse(stream(xml), List.of());

    assertThat(result.body().sections())
        .singleElement()
        .satisfies(s -> assertThat(s.blocks()).hasSize(1));
  }

  @Test
  @DisplayName("没有 body 时报告无正文")
  void should_report_no_body_when_body_absent() {
    assertThat(parser.parse(fixture("no-body-synthetic.xml"), List.of()).noBody()).isTrue();
    assertThat(parser.parse(stream("<article><body>  </body></article>"), List.of()).noBody())
        .isTrue();
  }

  @Test
  @DisplayName("XML 不合法时抛 XmlParseException")
  void should_reject_malformed_xml() {
    assertThatThrownBy(() -> parser.parse(stream("<article><body>"), List.of()))
        .isInstanceOf(XmlParseException.class);
  }

  // ========== 辅助方法 ==========

  private static String article(String bodyAndBack) {
    String body = bodyAndBack;
    String back = "";
    int backAt = bodyAndBack.indexOf("<back>");
    if (backAt >= 0) {
      body = bodyAndBack.substring(0, backAt);
      back = bodyAndBack.substring(backAt);
    }
    return "<article xmlns:xlink=\"http://www.w3.org/1999/xlink\""
        + " xmlns:mml=\"http://www.w3.org/1998/Math/MathML\"><body>"
        + body
        + "</body>"
        + back
        + "</article>";
  }

  private InputStream fixture(String name) {
    return getClass().getResourceAsStream("/jats/" + name);
  }

  private static InputStream stream(String xml) {
    return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
  }

  private static Block.Paragraph paragraph(JatsParseResult result, int index) {
    return (Block.Paragraph) result.body().sections().getFirst().blocks().get(index);
  }

  private static List<Section> allSections(List<Section> sections) {
    List<Section> all = new ArrayList<>();
    for (Section s : sections) {
      all.add(s);
      all.addAll(allSections(s.sections()));
    }
    return all;
  }

  private static List<Block.Figure> figures(JatsParseResult result) {
    return allSections(result.body().sections()).stream()
        .flatMap(s -> s.blocks().stream())
        .flatMap(JatsBodyParserAdapterTest::flatten)
        .filter(b -> b instanceof Block.Figure)
        .map(b -> (Block.Figure) b)
        .toList();
  }

  private static Stream<Block> flatten(Block block) {
    return switch (block) {
      case Block.Box box ->
          Stream.concat(
              Stream.of(block), box.blocks().stream().flatMap(JatsBodyParserAdapterTest::flatten));
      case Block.Quote quote ->
          Stream.concat(
              Stream.of(block),
              quote.blocks().stream().flatMap(JatsBodyParserAdapterTest::flatten));
      default -> Stream.of(block);
    };
  }

  static String text(List<Inline> inlines) {
    StringBuilder sb = new StringBuilder();
    for (Inline inline : inlines) {
      switch (inline) {
        case Inline.Text t -> sb.append(t.text());
        case Inline.Styled s -> sb.append(text(s.children()));
        case Inline.Xref x -> sb.append(text(x.children()));
        case Inline.Link l -> sb.append(text(l.children()));
        case Inline.MathInline m -> sb.append("[math]");
        case Inline.Break b -> sb.append("\n");
      }
    }
    return sb.toString();
  }
}
