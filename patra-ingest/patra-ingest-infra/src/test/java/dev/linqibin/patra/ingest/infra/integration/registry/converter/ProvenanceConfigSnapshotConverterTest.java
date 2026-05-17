package dev.linqibin.patra.ingest.infra.integration.registry.converter;

import static org.assertj.core.api.Assertions.*;

import dev.linqibin.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import dev.linqibin.patra.registry.api.dto.provenance.*;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ProvenanceConfigSnapshotConverter 单元测试。
///
/// 测试策略：
///
/// - 测试完整配置响应的转换
///   - 测试各个子配置的映射方法
///   - 测试 null 安全性
///   - 验证字段映射的正确性
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ProvenanceConfigSnapshotConverter 单元测试")
class ProvenanceConfigSnapshotConverterTest {

  private final ProvenanceConfigSnapshotConverter converter =
      new ProvenanceConfigSnapshotConverterImpl();

  // ========== convert() 完整转换测试 ==========

  @Nested
  @DisplayName("convert() 完整转换测试")
  class ConvertFullConfigTests {

    @Test
    @DisplayName("应该正确转换包含所有子配置的完整响应")
    void shouldConvertCompleteConfigResp() {
      // Given: 创建包含所有子配置的完整响应
      ProvenanceResp provenanceResp =
          new ProvenanceResp(
              1L,
              "PUBMED",
              "PubMed",
              "https://api.pubmed.gov",
              "UTC",
              "https://www.ncbi.nlm.nih.gov/books/NBK25501/",
              true,
              "ACTIVE");

      Instant now = Instant.parse("2025-01-15T10:00:00Z");
      WindowOffsetResp windowOffsetResp =
          new WindowOffsetResp(
              10L,
              1L,
              "QUERY_SESSION",
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

      PaginationConfigResp paginationResp =
          new PaginationConfigResp(
              20L, 1L, "QUERY_SESSION", now, null, "PAGE_NUMBER", 100, 10, "sort", 1);

      HttpConfigResp httpResp =
          new HttpConfigResp(
              30L,
              1L,
              "QUERY_SESSION",
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

      BatchingConfigResp batchingResp =
          new BatchingConfigResp(40L, 1L, "QUERY_SESSION", now, null, 50, "ids", ",", 200);

      RetryConfigResp retryResp =
          new RetryConfigResp(
              50L,
              1L,
              "QUERY_SESSION",
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

      RateLimitConfigResp rateLimitResp =
          new RateLimitConfigResp(60L, 1L, "QUERY_SESSION", now, null, 10, 3);

      ProvenanceConfigResp configResp =
          new ProvenanceConfigResp(
              provenanceResp,
              windowOffsetResp,
              paginationResp,
              httpResp,
              batchingResp,
              retryResp,
              rateLimitResp);

      // When: 转换为快照
      ProvenanceConfigSnapshot snapshot = converter.convert(configResp);

      // Then: 验证顶层结构
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.provenance()).isNotNull();
      assertThat(snapshot.windowOffset()).isNotNull();
      assertThat(snapshot.pagination()).isNotNull();
      assertThat(snapshot.http()).isNotNull();
      assertThat(snapshot.batching()).isNotNull();
      assertThat(snapshot.retry()).isNotNull();
      assertThat(snapshot.rateLimit()).isNotNull();

      // 验证 Provenance 信息
      assertThat(snapshot.provenance().id()).isEqualTo(1L);
      assertThat(snapshot.provenance().code()).isEqualTo("PUBMED");
      assertThat(snapshot.provenance().name()).isEqualTo("PubMed");
      assertThat(snapshot.provenance().baseUrlDefault()).isEqualTo("https://api.pubmed.gov");
      assertThat(snapshot.provenance().timezoneDefault()).isEqualTo("UTC");
      assertThat(snapshot.provenance().active()).isTrue();
      assertThat(snapshot.provenance().lifecycleStatusCode()).isEqualTo("ACTIVE");

      // 验证 WindowOffset 配置
      assertThat(snapshot.windowOffset().id()).isEqualTo(10L);
      assertThat(snapshot.windowOffset().windowModeCode()).isEqualTo("SLIDING");
      assertThat(snapshot.windowOffset().windowSizeValue()).isEqualTo(7);
      assertThat(snapshot.windowOffset().windowSizeUnitCode()).isEqualTo("DAY");

      // 验证 Pagination 配置
      assertThat(snapshot.pagination().id()).isEqualTo(20L);
      assertThat(snapshot.pagination().paginationModeCode()).isEqualTo("PAGE_NUMBER");
      assertThat(snapshot.pagination().pageSizeValue()).isEqualTo(100);

      // 验证 HTTP 配置
      assertThat(snapshot.http().id()).isEqualTo(30L);
      assertThat(snapshot.http().timeoutConnectMillis()).isEqualTo(5000);
      assertThat(snapshot.http().tlsVerifyEnabled()).isTrue();

      // 验证 Batching 配置
      assertThat(snapshot.batching().id()).isEqualTo(40L);
      assertThat(snapshot.batching().detailFetchBatchSize()).isEqualTo(50);

      // 验证 Retry 配置
      assertThat(snapshot.retry().id()).isEqualTo(50L);
      assertThat(snapshot.retry().maxRetryTimes()).isEqualTo(3);
      assertThat(snapshot.retry().backoffPolicyTypeCode()).isEqualTo("EXP_JITTER");

      // 验证 RateLimit 配置
      assertThat(snapshot.rateLimit().id()).isEqualTo(60L);
      assertThat(snapshot.rateLimit().maxConcurrentRequests()).isEqualTo(10);
    }

    @Test
    @DisplayName("应该正确转换只包含基础 Provenance 的响应")
    void shouldConvertMinimalConfigResp() {
      // Given: 创建只包含基础信息的响应
      ProvenanceResp provenanceResp =
          new ProvenanceResp(
              1L, "CROSSREF", "CrossRef", "https://api.crossref.org", "UTC", null, true, "ACTIVE");

      ProvenanceConfigResp configResp =
          new ProvenanceConfigResp(provenanceResp, null, null, null, null, null, null);

      // When: 转换为快照
      ProvenanceConfigSnapshot snapshot = converter.convert(configResp);

      // Then: 验证基础信息存在，其他配置为 null
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.provenance()).isNotNull();
      assertThat(snapshot.provenance().code()).isEqualTo("CROSSREF");
      assertThat(snapshot.windowOffset()).isNull();
      assertThat(snapshot.pagination()).isNull();
      assertThat(snapshot.http()).isNull();
      assertThat(snapshot.batching()).isNull();
      assertThat(snapshot.retry()).isNull();
      assertThat(snapshot.rateLimit()).isNull();
    }
  }

  // ========== mapProvenanceInfo() 测试 ==========

  @Nested
  @DisplayName("mapProvenanceInfo() 测试")
  class MapProvenanceInfoTests {

    @Test
    @DisplayName("应该正确映射 Provenance 基础信息")
    void shouldMapProvenanceInfo() {
      // Given
      ProvenanceResp resp =
          new ProvenanceResp(
              100L,
              "EPMC",
              "Europe PMC",
              "https://www.ebi.ac.uk/europepmc/webservices/rest",
              "Europe/London",
              "https://europepmc.org/RestfulWebService",
              false,
              "DEPRECATED");

      // When
      ProvenanceConfigSnapshot.ProvenanceInfo info = converter.mapProvenanceInfo(resp);

      // Then
      assertThat(info).isNotNull();
      assertThat(info.id()).isEqualTo(100L);
      assertThat(info.code()).isEqualTo("EPMC");
      assertThat(info.name()).isEqualTo("Europe PMC");
      assertThat(info.baseUrlDefault())
          .isEqualTo("https://www.ebi.ac.uk/europepmc/webservices/rest");
      assertThat(info.timezoneDefault()).isEqualTo("Europe/London");
      assertThat(info.docsUrl()).isEqualTo("https://europepmc.org/RestfulWebService");
      assertThat(info.active()).isFalse();
      assertThat(info.lifecycleStatusCode()).isEqualTo("DEPRECATED");
    }

    @Test
    @DisplayName("当 source 为 null 时应该返回 null")
    void shouldReturnNullWhenSourceIsNull() {
      // When
      ProvenanceConfigSnapshot.ProvenanceInfo info = converter.mapProvenanceInfo(null);

      // Then
      assertThat(info).isNull();
    }
  }

  // ========== mapWindowOffsetConfig() 测试 ==========

  @Nested
  @DisplayName("mapWindowOffsetConfig() 测试")
  class MapWindowOffsetConfigTests {

    @Test
    @DisplayName("应该正确映射 WindowOffset 配置")
    void shouldMapWindowOffsetConfig() {
      // Given
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      WindowOffsetResp resp =
          new WindowOffsetResp(
              1L,
              10L,
              "FETCH_DETAIL",
              effectiveFrom,
              effectiveTo,
              "CALENDAR",
              30,
              "DAY",
              "MONTH",
              2,
              "HOUR",
              15,
              "MINUTE",
              600,
              "COMPOSITE",
              "timestamp_id",
              "epochMillis",
              "created_date",
              5000,
              172800);

      // When
      ProvenanceConfigSnapshot.WindowOffsetConfig config = converter.mapWindowOffsetConfig(resp);

      // Then
      assertThat(config).isNotNull();
      assertThat(config.id()).isEqualTo(1L);
      assertThat(config.provenanceId()).isEqualTo(10L);
      assertThat(config.operationType()).isEqualTo("FETCH_DETAIL");
      assertThat(config.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(config.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(config.windowModeCode()).isEqualTo("CALENDAR");
      assertThat(config.windowSizeValue()).isEqualTo(30);
      assertThat(config.windowSizeUnitCode()).isEqualTo("DAY");
      assertThat(config.calendarAlignTo()).isEqualTo("MONTH");
      assertThat(config.lookbackValue()).isEqualTo(2);
      assertThat(config.lookbackUnitCode()).isEqualTo("HOUR");
      assertThat(config.overlapValue()).isEqualTo(15);
      assertThat(config.overlapUnitCode()).isEqualTo("MINUTE");
      assertThat(config.watermarkLagSeconds()).isEqualTo(600);
      assertThat(config.offsetTypeCode()).isEqualTo("COMPOSITE");
      assertThat(config.offsetFieldKey()).isEqualTo("timestamp_id");
      assertThat(config.offsetDateFormat()).isEqualTo("epochMillis");
      assertThat(config.windowDateFieldKey()).isEqualTo("created_date");
      assertThat(config.maxIdsPerWindow()).isEqualTo(5000);
      assertThat(config.maxWindowSpanSeconds()).isEqualTo(172800);
    }

    @Test
    @DisplayName("当 source 为 null 时应该返回 null")
    void shouldReturnNullWhenSourceIsNull() {
      // When
      ProvenanceConfigSnapshot.WindowOffsetConfig config = converter.mapWindowOffsetConfig(null);

      // Then
      assertThat(config).isNull();
    }
  }

  // ========== mapPaginationConfig() 测试 ==========

  @Nested
  @DisplayName("mapPaginationConfig() 测试")
  class MapPaginationConfigTests {

    @Test
    @DisplayName("应该正确映射 Pagination 配置")
    void shouldMapPaginationConfig() {
      // Given
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      PaginationConfigResp resp =
          new PaginationConfigResp(
              2L, 10L, "LIST_RESOURCES", effectiveFrom, null, "CURSOR", 50, 20, "orderBy", false);

      // When
      ProvenanceConfigSnapshot.PaginationConfig config = converter.mapPaginationConfig(resp);

      // Then
      assertThat(config).isNotNull();
      assertThat(config.id()).isEqualTo(2L);
      assertThat(config.provenanceId()).isEqualTo(10L);
      assertThat(config.operationType()).isEqualTo("LIST_RESOURCES");
      assertThat(config.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(config.effectiveTo()).isNull();
      assertThat(config.paginationModeCode()).isEqualTo("CURSOR");
      assertThat(config.pageSizeValue()).isEqualTo(50);
      assertThat(config.maxPagesPerExecution()).isEqualTo(20);
      assertThat(config.sortFieldParamName()).isEqualTo("orderBy");
      assertThat(config.sortingDirection()).isEqualTo(false);
    }

    @Test
    @DisplayName("当 source 为 null 时应该返回 null")
    void shouldReturnNullWhenSourceIsNull() {
      // When
      ProvenanceConfigSnapshot.PaginationConfig config = converter.mapPaginationConfig(null);

      // Then
      assertThat(config).isNull();
    }
  }

  // ========== mapHttpConfig() 测试 ==========

  @Nested
  @DisplayName("mapHttpConfig() 测试")
  class MapHttpConfigTests {

    @Test
    @DisplayName("应该正确映射 HTTP 配置")
    void shouldMapHttpConfig() {
      // Given
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      HttpConfigResp resp =
          new HttpConfigResp(
              3L,
              10L,
              null,
              effectiveFrom,
              null,
              "{\"User-Agent\":\"Patra/1.0\"}",
              10000,
              60000,
              120000,
              false,
              "http://proxy.example.com:8080",
              "CLAMP",
              600000,
              "X-Idempotency-Key",
              7200);

      // When
      ProvenanceConfigSnapshot.HttpConfig config = converter.mapHttpConfig(resp);

      // Then
      assertThat(config).isNotNull();
      assertThat(config.id()).isEqualTo(3L);
      assertThat(config.provenanceId()).isEqualTo(10L);
      assertThat(config.operationType()).isNull();
      assertThat(config.defaultHeadersJson()).isEqualTo("{\"User-Agent\":\"Patra/1.0\"}");
      assertThat(config.timeoutConnectMillis()).isEqualTo(10000);
      assertThat(config.timeoutReadMillis()).isEqualTo(60000);
      assertThat(config.timeoutTotalMillis()).isEqualTo(120000);
      assertThat(config.tlsVerifyEnabled()).isFalse();
      assertThat(config.proxyUrlValue()).isEqualTo("http://proxy.example.com:8080");
      assertThat(config.retryAfterPolicyCode()).isEqualTo("CLAMP");
      assertThat(config.retryAfterCapMillis()).isEqualTo(600000);
      assertThat(config.idempotencyHeaderName()).isEqualTo("X-Idempotency-Key");
      assertThat(config.idempotencyTtlSeconds()).isEqualTo(7200);
    }

    @Test
    @DisplayName("当 source 为 null 时应该返回 null")
    void shouldReturnNullWhenSourceIsNull() {
      // When
      ProvenanceConfigSnapshot.HttpConfig config = converter.mapHttpConfig(null);

      // Then
      assertThat(config).isNull();
    }
  }

  // ========== mapBatchingConfig() 测试 ==========

  @Nested
  @DisplayName("mapBatchingConfig() 测试")
  class MapBatchingConfigTests {

    @Test
    @DisplayName("应该正确映射 Batching 配置")
    void shouldMapBatchingConfig() {
      // Given
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      BatchingConfigResp resp =
          new BatchingConfigResp(
              4L, 10L, "BATCH_FETCH", effectiveFrom, null, 100, "id_list", "|", 500);

      // When
      ProvenanceConfigSnapshot.BatchingConfig config = converter.mapBatchingConfig(resp);

      // Then
      assertThat(config).isNotNull();
      assertThat(config.id()).isEqualTo(4L);
      assertThat(config.provenanceId()).isEqualTo(10L);
      assertThat(config.operationType()).isEqualTo("BATCH_FETCH");
      assertThat(config.detailFetchBatchSize()).isEqualTo(100);
      assertThat(config.idsParamName()).isEqualTo("id_list");
      assertThat(config.idsJoinDelimiter()).isEqualTo("|");
      assertThat(config.maxIdsPerRequest()).isEqualTo(500);
    }

    @Test
    @DisplayName("当 source 为 null 时应该返回 null")
    void shouldReturnNullWhenSourceIsNull() {
      // When
      ProvenanceConfigSnapshot.BatchingConfig config = converter.mapBatchingConfig(null);

      // Then
      assertThat(config).isNull();
    }
  }

  // ========== mapRetryConfig() 测试 ==========

  @Nested
  @DisplayName("mapRetryConfig() 测试")
  class MapRetryConfigTests {

    @Test
    @DisplayName("应该正确映射 Retry 配置")
    void shouldMapRetryConfig() {
      // Given
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      RetryConfigResp resp =
          new RetryConfigResp(
              5L,
              10L,
              "API_CALL",
              effectiveFrom,
              null,
              5,
              "DECOR_JITTER",
              2000,
              60000,
              1.5,
              0.2,
              "[500,502,503,504]",
              "[401,403,404]",
              false,
              10,
              120000);

      // When
      ProvenanceConfigSnapshot.RetryConfig config = converter.mapRetryConfig(resp);

      // Then
      assertThat(config).isNotNull();
      assertThat(config.id()).isEqualTo(5L);
      assertThat(config.provenanceId()).isEqualTo(10L);
      assertThat(config.operationType()).isEqualTo("API_CALL");
      assertThat(config.maxRetryTimes()).isEqualTo(5);
      assertThat(config.backoffPolicyTypeCode()).isEqualTo("DECOR_JITTER");
      assertThat(config.initialDelayMillis()).isEqualTo(2000);
      assertThat(config.maxDelayMillis()).isEqualTo(60000);
      assertThat(config.expMultiplierValue()).isEqualTo(1.5);
      assertThat(config.jitterFactorRatio()).isEqualTo(0.2);
      assertThat(config.retryHttpStatusJson()).isEqualTo("[500,502,503,504]");
      assertThat(config.giveupHttpStatusJson()).isEqualTo("[401,403,404]");
      assertThat(config.retryOnNetworkError()).isFalse();
      assertThat(config.circuitBreakThreshold()).isEqualTo(10);
      assertThat(config.circuitCooldownMillis()).isEqualTo(120000);
    }

    @Test
    @DisplayName("当 source 为 null 时应该返回 null")
    void shouldReturnNullWhenSourceIsNull() {
      // When
      ProvenanceConfigSnapshot.RetryConfig config = converter.mapRetryConfig(null);

      // Then
      assertThat(config).isNull();
    }
  }

  // ========== mapRateLimitConfig() 测试 ==========

  @Nested
  @DisplayName("mapRateLimitConfig() 测试")
  class MapRateLimitConfigTests {

    @Test
    @DisplayName("应该正确映射 RateLimit 配置")
    void shouldMapRateLimitConfig() {
      // Given
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      RateLimitConfigResp resp =
          new RateLimitConfigResp(6L, 10L, "RATE_LIMITED_OP", effectiveFrom, null, 5, 10);

      // When
      ProvenanceConfigSnapshot.RateLimitConfig config = converter.mapRateLimitConfig(resp);

      // Then
      assertThat(config).isNotNull();
      assertThat(config.id()).isEqualTo(6L);
      assertThat(config.provenanceId()).isEqualTo(10L);
      assertThat(config.operationType()).isEqualTo("RATE_LIMITED_OP");
      assertThat(config.maxConcurrentRequests()).isEqualTo(5);
      assertThat(config.perCredentialQpsLimit()).isEqualTo(10);
    }

    @Test
    @DisplayName("当 source 为 null 时应该返回 null")
    void shouldReturnNullWhenSourceIsNull() {
      // When
      ProvenanceConfigSnapshot.RateLimitConfig config = converter.mapRateLimitConfig(null);

      // Then
      assertThat(config).isNull();
    }
  }
}
