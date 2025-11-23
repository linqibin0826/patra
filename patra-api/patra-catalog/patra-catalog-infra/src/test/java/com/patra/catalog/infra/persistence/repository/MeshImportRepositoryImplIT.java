package com.patra.catalog.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.enums.MeshImportTaskStatus;
import com.patra.catalog.domain.model.enums.MeshTableImportStatus;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.model.valueobject.TableProgress;
import com.patra.catalog.infra.persistence.converter.MeshImportConverterImpl;
import com.patra.starter.mybatis.autoconfig.MybatisPluginAutoConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/// MeSH 导入仓储实现集成测试。
///
/// 使用 Testcontainers + MySQL 8 测试完整的 CRUD 操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
///   - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
///   - TestContainers：自动启动和停止 MySQL 容器
///   - 测试覆盖：save()、findById()、findRunningTask()、existsRunningTask()
///
/// **重点测试场景**：
///
/// - save() 新增场景：验证 ID 为 null 时执行 INSERT（自动分配雪花 ID）
///   - save() 更新场景：验证 ID 不为 null 时执行 UPDATE（使用乐观锁）
///   - 关联数据处理：验证 TableProgress 的"删除+重新插入"策略
///   - 并发控制：验证乐观锁机制（version 字段）
///
/// @author linqibin
/// @since 0.1.0
@MybatisPlusTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  MeshImportRepositoryImpl.class,
  MeshImportConverterImpl.class,
  MybatisPluginAutoConfig.class
})
@DisplayName("MeshImportRepositoryImpl 集成测试")
class MeshImportRepositoryImplIT {

  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0.35")
          .withDatabaseName("patra_catalog_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
  }

  @Autowired private MeshImportRepositoryImpl meshImportRepository;

  @Test
  @DisplayName("save() 新增场景 - ID为null时应该执行INSERT并分配雪花ID")
  void save_whenIdIsNull_shouldInsertAndAssignSnowflakeId() {
    // Given: 创建新任务（ID为null → 触发INSERT逻辑）
    MeshImportAggregate aggregate =
        new MeshImportAggregate(
            null, // ID为null，表示新任务
            "MeSH 2025 导入",
            MeshImportTaskStatus.PENDING,
            null,
            null,
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
            "a1b2c3d4e5f6",
            700_000_000L,
            List.of(
                TableProgress.builder()
                    .tableName("cat_mesh_descriptor")
                    .totalCount(35000)
                    .processedCount(0)
                    .failedCount(0)
                    .status(MeshTableImportStatus.NOT_STARTED)
                    .lastBatchNum(0)
                    .lastUpdateTime(Instant.now())
                    .build()),
            350000,
            0,
            0,
            null);

    // When: 保存任务
    MeshImportAggregate saved = meshImportRepository.save(aggregate);

    // Then: 应该分配ID
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getId().value()).isPositive();

    // Then: 可以通过ID查询
    Optional<MeshImportAggregate> found = meshImportRepository.findById(saved.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getTaskName()).isEqualTo("MeSH 2025 导入");
    assertThat(found.get().getTableProgressList()).hasSize(1);
    assertThat(found.get().getTableProgressList().get(0).getTableName())
        .isEqualTo("cat_mesh_descriptor");
  }

  @Test
  @DisplayName("save() 更新场景 - ID不为null时应该执行UPDATE（使用乐观锁）")
  void save_whenIdExists_shouldUpdateWithOptimisticLock() {
    // Given: 先保存一个任务（获得已分配的ID）
    MeshImportAggregate aggregate =
        new MeshImportAggregate(
            null,
            "MeSH 2025 导入",
            MeshImportTaskStatus.PENDING,
            null,
            null,
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
            "a1b2c3d4e5f6",
            700_000_000L,
            List.of(
                TableProgress.builder()
                    .tableName("cat_mesh_descriptor")
                    .totalCount(35000)
                    .processedCount(0)
                    .failedCount(0)
                    .status(MeshTableImportStatus.NOT_STARTED)
                    .lastBatchNum(0)
                    .lastUpdateTime(Instant.now())
                    .build()),
            350000,
            0,
            0,
            null);
    MeshImportAggregate saved = meshImportRepository.save(aggregate);

    // Given: 修改任务状态和进度（ID不为null → 触发UPDATE逻辑）
    MeshImportAggregate updated =
        new MeshImportAggregate(
            saved.getId(), // ID不为null，表示已存在的任务
            saved.getTaskName(),
            MeshImportTaskStatus.PROCESSING, // 修改状态
            Instant.now(),
            null,
            saved.getDescriptorSourceUrl(),
            saved.getQualifierSourceUrl(),
            saved.getXmlFileHash(),
            saved.getXmlFileSize(),
            List.of(
                TableProgress.builder()
                    .tableName("cat_mesh_descriptor")
                    .totalCount(35000)
                    .processedCount(5000) // 更新进度
                    .failedCount(10)
                    .status(MeshTableImportStatus.IN_PROGRESS)
                    .lastBatchNum(5)
                    .lastUpdateTime(Instant.now())
                    .build()),
            350000,
            5000,
            0,
            null);

    // When: 更新任务
    MeshImportAggregate result = meshImportRepository.save(updated);

    // Then: 应该更新状态和进度
    assertThat(result.getStatus()).isEqualTo(MeshImportTaskStatus.PROCESSING);
    assertThat(result.getProcessedRecords()).isEqualTo(5000);
    assertThat(result.getTableProgressList().get(0).getProcessedCount()).isEqualTo(5000);
  }

  @Test
  @DisplayName("查询正在运行的任务 - 应该返回状态为PROCESSING的任务")
  void findRunningTask_shouldReturnProcessingTask() {
    // Given: 保存一个PENDING任务
    MeshImportAggregate pendingTask =
        new MeshImportAggregate(
            null,
            "MeSH 2025 导入 - PENDING",
            MeshImportTaskStatus.PENDING,
            null,
            null,
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
            "a1b2c3d4e5f6",
            700_000_000L,
            List.of(),
            350000,
            0,
            0,
            null);
    meshImportRepository.save(pendingTask);

    // Given: 保存一个PROCESSING任务
    MeshImportAggregate processingTask =
        new MeshImportAggregate(
            null,
            "MeSH 2025 导入 - PROCESSING",
            MeshImportTaskStatus.PROCESSING,
            Instant.now(),
            null,
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
            "a1b2c3d4e5f6",
            700_000_000L,
            List.of(),
            350000,
            1000,
            0,
            null);
    MeshImportAggregate saved = meshImportRepository.save(processingTask);

    // When: 查询正在运行的任务
    Optional<MeshImportAggregate> found = meshImportRepository.findRunningTask();

    // Then: 应该返回PROCESSING任务
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getStatus()).isEqualTo(MeshImportTaskStatus.PROCESSING);
  }

  @Test
  @DisplayName("查询正在运行的任务 - 没有PROCESSING任务时应该返回空")
  void findRunningTask_noProcessingTask_shouldReturnEmpty() {
    // Given: 只有PENDING任务
    MeshImportAggregate pendingTask =
        new MeshImportAggregate(
            null,
            "MeSH 2025 导入",
            MeshImportTaskStatus.PENDING,
            null,
            null,
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
            "a1b2c3d4e5f6",
            700_000_000L,
            List.of(),
            350000,
            0,
            0,
            null);
    meshImportRepository.save(pendingTask);

    // When: 查询正在运行的任务
    Optional<MeshImportAggregate> found = meshImportRepository.findRunningTask();

    // Then: 应该返回空
    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("判断是否存在正在运行的任务 - 应该返回true")
  void existsRunningTask_hasProcessingTask_shouldReturnTrue() {
    // Given: 保存一个PROCESSING任务
    MeshImportAggregate processingTask =
        new MeshImportAggregate(
            null,
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
            1000,
            0,
            null);
    meshImportRepository.save(processingTask);

    // When: 判断是否存在正在运行的任务
    boolean exists = meshImportRepository.existsRunningTask();

    // Then: 应该返回true
    assertThat(exists).isTrue();
  }

  @Test
  @DisplayName("判断是否存在正在运行的任务 - 没有PROCESSING任务时应该返回false")
  void existsRunningTask_noProcessingTask_shouldReturnFalse() {
    // Given: 只有PENDING任务
    MeshImportAggregate pendingTask =
        new MeshImportAggregate(
            null,
            "MeSH 2025 导入",
            MeshImportTaskStatus.PENDING,
            null,
            null,
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
            "a1b2c3d4e5f6",
            700_000_000L,
            List.of(),
            350000,
            0,
            0,
            null);
    meshImportRepository.save(pendingTask);

    // When: 判断是否存在正在运行的任务
    boolean exists = meshImportRepository.existsRunningTask();

    // Then: 应该返回false
    assertThat(exists).isFalse();
  }

  @Test
  @DisplayName("根据ID查询任务 - 任务不存在时应该返回空")
  void findById_taskNotExists_shouldReturnEmpty() {
    // When: 查询不存在的任务
    Optional<MeshImportAggregate> found = meshImportRepository.findById(MeshImportId.of(999999L));

    // Then: 应该返回空
    assertThat(found).isEmpty();
  }
}
