package dev.patra.catalog.domain.fulltext.model.vo.body;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 正文结构的 JSON 形状测试：`kind` 取值与工程 spec 第 6.2 节一致，并能往返。
@DisplayName("FullTextBody JSON")
class FullTextBodyJsonTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  @DisplayName("每种块与行内节点的 kind 与 spec 一致，序列化后能原样读回")
  void should_serialize_kinds_and_round_trip() {
    List<Inline> inlines =
        List.of(
            new Inline.Text("a"),
            new Inline.Styled("italic", List.of(new Inline.Text("b"))),
            new Inline.Xref("bibr", List.of("B1", "B2"), List.of(new Inline.Text("1"))),
            new Inline.Link("https://doi.org/10.1/x", List.of(new Inline.Text("doi"))),
            new Inline.MathInline("<math><mi>x</mi></math>"),
            new Inline.Break());
    List<Block> blocks =
        List.of(
            new Block.Paragraph(inlines),
            Block.Figure.builder()
                .id("F1")
                .label("Figure 1")
                .caption(List.of(new Block.Paragraph(List.of(new Inline.Text("cap")))))
                .image("g1.webp")
                .width(1600)
                .height(2159)
                .build(),
            Block.Table.builder()
                .id("T1")
                .label("Table 1")
                .caption(List.of())
                .head(List.of(List.of(new TableCell(List.of(new Inline.Text("h")), 2, 1, true))))
                .body(List.of(List.of(new TableCell(List.of(new Inline.Text("v")), 1, 1, false))))
                .footnotes(List.of())
                .build(),
            Block.Formula.builder().id("E1").label("(1)").mathml("<math/>").build(),
            new Block.ListBlock(true, List.of(List.of(new Block.Paragraph(List.of())))),
            new Block.Quote(List.of()),
            new Block.Box("bx1", List.of(new Inline.Text("Box")), List.of()),
            new Block.Code("x_1"));
    FullTextBody body =
        FullTextBody.builder()
            .schemaVersion(FullTextBody.SCHEMA_VERSION)
            .blocks(List.of(new Block.Paragraph(List.of(new Inline.Text("top")))))
            .sections(
                List.of(new Section("s1", List.of(new Inline.Text("Intro")), List.of(), blocks)))
            .back(List.of())
            .references(
                List.of(
                    Reference.builder()
                        .id("B1")
                        .label("1")
                        .content(List.of(new Inline.Text("Ref")))
                        .pmid("123")
                        .doi("10.1/x")
                        .build()))
            .wordCount(42)
            .build();

    String json = mapper.writeValueAsString(body);
    JsonNode tree = mapper.readTree(json);

    JsonNode sectionBlocks = tree.path("sections").get(0).path("blocks");
    assertThat(sectionBlocks.findValuesAsString("kind"))
        .contains("paragraph", "figure", "table", "formula", "list", "quote", "box", "code");
    assertThat(sectionBlocks.get(0).path("content").findValuesAsString("kind"))
        .containsSubsequence("text", "style", "xref", "link", "math", "break");
    assertThat(json).doesNotContain("\"kind\":\"paragraph\",\"kind\"");
    assertThat(tree.path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(mapper.readValue(json, FullTextBody.class)).isEqualTo(body);
  }

  @Test
  @DisplayName("集合字段为 null 时视为空列表")
  void should_default_null_collections_to_empty() {
    FullTextBody body = FullTextBody.builder().schemaVersion(1).build();

    assertThat(body.blocks()).isEmpty();
    assertThat(body.sections()).isEmpty();
    assertThat(body.back()).isEmpty();
    assertThat(body.references()).isEmpty();
  }
}
