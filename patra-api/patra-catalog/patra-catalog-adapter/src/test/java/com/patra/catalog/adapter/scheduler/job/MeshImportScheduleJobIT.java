package com.patra.catalog.adapter.scheduler.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.adapter.scheduler.config.MeshDataSourceAutoConfiguration;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// MeshImportScheduleJob 切片测试。
///
/// **测试策略**：
///
/// - 使用最小化 Spring 上下文加载被测 Job
/// - Mock MeshImportUseCase 依赖
/// - 使用真实配置属性验证 URL 读取和版本号推断
/// - Mock 静态方法 XxlJobHelper（框架限制，无法避免）
///
/// **设计说明**：
///
/// URL 从配置文件读取，版本号从文件名自动推断。
/// 不再接受 Job 参数，所有配置都在 application-test.yml 中定义。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootTest(
    classes = MeshImportScheduleJob.class,
    properties = {
      "patra.catalog.mesh.descriptor-url=https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
      "patra.catalog.mesh.qualifier-url=https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml"
    })
@Import(MeshDataSourceAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("MeshImportScheduleJob 切片测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MeshImportScheduleJobIT {

  @Autowired private MeshImportScheduleJob meshImportScheduleJob;

  @MockitoBean private MeshImportUseCase meshImportUseCase;

  // ==================== Descriptor 导入测试 ====================

  @Nested
  @DisplayName("executeDescriptorImport 方法测试")
  class ExecuteDescriptorImportTests {

    @Test
    @DisplayName("应该从配置读取 URL 并自动推断版本号执行导入")
    void execute_shouldReadUrlFromConfigAndInferVersion() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        MeshDescriptorImportResult result =
            MeshDescriptorImportResult.success(
                1001L,
                "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
                "/tmp/mesh-import-12345.xml",
                "2025");

        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(meshImportUseCase.importDescriptors(any(MeshDescriptorImportCommand.class)))
            .thenReturn(result);

        // When
        meshImportScheduleJob.executeDescriptorImport();

        // Then - 验证从配置读取的 URL 和自动推断的版本号
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
    @DisplayName("应该在编排器执行失败时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenOrchestratorFails() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
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
    @DisplayName("应该从配置读取 URL 并自动推断版本号执行限定词导入")
    void executeQualifierImport_shouldReadUrlFromConfigAndInferVersion() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        MeshQualifierImportResult result =
            MeshQualifierImportResult.success(
                "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
                "2025",
                80);

        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(meshImportUseCase.importQualifiers(any(MeshQualifierImportCommand.class)))
            .thenReturn(result);

        // When
        meshImportScheduleJob.executeQualifierImport();

        // Then - 验证从配置读取的 URL 和自动推断的版本号
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
    @DisplayName("应该在执行失败时调用 handleFail 报告错误")
    void executeQualifierImport_shouldCallHandleFailWhenExecutionFails() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
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
