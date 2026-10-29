package dev.patra.catalog.infra.publication.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.patra.catalog.infra.kernel.support.SecureXmlInputFactory;
import dev.patra.catalog.infra.kernel.support.XmlParsingContext;
import dev.patra.common.CanonicalPublication;
import dev.patra.common.CanonicalPublication.AbstractSection;
import dev.patra.common.CanonicalPublication.Author;
import dev.patra.common.CanonicalPublication.Identifier;
import dev.patra.common.CanonicalPublication.Journal;
import dev.patra.common.CanonicalPublication.Keyword;
import dev.patra.common.CanonicalPublication.KeywordSet;
import dev.patra.common.CanonicalPublication.MeshHeading;
import dev.patra.common.CanonicalPublication.PublicationType;
import dev.patra.common.PartialDate;
import dev.patra.common.PublicationIdentifierType;
import java.io.StringReader;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// CanonicalPublicationParsingStrategy 单元测试。
///
/// 验证 PubMed 文献解析策略的正确性。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("CanonicalPublicationParsingStrategy 策略")
class CanonicalPublicationParsingStrategyTest {

  private static final javax.xml.stream.XMLInputFactory XML_INPUT_FACTORY =
      SecureXmlInputFactory.getInstance();

  private final CanonicalPublicationParsingStrategy strategy =
      CanonicalPublicationParsingStrategy.INSTANCE;

  // ========== 策略契约测试 ==========

  @Nested
  @DisplayName("策略契约")
  class StrategyContract {

    @Test
    @DisplayName("rootElementName() 应返回 PubmedArticle")
    void rootElementName_shouldReturnPubmedArticle() {
      assertEquals(PubmedXmlElements.Container.PUBMED_ARTICLE, strategy.rootElementName());
    }

    @Test
    @DisplayName("INSTANCE 应为非空单例")
    void instance_shouldBeNonNullSingleton() {
      assertNotNull(CanonicalPublicationParsingStrategy.INSTANCE);
      assertEquals(CanonicalPublicationParsingStrategy.INSTANCE, strategy);
    }
  }

  // ========== 标识符解析测试 ==========

  @Nested
  @DisplayName("标识符解析")
  class IdentifierParsing {

    @Test
    @DisplayName("应正确解析 PMID")
    void shouldParsePmid() throws Exception {
      var xml = minimalArticleXml("12345678", "Test Title", "101234567", 2024);
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertThat(result.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(PublicationIdentifierType.PMID, "12345678"));
    }

    @Test
    @DisplayName("缺少 PMID 时应返回 null")
    void shouldReturnNullWhenMissingPmid() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <Article>
                <ArticleTitle>Test</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>101234567</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result);
    }

    @Test
    @DisplayName("应解析 DOI")
    void shouldParseDoi() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <ArticleIdList>
                <ArticleId IdType="doi">10.1000/example.doi</ArticleId>
              </ArticleIdList>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(
                  PublicationIdentifierType.DOI, "10.1000/example.doi"));
    }

    @Test
    @DisplayName("应解析 PMC ID")
    void shouldParsePmcId() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <ArticleIdList>
                <ArticleId IdType="pmc">PMC1234567</ArticleId>
              </ArticleIdList>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(PublicationIdentifierType.PMC, "PMC1234567"));
    }
  }

  // ========== 标题解析测试 ==========

  @Nested
  @DisplayName("标题解析")
  class TitleParsing {

    @Test
    @DisplayName("应正确解析 ArticleTitle 到 title")
    void shouldParseArticleTitle() throws Exception {
      var xml = minimalArticleXml("1", "Sample Article Title", "N", 2024);
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("Sample Article Title", result.getTitle());
    }

    @Test
    @DisplayName("应正确解析 VernacularTitle 到 originalTitle")
    void shouldParseVernacularTitle() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>English Title</ArticleTitle>
                <VernacularTitle>中文标题</VernacularTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("English Title", result.getTitle());
      assertEquals("中文标题", result.getOriginalTitle());
    }
  }

  // ========== 期刊信息解析测试 ==========

  @Nested
  @DisplayName("期刊信息解析")
  class JournalInfoParsing {

    @Test
    @DisplayName("应解析 NlmUniqueID 到 journal.nlmUniqueId")
    void shouldParseNlmUniqueId() throws Exception {
      var xml = minimalArticleXml("1", "T", "101234567", 2024);
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getJournal());
      assertEquals("101234567", result.getJournal().getNlmUniqueId());
    }

    @Test
    @DisplayName("应解析 Print ISSN 到 journal.issn")
    void shouldParsePrintIssn() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <ISSN IssnType="Print">1234-5678</ISSN>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Journal journal = result.getJournal();
      assertNotNull(journal);
      assertEquals("1234-5678", journal.getIssn());
      assertEquals("print", journal.getIssnType());
    }

    @Test
    @DisplayName("应解析 Electronic ISSN 到 journal.issn（当无 Print ISSN 时）")
    void shouldParseElectronicIssnWhenNoPrint() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <ISSN IssnType="Electronic">8765-4321</ISSN>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Journal journal = result.getJournal();
      assertNotNull(journal);
      assertEquals("8765-4321", journal.getIssn());
      assertEquals("electronic", journal.getIssnType());
    }

    @Test
    @DisplayName("Print ISSN 应优先于 Electronic ISSN")
    void printIssnShouldTakePrecedenceOverElectronic() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <ISSN IssnType="Print">1234-5678</ISSN>
                  <ISSN IssnType="Electronic">8765-4321</ISSN>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Journal journal = result.getJournal();
      assertNotNull(journal);
      assertEquals("1234-5678", journal.getIssn());
      assertEquals("print", journal.getIssnType());
    }

    @Test
    @DisplayName("应解析 ISSNLinking 到 journal.issnLinking")
    void shouldParseIssnLinking() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
                <ISSNLinking>1111-2222</ISSNLinking>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("1111-2222", result.getJournal().getIssnLinking());
    }

    @Test
    @DisplayName("应解析期刊标题到 journal.title")
    void shouldParseJournalTitle() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <Title>Journal of Examples</Title>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("Journal of Examples", result.getJournal().getTitle());
    }

    @Test
    @DisplayName("应解析 Volume 和 Issue 到 journal")
    void shouldParseVolumeAndIssue() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <JournalIssue>
                    <Volume>10</Volume>
                    <Issue>2</Issue>
                  </JournalIssue>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Journal journal = result.getJournal();
      assertEquals("10", journal.getVolume());
      assertEquals("2", journal.getIssue());
    }
  }

  // ========== 出版日期解析测试 ==========

  @Nested
  @DisplayName("出版日期解析（按实际精度，不补月日）")
  class PubDateParsing {

    /// 用给定的 `<PubDate>` 内容解析一篇最小文献。
    private CanonicalPublication parseWithPubDate(String pubDateInner) throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <JournalIssue>
                    <PubDate>%s</PubDate>
                  </JournalIssue>
                </Journal>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """
              .formatted(pubDateInner);
      return strategy.parseRecord(createReaderAtStartElement(xml), XmlParsingContext.empty());
    }

    @Test
    @DisplayName("年月日齐全且合法：精确到日")
    void should_keep_day_precision_when_year_month_day_valid() throws Exception {
      PartialDate d =
          parseWithPubDate("<Year>2021</Year><Month>Oct</Month><Day>14</Day>")
              .getDates()
              .getPublished();

      assertThat(d).isEqualTo(new PartialDate(2021, 10, 14, null, null));
      assertThat(d.precision()).isEqualTo("day");
    }

    @Test
    @DisplayName("日对该月不合法（2 月 30 日）：丢掉日，降为月精度")
    void should_drop_day_when_day_invalid_for_month() throws Exception {
      PartialDate d =
          parseWithPubDate("<Year>2021</Year><Month>Feb</Month><Day>30</Day>")
              .getDates()
              .getPublished();

      assertThat(d).isEqualTo(new PartialDate(2021, 2, null, null, null));
      assertThat(d.precision()).isEqualTo("month");
    }

    @Test
    @DisplayName("只有年月：月精度，不补日")
    void should_keep_month_precision_when_no_day() throws Exception {
      assertThat(parseWithPubDate("<Year>2022</Year><Month>10</Month>").getDates().getPublished())
          .isEqualTo(new PartialDate(2022, 10, null, null, null));
    }

    @Test
    @DisplayName("只有年：年精度，不补月日")
    void should_keep_year_precision_when_year_only() throws Exception {
      PartialDate d = parseWithPubDate("<Year>2023</Year>").getDates().getPublished();

      assertThat(d).isEqualTo(new PartialDate(2023, null, null, null, null));
      assertThat(d.precision()).isEqualTo("year");
    }

    @Test
    @DisplayName("年 + 季节：年精度，季节原文另存")
    void should_store_season_with_year_precision() throws Exception {
      assertThat(
              parseWithPubDate("<Year>2000</Year><Season>Spring</Season>")
                  .getDates()
                  .getPublished())
          .isEqualTo(new PartialDate(2000, null, null, "Spring", null));
    }

    @Test
    @DisplayName("月份是区间（Jun-Jul）：年精度，原文进 text")
    void should_store_month_range_as_text_with_year_precision() throws Exception {
      assertThat(
              parseWithPubDate("<Year>2019</Year><Month>Jun-Jul</Month>").getDates().getPublished())
          .isEqualTo(new PartialDate(2019, null, null, null, "Jun-Jul"));
    }

    @Test
    @DisplayName("月份超出 1 ~ 12：年精度，原文进 text")
    void should_treat_out_of_range_month_as_year_precision() throws Exception {
      assertThat(
              parseWithPubDate("<Year>2019</Year><Month>13</Month><Day>2</Day>")
                  .getDates()
                  .getPublished())
          .isEqualTo(new PartialDate(2019, null, null, null, "13"));
    }

    @Test
    @DisplayName("MedlineDate：取第一个四位年份，原文进 text")
    void should_take_first_year_from_medline_date() throws Exception {
      assertThat(
              parseWithPubDate("<MedlineDate>1998 Dec-1999 Jan</MedlineDate>")
                  .getDates()
                  .getPublished())
          .isEqualTo(new PartialDate(1998, null, null, null, "1998 Dec-1999 Jan"));
    }

    @Test
    @DisplayName("没有任何年份：出版日期为 null")
    void should_leave_published_null_without_year() throws Exception {
      assertThat(parseWithPubDate("<Month>Jun</Month>").getDates().getPublished()).isNull();
    }
  }

  @Nested
  @DisplayName("引用字段解析：页码、ELocationID、期刊缩写")
  class CitationFieldParsing {

    /// 用给定的 Article 片段、MedlineJournalInfo 片段与 ArticleIdList 片段解析一篇最小文献。
    private CanonicalPublication parse(String articleExtra, String journalExtra, String idList)
        throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <Journal>
                  <Title>The New England journal of medicine</Title>
                  %s
                </Journal>
                <ArticleTitle>T</ArticleTitle>
                %s
              </Article>
              <MedlineJournalInfo>
                <MedlineTA>N Engl J Med</MedlineTA>
                <NlmUniqueID>0255562</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <ArticleIdList>%s</ArticleIdList>
            </PubmedData>
          </PubmedArticle>
          """
              .formatted(journalExtra, articleExtra, idList);
      return strategy.parseRecord(createReaderAtStartElement(xml), XmlParsingContext.empty());
    }

    private static List<String> idsOf(CanonicalPublication p, PublicationIdentifierType type) {
      return p.getIdentifiers().stream()
          .filter(id -> id.getType() == type)
          .map(Identifier::getValue)
          .toList();
    }

    @Test
    @DisplayName("Pagination：StartPage、EndPage、MedlinePgn 都读")
    void should_parse_medline_pgn_and_start_end_pages() throws Exception {
      CanonicalPublication p =
          parse(
              "<Pagination><StartPage>1451</StartPage><EndPage>1461</EndPage>"
                  + "<MedlinePgn>1451-61</MedlinePgn></Pagination>",
              "",
              "");

      assertThat(p.getPagination().getStartPage()).isEqualTo("1451");
      assertThat(p.getPagination().getEndPage()).isEqualTo("1461");
      assertThat(p.getPagination().getMedlinePgn()).isEqualTo("1451-61");
    }

    @Test
    @DisplayName("ELocationID pii 进标识符 PII")
    void should_parse_elocation_pii_as_identifier() throws Exception {
      CanonicalPublication p =
          parse("<ELocationID EIdType=\"pii\" ValidYN=\"Y\">e0123456</ELocationID>", "", "");

      assertThat(idsOf(p, PublicationIdentifierType.PII)).containsExactly("e0123456");
    }

    @Test
    @DisplayName("ValidYN=N 的 ELocationID 不收")
    void should_ignore_invalid_elocation() throws Exception {
      CanonicalPublication p =
          parse("<ELocationID EIdType=\"pii\" ValidYN=\"N\">bad</ELocationID>", "", "");

      assertThat(idsOf(p, PublicationIdentifierType.PII)).isEmpty();
    }

    @Test
    @DisplayName("ELocationID doi 只在 ArticleIdList 没有 DOI 时使用")
    void should_use_elocation_doi_only_when_article_id_doi_missing() throws Exception {
      String eloc = "<ELocationID EIdType=\"doi\" ValidYN=\"Y\">10.1/eloc</ELocationID>";

      CanonicalPublication onlyEloc = parse(eloc, "", "");
      CanonicalPublication both =
          parse(eloc, "", "<ArticleId IdType=\"doi\">10.1/articleid</ArticleId>");

      assertThat(idsOf(onlyEloc, PublicationIdentifierType.DOI)).containsExactly("10.1/eloc");
      assertThat(idsOf(both, PublicationIdentifierType.DOI)).containsExactly("10.1/articleid");
    }

    @Test
    @DisplayName("期刊 ISOAbbreviation 与 MedlineTA 都读")
    void should_parse_iso_abbreviation_and_medline_ta() throws Exception {
      CanonicalPublication p = parse("", "<ISOAbbreviation>N. Engl. J. Med.</ISOAbbreviation>", "");

      assertThat(p.getJournal().getIsoAbbreviation()).isEqualTo("N. Engl. J. Med.");
      assertThat(p.getJournal().getMedlineAbbreviation()).isEqualTo("N Engl J Med");
    }
  }

  @Nested
  @DisplayName("PMC 放开日期解析")
  class PmcReleaseDateParsing {

    @Test
    @DisplayName("应从 History 的 pmc-release 解析 PMC 放开日期")
    void should_parse_pmc_release_date_from_history() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <History>
                <PubMedPubDate PubStatus="pubmed">
                  <Year>2026</Year><Month>1</Month><Day>2</Day>
                </PubMedPubDate>
                <PubMedPubDate PubStatus="pmc-release">
                  <Year>2027</Year><Month>01</Month><Day>15</Day>
                </PubMedPubDate>
              </History>
              <PublicationStatus>ppublish</PublicationStatus>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getDates().getPmcRelease())
          .isEqualTo(new PartialDate(2027, 1, 15, null, null));
      assertThat(result.getPublicationStatus()).isEqualTo("ppublish");
    }

    @Test
    @DisplayName("pmc-release 只到年月：月精度，不补日")
    void should_parse_pmc_release_with_precision() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <History>
                <PubMedPubDate PubStatus="pmc-release">
                  <Year>2022</Year><Month>May</Month>
                </PubMedPubDate>
              </History>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getDates().getPmcRelease())
          .isEqualTo(new PartialDate(2022, 5, null, null, null));
    }

    @Test
    @DisplayName("没有 pmc-release 时 PMC 放开日期为空")
    void should_leave_pmc_release_null_when_absent() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <History>
                <PubMedPubDate PubStatus="pubmed">
                  <Year>2026</Year><Month>1</Month><Day>2</Day>
                </PubMedPubDate>
              </History>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getDates().getPmcRelease()).isNull();
    }
  }

  // ========== 语言解析测试 ==========

  @Nested
  @DisplayName("语言解析")
  class LanguageParsing {

    @Test
    @DisplayName("应保留原始 ISO 639-3 语言代码")
    void shouldPreserveOriginalLanguageCode() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Language>eng</Language>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("eng", result.getLanguage());
    }

    @Test
    @DisplayName("应取第一个语言作为主语言")
    void shouldUseFirstLanguageAsPrimary() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Language>chi</Language>
                <Language>eng</Language>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("chi", result.getLanguage());
    }
  }

  // ========== 其他元数据解析测试 ==========

  @Nested
  @DisplayName("其他元数据解析")
  class OtherMetadataParsing {

    @Test
    @DisplayName("应解析 PublicationStatus")
    void shouldParsePublicationStatus() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <PublicationStatus>epublish</PublicationStatus>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("epublish", result.getPublicationStatus());
    }

    @Test
    @DisplayName("应解析 AuthorList CompleteYN 属性到 authorsComplete")
    void shouldParseAuthorsComplete() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList CompleteYN="Y">
                  <Author>
                    <LastName>Doe</LastName>
                    <ForeName>John</ForeName>
                  </Author>
                </AuthorList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertTrue(result.getAuthorsComplete());
    }

    /// `MedlineCitation@Status`（如 `In-Process`）应原样落到 `metadata.status`，
    /// 且 metadata 块本身不再为 null。
    @Test
    @DisplayName("应解析 MedlineCitation Status 属性到 metadata.status")
    void shouldParseMedlineCitationStatus() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation Status="In-Process">
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getMetadata());
      assertEquals("In-Process", result.getMetadata().getStatus());
    }

    /// 同一 `MedlineCitation` 起始标签上的 `@Owner` 与 `@IndexingMethod`
    /// 应分别落到 `metadata.owner` / `metadata.indexingMethod`。
    @Test
    @DisplayName("应解析 MedlineCitation Owner 与 IndexingMethod 属性到 metadata")
    void shouldParseMedlineCitationOwnerAndIndexingMethod() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation Status="MEDLINE" Owner="NLM" IndexingMethod="Automated">
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("NLM", result.getMetadata().getOwner());
      assertEquals("Automated", result.getMetadata().getIndexingMethod());
    }

    /// `MedlineCitation` 下的 `<CitationSubset>` 子元素文本应落到 `metadata.citationSubset`。
    @Test
    @DisplayName("应解析 CitationSubset 元素到 metadata.citationSubset")
    void shouldParseCitationSubset() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation Status="MEDLINE">
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <CitationSubset>IM</CitationSubset>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals("IM", result.getMetadata().getCitationSubset());
    }
  }

  // ========== 作者解析测试 ==========

  @Nested
  @DisplayName("作者解析")
  class AuthorParsing {

    @Test
    @DisplayName("应解析作者基本信息")
    void shouldParseAuthorBasicInfo() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList CompleteYN="Y">
                  <Author>
                    <LastName>Smith</LastName>
                    <ForeName>John David</ForeName>
                    <Initials>JD</Initials>
                    <Suffix>Jr</Suffix>
                  </Author>
                </AuthorList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getAuthors());
      assertThat(result.getAuthors()).hasSize(1);
      Author author = result.getAuthors().getFirst();
      assertEquals("Smith", author.getLastName());
      assertEquals("John David", author.getForeName());
      assertEquals("JD", author.getInitials());
      assertEquals("Jr", author.getSuffix());
    }

    @Test
    @DisplayName("应解析多个作者")
    void shouldParseMultipleAuthors() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList>
                  <Author>
                    <LastName>First</LastName>
                  </Author>
                  <Author>
                    <LastName>Second</LastName>
                  </Author>
                  <Author>
                    <LastName>Third</LastName>
                  </Author>
                </AuthorList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAuthors()).hasSize(3);
      assertThat(result.getAuthors())
          .extracting(Author::getLastName)
          .containsExactly("First", "Second", "Third");
    }

    @Test
    @DisplayName("应解析集体作者名称到 organizationName")
    void shouldParseCollectiveNameToOrganizationName() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList>
                  <Author>
                    <CollectiveName>COVID-19 Research Consortium</CollectiveName>
                  </Author>
                </AuthorList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAuthors()).hasSize(1);
      Author author = result.getAuthors().getFirst();
      assertEquals("COVID-19 Research Consortium", author.getOrganizationName());
      assertNull(author.getLastName());
    }

    @Test
    @DisplayName("应解析作者 ORCID 标识符")
    void shouldParseAuthorOrcid() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList>
                  <Author>
                    <LastName>Doe</LastName>
                    <ForeName>John</ForeName>
                    <Identifier Source="ORCID">0000-0001-2345-6789</Identifier>
                  </Author>
                </AuthorList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Author author = result.getAuthors().getFirst();
      assertNotNull(author.getIdentifiers());
      assertThat(author.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(
                  PublicationIdentifierType.ORCID, "0000-0001-2345-6789"));
    }

    @Test
    @DisplayName("应解析作者机构信息")
    void shouldParseAuthorAffiliations() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <AuthorList>
                  <Author>
                    <LastName>Doe</LastName>
                    <AffiliationInfo>
                      <Affiliation>Department of Medicine, Harvard University, Boston, MA, USA</Affiliation>
                    </AffiliationInfo>
                    <AffiliationInfo>
                      <Affiliation>Massachusetts General Hospital, Boston, MA, USA</Affiliation>
                    </AffiliationInfo>
                  </Author>
                </AuthorList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      Author author = result.getAuthors().getFirst();
      assertNotNull(author.getAffiliations());
      assertThat(author.getAffiliations()).hasSize(2);
      assertThat(author.getAffiliations().getFirst().getName()).contains("Harvard University");
    }
  }

  // ========== 摘要解析测试 ==========

  @Nested
  @DisplayName("摘要解析")
  class AbstractParsing {

    @Test
    @DisplayName("应解析非结构化摘要")
    void shouldParseUnstructuredAbstract() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Abstract>
                  <AbstractText>This is the full abstract content without structure.</AbstractText>
                </Abstract>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getAbstractContent());
      assertNotNull(result.getAbstractContent().getSections());
      assertThat(result.getAbstractContent().getSections()).hasSize(1);
      assertEquals(
          "This is the full abstract content without structure.",
          result.getAbstractContent().getSections().getFirst().getContent());
      assertNull(result.getAbstractContent().getSections().getFirst().getLabel());
    }

    @Test
    @DisplayName("应解析结构化摘要（带 Label）")
    void shouldParseStructuredAbstract() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Abstract>
                  <AbstractText Label="BACKGROUND">Background content here.</AbstractText>
                  <AbstractText Label="METHODS">Methods content here.</AbstractText>
                  <AbstractText Label="RESULTS">Results content here.</AbstractText>
                  <AbstractText Label="CONCLUSIONS">Conclusions content here.</AbstractText>
                </Abstract>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getAbstractContent());
      assertThat(result.getAbstractContent().getSections()).hasSize(4);
      assertEquals("BACKGROUND", result.getAbstractContent().getSections().get(0).getLabel());
      assertEquals(
          "Background content here.",
          result.getAbstractContent().getSections().get(0).getContent());
      assertEquals("CONCLUSIONS", result.getAbstractContent().getSections().get(3).getLabel());
    }

    @Test
    @DisplayName("唯一一段无 Label 的摘要里有粘连标签时，拆成带标签的段落")
    void should_split_run_in_labels_of_single_unlabeled_abstract_text() throws Exception {
      var result =
          parseAbstractXml(
              "<AbstractText>ObjectiveA rises.MethodsB was used.ResultsC won."
                  + "ConclusionsD works.</AbstractText>");

      var sections = result.getAbstractContent().getSections();
      assertThat(sections)
          .extracting(AbstractSection::getLabel)
          .containsExactly("OBJECTIVE", "METHODS", "RESULTS", "CONCLUSIONS");
      assertEquals("A rises.", sections.getFirst().getContent());
    }

    @Test
    @DisplayName("已有 Label 的摘要不做粘连拆分")
    void should_keep_labeled_abstract_untouched() throws Exception {
      var result =
          parseAbstractXml(
              "<AbstractText Label=\"BACKGROUND\">Bg.</AbstractText>"
                  + "<AbstractText>MethodsThe x.ResultsThe y.</AbstractText>");

      var sections = result.getAbstractContent().getSections();
      assertThat(sections)
          .extracting(AbstractSection::getLabel)
          .containsExactly("BACKGROUND", null);
      assertEquals("MethodsThe x.ResultsThe y.", sections.get(1).getContent());
    }

    @Test
    @DisplayName("原文有带 Label 的段落（即使内容为空被丢弃）时不做粘连拆分")
    void should_not_split_when_blank_labeled_abstract_text_was_dropped() throws Exception {
      var result =
          parseAbstractXml(
              "<AbstractText Label=\"BACKGROUND\"> </AbstractText>"
                  + "<AbstractText>ObjectiveA.MethodsB.</AbstractText>");

      var sections = result.getAbstractContent().getSections();
      assertThat(sections).extracting(AbstractSection::getLabel).containsExactly((String) null);
      assertEquals("ObjectiveA.MethodsB.", sections.getFirst().getContent());
    }

    @Test
    @DisplayName("多段无 Label 的摘要不做粘连拆分")
    void should_keep_multiple_unlabeled_abstract_texts_untouched() throws Exception {
      var result =
          parseAbstractXml(
              "<AbstractText>ObjectiveA.MethodsB.</AbstractText>"
                  + "<AbstractText>Second paragraph.</AbstractText>");

      var sections = result.getAbstractContent().getSections();
      assertThat(sections).extracting(AbstractSection::getLabel).containsExactly(null, null);
      assertEquals("ObjectiveA.MethodsB.", sections.getFirst().getContent());
    }

    /// 解析只含给定 `AbstractText` 片段的最小 PubmedArticle。
    ///
    /// @param abstractTexts `<Abstract>` 内部的 XML 片段
    /// @return 解析结果
    private CanonicalPublication parseAbstractXml(String abstractTexts) throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Abstract>%s</Abstract>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """
              .formatted(abstractTexts);
      return strategy.parseRecord(createReaderAtStartElement(xml), XmlParsingContext.empty());
    }

    @Test
    @DisplayName("应解析主摘要的 CopyrightInformation")
    void parseAbstract_withCopyrightInformation_shouldCaptureCopyright() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Abstract>
                  <AbstractText>Abstract body.</AbstractText>
                  <CopyrightInformation>© 2026 Elsevier.</CopyrightInformation>
                </Abstract>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getAbstractContent());
      assertThat(result.getAbstractContent().getCopyright()).isEqualTo("© 2026 Elsevier.");
      // CopyrightInformation 作为兄弟元素存在时，AbstractText 仍应被正常捕获
      assertThat(result.getAbstractContent().getSections()).hasSize(1);
      assertThat(result.getAbstractContent().getSections().getFirst().getContent())
          .isEqualTo("Abstract body.");
    }

    @Test
    @DisplayName("CopyrightInformation 位于 AbstractText 之前也应正确解析")
    void parseAbstract_copyrightBeforeAbstractText_shouldCaptureBoth() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Abstract>
                  <CopyrightInformation>© 2026 Elsevier.</CopyrightInformation>
                  <AbstractText>Abstract body.</AbstractText>
                </Abstract>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAbstractContent().getCopyright()).isEqualTo("© 2026 Elsevier.");
      assertThat(result.getAbstractContent().getSections()).hasSize(1);
      assertThat(result.getAbstractContent().getSections().getFirst().getContent())
          .isEqualTo("Abstract body.");
    }

    @Test
    @DisplayName("空白 CopyrightInformation 应归一为 null")
    void parseAbstract_blankCopyrightInformation_shouldBeNull() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Abstract>
                  <AbstractText>Abstract body.</AbstractText>
                  <CopyrightInformation>   </CopyrightInformation>
                </Abstract>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNull(result.getAbstractContent().getCopyright());
    }

    @Test
    @DisplayName("混合 Label 的摘要应保留无 Label 段落及顺序")
    void parseAbstract_mixedLabels_shouldKeepUnlabeledSections() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Abstract>
                  <AbstractText Label="BACKGROUND">Background content here.</AbstractText>
                  <AbstractText>Unlabeled content here.</AbstractText>
                  <AbstractText Label="CONCLUSIONS">Conclusions content here.</AbstractText>
                </Abstract>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      var sections = result.getAbstractContent().getSections();
      assertThat(sections).hasSize(3);
      assertThat(sections.get(1).getLabel()).isNull();
      assertThat(sections.get(1).getContent()).isEqualTo("Unlabeled content here.");
      assertThat(sections.get(2).getLabel()).isEqualTo("CONCLUSIONS");
    }
  }

  // ========== MeSH 主题词解析测试 ==========

  @Nested
  @DisplayName("MeSH 主题词解析")
  class MeshHeadingParsing {

    @Test
    @DisplayName("应解析 MeSH 主题词 Descriptor")
    void shouldParseMeshDescriptor() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <MeshHeadingList>
                <MeshHeading>
                  <DescriptorName UI="D006801" MajorTopicYN="Y">Humans</DescriptorName>
                </MeshHeading>
              </MeshHeadingList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getMeshHeadings());
      assertThat(result.getMeshHeadings()).hasSize(1);
      MeshHeading heading = result.getMeshHeadings().getFirst();
      assertNotNull(heading.getDescriptorName());
      assertEquals("D006801", heading.getDescriptorName().getUi());
      assertEquals("Humans", heading.getDescriptorName().getTerm());
      assertTrue(heading.getDescriptorName().getMajorTopic());
    }

    @Test
    @DisplayName("应解析带 Qualifier 的 MeSH 主题词")
    void shouldParseMeshWithQualifiers() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <MeshHeadingList>
                <MeshHeading>
                  <DescriptorName UI="D000328" MajorTopicYN="N">Adult</DescriptorName>
                  <QualifierName UI="Q000235" MajorTopicYN="Y">genetics</QualifierName>
                  <QualifierName UI="Q000378" MajorTopicYN="N">metabolism</QualifierName>
                </MeshHeading>
              </MeshHeadingList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      MeshHeading heading = result.getMeshHeadings().getFirst();
      assertNotNull(heading.getQualifierNames());
      assertThat(heading.getQualifierNames()).hasSize(2);
      assertEquals("Q000235", heading.getQualifierNames().get(0).getUi());
      assertEquals("genetics", heading.getQualifierNames().get(0).getTerm());
      assertTrue(heading.getQualifierNames().get(0).getMajorTopic());
    }

    @Test
    @DisplayName("应解析多个 MeSH 主题词")
    void shouldParseMultipleMeshHeadings() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <MeshHeadingList>
                <MeshHeading>
                  <DescriptorName UI="D006801">Humans</DescriptorName>
                </MeshHeading>
                <MeshHeading>
                  <DescriptorName UI="D000328">Adult</DescriptorName>
                </MeshHeading>
                <MeshHeading>
                  <DescriptorName UI="D008297">Male</DescriptorName>
                </MeshHeading>
              </MeshHeadingList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getMeshHeadings()).hasSize(3);
      assertThat(result.getMeshHeadings())
          .extracting(h -> h.getDescriptorName().getTerm())
          .containsExactly("Humans", "Adult", "Male");
    }
  }

  // ========== 补充 MeSH 概念解析测试 ==========

  @Nested
  @DisplayName("补充 MeSH 概念解析")
  class SupplMeshParsing {

    @Test
    @DisplayName("应解析 SupplMeshList 中的补充 MeSH 概念")
    void shouldParseSupplMeshNames() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article><ArticleTitle>T</ArticleTitle></Article>
              <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              <SupplMeshList>
                <SupplMeshName UI="C538003" Type="Disease">Aspirin-sensitive asthma</SupplMeshName>
                <SupplMeshName UI="C095232" Type="Protocol">FOLFOX protocol</SupplMeshName>
              </SupplMeshList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getSupplMeshNames()).hasSize(2);

      var first = result.getSupplMeshNames().get(0);
      assertThat(first.getUi()).isEqualTo("C538003");
      assertThat(first.getName()).isEqualTo("Aspirin-sensitive asthma");
      assertThat(first.getType()).isEqualTo("Disease");

      var second = result.getSupplMeshNames().get(1);
      assertThat(second.getUi()).isEqualTo("C095232");
      assertThat(second.getName()).isEqualTo("FOLFOX protocol");
      assertThat(second.getType()).isEqualTo("Protocol");
    }

    @Test
    @DisplayName("无 SupplMeshList 时应返回空列表")
    void shouldReturnEmptyListWhenNoSupplMeshList() throws Exception {
      var xml = minimalArticleXml("1", "T", "N", 2024);
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getSupplMeshNames()).isEmpty();
    }

    @Test
    @DisplayName("应忽略缺少 UI 的 SupplMeshName")
    void shouldIgnoreSupplMeshWithoutUi() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article><ArticleTitle>T</ArticleTitle></Article>
              <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              <SupplMeshList>
                <SupplMeshName Type="Disease">No UI concept</SupplMeshName>
                <SupplMeshName UI="C538003" Type="Disease">Valid concept</SupplMeshName>
              </SupplMeshList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getSupplMeshNames()).hasSize(1);
      assertThat(result.getSupplMeshNames().get(0).getUi()).isEqualTo("C538003");
    }
  }

  // ========== 关键词解析测试 ==========

  @Nested
  @DisplayName("关键词解析")
  class KeywordParsing {

    @Test
    @DisplayName("应解析单个 KeywordList 中的关键词")
    void shouldParseKeywords() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article><ArticleTitle>T</ArticleTitle></Article>
              <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              <KeywordList Owner="NOTNLM">
                <Keyword MajorTopicYN="N">Machine learning</Keyword>
                <Keyword MajorTopicYN="Y">Drug discovery</Keyword>
              </KeywordList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getKeywords());
      assertThat(result.getKeywords()).hasSize(1);

      KeywordSet keywordSet = result.getKeywords().getFirst();
      assertThat(keywordSet.getSource()).isEqualTo("NOTNLM");
      assertThat(keywordSet.getKeywords()).hasSize(2);

      Keyword first = keywordSet.getKeywords().get(0);
      assertThat(first.getTerm()).isEqualTo("Machine learning");
      assertThat(first.getMajorTopic()).isFalse();

      Keyword second = keywordSet.getKeywords().get(1);
      assertThat(second.getTerm()).isEqualTo("Drug discovery");
      assertThat(second.getMajorTopic()).isTrue();
    }

    @Test
    @DisplayName("应解析多个 KeywordList（不同 Owner）")
    void shouldParseMultipleKeywordLists() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article><ArticleTitle>T</ArticleTitle></Article>
              <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              <KeywordList Owner="NOTNLM">
                <Keyword MajorTopicYN="N">Machine learning</Keyword>
              </KeywordList>
              <KeywordList Owner="NLM">
                <Keyword MajorTopicYN="N">Artificial Intelligence</Keyword>
              </KeywordList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getKeywords()).hasSize(2);
      assertThat(result.getKeywords())
          .extracting(KeywordSet::getSource)
          .containsExactly("NOTNLM", "NLM");

      assertThat(result.getKeywords().get(0).getKeywords()).hasSize(1);
      assertThat(result.getKeywords().get(0).getKeywords().getFirst().getTerm())
          .isEqualTo("Machine learning");

      assertThat(result.getKeywords().get(1).getKeywords()).hasSize(1);
      assertThat(result.getKeywords().get(1).getKeywords().getFirst().getTerm())
          .isEqualTo("Artificial Intelligence");
    }

    @Test
    @DisplayName("无 KeywordList 时应返回空列表")
    void shouldReturnEmptyListWhenNoKeywordList() throws Exception {
      var xml = minimalArticleXml("1", "T", "N", 2024);
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getKeywords()).isEmpty();
    }

    @Test
    @DisplayName("应忽略空白关键词")
    void shouldIgnoreBlankKeywords() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article><ArticleTitle>T</ArticleTitle></Article>
              <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              <KeywordList Owner="NOTNLM">
                <Keyword MajorTopicYN="N">  </Keyword>
                <Keyword MajorTopicYN="N">Valid keyword</Keyword>
              </KeywordList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getKeywords()).hasSize(1);
      assertThat(result.getKeywords().getFirst().getKeywords()).hasSize(1);
      assertThat(result.getKeywords().getFirst().getKeywords().getFirst().getTerm())
          .isEqualTo("Valid keyword");
    }

    @Test
    @DisplayName("所有关键词为空白时应忽略整个 KeywordList")
    void shouldIgnoreKeywordListWhenAllKeywordsBlank() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article><ArticleTitle>T</ArticleTitle></Article>
              <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              <KeywordList Owner="NOTNLM">
                <Keyword MajorTopicYN="N">  </Keyword>
                <Keyword MajorTopicYN="N"></Keyword>
              </KeywordList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getKeywords()).isEmpty();
    }

    @Test
    @DisplayName("MajorTopicYN 缺失时应默认为 false")
    void shouldDefaultMajorTopicToFalseWhenMissing() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article><ArticleTitle>T</ArticleTitle></Article>
              <MedlineJournalInfo><NlmUniqueID>N</NlmUniqueID></MedlineJournalInfo>
              <KeywordList Owner="NOTNLM">
                <Keyword>No attribute keyword</Keyword>
              </KeywordList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getKeywords()).hasSize(1);
      Keyword keyword = result.getKeywords().getFirst().getKeywords().getFirst();
      assertThat(keyword.getTerm()).isEqualTo("No attribute keyword");
      assertThat(keyword.getMajorTopic()).isFalse();
    }
  }

  // ========== 发表类型解析测试 ==========

  @Nested
  @DisplayName("发表类型解析")
  class PublicationTypeParsing {

    @Test
    @DisplayName("应解析发表类型")
    void shouldParsePublicationType() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <PublicationTypeList>
                  <PublicationType UI="D016428">Journal Article</PublicationType>
                </PublicationTypeList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getPublicationTypes());
      assertThat(result.getPublicationTypes()).hasSize(1);
      PublicationType pubType = result.getPublicationTypes().getFirst();
      assertEquals("D016428", pubType.getId());
      assertEquals("Journal Article", pubType.getValue());
    }

    @Test
    @DisplayName("应解析多个发表类型")
    void shouldParseMultiplePublicationTypes() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <PublicationTypeList>
                  <PublicationType UI="D016428">Journal Article</PublicationType>
                  <PublicationType UI="D016454">Review</PublicationType>
                  <PublicationType UI="D013485">Research Support, Non-U.S. Gov't</PublicationType>
                </PublicationTypeList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getPublicationTypes()).hasSize(3);
      assertThat(result.getPublicationTypes())
          .extracting(PublicationType::getValue)
          .containsExactly("Journal Article", "Review", "Research Support, Non-U.S. Gov't");
    }
  }

  // ========== 页码解析测试 ==========

  @Nested
  @DisplayName("页码解析")
  class PaginationParsing {

    @Test
    @DisplayName("应解析 MedlinePgn 页码")
    void shouldParseMedlinePgn() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Pagination>
                  <MedlinePgn>123-145</MedlinePgn>
                </Pagination>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getPagination());
      assertEquals("123-145", result.getPagination().getMedlinePgn());
    }

    @Test
    @DisplayName("应解析电子文章编号格式的页码")
    void shouldParseElectronicArticleNumber() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Pagination>
                  <MedlinePgn>e12345</MedlinePgn>
                </Pagination>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getPagination());
      assertEquals("e12345", result.getPagination().getMedlinePgn());
    }
  }

  // ========== 完整解析测试 ==========

  @Nested
  @DisplayName("完整文章解析")
  class FullArticleParsing {

    @Test
    @DisplayName("应正确解析完整的 PubmedArticle 到 CanonicalPublication")
    void shouldParseCompleteArticle() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation Status="MEDLINE">
              <PMID Version="1">12345678</PMID>
              <Article PubModel="Print">
                <Journal>
                  <ISSN IssnType="Print">1234-5678</ISSN>
                  <ISSN IssnType="Electronic">8765-4321</ISSN>
                  <JournalIssue CitedMedium="Print">
                    <Volume>10</Volume>
                    <Issue>2</Issue>
                    <PubDate>
                      <Year>2024</Year>
                      <Month>Jun</Month>
                      <Day>15</Day>
                    </PubDate>
                  </JournalIssue>
                  <Title>Journal of Examples</Title>
                </Journal>
                <ArticleTitle>Sample Article Title</ArticleTitle>
                <VernacularTitle>样本文章标题</VernacularTitle>
                <Language>eng</Language>
                <Language>chi</Language>
                <AuthorList CompleteYN="Y">
                  <Author>
                    <LastName>Doe</LastName>
                    <ForeName>John</ForeName>
                  </Author>
                </AuthorList>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>101234567</NlmUniqueID>
                <ISSNLinking>1111-2222</ISSNLinking>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <PublicationStatus>epublish</PublicationStatus>
              <ArticleIdList>
                <ArticleId IdType="pubmed">12345678</ArticleId>
                <ArticleId IdType="doi">10.1000/example.doi</ArticleId>
                <ArticleId IdType="pmc">PMC1234567</ArticleId>
              </ArticleIdList>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);

      // 标识符
      assertThat(result.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(PublicationIdentifierType.PMID, "12345678"),
              org.assertj.core.groups.Tuple.tuple(
                  PublicationIdentifierType.DOI, "10.1000/example.doi"),
              org.assertj.core.groups.Tuple.tuple(PublicationIdentifierType.PMC, "PMC1234567"));

      // 标题
      assertEquals("Sample Article Title", result.getTitle());
      assertEquals("样本文章标题", result.getOriginalTitle());

      // 期刊信息
      Journal journal = result.getJournal();
      assertNotNull(journal);
      assertEquals("101234567", journal.getNlmUniqueId());
      assertEquals("1234-5678", journal.getIssn()); // Print 优先
      assertEquals("print", journal.getIssnType());
      assertEquals("1111-2222", journal.getIssnLinking());
      assertEquals("Journal of Examples", journal.getTitle());
      assertEquals("10", journal.getVolume());
      assertEquals("2", journal.getIssue());

      // 日期
      assertEquals(new PartialDate(2024, 6, 15, null, null), result.getDates().getPublished());

      // 语言（保留原始 ISO 639-3 代码）
      assertEquals("eng", result.getLanguage());

      // 元数据
      assertEquals("epublish", result.getPublicationStatus());
      assertTrue(result.getAuthorsComplete());
    }
  }

  // ========== 其他语言摘要（OtherAbstract）解析测试 ==========

  @Nested
  @DisplayName("OtherAbstract 解析")
  class OtherAbstractParsing {

    @Test
    @DisplayName("应解析单个 OtherAbstract")
    void shouldParseSingleOtherAbstract() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText>这是中文摘要内容。</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result.getAlternativeAbstracts());
      assertThat(result.getAlternativeAbstracts()).hasSize(1);
      var altAbstract = result.getAlternativeAbstracts().getFirst();
      assertThat(altAbstract.getLanguage()).isEqualTo("chi");
      assertThat(altAbstract.getType()).isEqualTo("Publisher");
      assertThat(altAbstract.getText()).isEqualTo("这是中文摘要内容。");
    }

    @Test
    @DisplayName("应解析多个 OtherAbstract")
    void shouldParseMultipleOtherAbstracts() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText>中文摘要。</AbstractText>
              </OtherAbstract>
              <OtherAbstract Language="jpn" Type="AIMSHP">
                <AbstractText>日本語の要約。</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAlternativeAbstracts()).hasSize(2);
      assertThat(result.getAlternativeAbstracts())
          .extracting(
              CanonicalPublication.AlternativeAbstract::getLanguage,
              CanonicalPublication.AlternativeAbstract::getType)
          .containsExactly(
              org.assertj.core.groups.Tuple.tuple("chi", "Publisher"),
              org.assertj.core.groups.Tuple.tuple("jpn", "AIMSHP"));
    }

    @Test
    @DisplayName("应解析结构化 OtherAbstract")
    void shouldParseStructuredOtherAbstract() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText Label="BACKGROUND">背景内容。</AbstractText>
                <AbstractText Label="METHODS">方法内容。</AbstractText>
                <AbstractText Label="RESULTS">结果内容。</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAlternativeAbstracts()).hasSize(1);
      var altAbstract = result.getAlternativeAbstracts().getFirst();
      assertThat(altAbstract.getLanguage()).isEqualTo("chi");
      // 结构化摘要应拼接成完整文本
      assertThat(altAbstract.getText()).contains("背景内容", "方法内容", "结果内容");
    }

    @Test
    @DisplayName("结构化 OtherAbstract 应保留段落 label 与顺序（不拍平）")
    void parseOtherAbstract_shouldCarryStructuredSections() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText Label="OBJECTIVE">目的内容。</AbstractText>
                <AbstractText Label="METHODS">方法内容。</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      var altAbstract = result.getAlternativeAbstracts().getFirst();
      assertThat(altAbstract.getSections()).hasSize(2);
      assertThat(altAbstract.getSections().getFirst().getLabel()).isEqualTo("OBJECTIVE");
      assertThat(altAbstract.getSections().getFirst().getContent()).isEqualTo("目的内容。");
      assertThat(altAbstract.getSections().get(1).getLabel()).isEqualTo("METHODS");
      // 拼接文本仍保留
      assertThat(altAbstract.getText()).isNotBlank();
    }

    @Test
    @DisplayName("无 OtherAbstract 时应返回空列表")
    void shouldReturnEmptyListWhenNoOtherAbstract() throws Exception {
      var xml = minimalArticleXml("1", "T", "N", 2024);
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAlternativeAbstracts()).isEmpty();
    }

    @Test
    @DisplayName("应忽略空的 OtherAbstract")
    void shouldIgnoreEmptyOtherAbstract() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText></AbstractText>
              </OtherAbstract>
              <OtherAbstract Language="jpn" Type="AIMSHP">
                <AbstractText>有效的日文摘要。</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      // 空摘要应被忽略，只保留有效的日文摘要
      assertThat(result.getAlternativeAbstracts()).hasSize(1);
      assertThat(result.getAlternativeAbstracts().getFirst().getLanguage()).isEqualTo("jpn");
    }

    @Test
    @DisplayName("应正确解析 OtherAbstract 的 CopyrightInformation")
    void shouldParseCopyrightInformation() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText>中文摘要。</AbstractText>
                <CopyrightInformation>© 2024 Publisher.</CopyrightInformation>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAlternativeAbstracts()).hasSize(1);
      var altAbstract = result.getAlternativeAbstracts().getFirst();
      assertThat(altAbstract.getCopyright()).isEqualTo("© 2024 Publisher.");
    }

    @Test
    @DisplayName("应处理 plain-language 类型的 OtherAbstract")
    void shouldParsePlainLanguageType() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <OtherAbstract Language="eng" Type="plain-language-summary">
                <AbstractText>Plain language summary for patients.</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAlternativeAbstracts()).hasSize(1);
      var altAbstract = result.getAlternativeAbstracts().getFirst();
      assertThat(altAbstract.getLanguage()).isEqualTo("eng");
      assertThat(altAbstract.getType()).isEqualTo("plain-language-summary");
    }
  }

  // ========== 嵌套元素跳过测试 ==========

  @Nested
  @DisplayName("嵌套未知元素跳过")
  class NestedElementSkipping {

    @Test
    @DisplayName("PMID 不应被 CommentsCorrectionsList 内的 PMID 覆盖")
    void pmidShouldNotBeOverriddenByCommentsCorrectionsList() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>12345678</PMID>
              <Article>
                <ArticleTitle>Test Title</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <CommentsCorrectionsList>
                <CommentsCorrections RefType="Cites">
                  <RefSource>Some Journal. 2020</RefSource>
                  <PMID>99999</PMID>
                </CommentsCorrections>
                <CommentsCorrections RefType="Cites">
                  <RefSource>Another Journal. 2021</RefSource>
                  <PMID>88888</PMID>
                </CommentsCorrections>
              </CommentsCorrectionsList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      // PMID 应为文献自身的 12345678，而非引用列表中的 99999 或 88888
      assertThat(result.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(PublicationIdentifierType.PMID, "12345678"));
      assertThat(result.getIdentifiers())
          .extracting(Identifier::getValue)
          .doesNotContain("99999", "88888");
    }

    @Test
    @DisplayName("ArticleIdList 不应被 ReferenceList 内的 ArticleIdList 污染")
    void articleIdListShouldNotBePollutedByReferenceList() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
            <PubmedData>
              <ArticleIdList>
                <ArticleId IdType="doi">10.1000/real.doi</ArticleId>
              </ArticleIdList>
              <ReferenceList>
                <Reference>
                  <Citation>Some Reference</Citation>
                  <ArticleIdList>
                    <ArticleId IdType="doi">10.9999/fake</ArticleId>
                  </ArticleIdList>
                </Reference>
              </ReferenceList>
            </PubmedData>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      // 应包含文献自身的 DOI
      assertThat(result.getIdentifiers())
          .extracting(Identifier::getType, Identifier::getValue)
          .contains(
              org.assertj.core.groups.Tuple.tuple(
                  PublicationIdentifierType.DOI, "10.1000/real.doi"));
      // 不应包含引用列表中的 DOI
      assertThat(result.getIdentifiers())
          .extracting(Identifier::getValue)
          .doesNotContain("10.9999/fake");
    }

    @Test
    @DisplayName("嵌套未知元素应被正确跳过，后续元素仍能正常解析")
    void nestedUnknownElementsShouldBeSkippedCorrectly() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <ChemicalList>
                <Chemical>
                  <RegistryNumber>0</RegistryNumber>
                  <NameOfSubstance UI="D000241">Adenosine</NameOfSubstance>
                </Chemical>
              </ChemicalList>
              <MedlineJournalInfo>
                <NlmUniqueID>101234567</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      // ChemicalList 应被跳过，但后续的 MedlineJournalInfo 仍应正确解析
      assertNotNull(result);
      assertNotNull(result.getJournal());
      assertEquals("101234567", result.getJournal().getNlmUniqueId());
    }
  }

  // ========== Mixed Content 解析测试 ==========

  @Nested
  @DisplayName("Mixed Content 内联标签解析")
  class MixedContentParsing {

    @Test
    @DisplayName("标题中包含 <i> 斜体标签 → 保留原样")
    void shouldPreserveItalicTagInTitle() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>Role of <i>Helicobacter pylori</i> in gastric cancer</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getTitle())
          .isEqualTo("Role of <i>Helicobacter pylori</i> in gastric cancer");
    }

    @Test
    @DisplayName("摘要中包含 <sub>/<sup> 上下标标签 → 保留原样")
    void shouldPreserveSubSupTagsInAbstract() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
                <Abstract>
                  <AbstractText>The concentration of H<sub>2</sub>O<sub>2</sub> was 10<sup>-3</sup> mol/L.</AbstractText>
                </Abstract>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAbstractContent().getSections()).hasSize(1);
      assertThat(result.getAbstractContent().getSections().getFirst().getContent())
          .isEqualTo("The concentration of H<sub>2</sub>O<sub>2</sub> was 10<sup>-3</sup> mol/L.");
    }

    @Test
    @DisplayName("关键词中包含内联标签 → 保留原样")
    void shouldPreserveInlineTagsInKeyword() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <KeywordList Owner="NOTNLM">
                <Keyword><i>Staphylococcus aureus</i> infection</Keyword>
              </KeywordList>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getKeywords()).hasSize(1);
      assertThat(result.getKeywords().getFirst().getKeywords())
          .extracting(Keyword::getTerm)
          .containsExactly("<i>Staphylococcus aureus</i> infection");
    }

    @Test
    @DisplayName("OtherAbstract 中包含内联标签 → 保留原样")
    void shouldPreserveInlineTagsInOtherAbstract() throws Exception {
      var xml =
          """
          <PubmedArticle>
            <MedlineCitation>
              <PMID>1</PMID>
              <Article>
                <ArticleTitle>T</ArticleTitle>
              </Article>
              <MedlineJournalInfo>
                <NlmUniqueID>N</NlmUniqueID>
              </MedlineJournalInfo>
              <OtherAbstract Language="chi" Type="Publisher">
                <AbstractText><b>目的</b>：研究 <i>E. coli</i> 的耐药性。</AbstractText>
              </OtherAbstract>
            </MedlineCitation>
          </PubmedArticle>
          """;
      var reader = createReaderAtStartElement(xml);

      CanonicalPublication result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertThat(result.getAlternativeAbstracts()).hasSize(1);
      assertThat(result.getAlternativeAbstracts().getFirst().getText())
          .isEqualTo("<b>目的</b>：研究 <i>E. coli</i> 的耐药性。");
    }
  }

  // ========== 辅助方法 ==========

  /// 创建最小有效的 PubmedArticle XML。
  private String minimalArticleXml(String pmid, String title, String nlmId, int year) {
    return """
        <PubmedArticle>
          <MedlineCitation>
            <PMID>%s</PMID>
            <Article>
              <Journal>
                <JournalIssue>
                  <PubDate>
                    <Year>%d</Year>
                  </PubDate>
                </JournalIssue>
              </Journal>
              <ArticleTitle>%s</ArticleTitle>
            </Article>
            <MedlineJournalInfo>
              <NlmUniqueID>%s</NlmUniqueID>
            </MedlineJournalInfo>
          </MedlineCitation>
        </PubmedArticle>
        """
        .formatted(pmid, year, title, nlmId);
  }

  /// 创建定位到起始元素的 XMLStreamReader。
  private XMLStreamReader createReaderAtStartElement(String xml) throws XMLStreamException {
    var reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(xml));
    while (reader.hasNext()) {
      if (reader.next() == XMLStreamConstants.START_ELEMENT) {
        break;
      }
    }
    return reader;
  }
}
