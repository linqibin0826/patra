package dev.linqibin.patra.registry.app.converter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.registry.domain.model.aggregate.ProvenanceConfiguration;
import dev.linqibin.patra.registry.domain.model.read.provenance.BatchingConfigQuery;
import dev.linqibin.patra.registry.domain.model.read.provenance.HttpConfigQuery;
import dev.linqibin.patra.registry.domain.model.read.provenance.PaginationConfigQuery;
import dev.linqibin.patra.registry.domain.model.read.provenance.ProvenanceConfigQuery;
import dev.linqibin.patra.registry.domain.model.read.provenance.ProvenanceQuery;
import dev.linqibin.patra.registry.domain.model.read.provenance.RateLimitConfigQuery;
import dev.linqibin.patra.registry.domain.model.read.provenance.RetryConfigQuery;
import dev.linqibin.patra.registry.domain.model.read.provenance.WindowOffsetQuery;
import dev.linqibin.patra.registry.domain.model.vo.provenance.BatchingConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.HttpConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.PaginationConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.Provenance;
import dev.linqibin.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.RetryConfig;
import dev.linqibin.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/// ProvenanceQueryAssembler 单元测试。
///
/// 测试策略: 使用 MapStruct 生成的实现类进行纯 Java 单元测试, 无需 Spring 容器。
///
/// 测试覆盖:
///
/// - 字段映射验证 - 所有字段正确映射
///   - 布尔类型转换 - Boolean 正确处理
///   - JSON 字符串保持 - String 字段正确传递
///   - Null 值处理 - 输入/可选字段为 null 的场景
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ProvenanceQueryAssembler 单元测试")
class ProvenanceQueryAssemblerTest {

  private ProvenanceQueryAssembler assembler;

  @BeforeEach
  void setUp() {
    // 使用 MapStruct 生成的实现类
    assembler = Mappers.getMapper(ProvenanceQueryAssembler.class);
  }

  @Nested
  @DisplayName("toQuery(Provenance) 转换测试")
  class ProvenanceToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 Provenance 为 ProvenanceQuery")
    void shouldConvertCompleteProvenance() {
      // Given: 准备完整的 Provenance 对象
      Provenance provenance =
          new Provenance(
              1L,
              "PUBMED",
              "PubMed",
              "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/",
              "America/New_York",
              "https://www.ncbi.nlm.nih.gov/books/NBK25501/",
              true,
              "ACTIVE");

      // When: 执行转换
      ProvenanceQuery result = assembler.toQuery(provenance);

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
      // Given: 只填充必填字段
      Provenance provenance =
          new Provenance(
              2L,
              "EPMC",
              "Europe PMC",
              null, // 可选
              "UTC",
              null, // 可选
              false,
              "ACTIVE");

      // When: 执行转换
      ProvenanceQuery result = assembler.toQuery(provenance);

      // Then: 验证必填字段存在,可选字段为 null
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
      ProvenanceQuery result = assembler.toQuery((Provenance) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确转换 active 为 true")
    void shouldConvertActiveToTrue() {
      // Given: active 为 true
      Provenance provenance =
          new Provenance(3L, "ARXIV", "arXiv", null, "UTC", null, true, "ACTIVE");

      // When: 执行转换
      ProvenanceQuery result = assembler.toQuery(provenance);

      // Then: 验证 active 正确转换为 true
      assertThat(result.active()).isTrue();
    }

    @Test
    @DisplayName("应该正确转换 active 为 false")
    void shouldConvertActiveToFalse() {
      // Given: active 为 false
      Provenance provenance =
          new Provenance(
              4L, "DEPRECATED_SOURCE", "Deprecated Source", null, "UTC", null, false, "DEPRECATED");

      // When: 执行转换
      ProvenanceQuery result = assembler.toQuery(provenance);

      // Then: 验证 active 正确转换为 false
      assertThat(result.active()).isFalse();
    }
  }

  @Nested
  @DisplayName("toQuery(WindowOffsetConfig) 转换测试")
  class WindowOffsetConfigToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 WindowOffsetConfig 为 Query")
    void shouldConvertCompleteWindowOffsetConfig() {
      // Given: 准备完整的 WindowOffsetConfig 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      WindowOffsetConfig config =
          new WindowOffsetConfig(
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
      WindowOffsetQuery result = assembler.toQuery(config);

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
      // Given: 只填充必填字段
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              11L,
              2L,
              null, // 可选
              Instant.now(),
              null, // 可选
              "CALENDAR",
              1,
              "DAY",
              null, // 可选
              null, // 可选
              null, // 可选
              null, // 可选
              null, // 可选
              null, // 可选
              "DATE",
              "date",
              "yyyy-MM-dd",
              null, // 可选
              null, // 可选
              null); // 可选

      // When: 执行转换
      WindowOffsetQuery result = assembler.toQuery(config);

      // Then: 验证必填字段存在,可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(11L);
      assertThat(result.provenanceId()).isEqualTo(2L);
      assertThat(result.operationType()).isNull();
      assertThat(result.windowModeCode()).isEqualTo("CALENDAR");
      assertThat(result.effectiveTo()).isNull();
      assertThat(result.calendarAlignTo()).isNull();
      assertThat(result.lookbackValue()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      WindowOffsetQuery result = assembler.toQuery((WindowOffsetConfig) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toQuery(PaginationConfig) 转换测试")
  class PaginationConfigToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 PaginationConfig 为 Query")
    void shouldConvertCompletePaginationConfig() {
      // Given: 准备完整的 PaginationConfig 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      PaginationConfig config =
          new PaginationConfig(
              20L, 1L, "HARVEST", effectiveFrom, effectiveTo, "PAGE_NUMBER", 100, 50, "sort", true);

      // When: 执行转换
      PaginationConfigQuery result = assembler.toQuery(config);

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
      assertThat(result.sortingDirection()).isEqualTo(true);
    }

    @Test
    @DisplayName("应该正确处理可选字段为 null 的情况")
    void shouldHandleNullOptionalFields() {
      // Given: 只填充必填字段
      PaginationConfig config =
          new PaginationConfig(
              21L,
              2L,
              null, // 可选
              Instant.now(),
              null, // 可选
              "CURSOR",
              null, // 可选
              null, // 可选
              null, // 可选
              null); // 可选

      // When: 执行转换
      PaginationConfigQuery result = assembler.toQuery(config);

      // Then: 验证必填字段存在,可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(21L);
      assertThat(result.paginationModeCode()).isEqualTo("CURSOR");
      assertThat(result.operationType()).isNull();
      assertThat(result.pageSizeValue()).isNull();
      assertThat(result.sortingDirection()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      PaginationConfigQuery result = assembler.toQuery((PaginationConfig) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toQuery(HttpConfig) 转换测试")
  class HttpConfigToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 HttpConfig 为 Query")
    void shouldConvertCompleteHttpConfig() {
      // Given: 准备完整的 HttpConfig 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");
      String defaultHeadersJson =
          "{\"User-Agent\":\"PatraAPI/1.0\",\"Accept\":\"application/json\"}";

      HttpConfig config =
          new HttpConfig(
              30L,
              1L,
              "HARVEST",
              effectiveFrom,
              effectiveTo,
              defaultHeadersJson,
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
      HttpConfigQuery result = assembler.toQuery(config);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(30L);
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(result.defaultHeadersJson()).isEqualTo(defaultHeadersJson);
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
    @DisplayName("应该正确处理 tlsVerifyEnabled 为 false")
    void shouldHandleTlsVerifyEnabledFalse() {
      // Given: tlsVerifyEnabled 为 false
      HttpConfig config =
          new HttpConfig(
              31L,
              2L,
              null,
              Instant.now(),
              null,
              null,
              null,
              null,
              null,
              false, // 显式 false
              null,
              "HONOR",
              null,
              null,
              null);

      // When: 执行转换
      HttpConfigQuery result = assembler.toQuery(config);

      // Then: 验证 tlsVerifyEnabled 正确转换为 false
      assertThat(result).isNotNull();
      assertThat(result.tlsVerifyEnabled()).isFalse();
    }

    @Test
    @DisplayName("应该正确转换 tlsVerifyEnabled 为 true")
    void shouldConvertTlsVerifyEnabledToTrue() {
      // Given: tlsVerifyEnabled 为 true
      HttpConfig config =
          new HttpConfig(
              32L,
              3L,
              null,
              Instant.now(),
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
      HttpConfigQuery result = assembler.toQuery(config);

      // Then: 验证 tlsVerifyEnabled 正确转换为 true
      assertThat(result.tlsVerifyEnabled()).isTrue();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      HttpConfigQuery result = assembler.toQuery((HttpConfig) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确传递 JSON 字符串字段")
    void shouldPassJsonStringFields() {
      // Given: HttpConfig 包含 JSON 字符串
      String headersJson =
          "{\"Authorization\":\"Bearer token123\",\"Content-Type\":\"application/json\"}";

      HttpConfig config =
          new HttpConfig(
              33L,
              4L,
              null,
              Instant.now(),
              null,
              headersJson,
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
      HttpConfigQuery result = assembler.toQuery(config);

      // Then: 验证 JSON 字符串正确传递(不修改)
      assertThat(result.defaultHeadersJson()).isEqualTo(headersJson);
    }
  }

  @Nested
  @DisplayName("toQuery(BatchingConfig) 转换测试")
  class BatchingConfigToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 BatchingConfig 为 Query")
    void shouldConvertCompleteBatchingConfig() {
      // Given: 准备完整的 BatchingConfig 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      BatchingConfig config =
          new BatchingConfig(40L, 1L, "HARVEST", effectiveFrom, effectiveTo, 100, "id", ",", 200);

      // When: 执行转换
      BatchingConfigQuery result = assembler.toQuery(config);

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
      // Given: 只填充必填字段
      BatchingConfig config =
          new BatchingConfig(
              41L,
              2L,
              null, // 可选
              Instant.now(),
              null, // 可选
              null, // 可选
              null, // 可选
              null, // 可选
              null); // 可选

      // When: 执行转换
      BatchingConfigQuery result = assembler.toQuery(config);

      // Then: 验证必填字段存在,可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(41L);
      assertThat(result.provenanceId()).isEqualTo(2L);
      assertThat(result.operationType()).isNull();
      assertThat(result.detailFetchBatchSize()).isNull();
      assertThat(result.idsParamName()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      BatchingConfigQuery result = assembler.toQuery((BatchingConfig) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toQuery(RetryConfig) 转换测试")
  class RetryConfigToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 RetryConfig 为 Query")
    void shouldConvertCompleteRetryConfig() {
      // Given: 准备完整的 RetryConfig 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");
      String retryHttpStatusJson = "[500,502,503]";
      String giveupHttpStatusJson = "[400,401,403]";

      RetryConfig config =
          new RetryConfig(
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
              retryHttpStatusJson,
              giveupHttpStatusJson,
              true,
              5,
              60000);

      // When: 执行转换
      RetryConfigQuery result = assembler.toQuery(config);

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
      assertThat(result.retryHttpStatusJson()).isEqualTo(retryHttpStatusJson);
      assertThat(result.giveupHttpStatusJson()).isEqualTo(giveupHttpStatusJson);
      assertThat(result.retryOnNetworkError()).isTrue();
      assertThat(result.circuitBreakThreshold()).isEqualTo(5);
      assertThat(result.circuitCooldownMillis()).isEqualTo(60000);
    }

    @Test
    @DisplayName("应该正确处理 retryOnNetworkError 为 false")
    void shouldHandleRetryOnNetworkErrorFalse() {
      // Given: retryOnNetworkError 为 false
      RetryConfig config =
          new RetryConfig(
              51L,
              2L,
              null,
              Instant.now(),
              null,
              3,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              null,
              false, // 显式 false
              null,
              null);

      // When: 执行转换
      RetryConfigQuery result = assembler.toQuery(config);

      // Then: 验证 retryOnNetworkError 正确转换为 false
      assertThat(result).isNotNull();
      assertThat(result.retryOnNetworkError()).isFalse();
    }

    @Test
    @DisplayName("应该正确转换 retryOnNetworkError 为 true")
    void shouldConvertRetryOnNetworkErrorToTrue() {
      // Given: retryOnNetworkError 为 true
      RetryConfig config =
          new RetryConfig(
              52L,
              3L,
              null,
              Instant.now(),
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
      RetryConfigQuery result = assembler.toQuery(config);

      // Then: 验证 retryOnNetworkError 正确转换为 true
      assertThat(result.retryOnNetworkError()).isTrue();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      RetryConfigQuery result = assembler.toQuery((RetryConfig) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确传递 JSON 数组字符串")
    void shouldPassJsonArrayStrings() {
      // Given: RetryConfig 包含 JSON 数组字符串
      String retryStatusJson = "[429,503]";

      RetryConfig config =
          new RetryConfig(
              53L,
              4L,
              null,
              Instant.now(),
              null,
              3,
              "EXP_JITTER",
              null,
              null,
              null,
              null,
              retryStatusJson,
              null,
              false,
              null,
              null);

      // When: 执行转换
      RetryConfigQuery result = assembler.toQuery(config);

      // Then: 验证 JSON 数组字符串正确传递(不修改)
      assertThat(result.retryHttpStatusJson()).isEqualTo(retryStatusJson);
    }
  }

  @Nested
  @DisplayName("toQuery(RateLimitConfig) 转换测试")
  class RateLimitConfigToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 RateLimitConfig 为 Query")
    void shouldConvertCompleteRateLimitConfig() {
      // Given: 准备完整的 RateLimitConfig 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      RateLimitConfig config =
          new RateLimitConfig(60L, 1L, "HARVEST", effectiveFrom, effectiveTo, 10, 3);

      // When: 执行转换
      RateLimitConfigQuery result = assembler.toQuery(config);

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
      // Given: 只填充必填字段
      RateLimitConfig config =
          new RateLimitConfig(
              61L,
              2L,
              null, // 可选
              Instant.now(),
              null, // 可选
              null, // 可选
              null); // 可选

      // When: 执行转换
      RateLimitConfigQuery result = assembler.toQuery(config);

      // Then: 验证必填字段存在,可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(61L);
      assertThat(result.provenanceId()).isEqualTo(2L);
      assertThat(result.operationType()).isNull();
      assertThat(result.maxConcurrentRequests()).isNull();
      assertThat(result.perCredentialQpsLimit()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      RateLimitConfigQuery result = assembler.toQuery((RateLimitConfig) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toQuery(ProvenanceConfiguration) 转换测试")
  class ProvenanceConfigurationToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 ProvenanceConfiguration 为 Query")
    void shouldConvertCompleteProvenanceConfiguration() {
      // Given: 准备完整的 ProvenanceConfiguration 聚合根
      Provenance provenance =
          new Provenance(1L, "PUBMED", "PubMed", null, "UTC", null, true, "ACTIVE");

      Instant now = Instant.now();
      WindowOffsetConfig windowOffset =
          new WindowOffsetConfig(
              10L,
              1L,
              "HARVEST",
              now,
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
              "date",
              "yyyy-MM-dd",
              null,
              null,
              null);

      PaginationConfig pagination =
          new PaginationConfig(20L, 1L, "HARVEST", now, null, "PAGE_NUMBER", 100, null, null, null);

      HttpConfig http =
          new HttpConfig(
              30L, 1L, "HARVEST", now, null, null, null, null, null, false, null, "HONOR", null,
              null, null);

      BatchingConfig batching =
          new BatchingConfig(40L, 1L, "HARVEST", now, null, null, null, null, null);

      RetryConfig retry =
          new RetryConfig(
              50L, 1L, "HARVEST", now, null, 3, "FIXED", null, null, null, null, null, null, false,
              null, null);

      RateLimitConfig rateLimit = new RateLimitConfig(60L, 1L, "HARVEST", now, null, null, null);

      ProvenanceConfiguration configuration =
          new ProvenanceConfiguration(
              provenance, windowOffset, pagination, http, batching, retry, rateLimit);

      // When: 执行转换
      ProvenanceConfigQuery result = assembler.toQuery(configuration);

      // Then: 验证 ProvenanceConfiguration 正确转换,包含所有子对象
      assertThat(result).isNotNull();
      assertThat(result.provenance()).isNotNull();
      assertThat(result.provenance().id()).isEqualTo(1L);
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
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      ProvenanceConfigQuery result = assembler.toQuery((ProvenanceConfiguration) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }
}
