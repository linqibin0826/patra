package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.infra.persistence.entity.VenuePublicationStatsDO;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenuePublicationStatsConverter 单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 测试 toDO() 和 toEntity() 转换方法
/// - 测试类型转换（int <-> short）
/// - 测试 null 值处理和默认值逻辑
/// - 测试 null 输入处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenuePublicationStatsConverter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenuePublicationStatsConverterTest {

  private VenuePublicationStatsConverterImpl converter;

  @BeforeEach
  void setUp() {
    converter = new VenuePublicationStatsConverterImpl();
  }

  @Nested
  @DisplayName("toDO() 方法测试")
  class ToDOTests {

    @Test
    @DisplayName("应该正确转换完整统计数据到 DO")
    void shouldConvertFullStatsToDO() {
      // Given
      VenuePublicationStats stats = VenuePublicationStats.create(2024, 1500, 25000, 800);

      // When
      VenuePublicationStatsDO result = converter.toDO(stats);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getYear()).isEqualTo((short) 2024);
      assertThat(result.getWorksCount()).isEqualTo(1500);
      assertThat(result.getCitedByCount()).isEqualTo(25000);
      assertThat(result.getOaWorksCount()).isEqualTo(800);
      // id 和 venueId 由调用方设置
      assertThat(result.getId()).isNull();
      assertThat(result.getVenueId()).isNull();
    }

    @Test
    @DisplayName("应该正确转换不含 OA 数据的统计到 DO")
    void shouldConvertStatsWithoutOaToDO() {
      // Given
      VenuePublicationStats stats = VenuePublicationStats.create(2023, 1200, 20000);

      // When
      VenuePublicationStatsDO result = converter.toDO(stats);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getYear()).isEqualTo((short) 2023);
      assertThat(result.getWorksCount()).isEqualTo(1200);
      assertThat(result.getCitedByCount()).isEqualTo(20000);
      assertThat(result.getOaWorksCount()).isNull();
    }

    @Test
    @DisplayName("应该正确转换空统计到 DO")
    void shouldConvertEmptyStatsToDO() {
      // Given
      VenuePublicationStats stats = VenuePublicationStats.empty(2022);

      // When
      VenuePublicationStatsDO result = converter.toDO(stats);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getYear()).isEqualTo((short) 2022);
      assertThat(result.getWorksCount()).isZero();
      assertThat(result.getCitedByCount()).isZero();
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenStatsIsNull() {
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
      VenuePublicationStatsDO doEntity = new VenuePublicationStatsDO();
      doEntity.setYear((short) 2024);
      doEntity.setWorksCount(1500);
      doEntity.setCitedByCount(25000);
      doEntity.setOaWorksCount(800);

      // When
      VenuePublicationStats result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.year()).isEqualTo(2024);
      assertThat(result.worksCount()).isEqualTo(1500);
      assertThat(result.citedByCount()).isEqualTo(25000);
      assertThat(result.oaWorksCount()).isEqualTo(800);
    }

    @Test
    @DisplayName("worksCount 为 null 时应该使用默认值 0")
    void shouldUseDefaultZeroWhenWorksCountIsNull() {
      // Given
      VenuePublicationStatsDO doEntity = new VenuePublicationStatsDO();
      doEntity.setYear((short) 2024);
      doEntity.setWorksCount(null);
      doEntity.setCitedByCount(100);

      // When
      VenuePublicationStats result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.worksCount()).isZero();
    }

    @Test
    @DisplayName("citedByCount 为 null 时应该使用默认值 0")
    void shouldUseDefaultZeroWhenCitedByCountIsNull() {
      // Given
      VenuePublicationStatsDO doEntity = new VenuePublicationStatsDO();
      doEntity.setYear((short) 2024);
      doEntity.setWorksCount(100);
      doEntity.setCitedByCount(null);

      // When
      VenuePublicationStats result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.citedByCount()).isZero();
    }

    @Test
    @DisplayName("oaWorksCount 为 null 时应该保持为 null")
    void shouldKeepNullOaWorksCount() {
      // Given
      VenuePublicationStatsDO doEntity = new VenuePublicationStatsDO();
      doEntity.setYear((short) 2024);
      doEntity.setWorksCount(100);
      doEntity.setCitedByCount(500);
      doEntity.setOaWorksCount(null);

      // When
      VenuePublicationStats result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.oaWorksCount()).isNull();
      assertThat(result.hasOaWorksCount()).isFalse();
    }

    @Test
    @DisplayName("short 年份应该正确转换为 int")
    void shouldConvertShortYearToInt() {
      // Given
      VenuePublicationStatsDO doEntity = new VenuePublicationStatsDO();
      doEntity.setYear((short) 1990);
      doEntity.setWorksCount(50);
      doEntity.setCitedByCount(200);

      // When
      VenuePublicationStats result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.year()).isEqualTo(1990);
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenDOIsNull() {
      assertThat(converter.toEntity(null)).isNull();
    }
  }
}
