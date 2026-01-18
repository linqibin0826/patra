package com.patra.catalog.domain.model.vo.publication.pubmed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// PubmedArticle DTO 单元测试。
///
/// 验证 PubMed 文献解析中间数据结构的正确性。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedArticle DTO")
class PubmedArticleTest {

  @Nested
  @DisplayName("记录结构验证")
  class RecordStructureTest {

    @Test
    @DisplayName("应正确存储所有必填字段")
    void should_store_all_required_fields() {
      // given
      var article =
          PubmedArticle.builder()
              .pmid("12345678")
              .articleTitle("Sample Article Title")
              .nlmUniqueId("101234567")
              .pubYear(2024)
              .build();

      // then
      assertThat(article.pmid()).isEqualTo("12345678");
      assertThat(article.articleTitle()).isEqualTo("Sample Article Title");
      assertThat(article.nlmUniqueId()).isEqualTo("101234567");
      assertThat(article.pubYear()).isEqualTo(2024);
    }

    @Test
    @DisplayName("应正确存储所有可选字段")
    void should_store_all_optional_fields() {
      // given
      var article =
          PubmedArticle.builder()
              .pmid("12345678")
              .articleTitle("Sample Article Title")
              .nlmUniqueId("101234567")
              .pubYear(2024)
              // optional fields
              .doi("10.1000/example.doi")
              .pmcId("PMC1234567")
              .vernacularTitle("样本文章标题")
              .issnPrint("1234-5678")
              .issnElectronic("8765-4321")
              .issnLinking("1111-2222")
              .journalTitle("Journal of Examples")
              .volume("10")
              .issue("2")
              .pubMonth(6)
              .pubDay(15)
              .medlineDate("2024 Jun-Jul")
              .languages(List.of("eng", "chi"))
              .publicationStatus("epublish")
              .authorsComplete(true)
              .build();

      // then
      assertThat(article.doi()).isEqualTo("10.1000/example.doi");
      assertThat(article.pmcId()).isEqualTo("PMC1234567");
      assertThat(article.vernacularTitle()).isEqualTo("样本文章标题");
      assertThat(article.issnPrint()).isEqualTo("1234-5678");
      assertThat(article.issnElectronic()).isEqualTo("8765-4321");
      assertThat(article.issnLinking()).isEqualTo("1111-2222");
      assertThat(article.journalTitle()).isEqualTo("Journal of Examples");
      assertThat(article.volume()).isEqualTo("10");
      assertThat(article.issue()).isEqualTo("2");
      assertThat(article.pubMonth()).isEqualTo(6);
      assertThat(article.pubDay()).isEqualTo(15);
      assertThat(article.medlineDate()).isEqualTo("2024 Jun-Jul");
      assertThat(article.languages()).containsExactly("eng", "chi");
      assertThat(article.publicationStatus()).isEqualTo("epublish");
      assertThat(article.authorsComplete()).isTrue();
    }
  }

  @Nested
  @DisplayName("防御性拷贝")
  class DefensiveCopyTest {

    @Test
    @DisplayName("languages 列表应进行防御性拷贝")
    void should_defensively_copy_languages_list() {
      // given
      var mutableList = new java.util.ArrayList<>(List.of("eng", "chi"));
      var article =
          PubmedArticle.builder()
              .pmid("12345678")
              .articleTitle("Test")
              .nlmUniqueId("101234567")
              .pubYear(2024)
              .languages(mutableList)
              .build();

      // when - 修改原始列表
      mutableList.add("jpn");

      // then - article 中的列表不应受影响
      assertThat(article.languages()).containsExactly("eng", "chi");
    }

    @Test
    @DisplayName("null languages 应转换为空列表")
    void should_convert_null_languages_to_empty_list() {
      // given
      var article =
          PubmedArticle.builder()
              .pmid("12345678")
              .articleTitle("Test")
              .nlmUniqueId("101234567")
              .pubYear(2024)
              .languages(null)
              .build();

      // then
      assertThat(article.languages()).isEmpty();
    }
  }

  @Nested
  @DisplayName("便捷方法")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("hasDoi() 应正确判断 DOI 是否存在")
    void should_correctly_check_doi_existence() {
      var withDoi =
          PubmedArticle.builder()
              .pmid("1")
              .articleTitle("T")
              .nlmUniqueId("N")
              .pubYear(2024)
              .doi("10.1000/test")
              .build();
      var withoutDoi =
          PubmedArticle.builder()
              .pmid("1")
              .articleTitle("T")
              .nlmUniqueId("N")
              .pubYear(2024)
              .build();
      var withBlankDoi =
          PubmedArticle.builder()
              .pmid("1")
              .articleTitle("T")
              .nlmUniqueId("N")
              .pubYear(2024)
              .doi("  ")
              .build();

      assertThat(withDoi.hasDoi()).isTrue();
      assertThat(withoutDoi.hasDoi()).isFalse();
      assertThat(withBlankDoi.hasDoi()).isFalse();
    }

    @Test
    @DisplayName("hasPmcId() 应正确判断 PMC ID 是否存在")
    void should_correctly_check_pmcid_existence() {
      var withPmc =
          PubmedArticle.builder()
              .pmid("1")
              .articleTitle("T")
              .nlmUniqueId("N")
              .pubYear(2024)
              .pmcId("PMC123")
              .build();
      var withoutPmc =
          PubmedArticle.builder()
              .pmid("1")
              .articleTitle("T")
              .nlmUniqueId("N")
              .pubYear(2024)
              .build();

      assertThat(withPmc.hasPmcId()).isTrue();
      assertThat(withoutPmc.hasPmcId()).isFalse();
    }

    @Test
    @DisplayName("getPrimaryIssn() 应按优先级返回 ISSN")
    void should_return_issn_by_priority() {
      // ISSN-L > Print > Electronic
      var withAll =
          PubmedArticle.builder()
              .pmid("1")
              .articleTitle("T")
              .nlmUniqueId("N")
              .pubYear(2024)
              .issnLinking("1111-1111")
              .issnPrint("2222-2222")
              .issnElectronic("3333-3333")
              .build();
      var withPrintAndElectronic =
          PubmedArticle.builder()
              .pmid("1")
              .articleTitle("T")
              .nlmUniqueId("N")
              .pubYear(2024)
              .issnPrint("2222-2222")
              .issnElectronic("3333-3333")
              .build();
      var withElectronicOnly =
          PubmedArticle.builder()
              .pmid("1")
              .articleTitle("T")
              .nlmUniqueId("N")
              .pubYear(2024)
              .issnElectronic("3333-3333")
              .build();
      var withNone =
          PubmedArticle.builder()
              .pmid("1")
              .articleTitle("T")
              .nlmUniqueId("N")
              .pubYear(2024)
              .build();

      assertThat(withAll.getPrimaryIssn()).hasValue("1111-1111");
      assertThat(withPrintAndElectronic.getPrimaryIssn()).hasValue("2222-2222");
      assertThat(withElectronicOnly.getPrimaryIssn()).hasValue("3333-3333");
      assertThat(withNone.getPrimaryIssn()).isEmpty();
    }

    @Test
    @DisplayName("getAllIssns() 应返回所有非空 ISSN")
    void should_return_all_non_null_issns() {
      var article =
          PubmedArticle.builder()
              .pmid("1")
              .articleTitle("T")
              .nlmUniqueId("N")
              .pubYear(2024)
              .issnLinking("1111-1111")
              .issnPrint("2222-2222")
              .issnElectronic("3333-3333")
              .build();

      assertThat(article.getAllIssns())
          .containsExactlyInAnyOrder("1111-1111", "2222-2222", "3333-3333");
    }
  }
}
