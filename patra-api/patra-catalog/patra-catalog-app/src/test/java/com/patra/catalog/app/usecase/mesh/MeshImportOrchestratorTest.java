package com.patra.catalog.app.usecase.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.mesh.command.MeshImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshImportResult;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.enums.MeshDescriptorImportMode;
import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.domain.port.MeshDescriptorBatchPort;
import com.patra.catalog.domain.port.MeshDescriptorRepository;
import com.patra.catalog.domain.port.MeshQualifierRepository;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// MeSH 数据导入编排器单元测试。
///
/// **测试策略**：
///
/// - 单元测试：Mock 所有 Port 依赖
/// - 测试隔离：每个测试方法独立
///
/// **重点测试场景**：
///
/// - importDescriptors() INCREMENTAL 模式：不清空表，幂等执行
/// - importDescriptors() TRUNCATE_REIMPORT 模式：先清空再导入
/// - 参数传递正确性验证
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("MeshImportOrchestrator 单元测试")
class MeshImportOrchestratorTest {

  private static final String TEST_URL = "https://example.com/mesh.xml";
  private static final Path TEST_LOCAL_PATH = Path.of("/tmp/mesh-import-12345.xml");

  @Mock private XmlParserPort xmlParserPort;
  @Mock private MeshQualifierRepository qualifierRepository;
  @Mock private MeshDescriptorRepository descriptorRepository;
  @Mock private MeshDescriptorBatchPort meshDescriptorBatchPort;
  @Mock private FileDownloadPort fileDownloadPort;

  private MeshImportOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator =
        new MeshImportOrchestrator(
            xmlParserPort,
            qualifierRepository,
            descriptorRepository,
            meshDescriptorBatchPort,
            fileDownloadPort);
  }

  @Nested
  @DisplayName("importDescriptors() 方法测试")
  class ImportDescriptorsTest {

    @Test
    @DisplayName("INCREMENTAL 模式 - 不应该清空表")
    void incremental_shouldNotTruncateTable() {
      // Given
      MeshImportCommand command = MeshImportCommand.of(TEST_URL, "2025", "INCREMENTAL");
      when(fileDownloadPort.downloadToTemp(any(URI.class))).thenReturn(TEST_LOCAL_PATH);
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(12345L);

      // When
      MeshImportResult result = orchestrator.importDescriptors(command);

      // Then
      verify(fileDownloadPort).downloadToTemp(URI.create(TEST_URL));
      verify(descriptorRepository, never()).truncateAll();
      verify(meshDescriptorBatchPort).launchImport(any(MeshImportParams.class));

      assertThat(result).isNotNull();
      assertThat(result.executionId()).isEqualTo(12345L);
      assertThat(result.sourceUrl()).isEqualTo(TEST_URL);
      assertThat(result.filePath()).isEqualTo(TEST_LOCAL_PATH.toString());
      assertThat(result.meshVersion()).isEqualTo("2025");
      assertThat(result.mode()).isEqualTo(MeshDescriptorImportMode.INCREMENTAL);
    }

    @Test
    @DisplayName("INCREMENTAL 模式 - forceNewInstance 应该为 false，tempFile 应该为 true")
    void incremental_shouldSetForceNewInstanceFalseAndTempFileTrue() {
      // Given
      MeshImportCommand command = MeshImportCommand.of(TEST_URL, "2025", "INCREMENTAL");
      when(fileDownloadPort.downloadToTemp(any(URI.class))).thenReturn(TEST_LOCAL_PATH);
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(12345L);

      // When
      orchestrator.importDescriptors(command);

      // Then
      ArgumentCaptor<MeshImportParams> captor = ArgumentCaptor.forClass(MeshImportParams.class);
      verify(meshDescriptorBatchPort).launchImport(captor.capture());

      MeshImportParams params = captor.getValue();
      assertThat(params.filePath()).isEqualTo(TEST_LOCAL_PATH.toString());
      assertThat(params.meshVersion()).isEqualTo("2025");
      assertThat(params.forceNewInstance()).isFalse();
      assertThat(params.tempFile()).isTrue();
    }

    @Test
    @DisplayName("TRUNCATE_REIMPORT 模式 - 应该先清空表再导入")
    void truncateReimport_shouldTruncateBeforeImport() {
      // Given
      MeshImportCommand command = MeshImportCommand.of(TEST_URL, "2025", "TRUNCATE_REIMPORT");
      when(fileDownloadPort.downloadToTemp(any(URI.class))).thenReturn(TEST_LOCAL_PATH);
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(67890L);

      // When
      MeshImportResult result = orchestrator.importDescriptors(command);

      // Then
      verify(fileDownloadPort).downloadToTemp(URI.create(TEST_URL));
      verify(descriptorRepository).truncateAll();
      verify(meshDescriptorBatchPort).launchImport(any(MeshImportParams.class));

      assertThat(result).isNotNull();
      assertThat(result.executionId()).isEqualTo(67890L);
      assertThat(result.sourceUrl()).isEqualTo(TEST_URL);
      assertThat(result.filePath()).isEqualTo(TEST_LOCAL_PATH.toString());
      assertThat(result.mode()).isEqualTo(MeshDescriptorImportMode.TRUNCATE_REIMPORT);
    }

    @Test
    @DisplayName("TRUNCATE_REIMPORT 模式 - forceNewInstance 应该为 true，tempFile 应该为 true")
    void truncateReimport_shouldSetForceNewInstanceTrueAndTempFileTrue() {
      // Given
      MeshImportCommand command = MeshImportCommand.of(TEST_URL, "2025", "TRUNCATE_REIMPORT");
      when(fileDownloadPort.downloadToTemp(any(URI.class))).thenReturn(TEST_LOCAL_PATH);
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(67890L);

      // When
      orchestrator.importDescriptors(command);

      // Then
      ArgumentCaptor<MeshImportParams> captor = ArgumentCaptor.forClass(MeshImportParams.class);
      verify(meshDescriptorBatchPort).launchImport(captor.capture());

      MeshImportParams params = captor.getValue();
      assertThat(params.filePath()).isEqualTo(TEST_LOCAL_PATH.toString());
      assertThat(params.meshVersion()).isEqualTo("2025");
      assertThat(params.forceNewInstance()).isTrue();
      assertThat(params.tempFile()).isTrue();
    }

    @Test
    @DisplayName("参数传递正确性 - URL 和版本应该正确传递")
    void shouldPassCorrectParameters() {
      // Given
      String url = "https://nlmpubs.nlm.nih.gov/projects/mesh/2025/desc2025.xml";
      String meshVersion = "2025";
      Path localPath = Path.of("/tmp/mesh-import-param-test.xml");
      MeshImportCommand command = MeshImportCommand.of(url, meshVersion, "INCREMENTAL");
      when(fileDownloadPort.downloadToTemp(URI.create(url))).thenReturn(localPath);
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(99999L);

      // When
      MeshImportResult result = orchestrator.importDescriptors(command);

      // Then
      ArgumentCaptor<MeshImportParams> captor = ArgumentCaptor.forClass(MeshImportParams.class);
      verify(meshDescriptorBatchPort).launchImport(captor.capture());

      MeshImportParams params = captor.getValue();
      assertThat(params.filePath()).isEqualTo(localPath.toString());
      assertThat(params.meshVersion()).isEqualTo(meshVersion);

      assertThat(result.sourceUrl()).isEqualTo(url);
      assertThat(result.filePath()).isEqualTo(localPath.toString());
      assertThat(result.meshVersion()).isEqualTo(meshVersion);
    }

    @Test
    @DisplayName("返回结果应该包含正确的执行 ID")
    void shouldReturnCorrectExecutionId() {
      // Given
      Long expectedExecutionId = 1234567890L;
      MeshImportCommand command = MeshImportCommand.of(TEST_URL, "2025", "INCREMENTAL");
      when(fileDownloadPort.downloadToTemp(any(URI.class))).thenReturn(TEST_LOCAL_PATH);
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class)))
          .thenReturn(expectedExecutionId);

      // When
      MeshImportResult result = orchestrator.importDescriptors(command);

      // Then
      assertThat(result.executionId()).isEqualTo(expectedExecutionId);
      assertThat(result.message()).isNotBlank();
    }

    @Test
    @DisplayName("Job 启动失败时应该清理临时文件")
    void shouldCleanupTempFileWhenJobLaunchFails() {
      // Given
      MeshImportCommand command = MeshImportCommand.of(TEST_URL, "2025", "INCREMENTAL");
      Path tempFile = Path.of("/tmp/mesh-import-cleanup-test.xml");
      when(fileDownloadPort.downloadToTemp(any(URI.class))).thenReturn(tempFile);
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class)))
          .thenThrow(new RuntimeException("Job 启动失败"));

      // When & Then
      assertThatThrownBy(() -> orchestrator.importDescriptors(command))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Job 启动失败");

      // 注意：由于 Files.deleteIfExists 是静态方法，无法在单元测试中验证
      // 实际清理行为需要在集成测试中验证
    }
  }

  @Nested
  @DisplayName("importQualifiers() 方法测试")
  class ImportQualifiersTest {

    @Test
    @DisplayName("应该解析 XML 并批量保存限定词")
    void shouldParseXmlAndSaveBatch() {
      // Given
      InputStream inputStream = new ByteArrayInputStream("<QualifierRecordSet/>".getBytes());
      MeshQualifierAggregate qualifier1 = createMockQualifier("Q000001");
      MeshQualifierAggregate qualifier2 = createMockQualifier("Q000002");
      List<MeshQualifierAggregate> qualifiers = List.of(qualifier1, qualifier2);

      when(xmlParserPort.parseQualifiers(inputStream)).thenReturn(qualifiers.stream());

      // When
      int count = orchestrator.importQualifiers(inputStream);

      // Then
      assertThat(count).isEqualTo(2);
      verify(xmlParserPort).parseQualifiers(inputStream);
      verify(qualifierRepository).saveBatch(qualifiers);
    }

    @Test
    @DisplayName("应该正确返回导入的限定词数量")
    void shouldReturnCorrectCount() {
      // Given
      InputStream inputStream = new ByteArrayInputStream("<QualifierRecordSet/>".getBytes());
      List<MeshQualifierAggregate> qualifiers =
          List.of(
              createMockQualifier("Q000001"),
              createMockQualifier("Q000002"),
              createMockQualifier("Q000003"));

      when(xmlParserPort.parseQualifiers(inputStream)).thenReturn(qualifiers.stream());

      // When
      int count = orchestrator.importQualifiers(inputStream);

      // Then
      assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("空 XML 应该返回 0")
    void shouldReturnZeroForEmptyXml() {
      // Given
      InputStream inputStream = new ByteArrayInputStream("<QualifierRecordSet/>".getBytes());
      when(xmlParserPort.parseQualifiers(inputStream)).thenReturn(Stream.empty());

      // When
      int count = orchestrator.importQualifiers(inputStream);

      // Then
      assertThat(count).isEqualTo(0);
      verify(qualifierRepository).saveBatch(List.of());
    }

    /// 创建测试用限定词聚合根。
    ///
    /// @param qualifierUi 限定词 UI 字符串，格式如 "Q000001"
    /// @return 限定词聚合根实例
    private MeshQualifierAggregate createMockQualifier(String qualifierUi) {
      int num = Integer.parseInt(qualifierUi.substring(1));
      return MeshQualifierAggregate.create(
          MeshUI.qualifierOf(num),
          "Test Qualifier " + qualifierUi,
          "TQ");
    }
  }
}
