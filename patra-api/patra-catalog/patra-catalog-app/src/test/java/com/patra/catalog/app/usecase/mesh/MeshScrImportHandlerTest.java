package com.patra.catalog.app.usecase.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.mesh.command.MeshScrImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshScrImportResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import com.patra.catalog.domain.port.batch.MeshScrBatchPort;
import com.patra.catalog.domain.port.repository.MeshScrRepository;
import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
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

/// MeSH SCR 导入处理器单元测试。
///
/// **测试策略**：
///
/// - 验证正常导入流程
/// - 验证数据已存在时的异常处理
/// - 验证批处理启动失败的异常处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshScrImportHandler 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class MeshScrImportHandlerTest {

  @Mock private MeshScrRepository scrRepository;

  @Mock private MeshScrBatchPort meshScrBatchPort;

  private MeshScrImportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new MeshScrImportHandler(scrRepository, meshScrBatchPort);
  }

  @Nested
  @DisplayName("正常导入流程")
  class NormalFlowTest {

    @Test
    @DisplayName("数据库为空时 - 应该启动批处理并返回成功结果")
    void emptyDatabase_shouldLaunchBatchAndReturnSuccess() {
      // Given
      String url = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/supp2025.xml";
      String meshVersion = "2025";
      MeshScrImportCommand command = MeshScrImportCommand.of(url, meshVersion);
      Long expectedExecutionId = 12345L;

      when(scrRepository.hasAnyData()).thenReturn(false);
      when(meshScrBatchPort.launchImport(any(MeshImportParams.class)))
          .thenReturn(expectedExecutionId);

      // When
      MeshScrImportResult result = handler.handle(command);

      // Then
      assertThat(result.executionId()).isEqualTo(expectedExecutionId);
      assertThat(result.sourceUrl()).isEqualTo(url);
      assertThat(result.meshVersion()).isEqualTo(meshVersion);
      assertThat(result.message()).contains("MeSH SCR 导入任务已启动");

      // Verify batch port was called with correct parameters
      ArgumentCaptor<MeshImportParams> paramsCaptor =
          ArgumentCaptor.forClass(MeshImportParams.class);
      verify(meshScrBatchPort).launchImport(paramsCaptor.capture());
      MeshImportParams capturedParams = paramsCaptor.getValue();
      assertThat(capturedParams.downloadUrl()).isEqualTo(url);
      assertThat(capturedParams.meshVersion()).isEqualTo(meshVersion);
    }
  }

  @Nested
  @DisplayName("数据已存在场景")
  class DataExistsTest {

    @Test
    @DisplayName("数据库有数据时 - 应该抛出 DataAlreadyExistsException")
    void databaseHasData_shouldThrowDataAlreadyExistsException() {
      // Given
      MeshScrImportCommand command =
          MeshScrImportCommand.of("https://example.com/supp2025.xml", "2025");
      when(scrRepository.hasAnyData()).thenReturn(true);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(DataAlreadyExistsException.class)
          .hasMessageContaining("MeSH SCR");

      // Verify batch port was never called
      verify(meshScrBatchPort, never()).launchImport(any());
    }
  }

  @Nested
  @DisplayName("批处理启动失败场景")
  class BatchLaunchFailureTest {

    @Test
    @DisplayName("批处理抛出 RuntimeException - 应该包装为 ApplicationException")
    void batchThrowsRuntimeException_shouldWrapInApplicationException() {
      // Given
      MeshScrImportCommand command =
          MeshScrImportCommand.of("https://example.com/supp2025.xml", "2025");
      when(scrRepository.hasAnyData()).thenReturn(false);
      when(meshScrBatchPort.launchImport(any()))
          .thenThrow(new RuntimeException("Job launch failed"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("MeSH SCR 导入失败")
          .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("批处理抛出 ApplicationException - 应该直接重抛")
    void batchThrowsApplicationException_shouldRethrowDirectly() {
      // Given
      MeshScrImportCommand command =
          MeshScrImportCommand.of("https://example.com/supp2025.xml", "2025");
      // 使用匿名 ErrorCodeLike 避免依赖 api 模块
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
              "Original error message");

      when(scrRepository.hasAnyData()).thenReturn(false);
      when(meshScrBatchPort.launchImport(any())).thenThrow(originalException);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .isSameAs(originalException);
    }
  }
}
