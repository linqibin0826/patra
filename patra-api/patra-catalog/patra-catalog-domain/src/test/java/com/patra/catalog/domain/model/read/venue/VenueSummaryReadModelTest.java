package com.patra.catalog.domain.model.read.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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
      assertThatThrownBy(
              () -> new VenueSummaryReadModel(null, "Nature", null, null, "OPENALEX", null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("期刊 ID 不能为空");
    }

    @Test
    @DisplayName("displayName 为空白时应抛出异常")
    void shouldRejectBlankDisplayName() {
      assertThatThrownBy(
              () -> new VenueSummaryReadModel(1L, "   ", null, null, "OPENALEX", null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("期刊名称不能为空");
    }

    @Test
    @DisplayName("displayName 为 null 时应抛出异常")
    void shouldRejectNullDisplayName() {
      assertThatThrownBy(
              () -> new VenueSummaryReadModel(1L, null, null, null, "OPENALEX", null, null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("provenanceCode 为空白时应抛出异常")
    void shouldRejectBlankProvenanceCode() {
      assertThatThrownBy(
              () -> new VenueSummaryReadModel(1L, "Nature", null, null, "  ", null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("数据来源不能为空");
    }

    @Test
    @DisplayName("provenanceCode 为 null 时应抛出异常")
    void shouldRejectNullProvenanceCode() {
      assertThatThrownBy(
              () -> new VenueSummaryReadModel(1L, "Nature", null, null, null, null, null))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("可空字段验证")
  class NullableFieldValidation {

    @Test
    @DisplayName("可空字段为 null 时应正常构造")
    void shouldAllowNullOptionalFields() {
      var model = new VenueSummaryReadModel(1L, "Nature", null, null, "OPENALEX", null, null);

      assertThat(model.id()).isEqualTo(1L);
      assertThat(model.displayName()).isEqualTo("Nature");
      assertThat(model.issnL()).isNull();
      assertThat(model.nlmId()).isNull();
      assertThat(model.provenanceCode()).isEqualTo("OPENALEX");
      assertThat(model.countryCode()).isNull();
      assertThat(model.lastSyncedAt()).isNull();
    }

    @Test
    @DisplayName("所有字段均有值时应正常构造")
    void shouldConstructWithAllFields() {
      var now = Instant.now();
      var model =
          new VenueSummaryReadModel(1L, "Nature", "0028-0836", "0410462", "OPENALEX", "US", now);

      assertThat(model.id()).isEqualTo(1L);
      assertThat(model.displayName()).isEqualTo("Nature");
      assertThat(model.issnL()).isEqualTo("0028-0836");
      assertThat(model.nlmId()).isEqualTo("0410462");
      assertThat(model.provenanceCode()).isEqualTo("OPENALEX");
      assertThat(model.countryCode()).isEqualTo("US");
      assertThat(model.lastSyncedAt()).isEqualTo(now);
    }
  }
}
