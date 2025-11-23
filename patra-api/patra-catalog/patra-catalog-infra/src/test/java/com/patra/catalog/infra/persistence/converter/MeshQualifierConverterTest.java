package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.persistence.entity.MeshQualifierDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// MeSH 限定词转换器单元测试。
///
/// 验证 MeshQualifierAggregate 与 MeshQualifierDO 之间的双向转换正确性。
///
/// **测试策略**：
///
/// - 单元测试：测试转换器的转换逻辑
///   - 测试覆盖：toDataObject()、toDomain()
///   - 边界情况：null 值处理、值对象转换
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshQualifierConverter 单元测试")
class MeshQualifierConverterTest {

  private final MeshQualifierConverter converter = new MeshQualifierConverter();

  @Test
  @DisplayName("转换为数据库实体 - 应该正确转换聚合根所有字段")
  void toDataObject_validAggregate_shouldConvertAllFields() {
    // Given: 创建完整的限定词聚合根
    MeshQualifierAggregate aggregate =
        MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM")
            .withAnnotation("Used with organs, animals, and diseases for immunologic")
            .withDateCreated("19990101")
            .withDateRevised("20250101")
            .withDateEstablished("19990101")
            .withActiveStatus(true)
            .withMeshVersion("2025");

    // When: 转换为数据库实体
    MeshQualifierDO dataObject = converter.toDataObject(aggregate);

    // Then: 应该正确转换所有字段
    assertThat(dataObject).isNotNull();
    assertThat(dataObject.getId()).isNull(); // 新创建的对象，ID应该为null
    assertThat(dataObject.getUi()).isEqualTo("Q000001");
    assertThat(dataObject.getName()).isEqualTo("immunology");
    assertThat(dataObject.getAbbreviation()).isEqualTo("IM");
    assertThat(dataObject.getAnnotation())
        .isEqualTo("Used with organs, animals, and diseases for immunologic");
    assertThat(dataObject.getDateCreated()).isEqualTo("19990101");
    assertThat(dataObject.getDateRevised()).isEqualTo("20250101");
    assertThat(dataObject.getDateEstablished()).isEqualTo("19990101");
    assertThat(dataObject.getActiveStatus()).isTrue();
    assertThat(dataObject.getMeshVersion()).isEqualTo("2025");
  }

  @Test
  @DisplayName("转换为数据库实体 - 应该正确处理已持久化的聚合根")
  void toDataObject_withPersistedAggregate_shouldIncludeId() {
    // Given: 从数据库恢复的聚合根（带ID）
    MeshQualifierAggregate aggregate =
        MeshQualifierAggregate.restore(
            12345678L, // 已存在的ID
            MeshUI.qualifierOf(1),
            "immunology",
            "IM",
            "Test annotation",
            "19990101",
            "20250101",
            "19990101",
            true,
            "2025");

    // When: 转换为数据库实体
    MeshQualifierDO dataObject = converter.toDataObject(aggregate);

    // Then: 应该包含ID
    assertThat(dataObject).isNotNull();
    assertThat(dataObject.getId()).isEqualTo(12345678L);
    assertThat(dataObject.getUi()).isEqualTo("Q000001");
  }

  @Test
  @DisplayName("转换为数据库实体 - null聚合根应该返回null")
  void toDataObject_nullAggregate_shouldReturnNull() {
    // When: 转换null聚合根
    MeshQualifierDO dataObject = converter.toDataObject(null);

    // Then: 应该返回null
    assertThat(dataObject).isNull();
  }

  @Test
  @DisplayName("转换为领域对象 - 应该正确转换数据库实体所有字段")
  void toDomain_validDataObject_shouldConvertAllFields() {
    // Given: 创建完整的数据库实体
    MeshQualifierDO dataObject = new MeshQualifierDO();
    dataObject.setId(12345678L);
    dataObject.setUi("Q000001");
    dataObject.setName("immunology");
    dataObject.setAbbreviation("IM");
    dataObject.setAnnotation("Used with organs, animals, and diseases for immunologic");
    dataObject.setDateCreated("19990101");
    dataObject.setDateRevised("20250101");
    dataObject.setDateEstablished("19990101");
    dataObject.setActiveStatus(true);
    dataObject.setMeshVersion("2025");

    // When: 转换为领域对象
    MeshQualifierAggregate aggregate = converter.toDomain(dataObject);

    // Then: 应该正确转换所有字段
    assertThat(aggregate).isNotNull();
    assertThat(aggregate.getId()).isEqualTo(12345678L);
    assertThat(aggregate.getQualifierUi()).isEqualTo(MeshUI.qualifierOf(1));
    assertThat(aggregate.getQualifierUi().ui()).isEqualTo("Q000001");
    assertThat(aggregate.getName()).isEqualTo("immunology");
    assertThat(aggregate.getAbbreviation()).isEqualTo("IM");
    assertThat(aggregate.getAnnotation())
        .isEqualTo("Used with organs, animals, and diseases for immunologic");
    assertThat(aggregate.getDateCreated()).isEqualTo("19990101");
    assertThat(aggregate.getDateRevised()).isEqualTo("20250101");
    assertThat(aggregate.getDateEstablished()).isEqualTo("19990101");
    assertThat(aggregate.getActiveStatus()).isTrue();
    assertThat(aggregate.getMeshVersion()).isEqualTo("2025");
    assertThat(aggregate.isActive()).isTrue();
  }

  @Test
  @DisplayName("转换为领域对象 - 应该正确解析不同的限定词UI")
  void toDomain_differentQualifierUIs_shouldParseCorrectly() {
    // Given: Q000001
    MeshQualifierDO dataObject1 = new MeshQualifierDO();
    dataObject1.setId(1L);
    dataObject1.setUi("Q000001");
    dataObject1.setName("immunology");
    dataObject1.setAbbreviation("IM");

    // Given: Q000099
    MeshQualifierDO dataObject2 = new MeshQualifierDO();
    dataObject2.setId(2L);
    dataObject2.setUi("Q000099");
    dataObject2.setName("genetics");
    dataObject2.setAbbreviation("GE");

    // When: 转换为领域对象
    MeshQualifierAggregate aggregate1 = converter.toDomain(dataObject1);
    MeshQualifierAggregate aggregate2 = converter.toDomain(dataObject2);

    // Then: 应该正确解析不同的UI
    assertThat(aggregate1.getQualifierUi()).isEqualTo(MeshUI.qualifierOf(1));
    assertThat(aggregate1.getQualifierUi().ui()).isEqualTo("Q000001");

    assertThat(aggregate2.getQualifierUi()).isEqualTo(MeshUI.qualifierOf(99));
    assertThat(aggregate2.getQualifierUi().ui()).isEqualTo("Q000099");
  }

  @Test
  @DisplayName("转换为领域对象 - null数据库实体应该返回null")
  void toDomain_nullDataObject_shouldReturnNull() {
    // When: 转换null数据库实体
    MeshQualifierAggregate aggregate = converter.toDomain(null);

    // Then: 应该返回null
    assertThat(aggregate).isNull();
  }

  @Test
  @DisplayName("双向转换 - 应该保持数据一致性")
  void bidirectionalConversion_shouldMaintainDataConsistency() {
    // Given: 原始数据库实体
    MeshQualifierDO originalDO = new MeshQualifierDO();
    originalDO.setId(12345678L);
    originalDO.setUi("Q000001");
    originalDO.setName("immunology");
    originalDO.setAbbreviation("IM");
    originalDO.setAnnotation("Test annotation");
    originalDO.setDateCreated("19990101");
    originalDO.setDateRevised("20250101");
    originalDO.setDateEstablished("19990101");
    originalDO.setActiveStatus(true);
    originalDO.setMeshVersion("2025");

    // When: DO -> Domain -> DO
    MeshQualifierAggregate aggregate = converter.toDomain(originalDO);
    MeshQualifierDO convertedDO = converter.toDataObject(aggregate);

    // Then: 应该保持数据一致性
    assertThat(convertedDO.getId()).isEqualTo(originalDO.getId());
    assertThat(convertedDO.getUi()).isEqualTo(originalDO.getUi());
    assertThat(convertedDO.getName()).isEqualTo(originalDO.getName());
    assertThat(convertedDO.getAbbreviation()).isEqualTo(originalDO.getAbbreviation());
    assertThat(convertedDO.getAnnotation()).isEqualTo(originalDO.getAnnotation());
    assertThat(convertedDO.getDateCreated()).isEqualTo(originalDO.getDateCreated());
    assertThat(convertedDO.getDateRevised()).isEqualTo(originalDO.getDateRevised());
    assertThat(convertedDO.getDateEstablished()).isEqualTo(originalDO.getDateEstablished());
    assertThat(convertedDO.getActiveStatus()).isEqualTo(originalDO.getActiveStatus());
    assertThat(convertedDO.getMeshVersion()).isEqualTo(originalDO.getMeshVersion());
  }

  @Test
  @DisplayName("转换为数据库实体 - 应该正确处理可选字段为null的情况")
  void toDataObject_withNullOptionalFields_shouldHandleGracefully() {
    // Given: 只设置必填字段的聚合根
    MeshQualifierAggregate aggregate =
        MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM");
    // 不设置可选字段（annotation、dates、activeStatus、meshVersion）

    // When: 转换为数据库实体
    MeshQualifierDO dataObject = converter.toDataObject(aggregate);

    // Then: 应该正确处理null值
    assertThat(dataObject).isNotNull();
    assertThat(dataObject.getUi()).isEqualTo("Q000001");
    assertThat(dataObject.getName()).isEqualTo("immunology");
    assertThat(dataObject.getAbbreviation()).isEqualTo("IM");
    assertThat(dataObject.getAnnotation()).isNull();
    assertThat(dataObject.getDateCreated()).isNull();
    assertThat(dataObject.getDateRevised()).isNull();
    assertThat(dataObject.getDateEstablished()).isNull();
    assertThat(dataObject.getActiveStatus()).isNull();
    assertThat(dataObject.getMeshVersion()).isNull();
  }

  @Test
  @DisplayName("转换为领域对象 - 应该正确处理activeStatus为false的情况")
  void toDomain_withInactiveStatus_shouldConvertCorrectly() {
    // Given: activeStatus为false的数据库实体（已废弃的限定词）
    MeshQualifierDO dataObject = new MeshQualifierDO();
    dataObject.setId(12345678L);
    dataObject.setUi("Q000001");
    dataObject.setName("old qualifier");
    dataObject.setAbbreviation("OQ");
    dataObject.setActiveStatus(false);
    dataObject.setMeshVersion("2024");

    // When: 转换为领域对象
    MeshQualifierAggregate aggregate = converter.toDomain(dataObject);

    // Then: 应该正确转换状态
    assertThat(aggregate).isNotNull();
    assertThat(aggregate.getActiveStatus()).isFalse();
    assertThat(aggregate.isActive()).isFalse();
    assertThat(aggregate.isDeprecated()).isTrue();
  }
}
