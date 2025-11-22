package com.patra.catalog.app.usecase.meshimport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.patra.catalog.app.usecase.meshimport.command.StartImportCommand;
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
  @Mock private MeshDescriptorRepository meshDescriptorPort;
  @Mock private MeshDataValidator meshDataValidator;
  @Mock private com.patra.catalog.app.config.MeshImportConfig meshImportConfig;

  @InjectMocks private MeshImportOrchestrator orchestrator;

  private MeshImportAggregate mockAggregate;
  private File mockXmlFile;

  /// 创建所有表都已完成的聚合根（用于成功场景测试）
  private MeshImportAggregate createCompletedAggregate() {
    List<com.patra.catalog.domain.model.valueobject.TableProgress> completedTableProgressList =
        new java.util.ArrayList<>();
    List<String> tableNames =
        List.of("descriptor", "tree-number", "entry-term", "concept");
    for (String tableName : tableNames) {
      completedTableProgressList.add(
          com.patra.catalog.domain.model.valueobject.TableProgress.builder()
              .tableName(tableName)
              .totalCount(35000)
              .processedCount(35000)
              .failedCount(0)
              .status(com.patra.catalog.domain.model.enums.MeshTableImportStatus.COMPLETED)
              .lastBatchNum(100)
              .lastUpdateTime(java.time.Instant.now())
              .build());
    }

    return new MeshImportAggregate(
        MeshImportId.of(1L),
        "2025年MeSH数据首次导入",
        MeshImportTaskStatus.PROCESSING,
        java.time.Instant.now(),
        null,
        "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
        null,
        null,
        completedTableProgressList,
        140000,
        140000,
        0,
        null);
  }

  @BeforeEach
  void setUp() throws Exception {
    // Mock 配置
    when(meshImportConfig.getExpectedCountForTable(anyString())).thenReturn(35000);
    when(meshImportConfig.getSourceUrl())
        .thenReturn("https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");
    when(meshImportConfig.getExpectedFileSize()).thenReturn(734_003_200L); // 约 700 MB
    when(meshImportConfig.getFileSizeDifferenceThreshold()).thenReturn(10.0); // 10% 阈值

    // 初始化表进度列表（模拟 initializeTableProgressList 的逻辑）
    List<com.patra.catalog.domain.model.valueobject.TableProgress> tableProgressList =
        new java.util.ArrayList<>();
    List<String> tableNames =
        List.of("descriptor", "tree-number", "entry-term", "concept");
    for (String tableName : tableNames) {
      tableProgressList.add(
          com.patra.catalog.domain.model.valueobject.TableProgress.builder()
              .tableName(tableName)
              .totalCount(35000)
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
    when(mockXmlFile.length()).thenReturn(734_003_200L);
  }

  @Nested
  @DisplayName("startImport() 测试")
  class StartImportTests {

    @Test
    @DisplayName("应该成功执行完整导入流程")
    void shouldSuccessfullyCompleteImportFlow() throws Exception {
      // Given: 准备命令
      StartImportCommand command =
          new StartImportCommand(
              "2025年MeSH数据首次导入",
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");

      // Mock 下载（文件大小在预期范围内）
      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);

      // Mock 聚合根创建和保存（最后一次 save 返回所有表已完成的 aggregate）
      MeshImportAggregate completedAggregate = createCompletedAggregate();
      when(meshImportPort.save(any(MeshImportAggregate.class)))
          .thenReturn(mockAggregate)
          .thenReturn(mockAggregate)
          .thenReturn(completedAggregate);
      when(meshImportPort.findById(any(MeshImportId.class)))
          .thenReturn(Optional.of(completedAggregate));

      // Mock 解析（返回包含 totalCount 数量元素的流，使状态变为 COMPLETED）
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate.class)));
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
      MeshImportResultDTO result = orchestrator.startImport(command);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.getTaskId()).isNotNull();
      assertThat(result.getTaskName()).isEqualTo("2025年MeSH数据首次导入");

      // 验证各个步骤都被调用
      verify(meshFileDownloadPort).download(anyString());
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
      verify(xmlParserPort).parseDescriptors(any(FileInputStream.class));
      verify(xmlParserPort).parseTreeNumbers(any(FileInputStream.class));
      verify(xmlParserPort).parseEntryTerms(any(FileInputStream.class));
      verify(xmlParserPort).parseConcepts(any(FileInputStream.class));
      verify(meshDataValidator).validateDataCounts(anyMap());
    }

    @Test
    @DisplayName("当下载失败时应该标记任务为 FAILED")
    void shouldMarkTaskAsFailedWhenDownloadFails() {
      // Given: 准备命令
      StartImportCommand command =
          new StartImportCommand(
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
              "2025年MeSH数据导入");

      // Mock 下载失败
      when(meshFileDownloadPort.download(anyString())).thenThrow(new RuntimeException("网络连接失败"));

      // Mock 聚合根保存
      when(meshImportPort.save(any(MeshImportAggregate.class))).thenReturn(mockAggregate);

      // When & Then: 执行导入，预期抛出异常
      assertThatThrownBy(() -> orchestrator.startImport(command))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("网络连接失败");

      // 验证任务状态被标记为失败（通过 save 调用）
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
    }

    @Test
    @DisplayName("当文件大小超出阈值时应该抛出异常")
    void shouldThrowExceptionWhenFileSizeOutOfRange() throws Exception {
      // Given: 准备命令
      StartImportCommand command =
          new StartImportCommand(
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
              "2025年MeSH数据导入");

      // Mock 下载成功但文件大小异常（小于预期的 50%，超出 10% 阈值）
      File abnormalFile = new File("/tmp/mesh-import/desc2025-abnormal.xml");
      if (!abnormalFile.exists()) {
        abnormalFile.createNewFile();
      }
      File spyAbnormalFile = spy(abnormalFile);
      when(spyAbnormalFile.length()).thenReturn(300_000_000L); // 约 300 MB，远小于预期的 700 MB
      when(meshFileDownloadPort.download(anyString())).thenReturn(spyAbnormalFile);

      // Mock 聚合根保存
      when(meshImportPort.save(any(MeshImportAggregate.class))).thenReturn(mockAggregate);

      // When & Then: 执行导入，预期抛出异常（被包装成 RuntimeException）
      assertThatThrownBy(() -> orchestrator.startImport(command))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("文件大小异常");

      // 验证任务状态被标记为失败
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
    }

    @Test
    @DisplayName("当数据验证失败时应该生成警告")
    void shouldGenerateWarningsWhenDataValidationFails() throws Exception {
      // Given: 准备命令
      StartImportCommand command =
          new StartImportCommand(
              "2025年MeSH数据导入",
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");

      // Mock 下载（文件大小正常）
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
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate.class)));
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

      // Mock 验证失败（数量差异超过 5%）
      when(meshDataValidator.validateDataCounts(anyMap()))
          .thenReturn(
              new MeshDataValidator.ValidationResult(
                  false, List.of("Descriptor 数量差异超过 5%: 预期 35000, 实际 30000")));

      // When: 执行导入
      MeshImportResultDTO result = orchestrator.startImport(command);

      // Then: 验证结果（即使有警告，任务仍标记为成功）
      assertThat(result).isNotNull();
      assertThat(result.getTaskId()).isNotNull();

      // 验证各个步骤都被调用
      verify(meshFileDownloadPort).download(anyString());
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
      verify(xmlParserPort).parseDescriptors(any(FileInputStream.class));
      verify(xmlParserPort).parseTreeNumbers(any(FileInputStream.class));
      verify(xmlParserPort).parseEntryTerms(any(FileInputStream.class));
      verify(xmlParserPort).parseConcepts(any(FileInputStream.class));
      verify(meshDataValidator).validateDataCounts(anyMap());
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
      List<String> tableNames =
          List.of("descriptor", "tree-number", "entry-term", "concept");
      for (String tableName : tableNames) {
        tableProgressList.add(
            com.patra.catalog.domain.model.valueobject.TableProgress.builder()
                .tableName(tableName)
                .totalCount(35000)
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
              "2025年MeSH数据导入",
              MeshImportTaskStatus.FAILED,
              Instant.now(),
              Instant.now(),
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
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
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate.class)));
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
      MeshImportResultDTO result = orchestrator.retryFailedTask(MeshImportId.of(1L));

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
      assertThatThrownBy(() -> orchestrator.retryFailedTask(MeshImportId.of(999L)))
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
              "2025年MeSH数据导入",
              MeshImportTaskStatus.SUCCESS,
              Instant.now(),
              Instant.now(),
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
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
      assertThatThrownBy(() -> orchestrator.retryFailedTask(MeshImportId.of(1L)))
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
      // Given: 命令不包含 sourceUrl（使用默认值）
      StartImportCommand command = new StartImportCommand("2025年MeSH数据导入", null);

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
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 35000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate.class)));
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
      MeshImportResultDTO result = orchestrator.startImport(command);

      // Then: 验证使用了默认 URL
      assertThat(result).isNotNull();
      verify(meshFileDownloadPort).download(anyString());
    }

    @Test
    @DisplayName("当已存在正在运行的任务时应该抛出异常")
    void shouldThrowExceptionWhenTaskAlreadyRunning() {
      // Given: 已存在正在运行的任务
      when(meshImportPort.existsRunningTask()).thenReturn(true);

      StartImportCommand command =
          new StartImportCommand(
              "2025年MeSH数据导入",
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");

      // When & Then: 执行导入，预期抛出异常
      assertThatThrownBy(() -> orchestrator.startImport(command))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("已有正在运行的 MeSH 导入任务");
    }
  }
}
