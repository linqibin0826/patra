package com.patra.catalog.app.usecase.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.mesh.command.MeshDescriptorImportCommand;
import com.patra.catalog.app.usecase.mesh.command.MeshQualifierImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshDescriptorImportResult;
import com.patra.catalog.app.usecase.mesh.dto.MeshQualifierImportResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.enums.MeshFileType;
import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.port.batch.MeshDescriptorBatchPort;
import com.patra.catalog.domain.port.parser.MeshQualifierParserPort;
import com.patra.catalog.domain.port.repository.MeshDescriptorRepository;
import com.patra.catalog.domain.port.repository.MeshQualifierRepository;
import com.patra.catalog.domain.port.source.MeshSourceFilePort;
import com.patra.common.error.ApplicationException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
/// - importDescriptors()：数据存在性检查 + 正常导入流程
/// - importQualifiers()：数据存在性检查 + 下载/解析/保存流程
/// - 参数传递正确性验证
/// - 异常处理和临时文件清理
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("MeshImportOrchestrator 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class MeshImportOrchestratorTest {

  private static final String TEST_URL = "https://example.com/mesh.xml";
  private static final Path TEST_LOCAL_PATH = Path.of("/tmp/mesh-import-12345.xml");

  @Mock private MeshQualifierParserPort qualifierParserPort;
  @Mock private MeshQualifierRepository qualifierRepository;
  @Mock private MeshDescriptorRepository descriptorRepository;
  @Mock private MeshDescriptorBatchPort meshDescriptorBatchPort;
  @Mock private MeshSourceFilePort meshSourceFilePort;

  private MeshImportOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator =
        new MeshImportOrchestrator(
            qualifierParserPort,
            qualifierRepository,
            descriptorRepository,
            meshDescriptorBatchPort,
            meshSourceFilePort);
  }

  @Nested
  @DisplayName("importDescriptors() 方法测试")
  class ImportDescriptorsTest {

    @Nested
    @DisplayName("数据存在性检查")
    class DataExistenceCheckTest {

      @Test
      @DisplayName("表中已有数据时 - 应该抛出 DataAlreadyExistsException")
      void shouldThrowException_whenDataAlreadyExists() {
        // Given
        MeshDescriptorImportCommand command = MeshDescriptorImportCommand.of(TEST_URL, "2025");
        when(descriptorRepository.hasAnyData()).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> orchestrator.importDescriptors(command))
            .isInstanceOf(DataAlreadyExistsException.class)
            .hasMessageContaining("MeSH Descriptor");

        // 验证没有进行后续操作
        verify(meshSourceFilePort, never())
            .fetchFile(any(MeshFileType.class), anyString(), any(URI.class));
        verify(meshDescriptorBatchPort, never()).launchImport(any());
      }

      @Test
      @DisplayName("表中无数据时 - 应该正常执行导入")
      void shouldProceed_whenNoDataExists() {
        // Given
        MeshDescriptorImportCommand command = MeshDescriptorImportCommand.of(TEST_URL, "2025");
        when(descriptorRepository.hasAnyData()).thenReturn(false);
        when(meshSourceFilePort.fetchFile(any(MeshFileType.class), anyString(), any(URI.class)))
            .thenReturn(TEST_LOCAL_PATH);
        when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(12345L);

        // When
        MeshDescriptorImportResult result = orchestrator.importDescriptors(command);

        // Then
        verify(descriptorRepository).hasAnyData();
        verify(meshSourceFilePort).fetchFile(MeshFileType.DESCRIPTOR, "2025", URI.create(TEST_URL));
        verify(meshDescriptorBatchPort).launchImport(any(MeshImportParams.class));

        assertThat(result).isNotNull();
        assertThat(result.executionId()).isEqualTo(12345L);
      }
    }

    @Test
    @DisplayName("正常导入 - tempFile 应该为 true")
    void shouldSetTempFileTrue() {
      // Given
      MeshDescriptorImportCommand command = MeshDescriptorImportCommand.of(TEST_URL, "2025");
      when(descriptorRepository.hasAnyData()).thenReturn(false);
      when(meshSourceFilePort.fetchFile(any(MeshFileType.class), anyString(), any(URI.class)))
          .thenReturn(TEST_LOCAL_PATH);
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(12345L);

      // When
      orchestrator.importDescriptors(command);

      // Then
      ArgumentCaptor<MeshImportParams> captor = ArgumentCaptor.forClass(MeshImportParams.class);
      verify(meshDescriptorBatchPort).launchImport(captor.capture());

      MeshImportParams params = captor.getValue();
      assertThat(params.filePath()).isEqualTo(TEST_LOCAL_PATH.toString());
      assertThat(params.meshVersion()).isEqualTo("2025");
      assertThat(params.tempFile()).isTrue();
    }

    @Test
    @DisplayName("参数传递正确性 - URL 和版本应该正确传递")
    void shouldPassCorrectParameters() {
      // Given
      String url = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml";
      String meshVersion = "2025";
      Path localPath = Path.of("/tmp/mesh-import-param-test.xml");
      MeshDescriptorImportCommand command = MeshDescriptorImportCommand.of(url, meshVersion);

      when(descriptorRepository.hasAnyData()).thenReturn(false);
      when(meshSourceFilePort.fetchFile(MeshFileType.DESCRIPTOR, meshVersion, URI.create(url)))
          .thenReturn(localPath);
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(99999L);

      // When
      MeshDescriptorImportResult result = orchestrator.importDescriptors(command);

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
      MeshDescriptorImportCommand command = MeshDescriptorImportCommand.of(TEST_URL, "2025");

      when(descriptorRepository.hasAnyData()).thenReturn(false);
      when(meshSourceFilePort.fetchFile(any(MeshFileType.class), anyString(), any(URI.class)))
          .thenReturn(TEST_LOCAL_PATH);
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class)))
          .thenReturn(expectedExecutionId);

      // When
      MeshDescriptorImportResult result = orchestrator.importDescriptors(command);

      // Then
      assertThat(result.executionId()).isEqualTo(expectedExecutionId);
      assertThat(result.message()).isNotBlank();
    }

    @Test
    @DisplayName("Job 启动失败时应该清理临时文件并包装为 ApplicationException")
    void shouldCleanupTempFileWhenJobLaunchFails() {
      // Given
      MeshDescriptorImportCommand command = MeshDescriptorImportCommand.of(TEST_URL, "2025");
      Path tempFile = Path.of("/tmp/mesh-import-cleanup-test.xml");

      when(descriptorRepository.hasAnyData()).thenReturn(false);
      when(meshSourceFilePort.fetchFile(any(MeshFileType.class), anyString(), any(URI.class)))
          .thenReturn(tempFile);
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class)))
          .thenThrow(new RuntimeException("Job 启动失败"));

      // When & Then
      assertThatThrownBy(() -> orchestrator.importDescriptors(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("MeSH 主题词导入失败")
          .hasMessageContaining("Job 启动失败");

      // 注意：由于 Files.deleteIfExists 是静态方法，无法在单元测试中验证
      // 实际清理行为需要在集成测试中验证
    }
  }

  @Nested
  @DisplayName("importQualifiers(Command) 方法测试 - XXL-Job 调度入口")
  class ImportQualifiersWithCommandTest {

    private static final String QUALIFIER_URL =
        "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml";
    private static final Path QUALIFIER_LOCAL_PATH = Path.of("/tmp/mesh-qualifier-12345.xml");

    @Nested
    @DisplayName("数据存在性检查")
    class DataExistenceCheckTest {

      @Test
      @DisplayName("表中已有数据时 - 应该抛出 DataAlreadyExistsException")
      void shouldThrowException_whenDataAlreadyExists() {
        // Given
        MeshQualifierImportCommand command = MeshQualifierImportCommand.of(QUALIFIER_URL, "2025");
        when(qualifierRepository.hasAnyData()).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> orchestrator.importQualifiers(command))
            .isInstanceOf(DataAlreadyExistsException.class)
            .hasMessageContaining("MeSH Qualifier");

        // 验证没有进行后续操作
        verify(meshSourceFilePort, never())
            .fetchFile(any(MeshFileType.class), anyString(), any(URI.class));
        verify(qualifierParserPort, never()).parse(any(Path.class), anyString());
      }
    }

    @Test
    @DisplayName("正常导入 - 应该下载文件、解析 XML 并批量保存")
    void shouldDownloadParseAndSave() {
      // Given
      MeshQualifierImportCommand command = MeshQualifierImportCommand.of(QUALIFIER_URL, "2025");
      List<MeshQualifierAggregate> qualifiers =
          List.of(createMockQualifier("Q000001"), createMockQualifier("Q000002"));

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(meshSourceFilePort.fetchFile(any(MeshFileType.class), anyString(), any(URI.class)))
          .thenReturn(QUALIFIER_LOCAL_PATH);
      when(qualifierParserPort.parse(any(Path.class), anyString())).thenReturn(qualifiers.stream());

      // When
      MeshQualifierImportResult result = orchestrator.importQualifiers(command);

      // Then - 验证调用顺序：检查数据 → 获取文件 → 解析 → 保存
      verify(qualifierRepository).hasAnyData();
      verify(meshSourceFilePort)
          .fetchFile(MeshFileType.QUALIFIER, "2025", URI.create(QUALIFIER_URL));
      verify(qualifierParserPort).parse(any(Path.class), anyString());
      verify(qualifierRepository).saveBatch(qualifiers);

      // Then - 验证结果
      assertThat(result).isNotNull();
      assertThat(result.sourceUrl()).isEqualTo(QUALIFIER_URL);
      assertThat(result.meshVersion()).isEqualTo("2025");
      assertThat(result.importedCount()).isEqualTo(2);
      assertThat(result.message()).contains("限定词导入完成");
    }

    @Test
    @DisplayName("应该返回正确的导入数量")
    void shouldReturnCorrectImportedCount() {
      // Given
      MeshQualifierImportCommand command = MeshQualifierImportCommand.of(QUALIFIER_URL, "2025");
      List<MeshQualifierAggregate> qualifiers =
          List.of(
              createMockQualifier("Q000001"),
              createMockQualifier("Q000002"),
              createMockQualifier("Q000003"),
              createMockQualifier("Q000004"),
              createMockQualifier("Q000005"));

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(meshSourceFilePort.fetchFile(any(MeshFileType.class), anyString(), any(URI.class)))
          .thenReturn(QUALIFIER_LOCAL_PATH);
      when(qualifierParserPort.parse(any(Path.class), anyString())).thenReturn(qualifiers.stream());

      // When
      MeshQualifierImportResult result = orchestrator.importQualifiers(command);

      // Then
      assertThat(result.importedCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("空 XML 应该返回导入数量为 0")
    void shouldReturnZeroForEmptyXml() {
      // Given
      MeshQualifierImportCommand command = MeshQualifierImportCommand.of(QUALIFIER_URL, "2025");

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(meshSourceFilePort.fetchFile(any(MeshFileType.class), anyString(), any(URI.class)))
          .thenReturn(QUALIFIER_LOCAL_PATH);
      when(qualifierParserPort.parse(any(Path.class), anyString())).thenReturn(Stream.empty());

      // When
      MeshQualifierImportResult result = orchestrator.importQualifiers(command);

      // Then
      assertThat(result.importedCount()).isEqualTo(0);
      verify(qualifierRepository).saveBatch(List.of());
    }

    @Test
    @DisplayName("导入失败时应该清理临时文件并包装为 ApplicationException")
    void shouldCleanupTempFileWhenImportFails() {
      // Given
      MeshQualifierImportCommand command = MeshQualifierImportCommand.of(QUALIFIER_URL, "2025");

      when(qualifierRepository.hasAnyData()).thenReturn(false);
      when(meshSourceFilePort.fetchFile(any(MeshFileType.class), anyString(), any(URI.class)))
          .thenReturn(QUALIFIER_LOCAL_PATH);
      when(qualifierParserPort.parse(any(Path.class), anyString()))
          .thenThrow(new RuntimeException("XML 解析失败"));

      // When & Then
      assertThatThrownBy(() -> orchestrator.importQualifiers(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("MeSH 限定词导入失败")
          .hasMessageContaining("XML 解析失败");

      // 注意：实际文件清理行为需要在集成测试中验证
    }

    /// 创建测试用限定词聚合根。
    private MeshQualifierAggregate createMockQualifier(String qualifierUi) {
      int num = Integer.parseInt(qualifierUi.substring(1));
      return MeshQualifierAggregate.create(
          MeshUI.qualifierOf(num), "Test Qualifier " + qualifierUi, "TQ");
    }
  }
}
