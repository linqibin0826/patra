package dev.patra.catalog.infra.fulltext.parser;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.fulltext.model.vo.body.Reference;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 用真实夹具核对参考文献著录文本与 PMC 原文页（`pmc.ncbi.nlm.nih.gov/articles/<PMCID>/`）的显示一致。
@DisplayName("参考文献著录文本对齐 PMC 原文页")
class JatsReferenceFixtureTest {

  private final JatsBodyParserAdapter parser = new JatsBodyParserAdapter();

  @Test
  @DisplayName("PLOS 的 name 里姓与名之间没有空白，补空格（Sengupta P）")
  void should_separate_surname_and_given_names_when_adjacent() throws Exception {
    assertThat(text("PMC12768384.xml", 0))
        .startsWith("Sengupta P, Samuel ADT. Caenorhabditis elegans");
  }

  @Test
  @DisplayName("PMID / PMCID 的 pub-id 不进著录文本（PMC 原文页只把它们做成链接），pmid 字段照常取")
  void should_drop_pmid_and_pmcid_text() throws Exception {
    Reference ref = reference("PMC12768384.xml", 0);

    assertThat(text(ref))
        .endsWith("Curr Opin Neurobiol. 2009;19(6):637–43. doi: 10.1016/j.conb.2009.09.009");
    assertThat(ref.pmid()).isEqualTo("19896359");
  }

  @Test
  @DisplayName("PMC 补上的 DOI（assigning-authority=pmc）不进著录文本，doi 字段照常取")
  void should_drop_pmc_assigned_doi_text() throws Exception {
    Reference ref = reference("PMC12756210.xml", 0);

    assertThat(text(ref)).endsWith("Front Plant Sci 11:662. 10.3389/fpls.2020.00662");
    assertThat(ref.doi()).isEqualTo("10.3389/fpls.2020.00662");
  }

  @Test
  @DisplayName("Frontiers 的 person-group 里作者之间只有换行，补逗号")
  void should_separate_authors_with_comma_when_person_group_has_only_whitespace() throws Exception {
    assertThat(text("PMC13335072.xml", 0))
        .startsWith(
            "Cremers-Pijpers S, van Rossum C, Dautzenberg M, Wertheim H, Tostmann A, Hopman J."
                + " Disinfecting");
  }

  @Test
  @DisplayName("空的 etal 元素显示为 et al，跟在作者后面时前面补逗号")
  void should_render_empty_etal_as_et_al() throws Exception {
    assertThat(text("PMC13335072.xml", 1))
        .contains("Stewardson AJ, Matterson G, et al. Investigating the effect");
  }

  @Test
  @DisplayName("带文字的 etal 照原文，不补逗号（OUP 写成 Zhang MQ <etal>et al.</etal>）")
  void should_keep_etal_text_as_is() throws Exception {
    assertThat(text("PMC12754789.xml", 0)).startsWith("Spellman PT, Sherlock G, Zhang MQ et al. ");
  }

  @Test
  @DisplayName("作者组末尾的空白去掉，不在句点前留空格")
  void should_drop_trailing_whitespace_of_person_group() throws Exception {
    assertThat(text("PMC12754789.xml", 1)).startsWith("Straume M. DNA microarray");
  }

  @Test
  @DisplayName("人名的 suffix 前补逗号（SAGE：Cooney WP, III）")
  void should_separate_name_suffix_with_comma() throws Exception {
    assertThat(text("PMC12747861.xml", 0))
        .startsWith("Gelberman RH, Cooney WP, III, Szabo RM. Carpal instability.");
  }

  @Test
  @DisplayName("string-name 内部首尾的换行不在逗号前留空格（Hindawi）")
  void should_drop_whitespace_at_both_ends_of_string_name() throws Exception {
    assertThat(text("PMC12767392.xml", 0))
        .startsWith("Smeets R., Henningsen A., and Jung O., et al.Definition");
  }

  /// 读夹具里第 `index` 条参考文献。
  private Reference reference(String fileName, int index) throws Exception {
    Path file = Path.of(JatsReferenceFixtureTest.class.getResource("/jats/" + fileName).toURI());
    try (InputStream in = Files.newInputStream(file)) {
      return parser.parse(in, List.of()).body().references().get(index);
    }
  }

  /// 读夹具里第 `index` 条参考文献的纯文本。
  private String text(String fileName, int index) throws Exception {
    return text(reference(fileName, index));
  }

  /// 参考文献的纯文本，连续空白（含原文里的不换行空格）归一成一个空格再比较。
  private static String text(Reference ref) {
    return JatsBodyParserAdapterTest.text(ref.content()).replaceAll("[\\s\\u00a0]+", " ");
  }
}
