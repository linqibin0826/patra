package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.enums.VenueRelationType;
import com.patra.catalog.domain.model.vo.venue.VenueRelation;
import com.patra.catalog.infra.persistence.entity.VenueRelationDO;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueRelationConverter 单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 测试 toDO() 和 toEntity() 转换方法
/// - 测试枚举转换边界情况（无效枚举值使用默认值 PRECEDING）
/// - 测试可选字段处理
/// - 测试 null 输入处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueRelationConverter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueRelationConverterTest {

  private VenueRelationConverterImpl converter;

  @BeforeEach
  void setUp() {
    converter = new VenueRelationConverterImpl();
  }

  @Nested
  @DisplayName("toDO() 方法测试")
  class ToDOTests {

    @Test
    @DisplayName("应该正确转换前刊关系到 DO")
    void shouldConvertPrecedingRelationToDO() {
      // Given
      VenueRelation relation =
          VenueRelation.create("Journal of Medicine", VenueRelationType.PRECEDING);

      // When
      VenueRelationDO result = converter.toDO(relation);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getRelatedTitle()).isEqualTo("Journal of Medicine");
      assertThat(result.getRelationType()).isEqualTo("PRECEDING");
      assertThat(result.getRelatedVenueId()).isNull();
      assertThat(result.getRelatedNlmId()).isNull();
      // id 和 venueId 由调用方设置
      assertThat(result.getId()).isNull();
      assertThat(result.getVenueId()).isNull();
    }

    @Test
    @DisplayName("应该正确转换带 NLM ID 的关系到 DO")
    void shouldConvertRelationWithNlmIdToDO() {
      // Given
      VenueRelation relation =
          VenueRelation.create("Journal of Cardiology", VenueRelationType.SUCCEEDING, "101234567");

      // When
      VenueRelationDO result = converter.toDO(relation);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getRelatedTitle()).isEqualTo("Journal of Cardiology");
      assertThat(result.getRelationType()).isEqualTo("SUCCEEDING");
      assertThat(result.getRelatedNlmId()).isEqualTo("101234567");
    }

    @Test
    @DisplayName("应该正确转换完整的关系到 DO")
    void shouldConvertFullRelationToDO() {
      // Given
      VenueRelation relation =
          VenueRelation.create(
              "Old Journal",
              VenueRelationType.ABSORBED,
              "101234567",
              LocalDate.of(2000, 1, 1),
              "Merged in 2000");

      // When
      VenueRelationDO result = converter.toDO(relation);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getRelatedTitle()).isEqualTo("Old Journal");
      assertThat(result.getRelationType()).isEqualTo("ABSORBED");
      assertThat(result.getRelatedNlmId()).isEqualTo("101234567");
      assertThat(result.getEffectiveDate()).isEqualTo(LocalDate.of(2000, 1, 1));
      assertThat(result.getNotes()).isEqualTo("Merged in 2000");
    }

    @Test
    @DisplayName("应该正确转换所有关系类型到 DO")
    void shouldConvertAllRelationTypesToDO() {
      for (VenueRelationType type : VenueRelationType.values()) {
        // Given
        VenueRelation relation = VenueRelation.create("Test Journal", type);

        // When
        VenueRelationDO result = converter.toDO(relation);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRelationType()).isEqualTo(type.getCode());
      }
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenRelationIsNull() {
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
      VenueRelationDO doEntity = new VenueRelationDO();
      doEntity.setRelatedVenueId(12345L);
      doEntity.setRelatedNlmId("101234567");
      doEntity.setRelatedTitle("Journal of Medicine");
      doEntity.setRelationType("PRECEDING");
      doEntity.setEffectiveDate(LocalDate.of(2000, 1, 1));
      doEntity.setNotes("Historical predecessor");

      // When
      VenueRelation result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.relatedVenueId()).isEqualTo(12345L);
      assertThat(result.relatedNlmId()).isEqualTo("101234567");
      assertThat(result.relatedTitle()).isEqualTo("Journal of Medicine");
      assertThat(result.relationType()).isEqualTo(VenueRelationType.PRECEDING);
      assertThat(result.effectiveDate()).isEqualTo(LocalDate.of(2000, 1, 1));
      assertThat(result.notes()).isEqualTo("Historical predecessor");
    }

    @Test
    @DisplayName("无效的枚举代码值应该使用默认值 PRECEDING")
    void shouldUsePrecedingAsDefaultWhenCodeIsInvalid() {
      // Given
      VenueRelationDO doEntity = new VenueRelationDO();
      doEntity.setRelatedTitle("Test Journal");
      doEntity.setRelationType("INVALID_TYPE");

      // When
      VenueRelation result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.relationType()).isEqualTo(VenueRelationType.PRECEDING);
    }

    @Test
    @DisplayName("枚举代码为 null 时应该使用默认值 PRECEDING")
    void shouldUsePrecedingAsDefaultWhenCodeIsNull() {
      // Given
      VenueRelationDO doEntity = new VenueRelationDO();
      doEntity.setRelatedTitle("Test Journal");
      doEntity.setRelationType(null);

      // When
      VenueRelation result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.relationType()).isEqualTo(VenueRelationType.PRECEDING);
    }

    @Test
    @DisplayName("应该正确转换所有支持的关系类型")
    void shouldConvertAllSupportedRelationTypes() {
      for (VenueRelationType type : VenueRelationType.values()) {
        // Given
        VenueRelationDO doEntity = new VenueRelationDO();
        doEntity.setRelatedTitle("Test Journal");
        doEntity.setRelationType(type.getCode());

        // When
        VenueRelation result = converter.toEntity(doEntity);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.relationType()).isEqualTo(type);
      }
    }

    @Test
    @DisplayName("可选字段为 null 时应该正确处理")
    void shouldHandleNullOptionalFields() {
      // Given
      VenueRelationDO doEntity = new VenueRelationDO();
      doEntity.setRelatedTitle("Test Journal");
      doEntity.setRelationType("SUCCEEDING");
      doEntity.setRelatedVenueId(null);
      doEntity.setRelatedNlmId(null);
      doEntity.setEffectiveDate(null);
      doEntity.setNotes(null);

      // When
      VenueRelation result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.relatedVenueId()).isNull();
      assertThat(result.relatedNlmId()).isNull();
      assertThat(result.effectiveDate()).isNull();
      assertThat(result.notes()).isNull();
      assertThat(result.isLinkedToVenue()).isFalse();
      assertThat(result.hasNlmId()).isFalse();
      assertThat(result.hasEffectiveDate()).isFalse();
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenDOIsNull() {
      assertThat(converter.toEntity(null)).isNull();
    }
  }
}
