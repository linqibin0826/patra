package com.patra.ingest.infra.integration.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.exception.IngestConfigurationException;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.infra.integration.registry.converter.ProvenanceConfigSnapshotConverter;
import com.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.dto.provenance.ProvenanceResp;
import com.patra.registry.api.endpoint.ProvenanceEndpoint;
import dev.linqibin.commons.error.remote.RemoteCallException;
import dev.linqibin.commons.error.trait.ErrorTrait;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// PatraRegistryAdapter 单元测试。
///
/// 测试策略: 使用 Mockito mock 所有外部依赖，测试适配器的转换逻辑和异常处理。
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("PatraRegistryAdapter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class PatraRegistryAdapterTest {

  @Mock private ProvenanceEndpoint provenanceEndpoint;

  @Mock private ProvenanceConfigSnapshotConverter converter;

  @InjectMocks private PatraRegistryAdapter adapter;

  private static final ProvenanceCode PROVENANCE_CODE = ProvenanceCode.PUBMED;
  private static final OperationCode OPERATION_CODE = OperationCode.HARVEST;

  @Test
  @DisplayName("正常场景 - 成功获取配置并转换")
  void shouldFetchConfigSuccessfully() {
    // Given: 准备模拟的注册中心响应和转换结果
    ProvenanceConfigResp mockResp = createMockProvenanceConfigResp();
    ProvenanceConfigSnapshot expectedSnapshot = createMockSnapshot();

    when(provenanceEndpoint.getConfiguration(
            eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class)))
        .thenReturn(mockResp);
    when(converter.convert(mockResp)).thenReturn(expectedSnapshot);

    // When: 调用 fetchConfig
    ProvenanceConfigSnapshot result = adapter.fetchConfig(PROVENANCE_CODE, OPERATION_CODE);

    // Then: 验证返回正确的快照
    assertThat(result).isNotNull().isEqualTo(expectedSnapshot);
    verify(provenanceEndpoint)
        .getConfiguration(eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class));
    verify(converter).convert(mockResp);
  }

  @Test
  @DisplayName("空响应场景 - 返回最小快照")
  void shouldReturnMinimalSnapshotWhenResponseIsNull() {
    // Given: 注册中心返回 null
    when(provenanceEndpoint.getConfiguration(
            eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class)))
        .thenReturn(null);

    // When: 调用 fetchConfig
    ProvenanceConfigSnapshot result = adapter.fetchConfig(PROVENANCE_CODE, OPERATION_CODE);

    // Then: 验证返回最小快照
    assertThat(result).isNotNull();
    assertThat(result.provenance()).isNotNull();
    assertThat(result.provenance().code()).isEqualTo(PROVENANCE_CODE.getCode());
    assertThat(result.provenance().active()).isTrue();
    assertThat(result.windowOffset()).isNull();
    assertThat(result.pagination()).isNull();
  }

  @Test
  @DisplayName("404 错误场景 - 抛出 IngestConfigurationException")
  void shouldThrowExceptionWhenConfigNotFound() {
    // Given: 模拟 404 错误
    RemoteCallException notFoundException =
        new RemoteCallException(
            "CONFIG_NOT_FOUND",
            404,
            "Configuration not found",
            "http://registry.com/config",
            "trace-123",
            null);

    when(provenanceEndpoint.getConfiguration(
            eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class)))
        .thenThrow(notFoundException);

    // When & Then: 验证抛出正确的异常
    assertThatThrownBy(() -> adapter.fetchConfig(PROVENANCE_CODE, OPERATION_CODE))
        .isInstanceOf(IngestConfigurationException.class)
        .hasMessageContaining("未找到溯源配置")
        .hasMessageContaining("code=PUBMED")
        .hasMessageContaining("operationType=HARVEST")
        .hasCause(notFoundException);
  }

  @Test
  @DisplayName("服务器错误场景 - 降级到最小快照")
  void shouldReturnMinimalSnapshotWhenServerError() {
    // Given: 模拟 500 服务器错误
    RemoteCallException serverError =
        new RemoteCallException(
            "INTERNAL_SERVER_ERROR",
            500,
            "Internal server error",
            "http://registry.com/config",
            "trace-456",
            null);

    when(provenanceEndpoint.getConfiguration(
            eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class)))
        .thenThrow(serverError);

    // When: 调用 fetchConfig
    ProvenanceConfigSnapshot result = adapter.fetchConfig(PROVENANCE_CODE, OPERATION_CODE);

    // Then: 验证返回最小快照（降级）
    assertThat(result).isNotNull();
    assertThat(result.provenance()).isNotNull();
    assertThat(result.provenance().code()).isEqualTo(PROVENANCE_CODE.getCode());
    assertThat(result.provenance().active()).isTrue();
  }

  @Test
  @DisplayName("可重试错误场景 - 降级到最小快照")
  void shouldReturnMinimalSnapshotWhenRetryableError() {
    // Given: 模拟 503 可重试错误
    RemoteCallException retryableError =
        new RemoteCallException(
            "SERVICE_UNAVAILABLE",
            503,
            "Service temporarily unavailable",
            "http://registry.com/config",
            "trace-789",
            null);

    when(provenanceEndpoint.getConfiguration(
            eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class)))
        .thenThrow(retryableError);

    // When: 调用 fetchConfig
    ProvenanceConfigSnapshot result = adapter.fetchConfig(PROVENANCE_CODE, OPERATION_CODE);

    // Then: 验证返回最小快照（降级）
    assertThat(result).isNotNull();
    assertThat(result.provenance()).isNotNull();
    assertThat(result.provenance().code()).isEqualTo(PROVENANCE_CODE.getCode());
  }

  // ===== ErrorTrait 语义判断测试（优先于 HTTP 状态码） =====

  @Test
  @DisplayName("ErrorTrait NOT_FOUND - 优先使用语义判断抛出异常")
  void shouldThrowExceptionWhenNotFoundTrait() {
    // Given: 模拟携带 NOT_FOUND trait 的异常（HTTP 状态码故意不是 404，验证语义判断优先）
    RemoteCallException notFoundException =
        new RemoteCallException(
            "REG-0404",
            422, // 即使状态码不是 404，也应该根据 trait 判断
            "Resource not found",
            "http://registry.com/config",
            "trace-trait-404",
            null,
            Set.<ErrorTrait>of(StandardErrorTrait.NOT_FOUND));

    when(provenanceEndpoint.getConfiguration(
            eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class)))
        .thenThrow(notFoundException);

    // When & Then: 验证优先使用 ErrorTrait 语义判断
    assertThatThrownBy(() -> adapter.fetchConfig(PROVENANCE_CODE, OPERATION_CODE))
        .isInstanceOf(IngestConfigurationException.class)
        .hasMessageContaining("未找到溯源配置")
        .hasCause(notFoundException);
  }

  @Test
  @DisplayName("ErrorTrait DEP_UNAVAILABLE - 优先使用语义判断降级")
  void shouldReturnMinimalSnapshotWhenDepUnavailableTrait() {
    // Given: 模拟携带 DEP_UNAVAILABLE trait 的异常
    RemoteCallException depUnavailableException =
        new RemoteCallException(
            "REG-0503",
            503,
            "Dependency unavailable",
            "http://registry.com/config",
            "trace-trait-dep",
            null,
            Set.<ErrorTrait>of(StandardErrorTrait.DEP_UNAVAILABLE));

    when(provenanceEndpoint.getConfiguration(
            eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class)))
        .thenThrow(depUnavailableException);

    // When: 调用 fetchConfig
    ProvenanceConfigSnapshot result = adapter.fetchConfig(PROVENANCE_CODE, OPERATION_CODE);

    // Then: 验证优先使用 ErrorTrait 语义判断降级
    assertThat(result).isNotNull();
    assertThat(result.provenance()).isNotNull();
    assertThat(result.provenance().code()).isEqualTo(PROVENANCE_CODE.getCode());
  }

  @Test
  @DisplayName("ErrorTrait TIMEOUT - 优先使用语义判断降级")
  void shouldReturnMinimalSnapshotWhenTimeoutTrait() {
    // Given: 模拟携带 TIMEOUT trait 的异常（状态码可能是 408 或 504）
    RemoteCallException timeoutException =
        new RemoteCallException(
            "REG-0504",
            504,
            "Gateway timeout",
            "http://registry.com/config",
            "trace-trait-timeout",
            null,
            Set.<ErrorTrait>of(StandardErrorTrait.TIMEOUT));

    when(provenanceEndpoint.getConfiguration(
            eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class)))
        .thenThrow(timeoutException);

    // When: 调用 fetchConfig
    ProvenanceConfigSnapshot result = adapter.fetchConfig(PROVENANCE_CODE, OPERATION_CODE);

    // Then: 验证优先使用 ErrorTrait 语义判断降级
    assertThat(result).isNotNull();
    assertThat(result.provenance()).isNotNull();
    assertThat(result.provenance().code()).isEqualTo(PROVENANCE_CODE.getCode());
  }

  // ===== HTTP 状态码 Fallback 测试 =====

  @Test
  @DisplayName("客户端错误场景 - 抛出 IngestConfigurationException")
  void shouldThrowExceptionWhenClientError() {
    // Given: 模拟 400 客户端错误
    RemoteCallException clientError =
        new RemoteCallException(
            "INVALID_REQUEST",
            400,
            "Invalid request parameters",
            "http://registry.com/config",
            "trace-999",
            null);

    when(provenanceEndpoint.getConfiguration(
            eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class)))
        .thenThrow(clientError);

    // When & Then: 验证抛出正确的异常
    assertThatThrownBy(() -> adapter.fetchConfig(PROVENANCE_CODE, OPERATION_CODE))
        .isInstanceOf(IngestConfigurationException.class)
        .hasMessageContaining("注册中心客户端错误")
        .hasMessageContaining("httpStatus=400")
        .hasMessageContaining("errorCode=INVALID_REQUEST")
        .hasCause(clientError);
  }

  @Test
  @DisplayName("意外异常场景 - 抛出 IngestConfigurationException")
  void shouldThrowExceptionWhenUnexpectedError() {
    // Given: 模拟意外异常
    RuntimeException unexpectedException = new RuntimeException("Unexpected database error");

    when(provenanceEndpoint.getConfiguration(
            eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class)))
        .thenThrow(unexpectedException);

    // When & Then: 验证抛出正确的异常
    assertThatThrownBy(() -> adapter.fetchConfig(PROVENANCE_CODE, OPERATION_CODE))
        .isInstanceOf(IngestConfigurationException.class)
        .hasMessageContaining("获取配置时发生意外错误")
        .hasMessageContaining("code=PUBMED")
        .hasMessageContaining("operationType=HARVEST")
        .hasCause(unexpectedException);
  }

  @Test
  @DisplayName("转换器异常场景 - 抛出 IngestConfigurationException")
  void shouldThrowExceptionWhenConverterFails() {
    // Given: 模拟转换器抛出异常
    ProvenanceConfigResp mockResp = createMockProvenanceConfigResp();
    RuntimeException converterException = new RuntimeException("Conversion failed");

    when(provenanceEndpoint.getConfiguration(
            eq(PROVENANCE_CODE), eq("HARVEST"), any(Instant.class)))
        .thenReturn(mockResp);
    when(converter.convert(mockResp)).thenThrow(converterException);

    // When & Then: 验证抛出正确的异常
    assertThatThrownBy(() -> adapter.fetchConfig(PROVENANCE_CODE, OPERATION_CODE))
        .isInstanceOf(IngestConfigurationException.class)
        .hasMessageContaining("获取配置时发生意外错误")
        .hasCause(converterException);
  }

  // ===== 辅助方法 =====

  private ProvenanceConfigResp createMockProvenanceConfigResp() {
    ProvenanceResp provenanceResp =
        new ProvenanceResp(
            1L,
            PROVENANCE_CODE.getCode(),
            "PubMed",
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/",
            "UTC",
            "https://www.ncbi.nlm.nih.gov/books/NBK25500/",
            true,
            "ACTIVE");

    return new ProvenanceConfigResp(
        provenanceResp,
        null, // windowOffset
        null, // pagination
        null, // http
        null, // batching
        null, // retry
        null // rateLimit
        );
  }

  private ProvenanceConfigSnapshot createMockSnapshot() {
    ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
        new ProvenanceConfigSnapshot.ProvenanceInfo(
            1L,
            PROVENANCE_CODE.getCode(),
            "PubMed",
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/",
            "UTC",
            "https://www.ncbi.nlm.nih.gov/books/NBK25500/",
            true,
            "ACTIVE");

    return new ProvenanceConfigSnapshot(provenanceInfo, null, null, null, null, null, null);
  }
}
