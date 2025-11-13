package com.patra.starter.provenance.pubmed.converter;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.patra.common.model.CanonicalLiterature;
import com.patra.starter.provenance.pubmed.model.response.PubmedLiterature;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PubmedLiteratureConverter 单元测试
 *
 * @author linqibin
 */
@DisplayName("PubmedLiteratureConverter 测试")
class PubmedLiteratureConverterTest {

  private PubmedLiteratureConverter converter;
  private XmlMapper xmlMapper;

  @BeforeEach
  void setUp() {
    converter = new PubmedLiteratureConverter();
    xmlMapper = new XmlMapper();
  }

  @Test
  @DisplayName("toCanonicalLiterature - null文章返回null")
  void toCanonicalLiterature_shouldReturnNull_whenArticleIsNull() {
    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(null);

    // Assert
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("toCanonicalLiterature - 完整文章转换成功")
  void toCanonicalLiterature_shouldConvertCompleteArticle() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>12345678</PMID>
            <Article>
              <Journal>
                <ISSN IssnType="Electronic">1234-5678</ISSN>
                <Title>Medical Journal</Title>
                <JournalIssue>
                  <PubDate>
                    <Year>2023</Year>
                    <Month>DECEMBER</Month>
                    <Day>15</Day>
                  </PubDate>
                </JournalIssue>
              </Journal>
              <ArticleTitle>Test Article Title</ArticleTitle>
              <Abstract>
                <AbstractText Label="BACKGROUND">Background text</AbstractText>
                <AbstractText Label="METHODS">Methods text</AbstractText>
              </Abstract>
              <AuthorList>
                <Author>
                  <LastName>Smith</LastName>
                  <ForeName>John</ForeName>
                  <AffiliationInfo>
                    <Affiliation>University A</Affiliation>
                  </AffiliationInfo>
                </Author>
                <Author>
                  <LastName>Doe</LastName>
                  <ForeName>Jane</ForeName>
                  <AffiliationInfo>
                    <Affiliation>University B</Affiliation>
                  </AffiliationInfo>
                </Author>
              </AuthorList>
            </Article>
            <KeywordList>
              <Keyword>cancer</Keyword>
              <Keyword>treatment</Keyword>
            </KeywordList>
          </MedlineCitation>
          <PubmedData>
            <ArticleIdList>
              <ArticleId IdType="doi">10.1234/test.2023.001</ArticleId>
              <ArticleId IdType="pmc">PMC1234567</ArticleId>
            </ArticleIdList>
          </PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getTitle()).isEqualTo("Test Article Title");
    assertThat(result.getAbstractContent()).isNotNull();
    assertThat(result.getAbstractContent().getText())
        .isEqualTo("BACKGROUND: Background text\nMETHODS: Methods text");
    assertThat(result.getAbstractContent().getSections()).hasSize(2);
    assertThat(result.getAuthors()).hasSize(2);
    assertThat(result.getAuthors().get(0).getLastName()).isEqualTo("Smith");
    assertThat(result.getAuthors().get(0).getForeName()).isEqualTo("John");
    assertThat(result.getAuthors().get(0).getAffiliations()).hasSize(1);
    assertThat(result.getAuthors().get(0).getAffiliations().get(0).getName()).isEqualTo("University A");
    assertThat(result.getAuthors().get(1).getLastName()).isEqualTo("Doe");
    assertThat(result.getJournal()).isNotNull();
    assertThat(result.getJournal().getTitle()).isEqualTo("Medical Journal");
    assertThat(result.getJournal().getIssn()).isEqualTo("1234-5678");
    assertThat(result.getIdentifiers()).hasSize(3);
    assertThat(result.getIdentifiers()).extracting("type", "value")
        .contains(
            tuple("pmid", "12345678"),
            tuple("doi", "10.1234/test.2023.001"),
            tuple("pmc", "PMC1234567"));
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2023, 12, 15));
    assertThat(result.getKeywords()).hasSize(1);
    assertThat(result.getKeywords().get(0).getKeywords())
        .extracting("term")
        .containsExactly("cancer", "treatment");
  }

  @Test
  @DisplayName("toCanonicalLiterature - 仅包含标题的最小文章")
  void toCanonicalLiterature_shouldConvertMinimalArticle_withOnlyTitle() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>99999999</PMID>
            <Article>
              <ArticleTitle>Minimal Article</ArticleTitle>
            </Article>
          </MedlineCitation>
          <PubmedData>
          </PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getTitle()).isEqualTo("Minimal Article");
    assertThat(result.getAbstractContent()).isNull();
    assertThat(result.getAuthors()).isEmpty();
    assertThat(result.getJournal()).isNull();
    assertThat(result.getIdentifiers()).hasSize(1);
    assertThat(result.getIdentifiers().get(0).getType()).isEqualTo("pmid");
    assertThat(result.getIdentifiers().get(0).getValue()).isEqualTo("99999999");
    assertThat(result.getDates()).isNull();
    assertThat(result.getKeywords()).isNull();
  }

  @Test
  @DisplayName("toCanonicalLiterature - 无标签的摘要正确提取")
  void toCanonicalLiterature_shouldExtractAbstract_withoutLabels() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>11111111</PMID>
            <Article>
              <ArticleTitle>Title</ArticleTitle>
              <Abstract>
                <AbstractText>Simple abstract without labels.</AbstractText>
              </Abstract>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getAbstractContent()).isNotNull();
    assertThat(result.getAbstractContent().getText()).isEqualTo("Simple abstract without labels.");
  }

  @Test
  @DisplayName("toCanonicalLiterature - 空摘要返回null")
  void toCanonicalLiterature_shouldReturnNullAbstract_whenAbstractIsEmpty() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>22222222</PMID>
            <Article>
              <ArticleTitle>Title</ArticleTitle>
              <Abstract>
              </Abstract>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getAbstractContent()).isNull();
  }

  @Test
  @DisplayName("toCanonicalLiterature - 作者无机构信息")
  void toCanonicalLiterature_shouldConvertAuthors_withoutAffiliation() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>33333333</PMID>
            <Article>
              <ArticleTitle>Title</ArticleTitle>
              <AuthorList>
                <Author>
                  <LastName>Brown</LastName>
                  <ForeName>Alice</ForeName>
                </Author>
              </AuthorList>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getAuthors()).hasSize(1);
    assertThat(result.getAuthors().get(0).getLastName()).isEqualTo("Brown");
    assertThat(result.getAuthors().get(0).getForeName()).isEqualTo("Alice");
    assertThat(result.getAuthors().get(0).getAffiliations()).isNull();
  }

  @Test
  @DisplayName("toCanonicalLiterature - 期刊信息从MedlineJournalInfo提取")
  void toCanonicalLiterature_shouldExtractJournal_fromMedlineJournalInfo() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>44444444</PMID>
            <Article>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
            <MedlineJournalInfo>
              <MedlineTA>Med J</MedlineTA>
              <ISSNLinking>9876-5432</ISSNLinking>
            </MedlineJournalInfo>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getJournal()).isNotNull();
    assertThat(result.getJournal().getTitle()).isEqualTo("Med J");
    assertThat(result.getJournal().getIssn()).isEqualTo("9876-5432");
  }

  @Test
  @DisplayName("toCanonicalLiterature - 期刊信息优先从Journal提取")
  void toCanonicalLiterature_shouldPreferJournal_overMedlineJournalInfo() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>55555555</PMID>
            <Article>
              <Journal>
                <ISSN IssnType="Print">1111-2222</ISSN>
                <Title>Primary Journal</Title>
                <JournalIssue>
                  <PubDate>
                    <Year>2023</Year>
                  </PubDate>
                </JournalIssue>
              </Journal>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
            <MedlineJournalInfo>
              <MedlineTA>Secondary Journal</MedlineTA>
              <ISSNLinking>3333-4444</ISSNLinking>
            </MedlineJournalInfo>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getJournal()).isNotNull();
    assertThat(result.getJournal().getTitle()).isEqualTo("Primary Journal");
    assertThat(result.getJournal().getIssn()).isEqualTo("1111-2222");
  }

  @Test
  @DisplayName("toCanonicalLiterature - 标识符包含PMID、DOI、PMC")
  void toCanonicalLiterature_shouldBuildIdentifiers_withPmidDoiPmc() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>66666666</PMID>
            <Article>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
          </MedlineCitation>
          <PubmedData>
            <ArticleIdList>
              <ArticleId IdType="doi">10.9999/example</ArticleId>
              <ArticleId IdType="pmcid">PMC9999999</ArticleId>
              <ArticleId IdType="pubmed">66666666</ArticleId>
            </ArticleIdList>
          </PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getIdentifiers()).hasSize(3);
    assertThat(result.getIdentifiers()).extracting("type", "value")
        .contains(
            tuple("pmid", "66666666"),
            tuple("doi", "10.9999/example"),
            tuple("pmc", "PMC9999999"));
  }

  @Test
  @DisplayName("toCanonicalLiterature - PMC类型同时支持pmc和pmcid")
  void toCanonicalLiterature_shouldSupportBothPmcAndPmcid() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>77777777</PMID>
            <Article>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
          </MedlineCitation>
          <PubmedData>
            <ArticleIdList>
              <ArticleId IdType="pmc">PMC7777777</ArticleId>
            </ArticleIdList>
          </PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getIdentifiers()).extracting("type", "value")
        .contains(tuple("pmc", "PMC7777777"));
  }

  @Test
  @DisplayName("toCanonicalLiterature - 发表日期仅年份")
  void toCanonicalLiterature_shouldParsePublicationDate_withYearOnly() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>88888888</PMID>
            <Article>
              <Journal>
                <JournalIssue>
                  <PubDate>
                    <Year>2022</Year>
                  </PubDate>
                </JournalIssue>
              </Journal>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2022, 1, 1));
  }

  @Test
  @DisplayName("toCanonicalLiterature - 发表日期包含年月（完整月份名称）")
  void toCanonicalLiterature_shouldParsePublicationDate_withYearAndMonth() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>99999990</PMID>
            <Article>
              <Journal>
                <JournalIssue>
                  <PubDate>
                    <Year>2021</Year>
                    <Month>JUNE</Month>
                  </PubDate>
                </JournalIssue>
              </Journal>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2021, 6, 1));
  }

  @Test
  @DisplayName("toCanonicalLiterature - 发表日期月份为数字")
  void toCanonicalLiterature_shouldParsePublicationDate_withNumericMonth() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>99999991</PMID>
            <Article>
              <Journal>
                <JournalIssue>
                  <PubDate>
                    <Year>2020</Year>
                    <Month>3</Month>
                    <Day>25</Day>
                  </PubDate>
                </JournalIssue>
              </Journal>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2020, 3, 25));
  }

  @Test
  @DisplayName("toCanonicalLiterature - 发表日期无效时返回null")
  void toCanonicalLiterature_shouldReturnNullPublicationDate_whenDateIsInvalid() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>99999992</PMID>
            <Article>
              <Journal>
                <JournalIssue>
                  <PubDate>
                    <Year>invalid</Year>
                  </PubDate>
                </JournalIssue>
              </Journal>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getDates()).isNull();
  }

  @Test
  @DisplayName("toCanonicalLiterature - 月份超出范围时限制为1-12")
  void toCanonicalLiterature_shouldClampMonth_whenOutOfRange() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>99999993</PMID>
            <Article>
              <Journal>
                <JournalIssue>
                  <PubDate>
                    <Year>2023</Year>
                    <Month>15</Month>
                  </PubDate>
                </JournalIssue>
              </Journal>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2023, 12, 1));
  }

  @Test
  @DisplayName("toCanonicalLiterature - 日期超出范围时限制为1-28")
  void toCanonicalLiterature_shouldClampDay_whenOutOfRange() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>99999994</PMID>
            <Article>
              <Journal>
                <JournalIssue>
                  <PubDate>
                    <Year>2023</Year>
                    <Month>1</Month>
                    <Day>35</Day>
                  </PubDate>
                </JournalIssue>
              </Journal>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2023, 1, 28));
  }

  @Test
  @DisplayName("toCanonicalLiterature - 关键词过滤空白值")
  void toCanonicalLiterature_shouldFilterBlankKeywords() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>99999995</PMID>
            <Article>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
            <KeywordList>
              <Keyword>valid keyword</Keyword>
              <Keyword>   </Keyword>
              <Keyword>another valid</Keyword>
            </KeywordList>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getKeywords()).hasSize(1);
    assertThat(result.getKeywords().get(0).getKeywords())
        .extracting("term")
        .containsExactly("valid keyword", "another valid");
  }

  @Test
  @DisplayName("toCanonicalLiterature - 空关键词列表返回空集合")
  void toCanonicalLiterature_shouldReturnEmptyKeywords_whenKeywordListIsEmpty() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>99999996</PMID>
            <Article>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
            <KeywordList>
            </KeywordList>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getKeywords()).isNull();
  }

  @Test
  @DisplayName("toCanonicalLiterature - 作者有多个机构时仅取第一个")
  void toCanonicalLiterature_shouldUseFirstAffiliation_whenMultipleAffiliationsExist()
      throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>99999997</PMID>
            <Article>
              <ArticleTitle>Title</ArticleTitle>
              <AuthorList>
                <Author>
                  <LastName>Johnson</LastName>
                  <ForeName>Mark</ForeName>
                  <AffiliationInfo>
                    <Affiliation>First Affiliation</Affiliation>
                  </AffiliationInfo>
                  <AffiliationInfo>
                    <Affiliation>Second Affiliation</Affiliation>
                  </AffiliationInfo>
                </Author>
              </AuthorList>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getAuthors()).hasSize(1);
    assertThat(result.getAuthors().get(0).getAffiliations()).hasSize(2);
    assertThat(result.getAuthors().get(0).getAffiliations().get(0).getName()).isEqualTo("First Affiliation");
    assertThat(result.getAuthors().get(0).getAffiliations().get(1).getName()).isEqualTo("Second Affiliation");
  }

  @Test
  @DisplayName("toCanonicalLiterature - 无效月份名称时默认为1月")
  void toCanonicalLiterature_shouldDefaultToJanuary_whenMonthNameIsInvalid() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedLiterature>
          <MedlineCitation>
            <PMID>99999998</PMID>
            <Article>
              <Journal>
                <JournalIssue>
                  <PubDate>
                    <Year>2023</Year>
                    <Month>InvalidMonth</Month>
                  </PubDate>
                </JournalIssue>
              </Journal>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedLiterature>
        """;
    PubmedLiterature article = xmlMapper.readValue(xml, PubmedLiterature.class);

    // Act
    CanonicalLiterature result = converter.toCanonicalLiterature(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2023, 1, 1));
  }
}
