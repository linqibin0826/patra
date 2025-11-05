package com.patra.ingest.domain.model.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.BatchingConfig;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.HttpConfig;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.PaginationConfig;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.ProvenanceInfo;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.RateLimitConfig;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.RetryConfig;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot.WindowOffsetConfig;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProvenanceConfigSnapshot 配置快照测试")
class ProvenanceConfigSnapshotTest {

  // ==================== 测试数据工厂 ====================

  private static ProvenanceInfo createSampleProvenanceInfo() {
    return new ProvenanceInfo(
        1L,
        "pubmed",
        "PubMed",
        "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/",
        "UTC",
        "https://www.ncbi.nlm.nih.gov/books/NBK25501/",
        true,
        "ACTIVE");
  }

  private static WindowOffsetConfig createSampleWindowOffsetConfig() {
    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    return new WindowOffsetConfig(
        101L,
        1L,
        "FETCH",
        now,
        now.plusSeconds(86400),
        "SLIDING",
        7,
        "DAY",
        "DAY",
        1,
        "DAY",
        1,
        "HOUR",
        300,
        "DATE",
        "publication_date",
        "ISO_INSTANT",
        "publication_date",
        1000,
        3600);
  }

  private static PaginationConfig createSamplePaginationConfig() {
    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    return new PaginationConfig(
        201L,
        1L,
        "FETCH",
        now,
        null,
        "PAGE_NUMBER",
        100,
        10,
        "sort",
        1);
  }

  private static HttpConfig createSampleHttpConfig() {
    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    return new HttpConfig(
        301L,
        1L,
        "FETCH",
        now,
        null,
        "{\"User-Agent\":\"PatraBot/1.0\"}",
        5000,
        30000,
        60000,
        true,
        null,
        "RESPECT",
        300000,
        "Idempotency-Key",
        86400);
  }

  private static BatchingConfig createSampleBatchingConfig() {
    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    return new BatchingConfig(
        401L,
        1L,
        "DETAIL_FETCH",
        now,
        null,
        50,
        "ids",
        ",",
        200);
  }

  private static RetryConfig createSampleRetryConfig() {
    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    return new RetryConfig(
        501L,
        1L,
        "FETCH",
        now,
        null,
        3,
        "EXP_JITTER",
        1000,
        30000,
        2.0,
        0.2,
        "[429,503]",
        "[400,401,403]",
        true,
        5,
        60000);
  }

  private static RateLimitConfig createSampleRateLimitConfig() {
    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    return new RateLimitConfig(
        601L,
        1L,
        "FETCH",
        now,
        null,
        10,
        3);
  }

  private static ProvenanceConfigSnapshot createFullSnapshot() {
    return new ProvenanceConfigSnapshot(
        createSampleProvenanceInfo(),
        createSampleWindowOffsetConfig(),
        createSamplePaginationConfig(),
        createSampleHttpConfig(),
        createSampleBatchingConfig(),
        createSampleRetryConfig(),
        createSampleRateLimitConfig());
  }

  // ==================== ProvenanceConfigSnapshot 测试 ====================

  @Nested
  @DisplayName("构造器验证")
  class ConstructorValidation {

    @Test
    @DisplayName("应该成功创建包含所有配置的完整快照")
    void shouldCreateFullSnapshot() {
      // When
      ProvenanceConfigSnapshot snapshot = createFullSnapshot();

      // Then
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.provenance()).isNotNull();
      assertThat(snapshot.windowOffset()).isNotNull();
      assertThat(snapshot.pagination()).isNotNull();
      assertThat(snapshot.http()).isNotNull();
      assertThat(snapshot.batching()).isNotNull();
      assertThat(snapshot.retry()).isNotNull();
      assertThat(snapshot.rateLimit()).isNotNull();
    }

    @Test
    @DisplayName("应该成功创建仅包含必需 Provenance 信息的最小快照")
    void shouldCreateMinimalSnapshot() {
      // Given
      ProvenanceInfo provenance = createSampleProvenanceInfo();

      // When
      ProvenanceConfigSnapshot snapshot = new ProvenanceConfigSnapshot(
          provenance,
          null,
          null,
          null,
          null,
          null,
          null);

      // Then
      assertThat(snapshot.provenance()).isEqualTo(provenance);
      assertThat(snapshot.windowOffset()).isNull();
      assertThat(snapshot.pagination()).isNull();
      assertThat(snapshot.http()).isNull();
      assertThat(snapshot.batching()).isNull();
      assertThat(snapshot.retry()).isNull();
      assertThat(snapshot.rateLimit()).isNull();
    }

    @Test
    @DisplayName("应该成功创建包含部分配置的快照")
    void shouldCreatePartialSnapshot() {
      // Given
      ProvenanceInfo provenance = createSampleProvenanceInfo();
      PaginationConfig pagination = createSamplePaginationConfig();
      HttpConfig http = createSampleHttpConfig();

      // When
      ProvenanceConfigSnapshot snapshot = new ProvenanceConfigSnapshot(
          provenance,
          null,
          pagination,
          http,
          null,
          null,
          null);

      // Then
      assertThat(snapshot.provenance()).isEqualTo(provenance);
      assertThat(snapshot.windowOffset()).isNull();
      assertThat(snapshot.pagination()).isEqualTo(pagination);
      assertThat(snapshot.http()).isEqualTo(http);
      assertThat(snapshot.batching()).isNull();
      assertThat(snapshot.retry()).isNull();
      assertThat(snapshot.rateLimit()).isNull();
    }
  }

  @Nested
  @DisplayName("ProvenanceInfo 测试")
  class ProvenanceInfoTests {

    @Test
    @DisplayName("应该正确创建 ProvenanceInfo 对象")
    void shouldCreateProvenanceInfo() {
      // Given
      ProvenanceInfo info = createSampleProvenanceInfo();

      // Then
      assertThat(info.id()).isEqualTo(1L);
      assertThat(info.code()).isEqualTo("pubmed");
      assertThat(info.name()).isEqualTo("PubMed");
      assertThat(info.baseUrlDefault()).isEqualTo("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/");
      assertThat(info.timezoneDefault()).isEqualTo("UTC");
      assertThat(info.docsUrl()).isEqualTo("https://www.ncbi.nlm.nih.gov/books/NBK25501/");
      assertThat(info.active()).isTrue();
      assertThat(info.lifecycleStatusCode()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应该支持创建非激活状态的 ProvenanceInfo")
    void shouldCreateInactiveProvenanceInfo() {
      // Given
      ProvenanceInfo info = new ProvenanceInfo(
          2L,
          "crossref",
          "CrossRef",
          "https://api.crossref.org/",
          "UTC",
          "https://www.crossref.org/documentation/",
          false,
          "DEPRECATED");

      // Then
      assertThat(info.active()).isFalse();
      assertThat(info.lifecycleStatusCode()).isEqualTo("DEPRECATED");
    }

    @Test
    @DisplayName("ProvenanceInfo 相同值应该相等")
    void provenanceInfoEqualityForSameValues() {
      // Given
      ProvenanceInfo info1 = createSampleProvenanceInfo();
      ProvenanceInfo info2 = createSampleProvenanceInfo();

      // Then
      assertThat(info1).isEqualTo(info2);
      assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
    }

    @Test
    @DisplayName("ProvenanceInfo 不同 code 应该不相等")
    void provenanceInfoInequalityForDifferentCode() {
      // Given
      ProvenanceInfo info1 = createSampleProvenanceInfo();
      ProvenanceInfo info2 = new ProvenanceInfo(
          1L, "epmc", "EPMC", "https://example.com", "UTC", "https://docs.example.com", true, "ACTIVE");

      // Then
      assertThat(info1).isNotEqualTo(info2);
    }
  }

  @Nested
  @DisplayName("WindowOffsetConfig 测试")
  class WindowOffsetConfigTests {

    @Test
    @DisplayName("应该正确创建 WindowOffsetConfig 对象")
    void shouldCreateWindowOffsetConfig() {
      // Given
      WindowOffsetConfig config = createSampleWindowOffsetConfig();

      // Then
      assertThat(config.id()).isEqualTo(101L);
      assertThat(config.provenanceId()).isEqualTo(1L);
      assertThat(config.operationType()).isEqualTo("FETCH");
      assertThat(config.windowModeCode()).isEqualTo("SLIDING");
      assertThat(config.windowSizeValue()).isEqualTo(7);
      assertThat(config.windowSizeUnitCode()).isEqualTo("DAY");
      assertThat(config.offsetTypeCode()).isEqualTo("DATE");
      assertThat(config.offsetFieldKey()).isEqualTo("publication_date");
    }

    @Test
    @DisplayName("应该支持 CALENDAR 窗口模式")
    void shouldSupportCalendarWindowMode() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      WindowOffsetConfig config = new WindowOffsetConfig(
          102L, 1L, "FETCH", now, null,
          "CALENDAR", 1, "MONTH", "DAY",
          0, "DAY", 0, "HOUR", 0,
          "DATE", "created_at", "ISO_INSTANT", "created_at",
          500, 7200);

      // Then
      assertThat(config.windowModeCode()).isEqualTo("CALENDAR");
      assertThat(config.calendarAlignTo()).isEqualTo("DAY");
      assertThat(config.windowSizeUnitCode()).isEqualTo("MONTH");
    }

    @Test
    @DisplayName("应该支持 COMPOSITE 偏移类型")
    void shouldSupportCompositeOffsetType() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      WindowOffsetConfig config = new WindowOffsetConfig(
          103L, 1L, "FETCH", now, null,
          "SLIDING", 1, "DAY", null,
          0, "HOUR", 0, "HOUR", 0,
          "COMPOSITE", "date_id_composite", null, "publication_date",
          1000, 3600);

      // Then
      assertThat(config.offsetTypeCode()).isEqualTo("COMPOSITE");
      assertThat(config.offsetFieldKey()).isEqualTo("date_id_composite");
    }

    @Test
    @DisplayName("WindowOffsetConfig 相同值应该相等")
    void windowOffsetConfigEqualityForSameValues() {
      // Given
      WindowOffsetConfig config1 = createSampleWindowOffsetConfig();
      WindowOffsetConfig config2 = createSampleWindowOffsetConfig();

      // Then
      assertThat(config1).isEqualTo(config2);
      assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }
  }

  @Nested
  @DisplayName("PaginationConfig 测试")
  class PaginationConfigTests {

    @Test
    @DisplayName("应该正确创建 PAGE_NUMBER 模式的分页配置")
    void shouldCreatePageNumberPaginationConfig() {
      // Given
      PaginationConfig config = createSamplePaginationConfig();

      // Then
      assertThat(config.id()).isEqualTo(201L);
      assertThat(config.provenanceId()).isEqualTo(1L);
      assertThat(config.paginationModeCode()).isEqualTo("PAGE_NUMBER");
      assertThat(config.pageSizeValue()).isEqualTo(100);
      assertThat(config.maxPagesPerExecution()).isEqualTo(10);
      assertThat(config.sortingDirection()).isEqualTo(1); // ASC
    }

    @Test
    @DisplayName("应该支持 CURSOR 分页模式")
    void shouldSupportCursorPaginationMode() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      PaginationConfig config = new PaginationConfig(
          202L, 1L, "FETCH", now, null,
          "CURSOR", null, null, "cursor", 1);

      // Then
      assertThat(config.paginationModeCode()).isEqualTo("CURSOR");
      assertThat(config.pageSizeValue()).isNull();
      assertThat(config.sortFieldParamName()).isEqualTo("cursor");
    }

    @Test
    @DisplayName("应该支持 DESC 排序方向")
    void shouldSupportDescendingSortDirection() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      PaginationConfig config = new PaginationConfig(
          203L, 1L, "FETCH", now, null,
          "PAGE_NUMBER", 50, 20, "sort", 0); // 0 = DESC

      // Then
      assertThat(config.sortingDirection()).isEqualTo(0);
    }

    @Test
    @DisplayName("PaginationConfig 相同值应该相等")
    void paginationConfigEqualityForSameValues() {
      // Given
      PaginationConfig config1 = createSamplePaginationConfig();
      PaginationConfig config2 = createSamplePaginationConfig();

      // Then
      assertThat(config1).isEqualTo(config2);
      assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }
  }

  @Nested
  @DisplayName("HttpConfig 测试")
  class HttpConfigTests {

    @Test
    @DisplayName("应该正确创建 HttpConfig 对象")
    void shouldCreateHttpConfig() {
      // Given
      HttpConfig config = createSampleHttpConfig();

      // Then
      assertThat(config.id()).isEqualTo(301L);
      assertThat(config.provenanceId()).isEqualTo(1L);
      assertThat(config.defaultHeadersJson()).isEqualTo("{\"User-Agent\":\"PatraBot/1.0\"}");
      assertThat(config.timeoutConnectMillis()).isEqualTo(5000);
      assertThat(config.timeoutReadMillis()).isEqualTo(30000);
      assertThat(config.timeoutTotalMillis()).isEqualTo(60000);
      assertThat(config.tlsVerifyEnabled()).isTrue();
      assertThat(config.retryAfterPolicyCode()).isEqualTo("RESPECT");
    }

    @Test
    @DisplayName("应该支持禁用 TLS 验证")
    void shouldSupportDisabledTlsVerification() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      HttpConfig config = new HttpConfig(
          302L, 1L, "FETCH", now, null,
          null, 5000, 30000, 60000,
          false, // TLS 验证禁用
          null, "IGNORE", null, null, null);

      // Then
      assertThat(config.tlsVerifyEnabled()).isFalse();
    }

    @Test
    @DisplayName("应该支持配置代理")
    void shouldSupportProxyConfiguration() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      HttpConfig config = new HttpConfig(
          303L, 1L, "FETCH", now, null,
          null, 5000, 30000, 60000, true,
          "http://proxy.example.com:8080",
          "RESPECT", 300000, null, null);

      // Then
      assertThat(config.proxyUrlValue()).isEqualTo("http://proxy.example.com:8080");
    }

    @Test
    @DisplayName("应该支持 CLAMP 重试后策略")
    void shouldSupportClampRetryAfterPolicy() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      HttpConfig config = new HttpConfig(
          304L, 1L, "FETCH", now, null,
          null, 5000, 30000, 60000, true, null,
          "CLAMP", 60000, null, null);

      // Then
      assertThat(config.retryAfterPolicyCode()).isEqualTo("CLAMP");
      assertThat(config.retryAfterCapMillis()).isEqualTo(60000);
    }

    @Test
    @DisplayName("HttpConfig 相同值应该相等")
    void httpConfigEqualityForSameValues() {
      // Given
      HttpConfig config1 = createSampleHttpConfig();
      HttpConfig config2 = createSampleHttpConfig();

      // Then
      assertThat(config1).isEqualTo(config2);
      assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }
  }

  @Nested
  @DisplayName("BatchingConfig 测试")
  class BatchingConfigTests {

    @Test
    @DisplayName("应该正确创建 BatchingConfig 对象")
    void shouldCreateBatchingConfig() {
      // Given
      BatchingConfig config = createSampleBatchingConfig();

      // Then
      assertThat(config.id()).isEqualTo(401L);
      assertThat(config.provenanceId()).isEqualTo(1L);
      assertThat(config.operationType()).isEqualTo("DETAIL_FETCH");
      assertThat(config.detailFetchBatchSize()).isEqualTo(50);
      assertThat(config.idsParamName()).isEqualTo("ids");
      assertThat(config.idsJoinDelimiter()).isEqualTo(",");
      assertThat(config.maxIdsPerRequest()).isEqualTo(200);
    }

    @Test
    @DisplayName("应该支持自定义 IDs 分隔符")
    void shouldSupportCustomIdsDelimiter() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      BatchingConfig config = new BatchingConfig(
          402L, 1L, "DETAIL_FETCH", now, null,
          100, "id_list", "|", 500);

      // Then
      assertThat(config.idsJoinDelimiter()).isEqualTo("|");
    }

    @Test
    @DisplayName("BatchingConfig 相同值应该相等")
    void batchingConfigEqualityForSameValues() {
      // Given
      BatchingConfig config1 = createSampleBatchingConfig();
      BatchingConfig config2 = createSampleBatchingConfig();

      // Then
      assertThat(config1).isEqualTo(config2);
      assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }
  }

  @Nested
  @DisplayName("RetryConfig 测试")
  class RetryConfigTests {

    @Test
    @DisplayName("应该正确创建 RetryConfig 对象")
    void shouldCreateRetryConfig() {
      // Given
      RetryConfig config = createSampleRetryConfig();

      // Then
      assertThat(config.id()).isEqualTo(501L);
      assertThat(config.provenanceId()).isEqualTo(1L);
      assertThat(config.maxRetryTimes()).isEqualTo(3);
      assertThat(config.backoffPolicyTypeCode()).isEqualTo("EXP_JITTER");
      assertThat(config.initialDelayMillis()).isEqualTo(1000);
      assertThat(config.maxDelayMillis()).isEqualTo(30000);
      assertThat(config.expMultiplierValue()).isEqualTo(2.0);
      assertThat(config.jitterFactorRatio()).isEqualTo(0.2);
      assertThat(config.retryOnNetworkError()).isTrue();
    }

    @Test
    @DisplayName("应该支持 FIXED 退避策略")
    void shouldSupportFixedBackoffPolicy() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      RetryConfig config = new RetryConfig(
          502L, 1L, "FETCH", now, null,
          5, "FIXED", 2000, 2000, null, null,
          "[503]", "[400,401]", true, 3, 30000);

      // Then
      assertThat(config.backoffPolicyTypeCode()).isEqualTo("FIXED");
      assertThat(config.initialDelayMillis()).isEqualTo(2000);
      assertThat(config.maxDelayMillis()).isEqualTo(2000);
    }

    @Test
    @DisplayName("应该支持熔断器配置")
    void shouldSupportCircuitBreakerConfiguration() {
      // Given
      RetryConfig config = createSampleRetryConfig();

      // Then
      assertThat(config.circuitBreakThreshold()).isEqualTo(5);
      assertThat(config.circuitCooldownMillis()).isEqualTo(60000);
    }

    @Test
    @DisplayName("RetryConfig 相同值应该相等")
    void retryConfigEqualityForSameValues() {
      // Given
      RetryConfig config1 = createSampleRetryConfig();
      RetryConfig config2 = createSampleRetryConfig();

      // Then
      assertThat(config1).isEqualTo(config2);
      assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }
  }

  @Nested
  @DisplayName("RateLimitConfig 测试")
  class RateLimitConfigTests {

    @Test
    @DisplayName("应该正确创建 RateLimitConfig 对象")
    void shouldCreateRateLimitConfig() {
      // Given
      RateLimitConfig config = createSampleRateLimitConfig();

      // Then
      assertThat(config.id()).isEqualTo(601L);
      assertThat(config.provenanceId()).isEqualTo(1L);
      assertThat(config.maxConcurrentRequests()).isEqualTo(10);
      assertThat(config.perCredentialQpsLimit()).isEqualTo(3);
    }

    @Test
    @DisplayName("应该支持不限制凭证 QPS")
    void shouldSupportUnlimitedPerCredentialQps() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      RateLimitConfig config = new RateLimitConfig(
          602L, 1L, "FETCH", now, null,
          20, null);

      // Then
      assertThat(config.maxConcurrentRequests()).isEqualTo(20);
      assertThat(config.perCredentialQpsLimit()).isNull();
    }

    @Test
    @DisplayName("RateLimitConfig 相同值应该相等")
    void rateLimitConfigEqualityForSameValues() {
      // Given
      RateLimitConfig config1 = createSampleRateLimitConfig();
      RateLimitConfig config2 = createSampleRateLimitConfig();

      // Then
      assertThat(config1).isEqualTo(config2);
      assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }
  }

  @Nested
  @DisplayName("Record 语义 - Snapshot")
  class SnapshotRecordSemantics {

    @Test
    @DisplayName("相同值的快照应该相等")
    void shouldBeEqualForSameValues() {
      // Given
      ProvenanceConfigSnapshot snapshot1 = createFullSnapshot();
      ProvenanceConfigSnapshot snapshot2 = createFullSnapshot();

      // Then
      assertThat(snapshot1).isEqualTo(snapshot2);
      assertThat(snapshot1.hashCode()).isEqualTo(snapshot2.hashCode());
    }

    @Test
    @DisplayName("不同 ProvenanceInfo 的快照应该不相等")
    void shouldNotBeEqualForDifferentProvenanceInfo() {
      // Given
      ProvenanceInfo differentProvenance = new ProvenanceInfo(
          2L, "epmc", "EPMC", "https://api.epmc.org/", "UTC", "https://docs.epmc.org/", true, "ACTIVE");

      ProvenanceConfigSnapshot snapshot1 = createFullSnapshot();
      ProvenanceConfigSnapshot snapshot2 = new ProvenanceConfigSnapshot(
          differentProvenance,
          createSampleWindowOffsetConfig(),
          createSamplePaginationConfig(),
          createSampleHttpConfig(),
          createSampleBatchingConfig(),
          createSampleRetryConfig(),
          createSampleRateLimitConfig());

      // Then
      assertThat(snapshot1).isNotEqualTo(snapshot2);
    }

    @Test
    @DisplayName("toString 应该包含主要字段")
    void toStringShouldContainMainFields() {
      // Given
      ProvenanceConfigSnapshot snapshot = createFullSnapshot();

      // When
      String result = snapshot.toString();

      // Then
      assertThat(result)
          .contains("ProvenanceConfigSnapshot")
          .contains("provenance")
          .contains("windowOffset")
          .contains("pagination");
    }

    @Test
    @DisplayName("与 null 比较应该返回 false")
    void shouldNotEqualNull() {
      // Given
      ProvenanceConfigSnapshot snapshot = createFullSnapshot();

      // Then
      assertThat(snapshot).isNotEqualTo(null);
    }

    @Test
    @DisplayName("与自身比较应该返回 true")
    void shouldEqualSelf() {
      // Given
      ProvenanceConfigSnapshot snapshot = createFullSnapshot();

      // Then
      assertThat(snapshot).isEqualTo(snapshot);
    }
  }

  @Nested
  @DisplayName("不可变性验证")
  class ImmutabilityValidation {

    @Test
    @DisplayName("快照应该是不可变的 - Record 类型")
    void snapshotShouldBeImmutable() {
      // Given
      ProvenanceInfo originalProvenance = createSampleProvenanceInfo();
      ProvenanceConfigSnapshot snapshot = new ProvenanceConfigSnapshot(
          originalProvenance, null, null, null, null, null, null);

      // When - 获取 provenance 引用
      ProvenanceInfo retrievedProvenance = snapshot.provenance();

      // Then - Record 是不可变的，但需要验证引用稳定性
      assertThat(retrievedProvenance).isEqualTo(originalProvenance);
      assertThat(retrievedProvenance).isSameAs(originalProvenance);
    }

    @Test
    @DisplayName("嵌套 Record 也应该是不可变的")
    void nestedRecordsShouldBeImmutable() {
      // Given
      WindowOffsetConfig originalConfig = createSampleWindowOffsetConfig();
      ProvenanceConfigSnapshot snapshot = new ProvenanceConfigSnapshot(
          createSampleProvenanceInfo(), originalConfig, null, null, null, null, null);

      // When
      WindowOffsetConfig retrievedConfig = snapshot.windowOffset();

      // Then
      assertThat(retrievedConfig).isEqualTo(originalConfig);
      assertThat(retrievedConfig.windowSizeValue()).isEqualTo(7);
      assertThat(retrievedConfig.windowSizeUnitCode()).isEqualTo("DAY");
    }
  }

  @Nested
  @DisplayName("边界条件")
  class BoundaryConditions {

    @Test
    @DisplayName("应该处理所有配置为 null 的情况（除 Provenance）")
    void shouldHandleAllNullConfigs() {
      // Given
      ProvenanceInfo provenance = createSampleProvenanceInfo();

      // When & Then
      assertThatCode(() -> new ProvenanceConfigSnapshot(
          provenance, null, null, null, null, null, null))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该处理极长的 JSON 字符串")
    void shouldHandleLongJsonStrings() {
      // Given
      String longJson = "{\"header\":\"" + "A".repeat(10000) + "\"}";
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      HttpConfig config = new HttpConfig(
          999L, 1L, "FETCH", now, null,
          longJson, 5000, 30000, 60000, true, null, "RESPECT", 300000, null, null);

      // When
      ProvenanceConfigSnapshot snapshot = new ProvenanceConfigSnapshot(
          createSampleProvenanceInfo(), null, null, config, null, null, null);

      // Then
      assertThat(snapshot.http().defaultHeadersJson()).hasSize(longJson.length());
    }

    @Test
    @DisplayName("应该处理 effectiveTo 为 null 的长期有效配置")
    void shouldHandleNullEffectiveTo() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      PaginationConfig config = new PaginationConfig(
          999L, 1L, "FETCH", now, null, // effectiveTo = null (长期有效)
          "PAGE_NUMBER", 100, 10, "sort", 1);

      // When
      ProvenanceConfigSnapshot snapshot = new ProvenanceConfigSnapshot(
          createSampleProvenanceInfo(), null, config, null, null, null, null);

      // Then
      assertThat(snapshot.pagination().effectiveTo()).isNull();
    }

    @Test
    @DisplayName("应该处理 operationType 为 null 的通用配置")
    void shouldHandleNullOperationType() {
      // Given
      Instant now = Instant.parse("2025-01-01T00:00:00Z");
      RetryConfig config = new RetryConfig(
          999L, 1L, null, // operationType = null (通用配置)
          now, null, 3, "FIXED", 1000, 5000, null, null,
          "[429]", "[400]", true, 5, 60000);

      // When
      ProvenanceConfigSnapshot snapshot = new ProvenanceConfigSnapshot(
          createSampleProvenanceInfo(), null, null, null, null, config, null);

      // Then
      assertThat(snapshot.retry().operationType()).isNull();
    }
  }

  @Nested
  @DisplayName("实际业务场景")
  class RealWorldScenarios {

    @Test
    @DisplayName("PubMed 完整配置快照")
    void pubmedFullConfigSnapshot() {
      // Given - PubMed 实际配置场景
      ProvenanceInfo pubmedProvenance = new ProvenanceInfo(
          1L,
          "pubmed",
          "PubMed",
          "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/",
          "America/New_York",
          "https://www.ncbi.nlm.nih.gov/books/NBK25501/",
          true,
          "ACTIVE");

      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      WindowOffsetConfig windowConfig = new WindowOffsetConfig(
          100L, 1L, "FETCH", effectiveFrom, null,
          "SLIDING", 1, "DAY", null,
          2, "HOUR", 1, "HOUR", 300,
          "DATE", "pubdate", "yyyyMMdd", "pubdate",
          10000, 86400);

      PaginationConfig paginationConfig = new PaginationConfig(
          200L, 1L, "FETCH", effectiveFrom, null,
          "PAGE_NUMBER", 500, 100, "sort", 1);

      HttpConfig httpConfig = new HttpConfig(
          300L, 1L, "FETCH", effectiveFrom, null,
          "{\"User-Agent\":\"PatraBot/1.0\",\"X-Request-ID\":\"auto\"}",
          3000, 20000, 60000, true, null,
          "RESPECT", 600000,
          "X-Idempotency-Key", 3600);

      RetryConfig retryConfig = new RetryConfig(
          400L, 1L, "FETCH", effectiveFrom, null,
          3, "EXP_JITTER", 1000, 32000, 2.0, 0.25,
          "[429,503,504]", "[400,401,403,404]", true, 5, 300000);

      RateLimitConfig rateLimitConfig = new RateLimitConfig(
          500L, 1L, "FETCH", effectiveFrom, null,
          3, 3);

      // When
      ProvenanceConfigSnapshot snapshot = new ProvenanceConfigSnapshot(
          pubmedProvenance,
          windowConfig,
          paginationConfig,
          httpConfig,
          null, // 无批处理配置
          retryConfig,
          rateLimitConfig);

      // Then - 验证完整性
      assertThat(snapshot.provenance().code()).isEqualTo("pubmed");
      assertThat(snapshot.windowOffset().windowSizeValue()).isEqualTo(1);
      assertThat(snapshot.pagination().pageSizeValue()).isEqualTo(500);
      assertThat(snapshot.http().timeoutReadMillis()).isEqualTo(20000);
      assertThat(snapshot.batching()).isNull();
      assertThat(snapshot.retry().maxRetryTimes()).isEqualTo(3);
      assertThat(snapshot.rateLimit().perCredentialQpsLimit()).isEqualTo(3);
    }

    @Test
    @DisplayName("EPMC 批量详情获取配置快照")
    void epmcBatchDetailFetchSnapshot() {
      // Given - EPMC 批量详情获取场景
      ProvenanceInfo epmcProvenance = new ProvenanceInfo(
          2L,
          "epmc",
          "Europe PMC",
          "https://www.ebi.ac.uk/europepmc/webservices/rest/",
          "UTC",
          "https://europepmc.org/RestfulWebService",
          true,
          "ACTIVE");

      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      BatchingConfig batchingConfig = new BatchingConfig(
          100L, 2L, "DETAIL_FETCH", effectiveFrom, null,
          100, "ext_ids", ",", 1000);

      HttpConfig httpConfig = new HttpConfig(
          200L, 2L, "DETAIL_FETCH", effectiveFrom, null,
          "{\"Accept\":\"application/json\"}",
          5000, 30000, 90000, true, null,
          "CLAMP", 120000, null, null);

      RetryConfig retryConfig = new RetryConfig(
          300L, 2L, "DETAIL_FETCH", effectiveFrom, null,
          5, "EXP_JITTER", 500, 16000, 2.0, 0.3,
          "[429,503]", "[400,404]", true, 10, 600000);

      // When
      ProvenanceConfigSnapshot snapshot = new ProvenanceConfigSnapshot(
          epmcProvenance,
          null, // 无窗口配置
          null, // 无分页配置
          httpConfig,
          batchingConfig,
          retryConfig,
          null); // 无速率限制配置

      // Then
      assertThat(snapshot.provenance().code()).isEqualTo("epmc");
      assertThat(snapshot.windowOffset()).isNull();
      assertThat(snapshot.batching().maxIdsPerRequest()).isEqualTo(1000);
      assertThat(snapshot.http().retryAfterPolicyCode()).isEqualTo("CLAMP");
      assertThat(snapshot.retry().maxRetryTimes()).isEqualTo(5);
      assertThat(snapshot.rateLimit()).isNull();
    }
  }
}
