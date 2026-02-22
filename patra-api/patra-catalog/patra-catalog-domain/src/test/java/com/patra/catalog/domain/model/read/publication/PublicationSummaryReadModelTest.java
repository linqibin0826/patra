package com.patra.catalog.domain.model.read.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// PublicationSummaryReadModel 紧凑构造器校验测试。
///
/// @author linqibin
/// @since 0.1.0
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@DisplayName("PublicationSummaryReadModel 构造器校验测试")
class PublicationSummaryReadModelTest {

  @Nested
  @DisplayName("必填字段校验")
  class RequiredFieldValidation {

    @Test
    @DisplayName("id 为 null 时应抛出异常")
    void shouldRejectNullId() {
      assertThatThrownBy(
              () ->
                  new PublicationSummaryReadModel(
                      null, "Test Title", null, null, null, null, null, null, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("出版物 ID 不能为空");
    }

    @Test
    @DisplayName("title 为空白时应抛出异常")
    void shouldRejectBlankTitle() {
      assertThatThrownBy(
              () ->
                  new PublicationSummaryReadModel(
                      1L, "   ", null, null, null, null, null, null, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("出版物标题不能为空");
    }

    @Test
    @DisplayName("title 为 null 时应抛出异常")
    void shouldRejectNullTitle() {
      assertThatThrownBy(
              () ->
                  new PublicationSummaryReadModel(
                      1L, null, null, null, null, null, null, null, null, null, null))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("可空字段验证")
  class NullableFieldValidation {

    @Test
    @DisplayName("可空字段为 null 时应正常构造")
    void shouldAllowNullOptionalFields() {
      var model =
          new PublicationSummaryReadModel(
              1L, "Test Publication", null, null, null, null, null, null, null, null, null);

      assertThat(model.id()).isEqualTo(1L);
      assertThat(model.title()).isEqualTo("Test Publication");
      assertThat(model.pmid()).isNull();
      assertThat(model.doi()).isNull();
      assertThat(model.publicationYear()).isNull();
      assertThat(model.languageCode()).isNull();
      assertThat(model.isOa()).isNull();
      assertThat(model.oaStatus()).isNull();
      assertThat(model.venueName()).isNull();
      assertThat(model.citationCount()).isNull();
      assertThat(model.lastSyncedAt()).isNull();
    }

    @Test
    @DisplayName("所有字段均有值时应正常构造")
    void shouldConstructWithAllFields() {
      var now = Instant.now();
      var model =
          new PublicationSummaryReadModel(
              1L,
              "Nature Article",
              "12345678",
              "10.1038/nature12373",
              2024,
              "en",
              true,
              "gold",
              "Nature",
              42,
              now);

      assertThat(model.id()).isEqualTo(1L);
      assertThat(model.title()).isEqualTo("Nature Article");
      assertThat(model.pmid()).isEqualTo("12345678");
      assertThat(model.doi()).isEqualTo("10.1038/nature12373");
      assertThat(model.publicationYear()).isEqualTo(2024);
      assertThat(model.languageCode()).isEqualTo("en");
      assertThat(model.isOa()).isTrue();
      assertThat(model.oaStatus()).isEqualTo("gold");
      assertThat(model.venueName()).isEqualTo("Nature");
      assertThat(model.citationCount()).isEqualTo(42);
      assertThat(model.lastSyncedAt()).isEqualTo(now);
    }
  }
}
