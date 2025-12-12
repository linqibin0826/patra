package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.domain.model.aggregate.VenueRatingAggregate;
import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.catalog.domain.model.vo.venue.VenueRatingId;
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
/// 测试 `VenueRatingAggregate` 聚合根与 `VenueRatingDO` 数据库实体之间的转换。
///
/// @author linqibin
/// @since 0.6.0
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
    @DisplayName("应正确转换完整的聚合根到 DO")
    void shouldConvertFullAggregateToDO() {
      // Given
      String ratingData = "{\"jif\": 42.778}";
      String categories = "[{\"category\": \"Medicine\"}]";
      Instant fetchedAt = Instant.now();

      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(123L, 2024, RatingSystem.JCR, "Q1", new BigDecimal("42.778"));
      aggregate.updateRatingDetails(ratingData, categories);
      aggregate.recordSource("https://example.com", fetchedAt);

      // When
      VenueRatingDO doEntity = converter.toDO(aggregate);

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
      assertThat(doEntity.getId()).isNull(); // 新建聚合根的 ID 为 null
    }

    @Test
    @DisplayName("应正确处理 null 聚合根")
    void shouldHandleNullAggregate() {
      // When
      VenueRatingDO doEntity = converter.toDO(null);

      // Then
      assertThat(doEntity).isNull();
    }

    @Test
    @DisplayName("应正确处理 null JSON 字段")
    void shouldHandleNullJsonFields() {
      // Given: 创建不带 JSON 字段的聚合根
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.create(123L, 2024, RatingSystem.JCR, null, null);

      // When
      VenueRatingDO doEntity = converter.toDO(aggregate);

      // Then
      assertThat(doEntity.getRatingData()).isNull();
      assertThat(doEntity.getCategories()).isNull();
    }

    @Test
    @DisplayName("应正确转换已持久化的聚合根（带 ID）")
    void shouldConvertPersistedAggregateToDO() {
      // Given: 模拟从数据库恢复的聚合根
      VenueRatingAggregate aggregate =
          VenueRatingAggregate.restore(VenueRatingId.of(999L), 123L, 2024, RatingSystem.JCR, 5L);
      aggregate.restoreState("Q1", new BigDecimal("42.778"), null, null, null, null);

      // When
      VenueRatingDO doEntity = converter.toDO(aggregate);

      // Then
      assertThat(doEntity.getId()).isEqualTo(999L);
      assertThat(doEntity.getVersion()).isEqualTo(5L);
    }
  }

  @Nested
  @DisplayName("toAggregate() 方法测试")
  class ToAggregateTests {

    @Test
    @DisplayName("应正确转换完整的 DO 到聚合根")
    void shouldConvertFullDOToAggregate() throws Exception {
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
      doEntity.setVersion(3L);

      // When
      VenueRatingAggregate aggregate = converter.toAggregate(doEntity);

      // Then
      assertThat(aggregate).isNotNull();
      assertThat(aggregate.getId()).isEqualTo(VenueRatingId.of(1L));
      assertThat(aggregate.getVenueId()).isEqualTo(123L);
      assertThat(aggregate.getYear()).isEqualTo(2024);
      assertThat(aggregate.getRatingSystem()).isEqualTo(RatingSystem.JCR);
      assertThat(aggregate.getQuartile()).isEqualTo("Q1");
      assertThat(aggregate.getImpactScore()).isEqualByComparingTo(new BigDecimal("42.778"));
      assertThat(aggregate.getRatingData()).isEqualTo("{\"jif\":42.778}");
      assertThat(aggregate.getCategories()).contains("Medicine");
      assertThat(aggregate.getSourceUrl()).isEqualTo("https://example.com");
      assertThat(aggregate.getVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("应正确处理 null DO")
    void shouldHandleNullDO() {
      // When
      VenueRatingAggregate aggregate = converter.toAggregate(null);

      // Then
      assertThat(aggregate).isNull();
    }

    @Test
    @DisplayName("应正确处理 null JsonNode 字段")
    void shouldHandleNullJsonNodeFields() {
      // Given
      VenueRatingDO doEntity = new VenueRatingDO();
      doEntity.setId(1L);
      doEntity.setVenueId(123L);
      doEntity.setYear((short) 2024);
      doEntity.setRatingSystem("JCR");
      doEntity.setRatingData(null);
      doEntity.setCategories(null);
      doEntity.setVersion(0L);

      // When
      VenueRatingAggregate aggregate = converter.toAggregate(doEntity);

      // Then
      assertThat(aggregate.getRatingData()).isNull();
      assertThat(aggregate.getCategories()).isNull();
    }

    @Test
    @DisplayName("无效枚举代码应使用 JCR 默认值")
    void shouldUseDefaultRatingSystemForInvalidCode() {
      // Given
      VenueRatingDO doEntity = new VenueRatingDO();
      doEntity.setId(1L);
      doEntity.setVenueId(123L);
      doEntity.setYear((short) 2024);
      doEntity.setRatingSystem("INVALID_SYSTEM");
      doEntity.setVersion(0L);

      // When
      VenueRatingAggregate aggregate = converter.toAggregate(doEntity);

      // Then
      assertThat(aggregate.getRatingSystem()).isEqualTo(RatingSystem.JCR);
    }

    @Test
    @DisplayName("null 枚举代码应使用 JCR 默认值")
    void shouldUseDefaultRatingSystemForNullCode() {
      // Given
      VenueRatingDO doEntity = new VenueRatingDO();
      doEntity.setId(1L);
      doEntity.setVenueId(123L);
      doEntity.setYear((short) 2024);
      doEntity.setRatingSystem(null);
      doEntity.setVersion(0L);

      // When
      VenueRatingAggregate aggregate = converter.toAggregate(doEntity);

      // Then
      assertThat(aggregate.getRatingSystem()).isEqualTo(RatingSystem.JCR);
    }

    @Test
    @DisplayName("应正确转换所有评价体系枚举")
    void shouldConvertAllRatingSystems() {
      for (RatingSystem system : RatingSystem.values()) {
        // Given
        VenueRatingDO doEntity = new VenueRatingDO();
        doEntity.setId(1L);
        doEntity.setVenueId(123L);
        doEntity.setYear((short) 2024);
        doEntity.setRatingSystem(system.getCode());
        doEntity.setVersion(0L);

        // When
        VenueRatingAggregate aggregate = converter.toAggregate(doEntity);

        // Then
        assertThat(aggregate.getRatingSystem()).isEqualTo(system);
      }
    }

    @Test
    @DisplayName("恢复的聚合根应该不是脏状态")
    void restoredAggregateShouldNotBeDirty() {
      // Given
      VenueRatingDO doEntity = new VenueRatingDO();
      doEntity.setId(1L);
      doEntity.setVenueId(123L);
      doEntity.setYear((short) 2024);
      doEntity.setRatingSystem("JCR");
      doEntity.setVersion(0L);

      // When
      VenueRatingAggregate aggregate = converter.toAggregate(doEntity);

      // Then
      assertThat(aggregate.isDirty()).isFalse();
      assertThat(aggregate.isTransient()).isFalse();
    }
  }
}
