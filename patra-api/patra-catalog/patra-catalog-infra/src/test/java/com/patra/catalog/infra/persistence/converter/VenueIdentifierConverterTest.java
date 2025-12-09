package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueIdentifierConverter 单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 测试 toDO() 和 toEntity() 转换方法
/// - 测试枚举类型转换
/// - 测试 null 输入处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueIdentifierConverter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueIdentifierConverterTest {

  private VenueIdentifierConverterImpl converter;

  @BeforeEach
  void setUp() {
    converter = new VenueIdentifierConverterImpl();
  }

  @Nested
  @DisplayName("toDO() 方法测试")
  class ToDOTests {

    @Test
    @DisplayName("应该正确转换 OpenAlex ID 标识符到 DO")
    void shouldConvertOpenAlexIdentifierToDO() {
      // Given
      VenueIdentifier identifier = VenueIdentifier.forOpenAlex("S1234567890");

      // When
      VenueIdentifierDO result = converter.toDO(identifier);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getIdentifierType()).isEqualTo("OPENALEX");
      assertThat(result.getIdentifierValue()).isEqualTo("S1234567890");
      assertThat(result.getIsPrimary()).isFalse();
      // id 和 venueId 由调用方设置，这里应为 null
      assertThat(result.getId()).isNull();
      assertThat(result.getVenueId()).isNull();
    }

    @Test
    @DisplayName("应该正确转换 ISSN 标识符到 DO")
    void shouldConvertIssnIdentifierToDO() {
      // Given
      VenueIdentifier identifier = VenueIdentifier.forIssn("1234-5678");

      // When
      VenueIdentifierDO result = converter.toDO(identifier);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getIdentifierType()).isEqualTo("ISSN");
      assertThat(result.getIdentifierValue()).isEqualTo("1234-5678");
    }

    @Test
    @DisplayName("应该正确转换 NLM ID 标识符到 DO")
    void shouldConvertNlmIdentifierToDO() {
      // Given
      VenueIdentifier identifier = VenueIdentifier.forNlm("101234567");

      // When
      VenueIdentifierDO result = converter.toDO(identifier);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getIdentifierType()).isEqualTo("NLM");
      assertThat(result.getIdentifierValue()).isEqualTo("101234567");
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenIdentifierIsNull() {
      assertThat(converter.toDO(null)).isNull();
    }
  }

  @Nested
  @DisplayName("toEntity() 方法测试")
  class ToEntityTests {

    @Test
    @DisplayName("应该正确转换 DO 到 OpenAlex ID 领域实体")
    void shouldConvertDOToOpenAlexIdentifier() {
      // Given
      VenueIdentifierDO doEntity = new VenueIdentifierDO();
      doEntity.setIdentifierType("OPENALEX");
      doEntity.setIdentifierValue("S1234567890");

      // When
      VenueIdentifier result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.type()).isEqualTo(VenueIdentifierType.OPENALEX);
      assertThat(result.value()).isEqualTo("S1234567890");
    }

    @Test
    @DisplayName("应该正确转换 DO 到 ISSN 领域实体")
    void shouldConvertDOToIssnIdentifier() {
      // Given
      VenueIdentifierDO doEntity = new VenueIdentifierDO();
      doEntity.setIdentifierType("ISSN");
      doEntity.setIdentifierValue("1234-5678");

      // When
      VenueIdentifier result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.type()).isEqualTo(VenueIdentifierType.ISSN);
      assertThat(result.value()).isEqualTo("1234-5678");
    }

    @Test
    @DisplayName("应该正确转换 DO 到 Linking ISSN 领域实体")
    void shouldConvertDOToIssnLIdentifier() {
      // Given
      VenueIdentifierDO doEntity = new VenueIdentifierDO();
      doEntity.setIdentifierType("ISSN_L");
      doEntity.setIdentifierValue("1234-567X");

      // When
      VenueIdentifier result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.type()).isEqualTo(VenueIdentifierType.ISSN_L);
      assertThat(result.value()).isEqualTo("1234-567X");
    }

    @Test
    @DisplayName("应该正确转换所有支持的标识符类型")
    void shouldConvertAllSupportedIdentifierTypes() {
      for (VenueIdentifierType type : VenueIdentifierType.values()) {
        // Given - 根据标识符类型使用合适的测试值（ISSN 和 ISSN_L 需要符合 XXXX-XXXX 格式）
        String testValue = getTestValueForType(type);
        VenueIdentifierDO doEntity = new VenueIdentifierDO();
        doEntity.setIdentifierType(type.name());
        doEntity.setIdentifierValue(testValue);

        // When
        VenueIdentifier result = converter.toEntity(doEntity);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo(type);
      }
    }

    /// 根据标识符类型返回合适的测试值。
    ///
    /// ISSN 和 ISSN_L 类型需要符合 "XXXX-XXXX" 格式。
    private String getTestValueForType(VenueIdentifierType type) {
      return switch (type) {
        case ISSN, ISSN_L -> "1234-5678";
        default -> "test-value";
      };
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenDOIsNull() {
      assertThat(converter.toEntity(null)).isNull();
    }
  }
}
