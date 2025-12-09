package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.catalog.domain.model.vo.venue.VenueRating;
import com.patra.catalog.infra.persistence.entity.VenueRatingDO;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueRatingConverter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueRatingConverter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueRatingConverterTest {

  private VenueRatingConverterImpl converter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    converter = new VenueRatingConverterImpl();
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
      String ratingData = "{\"jif\": 42.778}";
      String categories = "[{\"category\": \"Medicine\"}]";
      Instant fetchedAt = Instant.now();

      VenueRating entity =
          VenueRating.of(
              123L,
              2024,
              RatingSystem.JCR,
              "Q1",
              new BigDecimal("42.778"),
              ratingData,
              categories,
              "https://example.com",
              fetchedAt);

      // When
      VenueRatingDO doEntity = converter.toDO(entity);

      // Then
      assertThat(doEntity).isNotNull();
      assertThat(doEntity.getVenueId()).isEqualTo(123L);
      assertThat(doEntity.getYear()).isEqualTo((short) 2024);
      assertThat(doEntity.getRatingSystem()).isEqualTo("JCR");
      assertThat(doEntity.getQuartile()).isEqualTo("Q1");
      assertThat(doEntity.getImpactScore()).isEqualByComparingTo(new BigDecimal("42.778"));
      assertThat(doEntity.getRatingData()).isNotNull();
      assertThat(doEntity.getRatingData().get("jif").asDouble()).isEqualTo(42.778);
      assertThat(doEntity.getCategories()).isNotNull();
      assertThat(doEntity.getSourceUrl()).isEqualTo("https://example.com");
      assertThat(doEntity.getFetchedAt()).isEqualTo(fetchedAt);
      assertThat(doEntity.getId()).isNull();
    }

    @Test
    @DisplayName("应正确处理 null 值对象")
    void shouldHandleNullEntity() {
      // When
      VenueRatingDO doEntity = converter.toDO(null);

      // Then
      assertThat(doEntity).isNull();
    }

    @Test
    @DisplayName("应正确处理 null JSON 字段")
    void shouldHandleNullJsonFields() {
      // Given
      VenueRating entity = VenueRating.create(123L, 2024, RatingSystem.JCR);

      // When
      VenueRatingDO doEntity = converter.toDO(entity);

      // Then
      assertThat(doEntity.getRatingData()).isNull();
      assertThat(doEntity.getCategories()).isNull();
    }

    @Test
    @DisplayName("应正确处理空白 JSON 字符串")
    void shouldHandleBlankJsonString() {
      // Given
      VenueRating entity =
          VenueRating.of(123L, 2024, RatingSystem.JCR, null, null, "  ", "  ", null, null);

      // When
      VenueRatingDO doEntity = converter.toDO(entity);

      // Then
      assertThat(doEntity.getRatingData()).isNull();
      assertThat(doEntity.getCategories()).isNull();
    }

    @Test
    @DisplayName("应正确处理无效 JSON 字符串")
    void shouldHandleInvalidJsonString() {
      // Given
      VenueRating entity =
          VenueRating.of(
              123L, 2024, RatingSystem.JCR, null, null, "invalid json", "also invalid", null, null);

      // When
      VenueRatingDO doEntity = converter.toDO(entity);

      // Then
      assertThat(doEntity.getRatingData()).isNull();
      assertThat(doEntity.getCategories()).isNull();
    }
  }

  @Nested
  @DisplayName("toEntity() 方法测试")
  class ToEntityTests {

    @Test
    @DisplayName("应正确转换完整的 DO 到值对象")
    void shouldConvertFullDOToEntity() throws Exception {
      // Given
      VenueRatingDO doEntity = new VenueRatingDO();
      doEntity.setId(1L);
      doEntity.setVenueId(123L);
      doEntity.setYear((short) 2024);
      doEntity.setRatingSystem("JCR");
      doEntity.setQuartile("Q1");
      doEntity.setImpactScore(new BigDecimal("42.778"));
      doEntity.setRatingData(objectMapper.readTree("{\"jif\": 42.778}"));
      doEntity.setCategories(objectMapper.readTree("[{\"category\": \"Medicine\"}]"));
      doEntity.setSourceUrl("https://example.com");
      doEntity.setFetchedAt(Instant.now());

      // When
      VenueRating entity = converter.toEntity(doEntity);

      // Then
      assertThat(entity).isNotNull();
      assertThat(entity.venueId()).isEqualTo(123L);
      assertThat(entity.year()).isEqualTo(2024);
      assertThat(entity.ratingSystem()).isEqualTo(RatingSystem.JCR);
      assertThat(entity.quartile()).isEqualTo("Q1");
      assertThat(entity.impactScore()).isEqualByComparingTo(new BigDecimal("42.778"));
      assertThat(entity.ratingData()).isEqualTo("{\"jif\":42.778}");
      assertThat(entity.categories()).contains("Medicine");
      assertThat(entity.sourceUrl()).isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("应正确处理 null DO")
    void shouldHandleNullDO() {
      // When
      VenueRating entity = converter.toEntity(null);

      // Then
      assertThat(entity).isNull();
    }

    @Test
    @DisplayName("应正确处理 null JsonNode 字段")
    void shouldHandleNullJsonNodeFields() {
      // Given
      VenueRatingDO doEntity = new VenueRatingDO();
      doEntity.setVenueId(123L);
      doEntity.setYear((short) 2024);
      doEntity.setRatingSystem("JCR");
      doEntity.setRatingData(null);
      doEntity.setCategories(null);

      // When
      VenueRating entity = converter.toEntity(doEntity);

      // Then
      assertThat(entity.ratingData()).isNull();
      assertThat(entity.categories()).isNull();
    }

    @Test
    @DisplayName("无效枚举代码应使用 JCR 默认值")
    void shouldUseDefaultRatingSystemForInvalidCode() {
      // Given
      VenueRatingDO doEntity = new VenueRatingDO();
      doEntity.setVenueId(123L);
      doEntity.setYear((short) 2024);
      doEntity.setRatingSystem("INVALID_SYSTEM");

      // When
      VenueRating entity = converter.toEntity(doEntity);

      // Then
      assertThat(entity.ratingSystem()).isEqualTo(RatingSystem.JCR);
    }

    @Test
    @DisplayName("null 枚举代码应使用 JCR 默认值")
    void shouldUseDefaultRatingSystemForNullCode() {
      // Given
      VenueRatingDO doEntity = new VenueRatingDO();
      doEntity.setVenueId(123L);
      doEntity.setYear((short) 2024);
      doEntity.setRatingSystem(null);

      // When
      VenueRating entity = converter.toEntity(doEntity);

      // Then
      assertThat(entity.ratingSystem()).isEqualTo(RatingSystem.JCR);
    }

    @Test
    @DisplayName("应正确转换所有评价体系枚举")
    void shouldConvertAllRatingSystems() {
      for (RatingSystem system : RatingSystem.values()) {
        // Given
        VenueRatingDO doEntity = new VenueRatingDO();
        doEntity.setVenueId(123L);
        doEntity.setYear((short) 2024);
        doEntity.setRatingSystem(system.getCode());

        // When
        VenueRating entity = converter.toEntity(doEntity);

        // Then
        assertThat(entity.ratingSystem()).isEqualTo(system);
      }
    }
  }
}
