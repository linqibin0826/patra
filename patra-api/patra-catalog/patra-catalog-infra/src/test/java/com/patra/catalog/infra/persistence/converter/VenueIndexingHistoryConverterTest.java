package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.enums.CitationSubset;
import com.patra.catalog.domain.model.enums.IndexingTreatment;
import com.patra.catalog.domain.model.vo.venue.VenueIndexingHistory;
import com.patra.catalog.infra.persistence.entity.VenueIndexingHistoryDO;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueIndexingHistoryConverter 单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 测试 toDO() 和 toEntity() 转换方法
/// - 测试枚举转换边界情况（无效枚举值返回 null）
/// - 测试 Boolean 包装类型处理
/// - 测试 null 输入处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueIndexingHistoryConverter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueIndexingHistoryConverterTest {

  private VenueIndexingHistoryConverterImpl converter;

  @BeforeEach
  void setUp() {
    converter = new VenueIndexingHistoryConverterImpl();
  }

  @Nested
  @DisplayName("toDO() 方法测试")
  class ToDOTests {

    @Test
    @DisplayName("应该正确转换当前索引记录到 DO")
    void shouldConvertCurrentIndexingToDO() {
      // Given
      VenueIndexingHistory history =
          VenueIndexingHistory.createCurrentIndexing(
              "MEDLINE", IndexingTreatment.FULL, CitationSubset.IM, 1966, "1", "1");

      // When
      VenueIndexingHistoryDO result = converter.toDO(history);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getIndexingSource()).isEqualTo("MEDLINE");
      assertThat(result.getCurrentlyIndexed()).isTrue();
      assertThat(result.getIndexingTreatment()).isEqualTo("FULL");
      assertThat(result.getCitationSubset()).isEqualTo("IM");
      assertThat(result.getStartYear()).isEqualTo(1966);
      assertThat(result.getStartVolume()).isEqualTo("1");
      assertThat(result.getStartIssue()).isEqualTo("1");
      assertThat(result.getEndYear()).isNull();
      // id 和 venueId 由调用方设置
      assertThat(result.getId()).isNull();
      assertThat(result.getVenueId()).isNull();
    }

    @Test
    @DisplayName("应该正确转换历史索引记录到 DO")
    void shouldConvertHistoricalIndexingToDO() {
      // Given
      VenueIndexingHistory history =
          VenueIndexingHistory.createHistoricalIndexing(
              "MEDLINE", 1950, "1", "1", 1965, "15", "12");

      // When
      VenueIndexingHistoryDO result = converter.toDO(history);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getIndexingSource()).isEqualTo("MEDLINE");
      assertThat(result.getCurrentlyIndexed()).isFalse();
      assertThat(result.getIndexingTreatment()).isNull();
      assertThat(result.getCitationSubset()).isNull();
      assertThat(result.getStartYear()).isEqualTo(1950);
      assertThat(result.getEndYear()).isEqualTo(1965);
    }

    @Test
    @DisplayName("枚举值为 null 时应该正确处理")
    void shouldHandleNullEnumValues() {
      // Given
      VenueIndexingHistory history = VenueIndexingHistory.createSimpleCurrentIndexing("PMC");

      // When
      VenueIndexingHistoryDO result = converter.toDO(history);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getIndexingSource()).isEqualTo("PMC");
      assertThat(result.getCurrentlyIndexed()).isTrue();
      assertThat(result.getIndexingTreatment()).isNull();
      assertThat(result.getCitationSubset()).isNull();
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenHistoryIsNull() {
      assertThat(converter.toDO(null)).isNull();
    }
  }

  @Nested
  @DisplayName("toEntity() 方法测试")
  class ToEntityTests {

    @Test
    @DisplayName("应该正确转换完整的 DO 到领域实体")
    void shouldConvertFullDOToEntity() {
      // Given
      VenueIndexingHistoryDO doEntity = new VenueIndexingHistoryDO();
      doEntity.setIndexingSource("MEDLINE");
      doEntity.setCurrentlyIndexed(true);
      doEntity.setIndexingTreatment("FULL");
      doEntity.setCitationSubset("IM");
      doEntity.setStartYear(1966);
      doEntity.setStartVolume("1");
      doEntity.setStartIssue("1");

      // When
      VenueIndexingHistory result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.indexingSource()).isEqualTo("MEDLINE");
      assertThat(result.currentlyIndexed()).isTrue();
      assertThat(result.indexingTreatment()).isEqualTo(IndexingTreatment.FULL);
      assertThat(result.citationSubset()).isEqualTo(CitationSubset.IM);
      assertThat(result.startYear()).isEqualTo(1966);
    }

    @Test
    @DisplayName("枚举代码值为 null 时应该返回 null 枚举")
    void shouldReturnNullEnumWhenCodeIsNull() {
      // Given
      VenueIndexingHistoryDO doEntity = new VenueIndexingHistoryDO();
      doEntity.setIndexingSource("PMC");
      doEntity.setCurrentlyIndexed(true);
      doEntity.setIndexingTreatment(null);
      doEntity.setCitationSubset(null);

      // When
      VenueIndexingHistory result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.indexingTreatment()).isNull();
      assertThat(result.citationSubset()).isNull();
    }

    @Test
    @DisplayName("无效的枚举代码值应该返回 null 枚举")
    void shouldReturnNullEnumWhenCodeIsInvalid() {
      // Given
      VenueIndexingHistoryDO doEntity = new VenueIndexingHistoryDO();
      doEntity.setIndexingSource("MEDLINE");
      doEntity.setCurrentlyIndexed(true);
      doEntity.setIndexingTreatment("INVALID_TREATMENT");
      doEntity.setCitationSubset("INVALID_SUBSET");

      // When
      VenueIndexingHistory result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.indexingTreatment()).isNull();
      assertThat(result.citationSubset()).isNull();
    }

    @Test
    @DisplayName("Boolean 包装类型为 null 时应该处理为 false")
    void shouldTreatNullBooleanAsFalse() {
      // Given
      VenueIndexingHistoryDO doEntity = new VenueIndexingHistoryDO();
      doEntity.setIndexingSource("MEDLINE");
      doEntity.setCurrentlyIndexed(null);

      // When
      VenueIndexingHistory result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.currentlyIndexed()).isFalse();
    }

    @Test
    @DisplayName("应该正确转换所有支持的引用子集类型")
    void shouldConvertAllSupportedCitationSubsets() {
      for (CitationSubset subset : CitationSubset.values()) {
        // Given
        VenueIndexingHistoryDO doEntity = new VenueIndexingHistoryDO();
        doEntity.setIndexingSource("MEDLINE");
        doEntity.setCurrentlyIndexed(true);
        doEntity.setCitationSubset(subset.getCode());

        // When
        VenueIndexingHistory result = converter.toEntity(doEntity);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.citationSubset()).isEqualTo(subset);
      }
    }
  }
}
