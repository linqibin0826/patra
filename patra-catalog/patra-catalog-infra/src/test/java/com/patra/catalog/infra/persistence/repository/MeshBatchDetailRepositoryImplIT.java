package com.patra.catalog.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.catalog.domain.model.enums.MeshBatchStatus;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.infra.persistence.entity.MeshBatchDetailDO;
import com.patra.catalog.infra.persistence.mapper.MeshBatchDetailMapper;
import com.patra.starter.mybatis.autoconfig.MybatisPluginAutoConfig;
import java.time.Instant;
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

/// MeSH 批次详情仓储实现集成测试。
///
/// 使用 @MybatisPlusTest + TestContainers（MySQL 8）测试批次查询操作。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
///   - 测试隔离：每个测试方法独立，使用 @Transactional 自动回滚
///   - TestContainers：自动启动和停止 MySQL 容器
///   - 测试覆盖：findFailedBatches()、countByStatus()
///
/// **重点测试场景**：
///
/// - 查询失败批次：验证返回所有状态为 FAILED 的批次
///   - 按状态计数：验证按状态统计批次数量
///   - 边界情况：无失败批次时返回空列表
///
/// @author linqibin
/// @since 0.1.0
@MybatisPlusTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MeshBatchDetailRepositoryImpl.class, MybatisPluginAutoConfig.class})
@DisplayName("MeshBatchDetailRepositoryImpl 集成测试")
class MeshBatchDetailRepositoryImplIT {

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

  @Autowired private MeshBatchDetailRepositoryImpl meshBatchDetailRepository;

  @Autowired private MeshBatchDetailMapper batchDetailMapper;

  @Test
  @DisplayName("查询失败批次 - 应该返回状态为FAILED的所有批次")
  void findFailedBatches_shouldReturnFailedBatches() {
    // Given: 创建一个任务ID
    Long importId = 1000L;
    MeshImportId meshImportId = new MeshImportId(importId);

    // Given: 插入多个批次（包含成功和失败）
    MeshBatchDetailDO successBatch1 =
        createBatchDetail(importId, "cat_mesh_descriptor", 1, "SUCCESS");
    MeshBatchDetailDO failedBatch1 =
        createBatchDetail(importId, "cat_mesh_descriptor", 2, "FAILED");
    failedBatch1.setErrorMessage("数据库连接超时");
    failedBatch1.setRetryCount(1);
    MeshBatchDetailDO successBatch2 =
        createBatchDetail(importId, "cat_mesh_qualifier", 1, "SUCCESS");
    MeshBatchDetailDO failedBatch2 = createBatchDetail(importId, "cat_mesh_qualifier", 2, "FAILED");
    failedBatch2.setErrorMessage("数据格式错误");
    failedBatch2.setRetryCount(2);

    batchDetailMapper.insert(successBatch1);
    batchDetailMapper.insert(failedBatch1);
    batchDetailMapper.insert(successBatch2);
    batchDetailMapper.insert(failedBatch2);

    // When: 查询失败批次
    var failedBatches = meshBatchDetailRepository.findFailedBatches(meshImportId);

    // Then: 应该只返回失败批次
    assertThat(failedBatches).hasSize(2);
    assertThat(failedBatches)
        .extracting("failureReason")
        .containsExactlyInAnyOrder("数据库连接超时", "数据格式错误");
    assertThat(failedBatches)
        .extracting("tableName")
        .containsExactly("cat_mesh_descriptor", "cat_mesh_qualifier");
    assertThat(failedBatches.get(0).canRetry()).isTrue();
    assertThat(failedBatches.get(1).canRetry()).isTrue();
  }

  @Test
  @DisplayName("查询失败批次 - 无失败批次时应该返回空列表")
  void findFailedBatches_noFailedBatches_shouldReturnEmptyList() {
    // Given: 创建一个任务ID
    Long importId = 2000L;
    MeshImportId meshImportId = new MeshImportId(importId);

    // Given: 只插入成功批次
    MeshBatchDetailDO successBatch =
        createBatchDetail(importId, "cat_mesh_descriptor", 1, "SUCCESS");
    batchDetailMapper.insert(successBatch);

    // When: 查询失败批次
    var failedBatches = meshBatchDetailRepository.findFailedBatches(meshImportId);

    // Then: 应该返回空列表
    assertThat(failedBatches).isEmpty();
  }

  @Test
  @DisplayName("按状态计数 - 应该返回指定状态的批次数量")
  void countByStatus_shouldReturnCountForStatus() {
    // Given: 创建一个任务ID
    Long importId = 3000L;
    MeshImportId meshImportId = new MeshImportId(importId);

    // Given: 插入不同状态的批次
    batchDetailMapper.insert(createBatchDetail(importId, "cat_mesh_descriptor", 1, "SUCCESS"));
    batchDetailMapper.insert(createBatchDetail(importId, "cat_mesh_descriptor", 2, "SUCCESS"));
    batchDetailMapper.insert(createBatchDetail(importId, "cat_mesh_descriptor", 3, "FAILED"));
    batchDetailMapper.insert(createBatchDetail(importId, "cat_mesh_qualifier", 1, "PROCESSING"));

    // When: 统计SUCCESS状态的批次
    Long successCount =
        meshBatchDetailRepository.countByStatus(meshImportId, MeshBatchStatus.SUCCESS);

    // Then: 应该返回2
    assertThat(successCount).isEqualTo(2L);

    // When: 统计FAILED状态的批次
    Long failedCount =
        meshBatchDetailRepository.countByStatus(meshImportId, MeshBatchStatus.FAILED);

    // Then: 应该返回1
    assertThat(failedCount).isEqualTo(1L);

    // When: 统计PROCESSING状态的批次
    Long processingCount =
        meshBatchDetailRepository.countByStatus(meshImportId, MeshBatchStatus.PROCESSING);

    // Then: 应该返回1
    assertThat(processingCount).isEqualTo(1L);
  }

  @Test
  @DisplayName("按状态计数 - 无匹配批次时应该返回0")
  void countByStatus_noMatchingBatches_shouldReturnZero() {
    // Given: 创建一个任务ID
    Long importId = 4000L;
    MeshImportId meshImportId = new MeshImportId(importId);

    // Given: 只插入SUCCESS状态的批次
    batchDetailMapper.insert(createBatchDetail(importId, "cat_mesh_descriptor", 1, "SUCCESS"));

    // When: 统计FAILED状态的批次
    Long failedCount =
        meshBatchDetailRepository.countByStatus(meshImportId, MeshBatchStatus.FAILED);

    // Then: 应该返回0
    assertThat(failedCount).isEqualTo(0L);
  }

  /// 创建批次详情测试数据。
  ///
  /// @param importId 任务ID
  /// @param tableName 表名
  /// @param batchNum 批次号
  /// @param status 状态
  /// @return 批次详情DO
  private MeshBatchDetailDO createBatchDetail(
      Long importId, String tableName, Integer batchNum, String status) {
    MeshBatchDetailDO batchDetail = new MeshBatchDetailDO();
    batchDetail.setImportId(importId);
    batchDetail.setTableName(tableName);
    batchDetail.setBatchNum(batchNum);
    batchDetail.setBatchSize(1000);
    batchDetail.setStatus(status);
    batchDetail.setRetryCount(0);
    batchDetail.setStartTime(Instant.now());
    if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
      batchDetail.setEndTime(Instant.now());
    }
    return batchDetail;
  }
}
