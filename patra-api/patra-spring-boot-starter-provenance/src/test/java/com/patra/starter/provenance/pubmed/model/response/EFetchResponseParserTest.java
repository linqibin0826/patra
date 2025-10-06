package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EFetchResponseParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldExtractCoreArticleMetadata() throws Exception {
        String payload = """
            {
              \"PubmedArticle\": [
                {
                  \"MedlineCitation\": {
                    \"PMID\": {\"#text\": \"41048095\"},
                    \"Article\": {
                      \"Journal\": {
                        \"ISSN\": {\"@IssnType\": \"Print\", \"#text\": \"1879-3479\"},
                        \"Title\": \"International journal of gynaecology and obstetrics\",\n                        \"ISOAbbreviation\": \"Int J Gynaecol Obstet\"
                      },
                      \"ArticleTitle\": \"Sample Article\",
                      \"Abstract\": {
                        \"AbstractText\": [
                          {\"@Label\": \"OBJECTIVE\", \"#text\": \"Objective text\"},
                          {\"#text\": \"General paragraph\"}
                        ]
                      },
                      \"Language\": \"eng\",
                      \"AuthorList\": {
                        \"Author\": [
                          {
                            \"LastName\": \"Doe\",
                            \"ForeName\": \"John\",
                            \"Initials\": \"J\",
                            \"AffiliationInfo\": [{\"Affiliation\": \"Example University\"}],
                            \"Identifier\": {\"@Source\": \"ORCID\", \"#text\": \"0000-0001\"}
                          }
                        ]
                      },
                      \"PublicationTypeList\": {\"PublicationType\": [\"Journal Article\"]}
                    },
                    \"MedlineJournalInfo\": {
                      \"MedlineTA\": \"Int J Gynaecol Obstet\",
                      \"Country\": \"United States\",
                      \"NlmUniqueID\": \"0210174\",
                      \"ISSNLinking\": \"0020-7292\"
                    }
                  },
                  \"PubmedData\": {
                    \"PublicationStatus\": \"ppublish\",
                    \"History\": {
                      \"PubMedPubDate\": [
                        {\"@PubStatus\": \"received\", \"Year\": \"2024\", \"Month\": \"06\", \"Day\": \"01\"}
                      ]
                    }
                  }
                }
              ]
            }
            """;

        EFetchResponse response = EFetchResponse.from(MAPPER.readTree(payload));
        assertThat(response.articles()).hasSize(1);
        PubmedArticle article = response.articles().get(0);
        assertThat(article.pmid()).isEqualTo("41048095");
        assertThat(article.article().title()).isEqualTo("Sample Article");
        assertThat(article.article().journal().issn()).isEqualTo("1879-3479");
        assertThat(article.article().abstractSections()).hasSize(2);
        assertThat(article.article().authors()).singleElement().satisfies(author -> {
            assertThat(author.lastName()).isEqualTo("Doe");
            assertThat(author.affiliations()).containsExactly("Example University");
            assertThat(author.identifier()).isEqualTo("0000-0001");
            assertThat(author.identifierSource()).isEqualTo("ORCID");
        });
        assertThat(article.pubmedData().publicationStatus()).isEqualTo("ppublish");
        assertThat(article.pubmedData().history()).hasSize(1);
    }
}
