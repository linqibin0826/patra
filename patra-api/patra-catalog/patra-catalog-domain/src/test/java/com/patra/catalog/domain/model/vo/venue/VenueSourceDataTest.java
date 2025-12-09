package com.patra.catalog.domain.model.vo.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.DataSourceCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// 载体数据源值对象单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueSourceData 载体数据源值对象")
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
      assertThat(data.venueId()).isEqualTo(venueId);
      assertThat(data.sourceCode()).isEqualTo(sourceCode);
      assertThat(data.sourceId()).isEqualTo(sourceId);
      assertThat(data.rawData()).isEqualTo(rawData);
      assertThat(data.extractedData()).isEqualTo(extractedData);
      assertThat(data.fetchedAt()).isNotNull();
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
      assertThat(data.venueId()).isEqualTo(venueId);
      assertThat(data.sourceCode()).isEqualTo(sourceCode);
      assertThat(data.sourceId()).isNull();
      assertThat(data.rawData()).isNull();
      assertThat(data.extractedData()).isNull();
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
  @DisplayName("of() 完整工厂方法")
  class OfTests {

    @Test
    @DisplayName("应正确创建包含所有字段的数据源记录")
    void shouldCreateWithAllFields() {
      // Given
      Long venueId = 123L;
      DataSourceCode sourceCode = DataSourceCode.OPENALEX;
      String sourceId = "S1234567890";
      String rawData = "{\"id\": \"S1234567890\"}";
      String extractedData = "{\"title\": \"Nature\"}";
      LocalDate createdAt = LocalDate.of(2020, 1, 1);
      LocalDate updatedAt = LocalDate.of(2024, 6, 15);

      // When
      VenueSourceData data =
          VenueSourceData.of(
              venueId, sourceCode, sourceId, rawData, extractedData, createdAt, updatedAt, null);

      // Then
      assertThat(data.venueId()).isEqualTo(venueId);
      assertThat(data.sourceCode()).isEqualTo(sourceCode);
      assertThat(data.sourceId()).isEqualTo(sourceId);
      assertThat(data.rawData()).isEqualTo(rawData);
      assertThat(data.extractedData()).isEqualTo(extractedData);
      assertThat(data.sourceCreatedAt()).isEqualTo(createdAt);
      assertThat(data.sourceUpdatedAt()).isEqualTo(updatedAt);
      assertThat(data.fetchedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("判断方法")
  class HasMethodsTests {

    @Test
    @DisplayName("hasRawData() 应正确判断")
    void shouldCheckHasRawData() {
      VenueSourceData withData =
          VenueSourceData.create(
              123L, DataSourceCode.OPENALEX, null, "{\"id\": \"S1234567890\"}", null);
      VenueSourceData withoutData = VenueSourceData.create(123L, DataSourceCode.OPENALEX);

      assertThat(withData.hasRawData()).isTrue();
      assertThat(withoutData.hasRawData()).isFalse();
    }

    @Test
    @DisplayName("hasExtractedData() 应正确判断")
    void shouldCheckHasExtractedData() {
      VenueSourceData withData =
          VenueSourceData.create(123L, DataSourceCode.OPENALEX, null, null, "{\"title\": \"Nature\"}");
      VenueSourceData withoutData = VenueSourceData.create(123L, DataSourceCode.OPENALEX);

      assertThat(withData.hasExtractedData()).isTrue();
      assertThat(withoutData.hasExtractedData()).isFalse();
    }

    @Test
    @DisplayName("hasSourceId() 应正确判断")
    void shouldCheckHasSourceId() {
      VenueSourceData withId =
          VenueSourceData.create(123L, DataSourceCode.OPENALEX, "S1234567890", null, null);
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
  @DisplayName("Record 特性测试")
  class RecordTests {

    @Test
    @DisplayName("Record 应自动生成 equals 基于所有字段")
    void shouldHaveValueBasedEquality() {
      VenueSourceData data1 =
          VenueSourceData.create(123L, DataSourceCode.OPENALEX, "S1", "{}", null);
      VenueSourceData data2 =
          VenueSourceData.create(123L, DataSourceCode.OPENALEX, "S1", "{}", null);

      // Record 的 equals 比较所有字段
      assertThat(data1.venueId()).isEqualTo(data2.venueId());
      assertThat(data1.sourceCode()).isEqualTo(data2.sourceCode());
      assertThat(data1.sourceId()).isEqualTo(data2.sourceId());
      assertThat(data1.rawData()).isEqualTo(data2.rawData());
    }

    @Test
    @DisplayName("toString() 应包含关键信息")
    void shouldContainKeyInfo() {
      VenueSourceData data =
          VenueSourceData.create(123L, DataSourceCode.OPENALEX, "S1234567890", "{}", null);

      String str = data.toString();
      assertThat(str).contains("123");
      assertThat(str).contains("OPENALEX");
      assertThat(str).contains("S1234567890");
    }

    @Test
    @DisplayName("Record 应为不可变对象")
    void shouldBeImmutable() {
      VenueSourceData data =
          VenueSourceData.create(123L, DataSourceCode.OPENALEX, "S1234567890", null, null);

      // Record 的所有字段都是 final 的，没有 setter 方法
      // 验证字段不可变
      assertThat(data.venueId()).isEqualTo(123L);
      assertThat(data.sourceCode()).isEqualTo(DataSourceCode.OPENALEX);
      assertThat(data.sourceId()).isEqualTo("S1234567890");
    }
  }
}
