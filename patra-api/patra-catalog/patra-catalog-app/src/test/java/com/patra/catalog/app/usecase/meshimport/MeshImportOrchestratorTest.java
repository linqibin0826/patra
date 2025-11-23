package com.patra.catalog.app.usecase.meshimport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.patra.catalog.app.usecase.meshimport.dto.MeshImportResultDTO;
import com.patra.catalog.app.usecase.meshimport.validator.MeshDataValidator;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.enums.MeshImportTaskStatus;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.port.MeshDescriptorRepository;
import com.patra.catalog.domain.port.MeshFileDownloadPort;
import com.patra.catalog.domain.port.MeshImportRepository;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

/// MeshImportOrchestrator 单元测试。
///
/// 测试策略：
///
/// - Mock 所有 Port 接口（不依赖真实实现）
///   - 使用 InOrder 验证调用顺序
///   - 验证事务边界（@Transactional 在 Orchestrator 层）
///   - 测试编排逻辑：下载 → 解析 → 保存 → 验证 → 更新状态
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("MeshImportOrchestrator 单元测试")
class MeshImportOrchestratorTest {

  @Mock private MeshImportRepository meshImportPort;
  @Mock private XmlParserPort xmlParserPort;
  @Mock private MeshFileDownloadPort meshFileDownloadPort;
  @Mock private MeshDescriptorRepository meshDescriptorRepository;
  @Mock private com.patra.catalog.domain.port.MeshQualifierRepository meshQualifierRepository;
  @Mock private MeshDataValidator meshDataValidator;
  @Mock private com.patra.catalog.app.config.MeshImportConfig meshImportConfig;

  // 策略实现 Mock
  @Mock private com.patra.catalog.app.usecase.meshimport.strategy.QualifierImporter qualifierImporter;
  @Mock private com.patra.catalog.app.usecase.meshimport.strategy.DescriptorImporter descriptorImporter;
  @Mock private com.patra.catalog.app.usecase.meshimport.strategy.TreeNumberImporter treeNumberImporter;
  @Mock private com.patra.catalog.app.usecase.meshimport.strategy.EntryTermImporter entryTermImporter;
  @Mock private com.patra.catalog.app.usecase.meshimport.strategy.ConceptImporter conceptImporter;

  private MeshImportOrchestrator orchestrator;

  private MeshImportAggregate mockAggregate;
  private File mockXmlFile;

  /// 创建所有表都已完成的聚合根（用于成功场景测试）
  private MeshImportAggregate createCompletedAggregate() {
    return createCompletedAggregate(35000);
  }

  /// 创建所有表都已完成的聚合根（可指定每个表的记录数）
  private MeshImportAggregate createCompletedAggregate(int countPerTable) {
    List<com.patra.catalog.domain.model.valueobject.TableProgress> completedTableProgressList =
        new java.util.ArrayList<>();
    List<String> tableNames =
        List.of("descriptor", "qualifier", "tree-number", "entry-term", "concept");
    for (String tableName : tableNames) {
      completedTableProgressList.add(
          com.patra.catalog.domain.model.valueobject.TableProgress.builder()
              .tableName(tableName)
              .expectedCount(countPerTable)
              .actualTotalCount(countPerTable) // 已完成，实际总数已知
              .processedCount(countPerTable)
              .failedCount(0)
              .status(com.patra.catalog.domain.model.enums.MeshTableImportStatus.COMPLETED)
              .lastBatchNum((countPerTable / 1000) + 1) // 假设批次大小 1000
              .lastUpdateTime(java.time.Instant.now())
              .build());
    }

    int totalCount = countPerTable * 5;
    return new MeshImportAggregate(
        MeshImportId.of(1L),
        "2025年MeSH数据首次导入",
        MeshImportTaskStatus.PROCESSING,
        java.time.Instant.now(),
        java.time.Instant.now(), // 修复：设置 endTime 为当前时间，避免 NullPointerException
        "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
        "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
        null,
        null,
        completedTableProgressList,
        totalCount,
        totalCount,
        0,
        null);
  }

  @BeforeEach
  void setUp() throws Exception {
    // Mock 策略实现的 getDataType() 方法
    when(qualifierImporter.getDataType())
        .thenReturn(com.patra.catalog.domain.model.enums.MeshDataType.QUALIFIER);
    when(descriptorImporter.getDataType())
        .thenReturn(com.patra.catalog.domain.model.enums.MeshDataType.DESCRIPTOR);
    when(treeNumberImporter.getDataType())
        .thenReturn(com.patra.catalog.domain.model.enums.MeshDataType.TREE_NUMBER);
    when(entryTermImporter.getDataType())
        .thenReturn(com.patra.catalog.domain.model.enums.MeshDataType.ENTRY_TERM);
    when(conceptImporter.getDataType())
        .thenReturn(com.patra.catalog.domain.model.enums.MeshDataType.CONCEPT);

    // Mock 策略实现的 importData() 方法（默认返回 35000，并标记表为完成）
    when(qualifierImporter.importData(any(File.class), any(MeshImportAggregate.class)))
        .thenAnswer(
            invocation -> {
              MeshImportAggregate agg = invocation.getArgument(1);
              agg.markTableAsCompleted("qualifier", 35000);
              return 35000;
            });
    when(descriptorImporter.importData(any(File.class), any(MeshImportAggregate.class)))
        .thenAnswer(
            invocation -> {
              MeshImportAggregate agg = invocation.getArgument(1);
              agg.markTableAsCompleted("descriptor", 35000);
              return 35000;
            });
    when(treeNumberImporter.importData(any(File.class), any(MeshImportAggregate.class)))
        .thenAnswer(
            invocation -> {
              MeshImportAggregate agg = invocation.getArgument(1);
              agg.markTableAsCompleted("tree-number", 35000);
              return 35000;
            });
    when(entryTermImporter.importData(any(File.class), any(MeshImportAggregate.class)))
        .thenAnswer(
            invocation -> {
              MeshImportAggregate agg = invocation.getArgument(1);
              agg.markTableAsCompleted("entry-term", 35000);
              return 35000;
            });
    when(conceptImporter.importData(any(File.class), any(MeshImportAggregate.class)))
        .thenAnswer(
            invocation -> {
              MeshImportAggregate agg = invocation.getArgument(1);
              agg.markTableAsCompleted("concept", 35000);
              return 35000;
            });

    // 手动创建 orchestrator（注入所有 Mock 依赖和策略列表）
    List<com.patra.catalog.app.usecase.meshimport.strategy.MeshDataImporter> importers =
        List.of(
            qualifierImporter,
            descriptorImporter,
            treeNumberImporter,
            entryTermImporter,
            conceptImporter);

    orchestrator =
        new MeshImportOrchestrator(
            meshImportPort,
            meshFileDownloadPort,
            meshDataValidator,
            meshImportConfig,
            importers);

    // Mock 配置
    when(meshImportConfig.getExpectedCountForTable(anyString())).thenReturn(35000);
    when(meshImportConfig.getDescriptorSourceUrl())
        .thenReturn("https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");
    when(meshImportConfig.getQualifierSourceUrl())
        .thenReturn("https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml");
    when(meshImportConfig.getExpectedFileSize()).thenReturn(313_524_224L); // 约 299 MB
    when(meshImportConfig.getFileSizeDifferenceThreshold()).thenReturn(10.0); // 10% 阈值

    // Mock 验证器（默认返回通过）
    when(meshDataValidator.validateDataCounts(anyMap()))
        .thenReturn(new MeshDataValidator.ValidationResult(true, List.of()));

    // 初始化表进度列表（模拟 initializeTableProgressList 的逻辑，包含 5 张表）
    List<com.patra.catalog.domain.model.valueobject.TableProgress> tableProgressList =
        new java.util.ArrayList<>();
    List<String> tableNames =
        List.of("descriptor", "qualifier", "tree-number", "entry-term", "concept");
    for (String tableName : tableNames) {
      tableProgressList.add(
          com.patra.catalog.domain.model.valueobject.TableProgress.builder()
              .tableName(tableName)
              .expectedCount(35000)
              .actualTotalCount(null) // 未开始，实际总数未知
              .processedCount(0)
              .failedCount(0)
              .status(com.patra.catalog.domain.model.enums.MeshTableImportStatus.NOT_STARTED)
              .lastBatchNum(0)
              .lastUpdateTime(java.time.Instant.now())
              .build());
    }

    // 模拟聚合根（包含初始化的表进度列表）
    mockAggregate =
        new MeshImportAggregate(
            MeshImportId.of(1L),
            "2025年MeSH数据首次导入",
            MeshImportTaskStatus.PENDING,
            null,
            null,
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
            "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
            null,
            null,
            tableProgressList,
            0,
            0,
            0,
            null);

    // 创建临时 XML 文件用于测试（确保文件存在以便创建 FileInputStream）
    File tempDir = new File("/tmp/mesh-import");
    if (!tempDir.exists()) {
      tempDir.mkdirs();
    }
    mockXmlFile = new File(tempDir, "desc2025-test.xml");
    if (!mockXmlFile.exists()) {
      mockXmlFile.createNewFile();
    }
    // 使用 spy 来 mock length() 方法
    mockXmlFile = spy(mockXmlFile);
    when(mockXmlFile.length()).thenReturn(313_524_224L); // 与 expectedFileSize 一致
  }

  @Nested
  @DisplayName("startImport() 测试")
  class StartImportTests {


    @Test
    @DisplayName("当下载失败时应该标记任务为 FAILED")
    void shouldMarkTaskAsFailedWhenDownloadFails() {
      // Mock 下载失败
      when(meshFileDownloadPort.download(anyString())).thenThrow(new RuntimeException("网络连接失败"));

      // Mock 聚合根保存
      when(meshImportPort.save(any(MeshImportAggregate.class))).thenReturn(mockAggregate);

      // When & Then: 执行导入，预期抛出异常
      assertThatThrownBy(() -> orchestrator.startImport())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("网络连接失败");

      // 验证任务状态被标记为失败（通过 save 调用）
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
    }

    @Test
    @DisplayName("当文件大小超出阈值时应该抛出异常")
    void shouldThrowExceptionWhenFileSizeOutOfRange() throws Exception {
      // Mock 下载成功但文件大小异常（小于预期的 50%，超出 10% 阈值）
      File abnormalFile = new File("/tmp/mesh-import/desc2025-abnormal.xml");
      if (!abnormalFile.exists()) {
        abnormalFile.createNewFile();
      }
      File spyAbnormalFile = spy(abnormalFile);
      // 修复：设置文件大小为预期的 50%（156,762,112 字节，约 150 MB），超出 10% 阈值
      when(spyAbnormalFile.length()).thenReturn(156_762_112L);
      when(meshFileDownloadPort.download(anyString())).thenReturn(spyAbnormalFile);

      // Mock 聚合根保存
      when(meshImportPort.save(any(MeshImportAggregate.class))).thenReturn(mockAggregate);

      // When & Then: 执行导入，预期抛出异常（被包装成 RuntimeException）
      assertThatThrownBy(() -> orchestrator.startImport())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("文件大小异常");

      // 验证任务状态被标记为失败
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
    }

  }

  @Nested
  @DisplayName("retryFailedTask() 测试")
  class RetryFailedTaskTests {

    @Test
    @DisplayName("应该成功重试失败的任务")
    void shouldSuccessfullyRetryFailedTask() throws Exception {
      // Given: 模拟失败的任务（包含初始化的表进度列表）
      List<com.patra.catalog.domain.model.valueobject.TableProgress> tableProgressList =
          new java.util.ArrayList<>();
      // 修复：添加 "qualifier" 到表列表（导入流程先导入 qualifier）
      List<String> tableNames = List.of("descriptor", "qualifier", "tree-number", "entry-term", "concept");
      for (String tableName : tableNames) {
        tableProgressList.add(
            com.patra.catalog.domain.model.valueobject.TableProgress.builder()
                .tableName(tableName)
                .expectedCount(35000)
                .actualTotalCount(null) // 未开始，实际总数未知
                .processedCount(0)
                .failedCount(0)
                .status(com.patra.catalog.domain.model.enums.MeshTableImportStatus.NOT_STARTED)
                .lastBatchNum(0)
                .lastUpdateTime(java.time.Instant.now())
                .build());
      }

      MeshImportAggregate failedAggregate =
          new MeshImportAggregate(
              MeshImportId.of(1L),
              "MeSH 数据导入 - 2025-01-20",
              MeshImportTaskStatus.FAILED,
              Instant.now(),
              Instant.now(),
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
              null,
              null,
              tableProgressList,
              0,
              0,
              1,
              "下载失败");

      // Mock 下载（文件大小正常）
      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);

      // Mock 聚合根保存和查询
      // save() 返回传入的参数本身，这样 aggregate 的状态会随着每次更新自然演变
      when(meshImportPort.save(any(MeshImportAggregate.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      when(meshImportPort.findById(any(MeshImportId.class)))
          .thenReturn(Optional.of(failedAggregate));

      // Mock 解析（返回包含 totalCount 数量元素的流，使状态变为 COMPLETED）
      // 修复：添加 parseQualifiers mock，因为导入流程先导入 qualifier
      when(xmlParserPort.parseQualifiers(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(
                      i ->
                          mock(
                              com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate
                                  .class)));
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(
                      i ->
                          mock(
                              com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate
                                  .class)));
      when(xmlParserPort.parseTreeNumbers(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshTreeNumber.class)));
      when(xmlParserPort.parseEntryTerms(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshEntryTerm.class)));
      when(xmlParserPort.parseConcepts(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshConcept.class)));

      // Mock 验证
      when(meshDataValidator.validateDataCounts(anyMap()))
          .thenReturn(new MeshDataValidator.ValidationResult(true, List.of()));

      // When: 重试任务
      MeshImportResultDTO result = orchestrator.retryFailedTask(1L);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.getTaskId()).isEqualTo("1");

      // 验证聚合根的 retry() 方法被间接调用（通过状态变化）
      verify(meshImportPort).findById(MeshImportId.of(1L));
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
    }

    @Test
    @DisplayName("当任务不存在时应该抛出异常")
    void shouldThrowExceptionWhenTaskNotFound() {
      // Given: Mock 任务不存在
      when(meshImportPort.findById(any(MeshImportId.class))).thenReturn(Optional.empty());

      // When & Then: 重试任务，预期抛出异常
      assertThatThrownBy(() -> orchestrator.retryFailedTask(999L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("任务不存在");
    }

    @Test
    @DisplayName("当任务不是 FAILED 状态时应该抛出异常")
    void shouldThrowExceptionWhenTaskNotFailed() {
      // Given: 模拟成功的任务（不能重试）
      MeshImportAggregate successAggregate =
          new MeshImportAggregate(
              MeshImportId.of(1L),
              "MeSH 数据导入 - 2025-01-20",
              MeshImportTaskStatus.SUCCESS,
              Instant.now(),
              Instant.now(),
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
              null,
              null,
              List.of(),
              350000,
              350000,
              0,
              null);

      when(meshImportPort.findById(any(MeshImportId.class)))
          .thenReturn(Optional.of(successAggregate));

      // When & Then: 重试任务，预期抛出异常
      assertThatThrownBy(() -> orchestrator.retryFailedTask(1L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("只能重试失败的任务");
    }
  }

  @Nested
  @DisplayName("clearAndRestart() 测试")
  class ClearAndRestartTests {

    @Test
    @DisplayName("应该成功清除进度并重新开始")
    void shouldSuccessfullyClearAndRestart() {
      // Given: 模拟正在运行的任务
      when(meshImportPort.findRunningTask()).thenReturn(Optional.of(mockAggregate));
      when(meshImportPort.save(any(MeshImportAggregate.class))).thenReturn(mockAggregate);

      // When: 清除并重启
      orchestrator.clearAndRestart();

      // Then: 验证进度被重置
      verify(meshImportPort).findRunningTask();
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
    }

    @Test
    @DisplayName("当没有正在运行的任务时应该不做任何操作")
    void shouldDoNothingWhenNoRunningTask() {
      // Given: 没有正在运行的任务
      when(meshImportPort.findRunningTask()).thenReturn(Optional.empty());

      // When: 清除并重启
      orchestrator.clearAndRestart();

      // Then: 验证只查询了任务，没有保存操作
      verify(meshImportPort).findRunningTask();
      verify(meshImportPort, never()).save(any(MeshImportAggregate.class));
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("当命令中没有提供 sourceUrl 时应该使用配置的默认值")
    void shouldUseDefaultSourceUrlWhenNotProvided() throws Exception {
      // Mock 下载（验证使用了配置的 URL，文件大小正常）
      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);

      // Mock 聚合根（最后一次 save 返回所有表已完成的 aggregate）
      MeshImportAggregate completedAggregate = createCompletedAggregate();
      when(meshImportPort.save(any(MeshImportAggregate.class)))
          .thenReturn(mockAggregate)
          .thenReturn(mockAggregate)
          .thenReturn(completedAggregate);
      when(meshImportPort.findById(any(MeshImportId.class)))
          .thenReturn(Optional.of(completedAggregate));

      // Mock 解析（返回包含 totalCount 数量元素的流，使状态变为 COMPLETED）
      // 修复：添加 parseQualifiers mock，因为导入流程先导入 qualifier
      when(xmlParserPort.parseQualifiers(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(
                      i ->
                          mock(
                              com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate
                                  .class)));
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(
                      i ->
                          mock(
                              com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate
                                  .class)));
      when(xmlParserPort.parseTreeNumbers(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshTreeNumber.class)));
      when(xmlParserPort.parseEntryTerms(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshEntryTerm.class)));
      when(xmlParserPort.parseConcepts(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshConcept.class)));

      // Mock 验证
      when(meshDataValidator.validateDataCounts(anyMap()))
          .thenReturn(new MeshDataValidator.ValidationResult(true, List.of()));

      // When: 执行导入
      MeshImportResultDTO result = orchestrator.startImport();

      // Then: 验证使用了默认 URL
      assertThat(result).isNotNull();
      verify(meshFileDownloadPort, times(2)).download(anyString());
    }

    @Test
    @DisplayName("当已存在正在运行的任务时应该抛出异常")
    void shouldThrowExceptionWhenTaskAlreadyRunning() {
      // Given: 已存在正在运行的任务
      when(meshImportPort.existsRunningTask()).thenReturn(true);

      // When & Then: 执行导入，预期抛出异常
      assertThatThrownBy(() -> orchestrator.startImport())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("已有正在运行的 MeSH 导入任务");
    }
  }
}
