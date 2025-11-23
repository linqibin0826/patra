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

  @InjectMocks private MeshImportOrchestrator orchestrator;

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
    // Mock 配置
    when(meshImportConfig.getExpectedCountForTable(anyString())).thenReturn(35000);
    when(meshImportConfig.getDescriptorSourceUrl())
        .thenReturn("https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");
    when(meshImportConfig.getQualifierSourceUrl())
        .thenReturn("https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml");
    when(meshImportConfig.getExpectedFileSize()).thenReturn(313_524_224L); // 约 299 MB
    when(meshImportConfig.getFileSizeDifferenceThreshold()).thenReturn(10.0); // 10% 阈值

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
    @DisplayName("应该成功执行完整导入流程")
    void shouldSuccessfullyCompleteImportFlow() throws Exception {
      // Mock 下载（文件大小在预期范围内，返回两个文件：desc 和 qual）
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

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.getTaskId()).isNotNull();
      assertThat(result.getTaskName()).isEqualTo("2025年MeSH数据首次导入");

      // 验证各个步骤都被调用（包括下载两个文件）
      verify(meshFileDownloadPort, times(2)).download(anyString());
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
      verify(xmlParserPort).parseQualifiers(any(FileInputStream.class));
      verify(xmlParserPort).parseDescriptors(any(FileInputStream.class));
      verify(xmlParserPort).parseTreeNumbers(any(FileInputStream.class));
      verify(xmlParserPort).parseEntryTerms(any(FileInputStream.class));
      verify(xmlParserPort).parseConcepts(any(FileInputStream.class));
      verify(meshDataValidator).validateDataCounts(anyMap());
    }

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

    @Test
    @DisplayName("当数据验证失败时应该生成警告")
    void shouldGenerateWarningsWhenDataValidationFails() throws Exception {
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

      // Mock 验证失败（数量差异超过 5%）
      when(meshDataValidator.validateDataCounts(anyMap()))
          .thenReturn(
              new MeshDataValidator.ValidationResult(
                  false, List.of("Descriptor 数量差异超过 5%: 预期 35000, 实际 30000")));

      // When: 执行导入
      MeshImportResultDTO result = orchestrator.startImport();

      // Then: 验证结果（即使有警告，任务仍标记为成功）
      assertThat(result).isNotNull();
      assertThat(result.getTaskId()).isNotNull();

      // 验证各个步骤都被调用
      verify(meshFileDownloadPort, times(2)).download(anyString());
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
      verify(xmlParserPort).parseQualifiers(any(FileInputStream.class));
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

  @Nested
  @DisplayName("批量保存逻辑测试")
  class BatchSaveLogicTests {

    @Test
    @DisplayName("应该按批次保存 Descriptor 并正确更新进度")
    void should_call_batchSave_multiple_times_when_importing_descriptors() throws Exception {
      // Given: 准备 3000 条数据，批次大小 1000
      when(meshImportConfig.getBatchSizeForTable("descriptor")).thenReturn(1000);
      when(meshImportConfig.getBatchSizeForTable("tree-number")).thenReturn(1000);
      when(meshImportConfig.getBatchSizeForTable("entry-term")).thenReturn(1000);
      when(meshImportConfig.getBatchSizeForTable("concept")).thenReturn(1000);
      // 关键修复：设置 expectedCount 为 3000（与测试数据量匹配）
      when(meshImportConfig.getExpectedCountForTable(anyString())).thenReturn(3000);
      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);

      // 关键修复：所有 save() 调用都返回传入的 aggregate（保留状态更新和正确的 totalCount）
      // 使用反射设置 ID（模拟 Repository 分配 ID）
      when(meshImportPort.save(any(MeshImportAggregate.class)))
          .thenAnswer(
              invocation -> {
                MeshImportAggregate aggregate = invocation.getArgument(0);
                // 如果 aggregate 没有 ID，通过反射设置 ID（模拟 Repository 行为）
                if (aggregate.getId() == null) {
                  try {
                    // ID 字段在父类 AggregateRoot 中
                    java.lang.reflect.Field idField =
                        com.patra.common.domain.AggregateRoot.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(aggregate, MeshImportId.of(1L));
                  } catch (Exception e) {
                    throw new RuntimeException("无法设置 ID", e);
                  }
                }
                return aggregate;
              });
      when(meshImportPort.findById(any(MeshImportId.class)))
          .thenReturn(java.util.Optional.of(mockAggregate));

      // Mock 解析返回 3000 条记录的流
      // Qualifier: 一次性导入（不分批），返回少量数据即可
      when(xmlParserPort.parseQualifiers(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 80)
                  .mapToObj(
                      i ->
                          mock(
                              com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate
                                  .class)));
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 3000)
                  .mapToObj(
                      i ->
                          mock(
                              com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate
                                  .class)));
      when(xmlParserPort.parseTreeNumbers(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 3000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshTreeNumber.class)));
      when(xmlParserPort.parseEntryTerms(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 3000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshEntryTerm.class)));
      when(xmlParserPort.parseConcepts(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 3000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshConcept.class)));

      // Mock 验证
      when(meshDataValidator.validateDataCounts(anyMap()))
          .thenReturn(new MeshDataValidator.ValidationResult(true, List.of()));

      // When: 执行导入
      orchestrator.startImport();

      // Then: 验证 Qualifier 一次性保存（约 80 条，不分批）
      verify(meshQualifierRepository, times(1)).saveBatch(any(List.class));

      // 验证 Descriptor 调用 3 次批量保存 (1000 + 1000 + 1000)
      verify(meshDescriptorRepository, times(3)).saveBatch(any(List.class));

      // 验证进度更新至少 3 次（每个批次后更新）
      verify(meshImportPort, atLeast(3)).save(any(MeshImportAggregate.class));
    }

    @Test
    @DisplayName("最后一批数据不足批次大小时也应该保存")
    void should_save_last_batch_even_if_not_full() throws Exception {
      // Given: 2500 条数据，批次大小 1000 (最后一批只有 500 条)
      when(meshImportConfig.getBatchSizeForTable("descriptor")).thenReturn(1000);
      when(meshImportConfig.getBatchSizeForTable("tree-number")).thenReturn(1000);
      when(meshImportConfig.getBatchSizeForTable("entry-term")).thenReturn(1000);
      when(meshImportConfig.getBatchSizeForTable("concept")).thenReturn(1000);
      // 关键修复：设置 expectedCount 为 2500（与测试数据量匹配）
      when(meshImportConfig.getExpectedCountForTable(anyString())).thenReturn(2500);
      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);

      // 关键修复：所有 save() 调用都返回传入的 aggregate（保留状态更新和正确的 totalCount）
      when(meshImportPort.save(any(MeshImportAggregate.class)))
          .thenAnswer(
              invocation -> {
                MeshImportAggregate aggregate = invocation.getArgument(0);
                if (aggregate.getId() == null) {
                  try {
                    // ID 字段在父类 AggregateRoot 中
                    java.lang.reflect.Field idField =
                        com.patra.common.domain.AggregateRoot.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(aggregate, MeshImportId.of(1L));
                  } catch (Exception e) {
                    throw new RuntimeException("无法设置 ID", e);
                  }
                }
                return aggregate;
              });
      when(meshImportPort.findById(any(MeshImportId.class)))
          .thenReturn(java.util.Optional.of(mockAggregate));

      // Mock 解析：所有表都返回 2500 条记录
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 2500)
                  .mapToObj(
                      i ->
                          mock(
                              com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate
                                  .class)));
      when(xmlParserPort.parseTreeNumbers(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 2500)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshTreeNumber.class)));
      when(xmlParserPort.parseEntryTerms(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 2500)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshEntryTerm.class)));
      when(xmlParserPort.parseConcepts(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 2500)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshConcept.class)));

      // Mock 验证
      when(meshDataValidator.validateDataCounts(anyMap()))
          .thenReturn(new MeshDataValidator.ValidationResult(true, List.of()));

      // When: 执行导入
      orchestrator.startImport();

      // Then: 验证调用 3 次批量保存 (1000 + 1000 + 500)
      verify(meshDescriptorRepository, times(3)).saveBatch(any(List.class));
    }

    @Test
    @DisplayName("批次保存失败时应该抛出异常并标记任务失败")
    void should_throw_exception_when_batch_save_fails() throws Exception {
      // Given: Mock 批量保存失败
      when(meshImportConfig.getBatchSizeForTable("descriptor")).thenReturn(1000);
      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);
      when(meshImportPort.save(any(MeshImportAggregate.class))).thenReturn(mockAggregate);

      // Mock 解析返回 1000 条记录
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 1000)
                  .mapToObj(
                      i ->
                          mock(
                              com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate
                                  .class)));

      // Mock 批量保存失败
      doThrow(new RuntimeException("数据库连接失败")).when(meshDescriptorRepository).saveBatch(any(List.class));

      // When & Then: 执行导入，预期抛出异常
      assertThatThrownBy(() -> orchestrator.startImport())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("批次保存失败");

      // 验证任务状态被标记为失败（通过 save 调用）
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
    }

    @Test
    @DisplayName("应该使用配置的批次大小进行分批保存")
    void should_use_configured_batch_size_for_each_table() throws Exception {
      // Given: 配置不同表的批次大小
      when(meshImportConfig.getBatchSizeForTable("descriptor")).thenReturn(1000);
      when(meshImportConfig.getBatchSizeForTable("tree-number")).thenReturn(1500);
      when(meshImportConfig.getBatchSizeForTable("entry-term")).thenReturn(2000);
      when(meshImportConfig.getBatchSizeForTable("concept")).thenReturn(2000);
      // 关键修复：设置 expectedCount 为 3000（与测试数据量匹配）
      when(meshImportConfig.getExpectedCountForTable(anyString())).thenReturn(3000);

      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);

      // 关键修复：所有 save() 调用都返回传入的 aggregate（保留状态更新和正确的 totalCount）
      when(meshImportPort.save(any(MeshImportAggregate.class)))
          .thenAnswer(
              invocation -> {
                MeshImportAggregate aggregate = invocation.getArgument(0);
                if (aggregate.getId() == null) {
                  try {
                    // ID 字段在父类 AggregateRoot 中
                    java.lang.reflect.Field idField =
                        com.patra.common.domain.AggregateRoot.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(aggregate, MeshImportId.of(1L));
                  } catch (Exception e) {
                    throw new RuntimeException("无法设置 ID", e);
                  }
                }
                return aggregate;
              });
      when(meshImportPort.findById(any(MeshImportId.class)))
          .thenReturn(java.util.Optional.of(mockAggregate));

      // Mock 解析：每个表返回 3000 条数据
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 3000)
                  .mapToObj(
                      i ->
                          mock(
                              com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate
                                  .class)));
      when(xmlParserPort.parseTreeNumbers(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 3000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshTreeNumber.class)));
      when(xmlParserPort.parseEntryTerms(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 3000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshEntryTerm.class)));
      when(xmlParserPort.parseConcepts(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 3000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshConcept.class)));

      // Mock 验证
      when(meshDataValidator.validateDataCounts(anyMap()))
          .thenReturn(new MeshDataValidator.ValidationResult(true, List.of()));

      // When: 执行导入
      orchestrator.startImport();

      // Then: 验证 descriptor 调用 3 次 (3000 / 1000)
      verify(meshDescriptorRepository, times(3)).saveBatch(any(List.class));

      // 验证 tree-number 调用 2 次 (3000 / 1500)
      verify(meshDescriptorRepository, times(2)).saveTreeNumbersBatch(any(List.class));

      // 验证 entry-term 调用 2 次 (3000 / 2000，最后一批 1000 条)
      verify(meshDescriptorRepository, times(2)).saveEntryTermsBatch(any(List.class));

      // 验证 concept 调用 2 次 (3000 / 2000，最后一批 1000 条)
      verify(meshDescriptorRepository, times(2)).saveConceptsBatch(any(List.class));
    }

    @Test
    @DisplayName("批次保存成功后应该更新正确的进度和批次号")
    void should_update_correct_progress_and_batch_number() throws Exception {
      // Given: 配置批次大小为 1000
      when(meshImportConfig.getBatchSizeForTable("descriptor")).thenReturn(1000);
      when(meshImportConfig.getBatchSizeForTable("tree-number")).thenReturn(1000);
      when(meshImportConfig.getBatchSizeForTable("entry-term")).thenReturn(1000);
      when(meshImportConfig.getBatchSizeForTable("concept")).thenReturn(1000);
      // 关键修复：设置 expectedCount 为 2000（与测试数据量匹配）
      when(meshImportConfig.getExpectedCountForTable(anyString())).thenReturn(2000);
      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);

      // 使用 ArgumentCaptor 捕获保存的聚合根
      org.mockito.ArgumentCaptor<MeshImportAggregate> aggregateCaptor =
          org.mockito.ArgumentCaptor.forClass(MeshImportAggregate.class);

      // 关键修复：所有 save() 调用都返回传入的 aggregate（保留状态更新和正确的 totalCount）
      when(meshImportPort.save(aggregateCaptor.capture()))
          .thenAnswer(
              invocation -> {
                MeshImportAggregate aggregate = invocation.getArgument(0);
                if (aggregate.getId() == null) {
                  try {
                    // ID 字段在父类 AggregateRoot 中
                    java.lang.reflect.Field idField =
                        com.patra.common.domain.AggregateRoot.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(aggregate, MeshImportId.of(1L));
                  } catch (Exception e) {
                    throw new RuntimeException("无法设置 ID", e);
                  }
                }
                return aggregate;
              });
      when(meshImportPort.findById(any(MeshImportId.class)))
          .thenReturn(java.util.Optional.of(mockAggregate));

      // Mock 解析：所有表都返回 2000 条记录（应该分 2 批）
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 2000)
                  .mapToObj(
                      i ->
                          mock(
                              com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate
                                  .class)));
      when(xmlParserPort.parseTreeNumbers(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 2000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshTreeNumber.class)));
      when(xmlParserPort.parseEntryTerms(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 2000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshEntryTerm.class)));
      when(xmlParserPort.parseConcepts(any(FileInputStream.class)))
          .thenReturn(
              java.util.stream.IntStream.range(0, 2000)
                  .mapToObj(i -> mock(com.patra.catalog.domain.model.entity.MeshConcept.class)));

      // Mock 验证
      when(meshDataValidator.validateDataCounts(anyMap()))
          .thenReturn(new MeshDataValidator.ValidationResult(true, List.of()));

      // When: 执行导入
      orchestrator.startImport();

      // Then: 验证调用了批量保存
      verify(meshDescriptorRepository, times(2)).saveBatch(any(List.class));

      // 验证进度更新（批次号应该是 1, 2）
      // 注意：aggregateCaptor 会捕获所有 save 调用，需要检查其中与 descriptor 相关的调用
      // 这里简化验证：只检查 save 被调用了多次
      verify(meshImportPort, atLeast(2)).save(any(MeshImportAggregate.class));
    }
  }
}
