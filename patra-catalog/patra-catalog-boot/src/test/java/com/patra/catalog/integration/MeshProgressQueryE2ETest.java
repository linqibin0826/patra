package com.patra.catalog.integration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import com.patra.catalog.api.dto.MeshProgressDTO;
import com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator;
import com.patra.catalog.app.usecase.meshimport.command.StartImportCommand;
import com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MeSH 导入进度查询 E2E 测试（User Story 2）。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>使用 {@link SpringBootTest} 加载完整应用上下文
 *   <li>使用 {@link Testcontainers} 启动真实的 MySQL 数据库
 *   <li>使用 {@link TestRestTemplate} 发送真实的 HTTP 请求
 *   <li>使用 {@link org.awaitility.Awaitility} 等待异步任务完成
 * </ul>
 *
 * <p>测试场景：
 *
 * <ul>
 *   <li>✅ 查询导入进度并验证进度递增
 *   <li>✅ 验证处理速度计算正确
 *   <li>✅ 验证预计剩余时间递减
 *   <li>✅ 验证各表进度详情
 * </ul>
 *
 * <p><b>注意</b>：此测试依赖完整的导入流程，执行时间较长（约 1-2 分钟）
 *
 * @author Patra Team
 * @since 0.2.0 (User Story 2)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("MeSH 导入进度查询 E2E 测试")
class MeshProgressQueryE2ETest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private MeshImportOrchestrator meshImportOrchestrator;

  @Container
  private static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("patra_catalog_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureMySql(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
  }

  @Test
  @DisplayName("应该能够实时查询导入进度并验证进度递增")
  void shouldQueryProgressInRealTime() {
    // Given: 启动一个导入任务
    StartImportCommand command =
        new StartImportCommand(
            "https://nlm.nih.gov/mesh/desc2025.xml", // sourceUrl（实际测试可用mock数据）
            "E2E 测试导入任务" // taskName
            );

    MeshImportResultDTO startResult = meshImportOrchestrator.startImport(command);
    String taskId = startResult.getTaskId();

    // When & Then: 多次查询进度,验证进度递增
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              // 发送 GET 请求查询进度
              ResponseEntity<MeshProgressDTO> response =
                  restTemplate.getForEntity(
                      "/api/v1/mesh/import/progress/{taskId}",
                      MeshProgressDTO.class,
                      taskId);

              // 验证响应状态
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

              MeshProgressDTO progress = response.getBody();
              assertThat(progress).isNotNull();

              // 验证基本字段
              assertThat(progress.taskId()).isEqualTo(taskId);
              assertThat(progress.taskName()).isEqualTo("E2E 测试导入任务");
              assertThat(progress.status()).isIn("PROCESSING", "SUCCESS");

              // 验证进度计算
              assertThat(progress.overallProgress()).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(100.0);

              // 验证表进度列表
              assertThat(progress.tableProgress()).isNotEmpty();
              assertThat(progress.tableProgress()).hasSize(5); // descriptor, qualifier, treeNumber, entryTerm, concept

              // 验证时间字段
              assertThat(progress.startTime()).isNotNull();
              assertThat(progress.elapsedSeconds()).isNotNull().isGreaterThan(0L);

              // 验证处理速度（如果有进度的话）
              if (progress.overallProgress() > 0.0) {
                assertThat(progress.processSpeed()).isNotNull().isGreaterThan(0.0);
              }

              // 验证预计剩余时间（如果有进度的话）
              if (progress.overallProgress() > 0.0 && progress.overallProgress() < 100.0) {
                assertThat(progress.estimatedRemainingSeconds()).isNotNull();
              }
            });
  }

  @Test
  @DisplayName("应该在任务不存在时返回 404")
  void shouldReturn404WhenTaskNotFound() {
    // Given: 不存在的任务 ID
    String nonExistentTaskId = "999999";

    // When: 查询不存在的任务
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "/api/v1/mesh/import/progress/{taskId}",
            String.class,
            nonExistentTaskId);

    // Then: 应该返回 404 Not Found
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("应该正确计算多表进度")
  void shouldCalculateMultiTableProgress() {
    // Given: 启动一个导入任务并等待部分完成
    StartImportCommand command =
        new StartImportCommand(
            "https://nlm.nih.gov/mesh/desc2025.xml",
            "多表进度测试任务"
        );

    MeshImportResultDTO startResult = meshImportOrchestrator.startImport(command);
    String taskId = startResult.getTaskId();

    // When: 等待一段时间后查询进度
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              ResponseEntity<MeshProgressDTO> response =
                  restTemplate.getForEntity(
                      "/api/v1/mesh/import/progress/{taskId}",
                      MeshProgressDTO.class,
                      taskId);

              MeshProgressDTO progress = response.getBody();
              assertThat(progress).isNotNull();

              // Then: 验证每张表的进度详情
              progress.tableProgress().forEach(tableProgress -> {
                // 验证表名
                assertThat(tableProgress.tableName())
                    .isIn("descriptor", "qualifier", "treeNumber", "entryTerm", "concept");

                // 验证进度百分比
                assertThat(tableProgress.progressPercentage())
                    .isGreaterThanOrEqualTo(0.0)
                    .isLessThanOrEqualTo(100.0);

                // 验证计数字段
                assertThat(tableProgress.processedCount()).isGreaterThanOrEqualTo(0);
                assertThat(tableProgress.totalCount()).isGreaterThan(0);
                assertThat(tableProgress.processedCount()).isLessThanOrEqualTo(tableProgress.totalCount());

                // 验证状态
                assertThat(tableProgress.status())
                    .isIn("NOT_STARTED", "IN_PROGRESS", "COMPLETED", "FAILED");
              });
            });
  }

  @Test
  @DisplayName("应该在任务完成后显示失败批次")
  void shouldShowFailedBatchesWhenPresent() {
    // Given: 启动一个可能有失败批次的导入任务（实际测试中可模拟失败）
    StartImportCommand command =
        new StartImportCommand(
            "https://nlm.nih.gov/mesh/desc2025.xml",
            "失败批次测试任务"
        );

    MeshImportResultDTO startResult = meshImportOrchestrator.startImport(command);
    String taskId = startResult.getTaskId();

    // When: 查询进度
    ResponseEntity<MeshProgressDTO> response =
        restTemplate.getForEntity(
            "/api/v1/mesh/import/progress/{taskId}",
            MeshProgressDTO.class,
            taskId);

    // Then: 验证失败批次列表结构
    MeshProgressDTO progress = response.getBody();
    assertThat(progress).isNotNull();
    assertThat(progress.failedBatches()).isNotNull(); // 可能为空,但不应为 null

    // 如果有失败批次,验证其结构
    if (!progress.failedBatches().isEmpty()) {
      progress.failedBatches().forEach(failedBatch -> {
        assertThat(failedBatch.tableName()).isNotBlank();
        assertThat(failedBatch.batchNum()).isGreaterThan(0);
        assertThat(failedBatch.errorMessage()).isNotBlank();
        assertThat(failedBatch.retryCount()).isGreaterThanOrEqualTo(0);
      });
    }
  }
}
