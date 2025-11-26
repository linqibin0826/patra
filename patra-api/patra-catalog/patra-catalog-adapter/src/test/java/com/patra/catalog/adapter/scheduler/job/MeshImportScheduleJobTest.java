package com.patra.catalog.adapter.scheduler.job;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.adapter.scheduler.param.MeshImportJobParam;
import com.patra.catalog.adapter.scheduler.param.MeshQualifierImportJobParam;
import com.patra.catalog.app.usecase.mesh.MeshImportUseCase;
import com.patra.catalog.app.usecase.mesh.command.MeshImportCommand;
import com.patra.catalog.app.usecase.mesh.command.MeshQualifierImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshImportResult;
import com.patra.catalog.app.usecase.mesh.dto.MeshQualifierImportResult;
import com.patra.catalog.domain.exception.CatalogScheduleParameterException;
import com.patra.catalog.domain.model.enums.MeshDescriptorImportMode;
import com.xxl.job.core.context.XxlJobHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

/// MeshImportScheduleJob 单元测试。
///
/// 测试策略：
///
/// - Mock 所有依赖（ObjectMapper、MeshImportOrchestrator）
/// - Mock 静态方法 XxlJobHelper
/// - 测试参数解析、验证、成功/失败场景
/// - 验证异常处理和日志记录
///
/// 不使用 @SpringBootTest - 纯单元测试，不依赖 Spring 容器
@ExtendWith(MockitoExtension.class)
@DisplayName("MeshImportScheduleJob 单元测试")
class MeshImportScheduleJobTest {

  @Mock private ObjectMapper objectMapper;
  @Mock private MeshImportUseCase meshImportUseCase;

  @InjectMocks private MeshImportScheduleJob meshImportScheduleJob;

  @Nested
  @DisplayName("execute 方法测试")
  class ExecuteTests {

    @Test
    @DisplayName("应该成功解析 JSON 参数并执行导入")
    void execute_shouldSucceedWithValidJsonParam() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://nlmpubs.nlm.nih.gov/projects/mesh/2025/desc2025.xml\",\"meshVersion\":\"2025\",\"mode\":\"INCREMENTAL\"}";
        MeshImportJobParam param =
            new MeshImportJobParam(
                "https://nlmpubs.nlm.nih.gov/projects/mesh/2025/desc2025.xml",
                "2025",
                "INCREMENTAL");
        MeshImportResult result =
            MeshImportResult.success(
                1001L,
                "https://nlmpubs.nlm.nih.gov/projects/mesh/2025/desc2025.xml",
                "/tmp/mesh-import-12345.xml",
                "2025",
                MeshDescriptorImportMode.INCREMENTAL);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshImportJobParam.class)).thenReturn(param);
        when(meshImportUseCase.importDescriptors(any(MeshImportCommand.class)))
            .thenReturn(result);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then
        verify(objectMapper).readValue(jsonParam, MeshImportJobParam.class);
        verify(meshImportUseCase).importDescriptors(any(MeshImportCommand.class));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该支持 TRUNCATE_REIMPORT 模式")
    void execute_shouldSupportTruncateReimportMode() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://example.com/mesh/desc2025.xml\",\"meshVersion\":\"2025\",\"mode\":\"TRUNCATE_REIMPORT\"}";
        MeshImportJobParam param =
            new MeshImportJobParam(
                "https://example.com/mesh/desc2025.xml", "2025", "TRUNCATE_REIMPORT");
        MeshImportResult result =
            MeshImportResult.success(
                1002L,
                "https://example.com/mesh/desc2025.xml",
                "/tmp/mesh-import-12345.xml",
                "2025",
                MeshDescriptorImportMode.TRUNCATE_REIMPORT);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshImportJobParam.class)).thenReturn(param);
        when(meshImportUseCase.importDescriptors(any(MeshImportCommand.class)))
            .thenReturn(result);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then
        verify(meshImportUseCase).importDescriptors(any(MeshImportCommand.class));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该支持小写模式值")
    void execute_shouldSupportLowercaseModeValue() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://example.com/mesh/desc2025.xml\",\"meshVersion\":\"2025\",\"mode\":\"incremental\"}";
        MeshImportJobParam param =
            new MeshImportJobParam("https://example.com/mesh/desc2025.xml", "2025", "incremental");
        MeshImportResult result =
            MeshImportResult.success(
                1003L,
                "https://example.com/mesh/desc2025.xml",
                "/tmp/mesh-import-12345.xml",
                "2025",
                MeshDescriptorImportMode.INCREMENTAL);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshImportJobParam.class)).thenReturn(param);
        when(meshImportUseCase.importDescriptors(any(MeshImportCommand.class)))
            .thenReturn(result);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then
        verify(meshImportUseCase).importDescriptors(any(MeshImportCommand.class));
      }
    }
  }

  @Nested
  @DisplayName("参数验证测试")
  class ParameterValidationTests {

    @Test
    @DisplayName("应该在参数为空时抛出 CatalogScheduleParameterException")
    void execute_shouldThrowExceptionWhenParamIsEmpty() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeDescriptorImport())
            .isInstanceOf(CatalogScheduleParameterException.class)
            .hasMessageContaining("参数不能为空");

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
        verify(meshImportUseCase, never()).importDescriptors(any(MeshImportCommand.class));
      }
    }

    @Test
    @DisplayName("应该在参数为 null 时抛出 CatalogScheduleParameterException")
    void execute_shouldThrowExceptionWhenParamIsNull() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(null);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeDescriptorImport())
            .isInstanceOf(CatalogScheduleParameterException.class)
            .hasMessageContaining("参数不能为空");

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在 JSON 解析失败时抛出 CatalogScheduleParameterException")
    void execute_shouldThrowExceptionWhenJsonParseFails() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String invalidJson = "{invalid json}";
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(invalidJson);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(invalidJson, MeshImportJobParam.class))
            .thenThrow(new RuntimeException("JSON 解析错误"));

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeDescriptorImport())
            .isInstanceOf(CatalogScheduleParameterException.class)
            .hasMessageContaining("参数解析失败");

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在缺少 url 字段时抛出 CatalogScheduleParameterException")
    void execute_shouldThrowExceptionWhenUrlMissing() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{\"meshVersion\":\"2025\",\"mode\":\"INCREMENTAL\"}";
        MeshImportJobParam param = new MeshImportJobParam(null, "2025", "INCREMENTAL");

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshImportJobParam.class)).thenReturn(param);

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeDescriptorImport())
            .isInstanceOf(CatalogScheduleParameterException.class)
            .hasMessageContaining("url");

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在缺少 meshVersion 字段时抛出 CatalogScheduleParameterException")
    void execute_shouldThrowExceptionWhenMeshVersionMissing() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://example.com/mesh/desc2025.xml\",\"mode\":\"INCREMENTAL\"}";
        MeshImportJobParam param =
            new MeshImportJobParam("https://example.com/mesh/desc2025.xml", null, "INCREMENTAL");

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshImportJobParam.class)).thenReturn(param);

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeDescriptorImport())
            .isInstanceOf(CatalogScheduleParameterException.class)
            .hasMessageContaining("meshVersion");

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在缺少 mode 字段时抛出 CatalogScheduleParameterException")
    void execute_shouldThrowExceptionWhenModeMissing() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://example.com/mesh/desc2025.xml\",\"meshVersion\":\"2025\"}";
        MeshImportJobParam param =
            new MeshImportJobParam("https://example.com/mesh/desc2025.xml", "2025", null);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshImportJobParam.class)).thenReturn(param);

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeDescriptorImport())
            .isInstanceOf(CatalogScheduleParameterException.class)
            .hasMessageContaining("mode");

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在导入模式非法时抛出 CatalogScheduleParameterException")
    void execute_shouldThrowExceptionWhenModeIsInvalid() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://example.com/mesh/desc2025.xml\",\"meshVersion\":\"2025\",\"mode\":\"INVALID_MODE\"}";
        MeshImportJobParam param =
            new MeshImportJobParam(
                "https://example.com/mesh/desc2025.xml", "2025", "INVALID_MODE");

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshImportJobParam.class)).thenReturn(param);

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeDescriptorImport())
            .isInstanceOf(CatalogScheduleParameterException.class)
            .hasMessageContaining("非法的导入模式值");

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }
  }

  @Nested
  @DisplayName("异常处理测试")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("应该在编排器执行失败时包装并抛出 RuntimeException")
    void execute_shouldWrapAndThrowExceptionWhenOrchestratorFails() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://example.com/mesh/desc2025.xml\",\"meshVersion\":\"2025\",\"mode\":\"INCREMENTAL\"}";
        MeshImportJobParam param =
            new MeshImportJobParam(
                "https://example.com/mesh/desc2025.xml", "2025", "INCREMENTAL");

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshImportJobParam.class)).thenReturn(param);

        RuntimeException cause = new RuntimeException("数据库连接失败");
        when(meshImportUseCase.importDescriptors(any(MeshImportCommand.class)))
            .thenThrow(cause);

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeDescriptorImport())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("MeSH 导入任务执行失败")
            .hasCause(cause);

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }
  }

  // ==================== Qualifier 导入测试 ====================

  @Nested
  @DisplayName("executeQualifierImport 方法测试")
  class ExecuteQualifierImportTests {

    @Test
    @DisplayName("应该成功解析 JSON 参数并执行限定词导入")
    void executeQualifierImport_shouldSucceedWithValidJsonParam() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://nlmpubs.nlm.nih.gov/projects/mesh/2025/qual2025.xml\",\"meshVersion\":\"2025\"}";
        MeshQualifierImportJobParam param =
            new MeshQualifierImportJobParam(
                "https://nlmpubs.nlm.nih.gov/projects/mesh/2025/qual2025.xml", "2025");
        MeshQualifierImportResult result =
            MeshQualifierImportResult.success(
                "https://nlmpubs.nlm.nih.gov/projects/mesh/2025/qual2025.xml", "2025", 80);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshQualifierImportJobParam.class)).thenReturn(param);
        when(meshImportUseCase.importQualifiers(any(MeshQualifierImportCommand.class)))
            .thenReturn(result);

        // When
        meshImportScheduleJob.executeQualifierImport();

        // Then
        verify(objectMapper).readValue(jsonParam, MeshQualifierImportJobParam.class);
        verify(meshImportUseCase).importQualifiers(any(MeshQualifierImportCommand.class));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在参数为空时抛出 CatalogScheduleParameterException")
    void executeQualifierImport_shouldThrowExceptionWhenParamIsEmpty() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeQualifierImport())
            .isInstanceOf(CatalogScheduleParameterException.class)
            .hasMessageContaining("参数不能为空");

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
        verify(meshImportUseCase, never()).importQualifiers(any(MeshQualifierImportCommand.class));
      }
    }

    @Test
    @DisplayName("应该在缺少 url 字段时抛出 CatalogScheduleParameterException")
    void executeQualifierImport_shouldThrowExceptionWhenUrlMissing() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{\"meshVersion\":\"2025\"}";
        MeshQualifierImportJobParam param = new MeshQualifierImportJobParam(null, "2025");

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshQualifierImportJobParam.class)).thenReturn(param);

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeQualifierImport())
            .isInstanceOf(CatalogScheduleParameterException.class)
            .hasMessageContaining("url");

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在缺少 meshVersion 字段时抛出 CatalogScheduleParameterException")
    void executeQualifierImport_shouldThrowExceptionWhenMeshVersionMissing() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{\"url\":\"https://example.com/mesh/qual2025.xml\"}";
        MeshQualifierImportJobParam param =
            new MeshQualifierImportJobParam("https://example.com/mesh/qual2025.xml", null);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshQualifierImportJobParam.class)).thenReturn(param);

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeQualifierImport())
            .isInstanceOf(CatalogScheduleParameterException.class)
            .hasMessageContaining("meshVersion");

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在执行失败时包装并抛出 RuntimeException")
    void executeQualifierImport_shouldWrapAndThrowExceptionWhenExecutionFails() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://example.com/mesh/qual2025.xml\",\"meshVersion\":\"2025\"}";
        MeshQualifierImportJobParam param =
            new MeshQualifierImportJobParam("https://example.com/mesh/qual2025.xml", "2025");

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, MeshQualifierImportJobParam.class)).thenReturn(param);

        RuntimeException cause = new RuntimeException("XML 解析失败");
        when(meshImportUseCase.importQualifiers(any(MeshQualifierImportCommand.class)))
            .thenThrow(cause);

        // When & Then
        assertThatThrownBy(() -> meshImportScheduleJob.executeQualifierImport())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("MeSH 限定词导入任务执行失败")
            .hasCause(cause);

        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }
  }
}
