package com.patra.catalog.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.DataSourceCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// 载体数据源实体单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueSourceData 载体数据源实体")
@Timeout(2)
class VenueSourceDataTest {

  @Nested
  @DisplayName("create() 工厂方法")
  class CreateTests {

    @Test
    @DisplayName("应正确创建完整数据源记录")
    void shouldCreateWithAllFields() {
      // Given
      Long venueId = 123L;
      DataSourceCode sourceCode = DataSourceCode.OPENALEX;
      String sourceId = "S1234567890";
      String rawData = "{\"id\": \"S1234567890\"}";
      String extractedData = "{\"title\": \"Nature\"}";

      // When
      VenueSourceData data =
          VenueSourceData.create(venueId, sourceCode, sourceId, rawData, extractedData);

      // Then
      assertThat(data.getVenueId()).isEqualTo(venueId);
      assertThat(data.getSourceCode()).isEqualTo(sourceCode);
      assertThat(data.getSourceId()).isEqualTo(sourceId);
      assertThat(data.getRawData()).isEqualTo(rawData);
      assertThat(data.getExtractedData()).isEqualTo(extractedData);
      assertThat(data.getId()).isNull();
      assertThat(data.getFetchedAt()).isNotNull();
    }

    @Test
    @DisplayName("应正确创建仅必填字段的数据源记录")
    void shouldCreateWithRequiredFieldsOnly() {
      // Given
      Long venueId = 456L;
      DataSourceCode sourceCode = DataSourceCode.PUBMED;

      // When
      VenueSourceData data = VenueSourceData.create(venueId, sourceCode);

      // Then
      assertThat(data.getVenueId()).isEqualTo(venueId);
      assertThat(data.getSourceCode()).isEqualTo(sourceCode);
      assertThat(data.getSourceId()).isNull();
      assertThat(data.getRawData()).isNull();
      assertThat(data.getExtractedData()).isNull();
    }

    @Test
    @DisplayName("venueId 为 null 应抛出异常")
    void shouldThrowWhenVenueIdIsNull() {
      assertThatThrownBy(() -> VenueSourceData.create(null, DataSourceCode.OPENALEX))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Venue ID 不能为空");
    }

    @Test
    @DisplayName("sourceCode 为 null 应抛出异常")
    void shouldThrowWhenSourceCodeIsNull() {
      assertThatThrownBy(() -> VenueSourceData.create(123L, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("数据源代码不能为空");
    }
  }

  @Nested
  @DisplayName("restore() 工厂方法")
  class RestoreTests {

    @Test
    @DisplayName("应正确从持久化状态重建实体")
    void shouldRestoreFromPersistence() {
      // Given
      Long id = 1L;
      Long venueId = 123L;
      DataSourceCode sourceCode = DataSourceCode.DOAJ;

      // When
      VenueSourceData data = VenueSourceData.restore(id, venueId, sourceCode);

      // Then
      assertThat(data.getId()).isEqualTo(id);
      assertThat(data.getVenueId()).isEqualTo(venueId);
      assertThat(data.getSourceCode()).isEqualTo(sourceCode);
    }
  }

  @Nested
  @DisplayName("链式设置方法")
  class WithMethodsTests {

    @Test
    @DisplayName("withSourceId() 应正确设置数据源 ID")
    void shouldSetSourceId() {
      VenueSourceData data = VenueSourceData.create(123L, DataSourceCode.OPENALEX);
      data.withSourceId("S1234567890");
      assertThat(data.getSourceId()).isEqualTo("S1234567890");
    }

    @Test
    @DisplayName("withRawData() 应正确设置原始数据")
    void shouldSetRawData() {
      VenueSourceData data = VenueSourceData.create(123L, DataSourceCode.OPENALEX);
      String json = "{\"id\": \"S1234567890\"}";
      data.withRawData(json);
      assertThat(data.getRawData()).isEqualTo(json);
    }

    @Test
    @DisplayName("withExtractedData() 应正确设置提取数据")
    void shouldSetExtractedData() {
      VenueSourceData data = VenueSourceData.create(123L, DataSourceCode.OPENALEX);
      String json = "{\"title\": \"Nature\"}";
      data.withExtractedData(json);
      assertThat(data.getExtractedData()).isEqualTo(json);
    }

    @Test
    @DisplayName("withSourceTimestamps() 应正确设置源数据时间戳")
    void shouldSetSourceTimestamps() {
      VenueSourceData data = VenueSourceData.create(123L, DataSourceCode.OPENALEX);
      LocalDate created = LocalDate.of(2020, 1, 1);
      LocalDate updated = LocalDate.of(2024, 6, 15);
      data.withSourceTimestamps(created, updated);

      assertThat(data.getSourceCreatedAt()).isEqualTo(created);
      assertThat(data.getSourceUpdatedAt()).isEqualTo(updated);
    }

    @Test
    @DisplayName("链式调用应正常工作")
    void shouldSupportChaining() {
      VenueSourceData data =
          VenueSourceData.create(123L, DataSourceCode.OPENALEX)
              .withSourceId("S1234567890")
              .withRawData("{\"id\": \"S1234567890\"}")
              .withExtractedData("{\"title\": \"Nature\"}")
              .withSourceTimestamps(LocalDate.of(2020, 1, 1), LocalDate.of(2024, 6, 15));

      assertThat(data.getSourceId()).isEqualTo("S1234567890");
      assertThat(data.getRawData()).isNotBlank();
      assertThat(data.getExtractedData()).isNotBlank();
      assertThat(data.getSourceCreatedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("判断方法")
  class HasMethodsTests {

    @Test
    @DisplayName("hasRawData() 应正确判断")
    void shouldCheckHasRawData() {
      VenueSourceData withData = VenueSourceData.create(123L, DataSourceCode.OPENALEX);
      withData.withRawData("{\"id\": \"S1234567890\"}");
      VenueSourceData withoutData = VenueSourceData.create(123L, DataSourceCode.OPENALEX);

      assertThat(withData.hasRawData()).isTrue();
      assertThat(withoutData.hasRawData()).isFalse();
    }

    @Test
    @DisplayName("hasExtractedData() 应正确判断")
    void shouldCheckHasExtractedData() {
      VenueSourceData withData = VenueSourceData.create(123L, DataSourceCode.OPENALEX);
      withData.withExtractedData("{\"title\": \"Nature\"}");
      VenueSourceData withoutData = VenueSourceData.create(123L, DataSourceCode.OPENALEX);

      assertThat(withData.hasExtractedData()).isTrue();
      assertThat(withoutData.hasExtractedData()).isFalse();
    }

    @Test
    @DisplayName("hasSourceId() 应正确判断")
    void shouldCheckHasSourceId() {
      VenueSourceData withId = VenueSourceData.create(123L, DataSourceCode.OPENALEX);
      withId.withSourceId("S1234567890");
      VenueSourceData withoutId = VenueSourceData.create(123L, DataSourceCode.OPENALEX);

      assertThat(withId.hasSourceId()).isTrue();
      assertThat(withoutId.hasSourceId()).isFalse();
    }
  }

  @Nested
  @DisplayName("数据源类型判断")
  class SourceTypeTests {

    @Test
    @DisplayName("isFromOpenAlex() 应正确判断")
    void shouldIdentifyOpenAlex() {
      VenueSourceData openalex = VenueSourceData.create(123L, DataSourceCode.OPENALEX);
      VenueSourceData pubmed = VenueSourceData.create(123L, DataSourceCode.PUBMED);

      assertThat(openalex.isFromOpenAlex()).isTrue();
      assertThat(pubmed.isFromOpenAlex()).isFalse();
    }

    @Test
    @DisplayName("isFromPubMed() 应正确判断")
    void shouldIdentifyPubMed() {
      VenueSourceData pubmed = VenueSourceData.create(123L, DataSourceCode.PUBMED);
      VenueSourceData openalex = VenueSourceData.create(123L, DataSourceCode.OPENALEX);

      assertThat(pubmed.isFromPubMed()).isTrue();
      assertThat(openalex.isFromPubMed()).isFalse();
    }

    @Test
    @DisplayName("isFromDoaj() 应正确判断")
    void shouldIdentifyDoaj() {
      VenueSourceData doaj = VenueSourceData.create(123L, DataSourceCode.DOAJ);
      VenueSourceData jcr = VenueSourceData.create(123L, DataSourceCode.JCR);

      assertThat(doaj.isFromDoaj()).isTrue();
      assertThat(jcr.isFromDoaj()).isFalse();
    }

    @Test
    @DisplayName("isFromCrossref() 应正确判断")
    void shouldIdentifyCrossref() {
      VenueSourceData crossref = VenueSourceData.create(123L, DataSourceCode.CROSSREF);
      VenueSourceData doaj = VenueSourceData.create(123L, DataSourceCode.DOAJ);

      assertThat(crossref.isFromCrossref()).isTrue();
      assertThat(doaj.isFromCrossref()).isFalse();
    }

    @Test
    @DisplayName("isFromJcr() 应正确判断")
    void shouldIdentifyJcr() {
      VenueSourceData jcr = VenueSourceData.create(123L, DataSourceCode.JCR);
      VenueSourceData crossref = VenueSourceData.create(123L, DataSourceCode.CROSSREF);

      assertThat(jcr.isFromJcr()).isTrue();
      assertThat(crossref.isFromJcr()).isFalse();
    }
  }

  @Nested
  @DisplayName("updateData() 方法")
  class UpdateDataTests {

    @Test
    @DisplayName("应正确更新原始数据和提取字段")
    void shouldUpdateData() {
      // Given
      VenueSourceData data =
          VenueSourceData.create(123L, DataSourceCode.OPENALEX, "S1", "{\"old\": true}", null);
      var originalFetchedAt = data.getFetchedAt();

      // When
      data.updateData("{\"new\": true}", "{\"extracted\": \"value\"}");

      // Then
      assertThat(data.getRawData()).isEqualTo("{\"new\": true}");
      assertThat(data.getExtractedData()).isEqualTo("{\"extracted\": \"value\"}");
      assertThat(data.getFetchedAt()).isAfterOrEqualTo(originalFetchedAt);
    }
  }

  @Nested
  @DisplayName("equals 和 hashCode")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("相同 venueId + sourceCode 应相等")
    void shouldBeEqualWithSameBusinessKey() {
      VenueSourceData data1 =
          VenueSourceData.create(123L, DataSourceCode.OPENALEX, "S1", "{}", null);
      VenueSourceData data2 =
          VenueSourceData.create(123L, DataSourceCode.OPENALEX, "S2", "{}", null);

      assertThat(data1).isEqualTo(data2);
      assertThat(data1.hashCode()).isEqualTo(data2.hashCode());
    }

    @Test
    @DisplayName("不同 venueId 应不相等")
    void shouldNotBeEqualWithDifferentVenueId() {
      VenueSourceData data1 = VenueSourceData.create(123L, DataSourceCode.OPENALEX);
      VenueSourceData data2 = VenueSourceData.create(456L, DataSourceCode.OPENALEX);

      assertThat(data1).isNotEqualTo(data2);
    }

    @Test
    @DisplayName("不同 sourceCode 应不相等")
    void shouldNotBeEqualWithDifferentSourceCode() {
      VenueSourceData data1 = VenueSourceData.create(123L, DataSourceCode.OPENALEX);
      VenueSourceData data2 = VenueSourceData.create(123L, DataSourceCode.PUBMED);

      assertThat(data1).isNotEqualTo(data2);
    }
  }

  @Nested
  @DisplayName("toString()")
  class ToStringTests {

    @Test
    @DisplayName("应包含关键信息")
    void shouldContainKeyInfo() {
      VenueSourceData data =
          VenueSourceData.create(123L, DataSourceCode.OPENALEX, "S1234567890", "{}", null);

      String str = data.toString();
      assertThat(str).contains("123");
      assertThat(str).contains("OPENALEX");
      assertThat(str).contains("S1234567890");
    }
  }
}
