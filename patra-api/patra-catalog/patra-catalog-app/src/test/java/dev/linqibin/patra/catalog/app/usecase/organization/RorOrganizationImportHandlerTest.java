package dev.linqibin.patra.catalog.app.usecase.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.app.usecase.organization.command.RorOrganizationImportCommand;
import dev.linqibin.patra.catalog.app.usecase.organization.command.RorOrganizationImportResult;
import dev.linqibin.patra.catalog.domain.exception.DataAlreadyExistsException;
import dev.linqibin.patra.catalog.domain.exception.InvalidRorImportParamsException;
import dev.linqibin.patra.catalog.domain.model.vo.organization.RorImportParams;
import dev.linqibin.patra.catalog.domain.port.batch.RorOrganizationBatchPort;
import dev.linqibin.patra.catalog.domain.port.repository.OrganizationRepository;
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

/// ROR 机构导入命令处理器单元测试。
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
@DisplayName("RorOrganizationImportHandler 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class RorOrganizationImportHandlerTest {

  private static final String TEST_URL =
      "https://zenodo.org/records/17468391/files/v2.0-2025-12-16-ror-data.zip";
  private static final String TEST_VERSION = "v2.0";

  @Mock private OrganizationRepository organizationRepository;
  @Mock private RorOrganizationBatchPort rorOrganizationBatchPort;

  private RorOrganizationImportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new RorOrganizationImportHandler(organizationRepository, rorOrganizationBatchPort);
  }

  @Nested
  @DisplayName("数据存在性检查测试")
  class DataExistenceCheckTest {

    @Test
    @DisplayName("表中已有数据时 - 应该抛出 DataAlreadyExistsException")
    void shouldThrowException_whenDataAlreadyExists() {
      // Given
      RorOrganizationImportCommand command =
          new RorOrganizationImportCommand(TEST_URL, TEST_VERSION);
      when(organizationRepository.hasAnyData()).thenReturn(true);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(DataAlreadyExistsException.class)
          .hasMessageContaining("Organization");

      // 验证没有进行后续操作
      verify(rorOrganizationBatchPort, never()).launchImport(any());
    }

    @Test
    @DisplayName("表中无数据时 - 应该正常执行导入")
    void shouldProceed_whenNoDataExists() {
      // Given
      RorOrganizationImportCommand command =
          new RorOrganizationImportCommand(TEST_URL, TEST_VERSION);

      when(organizationRepository.hasAnyData()).thenReturn(false);
      when(rorOrganizationBatchPort.launchImport(any(RorImportParams.class))).thenReturn(12345L);

      // When
      RorOrganizationImportResult result = handler.handle(command);

      // Then
      verify(organizationRepository).hasAnyData();
      verify(rorOrganizationBatchPort).launchImport(any(RorImportParams.class));

      assertThat(result).isNotNull();
      assertThat(result.executionId()).isEqualTo(12345L);
    }
  }

  @Nested
  @DisplayName("导入参数传递测试")
  class ImportParamsTest {

    @Test
    @DisplayName("应该正确传递 URL 和版本号到 BatchPort")
    void shouldPassCorrectParamsToBatchPort() {
      // Given
      RorOrganizationImportCommand command =
          new RorOrganizationImportCommand(TEST_URL, TEST_VERSION);

      when(organizationRepository.hasAnyData()).thenReturn(false);
      when(rorOrganizationBatchPort.launchImport(any(RorImportParams.class))).thenReturn(12345L);

      // When
      handler.handle(command);

      // Then
      ArgumentCaptor<RorImportParams> captor = ArgumentCaptor.forClass(RorImportParams.class);
      verify(rorOrganizationBatchPort).launchImport(captor.capture());

      RorImportParams params = captor.getValue();
      assertThat(params.downloadUrl()).isEqualTo(TEST_URL);
      assertThat(params.rorVersion()).isEqualTo(TEST_VERSION);
    }

    @Test
    @DisplayName("返回结果应该包含正确的执行信息")
    void shouldReturnCorrectResult() {
      // Given
      RorOrganizationImportCommand command =
          new RorOrganizationImportCommand(TEST_URL, TEST_VERSION);

      when(organizationRepository.hasAnyData()).thenReturn(false);
      when(rorOrganizationBatchPort.launchImport(any(RorImportParams.class))).thenReturn(99999L);

      // When
      RorOrganizationImportResult result = handler.handle(command);

      // Then
      assertThat(result.executionId()).isEqualTo(99999L);
      assertThat(result.url()).isEqualTo(TEST_URL);
      assertThat(result.rorVersion()).isEqualTo(TEST_VERSION);
    }
  }

  @Nested
  @DisplayName("异常处理测试")
  class ExceptionHandlingTest {

    @Test
    @DisplayName("InvalidRorImportParamsException 应该直接抛出（携带 RULE_VIOLATION 语义特征）")
    void shouldRethrowInvalidParamsException() {
      // Given
      RorOrganizationImportCommand command =
          new RorOrganizationImportCommand(TEST_URL, TEST_VERSION);

      when(organizationRepository.hasAnyData()).thenReturn(false);
      when(rorOrganizationBatchPort.launchImport(any(RorImportParams.class)))
          .thenThrow(new InvalidRorImportParamsException("参数无效"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(InvalidRorImportParamsException.class)
          .hasMessageContaining("参数无效");
    }

    @Test
    @DisplayName("RuntimeException 应该包装为 ApplicationException (CAT_1401)")
    void shouldWrapRuntimeException() {
      // Given
      RorOrganizationImportCommand command =
          new RorOrganizationImportCommand(TEST_URL, TEST_VERSION);

      when(organizationRepository.hasAnyData()).thenReturn(false);
      when(rorOrganizationBatchPort.launchImport(any(RorImportParams.class)))
          .thenThrow(new RuntimeException("Job 启动失败"));

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("ROR 机构导入失败")
          .hasMessageContaining("Job 启动失败")
          .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ApplicationException 应该直接抛出，不重复包装")
    void shouldRethrowApplicationExceptionWithoutWrapping() {
      // Given
      RorOrganizationImportCommand command =
          new RorOrganizationImportCommand(TEST_URL, TEST_VERSION);
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
              "原始错误");

      when(organizationRepository.hasAnyData()).thenReturn(false);
      when(rorOrganizationBatchPort.launchImport(any(RorImportParams.class)))
          .thenThrow(originalException);

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .isSameAs(originalException);
    }

    @Test
    @DisplayName("Checked Exception 应该包装为 ApplicationException (CAT_1401)")
    void shouldWrapCheckedException() {
      // Given
      RorOrganizationImportCommand command =
          new RorOrganizationImportCommand(TEST_URL, TEST_VERSION);

      when(organizationRepository.hasAnyData()).thenReturn(false);
      // 模拟抛出 checked exception（通过 RuntimeException 包装）
      when(rorOrganizationBatchPort.launchImport(any(RorImportParams.class)))
          .thenAnswer(
              invocation -> {
                throw new Exception("IO 错误");
              });

      // When & Then
      assertThatThrownBy(() -> handler.handle(command))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("ROR 机构导入时发生意外错误");
    }
  }
}
