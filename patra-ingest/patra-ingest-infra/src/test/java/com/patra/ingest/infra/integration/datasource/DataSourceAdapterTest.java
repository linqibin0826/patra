package com.patra.ingest.infra.integration.datasource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.infra.integration.datasource.acl.FetchMetadataTranslator;
import com.patra.ingest.infra.registry.ProviderRegistry;
import com.patra.starter.provenance.common.config.*;
import java.lang.reflect.Method;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * DataSourceAdapter 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>测试 ProvenanceConfigSnapshot 到 ProvenanceConfig 的转换逻辑
 *   <li>测试各个子配置的映射方法
 *   <li>测试 null 安全性
 *   <li>验证字段映射的正确性
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("DataSourceAdapter 单元测试")
@ExtendWith(MockitoExtension.class)
class DataSourceAdapterTest {

  @Mock private ProviderRegistry providerRegistry;

  private DataSourceAdapter adapter;

  @BeforeEach
  void setUp() {
    // 注意: 此测试类仅测试 convertToProvenanceConfig 私有方法(HTTP配置转换)
    // 该方法不使用 FetchMetadataTranslator，因此传入真实实例即可
    FetchMetadataTranslator translator = new FetchMetadataTranslator();
    adapter = new DataSourceAdapter(providerRegistry, translator);
  }

  // ========== convertToProvenanceConfig() 完整转换测试 ==========

  @Nested
  @DisplayName("convertToProvenanceConfig() 完整转换测试")
  class ConvertToProvenanceConfigTests {

    @Test
    @DisplayName("应该正确转换包含所有子配置的完整快照")
    void shouldConvertCompleteSnapshot() throws Exception {
      // Given: 创建包含所有子配置的完整快照
      ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
          new ProvenanceConfigSnapshot.ProvenanceInfo(
              1L,
              "PUBMED",
              "PubMed",
              "https://api.pubmed.gov",
              "UTC",
              "https://www.ncbi.nlm.nih.gov/books/NBK25501/",
              true,
              "ACTIVE");

      Instant now = Instant.parse("2025-01-15T10:00:00Z");
      ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset =
          new ProvenanceConfigSnapshot.WindowOffsetConfig(
              10L,
              1L,
              "FETCH_METADATA",
              now,
              null,
              "SLIDING",
              7,
              "DAY",
              null,
              1,
              "HOUR",
              30,
              "MINUTE",
              300,
              "DATE",
              "updated_at",
              "ISO_INSTANT",
              "publication_date",
              1000,
              86400);

      ProvenanceConfigSnapshot.PaginationConfig pagination =
          new ProvenanceConfigSnapshot.PaginationConfig(
              20L, 1L, "FETCH_METADATA", now, null, "PAGE_NUMBER", 100, 10, "sort", 1);

      ProvenanceConfigSnapshot.HttpConfig http =
          new ProvenanceConfigSnapshot.HttpConfig(
              30L,
              1L,
              "FETCH_METADATA",
              now,
              null,
              "{\"Accept\":\"application/json\"}",
              5000,
              30000,
              60000,
              true,
              null,
              "RESPECT",
              300000,
              "Idempotency-Key",
              3600);

      ProvenanceConfigSnapshot.BatchingConfig batching =
          new ProvenanceConfigSnapshot.BatchingConfig(
              40L, 1L, "FETCH_METADATA", now, null, 50, "ids", ",", 200);

      ProvenanceConfigSnapshot.RetryConfig retry =
          new ProvenanceConfigSnapshot.RetryConfig(
              50L,
              1L,
              "FETCH_METADATA",
              now,
              null,
              3,
              "EXP_JITTER",
              1000,
              30000,
              2.0,
              0.1,
              "[429,503]",
              "[400,401]",
              true,
              5,
              60000);

      ProvenanceConfigSnapshot.RateLimitConfig rateLimit =
          new ProvenanceConfigSnapshot.RateLimitConfig(60L, 1L, "FETCH_METADATA", now, null, 10, 3);

      ProvenanceConfigSnapshot snapshot =
          new ProvenanceConfigSnapshot(
              provenanceInfo, windowOffset, pagination, http, batching, retry, rateLimit);

      ExecutionContext context = mock(ExecutionContext.class);
      when(context.configSnapshot()).thenReturn(snapshot);

      // When: 调用私有方法 convertToProvenanceConfig
      ProvenanceConfig config = invokeConvertToProvenanceConfig(context);

      // Then: 验证顶层结构
      assertThat(config).isNotNull();
      assertThat(config.baseUrl()).isEqualTo("https://api.pubmed.gov");
      assertThat(config.http()).isNotNull();
      assertThat(config.pagination()).isNotNull();
      assertThat(config.windowOffset()).isNotNull();
      assertThat(config.batching()).isNotNull();
      assertThat(config.retry()).isNotNull();
      assertThat(config.rateLimit()).isNotNull();

      // 验证 HTTP 配置
      assertThat(config.http().defaultHeaders()).containsEntry("Accept", "application/json");
      assertThat(config.http().timeoutConnectMillis()).isEqualTo(5000);
      assertThat(config.http().timeoutReadMillis()).isEqualTo(30000);
      assertThat(config.http().timeoutTotalMillis()).isEqualTo(60000);

      // 验证 Pagination 配置
      assertThat(config.pagination().pageSizeValue()).isEqualTo(100);
      assertThat(config.pagination().maxPagesPerExecution()).isEqualTo(10);

      // 验证 WindowOffset 配置
      assertThat(config.windowOffset().windowModeCode()).isEqualTo("SLIDING");
      assertThat(config.windowOffset().windowSizeValue()).isEqualTo(7);
      assertThat(config.windowOffset().windowSizeUnitCode()).isEqualTo("DAY");

      // 验证 Batching 配置
      assertThat(config.batching().detailFetchBatchSize()).isEqualTo(50);
      assertThat(config.batching().maxIdsPerRequest()).isEqualTo(200);
      assertThat(config.batching().epostThreshold()).isNull();

      // 验证 Retry 配置
      assertThat(config.retry().maxRetryTimes()).isEqualTo(3);
      assertThat(config.retry().initialDelayMillis()).isEqualTo(1000);

      // 验证 RateLimit 配置
      assertThat(config.rateLimit().maxConcurrentRequests()).isEqualTo(10);
      assertThat(config.rateLimit().perCredentialQpsLimit()).isEqualTo(3);
    }

    @Test
    @DisplayName("应该正确转换只包含基础 Provenance 的快照")
    void shouldConvertMinimalSnapshot() throws Exception {
      // Given: 创建只包含基础信息的快照
      ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
          new ProvenanceConfigSnapshot.ProvenanceInfo(
              1L, "CROSSREF", "CrossRef", "https://api.crossref.org", "UTC", null, true, "ACTIVE");

      ProvenanceConfigSnapshot snapshot =
          new ProvenanceConfigSnapshot(provenanceInfo, null, null, null, null, null, null);

      ExecutionContext context = mock(ExecutionContext.class);
      when(context.configSnapshot()).thenReturn(snapshot);

      // When: 调用私有方法
      ProvenanceConfig config = invokeConvertToProvenanceConfig(context);

      // Then: 验证基础信息存在，其他配置为 null
      // 注意: ProvenanceConfig 构造器会自动创建默认的 HttpConfig (http != null ? http : new HttpConfig(...))
      assertThat(config).isNotNull();
      assertThat(config.baseUrl()).isEqualTo("https://api.crossref.org");
      assertThat(config.windowOffset()).isNull();
      assertThat(config.pagination()).isNull();
      assertThat(config.http()).isNotNull(); // ProvenanceConfig 会自动创建默认 HttpConfig
      assertThat(config.http().defaultHeaders()).isEmpty();
      assertThat(config.batching()).isNull();
      assertThat(config.retry()).isNull();
      assertThat(config.rateLimit()).isNull();
    }

    @Test
    @DisplayName("当配置快照为 null 时应该返回 null")
    void shouldReturnNullWhenSnapshotIsNull() throws Exception {
      // Given
      ExecutionContext context = mock(ExecutionContext.class);
      when(context.configSnapshot()).thenReturn(null);

      // When
      ProvenanceConfig config = invokeConvertToProvenanceConfig(context);

      // Then
      assertThat(config).isNull();
    }

    @Test
    @DisplayName("当 ProvenanceInfo 为 null 时应该返回 null")
    void shouldReturnNullWhenProvenanceInfoIsNull() throws Exception {
      // Given
      ProvenanceConfigSnapshot snapshot =
          new ProvenanceConfigSnapshot(null, null, null, null, null, null, null);

      ExecutionContext context = mock(ExecutionContext.class);
      when(context.configSnapshot()).thenReturn(snapshot);

      // When
      ProvenanceConfig config = invokeConvertToProvenanceConfig(context);

      // Then
      assertThat(config).isNull();
    }
  }

  // ========== HTTP Headers JSON 解析测试 ==========

  @Nested
  @DisplayName("parseHeadersJson() 测试")
  class ParseHeadersJsonTests {

    @Test
    @DisplayName("应该正确解析有效的 JSON 字符串")
    void shouldParseValidJson() throws Exception {
      // Given
      ProvenanceConfigSnapshot.HttpConfig http =
          new ProvenanceConfigSnapshot.HttpConfig(
              1L,
              1L,
              null,
              Instant.now(),
              null,
              "{\"Accept\":\"application/json\",\"User-Agent\":\"Patra/1.0\"}",
              5000,
              30000,
              60000,
              true,
              null,
              "RESPECT",
              300000,
              "Idempotency-Key",
              3600);

      ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
          new ProvenanceConfigSnapshot.ProvenanceInfo(
              1L, "TEST", "Test", "https://api.test.com", "UTC", null, true, "ACTIVE");

      ProvenanceConfigSnapshot snapshot =
          new ProvenanceConfigSnapshot(provenanceInfo, null, null, http, null, null, null);

      ExecutionContext context = mock(ExecutionContext.class);
      when(context.configSnapshot()).thenReturn(snapshot);

      // When
      ProvenanceConfig config = invokeConvertToProvenanceConfig(context);

      // Then
      assertThat(config.http()).isNotNull();
      assertThat(config.http().defaultHeaders())
          .containsEntry("Accept", "application/json")
          .containsEntry("User-Agent", "Patra/1.0");
    }

    @Test
    @DisplayName("当 JSON 为 null 时应该返回空 Map")
    void shouldReturnEmptyMapWhenJsonIsNull() throws Exception {
      // Given
      ProvenanceConfigSnapshot.HttpConfig http =
          new ProvenanceConfigSnapshot.HttpConfig(
              1L,
              1L,
              null,
              Instant.now(),
              null,
              null,
              5000,
              30000,
              60000,
              true,
              null,
              "RESPECT",
              300000,
              "Idempotency-Key",
              3600);

      ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
          new ProvenanceConfigSnapshot.ProvenanceInfo(
              1L, "TEST", "Test", "https://api.test.com", "UTC", null, true, "ACTIVE");

      ProvenanceConfigSnapshot snapshot =
          new ProvenanceConfigSnapshot(provenanceInfo, null, null, http, null, null, null);

      ExecutionContext context = mock(ExecutionContext.class);
      when(context.configSnapshot()).thenReturn(snapshot);

      // When
      ProvenanceConfig config = invokeConvertToProvenanceConfig(context);

      // Then
      assertThat(config.http()).isNotNull();
      assertThat(config.http().defaultHeaders()).isEmpty();
    }

    @Test
    @DisplayName("当 JSON 为空字符串时应该返回空 Map")
    void shouldReturnEmptyMapWhenJsonIsBlank() throws Exception {
      // Given
      ProvenanceConfigSnapshot.HttpConfig http =
          new ProvenanceConfigSnapshot.HttpConfig(
              1L,
              1L,
              null,
              Instant.now(),
              null,
              "  ",
              5000,
              30000,
              60000,
              true,
              null,
              "RESPECT",
              300000,
              "Idempotency-Key",
              3600);

      ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
          new ProvenanceConfigSnapshot.ProvenanceInfo(
              1L, "TEST", "Test", "https://api.test.com", "UTC", null, true, "ACTIVE");

      ProvenanceConfigSnapshot snapshot =
          new ProvenanceConfigSnapshot(provenanceInfo, null, null, http, null, null, null);

      ExecutionContext context = mock(ExecutionContext.class);
      when(context.configSnapshot()).thenReturn(snapshot);

      // When
      ProvenanceConfig config = invokeConvertToProvenanceConfig(context);

      // Then
      assertThat(config.http()).isNotNull();
      assertThat(config.http().defaultHeaders()).isEmpty();
    }

    @Test
    @DisplayName("当 JSON 格式错误时应该返回空 Map")
    void shouldReturnEmptyMapWhenJsonIsInvalid() throws Exception {
      // Given
      ProvenanceConfigSnapshot.HttpConfig http =
          new ProvenanceConfigSnapshot.HttpConfig(
              1L,
              1L,
              null,
              Instant.now(),
              null,
              "{invalid json}",
              5000,
              30000,
              60000,
              true,
              null,
              "RESPECT",
              300000,
              "Idempotency-Key",
              3600);

      ProvenanceConfigSnapshot.ProvenanceInfo provenanceInfo =
          new ProvenanceConfigSnapshot.ProvenanceInfo(
              1L, "TEST", "Test", "https://api.test.com", "UTC", null, true, "ACTIVE");

      ProvenanceConfigSnapshot snapshot =
          new ProvenanceConfigSnapshot(provenanceInfo, null, null, http, null, null, null);

      ExecutionContext context = mock(ExecutionContext.class);
      when(context.configSnapshot()).thenReturn(snapshot);

      // When
      ProvenanceConfig config = invokeConvertToProvenanceConfig(context);

      // Then
      assertThat(config.http()).isNotNull();
      assertThat(config.http().defaultHeaders()).isEmpty();
    }
  }

  // ========== 辅助方法 ==========

  /** 使用反射调用私有方法 convertToProvenanceConfig */
  private ProvenanceConfig invokeConvertToProvenanceConfig(ExecutionContext context)
      throws Exception {
    Method method =
        DataSourceAdapter.class.getDeclaredMethod(
            "convertToProvenanceConfig", ExecutionContext.class);
    method.setAccessible(true);
    return (ProvenanceConfig) method.invoke(adapter, context);
  }
}
