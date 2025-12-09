package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.domain.model.enums.DataSourceCode;
import com.patra.catalog.domain.model.vo.venue.VenueSourceData;
import com.patra.catalog.infra.persistence.entity.VenueSourceDataDO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueSourceDataConverter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueSourceDataConverter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueSourceDataConverterTest {

  private VenueSourceDataConverterImpl converter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    converter = new VenueSourceDataConverterImpl();
    objectMapper = new ObjectMapper();
    converter.objectMapper = objectMapper;
  }

  @Nested
  @DisplayName("toDO() 方法测试")
  class ToDOTests {

    @Test
    @DisplayName("应正确转换完整的值对象到 DO")
    void shouldConvertFullEntityToDO() {
      // Given
      String rawData = "{\"id\": \"S1234567890\"}";
      String extractedData = "{\"title\": \"Nature\"}";
      LocalDate createdAt = LocalDate.of(2020, 1, 1);
      LocalDate updatedAt = LocalDate.of(2024, 6, 15);
      Instant fetchedAt = Instant.now();

      VenueSourceData entity =
          VenueSourceData.of(
              123L,
              DataSourceCode.OPENALEX,
              "S1234567890",
              rawData,
              extractedData,
              createdAt,
              updatedAt,
              fetchedAt);

      // When
      VenueSourceDataDO doEntity = converter.toDO(entity);

      // Then
      assertThat(doEntity).isNotNull();
      assertThat(doEntity.getVenueId()).isEqualTo(123L);
      assertThat(doEntity.getSourceCode()).isEqualTo("OPENALEX");
      assertThat(doEntity.getSourceId()).isEqualTo("S1234567890");
      assertThat(doEntity.getRawData()).isNotNull();
      assertThat(doEntity.getRawData().get("id").asText()).isEqualTo("S1234567890");
      assertThat(doEntity.getExtractedData()).isNotNull();
      assertThat(doEntity.getExtractedData().get("title").asText()).isEqualTo("Nature");
      assertThat(doEntity.getSourceCreatedAt()).isEqualTo(createdAt);
      assertThat(doEntity.getSourceUpdatedAt()).isEqualTo(updatedAt);
      assertThat(doEntity.getFetchedAt()).isEqualTo(fetchedAt);
      assertThat(doEntity.getId()).isNull();
    }

    @Test
    @DisplayName("应正确处理 null 值对象")
    void shouldHandleNullEntity() {
      // When
      VenueSourceDataDO doEntity = converter.toDO(null);

      // Then
      assertThat(doEntity).isNull();
    }

    @Test
    @DisplayName("应正确处理 null JSON 字段")
    void shouldHandleNullJsonFields() {
      // Given
      VenueSourceData entity = VenueSourceData.create(123L, DataSourceCode.PUBMED);

      // When
      VenueSourceDataDO doEntity = converter.toDO(entity);

      // Then
      assertThat(doEntity.getRawData()).isNull();
      assertThat(doEntity.getExtractedData()).isNull();
    }

    @Test
    @DisplayName("应正确处理空白 JSON 字符串")
    void shouldHandleBlankJsonString() {
      // Given
      VenueSourceData entity =
          VenueSourceData.of(123L, DataSourceCode.OPENALEX, null, "  ", "  ", null, null, null);

      // When
      VenueSourceDataDO doEntity = converter.toDO(entity);

      // Then
      assertThat(doEntity.getRawData()).isNull();
      assertThat(doEntity.getExtractedData()).isNull();
    }

    @Test
    @DisplayName("应正确处理无效 JSON 字符串")
    void shouldHandleInvalidJsonString() {
      // Given
      VenueSourceData entity =
          VenueSourceData.of(
              123L,
              DataSourceCode.OPENALEX,
              null,
              "invalid json",
              "also invalid",
              null,
              null,
              null);

      // When
      VenueSourceDataDO doEntity = converter.toDO(entity);

      // Then
      assertThat(doEntity.getRawData()).isNull();
      assertThat(doEntity.getExtractedData()).isNull();
    }

    @Test
    @DisplayName("应正确转换所有数据源枚举")
    void shouldConvertAllDataSourceCodes() {
      for (DataSourceCode sourceCode : DataSourceCode.values()) {
        // Given
        VenueSourceData entity = VenueSourceData.create(123L, sourceCode);

        // When
        VenueSourceDataDO doEntity = converter.toDO(entity);

        // Then
        assertThat(doEntity.getSourceCode()).isEqualTo(sourceCode.getCode());
      }
    }
  }

  @Nested
  @DisplayName("toEntity() 方法测试")
  class ToEntityTests {

    @Test
    @DisplayName("应正确转换完整的 DO 到值对象")
    void shouldConvertFullDOToEntity() throws Exception {
      // Given
      VenueSourceDataDO doEntity = new VenueSourceDataDO();
      doEntity.setId(1L);
      doEntity.setVenueId(123L);
      doEntity.setSourceCode("OPENALEX");
      doEntity.setSourceId("S1234567890");
      doEntity.setRawData(objectMapper.readTree("{\"id\": \"S1234567890\"}"));
      doEntity.setExtractedData(objectMapper.readTree("{\"title\": \"Nature\"}"));
      doEntity.setSourceCreatedAt(LocalDate.of(2020, 1, 1));
      doEntity.setSourceUpdatedAt(LocalDate.of(2024, 6, 15));
      doEntity.setFetchedAt(Instant.now());

      // When
      VenueSourceData entity = converter.toEntity(doEntity);

      // Then
      assertThat(entity).isNotNull();
      assertThat(entity.venueId()).isEqualTo(123L);
      assertThat(entity.sourceCode()).isEqualTo(DataSourceCode.OPENALEX);
      assertThat(entity.sourceId()).isEqualTo("S1234567890");
      assertThat(entity.rawData()).isEqualTo("{\"id\":\"S1234567890\"}");
      assertThat(entity.extractedData()).contains("Nature");
      assertThat(entity.sourceCreatedAt()).isEqualTo(LocalDate.of(2020, 1, 1));
      assertThat(entity.sourceUpdatedAt()).isEqualTo(LocalDate.of(2024, 6, 15));
    }

    @Test
    @DisplayName("应正确处理 null DO")
    void shouldHandleNullDO() {
      // When
      VenueSourceData entity = converter.toEntity(null);

      // Then
      assertThat(entity).isNull();
    }

    @Test
    @DisplayName("应正确处理 null JsonNode 字段")
    void shouldHandleNullJsonNodeFields() {
      // Given
      VenueSourceDataDO doEntity = new VenueSourceDataDO();
      doEntity.setVenueId(123L);
      doEntity.setSourceCode("OPENALEX");
      doEntity.setRawData(null);
      doEntity.setExtractedData(null);

      // When
      VenueSourceData entity = converter.toEntity(doEntity);

      // Then
      assertThat(entity.rawData()).isNull();
      assertThat(entity.extractedData()).isNull();
    }

    @Test
    @DisplayName("无效枚举代码应使用 OPENALEX 默认值")
    void shouldUseDefaultSourceCodeForInvalidCode() {
      // Given
      VenueSourceDataDO doEntity = new VenueSourceDataDO();
      doEntity.setVenueId(123L);
      doEntity.setSourceCode("INVALID_SOURCE");

      // When
      VenueSourceData entity = converter.toEntity(doEntity);

      // Then
      assertThat(entity.sourceCode()).isEqualTo(DataSourceCode.OPENALEX);
    }

    @Test
    @DisplayName("null 枚举代码应使用 OPENALEX 默认值")
    void shouldUseDefaultSourceCodeForNullCode() {
      // Given
      VenueSourceDataDO doEntity = new VenueSourceDataDO();
      doEntity.setVenueId(123L);
      doEntity.setSourceCode(null);

      // When
      VenueSourceData entity = converter.toEntity(doEntity);

      // Then
      assertThat(entity.sourceCode()).isEqualTo(DataSourceCode.OPENALEX);
    }

    @Test
    @DisplayName("应正确转换所有数据源枚举")
    void shouldConvertAllDataSourceCodes() {
      for (DataSourceCode sourceCode : DataSourceCode.values()) {
        // Given
        VenueSourceDataDO doEntity = new VenueSourceDataDO();
        doEntity.setVenueId(123L);
        doEntity.setSourceCode(sourceCode.getCode());

        // When
        VenueSourceData entity = converter.toEntity(doEntity);

        // Then
        assertThat(entity.sourceCode()).isEqualTo(sourceCode);
      }
    }
  }
}
