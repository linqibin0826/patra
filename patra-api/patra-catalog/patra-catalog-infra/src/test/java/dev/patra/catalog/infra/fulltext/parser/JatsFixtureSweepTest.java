package dev.patra.catalog.infra.fulltext.parser;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.JatsParseResult;
import dev.patra.catalog.domain.fulltext.model.vo.body.Block;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import dev.patra.catalog.domain.fulltext.model.vo.body.Inline;
import dev.patra.catalog.domain.fulltext.model.vo.body.Reference;
import dev.patra.catalog.domain.fulltext.model.vo.body.Section;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/// 对 `src/test/resources/jats/` 下的全部夹具跑解析器（来源与许可见同目录 README）。
@DisplayName("JATS 夹具全量解析")
class JatsFixtureSweepTest {

  private static final Pattern GRAPHIC_HREF =
      Pattern.compile("<(?:graphic|inline-graphic)[^>]*xlink:href=\"([^\"]+)\"");

  private static final Pattern TAG = Pattern.compile("<([a-zA-Z][\\w-]*)");

  private final JatsBodyParserAdapter parser = new JatsBodyParserAdapter();

  static Stream<String> fixtures() throws IOException, URISyntaxException {
    try (Stream<Path> files = Files.list(dir())) {
      return files
          .map(p -> p.getFileName().toString())
          .filter(n -> n.endsWith(".xml"))
          .sorted()
          .toList()
          .stream();
    }
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("fixtures")
  @DisplayName("每篇都能解析，输出的交叉引用都能找到目标，所有图都匹配上图片")
  void should_parse_every_fixture_and_resolve_all_xref_targets(String fileName) throws Exception {
    String xml = Files.readString(dir().resolve(fileName), StandardCharsets.UTF_8);
    List<FullTextMedia> media = new ArrayList<>();
    Matcher m = GRAPHIC_HREF.matcher(xml);
    while (m.find()) {
      media.add(FullTextMedia.of(m.group(1), null, null));
    }

    JatsParseResult result;
    try (InputStream in = Files.newInputStream(dir().resolve(fileName))) {
      result = parser.parse(in, media);
    }

    assertThat(result.noBody()).isEqualTo(fileName.equals("no-body-synthetic.xml"));
    if (result.noBody()) {
      return;
    }
    FullTextBody body = result.body();
    Collected c = new Collected();
    c.blocks(body.blocks());
    c.sections(body.sections());
    c.sections(body.back());
    for (Reference ref : body.references()) {
      c.ids.add(ref.id());
      c.inlines(ref.content());
    }
    assertThat(c.xrefTargets).as("交叉引用目标").isSubsetOf(c.ids);
    assertThat(c.figureImages).as("图片").doesNotContainNull();
    assertThat(body.blocks().size() + body.sections().size()).as("正文内容").isPositive();
  }

  @Test
  @DisplayName("夹具里出现的 MathML 标签全集与 mathml-tags.txt 一致（交给 PAP-88 扩充白名单）")
  void should_collect_mathml_tag_names() throws Exception {
    Set<String> tags = new TreeSet<>();
    for (String fileName : fixtures().toList()) {
      try (InputStream in = Files.newInputStream(dir().resolve(fileName))) {
        JatsParseResult result = parser.parse(in, List.of());
        if (result.noBody()) {
          continue;
        }
        Collected c = new Collected();
        c.blocks(result.body().blocks());
        c.sections(result.body().sections());
        c.sections(result.body().back());
        for (String mathml : c.mathml) {
          Matcher m = TAG.matcher(mathml);
          while (m.find()) {
            tags.add(m.group(1));
          }
        }
      }
    }

    List<String> expected =
        Files.readAllLines(dir().resolve("mathml-tags.txt")).stream()
            .filter(s -> !s.isBlank())
            .toList();
    assertThat(tags).containsExactlyElementsOf(expected);
  }

  @Test
  @DisplayName("Wiley 公式密集的文章按 spec 口径约 1.1 万词（MathML 记号不计）")
  void should_count_words_of_math_heavy_article_by_spec_rule() throws Exception {
    try (InputStream in = Files.newInputStream(dir().resolve("PMC12670376.xml"))) {
      assertThat(parser.parse(in, List.of()).body().wordCount()).isBetween(10_000, 12_000);
    }
  }

  private static Path dir() throws URISyntaxException {
    return Path.of(JatsFixtureSweepTest.class.getResource("/jats").toURI());
  }

  /// 递归收集 id、交叉引用目标、图片与 MathML。
  private static final class Collected {
    private final Set<String> ids = new HashSet<>();
    private final Set<String> xrefTargets = new HashSet<>();
    private final List<String> figureImages = new ArrayList<>();
    private final List<String> mathml = new ArrayList<>();

    void sections(List<Section> sections) {
      for (Section s : sections) {
        ids.add(s.id());
        inlines(s.title());
        blocks(s.blocks());
        sections(s.sections());
      }
    }

    void blocks(List<Block> blocks) {
      for (Block block : blocks) {
        switch (block) {
          case Block.Paragraph p -> inlines(p.content());
          case Block.Figure f -> {
            ids.add(f.id());
            figureImages.add(f.image());
            blocks(f.caption());
          }
          case Block.Table t -> {
            ids.add(t.id());
            blocks(t.caption());
            blocks(t.footnotes());
            Stream.concat(t.head().stream(), t.body().stream())
                .flatMap(List::stream)
                .forEach(cell -> inlines(cell.content()));
          }
          case Block.Formula f -> {
            ids.add(f.id());
            if (f.mathml() != null) {
              mathml.add(f.mathml());
            } else {
              figureImages.add(f.image());
            }
          }
          case Block.ListBlock l -> l.items().forEach(this::blocks);
          case Block.Quote q -> blocks(q.blocks());
          case Block.Box b -> {
            ids.add(b.id());
            inlines(b.title());
            blocks(b.blocks());
          }
          case Block.Code code -> {
            // 无可收集内容
          }
        }
      }
    }

    void inlines(List<Inline> inlines) {
      for (Inline inline : inlines) {
        switch (inline) {
          case Inline.Styled s -> inlines(s.children());
          case Inline.Xref x -> {
            xrefTargets.addAll(x.targets());
            inlines(x.children());
          }
          case Inline.Link l -> inlines(l.children());
          case Inline.MathInline mi -> mathml.add(mi.mathml());
          case Inline.Text t -> {
            // 无可收集内容
          }
          case Inline.Break b -> {
            // 无可收集内容
          }
        }
      }
    }
  }
}
