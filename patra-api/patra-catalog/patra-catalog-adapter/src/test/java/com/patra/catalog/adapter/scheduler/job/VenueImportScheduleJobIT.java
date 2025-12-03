package com.patra.catalog.adapter.scheduler.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.VenueImportUseCase;
import com.patra.catalog.app.usecase.venue.command.VenueImportCommand;
import com.patra.catalog.app.usecase.venue.dto.VenueImportResult;
import com.patra.catalog.domain.model.enums.DataImportMode;
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

/// VenueImportScheduleJob 切片测试。
///
/// 测试策略：
///
/// - 使用最小化 Spring 上下文加载被测 Job
/// - Mock VenueImportUseCase 依赖
/// - 使用真实 ObjectMapper 验证 JSON 解析
/// - Mock 静态方法 XxlJobHelper（框架限制，无法避免）
///
/// @author linqibin
/// @since 0.1.0
@SpringBootTest(classes = VenueImportScheduleJob.class)
@Import(JacksonAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("VenueImportScheduleJob 切片测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueImportScheduleJobIT {

  @Autowired private VenueImportScheduleJob venueImportScheduleJob;

  @MockitoBean private VenueImportUseCase venueImportUseCase;

  @Nested
  @DisplayName("executeVenueImport 方法测试")
  class ExecuteTests {

    @Test
    @DisplayName("应该在空参数时使用默认 INCREMENTAL 模式")
    void execute_shouldUseDefaultModeWithEmptyParam() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        VenueImportResult result =
            VenueImportResult.success(1001L, 42, 255000, DataImportMode.INCREMENTAL);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(venueImportUseCase.importVenues(any(VenueImportCommand.class))).thenReturn(result);

        // When
        venueImportScheduleJob.executeVenueImport();

        // Then
        verify(venueImportUseCase)
            .importVenues(argThat(cmd -> cmd.mode() == DataImportMode.INCREMENTAL));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在 null 参数时使用默认 INCREMENTAL 模式")
    void execute_shouldUseDefaultModeWithNullParam() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        VenueImportResult result =
            VenueImportResult.success(1002L, 42, 255000, DataImportMode.INCREMENTAL);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(null);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(venueImportUseCase.importVenues(any(VenueImportCommand.class))).thenReturn(result);

        // When
        venueImportScheduleJob.executeVenueImport();

        // Then
        verify(venueImportUseCase)
            .importVenues(argThat(cmd -> cmd.mode() == DataImportMode.INCREMENTAL));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该成功解析 JSON 参数并使用 INCREMENTAL 模式")
    void execute_shouldSucceedWithIncrementalMode() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{\"mode\":\"INCREMENTAL\"}";
        VenueImportResult result =
            VenueImportResult.success(1003L, 42, 255000, DataImportMode.INCREMENTAL);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(venueImportUseCase.importVenues(any(VenueImportCommand.class))).thenReturn(result);

        // When
        venueImportScheduleJob.executeVenueImport();

        // Then - 使用真实 ObjectMapper，验证解析后的 Command 参数
        verify(venueImportUseCase)
            .importVenues(argThat(cmd -> cmd.mode() == DataImportMode.INCREMENTAL));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该成功解析 TRUNCATE_REIMPORT 模式")
    void execute_shouldSucceedWithTruncateReimportMode() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{\"mode\":\"TRUNCATE_REIMPORT\"}";
        VenueImportResult result =
            VenueImportResult.success(1004L, 42, 255000, DataImportMode.TRUNCATE_REIMPORT);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(venueImportUseCase.importVenues(any(VenueImportCommand.class))).thenReturn(result);

        // When
        venueImportScheduleJob.executeVenueImport();

        // Then
        verify(venueImportUseCase)
            .importVenues(argThat(cmd -> cmd.mode() == DataImportMode.TRUNCATE_REIMPORT));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该支持小写模式值")
    void execute_shouldSupportLowercaseModeValue() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{\"mode\":\"incremental\"}";
        VenueImportResult result =
            VenueImportResult.success(1005L, 42, 255000, DataImportMode.INCREMENTAL);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(venueImportUseCase.importVenues(any(VenueImportCommand.class))).thenReturn(result);

        // When
        venueImportScheduleJob.executeVenueImport();

        // Then
        verify(venueImportUseCase)
            .importVenues(argThat(cmd -> cmd.mode() == DataImportMode.INCREMENTAL));
      }
    }

    @Test
    @DisplayName("应该在空 JSON 对象时使用默认 INCREMENTAL 模式")
    void execute_shouldUseDefaultModeWithEmptyJsonObject() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{}";
        VenueImportResult result =
            VenueImportResult.success(1006L, 42, 255000, DataImportMode.INCREMENTAL);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(venueImportUseCase.importVenues(any(VenueImportCommand.class))).thenReturn(result);

        // When
        venueImportScheduleJob.executeVenueImport();

        // Then
        verify(venueImportUseCase)
            .importVenues(argThat(cmd -> cmd.mode() == DataImportMode.INCREMENTAL));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }
  }

  @Nested
  @DisplayName("参数验证测试")
  class ParameterValidationTests {

    @Test
    @DisplayName("应该在 JSON 解析失败时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenJsonParseFails() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given - 真实 ObjectMapper 会抛出解析异常
        String invalidJson = "{invalid json}";
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(invalidJson);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When
        venueImportScheduleJob.executeVenueImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
        verify(venueImportUseCase, never()).importVenues(any(VenueImportCommand.class));
      }
    }

    @Test
    @DisplayName("应该在导入模式非法时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenModeIsInvalid() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{\"mode\":\"INVALID_MODE\"}";

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        // When
        venueImportScheduleJob.executeVenueImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
        verify(venueImportUseCase, never()).importVenues(any(VenueImportCommand.class));
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
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        RuntimeException cause = new RuntimeException("数据库连接失败");
        when(venueImportUseCase.importVenues(any(VenueImportCommand.class))).thenThrow(cause);

        // When
        venueImportScheduleJob.executeVenueImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在网络错误时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenNetworkFails() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        String jsonParam = "{\"mode\":\"INCREMENTAL\"}";

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        RuntimeException cause = new RuntimeException("无法连接到 OpenAlex S3");
        when(venueImportUseCase.importVenues(any(VenueImportCommand.class))).thenThrow(cause);

        // When
        venueImportScheduleJob.executeVenueImport();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }
  }
}
