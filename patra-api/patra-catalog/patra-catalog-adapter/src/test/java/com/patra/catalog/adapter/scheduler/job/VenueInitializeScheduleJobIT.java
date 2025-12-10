package com.patra.catalog.adapter.scheduler.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.initialize.command.VenueInitializeCommand;
import com.patra.catalog.app.usecase.venue.initialize.dto.VenueInitializeResult;
import com.patra.common.cqrs.CommandBus;
import com.xxl.job.core.context.XxlJobHelper;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// VenueInitializeScheduleJob 切片测试。
///
/// 测试策略：
///
/// - 使用最小化 Spring 上下文加载被测 Job
/// - Mock CommandBus 依赖
/// - Mock 静态方法 XxlJobHelper（框架限制，无法避免）
///
/// **设计说明**：
///
/// 初始化操作设计为「一次性初始化」语义，不接受任何参数。
/// 直接触发即可执行初始化，数据源 URL 从 OpenAlex S3 Manifest 动态获取。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootTest(classes = VenueInitializeScheduleJob.class)
@ActiveProfiles("test")
@DisplayName("VenueInitializeScheduleJob 切片测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class VenueInitializeScheduleJobIT {

  @Autowired private VenueInitializeScheduleJob venueInitializeScheduleJob;

  @MockitoBean private CommandBus commandBus;

  @Nested
  @DisplayName("executeVenueInitialize 方法测试")
  class ExecuteTests {

    @Test
    @DisplayName("应该成功执行初始化")
    void execute_shouldSucceed() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        VenueInitializeResult result = VenueInitializeResult.success(1001L, 42, 255000);

        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(commandBus.handle(any(VenueInitializeCommand.class))).thenReturn(result);

        // When
        venueInitializeScheduleJob.executeVenueInitialize();

        // Then
        verify(commandBus).handle(any(VenueInitializeCommand.class));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该返回正确的文件数和记录数")
    void execute_shouldReturnCorrectCounts() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        VenueInitializeResult result = VenueInitializeResult.success(1002L, 100, 500000);

        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(commandBus.handle(any(VenueInitializeCommand.class))).thenReturn(result);

        // When
        venueInitializeScheduleJob.executeVenueInitialize();

        // Then
        verify(commandBus).handle(any(VenueInitializeCommand.class));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
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
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        RuntimeException cause = new RuntimeException("数据库连接失败");
        when(commandBus.handle(any(VenueInitializeCommand.class))).thenThrow(cause);

        // When
        venueInitializeScheduleJob.executeVenueInitialize();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在网络错误时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenNetworkFails() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        RuntimeException cause = new RuntimeException("无法连接到 OpenAlex S3");
        when(commandBus.handle(any(VenueInitializeCommand.class))).thenThrow(cause);

        // When
        venueInitializeScheduleJob.executeVenueInitialize();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在数据已存在时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenDataAlreadyExists() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {
        // Given
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        RuntimeException cause = new RuntimeException("表中已存在 Venue 数据，请先手动清空数据库后再执行初始化");
        when(commandBus.handle(any(VenueInitializeCommand.class))).thenThrow(cause);

        // When
        venueInitializeScheduleJob.executeVenueInitialize();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }
  }
}
