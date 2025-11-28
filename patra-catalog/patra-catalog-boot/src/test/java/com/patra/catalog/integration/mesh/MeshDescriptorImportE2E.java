package com.patra.catalog.integration.mesh;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.mesh.MeshImportUseCase;
import com.patra.catalog.app.usecase.mesh.command.MeshDescriptorImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshDescriptorImportResult;
import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.infra.persistence.mapper.MeshConceptMapper;
import com.patra.catalog.infra.persistence.mapper.MeshConceptRelationMapper;
import com.patra.catalog.infra.persistence.mapper.MeshDescriptorMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryCombinationMapper;
import com.patra.catalog.infra.persistence.mapper.MeshEntryTermMapper;
import com.patra.catalog.infra.persistence.mapper.MeshTreeNumberMapper;
import com.patra.catalog.integration.config.CatalogMySQLContainerInitializer;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// MeSH Descriptor 导入端到端测试。
///
/// 测试完整的 MeSH 主题词导入流程：
///
/// ```
/// MeshImportOrchestrator.importDescriptors()
///   → FileDownloadPort.downloadToTemp()
///   → MeshDescriptorBatchAdapter.launchImport()
///     → Spring Batch Job
///       → MeshDescriptorItemReader (XML 解析)
///       → MeshDescriptorItemWriter (批量写入 6 张表)
///   → MeshImportJobExecutionListener (清理临时文件)
/// ```
///
/// ### 测试数据
///
/// 使用 502 条真实 MeSH Descriptor 数据（从 desc2025.xml 提取），刚好超过 chunk size (500)，
/// 可测试跨 chunk 处理和断点续传。
///
/// ### 测试场景
///
/// - TRUNCATE_REIMPORT 模式：完整导入流程
/// - INCREMENTAL 模式：增量导入、幂等性
/// - 断点续传：Job 失败后重启继续
///
/// @author linqibin
/// @since 0.1.0
@SpringBootTest(
    properties = {
      "spring.cloud.nacos.config.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.nacos.config.import-check.enabled=false",
      "spring.config.import=classpath:catalog-error-config.yaml"
    })
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@ActiveProfiles("e2e-test")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@DisplayName("MeSH Descriptor 导入 E2E 测试")
class MeshDescriptorImportE2E {

  /// 基础 URL 模板，每个 nested class 使用不同的路径确保 Job 参数唯一。
  ///
  /// **背景**：Spring Batch 的 JobRepository 有内存缓存，即使清空数据库表，
  /// 内存中仍保留 Job 执行记录。使用不同 URL 可避免 Job 参数冲突。
  private static final String BASE_URL_TEMPLATE = "http://test.nlm.nih.gov/mesh/%s/desc2025.xml";
  private static final String MESH_VERSION = "2025";
  /// 测试资源文件路径（classpath 相对路径）。
  private static final String TEST_RESOURCE_PATH = "/xml/test-descriptors-e2e.xml";
  /// 期望的 Descriptor 数量。
  /// 测试数据包含 502 条 DescriptorRecord，全部应成功导入。
  private static final int EXPECTED_DESCRIPTOR_COUNT = 502;

  // ========== Test Dependencies ==========

  @Autowired private MeshImportUseCase meshImportUseCase;

  @Autowired private MeshDescriptorMapper descriptorMapper;
  @Autowired private MeshTreeNumberMapper treeNumberMapper;
  @Autowired private MeshConceptMapper conceptMapper;
  @Autowired private MeshConceptRelationMapper conceptRelationMapper;
  @Autowired private MeshEntryTermMapper entryTermMapper;
  @Autowired private MeshEntryCombinationMapper entryCombinationMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  /// Mock FileDownloadPort，返回本地测试文件路径。
  @MockitoBean private FileDownloadPort fileDownloadPort;

  private Path tempFile;

  // ========== Setup & Teardown ==========

  @BeforeEach
  void setUp() throws IOException {
    // 清空所有 MeSH 表（注意顺序：先子表后主表）
    cleanupAllTables();

    // 复制测试文件到临时目录并配置 Mock
    prepareTempFileAndMock();
  }

  /// 复制测试资源到临时文件并配置 Mock。
  ///
  /// 使用 ClassLoader 资源加载方式，避免依赖工作目录。
  /// 临时文件会被 `MeshImportJobExecutionListener` 在 Job 完成后清理。
  private void prepareTempFileAndMock() throws IOException {
    tempFile = Files.createTempFile("mesh-e2e-", ".xml");
    try (var inputStream = getClass().getResourceAsStream(TEST_RESOURCE_PATH)) {
      if (inputStream == null) {
        throw new IllegalStateException("测试资源文件不存在: " + TEST_RESOURCE_PATH);
      }
      Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
    }
    when(fileDownloadPort.downloadToTemp(any(URI.class))).thenReturn(tempFile);
  }

  /// 清空所有 MeSH 相关表。
  private void cleanupAllTables() {
    // 清空业务表（注意顺序：先子表后主表）
    entryCombinationMapper.delete(null);
    conceptRelationMapper.delete(null);
    entryTermMapper.delete(null);
    conceptMapper.delete(null);
    treeNumberMapper.delete(null);
    descriptorMapper.delete(null);

    // 清空 Spring Batch 元数据表（确保重试时可以重新执行 Job）
    // 按外键依赖顺序删除
    jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
    jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
    jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
    jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION");
    jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION");
    jdbcTemplate.execute("DELETE FROM BATCH_JOB_INSTANCE");
    // 重置序列值
    jdbcTemplate.execute("UPDATE BATCH_STEP_EXECUTION_SEQ SET ID = 0");
    jdbcTemplate.execute("UPDATE BATCH_JOB_EXECUTION_SEQ SET ID = 0");
    jdbcTemplate.execute("UPDATE BATCH_JOB_SEQ SET ID = 0");
  }

  // ========== TRUNCATE_REIMPORT 模式测试 ==========

  @Nested
  @Order(2)
  @DisplayName("TRUNCATE_REIMPORT 模式测试")
  class TruncateReimportModeTest {

    /// 此 nested class 专用的 URL，与其他 nested class 区分避免 Job 参数冲突。
    private static final String TEST_URL = BASE_URL_TEMPLATE.formatted("truncate");

    @Test
    @DisplayName("应该完成 TRUNCATE_REIMPORT 模式的完整导入流程")
    void shouldCompleteFullImportWithTruncateReimportMode() {
      // Given
      MeshDescriptorImportCommand command =
          MeshDescriptorImportCommand.of(TEST_URL, MESH_VERSION, "TRUNCATE_REIMPORT");

      // When
      MeshDescriptorImportResult result = meshImportUseCase.importDescriptors(command);

      // Then - 验证 Job 启动成功
      assertThat(result).isNotNull();
      assertThat(result.executionId()).isNotNull();
      assertThat(result.sourceUrl()).isEqualTo(TEST_URL);
      assertThat(result.meshVersion()).isEqualTo(MESH_VERSION);

      // Then - 验证数据库状态
      verifyDatabaseState(EXPECTED_DESCRIPTOR_COUNT);
    }

    @Test
    @DisplayName("TRUNCATE_REIMPORT 模式重复执行应该幂等")
    void shouldBeIdempotentWithTruncateReimport() throws IOException {
      // Given - 执行第一次导入
      MeshDescriptorImportCommand command =
          MeshDescriptorImportCommand.of(TEST_URL, MESH_VERSION, "TRUNCATE_REIMPORT");
      meshImportUseCase.importDescriptors(command);

      // 记录第一次导入后的状态
      long firstDescriptorCount = descriptorMapper.selectCount(null);
      long firstTreeNumberCount = treeNumberMapper.selectCount(null);

      // 重新准备临时文件（因为第一次执行后被清理）
      prepareTempFileAndMock();

      // When - 执行第二次导入
      MeshDescriptorImportResult secondResult = meshImportUseCase.importDescriptors(command);

      // Then - 验证第二次导入成功
      assertThat(secondResult.executionId()).isNotNull();

      // Then - 验证数据库状态与第一次一致（幂等性）
      assertThat(descriptorMapper.selectCount(null)).isEqualTo(firstDescriptorCount);
      assertThat(treeNumberMapper.selectCount(null)).isEqualTo(firstTreeNumberCount);
    }
  }

  // ========== INCREMENTAL 模式测试 ==========

  @Nested
  @Order(1)
  @DisplayName("INCREMENTAL 模式测试")
  class IncrementalModeTest {

    /// 此 nested class 专用的 URL，与其他 nested class 区分避免 Job 参数冲突。
    private static final String TEST_URL = BASE_URL_TEMPLATE.formatted("incremental");

    @Test
    @DisplayName("INCREMENTAL 模式 - 首次导入应该成功")
    void shouldImportSuccessfullyOnFirstRun() {
      // Given
      MeshDescriptorImportCommand command =
          MeshDescriptorImportCommand.of(TEST_URL, MESH_VERSION, "INCREMENTAL");

      // When
      MeshDescriptorImportResult result = meshImportUseCase.importDescriptors(command);

      // Then - 验证 Job 启动成功
      assertThat(result).isNotNull();
      assertThat(result.executionId()).isNotNull();

      // Then - 验证所有记录都被导入
      verifyDatabaseState(EXPECTED_DESCRIPTOR_COUNT);
    }

    @Test
    @DisplayName("INCREMENTAL 模式 - 重复导入应该跳过已存在记录（幂等性）")
    void shouldSkipExistingRecordsOnRerun() throws IOException {
      // Given - 执行第一次导入
      MeshDescriptorImportCommand command =
          MeshDescriptorImportCommand.of(TEST_URL, MESH_VERSION, "INCREMENTAL");
      meshImportUseCase.importDescriptors(command);

      // 记录第一次导入后的状态
      long firstDescriptorCount = descriptorMapper.selectCount(null);

      // 重新准备临时文件
      prepareTempFileAndMock();

      // When - 再次以 INCREMENTAL 模式导入相同数据
      MeshDescriptorImportResult secondResult = meshImportUseCase.importDescriptors(command);

      // Then - 验证 Job 成功完成（跳过了已存在的记录）
      assertThat(secondResult.executionId()).isNotNull();

      // Then - 验证记录数不变（DataIntegrityViolationException 被跳过）
      assertThat(descriptorMapper.selectCount(null)).isEqualTo(firstDescriptorCount);
    }
  }

  // ========== 临时文件清理测试 ==========

  @Nested
  @Order(3)
  @DisplayName("临时文件清理测试")
  class TempFileCleanupTest {

    /// 此 nested class 专用的 URL，与其他 nested class 区分避免 Job 参数冲突。
    private static final String TEST_URL = BASE_URL_TEMPLATE.formatted("cleanup");

    @Test
    @DisplayName("Job 完成后应该清理临时文件")
    void shouldCleanupTempFileAfterJobCompletion() {
      // Given
      MeshDescriptorImportCommand command =
          MeshDescriptorImportCommand.of(TEST_URL, MESH_VERSION, "TRUNCATE_REIMPORT");

      // When
      meshImportUseCase.importDescriptors(command);

      // Then - 验证临时文件已被清理
      assertThat(Files.exists(tempFile)).isFalse();
    }
  }

  // ========== Helper Methods ==========

  /// 验证数据库状态。
  ///
  /// @param expectedDescriptorCount 预期的 Descriptor 记录数
  private void verifyDatabaseState(int expectedDescriptorCount) {
    // 验证主表记录数
    assertThat(descriptorMapper.selectCount(null))
        .as("Descriptor 记录数")
        .isEqualTo(expectedDescriptorCount);

    // 验证子表有数据
    assertThat(treeNumberMapper.selectCount(null)).as("TreeNumber 记录数").isGreaterThan(0);

    assertThat(conceptMapper.selectCount(null)).as("Concept 记录数").isGreaterThan(0);

    assertThat(entryTermMapper.selectCount(null)).as("EntryTerm 记录数").isGreaterThan(0);

    // 注：conceptRelation 和 entryCombination 可能为空（取决于测试数据）
  }
}
