package com.patra.catalog.app.usecase.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.mesh.command.MeshDescriptorImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshDescriptorImportResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import com.patra.catalog.domain.port.batch.MeshBatchPort;
import com.patra.catalog.domain.port.repository.MeshDescriptorRepository;
import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.commons.error.codes.ErrorCodeLike;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// MeSH 主题词导入处理器单元测试。
///
/// **测试策略**：
///
/// - 单元测试：Mock 所有 Port 依赖
/// - 测试隔离：每个测试方法独立
///
/// **重点测试场景**：
///
/// - 数据存在性检查：表中有数据时应拒绝导入
/// - 正常导入流程：传递 URL 和版本 → 启动批处理
/// - 异常包装和传播
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("MeshDescriptorImportHandler 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class MeshDescriptorImportHandlerTest {

  private static final String TEST_URL =
      "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml";
  private static final String TEST_VERSION = "2025";

  @Mock private MeshDescriptorRepository descriptorRepository;
  @Mock private MeshBatchPort meshBatchPort;

  private MeshDescriptorImportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new MeshDescriptorImportHandler(descriptorRepository, meshBatchPort);
  }

  @Nested
  @DisplayName("数据存在性检查测试")
  class DataExistenceCheckTest {

    @Test
    @DisplayName("表中已有数据时 - 应该抛出 DataAlreadyExistsException")
    void shouldThrowException_whenDataAlreadyExists() {
      // Given
      MeshDescriptorImportCommand command = new MeshDescriptorImportCommand(TEST_URL, TEST_VERSION);
      when(descriptorRepository.hasAnyData()).thenReturn(true);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(DataAlreadyExistsException.class)
          .hasMessageContaining("MeSH Descriptor");

      // 验证没有启动批处理
      verify(meshBatchPort, never()).launchDescriptorImport(any());
    }

    @Test
    @DisplayName("表中无数据时 - 应该正常启动批处理")
    void shouldProceed_whenNoDataExists() {
      // Given
      MeshDescriptorImportCommand command = new MeshDescriptorImportCommand(TEST_URL, TEST_VERSION);

      when(descriptorRepository.hasAnyData()).thenReturn(false);
      when(meshBatchPort.launchDescriptorImport(any(MeshImportParams.class))).thenReturn(12345L);

      // When
      MeshDescriptorImportResult result = handler.handle(command);

      // Then
      verify(descriptorRepository).hasAnyData();
      verify(meshBatchPort).launchDescriptorImport(any(MeshImportParams.class));

      assertThat(result).isNotNull();
      assertThat(result.executionId()).isEqualTo(12345L);
    }
  }

  @Nested
  @DisplayName("导入参数传递测试")
  class ImportParamsTest {

    @Test
    @DisplayName("应该正确传递 URL 和版本号到 MeshBatchPort")
    void shouldPassCorrectParamsToBatchPort() {
      // Given
      MeshDescriptorImportCommand command = new MeshDescriptorImportCommand(TEST_URL, TEST_VERSION);

      when(descriptorRepository.hasAnyData()).thenReturn(false);
      when(meshBatchPort.launchDescriptorImport(any(MeshImportParams.class))).thenReturn(12345L);

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<MeshImportParams> captor = ArgumentCaptor.forClass(MeshImportParams.class);
      verify(meshBatchPort).launchDescriptorImport(captor.capture());

      MeshImportParams params = captor.getValue();
      assertThat(params.downloadUrl()).isEqualTo(TEST_URL);
      assertThat(params.meshVersion()).isEqualTo(TEST_VERSION);
    }

    @Test
    @DisplayName("返回结果应该包含正确的执行信息")
    void shouldReturnCorrectResult() {
      // Given
      MeshDescriptorImportCommand command = new MeshDescriptorImportCommand(TEST_URL, TEST_VERSION);

      when(descriptorRepository.hasAnyData()).thenReturn(false);
      when(meshBatchPort.launchDescriptorImport(any(MeshImportParams.class))).thenReturn(99999L);

      // When
      MeshDescriptorImportResult result = handler.handle(command);

      // Then
      assertThat(result.executionId()).isEqualTo(99999L);
      assertThat(result.sourceUrl()).isEqualTo(TEST_URL);
      assertThat(result.meshVersion()).isEqualTo(TEST_VERSION);
    }
  }

  @Nested
  @DisplayName("异常处理测试")
  class ExceptionHandlingTest {

    @Test
    @DisplayName("ApplicationException 应该直接抛出，不重复包装")
    void shouldRethrowApplicationException() {
      // Given
      MeshDescriptorImportCommand command = new MeshDescriptorImportCommand(TEST_URL, TEST_VERSION);
      ApplicationException originalException =
          new ApplicationException(
              new ErrorCodeLike() {
                @Override
                public String code() {
                  return "TEST_ERROR";
                }

                @Override
                public int httpStatus() {
                  return 500;
                }
              },
              "原始错误");

      when(descriptorRepository.hasAnyData()).thenReturn(false);
      when(meshBatchPort.launchDescriptorImport(any(MeshImportParams.class)))
          .thenThrow(originalException);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .isSameAs(originalException);
    }

    @Test
    @DisplayName("RuntimeException 应该包装为 ApplicationException (CAT_1002)")
    void shouldWrapRuntimeException() {
      // Given
      MeshDescriptorImportCommand command = new MeshDescriptorImportCommand(TEST_URL, TEST_VERSION);

      when(descriptorRepository.hasAnyData()).thenReturn(false);
      when(meshBatchPort.launchDescriptorImport(any(MeshImportParams.class)))
          .thenThrow(new RuntimeException("Job 启动失败"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("MeSH 主题词导入失败")
          .hasMessageContaining("Job 启动失败")
          .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Checked Exception 应该包装为 ApplicationException (CAT_1002)")
    void shouldWrapCheckedException() {
      // Given
      MeshDescriptorImportCommand command = new MeshDescriptorImportCommand(TEST_URL, TEST_VERSION);

      when(descriptorRepository.hasAnyData()).thenReturn(false);
      when(meshBatchPort.launchDescriptorImport(any(MeshImportParams.class)))
          .thenAnswer(
              invocation -> {
                throw new Exception("IO 错误");
              });

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("MeSH 主题词导入时发生意外错误");
    }
  }
}
