package com.patra.catalog.domain.model.read.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueSummaryReadModel 紧凑构造器校验测试。
///
/// @author linqibin
/// @since 0.1.0
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@DisplayName("VenueSummaryReadModel 构造器校验测试")
class VenueSummaryReadModelTest {

  @Nested
  @DisplayName("必填字段校验")
  class RequiredFieldValidation {

    @Test
    @DisplayName("id 为 null 时应抛出异常")
    void shouldRejectNullId() {
      assertThatThrownBy(() -> VenueSummaryReadModel.builder().id(null).title("Nature").build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("id must not be null");
    }

    @Test
    @DisplayName("title 为空白时应抛出异常")
    void shouldRejectBlankTitle() {
      assertThatThrownBy(() -> VenueSummaryReadModel.builder().id(1L).title("   ").build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("title must not be blank");
    }

    @Test
    @DisplayName("title 为 null 时应抛出异常")
    void shouldRejectNullTitle() {
      assertThatThrownBy(() -> VenueSummaryReadModel.builder().id(1L).title(null).build())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("可空字段验证")
  class NullableFieldValidation {

    @Test
    @DisplayName("可空字段为 null 时应正常构造")
    void shouldAllowNullOptionalFields() {
      var model = VenueSummaryReadModel.builder().id(1L).title("Nature").build();

      assertThat(model.id()).isEqualTo(1L);
      assertThat(model.title()).isEqualTo("Nature");
      assertThat(model.titleZh()).isNull();
      assertThat(model.countryCode()).isNull();
      assertThat(model.imageUrl()).isNull();
      assertThat(model.hIndex()).isNull();
      assertThat(model.jifQuartile()).isNull();
      assertThat(model.casMajorQuartile()).isNull();
      assertThat(model.casTopJournal()).isNull();
      assertThat(model.warningListStatus()).isNull();
      assertThat(model.isOa()).isNull();
      assertThat(model.researchDirection()).isNull();
    }

    @Test
    @DisplayName("所有字段均有值时应正常构造")
    void shouldConstructWithAllFields() {
      var model =
          VenueSummaryReadModel.builder()
              .id(1L)
              .title("Nature")
              .titleZh("自然")
              .countryCode("US")
              .imageUrl("https://example.com/nature.jpg")
              .hIndex(412)
              .jifQuartile("Q1")
              .casMajorQuartile("1区")
              .casTopJournal(true)
              .warningListStatus(null)
              .isOa(true)
              .researchDirection("医学 · 综合")
              .build();

      assertThat(model.id()).isEqualTo(1L);
      assertThat(model.title()).isEqualTo("Nature");
      assertThat(model.titleZh()).isEqualTo("自然");
      assertThat(model.countryCode()).isEqualTo("US");
      assertThat(model.imageUrl()).isEqualTo("https://example.com/nature.jpg");
      assertThat(model.hIndex()).isEqualTo(412);
      assertThat(model.jifQuartile()).isEqualTo("Q1");
      assertThat(model.casMajorQuartile()).isEqualTo("1区");
      assertThat(model.casTopJournal()).isTrue();
      assertThat(model.warningListStatus()).isNull();
      assertThat(model.isOa()).isTrue();
      assertThat(model.researchDirection()).isEqualTo("医学 · 综合");
    }
  }
}
