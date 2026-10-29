package dev.patra.catalog.infra.portal.citation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.kernel.model.enums.PublicationDateType;
import dev.patra.catalog.domain.kernel.model.vo.Pagination;
import dev.patra.catalog.domain.kernel.model.vo.PublicationDate;
import dev.patra.catalog.domain.portal.model.enums.CitationStyle;
import dev.patra.catalog.domain.portal.model.read.CitationAuthor;
import dev.patra.catalog.domain.portal.model.read.CitationEntry;
import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// `CiteprocCitationFormatterAdapter` 单元测试：四种样式与夹具逐行一致，编号样式按请求顺序，APA 按字母序。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("CiteprocCitationFormatterAdapter 单元测试")
class CiteprocCitationFormatterAdapterTest {

  private final CiteprocCitationFormatterAdapter adapter = new CiteprocCitationFormatterAdapter();

  @ParameterizedTest(name = "{0}")
  @EnumSource(CitationStyle.class)
  @DisplayName("三篇样例与夹具逐行相等（label<TAB>text）")
  void should_match_fixture(CitationStyle style) throws IOException {
    List<CitationEntry> entries = adapter.format(style, CitationRecordFixture.samples());

    List<String> lines =
        entries.stream().map(e -> (e.label() == null ? "" : e.label()) + "\t" + e.text()).toList();
    assertThat(lines).containsExactlyElementsOf(fixture(style));
  }

  @Test
  @DisplayName("GB/T 用 en-US：超过 3 位作者写 et al.，不写「等」")
  void should_render_gb_t_with_et_al_not_deng() {
    String text =
        adapter
            .format(CitationStyle.GB_T_7714_2015, List.of(CitationRecordFixture.full()))
            .get(0)
            .text();

    assertThat(text).contains("et al.").doesNotContain("等");
  }

  @Test
  @DisplayName("编号样式按请求顺序编号")
  void should_keep_request_order_for_numeric_styles() {
    List<CitationEntry> entries =
        adapter.format(
            CitationStyle.VANCOUVER,
            List.of(CitationRecordFixture.yearMonth(), CitationRecordFixture.full()));

    assertThat(entries).extracting(CitationEntry::label).containsExactly("1.", "2.");
    assertThat(entries.get(0).text()).startsWith("Wang L.");
    assertThat(entries.get(1).text()).startsWith("Anker SD");
  }

  @Test
  @DisplayName("APA 按作者字母序返回，label 为 null")
  void should_return_apa_in_alphabetical_order_with_null_label() {
    List<CitationEntry> entries =
        adapter.format(
            CitationStyle.APA_7,
            List.of(
                CitationRecordFixture.yearMonth(),
                CitationRecordFixture.yearOnlyWithArticleNumber(),
                CitationRecordFixture.full()));

    assertThat(entries).extracting(CitationEntry::label).containsOnlyNulls();
    assertThat(entries)
        .extracting(e -> e.text().substring(0, e.text().indexOf(',')))
        .containsExactly("Anker", "Smith", "Wang");
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(CitationStyle.class)
  @DisplayName("没有作者的文献照样生成，不报错")
  void should_render_record_without_authors(CitationStyle style) {
    List<CitationEntry> entries =
        adapter.format(style, List.of(CitationRecordFixture.withoutAuthors()));

    assertThat(entries)
        .singleElement()
        .extracting(CitationEntry::text)
        .asString()
        .contains("Anonymous editorial");
  }

  /// 真实 PubMed 标题末尾带句点时，GB/T 输出「标题[J/OL]」，不出现「.[J/OL]」。
  @Test
  @DisplayName("GB/T：PubMed 标题末尾的句点不留在文献类型标识前")
  void should_not_leave_title_period_before_gb_t_type_marker() {
    CitationRecord record =
        CitationRecord.builder()
            .id(8L)
            .doi("10.1080/10810730.2026.2615828")
            .title(
                "Promotion - The 4th P of Social Marketing: A Scoping Review of Efforts from"
                    + " 2010-2022.")
            .authors(List.of(CitationAuthor.of("Perrault", "Evan K", "EK", null)))
            .journalTitle("Journal of health communication")
            .journalAbbreviation("J Health Commun")
            .volume("31")
            .issue("1")
            .pagination(Pagination.of("65-76", "65", "76"))
            .publishedDate(PublicationDate.ofYear(PublicationDateType.PUBLISHED, 2026))
            .language("en")
            .build();

    String text = adapter.format(CitationStyle.GB_T_7714_2015, List.of(record)).get(0).text();

    assertThat(text).contains("2010-2022[J/OL]").doesNotContain(".[J/OL]");
  }

  @Test
  @DisplayName("空入参返回空列表")
  void should_return_empty_for_empty_input() {
    assertThat(adapter.format(CitationStyle.AMA_11, List.of())).isEmpty();
  }

  private List<String> fixture(CitationStyle style) throws IOException {
    try (InputStream in =
        getClass().getResourceAsStream("/citation/expected/" + style.code() + ".txt")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
    }
  }
}
