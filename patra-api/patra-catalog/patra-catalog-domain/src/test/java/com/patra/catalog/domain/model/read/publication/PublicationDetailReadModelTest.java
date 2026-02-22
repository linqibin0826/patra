package com.patra.catalog.domain.model.read.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.AbstractInfo;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.IdentifierInfo;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.KeywordInfo;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.MeshHeadingInfo;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.MeshHeadingInfo.MeshQualifierInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// PublicationDetailReadModel 紧凑构造器校验测试。
///
/// @author linqibin
/// @since 0.1.0
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@DisplayName("PublicationDetailReadModel 构造器校验测试")
class PublicationDetailReadModelTest {

  @Nested
  @DisplayName("必填字段校验")
  class RequiredFieldValidation {

    @Test
    @DisplayName("id 为 null 时应抛出异常")
    void shouldRejectNullId() {
      assertThatThrownBy(
              () ->
                  PublicationDetailReadModel.builder()
                      .id(null)
                      .title("Test")
                      .createdAt(Instant.now())
                      .updatedAt(Instant.now())
                      .build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("出版物 ID 不能为空");
    }

    @Test
    @DisplayName("title 为空白时应抛出异常")
    void shouldRejectBlankTitle() {
      assertThatThrownBy(
              () ->
                  PublicationDetailReadModel.builder()
                      .id(1L)
                      .title("   ")
                      .createdAt(Instant.now())
                      .updatedAt(Instant.now())
                      .build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("出版物标题不能为空");
    }

    @Test
    @DisplayName("createdAt 为 null 时应抛出异常")
    void shouldRejectNullCreatedAt() {
      assertThatThrownBy(
              () ->
                  PublicationDetailReadModel.builder()
                      .id(1L)
                      .title("Test")
                      .createdAt(null)
                      .updatedAt(Instant.now())
                      .build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("创建时间不能为空");
    }

    @Test
    @DisplayName("updatedAt 为 null 时应抛出异常")
    void shouldRejectNullUpdatedAt() {
      assertThatThrownBy(
              () ->
                  PublicationDetailReadModel.builder()
                      .id(1L)
                      .title("Test")
                      .createdAt(Instant.now())
                      .updatedAt(null)
                      .build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("更新时间不能为空");
    }
  }

  @Nested
  @DisplayName("集合防御性拷贝")
  class DefensiveCopyTests {

    @Test
    @DisplayName("abstracts 为 null 时应归一化为空列表")
    void shouldNormalizeNullAbstractsToEmptyList() {
      var model = buildMinimal().abstracts(null).build();
      assertThat(model.abstracts()).isEmpty();
    }

    @Test
    @DisplayName("identifiers 为 null 时应归一化为空列表")
    void shouldNormalizeNullIdentifiersToEmptyList() {
      var model = buildMinimal().identifiers(null).build();
      assertThat(model.identifiers()).isEmpty();
    }

    @Test
    @DisplayName("keywords 为 null 时应归一化为空列表")
    void shouldNormalizeNullKeywordsToEmptyList() {
      var model = buildMinimal().keywords(null).build();
      assertThat(model.keywords()).isEmpty();
    }

    @Test
    @DisplayName("meshHeadings 为 null 时应归一化为空列表")
    void shouldNormalizeNullMeshHeadingsToEmptyList() {
      var model = buildMinimal().meshHeadings(null).build();
      assertThat(model.meshHeadings()).isEmpty();
    }

    @Test
    @DisplayName("集合应为不可变副本，修改原列表不影响模型")
    void shouldDefensivelyCopyCollections() {
      var mutableIdentifiers =
          new ArrayList<>(List.of(new IdentifierInfo("pmid", "12345678", "pubmed")));
      var model = buildMinimal().identifiers(mutableIdentifiers).build();

      mutableIdentifiers.add(new IdentifierInfo("doi", "10.1234/test", "crossref"));

      assertThat(model.identifiers()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("嵌套记录构造")
  class NestedRecordTests {

    @Test
    @DisplayName("AbstractInfo 应正常构造")
    void shouldConstructAbstractInfo() {
      var info = new AbstractInfo("This is the abstract text.", null, "Copyright 2024", "MAIN");
      assertThat(info.plainText()).isEqualTo("This is the abstract text.");
      assertThat(info.structuredSections()).isNull();
      assertThat(info.copyright()).isEqualTo("Copyright 2024");
      assertThat(info.abstractType()).isEqualTo("MAIN");
    }

    @Test
    @DisplayName("IdentifierInfo 应正常构造")
    void shouldConstructIdentifierInfo() {
      var info = new IdentifierInfo("doi", "10.1038/nature12373", "crossref");
      assertThat(info.type()).isEqualTo("doi");
      assertThat(info.value()).isEqualTo("10.1038/nature12373");
      assertThat(info.source()).isEqualTo("crossref");
    }

    @Test
    @DisplayName("KeywordInfo 应正常构造")
    void shouldConstructKeywordInfo() {
      var info = new KeywordInfo("apoptosis", true, "MeSH");
      assertThat(info.term()).isEqualTo("apoptosis");
      assertThat(info.major()).isTrue();
      assertThat(info.keywordSet()).isEqualTo("MeSH");
    }

    @Test
    @DisplayName("MeshHeadingInfo 应正常构造，qualifiers 防御性拷贝")
    void shouldConstructMeshHeadingInfoWithDefensiveCopy() {
      var qualifiers = new ArrayList<>(List.of(new MeshQualifierInfo("Q000235", true)));
      var heading = new MeshHeadingInfo("D001234", true, qualifiers);

      qualifiers.add(new MeshQualifierInfo("Q000333", false));

      assertThat(heading.descriptorUi()).isEqualTo("D001234");
      assertThat(heading.majorTopic()).isTrue();
      assertThat(heading.qualifiers()).hasSize(1);
    }

    @Test
    @DisplayName("MeshHeadingInfo qualifiers 为 null 时应归一化为空列表")
    void shouldNormalizeNullMeshQualifiers() {
      var heading = new MeshHeadingInfo("D001234", false, null);
      assertThat(heading.qualifiers()).isEmpty();
    }
  }

  /// 构建仅包含必填字段的 Builder。
  private PublicationDetailReadModel.PublicationDetailReadModelBuilder buildMinimal() {
    return PublicationDetailReadModel.builder()
        .id(1L)
        .title("Test Publication")
        .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
        .updatedAt(Instant.parse("2026-01-01T00:00:00Z"));
  }
}
