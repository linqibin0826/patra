package com.patra.ingest.adapter.scheduler.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import com.patra.common.cqrs.CommandBus;
import com.patra.ingest.adapter.scheduler.param.OutboxRelayJobParam;
import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import com.patra.ingest.app.usecase.relay.dto.RelayReport;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.patra.ingest.domain.messaging.IngestPublishingChannels;
import com.xxl.job.core.context.XxlJobHelper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/// OutboxRelayJob 单元测试。
///
/// 测试策略：
/// - Mock 所有依赖（ObjectMapper, CommandBus, OutboxRelayProperties, Clock）
/// - Mock 静态方法 XxlJobHelper, NetUtil, IdUtil
/// - 测试参数解析、命令构建、成功/失败场景
/// - 验证异常处理和日志记录
///
/// 不使用 @SpringBootTest - 纯单元测试，不依赖 Spring 容器。
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayJob 单元测试")
class OutboxRelayJobTest {

  @Mock private ObjectMapper objectMapper;
  @Mock private CommandBus commandBus;
  @Mock private OutboxRelayProperties relayProperties;
  @Mock private Clock clock;

  @InjectMocks private OutboxRelayJob outboxRelayJob;

  private final Instant fixedInstant = Instant.parse("2025-01-15T10:00:00Z");
  private final Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

  @BeforeEach
  void setUp() {
    when(clock.instant()).thenReturn(fixedInstant);
  }

  @Nested
  @DisplayName("execute 方法测试")
  class ExecuteTests {

    @Test
    @DisplayName("应该在参数为空时成功执行并使用默认配置")
    void execute_shouldSucceedWithEmptyParam() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class);
          MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class);
          MockedStatic<IdUtil> idUtil = mockStatic(IdUtil.class)) {

        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);
        netUtil.when(NetUtil::getLocalHostName).thenReturn("test-host");
        idUtil.when(IdUtil::fastSimpleUUID).thenReturn("uuid-12345");

        RelayReport mockReport = new RelayReport(IngestPublishingChannels.TASK, 10, 8, 1, 1, 0);
        when(commandBus.handle(any(OutboxRelayCommand.class))).thenReturn(mockReport);

        // When
        outboxRelayJob.execute();

        // Then
        verify(commandBus, times(1)).handle(any(OutboxRelayCommand.class));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该成功解析 JSON 参数并执行")
    void execute_shouldSucceedWithJsonParam() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class);
          MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class);
          MockedStatic<IdUtil> idUtil = mockStatic(IdUtil.class)) {

        // Given
        String jsonParam =
            "{\"channel\":\"TASK\",\"batchSize\":50,\"leaseDuration\":\"PT30S\",\"maxAttempts\":5,\"initialBackoff\":\"PT5S\"}";
        OutboxRelayJobParam param = new OutboxRelayJobParam("TASK", 50, "PT30S", 5, "PT5S");

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);
        netUtil.when(NetUtil::getLocalHostName).thenReturn("test-host");
        idUtil.when(IdUtil::fastSimpleUUID).thenReturn("uuid-12345");

        when(objectMapper.readValue(jsonParam, OutboxRelayJobParam.class)).thenReturn(param);

        RelayReport mockReport = new RelayReport(IngestPublishingChannels.TASK, 50, 45, 3, 2, 0);
        when(commandBus.handle(any(OutboxRelayCommand.class))).thenReturn(mockReport);

        // When
        outboxRelayJob.execute();

        // Then
        verify(objectMapper).readValue(jsonParam, OutboxRelayJobParam.class);
        verify(commandBus, times(1)).handle(any(OutboxRelayCommand.class));
        xxlJobHelper.verify(() -> XxlJobHelper.handleSuccess(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在参数解析失败时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenParamParseFails() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {

        // Given
        String invalidJson = "{invalid json}";
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(invalidJson);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(invalidJson, OutboxRelayJobParam.class))
            .thenThrow(new RuntimeException("JSON 解析错误"));

        // When
        outboxRelayJob.execute();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在 UseCase 抛出 OutboxRelayExecutionException 时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenOutboxRelayExecutionExceptionThrown() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class);
          MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class);
          MockedStatic<IdUtil> idUtil = mockStatic(IdUtil.class)) {

        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);
        netUtil.when(NetUtil::getLocalHostName).thenReturn("test-host");
        idUtil.when(IdUtil::fastSimpleUUID).thenReturn("uuid-12345");

        OutboxRelayExecutionException expectedException =
            new OutboxRelayExecutionException("Relay 执行失败", new RuntimeException("底层错误"));
        when(commandBus.handle(any(OutboxRelayCommand.class))).thenThrow(expectedException);

        // When
        outboxRelayJob.execute();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该在 UseCase 抛出其他异常时调用 handleFail 报告错误")
    void execute_shouldCallHandleFailWhenOtherExceptionThrown() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class);
          MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class);
          MockedStatic<IdUtil> idUtil = mockStatic(IdUtil.class)) {

        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);
        netUtil.when(NetUtil::getLocalHostName).thenReturn("test-host");
        idUtil.when(IdUtil::fastSimpleUUID).thenReturn("uuid-12345");

        RuntimeException cause = new RuntimeException("数据库连接失败");
        when(commandBus.handle(any(OutboxRelayCommand.class))).thenThrow(cause);

        // When
        outboxRelayJob.execute();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }
  }

  @Nested
  @DisplayName("参数解析测试")
  class ParameterParsingTests {

    @Test
    @DisplayName("应该正确解析通道名称")
    void shouldParseChannelNameCorrectly() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class);
          MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class);
          MockedStatic<IdUtil> idUtil = mockStatic(IdUtil.class)) {

        // Given
        String jsonParam = "{\"channel\":\"TASK\"}";
        OutboxRelayJobParam param = new OutboxRelayJobParam("TASK", null, null, null, null);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);
        netUtil.when(NetUtil::getLocalHostName).thenReturn("test-host");
        idUtil.when(IdUtil::fastSimpleUUID).thenReturn("uuid-12345");

        when(objectMapper.readValue(jsonParam, OutboxRelayJobParam.class)).thenReturn(param);

        RelayReport mockReport = new RelayReport(IngestPublishingChannels.TASK, 10, 8, 1, 1, 0);
        when(commandBus.handle(any(OutboxRelayCommand.class))).thenReturn(mockReport);

        // When
        outboxRelayJob.execute();

        // Then
        verify(commandBus).handle(any(OutboxRelayCommand.class));
      }
    }

    @Test
    @DisplayName("应该在非法通道值时调用 handleFail 报告错误")
    void shouldCallHandleFailForInvalidChannel() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {

        // Given
        String jsonParam = "{\"channel\":\"INVALID_CHANNEL\"}";
        OutboxRelayJobParam param =
            new OutboxRelayJobParam("INVALID_CHANNEL", null, null, null, null);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, OutboxRelayJobParam.class)).thenReturn(param);

        // When
        outboxRelayJob.execute();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }

    @Test
    @DisplayName("应该正确解析 ISO-8601 持续时间格式")
    void shouldParseISO8601DurationCorrectly() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class);
          MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class);
          MockedStatic<IdUtil> idUtil = mockStatic(IdUtil.class)) {

        // Given
        String jsonParam = "{\"leaseDuration\":\"PT30S\"}";
        OutboxRelayJobParam param = new OutboxRelayJobParam(null, null, "PT30S", null, null);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);
        netUtil.when(NetUtil::getLocalHostName).thenReturn("test-host");
        idUtil.when(IdUtil::fastSimpleUUID).thenReturn("uuid-12345");

        when(objectMapper.readValue(jsonParam, OutboxRelayJobParam.class)).thenReturn(param);

        RelayReport mockReport = new RelayReport(null, 10, 8, 1, 1, 0);
        when(commandBus.handle(any(OutboxRelayCommand.class))).thenReturn(mockReport);

        // When
        outboxRelayJob.execute();

        // Then
        verify(commandBus).handle(any(OutboxRelayCommand.class));
      }
    }

    @Test
    @DisplayName("应该正确解析纯秒数持续时间格式")
    void shouldParseNumericSecondsDurationCorrectly() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class);
          MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class);
          MockedStatic<IdUtil> idUtil = mockStatic(IdUtil.class)) {

        // Given
        String jsonParam = "{\"leaseDuration\":\"60\"}";
        OutboxRelayJobParam param = new OutboxRelayJobParam(null, null, "60", null, null);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);
        netUtil.when(NetUtil::getLocalHostName).thenReturn("test-host");
        idUtil.when(IdUtil::fastSimpleUUID).thenReturn("uuid-12345");

        when(objectMapper.readValue(jsonParam, OutboxRelayJobParam.class)).thenReturn(param);

        RelayReport mockReport = new RelayReport(null, 10, 8, 1, 1, 0);
        when(commandBus.handle(any(OutboxRelayCommand.class))).thenReturn(mockReport);

        // When
        outboxRelayJob.execute();

        // Then
        verify(commandBus).handle(any(OutboxRelayCommand.class));
      }
    }

    @Test
    @DisplayName("应该在非法持续时间格式时调用 handleFail 报告错误")
    void shouldCallHandleFailForInvalidDuration() throws Exception {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class)) {

        // Given
        String jsonParam = "{\"leaseDuration\":\"invalid\"}";
        OutboxRelayJobParam param = new OutboxRelayJobParam(null, null, "invalid", null, null);

        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn(jsonParam);
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);

        when(objectMapper.readValue(jsonParam, OutboxRelayJobParam.class)).thenReturn(param);

        // When
        outboxRelayJob.execute();

        // Then
        xxlJobHelper.verify(() -> XxlJobHelper.handleFail(any(String.class)), times(1));
      }
    }
  }

  @Nested
  @DisplayName("租约所有者构建测试")
  class LeaseOwnerBuildingTests {

    @Test
    @DisplayName("应该构建包含主机名、jobId、threadId 和 UUID 的租约所有者标识符")
    void shouldBuildLeaseOwnerWithAllComponents() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class);
          MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class);
          MockedStatic<IdUtil> idUtil = mockStatic(IdUtil.class)) {

        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(456L);
        netUtil.when(NetUtil::getLocalHostName).thenReturn("production-server");
        idUtil.when(IdUtil::fastSimpleUUID).thenReturn("abc123");

        RelayReport mockReport = new RelayReport(null, 0, 0, 0, 0, 0);
        when(commandBus.handle(any(OutboxRelayCommand.class))).thenReturn(mockReport);

        // When
        outboxRelayJob.execute();

        // Then
        verify(commandBus).handle(any(OutboxRelayCommand.class));
        netUtil.verify(NetUtil::getLocalHostName, times(1));
        xxlJobHelper.verify(XxlJobHelper::getJobId, atLeastOnce());
        idUtil.verify(IdUtil::fastSimpleUUID, times(1));
      }
    }

    @Test
    @DisplayName("应该在主机名为空时使用 unknown 作为默认值")
    void shouldUseUnknownWhenHostnameIsBlank() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class);
          MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class);
          MockedStatic<IdUtil> idUtil = mockStatic(IdUtil.class)) {

        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(789L);
        netUtil.when(NetUtil::getLocalHostName).thenReturn("");
        idUtil.when(IdUtil::fastSimpleUUID).thenReturn("xyz789");

        RelayReport mockReport = new RelayReport(null, 0, 0, 0, 0, 0);
        when(commandBus.handle(any(OutboxRelayCommand.class))).thenReturn(mockReport);

        // When
        outboxRelayJob.execute();

        // Then
        verify(commandBus).handle(any(OutboxRelayCommand.class));
      }
    }
  }

  @Nested
  @DisplayName("成功处理测试")
  class SuccessHandlingTests {

    @Test
    @DisplayName("应该正确记录和报告成功的 relay 执行结果")
    void shouldLogAndReportSuccessfulRelayExecution() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class);
          MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class);
          MockedStatic<IdUtil> idUtil = mockStatic(IdUtil.class)) {

        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);
        netUtil.when(NetUtil::getLocalHostName).thenReturn("test-host");
        idUtil.when(IdUtil::fastSimpleUUID).thenReturn("uuid-12345");

        RelayReport mockReport = new RelayReport(IngestPublishingChannels.TASK, 100, 95, 3, 2, 1);
        when(commandBus.handle(any(OutboxRelayCommand.class))).thenReturn(mockReport);

        // When
        outboxRelayJob.execute();

        // Then
        verify(commandBus).handle(any(OutboxRelayCommand.class));
        xxlJobHelper.verify(
            () ->
                XxlJobHelper.handleSuccess(
                    "Relay 完成 channel=INGEST_TASK fetched=100 published=95 retried=3 failed=2 leaseMissed=1"),
            times(1));
      }
    }

    @Test
    @DisplayName("应该在通道为 null 时使用 ALL_CHANNELS 标识符")
    void shouldUseAllChannelsWhenChannelIsNull() {
      try (MockedStatic<XxlJobHelper> xxlJobHelper = mockStatic(XxlJobHelper.class);
          MockedStatic<NetUtil> netUtil = mockStatic(NetUtil.class);
          MockedStatic<IdUtil> idUtil = mockStatic(IdUtil.class)) {

        // Given
        xxlJobHelper.when(XxlJobHelper::getJobParam).thenReturn("");
        xxlJobHelper.when(XxlJobHelper::getJobId).thenReturn(123L);
        netUtil.when(NetUtil::getLocalHostName).thenReturn("test-host");
        idUtil.when(IdUtil::fastSimpleUUID).thenReturn("uuid-12345");

        RelayReport mockReport = new RelayReport(null, 50, 48, 1, 1, 0);
        when(commandBus.handle(any(OutboxRelayCommand.class))).thenReturn(mockReport);

        // When
        outboxRelayJob.execute();

        // Then
        xxlJobHelper.verify(
            () ->
                XxlJobHelper.handleSuccess(
                    "Relay 完成 channel=ALL_CHANNELS fetched=50 published=48 retried=1 failed=1 leaseMissed=0"),
            times(1));
      }
    }
  }
}
