package com.patra.catalog.adapter.scheduler.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.mesh.MeshImportUseCase;
import com.patra.catalog.app.usecase.mesh.command.MeshDescriptorImportCommand;
import com.patra.catalog.app.usecase.mesh.command.MeshQualifierImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshDescriptorImportResult;
import com.patra.catalog.app.usecase.mesh.dto.MeshQualifierImportResult;
import com.xxl.job.core.context.XxlJobHelper;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// MeshImportScheduleJob 切片测试。
///
/// 测试策略：
///
/// - 使用最小化 Spring 上下文加载被测 Job
/// - Mock MeshImportUseCase 依赖
/// - 使用真实 ObjectMapper 验证 JSON 解析
/// - Mock 静态方法 XxlJobHelper（框架限制，无法避免）
///
/// **设计说明**：
///
/// 导入操作设计为「一次性初始化」语义，不再支持导入模式参数。
/// JSON 参数只包含 url 和 meshVersion 两个必填字段。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootTest(classes = MeshImportScheduleJob.class)
@Import(JacksonAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("MeshImportScheduleJob 切片测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MeshImportScheduleJobIT {

  @Autowired private MeshImportScheduleJob meshImportScheduleJob;

  @MockitoBean private MeshImportUseCase meshImportUseCase;

  @Nested
  @DisplayName("executeDescriptorImport 方法测试")
  class ExecuteTests {

    @Test
    @DisplayName("应该成功解析 JSON 参数并执行导入")
    void execute_shouldSucceedWithValidJsonParam() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml\",\"meshVersion\":\"2025\"}";
        MeshDescriptorImportResult result =
            MeshDescriptorImportResult.success(
                1001L,
                "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
                "/tmp/mesh-import-12345.xml",
                "2025");

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(meshImportUseCase.importDescriptors(any(MeshDescriptorImportCommand.class)))
            .thenReturn(result);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then - 使用真实 ObjectMapper，验证解析后的 Command 参数
        verify(meshImportUseCase)
            .importDescriptors(
                argThat(
                    cmd ->
                        cmd.url()
                                .equals(
                                    "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml")
                            && cmd.meshVersion().equals("2025")));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该正确传递 URL 和版本号")
    void execute_shouldPassCorrectUrlAndVersion() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://example.com/mesh/desc2024.xml\",\"meshVersion\":\"2024\"}";
        MeshDescriptorImportResult result =
            MeshDescriptorImportResult.success(
                1002L,
                "https://example.com/mesh/desc2024.xml",
                "/tmp/mesh-import-12345.xml",
                "2024");

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(meshImportUseCase.importDescriptors(any(MeshDescriptorImportCommand.class)))
            .thenReturn(result);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then
        verify(meshImportUseCase)
            .importDescriptors(
                argThat(
                    cmd ->
                        cmd.url().equals("https://example.com/mesh/desc2024.xml")
                            && cmd.meshVersion().equals("2024")));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }
  }

  @Nested
  @DisplayName("参数验证测试")
  class ParameterValidationTests {

    @Test
    @DisplayName("应该在参数为空时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenParamIsEmpty() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
        verify(meshImportUseCase, never())
            .importDescriptors(any(MeshDescriptorImportCommand.class));
      }
    }

    @Test
    @DisplayName("应该在参数为 null 时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenParamIsNull() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(null);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在 JSON 解析失败时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenJsonParseFails() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given - 真实 ObjectMapper 会抛出解析异常
        String invalidJson = "{invalid json}";
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(invalidJson);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在缺少 url 字段时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenUrlMissing() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given - 真实 ObjectMapper 解析后 url 为 null
        String jsonParam = "{\"meshVersion\":\"2025\"}";

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在缺少 meshVersion 字段时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenMeshVersionMissing() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{\"url\":\"https://example.com/mesh/desc2025.xml\"}";

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }
  }

  @Nested
  @DisplayName("异常处理测试")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("应该在编排器执行失败时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenOrchestratorFails() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://example.com/mesh/desc2025.xml\",\"meshVersion\":\"2025\"}";

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        RuntimeException cause = new RuntimeException("数据库连接失败");
        when(meshImportUseCase.importDescriptors(any(MeshDescriptorImportCommand.class)))
            .thenThrow(cause);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then
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
    void executeQualifierImport_shouldSucceedWithValidJsonParam() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml\",\"meshVersion\":\"2025\"}";
        MeshQualifierImportResult result =
            MeshQualifierImportResult.success(
                "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
                "2025",
                80);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(meshImportUseCase.importQualifiers(any(MeshQualifierImportCommand.class)))
            .thenReturn(result);

        // When
        meshImportScheduleJob.executeQualifierImport();

        // Then - 使用真实 ObjectMapper，验证解析后的 Command 参数
        verify(meshImportUseCase)
            .importQualifiers(
                argThat(
                    cmd ->
                        cmd.url()
                                .equals(
                                    "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml")
                            && cmd.meshVersion().equals("2025")));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在参数为空时调用 handleFail 报告错误")
    void executeQualifierImport_shouldCallHandleFailWhenParamIsEmpty() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When
        meshImportScheduleJob.executeQualifierImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
        verify(meshImportUseCase, never()).importQualifiers(any(MeshQualifierImportCommand.class));
      }
    }

    @Test
    @DisplayName("应该在缺少 url 字段时调用 handleFail 报告错误")
    void executeQualifierImport_shouldCallHandleFailWhenUrlMissing() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{\"meshVersion\":\"2025\"}";

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When
        meshImportScheduleJob.executeQualifierImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在缺少 meshVersion 字段时调用 handleFail 报告错误")
    void executeQualifierImport_shouldCallHandleFailWhenMeshVersionMissing() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{\"url\":\"https://example.com/mesh/qual2025.xml\"}";

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When
        meshImportScheduleJob.executeQualifierImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在执行失败时调用 handleFail 报告错误")
    void executeQualifierImport_shouldCallHandleFailWhenExecutionFails() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam =
            "{\"url\":\"https://example.com/mesh/qual2025.xml\",\"meshVersion\":\"2025\"}";

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        RuntimeException cause = new RuntimeException("XML 解析失败");
        when(meshImportUseCase.importQualifiers(any(MeshQualifierImportCommand.class)))
            .thenThrow(cause);

        // When
        meshImportScheduleJob.executeQualifierImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }
  }
}
