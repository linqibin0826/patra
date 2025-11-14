package com.patra.ingest.infra.integration.storage.acl;

import static org.assertj.core.api.Assertions.*;

import com.patra.catalog.api.dto.AuthorDTO;
import com.patra.catalog.api.dto.JournalDTO;
import com.patra.catalog.api.dto.LiteratureDTO;
import com.patra.common.model.CanonicalLiterature;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * LiteratureConverter 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>测试 CanonicalLiterature → LiteratureDTO 转换
 *   <li>测试作者列表映射 (mapAuthors)
 *   <li>测试期刊映射 (mapJournal)
 *   <li>测试关联解析 (resolveAffiliations)
 *   <li>测试 null 和空值安全性
 *   <li>测试批量转换 (List)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("LiteratureConverter 单元测试")
class LiteratureConverterTest {

  private final LiteratureConverter converter = new LiteratureConverterImpl();

  // ========== toDto() 单个转换测试 ==========

  @Nested
  @DisplayName("toDto() 单个转换测试")
  class ToDtoSingleTests {

    @Test
    @DisplayName("应该正确转换包含所有字段的完整文献")
    void shouldConvertCompleteLiterature() {
      // Given: 创建完整的 CanonicalLiterature
      CanonicalLiterature.Author author1 =
          CanonicalLiterature.Author.builder()
              .lastName("Smith")
              .foreName("John")
              .initials("JD")
              .affiliations(
                  List.of(
                      CanonicalLiterature.Affiliation.builder()
                          .name("Harvard Medical School")
                          .build()))
              .build();

      CanonicalLiterature.Author author2 =
          CanonicalLiterature.Author.builder()
              .lastName("Johnson")
              .foreName("Mary")
              .affiliations(
                  List.of(
                      CanonicalLiterature.Affiliation.builder()
                          .name("Stanford University")
                          .build()))
              .build();

      CanonicalLiterature.Journal journal =
          CanonicalLiterature.Journal.builder()
              .title("Nature Medicine")
              .issn("1078-8956")
              .issnType("electronic")
              .publisher("Nature Publishing Group")
              .country("US")
              .build();

      CanonicalLiterature literature =
          CanonicalLiterature.builder()
              .title("Novel Approaches to Cancer Treatment")
              .abstractContent(
                  CanonicalLiterature.Abstract.builder()
                      .text(
                          "This study explores innovative methods for treating various types of cancer.")
                      .build())
              .authors(List.of(author1, author2))
              .journal(journal)
              .identifiers(
                  List.of(
                      CanonicalLiterature.Identifier.builder()
                          .type("pmid")
                          .value("12345678")
                          .build(),
                      CanonicalLiterature.Identifier.builder()
                          .type("doi")
                          .value("10.1038/nm.12345")
                          .build()))
              .dates(
                  CanonicalLiterature.PublicationDates.builder()
                      .published(LocalDate.of(2025, 1, 15))
                      .build())
              .keywords(
                  List.of(
                      CanonicalLiterature.KeywordSet.builder()
                          .source("author")
                          .keywords(
                              List.of(
                                  CanonicalLiterature.Keyword.builder().term("cancer").build(),
                                  CanonicalLiterature.Keyword.builder().term("treatment").build(),
                                  CanonicalLiterature.Keyword.builder()
                                      .term("immunotherapy")
                                      .build()))
                          .build()))
              .build();

      // When: 转换为 DTO
      LiteratureDTO dto = converter.toDto(literature);

      // Then: 验证所有字段
      assertThat(dto).isNotNull();
      assertThat(dto.title()).isEqualTo("Novel Approaches to Cancer Treatment");
      assertThat(dto.abstractText())
          .isEqualTo(
              "This study explores innovative methods for treating various types of cancer.");
      assertThat(dto.publicationDate()).isEqualTo(LocalDate.of(2025, 1, 15));
      assertThat(dto.keywords()).containsExactly("cancer", "treatment", "immunotherapy");
      assertThat(dto.identifiers())
          .containsEntry("pmid", "12345678")
          .containsEntry("doi", "10.1038/nm.12345");

      // 验证作者列表
      assertThat(dto.authors()).hasSize(2);
      AuthorDTO firstAuthor = dto.authors().get(0);
      assertThat(firstAuthor.lastName()).isEqualTo("Smith");
      assertThat(firstAuthor.foreName()).isEqualTo("John");
      assertThat(firstAuthor.affiliations()).containsExactly("Harvard Medical School");
      assertThat(firstAuthor.initials()).isEqualTo("JD");
      assertThat(firstAuthor.identifier()).isNull();
      assertThat(firstAuthor.identifierSource()).isNull();

      AuthorDTO secondAuthor = dto.authors().get(1);
      assertThat(secondAuthor.lastName()).isEqualTo("Johnson");
      assertThat(secondAuthor.foreName()).isEqualTo("Mary");
      assertThat(secondAuthor.affiliations()).containsExactly("Stanford University");

      // 验证期刊
      assertThat(dto.journal()).isNotNull();
      assertThat(dto.journal().title()).isEqualTo("Nature Medicine");
      assertThat(dto.journal().issn()).isEqualTo("1078-8956");
      assertThat(dto.journal().publisher()).isEqualTo("Nature Publishing Group");
      assertThat(dto.journal().issnType()).isEqualTo("electronic");
      assertThat(dto.journal().country()).isEqualTo("US");

      // 验证语言和发表类型
      assertThat(dto.language()).isNull();
      assertThat(dto.publicationTypes()).isEmpty();
    }

    @Test
    @DisplayName("应该正确转换只包含必填字段的最小文献")
    void shouldConvertMinimalLiterature() {
      // Given: 创建只有标题的文献
      CanonicalLiterature literature =
          CanonicalLiterature.builder()
              .title("Minimal Literature Entry")
              .abstractContent(null)
              .authors(null)
              .journal(null)
              .identifiers(null)
              .dates(null)
              .keywords(null)
              .build();

      // When: 转换为 DTO
      LiteratureDTO dto = converter.toDto(literature);

      // Then: 验证
      assertThat(dto).isNotNull();
      assertThat(dto.title()).isEqualTo("Minimal Literature Entry");
      assertThat(dto.abstractText()).isNull();
      assertThat(dto.authors()).isEmpty();
      assertThat(dto.journal()).isNull();
      assertThat(dto.identifiers()).isNull();
      assertThat(dto.publicationDate()).isNull();
      assertThat(dto.keywords()).isNull();
      assertThat(dto.language()).isNull();
      assertThat(dto.publicationTypes()).isEmpty();
    }

    @Test
    @DisplayName("应该正确转换包含空作者列表的文献")
    void shouldConvertLiteratureWithEmptyAuthors() {
      // Given
      CanonicalLiterature literature =
          CanonicalLiterature.builder().title("Paper Without Authors").authors(List.of()).build();

      // When
      LiteratureDTO dto = converter.toDto(literature);

      // Then
      assertThat(dto).isNotNull();
      assertThat(dto.authors()).isEmpty();
    }
  }

  // ========== toDto() 批量转换测试 ==========

  @Nested
  @DisplayName("toDto() 批量转换测试")
  class ToDtoListTests {

    @Test
    @DisplayName("应该正确转换文献列表")
    void shouldConvertLiteratureList() {
      // Given: 创建多个文献
      CanonicalLiterature lit1 =
          CanonicalLiterature.builder()
              .title("First Paper")
              .dates(
                  CanonicalLiterature.PublicationDates.builder()
                      .published(LocalDate.of(2025, 1, 1))
                      .build())
              .build();

      CanonicalLiterature lit2 =
          CanonicalLiterature.builder()
              .title("Second Paper")
              .dates(
                  CanonicalLiterature.PublicationDates.builder()
                      .published(LocalDate.of(2025, 1, 2))
                      .build())
              .build();

      List<CanonicalLiterature> literatures = List.of(lit1, lit2);

      // When: 批量转换
      List<LiteratureDTO> dtos = converter.toDto(literatures);

      // Then
      assertThat(dtos).hasSize(2);
      assertThat(dtos.get(0).title()).isEqualTo("First Paper");
      assertThat(dtos.get(1).title()).isEqualTo("Second Paper");
    }

    @Test
    @DisplayName("应该正确转换空文献列表")
    void shouldConvertEmptyLiteratureList() {
      // Given
      List<CanonicalLiterature> literatures = List.of();

      // When
      List<LiteratureDTO> dtos = converter.toDto(literatures);

      // Then
      assertThat(dtos).isEmpty();
    }
  }

  // ========== mapAuthors() 测试 ==========

  @Nested
  @DisplayName("mapAuthors() 测试")
  class MapAuthorsTests {

    @Test
    @DisplayName("应该正确映射多个作者")
    void shouldMapMultipleAuthors() {
      // Given
      CanonicalLiterature.Author author1 =
          CanonicalLiterature.Author.builder()
              .lastName("Doe")
              .foreName("Jane")
              .affiliations(List.of(CanonicalLiterature.Affiliation.builder().name("MIT").build()))
              .build();

      CanonicalLiterature.Author author2 =
          CanonicalLiterature.Author.builder()
              .lastName("Lee")
              .foreName("David")
              .affiliations(
                  List.of(CanonicalLiterature.Affiliation.builder().name("Caltech").build()))
              .build();

      List<CanonicalLiterature.Author> authors = List.of(author1, author2);

      // When
      List<AuthorDTO> authorDTOs = converter.mapAuthors(authors);

      // Then
      assertThat(authorDTOs).hasSize(2);

      AuthorDTO firstDTO = authorDTOs.get(0);
      assertThat(firstDTO.lastName()).isEqualTo("Doe");
      assertThat(firstDTO.foreName()).isEqualTo("Jane");
      assertThat(firstDTO.affiliations()).containsExactly("MIT");
      assertThat(firstDTO.initials()).isNull();
      assertThat(firstDTO.identifier()).isNull();
      assertThat(firstDTO.identifierSource()).isNull();

      AuthorDTO secondDTO = authorDTOs.get(1);
      assertThat(secondDTO.lastName()).isEqualTo("Lee");
      assertThat(secondDTO.foreName()).isEqualTo("David");
      assertThat(secondDTO.affiliations()).containsExactly("Caltech");
    }

    @Test
    @DisplayName("应该正确映射没有关联的作者")
    void shouldMapAuthorWithoutAffiliation() {
      // Given: 作者没有关联
      CanonicalLiterature.Author author =
          CanonicalLiterature.Author.builder()
              .lastName("Wang")
              .foreName("Li")
              .affiliations(null)
              .build();

      List<CanonicalLiterature.Author> authors = List.of(author);

      // When
      List<AuthorDTO> authorDTOs = converter.mapAuthors(authors);

      // Then
      assertThat(authorDTOs).hasSize(1);
      assertThat(authorDTOs.get(0).lastName()).isEqualTo("Wang");
      assertThat(authorDTOs.get(0).foreName()).isEqualTo("Li");
      assertThat(authorDTOs.get(0).affiliations()).isEmpty();
    }

    @Test
    @DisplayName("应该正确映射空白关联的作者")
    void shouldMapAuthorWithBlankAffiliation() {
      // Given: 作者关联为空白机构名称
      CanonicalLiterature.Author author =
          CanonicalLiterature.Author.builder()
              .lastName("Chen")
              .foreName("Wei")
              .affiliations(List.of(CanonicalLiterature.Affiliation.builder().name("   ").build()))
              .build();

      List<CanonicalLiterature.Author> authors = List.of(author);

      // When
      List<AuthorDTO> authorDTOs = converter.mapAuthors(authors);

      // Then
      assertThat(authorDTOs).hasSize(1);
      assertThat(authorDTOs.get(0).affiliations()).isEmpty();
    }

    @Test
    @DisplayName("当作者列表为 null 时应该返回空列表")
    void shouldReturnEmptyListWhenAuthorsIsNull() {
      // When
      List<AuthorDTO> authorDTOs = converter.mapAuthors(null);

      // Then
      assertThat(authorDTOs).isEmpty();
    }

    @Test
    @DisplayName("当作者列表为空时应该返回空列表")
    void shouldReturnEmptyListWhenAuthorsIsEmpty() {
      // When
      List<AuthorDTO> authorDTOs = converter.mapAuthors(List.of());

      // Then
      assertThat(authorDTOs).isEmpty();
    }
  }

  // ========== mapJournal() 测试 ==========

  @Nested
  @DisplayName("mapJournal() 测试")
  class MapJournalTests {

    @Test
    @DisplayName("应该正确映射完整期刊信息")
    void shouldMapCompleteJournal() {
      // Given
      CanonicalLiterature.Journal journal =
          CanonicalLiterature.Journal.builder()
              .title("Science")
              .issn("0036-8075")
              .issnType("print")
              .publisher("American Association for the Advancement of Science")
              .country("USA")
              .build();

      // When
      JournalDTO journalDTO = converter.mapJournal(journal);

      // Then
      assertThat(journalDTO).isNotNull();
      assertThat(journalDTO.title()).isEqualTo("Science");
      assertThat(journalDTO.issn()).isEqualTo("0036-8075");
      assertThat(journalDTO.publisher())
          .isEqualTo("American Association for the Advancement of Science");
      assertThat(journalDTO.issnType()).isEqualTo("print");
      assertThat(journalDTO.country()).isEqualTo("USA");
    }

    @Test
    @DisplayName("应该正确映射只有标题的期刊")
    void shouldMapJournalWithTitleOnly() {
      // Given
      CanonicalLiterature.Journal journal =
          CanonicalLiterature.Journal.builder().title("Cell").issn(null).publisher(null).build();

      // When
      JournalDTO journalDTO = converter.mapJournal(journal);

      // Then
      assertThat(journalDTO).isNotNull();
      assertThat(journalDTO.title()).isEqualTo("Cell");
      assertThat(journalDTO.issn()).isNull();
      assertThat(journalDTO.publisher()).isNull();
    }

    @Test
    @DisplayName("当期刊为 null 时应该返回 null")
    void shouldReturnNullWhenJournalIsNull() {
      // When
      JournalDTO journalDTO = converter.mapJournal(null);

      // Then
      assertThat(journalDTO).isNull();
    }
  }

  // ========== resolveAffiliations() 测试 ==========

  @Nested
  @DisplayName("resolveAffiliations() 测试")
  class ResolveAffiliationsTests {

    @Test
    @DisplayName("应该将机构列表转换为名称列表")
    void shouldConvertAffiliationsToNameList() {
      // Given
      List<CanonicalLiterature.Affiliation> affiliations =
          List.of(
              CanonicalLiterature.Affiliation.builder()
                  .name("University of California, Berkeley")
                  .build());

      // When
      List<String> names = converter.resolveAffiliations(affiliations);

      // Then
      assertThat(names).containsExactly("University of California, Berkeley");
    }

    @Test
    @DisplayName("当机构列表为 null 时应该返回空列表")
    void shouldReturnEmptyListWhenAffiliationsIsNull() {
      // When
      List<String> names = converter.resolveAffiliations(null);

      // Then
      assertThat(names).isEmpty();
    }

    @Test
    @DisplayName("当机构列表为空时应该返回空列表")
    void shouldReturnEmptyListWhenAffiliationsIsEmpty() {
      // When
      List<String> names = converter.resolveAffiliations(List.of());

      // Then
      assertThat(names).isEmpty();
    }

    @Test
    @DisplayName("应该过滤空白机构名称")
    void shouldFilterBlankAffiliationNames() {
      // Given
      List<CanonicalLiterature.Affiliation> affiliations =
          List.of(
              CanonicalLiterature.Affiliation.builder().name("Valid Name").build(),
              CanonicalLiterature.Affiliation.builder().name("   ").build(),
              CanonicalLiterature.Affiliation.builder().name("").build());

      // When
      List<String> names = converter.resolveAffiliations(affiliations);

      // Then
      assertThat(names).containsExactly("Valid Name");
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("应该正确处理包含特殊字符的文献")
    void shouldHandleLiteratureWithSpecialCharacters() {
      // Given
      CanonicalLiterature literature =
          CanonicalLiterature.builder()
              .title("Study on α-β proteins & γ-rays: a \"novel\" approach")
              .abstractContent(
                  CanonicalLiterature.Abstract.builder()
                      .text("Testing special chars: <tag>, {brace}, [bracket], @symbol")
                      .build())
              .identifiers(
                  List.of(
                      CanonicalLiterature.Identifier.builder()
                          .type("doi")
                          .value("10.1000/xyz<>123")
                          .build()))
              .build();

      // When
      LiteratureDTO dto = converter.toDto(literature);

      // Then
      assertThat(dto).isNotNull();
      assertThat(dto.title()).isEqualTo("Study on α-β proteins & γ-rays: a \"novel\" approach");
      assertThat(dto.abstractText())
          .isEqualTo("Testing special chars: <tag>, {brace}, [bracket], @symbol");
      assertThat(dto.identifiers()).containsEntry("doi", "10.1000/xyz<>123");
    }

    @Test
    @DisplayName("应该正确处理包含 Unicode 字符的作者姓名")
    void shouldHandleAuthorsWithUnicodeCharacters() {
      // Given
      CanonicalLiterature.Author author =
          CanonicalLiterature.Author.builder()
              .lastName("李")
              .foreName("明")
              .affiliations(List.of(CanonicalLiterature.Affiliation.builder().name("北京大学").build()))
              .build();

      CanonicalLiterature literature =
          CanonicalLiterature.builder().title("Research Paper").authors(List.of(author)).build();

      // When
      LiteratureDTO dto = converter.toDto(literature);

      // Then
      assertThat(dto.authors()).hasSize(1);
      assertThat(dto.authors().get(0).lastName()).isEqualTo("李");
      assertThat(dto.authors().get(0).foreName()).isEqualTo("明");
      assertThat(dto.authors().get(0).affiliations()).containsExactly("北京大学");
    }

    @Test
    @DisplayName("应该正确处理包含多个标识符的文献")
    void shouldHandleLiteratureWithMultipleIdentifiers() {
      // Given
      CanonicalLiterature literature =
          CanonicalLiterature.builder()
              .title("Multi-Identifier Paper")
              .identifiers(
                  List.of(
                      CanonicalLiterature.Identifier.builder()
                          .type("pmid")
                          .value("12345678")
                          .build(),
                      CanonicalLiterature.Identifier.builder()
                          .type("doi")
                          .value("10.1038/nature12345")
                          .build(),
                      CanonicalLiterature.Identifier.builder()
                          .type("pmc")
                          .value("PMC9876543")
                          .build(),
                      CanonicalLiterature.Identifier.builder()
                          .type("arxiv")
                          .value("2101.12345")
                          .build()))
              .build();

      // When
      LiteratureDTO dto = converter.toDto(literature);

      // Then
      assertThat(dto.identifiers())
          .hasSize(4)
          .containsEntry("pmid", "12345678")
          .containsEntry("doi", "10.1038/nature12345")
          .containsEntry("pmc", "PMC9876543")
          .containsEntry("arxiv", "2101.12345");
    }

    @Test
    @DisplayName("应该正确处理包含多个关键词的文献")
    void shouldHandleLiteratureWithMultipleKeywords() {
      // Given
      CanonicalLiterature literature =
          CanonicalLiterature.builder()
              .title("Keyword-Rich Paper")
              .keywords(
                  List.of(
                      CanonicalLiterature.KeywordSet.builder()
                          .source("author")
                          .keywords(
                              List.of(
                                  CanonicalLiterature.Keyword.builder()
                                      .term("machine learning")
                                      .build(),
                                  CanonicalLiterature.Keyword.builder()
                                      .term("deep learning")
                                      .build(),
                                  CanonicalLiterature.Keyword.builder()
                                      .term("neural networks")
                                      .build(),
                                  CanonicalLiterature.Keyword.builder()
                                      .term("artificial intelligence")
                                      .build(),
                                  CanonicalLiterature.Keyword.builder()
                                      .term("computer vision")
                                      .build()))
                          .build()))
              .build();

      // When
      LiteratureDTO dto = converter.toDto(literature);

      // Then
      assertThat(dto.keywords())
          .containsExactly(
              "machine learning",
              "deep learning",
              "neural networks",
              "artificial intelligence",
              "computer vision");
    }
  }
}
