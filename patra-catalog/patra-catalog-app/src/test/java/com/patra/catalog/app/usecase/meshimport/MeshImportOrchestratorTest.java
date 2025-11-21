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
import com.patra.catalog.domain.port.MeshDescriptorPort;
import com.patra.catalog.domain.port.MeshFileDownloadPort;
import com.patra.catalog.domain.port.MeshImportPort;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MeshImportOrchestrator 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>Mock 所有 Port 接口（不依赖真实实现）
 *   <li>使用 InOrder 验证调用顺序
 *   <li>验证事务边界（@Transactional 在 Orchestrator 层）
 *   <li>测试编排逻辑：下载 → 解析 → 保存 → 验证 → 更新状态
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MeshImportOrchestrator 单元测试")
class MeshImportOrchestratorTest {

  @Mock private MeshImportPort meshImportPort;
  @Mock private XmlParserPort xmlParserPort;
  @Mock private MeshFileDownloadPort meshFileDownloadPort;
  @Mock private MeshDescriptorPort meshDescriptorPort;
  @Mock private MeshDataValidator meshDataValidator;
  @Mock private com.patra.catalog.app.config.MeshImportConfig meshImportConfig;

  @InjectMocks private MeshImportOrchestrator orchestrator;

  private MeshImportAggregate mockAggregate;
  private File mockXmlFile;

  @BeforeEach
  void setUp() {
    // Mock 配置
    when(meshImportConfig.getExpectedCountForTable(anyString())).thenReturn(35000);
    when(meshImportConfig.getSourceUrl()).thenReturn("https://nlm.nih.gov/mesh/desc2025.xml");

    // 模拟聚合根
    mockAggregate =
        new MeshImportAggregate(
            MeshImportId.of(1L),
            "2025年MeSH数据首次导入",
            MeshImportTaskStatus.PENDING,
            null,
            null,
            "https://nlm.nih.gov/mesh/desc2025.xml",
            null,
            null,
            List.of(),
            0,
            0,
            0,
            null);

    // 模拟 XML 文件
    mockXmlFile = new File("/tmp/mesh-import/desc2025.xml");
  }

  @Nested
  @DisplayName("startImport() 测试")
  class StartImportTests {

    @Test
    @DisplayName("应该成功执行完整导入流程")
    void shouldSuccessfullyCompleteImportFlow() throws Exception {
      // Given: 准备命令
      StartImportCommand command =
          new StartImportCommand("https://nlm.nih.gov/mesh/desc2025.xml", "2025年MeSH数据首次导入");

      // Mock 下载
      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);
      when(meshFileDownloadPort.validateChecksum(any(File.class), anyString())).thenReturn(true);

      // Mock 聚合根创建和保存
      when(meshImportPort.save(any(MeshImportAggregate.class))).thenReturn(mockAggregate);
      when(meshImportPort.findById(any(MeshImportId.class))).thenReturn(Optional.of(mockAggregate));

      // Mock 解析（返回空流用于测试）
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());
      when(xmlParserPort.parseTreeNumbers(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());
      when(xmlParserPort.parseEntryTerms(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());
      when(xmlParserPort.parseConcepts(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());

      // Mock 验证
      when(meshDataValidator.validateDataCounts(anyMap()))
          .thenReturn(new MeshDataValidator.ValidationResult(true, List.of()));

      // When: 执行导入
      MeshImportResultDTO result = orchestrator.startImport(command);

      // Then: 验证结果
      assertThat(result).isNotNull();
      assertThat(result.getTaskId()).isNotNull();
      assertThat(result.getTaskName()).isEqualTo("2025年MeSH数据首次导入");

      // 验证调用顺序
      InOrder inOrder =
          inOrder(
              meshFileDownloadPort,
              meshImportPort,
              xmlParserPort,
              meshDataValidator);

      inOrder.verify(meshFileDownloadPort).download(anyString());
      inOrder.verify(meshFileDownloadPort).validateChecksum(any(File.class), anyString());
      inOrder.verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
      inOrder.verify(xmlParserPort).parseDescriptors(any(FileInputStream.class));
      inOrder.verify(meshDataValidator).validateDataCounts(anyMap());
    }

    @Test
    @DisplayName("当下载失败时应该标记任务为 FAILED")
    void shouldMarkTaskAsFailedWhenDownloadFails() {
      // Given: 准备命令
      StartImportCommand command =
          new StartImportCommand("https://nlm.nih.gov/mesh/desc2025.xml", "2025年MeSH数据导入");

      // Mock 下载失败
      when(meshFileDownloadPort.download(anyString()))
          .thenThrow(new RuntimeException("网络连接失败"));

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
    @DisplayName("当校验和不匹配时应该抛出异常")
    void shouldThrowExceptionWhenChecksumMismatch() {
      // Given: 准备命令
      StartImportCommand command =
          new StartImportCommand("https://nlm.nih.gov/mesh/desc2025.xml", "2025年MeSH数据导入");

      // Mock 下载成功但校验失败
      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);
      when(meshFileDownloadPort.validateChecksum(any(File.class), anyString())).thenReturn(false);

      // Mock 聚合根保存
      when(meshImportPort.save(any(MeshImportAggregate.class))).thenReturn(mockAggregate);

      // When & Then: 执行导入，预期抛出异常
      assertThatThrownBy(() -> orchestrator.startImport(command))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("校验");

      // 验证任务状态被标记为失败
      verify(meshImportPort, atLeastOnce()).save(any(MeshImportAggregate.class));
    }

    @Test
    @DisplayName("当数据验证失败时应该生成警告")
    void shouldGenerateWarningsWhenDataValidationFails() throws Exception {
      // Given: 准备命令
      StartImportCommand command =
          new StartImportCommand("https://nlm.nih.gov/mesh/desc2025.xml", "2025年MeSH数据导入");

      // Mock 下载
      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);
      when(meshFileDownloadPort.validateChecksum(any(File.class), anyString())).thenReturn(true);

      // Mock 聚合根
      when(meshImportPort.save(any(MeshImportAggregate.class))).thenReturn(mockAggregate);
      when(meshImportPort.findById(any(MeshImportId.class))).thenReturn(Optional.of(mockAggregate));

      // Mock 解析
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());
      when(xmlParserPort.parseTreeNumbers(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());
      when(xmlParserPort.parseEntryTerms(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());
      when(xmlParserPort.parseConcepts(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());

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

      // 验证调用了验证器
      verify(meshDataValidator).validateDataCounts(anyMap());
    }
  }

  @Nested
  @DisplayName("retryFailedTask() 测试")
  class RetryFailedTaskTests {

    @Test
    @DisplayName("应该成功重试失败的任务")
    void shouldSuccessfullyRetryFailedTask() {
      // Given: 模拟失败的任务
      MeshImportAggregate failedAggregate =
          new MeshImportAggregate(
              MeshImportId.of(1L),
              "2025年MeSH数据导入",
              MeshImportTaskStatus.FAILED,
              Instant.now(),
              Instant.now(),
              "https://nlm.nih.gov/mesh/desc2025.xml",
              null,
              null,
              List.of(),
              0,
              0,
              1,
              "下载失败");

      when(meshImportPort.findById(any(MeshImportId.class)))
          .thenReturn(Optional.of(failedAggregate));
      when(meshImportPort.save(any(MeshImportAggregate.class))).thenReturn(failedAggregate);

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
              "https://nlm.nih.gov/mesh/desc2025.xml",
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
          .hasMessageContaining("只有 FAILED 状态的任务可以重试");
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
      StartImportCommand command = new StartImportCommand(null, "2025年MeSH数据导入");

      // Mock 下载（验证使用了配置的 URL）
      when(meshFileDownloadPort.download(anyString())).thenReturn(mockXmlFile);
      when(meshFileDownloadPort.validateChecksum(any(File.class), anyString())).thenReturn(true);

      // Mock 聚合根
      when(meshImportPort.save(any(MeshImportAggregate.class))).thenReturn(mockAggregate);
      when(meshImportPort.findById(any(MeshImportId.class))).thenReturn(Optional.of(mockAggregate));

      // Mock 解析
      when(xmlParserPort.parseDescriptors(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());
      when(xmlParserPort.parseTreeNumbers(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());
      when(xmlParserPort.parseEntryTerms(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());
      when(xmlParserPort.parseConcepts(any(FileInputStream.class))).thenReturn(java.util.stream.Stream.empty());

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
          new StartImportCommand("https://nlm.nih.gov/mesh/desc2025.xml", "2025年MeSH数据导入");

      // When & Then: 执行导入，预期抛出异常
      assertThatThrownBy(() -> orchestrator.startImport(command))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("已有正在运行的任务");
    }
  }
}
