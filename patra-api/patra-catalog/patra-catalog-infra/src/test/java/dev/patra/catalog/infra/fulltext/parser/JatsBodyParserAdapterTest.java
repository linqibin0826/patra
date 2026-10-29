package dev.patra.catalog.infra.fulltext.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.JatsParseResult;
import dev.patra.catalog.domain.fulltext.model.vo.body.Block;
import dev.patra.catalog.domain.fulltext.model.vo.body.Inline;
import dev.patra.catalog.domain.fulltext.model.vo.body.Reference;
import dev.patra.catalog.domain.fulltext.model.vo.body.Section;
import dev.patra.catalog.domain.fulltext.model.vo.body.TableCell;
import dev.patra.catalog.domain.kernel.exception.XmlParseException;
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

  // ========== 公式 ==========

  @Test
  @DisplayName("行间公式输出去掉命名空间前缀的 MathML，保留属性")
  void should_emit_mathml_without_namespace_prefix() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title><disp-formula id="e1"><label>(1)</label>
              <mml:math id="m1" display="block"><mml:mtable columnalign="left"><mml:mtr><mml:mtd><mml:mi>x</mml:mi><mml:mo>&lt;</mml:mo><mml:mn>1</mml:mn></mml:mtd></mml:mtr></mml:mtable></mml:math>
            </disp-formula></sec>
            """);

    Block.Formula formula = (Block.Formula) firstBlock(parser.parse(stream(xml), List.of()));

    assertThat(formula.id()).isEqualTo("e1");
    assertThat(formula.label()).isEqualTo("(1)");
    assertThat(formula.mathml())
        .startsWith("<math")
        .doesNotContain("mml:")
        .doesNotContain("xmlns")
        .contains("display=\"block\"")
        .contains("columnalign=\"left\"")
        .contains("<mo>&lt;</mo>");
    assertThat(formula.image()).isNull();
  }

  @Test
  @DisplayName("只有 TeX 的行间公式输出 code 块并计警告")
  void should_fallback_to_code_when_only_tex() {
    String xml =
        article(
            "<sec id=\"s1\"><title>T</title><disp-formula id=\"e1\"><tex-math>x_1</tex-math></disp-formula></sec>");

    JatsParseResult result = parser.parse(stream(xml), List.of());

    assertThat(firstBlock(result)).isEqualTo(new Block.Code("x_1"));
    assertThat(result.warningCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("TeX 去掉 documentclass 外壳与外层 $$")
  void should_strip_documentclass_wrapper_from_tex() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title><disp-formula id="e1"><tex-math id="t1"><![CDATA[\\documentclass[12pt]{minimal}
            \\usepackage{amsmath}
            \\begin{document}$$a^2+b^2$$\\end{document}]]></tex-math></disp-formula></sec>
            """);

    assertThat(firstBlock(parser.parse(stream(xml), List.of())))
        .isEqualTo(new Block.Code("a^2+b^2"));
  }

  @Test
  @DisplayName("段落与单元格里直接写的 MathML 都输出为行内公式")
  void should_emit_math_inline_for_bare_mathml_in_paragraph_and_cell() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title>
              <p>let <mml:math><mml:mi>y</mml:mi></mml:math> be</p>
              <table-wrap id="t1"><table><tbody><tr><td><mml:math><mml:mn>2</mml:mn></mml:math></td></tr></tbody></table></table-wrap>
            </sec>
            """);

    List<Block> blocks = parser.parse(stream(xml), List.of()).body().sections().getFirst().blocks();

    assertThat(((Block.Paragraph) blocks.get(0)).content())
        .contains(new Inline.MathInline("<math><mi>y</mi></math>"));
    assertThat(((Block.Table) blocks.get(1)).body().getFirst().getFirst().content())
        .containsExactly(new Inline.MathInline("<math><mn>2</mn></math>"));
  }

  @Test
  @DisplayName("alternatives 里优先取 MathML，其次 TeX，最后公式图片")
  void should_prefer_mathml_inside_alternatives() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title>
              <disp-formula id="e1"><alternatives><tex-math>x</tex-math><mml:math><mml:mi>x</mml:mi></mml:math><graphic xlink:href="e1.gif"/></alternatives></disp-formula>
              <p>in <inline-formula><alternatives><tex-math>z</tex-math><mml:math><mml:mi>z</mml:mi></mml:math></alternatives></inline-formula></p>
            </sec>
            """);

    List<Block> blocks = parser.parse(stream(xml), List.of()).body().sections().getFirst().blocks();

    assertThat(((Block.Formula) blocks.get(0)).mathml()).isEqualTo("<math><mi>x</mi></math>");
    assertThat(((Block.Paragraph) blocks.get(1)).content())
        .contains(new Inline.MathInline("<math><mi>z</mi></math>"));
  }

  @Test
  @DisplayName("只有公式图片时输出带图片的公式块并计警告")
  void should_use_formula_image_when_only_graphic() {
    String xml =
        article(
            "<sec id=\"s1\"><title>T</title><disp-formula id=\"e1\"><graphic xlink:href=\"pcbi.e001\"/></disp-formula></sec>");

    JatsParseResult result =
        parser.parse(stream(xml), List.of(FullTextMedia.of("pcbi.e001.jpg", 120, 30)));

    assertThat(firstBlock(result))
        .isEqualTo(
            Block.Formula.builder().id("e1").image("pcbi.e001.jpg").width(120).height(30).build());
    assertThat(result.warningCount()).isEqualTo(1);
  }

  // ========== 参考文献与 back ==========

  @Test
  @DisplayName("mixed-citation 原样转换，element-citation 按作者、标题、期刊、年卷页拼接，取 PMID 与 DOI")
  void should_parse_mixed_and_element_citations_with_pmid_doi() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title><p>x</p></sec>
            <back><ref-list>
              <ref id="B1"><label>1</label><mixed-citation publication-type="journal">Smith J. <italic>Title one</italic>. Nature. 2020;1:2. <pub-id pub-id-type="pmid">111</pub-id></mixed-citation></ref>
              <ref id="B2"><element-citation publication-type="journal">
                <person-group person-group-type="author"><name><surname>Doe</surname><given-names>JA</given-names></name><name><surname>Roe</surname><given-names>B</given-names></name><etal/></person-group>
                <article-title>Title two</article-title><source>Cell</source><year>2021</year><volume>5</volume><fpage>10</fpage><lpage>20</lpage>
                <pub-id pub-id-type="doi">10.1/two</pub-id></element-citation></ref>
            </ref-list></back>
            """);

    List<Reference> refs = parser.parse(stream(xml), List.of()).body().references();

    assertThat(refs).hasSize(2);
    assertThat(refs.get(0).id()).isEqualTo("B1");
    assertThat(refs.get(0).label()).isEqualTo("1");
    assertThat(refs.get(0).pmid()).isEqualTo("111");
    assertThat(refs.get(0).content())
        .contains(new Inline.Styled("italic", List.of(new Inline.Text("Title one"))));
    assertThat(text(refs.get(1).content()))
        .isEqualTo("Doe JA, Roe B, et al. Title two. Cell. 2021;5:10-20.");
    assertThat(refs.get(1).doi()).isEqualTo("10.1/two");
  }

  @Test
  @DisplayName("element-citation 的 string-name 子元素之间没有空白时补空格")
  void should_separate_string_name_parts_in_element_citation() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title><p>x</p></sec>
            <back><ref-list><ref id="R1"><element-citation>
              <person-group><string-name><surname>Lee</surname><given-names>K</given-names></string-name></person-group>
              <article-title>Title</article-title>
            </element-citation></ref></ref-list></back>
            """);

    Reference ref = parser.parse(stream(xml), List.of()).body().references().getFirst();

    assertThat(text(ref.content())).isEqualTo("Lee K. Title.");
  }

  @Test
  @DisplayName("citation-alternatives 优先取 mixed-citation，PMID / DOI 从另一种里补")
  void should_read_citation_alternatives() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title><p>x</p></sec>
            <back><ref-list><ref id="R1"><citation-alternatives>
              <element-citation><article-title>E</article-title><pub-id pub-id-type="doi">10.9/alt</pub-id></element-citation>
              <mixed-citation>Mixed text.</mixed-citation>
            </citation-alternatives></ref></ref-list></back>
            """);

    Reference ref = parser.parse(stream(xml), List.of()).body().references().getFirst();

    assertThat(text(ref.content())).isEqualTo("Mixed text.");
    assertThat(ref.doi()).isEqualTo("10.9/alt");
  }

  @Test
  @DisplayName("致谢、脚注组、数据可用性节进 back，脚注带 id")
  void should_put_ack_and_footnotes_into_back() {
    String xml =
        article(
            """
            <sec id="s1"><title>T</title><p>see<xref ref-type="fn" rid="fn1">a</xref></p></sec>
            <back>
              <ack><title>Acknowledgements</title><p>Thanks.</p></ack>
              <fn-group><fn id="fn1"><label>a</label><p>Note.</p></fn></fn-group>
              <sec id="das"><title>Data availability</title><p>Open.</p></sec>
              <ref-list/>
            </back>
            """);

    JatsParseResult result = parser.parse(stream(xml), List.of());

    List<Section> back = result.body().back();
    assertThat(back)
        .extracting(s -> text(s.title()))
        .containsExactly("Acknowledgements", "", "Data availability");
    assertThat(back.get(1).sections())
        .singleElement()
        .satisfies(
            fn -> {
              assertThat(fn.id()).isEqualTo("fn1");
              assertThat(text(fn.title())).isEqualTo("a");
            });
    assertThat(paragraph(result, 0).content()).anyMatch(i -> i instanceof Inline.Xref);
  }

  // ========== 字数 ==========

  @Test
  @DisplayName("字数计章节标题、段落、表题，不计参考文献、公式、表格单元格与代码")
  void should_count_words_excluding_references_math_table_cells_and_code() {
    String xml =
        article(
            """
            <sec id="s1"><title>Methods</title>
              <p>one two three</p>
              <disp-formula id="e1"><mml:math><mml:mi>alpha</mml:mi></mml:math></disp-formula>
              <p><mml:math><mml:mi>beta</mml:mi></mml:math></p>
              <table-wrap id="t1"><caption><title>Cap two</title></caption><table><tbody><tr><td>a b c d e</td><td>f g h i j</td></tr></tbody></table></table-wrap>
              <preformat>code words here</preformat>
            </sec>
            <back><ref-list><ref id="R1"><mixed-citation>one two three four five six seven eight nine ten</mixed-citation></ref></ref-list></back>
            """);

    assertThat(parser.parse(stream(xml), List.of()).body().wordCount()).isEqualTo(6);
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

  private static Block firstBlock(JatsParseResult result) {
    return result.body().sections().getFirst().blocks().getFirst();
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
