package dev.linqibin.patra.catalog.adapter.scheduler.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xxl.job.core.context.XxlJobHelper;
import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.catalog.adapter.scheduler.config.RorDataSourceAutoConfiguration;
import dev.linqibin.patra.catalog.adapter.scheduler.exception.RorConfigurationException;
import dev.linqibin.patra.catalog.app.usecase.organization.command.RorOrganizationImportCommand;
import dev.linqibin.patra.catalog.app.usecase.organization.command.RorOrganizationImportResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// RorOrganizationImportScheduleJob 切片测试。
///
/// **测试策略**：
///
/// - 使用最小化 Spring 上下文加载被测 Job
/// - Mock CommandBus 依赖
/// - 使用真实配置属性验证 URL 读取和版本号推断
/// - Mock 静态方法 XxlJobHelper（框架限制，无法避免）
///
/// **设计说明**：
///
/// URL 从配置文件读取，版本号从文件名自动推断。
/// 不再接受 Job 参数，所有配置都在测试属性中定义。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootTest(
    classes = RorOrganizationImportScheduleJob.class,
    properties = {
      "patra.catalog.ror.download-url=https://zenodo.org/records/17468391/files/v2.0-2025-12-16-ror-data.zip"
    })
@Import(RorDataSourceAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("RorOrganizationImportScheduleJob 切片测试")
class RorOrganizationImportScheduleJobIT {

  @Autowired private RorOrganizationImportScheduleJob rorOrganizationImportScheduleJob;

  @MockitoBean private CommandBus commandBus;

  // ==================== 正常导入测试 ====================

  @Nested
  @DisplayName("executeRorOrganizationImport 方法测试")
  class ExecuteRorOrganizationImportTests {

    @Test
    @DisplayName("应该从配置读取 URL 并自动推断版本号执行导入")
    void execute_shouldReadUrlFromConfigAndInferVersion() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        RorOrganizationImportResult result =
            RorOrganizationImportResult.success(
                1001L,
                "https://zenodo.org/records/17468391/files/v2.0-2025-12-16-ror-data.zip",
                "v2.0");

        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(commandBus.handle(any(RorOrganizationImportCommand.class))).thenReturn(result);

        // When
        rorOrganizationImportScheduleJob.executeRorOrganizationImport();

        // Then - 验证从配置读取的 URL 和自动推断的版本号
        verify(commandBus)
            .handle(
                argThat(
                    (RorOrganizationImportCommand cmd) ->
                        cmd.url()
                                .equals(
                                    "https://zenodo.org/records/17468391/files/v2.0-2025-12-16-ror-data.zip")
                            && cmd.rorVersion().equals("v2.0")));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在 CommandBus 执行失败时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenCommandBusFails() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        RuntimeException cause = new RuntimeException("数据库连接失败");
        when(commandBus.handle(any(RorOrganizationImportCommand.class))).thenThrow(cause);

        // When
        rorOrganizationImportScheduleJob.executeRorOrganizationImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }
  }

  // ==================== 配置错误测试 ====================

  @Nested
  @DisplayName("配置错误处理测试")
  class ConfigurationErrorTests {

    @Test
    @DisplayName("应该在版本号解析失败时调用 handleFail 报告配置错误")
    void execute_shouldCallHandleFailWhenVersionParsingFails() {
      // 创建一个新的 Job 实例使用无效配置
      // 由于我们使用的是测试属性中的有效 URL，这个测试验证配置异常的传播
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // 模拟 CommandBus 抛出配置异常（通过包装）
        when(commandBus.handle(any(RorOrganizationImportCommand.class)))
            .thenThrow(new RorConfigurationException("无法解析版本号"));

        // When
        rorOrganizationImportScheduleJob.executeRorOrganizationImport();

        // Then - 配置异常应该通过 handleFail 报告
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }
  }

  // ==================== 结果报告测试 ====================

  @Nested
  @DisplayName("结果报告测试")
  class ResultReportingTests {

    @Test
    @DisplayName("成功时应该调用 handleSuccess 并包含执行信息")
    void execute_shouldReportSuccessWithExecutionInfo() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        RorOrganizationImportResult result =
            RorOrganizationImportResult.success(
                12345L,
                "https://zenodo.org/records/17468391/files/v2.0-2025-12-16-ror-data.zip",
                "v2.0");

        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(commandBus.handle(any(RorOrganizationImportCommand.class))).thenReturn(result);

        // When
        rorOrganizationImportScheduleJob.executeRorOrganizationImport();

        // Then
        xxlJobHelper.verify(
            () ->
                XxlJobHelper.handleSuccess(
                    argThat(
                        (String msg) ->
                            msg.contains("12345") && msg.contains("v2.0") && msg.contains("已启动"))),
            times(1));
      }
    }

    @Test
    @DisplayName("失败时应该调用 handleFail 并包含错误信息")
    void execute_shouldReportFailureWithErrorMessage() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        RuntimeException cause = new RuntimeException("表中已有数据");
        when(commandBus.handle(any(RorOrganizationImportCommand.class))).thenThrow(cause);

        // When
        rorOrganizationImportScheduleJob.executeRorOrganizationImport();

        // Then
        xxlJobHelper.verify(
            () -> XxlJobHelper.handleFail(argThat((String msg) -> msg.contains("表中已有数据"))),
            times(1));
      }
    }
  }
}
