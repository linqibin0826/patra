package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.vo.venue.VenueMesh;
import com.patra.catalog.infra.persistence.entity.VenueMeshDO;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueMeshConverter 单元测试。
///
/// **测试策略**：
///
/// - 纯单元测试，无 Mock
/// - 测试 toDO() 和 toEntity() 转换方法
/// - 测试 Boolean 包装类型处理
/// - 测试可选字段（Qualifier）处理
/// - 测试 null 输入处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueMeshConverter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueMeshConverterTest {

  private VenueMeshConverterImpl converter;

  @BeforeEach
  void setUp() {
    converter = new VenueMeshConverterImpl();
  }

  @Nested
  @DisplayName("toDO() 方法测试")
  class ToDOTests {

    @Test
    @DisplayName("应该正确转换主要主题到 DO")
    void shouldConvertMajorTopicToDO() {
      // Given
      VenueMesh mesh = VenueMesh.major("Medicine", "D008511");

      // When
      VenueMeshDO result = converter.toDO(mesh);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getDescriptorName()).isEqualTo("Medicine");
      assertThat(result.getDescriptorUi()).isEqualTo("D008511");
      assertThat(result.getIsMajorTopic()).isTrue();
      assertThat(result.getQualifierName()).isNull();
      assertThat(result.getQualifierUi()).isNull();
      // id 和 venueId 由调用方设置
      assertThat(result.getId()).isNull();
      assertThat(result.getVenueId()).isNull();
    }

    @Test
    @DisplayName("应该正确转换次要主题到 DO")
    void shouldConvertMinorTopicToDO() {
      // Given
      VenueMesh mesh = VenueMesh.minor("Cardiology", "D002309");

      // When
      VenueMeshDO result = converter.toDO(mesh);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getDescriptorName()).isEqualTo("Cardiology");
      assertThat(result.getDescriptorUi()).isEqualTo("D002309");
      assertThat(result.getIsMajorTopic()).isFalse();
    }

    @Test
    @DisplayName("应该正确转换带限定符的主题到 DO")
    void shouldConvertTopicWithQualifierToDO() {
      // Given
      VenueMesh mesh = VenueMesh.create("Cardiology", "D002309", true, "methods", "Q000379");

      // When
      VenueMeshDO result = converter.toDO(mesh);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getDescriptorName()).isEqualTo("Cardiology");
      assertThat(result.getIsMajorTopic()).isTrue();
      assertThat(result.getQualifierName()).isEqualTo("methods");
      assertThat(result.getQualifierUi()).isEqualTo("Q000379");
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenMeshIsNull() {
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
      VenueMeshDO doEntity = new VenueMeshDO();
      doEntity.setDescriptorName("Medicine");
      doEntity.setDescriptorUi("D008511");
      doEntity.setIsMajorTopic(true);
      doEntity.setQualifierName("methods");
      doEntity.setQualifierUi("Q000379");

      // When
      VenueMesh result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.descriptorName()).isEqualTo("Medicine");
      assertThat(result.descriptorUi()).isEqualTo("D008511");
      assertThat(result.isMajorTopic()).isTrue();
      assertThat(result.qualifierName()).isEqualTo("methods");
      assertThat(result.qualifierUi()).isEqualTo("Q000379");
    }

    @Test
    @DisplayName("Boolean 包装类型为 null 时应该处理为 false")
    void shouldTreatNullBooleanAsFalse() {
      // Given
      VenueMeshDO doEntity = new VenueMeshDO();
      doEntity.setDescriptorName("Test");
      doEntity.setIsMajorTopic(null);

      // When
      VenueMesh result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isMajorTopic()).isFalse();
    }

    @Test
    @DisplayName("Boolean 包装类型为 false 时应该正确处理")
    void shouldHandleFalseBoolean() {
      // Given
      VenueMeshDO doEntity = new VenueMeshDO();
      doEntity.setDescriptorName("Test");
      doEntity.setIsMajorTopic(false);

      // When
      VenueMesh result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.isMajorTopic()).isFalse();
    }

    @Test
    @DisplayName("无限定符时应该正确处理")
    void shouldHandleNoQualifier() {
      // Given
      VenueMeshDO doEntity = new VenueMeshDO();
      doEntity.setDescriptorName("Medicine");
      doEntity.setDescriptorUi("D008511");
      doEntity.setIsMajorTopic(true);
      doEntity.setQualifierName(null);
      doEntity.setQualifierUi(null);

      // When
      VenueMesh result = converter.toEntity(doEntity);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.qualifierName()).isNull();
      assertThat(result.qualifierUi()).isNull();
      assertThat(result.hasQualifier()).isFalse();
    }

    @Test
    @DisplayName("参数为 null 时应该返回 null")
    void shouldReturnNullWhenDOIsNull() {
      assertThat(converter.toEntity(null)).isNull();
    }
  }
}
