package dev.linqibin.patra.catalog.integration.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.catalog.app.usecase.mesh.command.MeshScrImportCommand;
import dev.linqibin.patra.catalog.app.usecase.mesh.dto.MeshScrImportResult;
import dev.linqibin.patra.catalog.domain.exception.DataAlreadyExistsException;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadResult;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshConceptDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshEntryTermDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrHeadingMappedToDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrIndexingInfoDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrPharmacologicalActionDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.MeshScrSourceDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.MeshScrHeadingMappedToEntity;
import dev.linqibin.patra.catalog.integration.config.CatalogMySQLContainerInitializer;
import dev.linqibin.starter.objectstorage.ObjectStorageOperations;
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

/// MeSH SCR 导入端到端测试。
///
/// 测试完整的 MeSH SCR 导入流程：
///
/// ```
/// commandBus.handle(MeshScrImportCommand)
///   → MeshScrImportHandler.handle()
///     → MeshBatchAdapter.launchScrImport()
///       → Spring Batch Job
///         → MeshScrItemReader (临时文件下载 + XML 解析)
///         → MeshScrItemWriter (批量写入多张表)
/// ```
///
/// ### 测试数据
///
/// 使用 3 条 SCR 记录的简化 XML，配合 chunk size=2 验证跨 chunk 处理。
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
      "spring.config.import=classpath:catalog-error-config.yaml",
      "patra.batch.chunk.default-size=2"
    })
@ContextConfiguration(initializers = CatalogMySQLContainerInitializer.class)
@ActiveProfiles("e2e-test")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@DisplayName("MeSH SCR 导入 E2E 测试")
class MeshScrImportE2E {

  /// 基础 URL 模板，每个 nested class 使用不同的路径确保 Job 参数唯一。
  private static final String BASE_URL_TEMPLATE = "http://test.nlm.nih.gov/mesh/%s/supp2025.xml";
  private static final String MESH_VERSION = "2025";
  /// 测试资源文件路径（classpath 相对路径）。
  private static final String TEST_RESOURCE_PATH = "/xml/test-scr-e2e.xml";

  /// 期望导入的 SCR 记录数。
  private static final int EXPECTED_SCR_COUNT = 3;
  /// 期望导入的 SCR 映射关系数。
  private static final int EXPECTED_HEADING_MAPPED_TO_COUNT = 3;
  /// 期望导入的 SCR 来源数。
  private static final int EXPECTED_SOURCE_COUNT = 3;
  /// 期望导入的 SCR 索引信息数。
  private static final int EXPECTED_INDEXING_INFO_COUNT = 2;
  /// 期望导入的 SCR 药理作用数。
  private static final int EXPECTED_PHARMACOLOGICAL_ACTION_COUNT = 1;
  /// 期望导入的 SCR 概念数。
  private static final int EXPECTED_CONCEPT_COUNT = 1;
  /// 期望导入的 SCR 入口术语数。
  private static final int EXPECTED_ENTRY_TERM_COUNT = 1;

  // ========== Test Dependencies ==========

  @Autowired private CommandBus commandBus;

  @Autowired private MeshScrDao scrDao;
  @Autowired private MeshScrHeadingMappedToDao headingMappedToDao;
  @Autowired private MeshConceptDao conceptDao;
  @Autowired private MeshEntryTermDao entryTermDao;
  @Autowired private MeshScrSourceDao sourceDao;
  @Autowired private MeshScrIndexingInfoDao indexingInfoDao;
  @Autowired private MeshScrPharmacologicalActionDao pharmacologicalActionDao;
  @Autowired private JdbcTemplate jdbcTemplate;

  /// Mock FileDownloadPort，将测试资源文件写入临时文件后返回路径。
  @MockitoBean private FileDownloadPort fileDownloadPort;

  /// Mock ObjectStorageOperations，E2E 测试不需要真实的对象存储。
  @MockitoBean private ObjectStorageOperations objectStorageOperations;

  // ========== Setup & Teardown ==========

  /// 测试初始化。
  @BeforeEach
  void setUp() {
    cleanupAllTables();
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
    Path tempFile = Files.createTempFile("mesh-scr-e2e-", ".xml");
    Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    inputStream.close();
    return FileDownloadResult.of(tempFile, Files.size(tempFile));
  }

  /// 清空所有 MeSH SCR 相关表。
  private void cleanupAllTables() {
    // 清空业务表（注意顺序：先子表后主表）
    pharmacologicalActionDao.deleteAllInBatch();
    indexingInfoDao.deleteAllInBatch();
    sourceDao.deleteAllInBatch();
    headingMappedToDao.deleteAllInBatch();
    entryTermDao.deleteAllInBatch();
    conceptDao.deleteAllInBatch();
    scrDao.deleteAllInBatch();

    // 清空 Spring Batch 元数据表（确保重试时可以重新执行 Job）
    jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
    jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
    jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
    jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION");
    jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION");
    jdbcTemplate.execute("DELETE FROM BATCH_JOB_INSTANCE");
    jdbcTemplate.execute("UPDATE BATCH_STEP_EXECUTION_SEQ SET ID = 0");
    jdbcTemplate.execute("UPDATE BATCH_JOB_EXECUTION_SEQ SET ID = 0");
    jdbcTemplate.execute("UPDATE BATCH_JOB_INSTANCE_SEQ SET ID = 0");
  }

  // ========== 一次性初始化测试 ==========

  @Nested
  @Order(1)
  @DisplayName("一次性初始化测试")
  class OneTimeInitializationTest {

    /// 此 nested class 专用的 URL，与其他 nested class 区分避免 Job 参数冲突。
    private static final String TEST_URL = BASE_URL_TEMPLATE.formatted("init");

    /// 首次导入应成功完成。
    @Test
    @DisplayName("首次导入应该成功完成")
    void shouldCompleteFirstImport() {
      // Given
      MeshScrImportCommand command = MeshScrImportCommand.of(TEST_URL, MESH_VERSION);

      // When
      MeshScrImportResult result = commandBus.handle(command);

      // Then - 验证 Job 启动成功
      assertThat(result).isNotNull();
      assertThat(result.executionId()).isNotNull();
      assertThat(result.sourceUrl()).isEqualTo(TEST_URL);
      assertThat(result.meshVersion()).isEqualTo(MESH_VERSION);

      // Then - 验证数据库状态
      verifyDatabaseState();
    }
  }

  // ========== 数据存在性检查测试 ==========

  @Nested
  @Order(2)
  @DisplayName("数据存在性检查测试")
  class DataExistenceCheckTest {

    /// 此 nested class 专用的 URL，与其他 nested class 区分避免 Job 参数冲突。
    private static final String TEST_URL = BASE_URL_TEMPLATE.formatted("existence");

    /// 表中已有数据时应抛出 ApplicationException。
    @Test
    @DisplayName("表中已有数据时应该抛出 ApplicationException")
    void shouldThrowExceptionWhenDataAlreadyExists() {
      // Given - 先执行第一次导入
      MeshScrImportCommand command = MeshScrImportCommand.of(TEST_URL, MESH_VERSION);
      commandBus.handle(command);

      // 验证数据已导入
      assertThat(scrDao.count()).isEqualTo(EXPECTED_SCR_COUNT);

      // When/Then - 再次导入应该抛出 DataAlreadyExistsException
      assertThatThrownBy(() -> commandBus.handle(command))
          .isInstanceOf(DataAlreadyExistsException.class)
          .hasMessageContaining("MeSH SCR");
    }
  }

  // ========== Helper Methods ==========

  /// 验证数据库状态。
  private void verifyDatabaseState() {
    assertThat(scrDao.count()).as("SCR 主表记录数").isEqualTo(EXPECTED_SCR_COUNT);
    assertThat(headingMappedToDao.count())
        .as("SCR 映射关系记录数")
        .isEqualTo(EXPECTED_HEADING_MAPPED_TO_COUNT);
    assertThat(sourceDao.count()).as("SCR 来源记录数").isEqualTo(EXPECTED_SOURCE_COUNT);
    assertThat(indexingInfoDao.count()).as("SCR 索引信息记录数").isEqualTo(EXPECTED_INDEXING_INFO_COUNT);
    assertThat(pharmacologicalActionDao.count())
        .as("SCR 药理作用记录数")
        .isEqualTo(EXPECTED_PHARMACOLOGICAL_ACTION_COUNT);
    assertThat(conceptDao.count()).as("SCR 概念记录数").isEqualTo(EXPECTED_CONCEPT_COUNT);
    assertThat(entryTermDao.count()).as("SCR 入口术语记录数").isEqualTo(EXPECTED_ENTRY_TERM_COUNT);

    // 验证 Major Topic（星号前缀）功能
    verifyMajorTopicParsing();
  }

  /// 验证 Major Topic 解析功能。
  ///
  /// 测试数据 C000003 包含一个带星号前缀的 HeadingMappedTo（*D000020），
  /// 应该被解析为 majorTopic=true，且星号被剥离存储为 D000020。
  private void verifyMajorTopicParsing() {
    var majorTopicMappings =
        headingMappedToDao.findAll().stream()
            .filter(MeshScrHeadingMappedToEntity::isMajorTopic)
            .toList();

    assertThat(majorTopicMappings).as("Major Topic 映射数量").hasSize(1);

    MeshScrHeadingMappedToEntity majorTopicMapping = majorTopicMappings.getFirst();
    assertThat(majorTopicMapping.getScrUi()).as("Major Topic 所属 SCR").isEqualTo("C000003");
    assertThat(majorTopicMapping.getDescriptorUi())
        .as("Major Topic Descriptor UI (星号应被剥离)")
        .isEqualTo("D000020");
    assertThat(majorTopicMapping.getQualifierUi())
        .as("Major Topic Qualifier UI")
        .isEqualTo("Q000002");
  }
}
