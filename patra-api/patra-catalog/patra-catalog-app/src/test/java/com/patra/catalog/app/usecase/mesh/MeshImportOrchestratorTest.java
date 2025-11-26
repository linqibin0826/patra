package com.patra.catalog.app.usecase.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.mesh.command.MeshImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshImportResult;
import com.patra.catalog.domain.model.enums.MeshDescriptorImportMode;
import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import com.patra.catalog.domain.port.MeshDescriptorBatchPort;
import com.patra.catalog.domain.port.MeshDescriptorRepository;
import com.patra.catalog.domain.port.MeshQualifierRepository;
import com.patra.catalog.domain.port.XmlParserPort;
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

  @Mock private XmlParserPort xmlParserPort;
  @Mock private MeshQualifierRepository qualifierRepository;
  @Mock private MeshDescriptorRepository descriptorRepository;
  @Mock private MeshDescriptorBatchPort meshDescriptorBatchPort;

  private MeshImportOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator =
        new MeshImportOrchestrator(
            xmlParserPort, qualifierRepository, descriptorRepository, meshDescriptorBatchPort);
  }

  @Nested
  @DisplayName("importDescriptors() 方法测试")
  class ImportDescriptorsTest {

    @Test
    @DisplayName("INCREMENTAL 模式 - 不应该清空表")
    void incremental_shouldNotTruncateTable() {
      // Given
      MeshImportCommand command =
          MeshImportCommand.of("/path/to/mesh.xml", "2025", "INCREMENTAL");
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(12345L);

      // When
      MeshImportResult result = orchestrator.importDescriptors(command);

      // Then
      verify(descriptorRepository, never()).truncateAll();
      verify(meshDescriptorBatchPort).launchImport(any(MeshImportParams.class));

      assertThat(result).isNotNull();
      assertThat(result.executionId()).isEqualTo(12345L);
      assertThat(result.filePath()).isEqualTo("/path/to/mesh.xml");
      assertThat(result.meshVersion()).isEqualTo("2025");
      assertThat(result.mode()).isEqualTo(MeshDescriptorImportMode.INCREMENTAL);
    }

    @Test
    @DisplayName("INCREMENTAL 模式 - forceNewInstance 应该为 false")
    void incremental_shouldSetForceNewInstanceFalse() {
      // Given
      MeshImportCommand command =
          MeshImportCommand.of("/path/to/mesh.xml", "2025", "INCREMENTAL");
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(12345L);

      // When
      orchestrator.importDescriptors(command);

      // Then
      ArgumentCaptor<MeshImportParams> captor = ArgumentCaptor.forClass(MeshImportParams.class);
      verify(meshDescriptorBatchPort).launchImport(captor.capture());

      MeshImportParams params = captor.getValue();
      assertThat(params.filePath()).isEqualTo("/path/to/mesh.xml");
      assertThat(params.meshVersion()).isEqualTo("2025");
      assertThat(params.forceNewInstance()).isFalse();
    }

    @Test
    @DisplayName("TRUNCATE_REIMPORT 模式 - 应该先清空表再导入")
    void truncateReimport_shouldTruncateBeforeImport() {
      // Given
      MeshImportCommand command =
          MeshImportCommand.of("/path/to/mesh.xml", "2025", "TRUNCATE_REIMPORT");
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(67890L);

      // When
      MeshImportResult result = orchestrator.importDescriptors(command);

      // Then
      verify(descriptorRepository).truncateAll();
      verify(meshDescriptorBatchPort).launchImport(any(MeshImportParams.class));

      assertThat(result).isNotNull();
      assertThat(result.executionId()).isEqualTo(67890L);
      assertThat(result.mode()).isEqualTo(MeshDescriptorImportMode.TRUNCATE_REIMPORT);
    }

    @Test
    @DisplayName("TRUNCATE_REIMPORT 模式 - forceNewInstance 应该为 true")
    void truncateReimport_shouldSetForceNewInstanceTrue() {
      // Given
      MeshImportCommand command =
          MeshImportCommand.of("/path/to/mesh.xml", "2025", "TRUNCATE_REIMPORT");
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(67890L);

      // When
      orchestrator.importDescriptors(command);

      // Then
      ArgumentCaptor<MeshImportParams> captor = ArgumentCaptor.forClass(MeshImportParams.class);
      verify(meshDescriptorBatchPort).launchImport(captor.capture());

      MeshImportParams params = captor.getValue();
      assertThat(params.filePath()).isEqualTo("/path/to/mesh.xml");
      assertThat(params.meshVersion()).isEqualTo("2025");
      assertThat(params.forceNewInstance()).isTrue();
    }

    @Test
    @DisplayName("参数传递正确性 - 文件路径和版本应该正确传递")
    void shouldPassCorrectParameters() {
      // Given
      String filePath = "/data/mesh/desc2025.xml";
      String meshVersion = "2025";
      MeshImportCommand command = MeshImportCommand.of(filePath, meshVersion, "INCREMENTAL");
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class))).thenReturn(99999L);

      // When
      MeshImportResult result = orchestrator.importDescriptors(command);

      // Then
      ArgumentCaptor<MeshImportParams> captor = ArgumentCaptor.forClass(MeshImportParams.class);
      verify(meshDescriptorBatchPort).launchImport(captor.capture());

      MeshImportParams params = captor.getValue();
      assertThat(params.filePath()).isEqualTo(filePath);
      assertThat(params.meshVersion()).isEqualTo(meshVersion);

      assertThat(result.filePath()).isEqualTo(filePath);
      assertThat(result.meshVersion()).isEqualTo(meshVersion);
    }

    @Test
    @DisplayName("返回结果应该包含正确的执行 ID")
    void shouldReturnCorrectExecutionId() {
      // Given
      Long expectedExecutionId = 1234567890L;
      MeshImportCommand command = MeshImportCommand.of("/path/to/mesh.xml", "2025", "INCREMENTAL");
      when(meshDescriptorBatchPort.launchImport(any(MeshImportParams.class)))
          .thenReturn(expectedExecutionId);

      // When
      MeshImportResult result = orchestrator.importDescriptors(command);

      // Then
      assertThat(result.executionId()).isEqualTo(expectedExecutionId);
      assertThat(result.message()).isNotBlank();
    }
  }
}
