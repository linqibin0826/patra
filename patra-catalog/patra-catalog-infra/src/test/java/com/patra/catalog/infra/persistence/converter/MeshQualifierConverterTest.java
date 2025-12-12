package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.vo.mesh.MeshQualifierId;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.persistence.entity.MeshQualifierDO;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class MeshQualifierConverterTest {

  private final MeshQualifierConverter converter = new MeshQualifierConverter();

  @Test
  @DisplayName("转换为数据库实体 - 应该正确转换聚合根所有字段")
  void toDataObject_validAggregate_shouldConvertAllFields() {
    // Given: 创建完整的限定词聚合根
    MeshQualifierAggregate aggregate =
        MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM")
            .withAnnotation("Used with organs, animals, and diseases for immunologic")
            .withDateCreated(LocalDate.of(1999, 1, 1))
            .withDateRevised(LocalDate.of(2025, 1, 1))
            .withDateEstablished(LocalDate.of(1999, 1, 1))
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
    assertThat(dataObject.getDateCreated()).isEqualTo(LocalDate.of(1999, 1, 1));
    assertThat(dataObject.getDateRevised()).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(dataObject.getDateEstablished()).isEqualTo(LocalDate.of(1999, 1, 1));
    assertThat(dataObject.getActiveStatus()).isTrue();
    assertThat(dataObject.getMeshVersion()).isEqualTo("2025");
  }

  @Test
  @DisplayName("转换为数据库实体 - 应该正确处理已持久化的聚合根")
  void toDataObject_withPersistedAggregate_shouldIncludeId() {
    // Given: 从数据库恢复的聚合根（带ID）
    MeshQualifierAggregate aggregate =
        MeshQualifierAggregate.restore(
            MeshQualifierId.of(12345678L), // 已存在的ID
            MeshUI.qualifierOf(1),
            "immunology",
            "IM",
            "Test annotation",
            LocalDate.of(1999, 1, 1),
            LocalDate.of(2025, 1, 1),
            LocalDate.of(1999, 1, 1),
            true,
            "2025",
            null, // historyNote
            null, // onlineNote
            null); // treeNumbers

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
    dataObject.setDateCreated(LocalDate.of(1999, 1, 1));
    dataObject.setDateRevised(LocalDate.of(2025, 1, 1));
    dataObject.setDateEstablished(LocalDate.of(1999, 1, 1));
    dataObject.setActiveStatus(true);
    dataObject.setMeshVersion("2025");

    // When: 转换为领域对象
    MeshQualifierAggregate aggregate = converter.toDomain(dataObject);

    // Then: 应该正确转换所有字段
    assertThat(aggregate).isNotNull();
    assertThat(aggregate.getId().value()).isEqualTo(12345678L);
    assertThat(aggregate.getQualifierUi()).isEqualTo(MeshUI.qualifierOf(1));
    assertThat(aggregate.getQualifierUi().ui()).isEqualTo("Q000001");
    assertThat(aggregate.getName()).isEqualTo("immunology");
    assertThat(aggregate.getAbbreviation()).isEqualTo("IM");
    assertThat(aggregate.getAnnotation())
        .isEqualTo("Used with organs, animals, and diseases for immunologic");
    assertThat(aggregate.getDateCreated()).isEqualTo(LocalDate.of(1999, 1, 1));
    assertThat(aggregate.getDateRevised()).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(aggregate.getDateEstablished()).isEqualTo(LocalDate.of(1999, 1, 1));
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
    originalDO.setDateCreated(LocalDate.of(1999, 1, 1));
    originalDO.setDateRevised(LocalDate.of(2025, 1, 1));
    originalDO.setDateEstablished(LocalDate.of(1999, 1, 1));
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
    // 不设置可选字段（annotation、dates、meshVersion）
    // 注意：activeStatus 由 create() 工厂方法设置默认值 true（业务规则：新创建的限定词默认有效）

    // When: 转换为数据库实体
    MeshQualifierDO dataObject = converter.toDataObject(aggregate);

    // Then: 应该正确处理null值，activeStatus 应为默认值 true
    assertThat(dataObject).isNotNull();
    assertThat(dataObject.getUi()).isEqualTo("Q000001");
    assertThat(dataObject.getName()).isEqualTo("immunology");
    assertThat(dataObject.getAbbreviation()).isEqualTo("IM");
    assertThat(dataObject.getAnnotation()).isNull();
    assertThat(dataObject.getDateCreated()).isNull();
    assertThat(dataObject.getDateRevised()).isNull();
    assertThat(dataObject.getDateEstablished()).isNull();
    assertThat(dataObject.getActiveStatus()).isTrue(); // create() 默认设置为 true
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

  @Test
  @DisplayName("转换为领域对象 - 10 位 UI 格式（如 Q000000981）应该保持原样")
  void toDomain_tenDigitQualifierUI_shouldPreserveFormat() {
    // Given: 10 位 UI 格式的数据库实体（MeSH 2025 新格式）
    MeshQualifierDO dataObject = new MeshQualifierDO();
    dataObject.setId(981L);
    dataObject.setUi("Q000000981"); // 10 位格式
    dataObject.setName("diagnostic imaging");
    dataObject.setAbbreviation("DG");
    dataObject.setActiveStatus(true);
    dataObject.setMeshVersion("2025");

    // When: 转换为领域对象
    MeshQualifierAggregate aggregate = converter.toDomain(dataObject);

    // Then: 应该保持 10 位格式（修复前会被错误转换为 Q000981）
    assertThat(aggregate).isNotNull();
    assertThat(aggregate.getQualifierUi().ui()).isEqualTo("Q000000981");
    assertThat(aggregate.getName()).isEqualTo("diagnostic imaging");
    assertThat(aggregate.getAbbreviation()).isEqualTo("DG");
  }

  @Test
  @DisplayName("双向转换 - 10 位 UI 格式应该保持数据一致性")
  void bidirectionalConversion_tenDigitUI_shouldMaintainConsistency() {
    // Given: 10 位 UI 格式的数据库实体
    MeshQualifierDO originalDO = new MeshQualifierDO();
    originalDO.setId(981L);
    originalDO.setUi("Q000000981");
    originalDO.setName("diagnostic imaging");
    originalDO.setAbbreviation("DG");
    originalDO.setActiveStatus(true);
    originalDO.setMeshVersion("2025");

    // When: DO -> Domain -> DO
    MeshQualifierAggregate aggregate = converter.toDomain(originalDO);
    MeshQualifierDO convertedDO = converter.toDataObject(aggregate);

    // Then: UI 格式应该保持一致（10 位）
    assertThat(convertedDO.getUi()).isEqualTo("Q000000981");
    assertThat(convertedDO.getUi()).isEqualTo(originalDO.getUi());
  }

  // ========== 新增字段测试（HistoryNote, OnlineNote, TreeNumbers） ==========

  @Test
  @DisplayName("转换为数据库实体 - 应该正确转换 HistoryNote 和 OnlineNote")
  void toDataObject_withHistoryNoteAndOnlineNote_shouldConvertCorrectly() {
    // Given: 创建包含历史说明和在线说明的聚合根
    MeshQualifierAggregate aggregate =
        MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM")
            .withHistoryNote("66; used with Category A 1966-74")
            .withOnlineNote("search policy: Online Manual; use: main heading/IM or IM (SH)")
            .withActiveStatus(true)
            .withMeshVersion("2025");

    // When: 转换为数据库实体
    MeshQualifierDO dataObject = converter.toDataObject(aggregate);

    // Then: 应该正确转换新字段
    assertThat(dataObject).isNotNull();
    assertThat(dataObject.getHistoryNote()).isEqualTo("66; used with Category A 1966-74");
    assertThat(dataObject.getOnlineNote())
        .isEqualTo("search policy: Online Manual; use: main heading/IM or IM (SH)");
  }

  @Test
  @DisplayName("转换为数据库实体 - 应该正确转换 TreeNumbers 为 JSON")
  void toDataObject_withTreeNumbers_shouldConvertToJson() {
    // Given: 创建包含树形编号的聚合根
    List<String> treeNumbers = List.of("Y01.060", "Y01.060.010");
    MeshQualifierAggregate aggregate =
        MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM")
            .withTreeNumbers(treeNumbers)
            .withActiveStatus(true)
            .withMeshVersion("2025");

    // When: 转换为数据库实体
    MeshQualifierDO dataObject = converter.toDataObject(aggregate);

    // Then: 应该正确转换为 JSON 字符串
    assertThat(dataObject).isNotNull();
    assertThat(dataObject.getTreeNumbers()).isEqualTo("[\"Y01.060\",\"Y01.060.010\"]");
  }

  @Test
  @DisplayName("转换为数据库实体 - 空 TreeNumbers 应该转换为 null")
  void toDataObject_withEmptyTreeNumbers_shouldConvertToNull() {
    // Given: 创建没有树形编号的聚合根
    MeshQualifierAggregate aggregate =
        MeshQualifierAggregate.create(MeshUI.qualifierOf(1), "immunology", "IM")
            .withTreeNumbers(List.of())
            .withActiveStatus(true);

    // When: 转换为数据库实体
    MeshQualifierDO dataObject = converter.toDataObject(aggregate);

    // Then: 空列表应该转换为 null
    assertThat(dataObject).isNotNull();
    assertThat(dataObject.getTreeNumbers()).isNull();
  }

  @Test
  @DisplayName("转换为领域对象 - 应该正确转换 HistoryNote 和 OnlineNote")
  void toDomain_withHistoryNoteAndOnlineNote_shouldConvertCorrectly() {
    // Given: 创建包含历史说明和在线说明的数据库实体
    MeshQualifierDO dataObject = new MeshQualifierDO();
    dataObject.setId(12345678L);
    dataObject.setUi("Q000001");
    dataObject.setName("immunology");
    dataObject.setAbbreviation("IM");
    dataObject.setHistoryNote("66; used with Category A 1966-74");
    dataObject.setOnlineNote("search policy: Online Manual");
    dataObject.setActiveStatus(true);
    dataObject.setMeshVersion("2025");

    // When: 转换为领域对象
    MeshQualifierAggregate aggregate = converter.toDomain(dataObject);

    // Then: 应该正确转换新字段
    assertThat(aggregate).isNotNull();
    assertThat(aggregate.getHistoryNote()).isEqualTo("66; used with Category A 1966-74");
    assertThat(aggregate.getOnlineNote()).isEqualTo("search policy: Online Manual");
  }

  @Test
  @DisplayName("转换为领域对象 - 应该正确解析 TreeNumbers JSON")
  void toDomain_withTreeNumbersJson_shouldParseCorrectly() {
    // Given: 创建包含 JSON 树形编号的数据库实体
    MeshQualifierDO dataObject = new MeshQualifierDO();
    dataObject.setId(12345678L);
    dataObject.setUi("Q000001");
    dataObject.setName("immunology");
    dataObject.setAbbreviation("IM");
    dataObject.setTreeNumbers("[\"Y01.060\",\"Y01.060.010\"]");
    dataObject.setActiveStatus(true);
    dataObject.setMeshVersion("2025");

    // When: 转换为领域对象
    MeshQualifierAggregate aggregate = converter.toDomain(dataObject);

    // Then: 应该正确解析 JSON 为列表
    assertThat(aggregate).isNotNull();
    assertThat(aggregate.getTreeNumbers()).containsExactly("Y01.060", "Y01.060.010");
  }

  @Test
  @DisplayName("转换为领域对象 - null TreeNumbers 应该转换为空列表")
  void toDomain_withNullTreeNumbers_shouldConvertToEmptyList() {
    // Given: 创建没有树形编号的数据库实体
    MeshQualifierDO dataObject = new MeshQualifierDO();
    dataObject.setId(12345678L);
    dataObject.setUi("Q000001");
    dataObject.setName("immunology");
    dataObject.setAbbreviation("IM");
    dataObject.setTreeNumbers(null);
    dataObject.setActiveStatus(true);

    // When: 转换为领域对象
    MeshQualifierAggregate aggregate = converter.toDomain(dataObject);

    // Then: null 应该转换为空列表
    assertThat(aggregate).isNotNull();
    assertThat(aggregate.getTreeNumbers()).isEmpty();
  }

  @Test
  @DisplayName("双向转换 - 新字段应该保持数据一致性")
  void bidirectionalConversion_newFields_shouldMaintainConsistency() {
    // Given: 包含所有新字段的数据库实体
    MeshQualifierDO originalDO = new MeshQualifierDO();
    originalDO.setId(12345678L);
    originalDO.setUi("Q000001");
    originalDO.setName("immunology");
    originalDO.setAbbreviation("IM");
    originalDO.setHistoryNote("66; used with Category A 1966-74");
    originalDO.setOnlineNote("search policy: Online Manual");
    originalDO.setTreeNumbers("[\"Y01.060\",\"Y01.060.010\"]");
    originalDO.setActiveStatus(true);
    originalDO.setMeshVersion("2025");

    // When: DO -> Domain -> DO
    MeshQualifierAggregate aggregate = converter.toDomain(originalDO);
    MeshQualifierDO convertedDO = converter.toDataObject(aggregate);

    // Then: 新字段应该保持数据一致性
    assertThat(convertedDO.getHistoryNote()).isEqualTo(originalDO.getHistoryNote());
    assertThat(convertedDO.getOnlineNote()).isEqualTo(originalDO.getOnlineNote());
    assertThat(convertedDO.getTreeNumbers()).isEqualTo(originalDO.getTreeNumbers());
  }

  @Test
  @DisplayName("转换为领域对象 - 无效 JSON 格式应该抛出异常")
  void toDomain_withInvalidJsonTreeNumbers_shouldThrowException() {
    // Given: 包含无效 JSON 的数据库实体
    MeshQualifierDO dataObject = new MeshQualifierDO();
    dataObject.setId(1L);
    dataObject.setUi("Q000001");
    dataObject.setName("test");
    dataObject.setAbbreviation("TT");
    dataObject.setTreeNumbers("invalid json");

    // When & Then: 应该抛出 IllegalStateException
    assertThatThrownBy(() -> converter.toDomain(dataObject))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to parse tree numbers from JSON");
  }
}
