package com.patra.starter.provenance.pubmed.converter;

import static org.assertj.core.api.Assertions.*;

import com.patra.common.model.CanonicalPublication;
import com.patra.starter.provenance.pubmed.model.response.PubmedPublication;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.xml.XmlMapper;

/// PubmedPublicationConverter 单元测试
///
/// @author linqibin
@DisplayName("PubmedPublicationConverter 测试")
class PubmedPublicationConverterTest {

  private PubmedPublicationConverter converter;
  private XmlMapper xmlMapper;

  @BeforeEach
  void setUp() {
    converter = new PubmedPublicationConverter();
    xmlMapper = new XmlMapper();
  }

  @Test
  @DisplayName("toCanonicalPublication - null文章返回null")
  void toCanonicalPublication_shouldReturnNull_whenArticleIsNull() {
    // Act
    CanonicalPublication result = converter.toCanonicalPublication(null);

    // Assert
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("toCanonicalPublication - 完整文章转换成功")
  void toCanonicalPublication_shouldConvertCompleteArticle() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

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
    assertThat(result.getAuthors().get(0).getAffiliations().get(0).getName())
        .isEqualTo("University A");
    assertThat(result.getAuthors().get(1).getLastName()).isEqualTo("Doe");
    assertThat(result.getJournal()).isNotNull();
    assertThat(result.getJournal().getTitle()).isEqualTo("Medical Journal");
    assertThat(result.getJournal().getIssn()).isEqualTo("1234-5678");
    assertThat(result.getIdentifiers()).hasSize(3);
    assertThat(result.getIdentifiers())
        .extracting("type", "value")
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
  @DisplayName("toCanonicalPublication - 仅包含标题的最小文章")
  void toCanonicalPublication_shouldConvertMinimalArticle_withOnlyTitle() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
          <MedlineCitation>
            <PMID>99999999</PMID>
            <Article>
              <ArticleTitle>Minimal Article</ArticleTitle>
            </Article>
          </MedlineCitation>
          <PubmedData>
          </PubmedData>
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

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
  @DisplayName("toCanonicalPublication - 无标签的摘要正确提取")
  void toCanonicalPublication_shouldExtractAbstract_withoutLabels() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getAbstractContent()).isNotNull();
    assertThat(result.getAbstractContent().getText()).isEqualTo("Simple abstract without labels.");
  }

  @Test
  @DisplayName("toCanonicalPublication - 空摘要返回null")
  void toCanonicalPublication_shouldReturnNullAbstract_whenAbstractIsEmpty() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
          <MedlineCitation>
            <PMID>22222222</PMID>
            <Article>
              <ArticleTitle>Title</ArticleTitle>
              <Abstract>
              </Abstract>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getAbstractContent()).isNull();
  }

  @Test
  @DisplayName("toCanonicalPublication - 作者无机构信息")
  void toCanonicalPublication_shouldConvertAuthors_withoutAffiliation() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getAuthors()).hasSize(1);
    assertThat(result.getAuthors().get(0).getLastName()).isEqualTo("Brown");
    assertThat(result.getAuthors().get(0).getForeName()).isEqualTo("Alice");
    assertThat(result.getAuthors().get(0).getAffiliations()).isNull();
  }

  @Test
  @DisplayName("toCanonicalPublication - 期刊信息从MedlineJournalInfo提取")
  void toCanonicalPublication_shouldExtractJournal_fromMedlineJournalInfo() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getJournal()).isNotNull();
    assertThat(result.getJournal().getTitle()).isEqualTo("Med J");
    assertThat(result.getJournal().getIssn()).isEqualTo("9876-5432");
  }

  @Test
  @DisplayName("toCanonicalPublication - 期刊信息优先从Journal提取")
  void toCanonicalPublication_shouldPreferJournal_overMedlineJournalInfo() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getJournal()).isNotNull();
    assertThat(result.getJournal().getTitle()).isEqualTo("Primary Journal");
    assertThat(result.getJournal().getIssn()).isEqualTo("1111-2222");
  }

  @Test
  @DisplayName("toCanonicalPublication - 标识符包含PMID、DOI、PMC")
  void toCanonicalPublication_shouldBuildIdentifiers_withPmidDoiPmc() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getIdentifiers()).hasSize(3);
    assertThat(result.getIdentifiers())
        .extracting("type", "value")
        .contains(
            tuple("pmid", "66666666"), tuple("doi", "10.9999/example"), tuple("pmc", "PMC9999999"));
  }

  @Test
  @DisplayName("toCanonicalPublication - PMC类型同时支持pmc和pmcid")
  void toCanonicalPublication_shouldSupportBothPmcAndPmcid() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getIdentifiers())
        .extracting("type", "value")
        .contains(tuple("pmc", "PMC7777777"));
  }

  @Test
  @DisplayName("toCanonicalPublication - 发表日期仅年份")
  void toCanonicalPublication_shouldParsePublicationDate_withYearOnly() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2022, 1, 1));
  }

  @Test
  @DisplayName("toCanonicalPublication - 发表日期包含年月（完整月份名称）")
  void toCanonicalPublication_shouldParsePublicationDate_withYearAndMonth() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2021, 6, 1));
  }

  @Test
  @DisplayName("toCanonicalPublication - 发表日期月份为数字")
  void toCanonicalPublication_shouldParsePublicationDate_withNumericMonth() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2020, 3, 25));
  }

  @Test
  @DisplayName("toCanonicalPublication - 发表日期无效时返回null")
  void toCanonicalPublication_shouldReturnNullPublicationDate_whenDateIsInvalid() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getDates()).isNull();
  }

  @Test
  @DisplayName("toCanonicalPublication - 月份超出范围时限制为1-12")
  void toCanonicalPublication_shouldClampMonth_whenOutOfRange() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2023, 12, 1));
  }

  @Test
  @DisplayName("toCanonicalPublication - 日期超出范围时限制为1-28")
  void toCanonicalPublication_shouldClampDay_whenOutOfRange() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2023, 1, 28));
  }

  @Test
  @DisplayName("toCanonicalPublication - 关键词过滤空白值")
  void toCanonicalPublication_shouldFilterBlankKeywords() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getKeywords()).hasSize(1);
    assertThat(result.getKeywords().get(0).getKeywords())
        .extracting("term")
        .containsExactly("valid keyword", "another valid");
  }

  @Test
  @DisplayName("toCanonicalPublication - 空关键词列表返回空集合")
  void toCanonicalPublication_shouldReturnEmptyKeywords_whenKeywordListIsEmpty() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
          <MedlineCitation>
            <PMID>99999996</PMID>
            <Article>
              <ArticleTitle>Title</ArticleTitle>
            </Article>
            <KeywordList>
            </KeywordList>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getKeywords()).isNull();
  }

  @Test
  @DisplayName("toCanonicalPublication - 作者有多个机构时仅取第一个")
  void toCanonicalPublication_shouldUseFirstAffiliation_whenMultipleAffiliationsExist()
      throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getAuthors()).hasSize(1);
    assertThat(result.getAuthors().get(0).getAffiliations()).hasSize(2);
    assertThat(result.getAuthors().get(0).getAffiliations().get(0).getName())
        .isEqualTo("First Affiliation");
    assertThat(result.getAuthors().get(0).getAffiliations().get(1).getName())
        .isEqualTo("Second Affiliation");
  }

  @Test
  @DisplayName("toCanonicalPublication - 机构标识符正确提取")
  void toCanonicalPublication_shouldExtractAffiliationIdentifiers() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
          <MedlineCitation>
            <PMID>99999999</PMID>
            <Article>
              <ArticleTitle>Title with Affiliation Identifiers</ArticleTitle>
              <AuthorList>
                <Author>
                  <LastName>Wang</LastName>
                  <ForeName>Li</ForeName>
                  <AffiliationInfo>
                    <Affiliation>Peking University, Beijing, China</Affiliation>
                    <Identifier Source="ROR">03vek6s52</Identifier>
                    <Identifier Source="Ringgold">12345</Identifier>
                  </AffiliationInfo>
                </Author>
              </AuthorList>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getAuthors()).hasSize(1);
    assertThat(result.getAuthors().get(0).getAffiliations()).hasSize(1);

    var affiliation = result.getAuthors().get(0).getAffiliations().get(0);
    assertThat(affiliation.getName()).isEqualTo("Peking University, Beijing, China");
    assertThat(affiliation.getIdentifiers()).hasSize(2);
    assertThat(affiliation.getIdentifiers())
        .extracting("type", "value")
        .contains(tuple("ror", "03vek6s52"), tuple("ringgold", "12345"));
  }

  @Test
  @DisplayName("toCanonicalPublication - 多机构各有独立标识符")
  void toCanonicalPublication_shouldExtractIdentifiersForEachAffiliation() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
          <MedlineCitation>
            <PMID>88888888</PMID>
            <Article>
              <ArticleTitle>Multi-Affiliation with Identifiers</ArticleTitle>
              <AuthorList>
                <Author>
                  <LastName>Zhang</LastName>
                  <ForeName>Wei</ForeName>
                  <AffiliationInfo>
                    <Affiliation>Tsinghua University</Affiliation>
                    <Identifier Source="ROR">00k4n6c32</Identifier>
                  </AffiliationInfo>
                  <AffiliationInfo>
                    <Affiliation>Harvard Medical School</Affiliation>
                    <Identifier Source="Ringgold">67890</Identifier>
                    <Identifier Source="GRID">grid.38142.3c</Identifier>
                  </AffiliationInfo>
                </Author>
              </AuthorList>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getAuthors()).hasSize(1);
    var affiliations = result.getAuthors().get(0).getAffiliations();
    assertThat(affiliations).hasSize(2);

    // 第一个机构：清华大学，只有 ROR
    assertThat(affiliations.get(0).getName()).isEqualTo("Tsinghua University");
    assertThat(affiliations.get(0).getIdentifiers()).hasSize(1);
    assertThat(affiliations.get(0).getIdentifiers().get(0).getType()).isEqualTo("ror");
    assertThat(affiliations.get(0).getIdentifiers().get(0).getValue()).isEqualTo("00k4n6c32");

    // 第二个机构：哈佛医学院，有 Ringgold 和 GRID
    assertThat(affiliations.get(1).getName()).isEqualTo("Harvard Medical School");
    assertThat(affiliations.get(1).getIdentifiers()).hasSize(2);
    assertThat(affiliations.get(1).getIdentifiers())
        .extracting("type", "value")
        .contains(tuple("ringgold", "67890"), tuple("grid", "grid.38142.3c"));
  }

  @Test
  @DisplayName("toCanonicalPublication - 机构无标识符时identifiers为null")
  void toCanonicalPublication_shouldReturnNullIdentifiers_whenNoIdentifiersProvided()
      throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
          <MedlineCitation>
            <PMID>77777777</PMID>
            <Article>
              <ArticleTitle>No Identifiers</ArticleTitle>
              <AuthorList>
                <Author>
                  <LastName>Liu</LastName>
                  <ForeName>Ming</ForeName>
                  <AffiliationInfo>
                    <Affiliation>Unknown University</Affiliation>
                  </AffiliationInfo>
                </Author>
              </AuthorList>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getAuthors()).hasSize(1);
    assertThat(result.getAuthors().get(0).getAffiliations()).hasSize(1);
    assertThat(result.getAuthors().get(0).getAffiliations().get(0).getName())
        .isEqualTo("Unknown University");
    assertThat(result.getAuthors().get(0).getAffiliations().get(0).getIdentifiers()).isNull();
  }

  @Test
  @DisplayName("toCanonicalPublication - 标识符类型转换为小写")
  void toCanonicalPublication_shouldLowercaseIdentifierType() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
          <MedlineCitation>
            <PMID>66666666</PMID>
            <Article>
              <ArticleTitle>Uppercase Identifier Source</ArticleTitle>
              <AuthorList>
                <Author>
                  <LastName>Chen</LastName>
                  <ForeName>Hui</ForeName>
                  <AffiliationInfo>
                    <Affiliation>Some University</Affiliation>
                    <Identifier Source="ROR">abc123</Identifier>
                    <Identifier Source="RINGGOLD">789</Identifier>
                  </AffiliationInfo>
                </Author>
              </AuthorList>
            </Article>
          </MedlineCitation>
          <PubmedData></PubmedData>
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    var identifiers = result.getAuthors().get(0).getAffiliations().get(0).getIdentifiers();
    assertThat(identifiers).hasSize(2);
    // 验证类型已转换为小写
    assertThat(identifiers).extracting("type").containsExactly("ror", "ringgold");
  }

  @Test
  @DisplayName("toCanonicalPublication - 无效月份名称时默认为1月")
  void toCanonicalPublication_shouldDefaultToJanuary_whenMonthNameIsInvalid() throws Exception {
    // Arrange
    String xml =
        """
        <PubmedPublication>
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
        </PubmedPublication>
        """;
    PubmedPublication article = xmlMapper.readValue(xml, PubmedPublication.class);

    // Act
    CanonicalPublication result = converter.toCanonicalPublication(article);

    // Assert
    assertThat(result.getDates()).isNotNull();
    assertThat(result.getDates().getPublished()).isEqualTo(LocalDate.of(2023, 1, 1));
  }
}
