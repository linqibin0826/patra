package com.patra.registry.adapter.rest.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.registry.api.dto.provenance.BatchingConfigResp;
import com.patra.registry.api.dto.provenance.HttpConfigResp;
import com.patra.registry.api.dto.provenance.PaginationConfigResp;
import com.patra.registry.api.dto.provenance.ProvenanceConfigResp;
import com.patra.registry.api.dto.provenance.ProvenanceResp;
import com.patra.registry.api.dto.provenance.RateLimitConfigResp;
import com.patra.registry.api.dto.provenance.RetryConfigResp;
import com.patra.registry.api.dto.provenance.WindowOffsetResp;
import com.patra.registry.domain.model.read.provenance.BatchingConfigQuery;
import com.patra.registry.domain.model.read.provenance.HttpConfigQuery;
import com.patra.registry.domain.model.read.provenance.PaginationConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import com.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import com.patra.registry.domain.model.read.provenance.RateLimitConfigQuery;
import com.patra.registry.domain.model.read.provenance.RetryConfigQuery;
import com.patra.registry.domain.model.read.provenance.WindowOffsetQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/// ProvenanceApiConverter 单元测试。
/// 
/// 测试策略: 使用 MapStruct 生成的实现类进行纯 Java 单元测试, 无需 Spring 容器。
/// 
/// 测试覆盖:
/// 
/// - 字段映射验证 - Query → Resp 所有字段正确映射
///   - List 转换 - 集合转换保持迭代顺序
///   - Null 值处理 - 输入为 null 或可选字段为 null 的场景
///   - 布尔字段转换 - boolean 类型正确映射
///   - 时间类型转换 - Instant 正确转换
/// 
/// @author linqibin
/// @since 0.1.0
@DisplayName("ProvenanceApiConverter 单元测试")
class ProvenanceApiConverterTest {

  private ProvenanceApiConverter converter;

  @BeforeEach
  void setUp() {
    // 使用 MapStruct 生成的实现类
    converter = Mappers.getMapper(ProvenanceApiConverter.class);
  }

  @Nested
  @DisplayName("toResp(ProvenanceQuery) 转换测试")
  class ProvenanceQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的数据源查询对象为 Resp")
    void shouldConvertCompleteProvenanceQuery() {
      // Given: 准备完整的 Query 对象
      ProvenanceQuery query =
          new ProvenanceQuery(
              1L,
              "PUBMED",
              "PubMed",
              "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/",
              "America/New_York",
              "https://www.ncbi.nlm.nih.gov/books/NBK25501/",
              true,
              "ACTIVE");

      // When: 执行转换
      ProvenanceResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(1L);
      assertThat(result.code()).isEqualTo("PUBMED");
      assertThat(result.name()).isEqualTo("PubMed");
      assertThat(result.baseUrlDefault())
          .isEqualTo("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/");
      assertThat(result.timezoneDefault()).isEqualTo("America/New_York");
      assertThat(result.docsUrl()).isEqualTo("https://www.ncbi.nlm.nih.gov/books/NBK25501/");
      assertThat(result.active()).isTrue();
      assertThat(result.lifecycleStatusCode()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应该正确处理可选字段为 null 的情况")
    void shouldHandleNullOptionalFields() {
      // Given: 可选字段为 null
      ProvenanceQuery query =
          new ProvenanceQuery(2L, "EPMC", "Europe PMC", null, "UTC", null, false, "ACTIVE");

      // When: 执行转换
      ProvenanceResp result = converter.toResp(query);

      // Then: 验证可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(2L);
      assertThat(result.code()).isEqualTo("EPMC");
      assertThat(result.name()).isEqualTo("Europe PMC");
      assertThat(result.baseUrlDefault()).isNull();
      assertThat(result.timezoneDefault()).isEqualTo("UTC");
      assertThat(result.docsUrl()).isNull();
      assertThat(result.active()).isFalse();
      assertThat(result.lifecycleStatusCode()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      ProvenanceResp result = converter.toResp((ProvenanceQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确转换 active 为 true")
    void shouldConvertActiveToTrue() {
      // Given: active 为 true
      ProvenanceQuery query =
          new ProvenanceQuery(3L, "ARXIV", "arXiv", null, "UTC", null, true, "ACTIVE");

      // When: 执行转换
      ProvenanceResp result = converter.toResp(query);

      // Then: 验证 active 为 true
      assertThat(result.active()).isTrue();
    }

    @Test
    @DisplayName("应该正确转换 active 为 false")
    void shouldConvertActiveToFalse() {
      // Given: active 为 false
      ProvenanceQuery query =
          new ProvenanceQuery(
              4L, "DEPRECATED_SOURCE", "Deprecated Source", null, "UTC", null, false, "DEPRECATED");

      // When: 执行转换
      ProvenanceResp result = converter.toResp(query);

      // Then: 验证 active 为 false
      assertThat(result.active()).isFalse();
    }
  }

  @Nested
  @DisplayName("toResp(List<ProvenanceQuery>) 转换测试")
  class ProvenanceQueryListToRespTests {

    @Test
    @DisplayName("应该正确转换数据源查询对象列表")
    void shouldConvertProvenanceQueryList() {
      // Given: 准备查询对象列表
      ProvenanceQuery query1 =
          new ProvenanceQuery(1L, "PUBMED", "PubMed", null, "UTC", null, true, "ACTIVE");
      ProvenanceQuery query2 =
          new ProvenanceQuery(2L, "EPMC", "Europe PMC", null, "UTC", null, true, "ACTIVE");
      List<ProvenanceQuery> queries = List.of(query1, query2);

      // When: 执行转换
      List<ProvenanceResp> result = converter.toResp(queries);

      // Then: 验证列表转换正确
      assertThat(result).hasSize(2);
      assertThat(result.get(0).code()).isEqualTo("PUBMED");
      assertThat(result.get(1).code()).isEqualTo("EPMC");
    }

    @Test
    @DisplayName("当输入列表为空时应该返回空列表")
    void shouldReturnEmptyListWhenInputIsEmpty() {
      // When: 输入空列表
      List<ProvenanceResp> result = converter.toResp(List.of());

      // Then: 返回空列表
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      List<ProvenanceResp> result = converter.toResp((List<ProvenanceQuery>) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toResp(WindowOffsetQuery) 转换测试")
  class WindowOffsetQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的时间窗口偏移查询对象")
    void shouldConvertCompleteWindowOffsetQuery() {
      // Given: 准备完整的 Query 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      WindowOffsetQuery query =
          new WindowOffsetQuery(
              10L,
              1L,
              "HARVEST",
              effectiveFrom,
              effectiveTo,
              "SLIDING",
              7,
              "DAY",
              "DAY",
              1,
              "HOUR",
              15,
              "MINUTE",
              300,
              "DATE",
              "publish_date",
              "yyyy/MM/dd",
              "publish_date",
              10000,
              86400);

      // When: 执行转换
      WindowOffsetResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(10L);
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(result.windowModeCode()).isEqualTo("SLIDING");
      assertThat(result.windowSizeValue()).isEqualTo(7);
      assertThat(result.windowSizeUnitCode()).isEqualTo("DAY");
      assertThat(result.calendarAlignTo()).isEqualTo("DAY");
      assertThat(result.lookbackValue()).isEqualTo(1);
      assertThat(result.lookbackUnitCode()).isEqualTo("HOUR");
      assertThat(result.overlapValue()).isEqualTo(15);
      assertThat(result.overlapUnitCode()).isEqualTo("MINUTE");
      assertThat(result.watermarkLagSeconds()).isEqualTo(300);
      assertThat(result.offsetTypeCode()).isEqualTo("DATE");
      assertThat(result.offsetFieldKey()).isEqualTo("publish_date");
      assertThat(result.offsetDateFormat()).isEqualTo("yyyy/MM/dd");
      assertThat(result.windowDateFieldKey()).isEqualTo("publish_date");
      assertThat(result.maxIdsPerWindow()).isEqualTo(10000);
      assertThat(result.maxWindowSpanSeconds()).isEqualTo(86400);
    }

    @Test
    @DisplayName("应该正确处理可选字段为 null 的情况")
    void shouldHandleNullOptionalFields() {
      // Given: 可选字段为 null
      Instant effectiveFrom = Instant.now();

      WindowOffsetQuery query =
          new WindowOffsetQuery(
              11L,
              2L,
              null,
              effectiveFrom,
              null,
              "CALENDAR",
              1,
              "DAY",
              null,
              null,
              null,
              null,
              null,
              null,
              "DATE",
              "date",
              "yyyy-MM-dd",
              null,
              null,
              null);

      // When: 执行转换
      WindowOffsetResp result = converter.toResp(query);

      // Then: 验证可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(11L);
      assertThat(result.provenanceId()).isEqualTo(2L);
      assertThat(result.operationType()).isNull();
      assertThat(result.effectiveTo()).isNull();
      assertThat(result.windowModeCode()).isEqualTo("CALENDAR");
      assertThat(result.calendarAlignTo()).isNull();
      assertThat(result.lookbackValue()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      WindowOffsetResp result = converter.toResp((WindowOffsetQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toResp(PaginationConfigQuery) 转换测试")
  class PaginationConfigQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的分页配置查询对象")
    void shouldConvertCompletePaginationConfigQuery() {
      // Given: 准备完整的 Query 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      PaginationConfigQuery query =
          new PaginationConfigQuery(
              20L, 1L, "HARVEST", effectiveFrom, effectiveTo, "PAGE_NUMBER", 100, 50, "sort", 1);

      // When: 执行转换
      PaginationConfigResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(20L);
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(result.paginationModeCode()).isEqualTo("PAGE_NUMBER");
      assertThat(result.pageSizeValue()).isEqualTo(100);
      assertThat(result.maxPagesPerExecution()).isEqualTo(50);
      assertThat(result.sortFieldParamName()).isEqualTo("sort");
      assertThat(result.sortingDirection()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该正确处理可选字段为 null 的情况")
    void shouldHandleNullOptionalFields() {
      // Given: 可选字段为 null
      Instant effectiveFrom = Instant.now();

      PaginationConfigQuery query =
          new PaginationConfigQuery(
              21L, 2L, null, effectiveFrom, null, "CURSOR", null, null, null, null);

      // When: 执行转换
      PaginationConfigResp result = converter.toResp(query);

      // Then: 验证可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(21L);
      assertThat(result.operationType()).isNull();
      assertThat(result.pageSizeValue()).isNull();
      assertThat(result.sortingDirection()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      PaginationConfigResp result = converter.toResp((PaginationConfigQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toResp(HttpConfigQuery) 转换测试")
  class HttpConfigQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的 HTTP 配置查询对象")
    void shouldConvertCompleteHttpConfigQuery() {
      // Given: 准备完整的 Query 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      HttpConfigQuery query =
          new HttpConfigQuery(
              30L,
              1L,
              "HARVEST",
              effectiveFrom,
              effectiveTo,
              "{\"User-Agent\":\"PatraAPI/1.0\"}",
              5000,
              30000,
              60000,
              true,
              "http://proxy.example.com:8080",
              "HONOR",
              60000,
              "X-Idempotency-Key",
              86400);

      // When: 执行转换
      HttpConfigResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(30L);
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(result.defaultHeadersJson()).isEqualTo("{\"User-Agent\":\"PatraAPI/1.0\"}");
      assertThat(result.timeoutConnectMillis()).isEqualTo(5000);
      assertThat(result.timeoutReadMillis()).isEqualTo(30000);
      assertThat(result.timeoutTotalMillis()).isEqualTo(60000);
      assertThat(result.tlsVerifyEnabled()).isTrue();
      assertThat(result.proxyUrlValue()).isEqualTo("http://proxy.example.com:8080");
      assertThat(result.retryAfterPolicyCode()).isEqualTo("HONOR");
      assertThat(result.retryAfterCapMillis()).isEqualTo(60000);
      assertThat(result.idempotencyHeaderName()).isEqualTo("X-Idempotency-Key");
      assertThat(result.idempotencyTtlSeconds()).isEqualTo(86400);
    }

    @Test
    @DisplayName("应该正确处理 tlsVerifyEnabled 为 false 的情况")
    void shouldHandleTlsVerifyEnabledAsFalse() {
      // Given: tlsVerifyEnabled 为 false
      Instant effectiveFrom = Instant.now();

      HttpConfigQuery query =
          new HttpConfigQuery(
              31L,
              2L,
              null,
              effectiveFrom,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              "HONOR",
              null,
              null,
              null);

      // When: 执行转换
      HttpConfigResp result = converter.toResp(query);

      // Then: 验证 tlsVerifyEnabled 为 false
      assertThat(result).isNotNull();
      assertThat(result.tlsVerifyEnabled()).isFalse();
    }

    @Test
    @DisplayName("应该正确转换 tlsVerifyEnabled 为 true")
    void shouldConvertTlsVerifyEnabledToTrue() {
      // Given: tlsVerifyEnabled 为 true
      Instant effectiveFrom = Instant.now();

      HttpConfigQuery query =
          new HttpConfigQuery(
              32L,
              3L,
              null,
              effectiveFrom,
              null,
              null,
              null,
              null,
              null,
              true,
              null,
              "HONOR",
              null,
              null,
              null);

      // When: 执行转换
      HttpConfigResp result = converter.toResp(query);

      // Then: 验证 tlsVerifyEnabled 为 true
      assertThat(result.tlsVerifyEnabled()).isTrue();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      HttpConfigResp result = converter.toResp((HttpConfigQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toResp(BatchingConfigQuery) 转换测试")
  class BatchingConfigQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的批处理配置查询对象")
    void shouldConvertCompleteBatchingConfigQuery() {
      // Given: 准备完整的 Query 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      BatchingConfigQuery query =
          new BatchingConfigQuery(
              40L, 1L, "HARVEST", effectiveFrom, effectiveTo, 100, "id", ",", 200);

      // When: 执行转换
      BatchingConfigResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(40L);
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(result.detailFetchBatchSize()).isEqualTo(100);
      assertThat(result.idsParamName()).isEqualTo("id");
      assertThat(result.idsJoinDelimiter()).isEqualTo(",");
      assertThat(result.maxIdsPerRequest()).isEqualTo(200);
    }

    @Test
    @DisplayName("应该正确处理可选字段为 null 的情况")
    void shouldHandleNullOptionalFields() {
      // Given: 可选字段为 null
      Instant effectiveFrom = Instant.now();

      BatchingConfigQuery query =
          new BatchingConfigQuery(41L, 2L, null, effectiveFrom, null, null, null, null, null);

      // When: 执行转换
      BatchingConfigResp result = converter.toResp(query);

      // Then: 验证可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(41L);
      assertThat(result.operationType()).isNull();
      assertThat(result.detailFetchBatchSize()).isNull();
      assertThat(result.idsParamName()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      BatchingConfigResp result = converter.toResp((BatchingConfigQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toResp(RetryConfigQuery) 转换测试")
  class RetryConfigQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的重试配置查询对象")
    void shouldConvertCompleteRetryConfigQuery() {
      // Given: 准备完整的 Query 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      RetryConfigQuery query =
          new RetryConfigQuery(
              50L,
              1L,
              "HARVEST",
              effectiveFrom,
              effectiveTo,
              3,
              "EXPONENTIAL",
              1000,
              30000,
              2.0,
              0.2,
              "[500,502,503]",
              "[400,401,403]",
              true,
              5,
              60000);

      // When: 执行转换
      RetryConfigResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(50L);
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(result.maxRetryTimes()).isEqualTo(3);
      assertThat(result.backoffPolicyTypeCode()).isEqualTo("EXPONENTIAL");
      assertThat(result.initialDelayMillis()).isEqualTo(1000);
      assertThat(result.maxDelayMillis()).isEqualTo(30000);
      assertThat(result.expMultiplierValue()).isEqualTo(2.0);
      assertThat(result.jitterFactorRatio()).isEqualTo(0.2);
      assertThat(result.retryHttpStatusJson()).isEqualTo("[500,502,503]");
      assertThat(result.giveupHttpStatusJson()).isEqualTo("[400,401,403]");
      assertThat(result.retryOnNetworkError()).isTrue();
      assertThat(result.circuitBreakThreshold()).isEqualTo(5);
      assertThat(result.circuitCooldownMillis()).isEqualTo(60000);
    }

    @Test
    @DisplayName("应该正确处理 retryOnNetworkError 为 false 的情况")
    void shouldHandleRetryOnNetworkErrorAsFalse() {
      // Given: retryOnNetworkError 为 false
      Instant effectiveFrom = Instant.now();

      RetryConfigQuery query =
          new RetryConfigQuery(
              51L,
              2L,
              null,
              effectiveFrom,
              null,
              3,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When: 执行转换
      RetryConfigResp result = converter.toResp(query);

      // Then: 验证 retryOnNetworkError 为 false
      assertThat(result).isNotNull();
      assertThat(result.retryOnNetworkError()).isFalse();
    }

    @Test
    @DisplayName("应该正确转换 retryOnNetworkError 为 true")
    void shouldConvertRetryOnNetworkErrorToTrue() {
      // Given: retryOnNetworkError 为 true
      Instant effectiveFrom = Instant.now();

      RetryConfigQuery query =
          new RetryConfigQuery(
              52L,
              3L,
              null,
              effectiveFrom,
              null,
              3,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              null,
              true,
              null,
              null);

      // When: 执行转换
      RetryConfigResp result = converter.toResp(query);

      // Then: 验证 retryOnNetworkError 为 true
      assertThat(result.retryOnNetworkError()).isTrue();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      RetryConfigResp result = converter.toResp((RetryConfigQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toResp(RateLimitConfigQuery) 转换测试")
  class RateLimitConfigQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的速率限制配置查询对象")
    void shouldConvertCompleteRateLimitConfigQuery() {
      // Given: 准备完整的 Query 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      RateLimitConfigQuery query =
          new RateLimitConfigQuery(60L, 1L, "HARVEST", effectiveFrom, effectiveTo, 10, 3);

      // When: 执行转换
      RateLimitConfigResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(60L);
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(result.maxConcurrentRequests()).isEqualTo(10);
      assertThat(result.perCredentialQpsLimit()).isEqualTo(3);
    }

    @Test
    @DisplayName("应该正确处理可选字段为 null 的情况")
    void shouldHandleNullOptionalFields() {
      // Given: 可选字段为 null
      Instant effectiveFrom = Instant.now();

      RateLimitConfigQuery query =
          new RateLimitConfigQuery(61L, 2L, null, effectiveFrom, null, null, null);

      // When: 执行转换
      RateLimitConfigResp result = converter.toResp(query);

      // Then: 验证可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(61L);
      assertThat(result.operationType()).isNull();
      assertThat(result.maxConcurrentRequests()).isNull();
      assertThat(result.perCredentialQpsLimit()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      RateLimitConfigResp result = converter.toResp((RateLimitConfigQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toResp(ProvenanceConfigQuery) 转换测试")
  class ProvenanceConfigQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的数据源配置查询对象")
    void shouldConvertCompleteProvenanceConfigQuery() {
      // Given: 准备完整的 Query 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      ProvenanceQuery provenance =
          new ProvenanceQuery(1L, "PUBMED", "PubMed", null, "UTC", null, true, "ACTIVE");

      WindowOffsetQuery windowOffset =
          new WindowOffsetQuery(
              10L,
              1L,
              "HARVEST",
              effectiveFrom,
              null,
              "SLIDING",
              7,
              "DAY",
              null,
              null,
              null,
              null,
              null,
              null,
              "DATE",
              "publish_date",
              "yyyy/MM/dd",
              null,
              null,
              null);

      PaginationConfigQuery pagination =
          new PaginationConfigQuery(
              20L, 1L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", 100, null, null, null);

      HttpConfigQuery http =
          new HttpConfigQuery(
              30L,
              1L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
              null,
              null,
              null,
              true,
              null,
              "HONOR",
              null,
              null,
              null);

      BatchingConfigQuery batching =
          new BatchingConfigQuery(40L, 1L, "HARVEST", effectiveFrom, null, 100, null, null, null);

      RetryConfigQuery retry =
          new RetryConfigQuery(
              50L,
              1L,
              "HARVEST",
              effectiveFrom,
              null,
              3,
              "EXPONENTIAL",
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      RateLimitConfigQuery rateLimit =
          new RateLimitConfigQuery(60L, 1L, "HARVEST", effectiveFrom, null, null, null);

      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(
              provenance, windowOffset, pagination, http, batching, retry, rateLimit);

      // When: 执行转换
      ProvenanceConfigResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.provenance()).isNotNull();
      assertThat(result.provenance().code()).isEqualTo("PUBMED");
      assertThat(result.windowOffset()).isNotNull();
      assertThat(result.windowOffset().id()).isEqualTo(10L);
      assertThat(result.pagination()).isNotNull();
      assertThat(result.pagination().id()).isEqualTo(20L);
      assertThat(result.http()).isNotNull();
      assertThat(result.http().id()).isEqualTo(30L);
      assertThat(result.batching()).isNotNull();
      assertThat(result.batching().id()).isEqualTo(40L);
      assertThat(result.retry()).isNotNull();
      assertThat(result.retry().id()).isEqualTo(50L);
      assertThat(result.rateLimit()).isNotNull();
      assertThat(result.rateLimit().id()).isEqualTo(60L);
    }

    @Test
    @DisplayName("应该正确处理可选配置维度为 null 的情况")
    void shouldHandleNullOptionalConfigDimensions() {
      // Given: 可选配置维度为 null
      ProvenanceQuery provenance =
          new ProvenanceQuery(2L, "EPMC", "Europe PMC", null, "UTC", null, true, "ACTIVE");

      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(provenance, null, null, null, null, null, null);

      // When: 执行转换
      ProvenanceConfigResp result = converter.toResp(query);

      // Then: 验证可选配置维度为 null
      assertThat(result).isNotNull();
      assertThat(result.provenance()).isNotNull();
      assertThat(result.provenance().code()).isEqualTo("EPMC");
      assertThat(result.windowOffset()).isNull();
      assertThat(result.pagination()).isNull();
      assertThat(result.http()).isNull();
      assertThat(result.batching()).isNull();
      assertThat(result.retry()).isNull();
      assertThat(result.rateLimit()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      ProvenanceConfigResp result = converter.toResp((ProvenanceConfigQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }
}
