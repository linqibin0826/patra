package dev.patra.catalog.infra.publication.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.kernel.exception.XmlParseException;
import dev.patra.catalog.domain.kernel.model.enums.PublicationIdentifierType;
import dev.patra.catalog.domain.publication.model.vo.CanonicalPublication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PubmedXmlParserAdapter 集成测试：解析一篇 `<PubmedArticle>` 原文（逐篇切出或从来源表读出）。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedXmlParserAdapter 集成测试")
class PubmedXmlParserAdapterIT {

  private final PubmedXmlParserAdapter adapter = new PubmedXmlParserAdapter();

  @Test
  @DisplayName("一篇完整原文：标识符、期刊、卷期、日期部件都解析出来")
  void should_parse_single_article() {
    String raw =
        """
        <PubmedArticle>
          <MedlineCitation Status="MEDLINE">
            <PMID Version="1">12345678</PMID>
            <Article PubModel="Print">
              <Journal>
                <ISSN IssnType="Print">1234-5678</ISSN>
                <JournalIssue CitedMedium="Print">
                  <Volume>10</Volume>
                  <Issue>2</Issue>
                  <PubDate><Year>2024</Year><Month>Jun</Month></PubDate>
                </JournalIssue>
                <Title>Journal of Examples</Title>
              </Journal>
              <ArticleTitle>Sample Article Title</ArticleTitle>
              <Language>eng</Language>
            </Article>
            <MedlineJournalInfo><NlmUniqueID>101234567</NlmUniqueID></MedlineJournalInfo>
          </MedlineCitation>
          <PubmedData>
            <PublicationStatus>epublish</PublicationStatus>
            <ArticleIdList><ArticleId IdType="doi">10.1000/example</ArticleId></ArticleIdList>
          </PubmedData>
        </PubmedArticle>
        """;

    CanonicalPublication pub = adapter.parseArticle(raw);

    assertThat(idOf(pub, PublicationIdentifierType.PMID)).isEqualTo("12345678");
    assertThat(idOf(pub, PublicationIdentifierType.DOI)).isEqualTo("10.1000/example");
    assertThat(pub.getTitle()).isEqualTo("Sample Article Title");
    assertThat(pub.getJournal().getNlmUniqueId()).isEqualTo("101234567");
    assertThat(pub.getJournal().getVolume()).isEqualTo("10");
    assertThat(pub.getJournal().getIssue()).isEqualTo("2");
    assertThat(pub.getDates().getPublished().year()).isEqualTo(2024);
    assertThat(pub.getDates().getPublished().month()).isEqualTo(6);
    assertThat(pub.getDates().getPublished().day()).isNull();
  }

  @Test
  @DisplayName("缺 PMID 的原文返回 null")
  void should_return_null_without_pmid() {
    String raw =
        """
        <PubmedArticle>
          <MedlineCitation><Article><ArticleTitle>No PMID</ArticleTitle></Article></MedlineCitation>
        </PubmedArticle>
        """;

    assertThat(adapter.parseArticle(raw)).isNull();
  }

  @Test
  @DisplayName("原文不是合法 XML（截断）时抛 XmlParseException")
  void should_throw_on_malformed_xml() {
    assertThatThrownBy(() -> adapter.parseArticle("<PubmedArticle><MedlineCitation><PMID>1</PMID>"))
        .isInstanceOf(XmlParseException.class);
  }

  private static String idOf(CanonicalPublication publication, PublicationIdentifierType type) {
    return publication.getIdentifiers().stream()
        .filter(id -> type == id.getType())
        .map(CanonicalPublication.Identifier::getValue)
        .findFirst()
        .orElse(null);
  }
}
