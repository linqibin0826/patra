package com.patra.starter.provenance.pubmed.model.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.patra.starter.provenance.pubmed.model.response.PubmedData.ArticleId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EFetchResponseParserTest {

  private static final XmlMapper XML_MAPPER = createXmlMapper();

  private static XmlMapper createXmlMapper() {
    return XmlMapper.builder()
        .findAndAddModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        .defaultUseWrapper(false)
        .build();
  }

  @Test
  @DisplayName("fromXml() should extract core article metadata")
  void shouldExtractCoreArticleMetadata() throws Exception {
    String payload =
        """
        <PubmedArticleSet>
          <PubmedArticle>
            <MedlineCitation>
              <PMID>41048095</PMID>
              <Article>
                <Journal>
                  <ISSN IssnType="Print">1879-3479</ISSN>
                  <JournalIssue>
                    <PubDate>
                      <Year>2024</Year>
                      <Month>Jun</Month>
                      <Day>01</Day>
                    </PubDate>
                  </JournalIssue>
                  <Title>International journal of gynaecology and obstetrics</Title>
                  <ISOAbbreviation>Int J Gynaecol Obstet</ISOAbbreviation>
                </Journal>
                <ArticleTitle>Sample Article</ArticleTitle>
                <Abstract>
                  <AbstractText Label="OBJECTIVE">Objective text</AbstractText>
                  <AbstractText>General paragraph</AbstractText>
                </Abstract>
                <Language>eng</Language>
                <AuthorList>
                  <Author>
                    <LastName>Doe</LastName>
                    <ForeName>John</ForeName>
                    <Initials>J</Initials>
                    <AffiliationInfo>
                      <Affiliation>Example University</Affiliation>
                    </AffiliationInfo>
                    <Identifier Source="ORCID">0000-0001</Identifier>
                  </Author>
                </AuthorList>
                <PublicationTypeList>
                  <PublicationType>Journal Article</PublicationType>
                </PublicationTypeList>
              </Article>
              <MedlineJournalInfo>
                <MedlineTA>Int J Gynaecol Obstet</MedlineTA>
                <Country>United States</Country>
                <NlmUniqueID>0210174</NlmUniqueID>
                <ISSNLinking>0020-7292</ISSNLinking>
              </MedlineJournalInfo>
              <KeywordList>
                <Keyword>Obstetrics</Keyword>
                <Keyword>Gynecology</Keyword>
              </KeywordList>
            </MedlineCitation>
            <PubmedData>
              <PublicationStatus>ppublish</PublicationStatus>
              <History>
                <PubMedPubDate PubStatus="received">
                  <Year>2024</Year>
                  <Month>06</Month>
                  <Day>01</Day>
                </PubMedPubDate>
              </History>
              <ArticleIdList>
                <ArticleId IdType="doi">10.1000/sample</ArticleId>
                <ArticleId IdType="pmc">PMC123456</ArticleId>
              </ArticleIdList>
            </PubmedData>
          </PubmedArticle>
        </PubmedArticleSet>
        """;

    EFetchResponse response = EFetchResponse.fromXml(XML_MAPPER, payload);

    assertThat(response.articles()).hasSize(1);
    PubmedArticle article = response.articles().get(0);

    assertThat(article.pmid()).isEqualTo("41048095");
    assertThat(article.article()).isNotNull();
    assertThat(article.article().title()).isEqualTo("Sample Article");
    assertThat(article.article().journal().issn()).isEqualTo("1879-3479");
    assertThat(article.article().abstractSections())
        .extracting(Article.AbstractSection::text)
        .containsExactly("Objective text", "General paragraph");
    assertThat(article.article().authors())
        .singleElement()
        .satisfies(
            author -> {
              assertThat(author.lastName()).isEqualTo("Doe");
              assertThat(author.affiliations()).containsExactly("Example University");
              assertThat(author.identifier()).isEqualTo("0000-0001");
              assertThat(author.identifierSource()).isEqualTo("ORCID");
            });
    assertThat(article.pubmedData().publicationStatus()).isEqualTo("ppublish");
    assertThat(article.pubmedData().history()).hasSize(1);
    assertThat(article.articleIds())
        .extracting(id -> id.type().toLowerCase(), ArticleId::value)
        .containsExactlyInAnyOrder(tuple("doi", "10.1000/sample"), tuple("pmc", "PMC123456"));
    assertThat(article.keywords()).containsExactly("Obstetrics", "Gynecology");
  }

  @Test
  @DisplayName("fromUidListText() should parse uid list payload")
  void shouldParseUidListPayload() throws Exception {
    String payload = "123\n456\n789\n";

    EFetchResponse response = EFetchResponse.fromUidListText(payload);

    assertThat(response.articles()).isEmpty();
    assertThat(response.uids()).containsExactly("123", "456", "789");
  }
}
