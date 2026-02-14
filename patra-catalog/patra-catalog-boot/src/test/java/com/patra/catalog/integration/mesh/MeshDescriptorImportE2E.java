package com.patra.catalog.integration.mesh;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.mesh.command.MeshDescriptorImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshDescriptorImportResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.catalog.domain.port.source.FileDownloadResult;
import com.patra.catalog.infra.persistence.dao.MeshConceptDao;
import com.patra.catalog.infra.persistence.dao.MeshConceptRelationDao;
import com.patra.catalog.infra.persistence.dao.MeshDescriptorDao;
import com.patra.catalog.infra.persistence.dao.MeshEntryCombinationDao;
import com.patra.catalog.infra.persistence.dao.MeshEntryTermDao;
import com.patra.catalog.infra.persistence.dao.MeshTreeNumberDao;
import com.patra.catalog.integration.config.CatalogMySQLContainerInitializer;
import com.patra.common.cqrs.CommandBus;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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
/// commandBus.handle(MeshDescriptorImportCommand)
///   → MeshDescriptorImportHandler.handle()
///     → MeshBatchAdapter.launchDescriptorImport()
///       → Spring Batch Job
///         → MeshDescriptorItemReader (临时文件下载 + XML 解析)
///         → MeshDescriptorItemWriter (批量写入 6 张表)
/// ```
///
/// ### 测试数据
///
/// 使用 502 条真实 MeSH Descriptor 数据（从 desc2025.xml 提取），刚好超过 chunk size (500)，
/// 可测试跨 chunk 处理和断点续传。
///
/// ### 测试场景
///
/// - 一次性初始化：首次导入应成功
/// - 数据存在性检查：表中有数据时应拒绝导入
///
/// @author linqibin
/// @since 0.1.0
@SpringBootTest(
    properties = {
      "spring.cloud.consul.enabled=false",
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

  @Autowired private CommandBus commandBus;

  @Autowired private MeshDescriptorDao descriptorRepository;
  @Autowired private MeshTreeNumberDao treeNumberRepository;
  @Autowired private MeshConceptDao conceptRepository;
  @Autowired private MeshConceptRelationDao conceptRelationRepository;
  @Autowired private MeshEntryTermDao entryTermRepository;
  @Autowired private MeshEntryCombinationDao entryCombinationRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  /// Mock FileDownloadPort，将测试资源文件写入临时文件后返回路径。
  @MockitoBean private FileDownloadPort fileDownloadPort;

  // ========== Setup & Teardown ==========

  @BeforeEach
  void setUp() {
    // 清空所有 MeSH 表（注意顺序：先子表后主表）
    cleanupAllTables();

    // 配置 Mock：每次调用都将测试资源写入临时文件
    configureFileDownloadMock();
  }

  /// 配置文件下载 Mock。
  ///
  /// 每次调用 download() 都将测试 XML 写入新的临时文件，返回 FileDownloadResult。
  private void configureFileDownloadMock() {
    when(fileDownloadPort.download(any(URI.class)))
        .thenAnswer(
            invocation -> {
              InputStream testInputStream = getClass().getResourceAsStream(TEST_RESOURCE_PATH);
              if (testInputStream == null) {
                throw new IllegalStateException("测试资源文件不存在: " + TEST_RESOURCE_PATH);
              }
              return copyToTempFile(testInputStream);
            });
  }

  /// 将 InputStream 内容写入临时文件。
  ///
  /// @param inputStream 输入流
  /// @return 临时文件下载结果
  private FileDownloadResult copyToTempFile(InputStream inputStream) throws IOException {
    Path tempFile = Files.createTempFile("mesh-descriptor-e2e-", ".xml");
    Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    inputStream.close();
    return FileDownloadResult.of(tempFile, Files.size(tempFile));
  }

  /// 清空所有 MeSH 相关表。
  private void cleanupAllTables() {
    // 清空业务表（注意顺序：先子表后主表）
    entryCombinationRepository.deleteAllInBatch();
    conceptRelationRepository.deleteAllInBatch();
    entryTermRepository.deleteAllInBatch();
    conceptRepository.deleteAllInBatch();
    treeNumberRepository.deleteAllInBatch();
    descriptorRepository.deleteAllInBatch();

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

  // ========== 一次性初始化测试 ==========

  @Nested
  @Order(1)
  @DisplayName("一次性初始化测试")
  class OneTimeInitializationTest {

    /// 此 nested class 专用的 URL，与其他 nested class 区分避免 Job 参数冲突。
    private static final String TEST_URL = BASE_URL_TEMPLATE.formatted("init");

    @Test
    @DisplayName("首次导入应该成功完成")
    void shouldCompleteFirstImport() {
      // Given
      MeshDescriptorImportCommand command = MeshDescriptorImportCommand.of(TEST_URL, MESH_VERSION);

      // When
      MeshDescriptorImportResult result = commandBus.handle(command);

      // Then - 验证 Job 启动成功
      assertThat(result).isNotNull();
      assertThat(result.executionId()).isNotNull();
      assertThat(result.sourceUrl()).isEqualTo(TEST_URL);
      assertThat(result.meshVersion()).isEqualTo(MESH_VERSION);

      // Then - 验证数据库状态
      verifyDatabaseState(EXPECTED_DESCRIPTOR_COUNT);
    }
  }

  // ========== 数据存在性检查测试 ==========

  @Nested
  @Order(2)
  @DisplayName("数据存在性检查测试")
  class DataExistenceCheckTest {

    /// 此 nested class 专用的 URL，与其他 nested class 区分避免 Job 参数冲突。
    private static final String TEST_URL = BASE_URL_TEMPLATE.formatted("existence");

    @Test
    @DisplayName("表中已有数据时应该抛出 DataAlreadyExistsException")
    void shouldThrowExceptionWhenDataAlreadyExists() {
      // Given - 先执行第一次导入
      MeshDescriptorImportCommand command = MeshDescriptorImportCommand.of(TEST_URL, MESH_VERSION);
      commandBus.handle(command);

      // 验证数据已导入
      assertThat(descriptorRepository.count()).isEqualTo(EXPECTED_DESCRIPTOR_COUNT);

      // When/Then - 再次导入应该抛出 DataAlreadyExistsException
      assertThatThrownBy(() -> commandBus.handle(command))
          .isInstanceOf(DataAlreadyExistsException.class)
          .hasMessageContaining("MeSH Descriptor");
    }
  }

  // ========== Helper Methods ==========

  /// 验证数据库状态。
  ///
  /// @param expectedDescriptorCount 预期的 Descriptor 记录数
  private void verifyDatabaseState(int expectedDescriptorCount) {
    // 验证主表记录数
    assertThat(descriptorRepository.count())
        .as("Descriptor 记录数")
        .isEqualTo(expectedDescriptorCount);

    // 验证子表有数据
    assertThat(treeNumberRepository.count()).as("TreeNumber 记录数").isGreaterThan(0);

    assertThat(conceptRepository.count()).as("Concept 记录数").isGreaterThan(0);

    assertThat(entryTermRepository.count()).as("EntryTerm 记录数").isGreaterThan(0);

    // 注：conceptRelation 和 entryCombination 可能为空（取决于测试数据）
  }
}
