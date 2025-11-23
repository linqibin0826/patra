package com.patra.catalog.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.enums.MeshImportTaskStatus;
import com.patra.catalog.domain.model.enums.MeshTableImportStatus;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.model.valueobject.TableProgress;
import com.patra.catalog.infra.persistence.entity.MeshImportTaskDO;
import com.patra.catalog.infra.persistence.entity.MeshTableProgressDO;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/// MeSH 导入转换器单元测试。
///
/// 验证 Domain 对象与 DO 对象之间的转换正确性。
///
/// **测试策略**：
///
/// - 单元测试：测试 MapStruct 生成的转换器
///   - 测试覆盖：toDomain()、toTaskDO()、toProgressDOList()、toTableProgress()、toProgressDO()
///   - 边界情况：null 值处理、集合转换、嵌套对象转换
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshImportConverter 单元测试")
class MeshImportConverterTest {

  private final MeshImportConverter converter = Mappers.getMapper(MeshImportConverter.class);

  @Test
  @DisplayName("转换为领域对象 - 应该正确转换TaskDO和ProgressDOList")
  void toDomain_validDOObjects_shouldConvertToAggregate() {
    // Given: TaskDO
    MeshImportTaskDO taskDO = new MeshImportTaskDO();
    taskDO.setId(123456789L);
    taskDO.setTaskName("MeSH 2025 导入");
    taskDO.setStatus("PENDING");
    taskDO.setDescriptorSourceUrl(
        "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");
    taskDO.setXmlFileHash("a1b2c3d4e5f6");
    taskDO.setXmlFileSize(700_000_000L);
    taskDO.setTotalRecords(350000);
    taskDO.setProcessedRecords(0);
    taskDO.setFailedBatchCount(0);
    taskDO.setLastErrorMessage(null);
    taskDO.setStartTime(null);
    taskDO.setEndTime(null);

    // Given: ProgressDOList
    MeshTableProgressDO progressDO = new MeshTableProgressDO();
    progressDO.setId(987654321L);
    progressDO.setImportId(123456789L);
    progressDO.setTableName("cat_mesh_descriptor");
    progressDO.setTotalCount(35000);
    progressDO.setProcessedCount(0);
    progressDO.setFailedCount(0);
    progressDO.setStatus("NOT_STARTED");
    progressDO.setLastBatchNum(0);
    List<MeshTableProgressDO> progressDOList = List.of(progressDO);

    // When: 转换为领域对象
    MeshImportAggregate aggregate = converter.toDomain(taskDO, progressDOList);

    // Then: 应该正确转换
    assertThat(aggregate).isNotNull();
    assertThat(aggregate.getId()).isEqualTo(MeshImportId.of(123456789L));
    assertThat(aggregate.getTaskName()).isEqualTo("MeSH 2025 导入");
    assertThat(aggregate.getStatus()).isEqualTo(MeshImportTaskStatus.PENDING);
    assertThat(aggregate.getDescriptorSourceUrl())
        .isEqualTo("https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");
    assertThat(aggregate.getXmlFileHash()).isEqualTo("a1b2c3d4e5f6");
    assertThat(aggregate.getXmlFileSize()).isEqualTo(700_000_000L);
    assertThat(aggregate.getTotalRecords()).isEqualTo(350000);
    assertThat(aggregate.getProcessedRecords()).isEqualTo(0);

    // Then: 应该转换表进度列表
    assertThat(aggregate.getTableProgressList()).hasSize(1);
    TableProgress progress = aggregate.getTableProgressList().get(0);
    assertThat(progress.getTableName()).isEqualTo("cat_mesh_descriptor");
    assertThat(progress.getExpectedCount()).isEqualTo(35000);
    assertThat(progress.getProcessedCount()).isEqualTo(0);
    assertThat(progress.getStatus()).isEqualTo(MeshTableImportStatus.NOT_STARTED);
  }

  @Test
  @DisplayName("转换为TaskDO - 应该正确转换聚合根")
  void toTaskDO_validAggregate_shouldConvertToTaskDO() {
    // Given: 聚合根
    MeshImportAggregate aggregate =
        new MeshImportAggregate(
            MeshImportId.of(123456789L),
            "MeSH 2025 导入",
            MeshImportTaskStatus.PROCESSING,
            Instant.now(),
            null,
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
            "a1b2c3d4e5f6",
            700_000_000L,
            List.of(),
            350000,
            5000,
            0,
            null);

    // When: 转换为TaskDO
    MeshImportTaskDO taskDO = converter.toTaskDO(aggregate);

    // Then: 应该正确转换
    assertThat(taskDO).isNotNull();
    assertThat(taskDO.getId()).isEqualTo(123456789L);
    assertThat(taskDO.getTaskName()).isEqualTo("MeSH 2025 导入");
    assertThat(taskDO.getStatus()).isEqualTo("PROCESSING");
    assertThat(taskDO.getDescriptorSourceUrl())
        .isEqualTo("https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");
    assertThat(taskDO.getXmlFileHash()).isEqualTo("a1b2c3d4e5f6");
    assertThat(taskDO.getTotalRecords()).isEqualTo(350000);
    assertThat(taskDO.getProcessedRecords()).isEqualTo(5000);
    assertThat(taskDO.getStartTime()).isNotNull();
  }

  @Test
  @DisplayName("转换为ProgressDOList - 应该正确转换表进度列表")
  void toProgressDOList_validAggregate_shouldConvertToProgressDOList() {
    // Given: 聚合根（包含2个表进度）
    TableProgress progress1 =
        TableProgress.builder()
            .tableName("cat_mesh_descriptor")
            .expectedCount(35000)
            .actualTotalCount(null)
            .processedCount(5000)
            .failedCount(10)
            .status(MeshTableImportStatus.IN_PROGRESS)
            .lastBatchNum(5)
            .lastUpdateTime(Instant.now())
            .build();

    TableProgress progress2 =
        TableProgress.builder()
            .tableName("cat_mesh_qualifier")
            .expectedCount(500)
            .actualTotalCount(null)
            .processedCount(0)
            .failedCount(0)
            .status(MeshTableImportStatus.NOT_STARTED)
            .lastBatchNum(0)
            .lastUpdateTime(Instant.now())
            .build();

    MeshImportAggregate aggregate =
        new MeshImportAggregate(
            MeshImportId.of(123456789L),
            "MeSH 2025 导入",
            MeshImportTaskStatus.PROCESSING,
            Instant.now(),
            null,
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
            "a1b2c3d4e5f6",
            700_000_000L,
            List.of(progress1, progress2),
            350000,
            5000,
            0,
            null);

    // When: 转换为ProgressDOList
    List<MeshTableProgressDO> progressDOList = converter.toProgressDOList(aggregate);

    // Then: 应该正确转换
    assertThat(progressDOList).hasSize(2);

    MeshTableProgressDO progressDO1 = progressDOList.get(0);
    assertThat(progressDO1.getImportId()).isEqualTo(123456789L);
    assertThat(progressDO1.getTableName()).isEqualTo("cat_mesh_descriptor");
    assertThat(progressDO1.getTotalCount()).isEqualTo(35000);
    assertThat(progressDO1.getProcessedCount()).isEqualTo(5000);
    assertThat(progressDO1.getStatus()).isEqualTo("IN_PROGRESS");
    assertThat(progressDO1.getLastBatchNum()).isEqualTo(5);

    MeshTableProgressDO progressDO2 = progressDOList.get(1);
    assertThat(progressDO2.getTableName()).isEqualTo("cat_mesh_qualifier");
    assertThat(progressDO2.getStatus()).isEqualTo("NOT_STARTED");
  }

  @Test
  @DisplayName("转换为TableProgress - 应该正确转换ProgressDO")
  void toTableProgress_validProgressDO_shouldConvertToTableProgress() {
    // Given: ProgressDO
    MeshTableProgressDO progressDO = new MeshTableProgressDO();
    progressDO.setTableName("cat_mesh_descriptor");
    progressDO.setTotalCount(35000);
    progressDO.setProcessedCount(5000);
    progressDO.setFailedCount(10);
    progressDO.setStatus("IN_PROGRESS");
    progressDO.setLastBatchNum(5);

    // When: 转换为TableProgress
    TableProgress progress = converter.toTableProgress(progressDO);

    // Then: 应该正确转换
    assertThat(progress).isNotNull();
    assertThat(progress.getTableName()).isEqualTo("cat_mesh_descriptor");
    assertThat(progress.getExpectedCount()).isEqualTo(35000);
    assertThat(progress.getProcessedCount()).isEqualTo(5000);
    assertThat(progress.getFailedCount()).isEqualTo(10);
    assertThat(progress.getStatus()).isEqualTo(MeshTableImportStatus.IN_PROGRESS);
    assertThat(progress.getLastBatchNum()).isEqualTo(5);
  }

  @Test
  @DisplayName("转换为ProgressDO - 应该正确转换TableProgress")
  void toProgressDO_validTableProgress_shouldConvertToProgressDO() {
    // Given: TableProgress
    TableProgress progress =
        TableProgress.builder()
            .tableName("cat_mesh_descriptor")
            .expectedCount(35000)
            .actualTotalCount(null)
            .processedCount(5000)
            .failedCount(10)
            .status(MeshTableImportStatus.IN_PROGRESS)
            .lastBatchNum(5)
            .lastUpdateTime(Instant.now())
            .build();

    // When: 转换为ProgressDO
    MeshTableProgressDO progressDO = converter.toProgressDO(progress);

    // Then: 应该正确转换
    assertThat(progressDO).isNotNull();
    assertThat(progressDO.getTableName()).isEqualTo("cat_mesh_descriptor");
    assertThat(progressDO.getTotalCount()).isEqualTo(35000);
    assertThat(progressDO.getProcessedCount()).isEqualTo(5000);
    assertThat(progressDO.getFailedCount()).isEqualTo(10);
    assertThat(progressDO.getStatus()).isEqualTo("IN_PROGRESS");
    assertThat(progressDO.getLastBatchNum()).isEqualTo(5);
  }

  @Test
  @DisplayName("处理null值 - 应该正确处理null字段")
  void toDomain_withNullFields_shouldHandleGracefully() {
    // Given: TaskDO with null fields
    MeshImportTaskDO taskDO = new MeshImportTaskDO();
    taskDO.setId(123456789L);
    taskDO.setTaskName("MeSH 2025 导入");
    taskDO.setStatus("PENDING");
    taskDO.setDescriptorSourceUrl(
        "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");
    taskDO.setXmlFileHash(null); // null field
    taskDO.setXmlFileSize(null); // null field
    taskDO.setTotalRecords(350000);
    taskDO.setProcessedRecords(0);
    taskDO.setFailedBatchCount(0);
    taskDO.setLastErrorMessage(null);
    taskDO.setStartTime(null);
    taskDO.setEndTime(null);

    // When: 转换为领域对象
    MeshImportAggregate aggregate = converter.toDomain(taskDO, List.of());

    // Then: 应该正确处理null值
    assertThat(aggregate).isNotNull();
    assertThat(aggregate.getXmlFileHash()).isNull();
    assertThat(aggregate.getXmlFileSize()).isNull();
  }

  @Test
  @DisplayName("转换空集合 - 应该返回空列表")
  void toProgressDOList_emptyProgressList_shouldReturnEmptyList() {
    // Given: 空表进度列表
    MeshImportAggregate aggregate =
        new MeshImportAggregate(
            MeshImportId.of(123456789L),
            "MeSH 2025 导入",
            MeshImportTaskStatus.PENDING,
            null,
            null,
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
            "a1b2c3d4e5f6",
            700_000_000L,
            List.of(), // 空列表
            350000,
            0,
            0,
            null);

    // When: 转换为ProgressDOList
    List<MeshTableProgressDO> progressDOList = converter.toProgressDOList(aggregate);

    // Then: 应该返回空列表
    assertThat(progressDOList).isEmpty();
  }
}
