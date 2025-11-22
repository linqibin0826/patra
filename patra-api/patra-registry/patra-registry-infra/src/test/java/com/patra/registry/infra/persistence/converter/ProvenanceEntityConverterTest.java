package com.patra.registry.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;
import com.patra.registry.infra.persistence.entity.provenance.RegProvBatchingCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvHttpCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvPaginationCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRateLimitCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRetryCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvWindowOffsetCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/// ProvenanceEntityConverter 单元测试。
///
/// 测试策略: 使用 MapStruct 生成的实现类进行纯 Java 单元测试, 无需 Spring 容器。
///
/// 测试覆盖:
///
/// - 字段映射验证 - 所有字段正确映射
///   - 布尔类型转换 - TINYINT(1) → Boolean 正确处理
///   - JSON 序列化 - JsonNode → String 正确转换
///   - Null 值处理 - 输入/可选字段为 null 的场景
///   - 字段名映射 - code/name 等特殊映射规则
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ProvenanceEntityConverter 单元测试")
class ProvenanceEntityConverterTest {

  private ProvenanceEntityConverter converter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    // 使用 MapStruct 生成的实现类
    converter = Mappers.getMapper(ProvenanceEntityConverter.class);
    objectMapper = new ObjectMapper();
  }

  @Nested
  @DisplayName("toDomain(RegProvenanceDO) 转换测试")
  class RegProvenanceDOToDomainTests {

    @Test
    @DisplayName("应该正确转换完整的数据源 DO 为 Provenance")
    void shouldConvertCompleteProvenanceDO() {
      // Given: 准备完整的 DO 对象
      RegProvenanceDO provenanceDO =
          RegProvenanceDO.builder()
              .id(1L)
              .provenanceCode("PUBMED")
              .provenanceName("PubMed")
              .baseUrlDefault("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/")
              .timezoneDefault("America/New_York")
              .docsUrl("https://www.ncbi.nlm.nih.gov/books/NBK25501/")
              .isActive(true)
              .lifecycleStatusCode("ACTIVE")
              .build();

      // When: 执行转换
      Provenance result = converter.toDomain(provenanceDO);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(1L);
      assertThat(result.code()).isEqualTo("PUBMED"); // provenanceCode → code
      assertThat(result.name()).isEqualTo("PubMed"); // provenanceName → name
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
      RegProvenanceDO provenanceDO =
          RegProvenanceDO.builder()
              .id(2L)
              .provenanceCode("EPMC")
              .provenanceName("Europe PMC")
              .baseUrlDefault(null) // 可选
              .timezoneDefault("UTC") // 必填
              .docsUrl(null) // 可选
              .isActive(null) // 布尔字段 null
              .lifecycleStatusCode("ACTIVE") // 必填
              .build();

      // When: 执行转换
      Provenance result = converter.toDomain(provenanceDO);

      // Then: 验证必填字段存在,可选字段为 null,布尔字段为 false
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(2L);
      assertThat(result.code()).isEqualTo("EPMC");
      assertThat(result.name()).isEqualTo("Europe PMC");
      assertThat(result.baseUrlDefault()).isNull();
      assertThat(result.timezoneDefault()).isEqualTo("UTC"); // 必填字段
      assertThat(result.docsUrl()).isNull();
      assertThat(result.active()).isFalse(); // null → false
      assertThat(result.lifecycleStatusCode()).isEqualTo("ACTIVE"); // 必填字段
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      Provenance result = converter.toDomain((RegProvenanceDO) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确转换 isActive 为 true")
    void shouldConvertIsActiveToTrue() {
      // Given: isActive 为 Boolean.TRUE
      RegProvenanceDO provenanceDO =
          RegProvenanceDO.builder()
              .id(3L)
              .provenanceCode("ARXIV")
              .provenanceName("arXiv")
              .timezoneDefault("UTC")
              .lifecycleStatusCode("ACTIVE")
              .isActive(Boolean.TRUE)
              .build();

      // When: 执行转换
      Provenance result = converter.toDomain(provenanceDO);

      // Then: 验证 active 正确转换为 true
      assertThat(result.active()).isTrue();
    }

    @Test
    @DisplayName("应该正确转换 isActive 为 false")
    void shouldConvertIsActiveToFalse() {
      // Given: isActive 为 Boolean.FALSE
      RegProvenanceDO provenanceDO =
          RegProvenanceDO.builder()
              .id(4L)
              .provenanceCode("DEPRECATED_SOURCE")
              .provenanceName("Deprecated Source")
              .timezoneDefault("UTC")
              .lifecycleStatusCode("DEPRECATED")
              .isActive(Boolean.FALSE)
              .build();

      // When: 执行转换
      Provenance result = converter.toDomain(provenanceDO);

      // Then: 验证 active 正确转换为 false
      assertThat(result.active()).isFalse();
    }

    @Test
    @DisplayName("应该正确映射字段名 provenanceCode → code")
    void shouldMapProvenanceCodeToCode() {
      // Given: DO 有 provenanceCode
      RegProvenanceDO provenanceDO =
          RegProvenanceDO.builder()
              .id(5L)
              .provenanceCode("TEST_CODE")
              .provenanceName("Test Name")
              .timezoneDefault("UTC")
              .lifecycleStatusCode("ACTIVE")
              .build();

      // When: 执行转换
      Provenance result = converter.toDomain(provenanceDO);

      // Then: 验证映射为 code
      assertThat(result.code()).isEqualTo("TEST_CODE");
    }

    @Test
    @DisplayName("应该正确映射字段名 provenanceName → name")
    void shouldMapProvenanceNameToName() {
      // Given: DO 有 provenanceName
      RegProvenanceDO provenanceDO =
          RegProvenanceDO.builder()
              .id(6L)
              .provenanceCode("TEST")
              .provenanceName("Test Provenance Name")
              .timezoneDefault("UTC")
              .lifecycleStatusCode("ACTIVE")
              .build();

      // When: 执行转换
      Provenance result = converter.toDomain(provenanceDO);

      // Then: 验证映射为 name
      assertThat(result.name()).isEqualTo("Test Provenance Name");
    }
  }

  @Nested
  @DisplayName("toDomain(RegProvWindowOffsetCfgDO) 转换测试")
  class RegProvWindowOffsetCfgDOToDomainTests {

    @Test
    @DisplayName("应该正确转换完整的窗口偏移配置 DO")
    void shouldConvertCompleteWindowOffsetCfgDO() {
      // Given: 准备完整的 DO 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      RegProvWindowOffsetCfgDO windowOffsetDO =
          RegProvWindowOffsetCfgDO.builder()
              .id(10L)
              .provenanceId(1L)
              .operationType("HARVEST")
              .effectiveFrom(effectiveFrom)
              .effectiveTo(effectiveTo)
              .windowModeCode("SLIDING")
              .windowSizeValue(7)
              .windowSizeUnitCode("DAY")
              .calendarAlignTo("DAY")
              .lookbackValue(1)
              .lookbackUnitCode("HOUR")
              .overlapValue(15)
              .overlapUnitCode("MINUTE")
              .watermarkLagSeconds(300)
              .offsetTypeCode("DATE")
              .offsetFieldKey("publish_date")
              .offsetDateFormat("yyyy/MM/dd")
              .windowDateFieldKey("publish_date")
              .maxIdsPerWindow(10000)
              .maxWindowSpanSeconds(86400)
              .lifecycleStatusCode("ACTIVE")
              .build();

      // When: 执行转换
      WindowOffsetConfig result = converter.toDomain(windowOffsetDO);

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
      RegProvWindowOffsetCfgDO windowOffsetDO =
          RegProvWindowOffsetCfgDO.builder()
              .id(11L)
              .provenanceId(2L)
              .windowModeCode("CALENDAR")
              .windowSizeValue(1)
              .windowSizeUnitCode("DAY")
              .offsetTypeCode("DATE")
              .effectiveFrom(Instant.now())
              .operationType(null) // 可选
              .effectiveTo(null) // 可选
              .calendarAlignTo(null) // 可选
              .lookbackValue(null) // 可选
              .lookbackUnitCode(null) // 可选
              .overlapValue(null) // 可选
              .overlapUnitCode(null) // 可选
              .watermarkLagSeconds(null) // 可选
              .offsetFieldKey("date") // DATE offsetType 需要至少一个 std_key
              .offsetDateFormat("yyyy-MM-dd") // offset field 的日期格式
              .windowDateFieldKey(null) // 可选
              .maxIdsPerWindow(null) // 可选
              .maxWindowSpanSeconds(null) // 可选
              .build();

      // When: 执行转换
      WindowOffsetConfig result = converter.toDomain(windowOffsetDO);

      // Then: 验证必填字段存在,可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(11L);
      assertThat(result.provenanceId()).isEqualTo(2L);
      assertThat(result.windowModeCode()).isEqualTo("CALENDAR");
      assertThat(result.operationType()).isNull();
      assertThat(result.effectiveTo()).isNull();
      assertThat(result.calendarAlignTo()).isNull();
      assertThat(result.lookbackValue()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      WindowOffsetConfig result = converter.toDomain((RegProvWindowOffsetCfgDO) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toDomain(RegProvPaginationCfgDO) 转换测试")
  class RegProvPaginationCfgDOToDomainTests {

    @Test
    @DisplayName("应该正确转换完整的分页配置 DO")
    void shouldConvertCompletePaginationCfgDO() {
      // Given: 准备完整的 DO 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      RegProvPaginationCfgDO paginationDO =
          RegProvPaginationCfgDO.builder()
              .id(20L)
              .provenanceId(1L)
              .operationType("HARVEST")
              .effectiveFrom(effectiveFrom)
              .effectiveTo(effectiveTo)
              .paginationModeCode("PAGE_NUMBER")
              .pageSizeValue(100)
              .maxPagesPerExecution(50)
              .sortFieldParamName("sort")
              .sortingDirection(1) // 1 = 升序
              .lifecycleStatusCode("ACTIVE")
              .build();

      // When: 执行转换
      PaginationConfig result = converter.toDomain(paginationDO);

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
      // Given: 只填充必填字段
      RegProvPaginationCfgDO paginationDO =
          RegProvPaginationCfgDO.builder()
              .id(21L)
              .provenanceId(2L)
              .paginationModeCode("CURSOR")
              .effectiveFrom(Instant.now())
              .operationType(null) // 可选
              .effectiveTo(null) // 可选
              .pageSizeValue(null) // 可选
              .maxPagesPerExecution(null) // 可选
              .sortFieldParamName(null) // 可选
              .sortingDirection(null) // 可选
              .build();

      // When: 执行转换
      PaginationConfig result = converter.toDomain(paginationDO);

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
      PaginationConfig result = converter.toDomain((RegProvPaginationCfgDO) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toDomain(RegProvHttpCfgDO) 转换测试")
  class RegProvHttpCfgDOToDomainTests {

    @Test
    @DisplayName("应该正确转换完整的 HTTP 配置 DO")
    void shouldConvertCompleteHttpCfgDO() throws Exception {
      // Given: 准备完整的 DO 对象
      ObjectNode defaultHeadersJson = objectMapper.createObjectNode();
      defaultHeadersJson.put("User-Agent", "PatraAPI/1.0");
      defaultHeadersJson.put("Accept", "application/json");

      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      RegProvHttpCfgDO httpCfgDO =
          RegProvHttpCfgDO.builder()
              .id(30L)
              .provenanceId(1L)
              .operationType("HARVEST")
              .effectiveFrom(effectiveFrom)
              .effectiveTo(effectiveTo)
              .defaultHeadersJson(defaultHeadersJson)
              .timeoutConnectMillis(5000)
              .timeoutReadMillis(30000)
              .timeoutTotalMillis(60000)
              .tlsVerifyEnabled(true)
              .proxyUrlValue("http://proxy.example.com:8080")
              .retryAfterPolicyCode("HONOR")
              .retryAfterCapMillis(60000)
              .idempotencyHeaderName("X-Idempotency-Key")
              .idempotencyTtlSeconds(86400)
              .lifecycleStatusCode("ACTIVE")
              .build();

      // When: 执行转换
      HttpConfig result = converter.toDomain(httpCfgDO);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(30L);
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(result.defaultHeadersJson()).contains("User-Agent", "PatraAPI/1.0");
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
    @DisplayName("应该正确处理布尔字段 tlsVerifyEnabled 为 null")
    void shouldHandleNullTlsVerifyEnabled() {
      // Given: tlsVerifyEnabled 为 null
      RegProvHttpCfgDO httpCfgDO =
          RegProvHttpCfgDO.builder()
              .id(31L)
              .provenanceId(2L)
              .effectiveFrom(Instant.now())
              .retryAfterPolicyCode("HONOR") // 必填
              .tlsVerifyEnabled(null) // null
              .build();

      // When: 执行转换
      HttpConfig result = converter.toDomain(httpCfgDO);

      // Then: 验证 tlsVerifyEnabled 为 null 时转换为 false
      assertThat(result).isNotNull();
      assertThat(result.tlsVerifyEnabled()).isFalse();
    }

    @Test
    @DisplayName("应该正确转换 tlsVerifyEnabled 为 true")
    void shouldConvertTlsVerifyEnabledToTrue() {
      // Given: tlsVerifyEnabled 为 Boolean.TRUE
      RegProvHttpCfgDO httpCfgDO =
          RegProvHttpCfgDO.builder()
              .id(32L)
              .provenanceId(3L)
              .effectiveFrom(Instant.now())
              .retryAfterPolicyCode("HONOR") // 必填
              .tlsVerifyEnabled(Boolean.TRUE)
              .build();

      // When: 执行转换
      HttpConfig result = converter.toDomain(httpCfgDO);

      // Then: 验证 tlsVerifyEnabled 正确转换为 true
      assertThat(result.tlsVerifyEnabled()).isTrue();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      HttpConfig result = converter.toDomain((RegProvHttpCfgDO) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确序列化 defaultHeadersJson")
    void shouldSerializeDefaultHeadersJson() throws Exception {
      // Given: DO 包含 JSON 头
      ObjectNode headersJson = objectMapper.createObjectNode();
      headersJson.put("Authorization", "Bearer token123");
      headersJson.put("Content-Type", "application/json");

      RegProvHttpCfgDO httpCfgDO =
          RegProvHttpCfgDO.builder()
              .id(33L)
              .provenanceId(4L)
              .effectiveFrom(Instant.now())
              .retryAfterPolicyCode("HONOR") // 必填
              .defaultHeadersJson(headersJson)
              .build();

      // When: 执行转换
      HttpConfig result = converter.toDomain(httpCfgDO);

      // Then: 验证 JSON 正确序列化
      assertThat(result.defaultHeadersJson()).isNotNull();
      assertThat(result.defaultHeadersJson()).contains("Authorization", "Bearer token123");
      assertThat(result.defaultHeadersJson()).contains("Content-Type", "application/json");
    }
  }

  @Nested
  @DisplayName("toDomain(RegProvBatchingCfgDO) 转换测试")
  class RegProvBatchingCfgDOToDomainTests {

    @Test
    @DisplayName("应该正确转换完整的批处理配置 DO")
    void shouldConvertCompleteBatchingCfgDO() {
      // Given: 准备完整的 DO 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      RegProvBatchingCfgDO batchingDO =
          RegProvBatchingCfgDO.builder()
              .id(40L)
              .provenanceId(1L)
              .operationType("HARVEST")
              .effectiveFrom(effectiveFrom)
              .effectiveTo(effectiveTo)
              .detailFetchBatchSize(100)
              .idsParamName("id")
              .idsJoinDelimiter(",")
              .maxIdsPerRequest(200)
              .lifecycleStatusCode("ACTIVE")
              .build();

      // When: 执行转换
      BatchingConfig result = converter.toDomain(batchingDO);

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
      RegProvBatchingCfgDO batchingDO =
          RegProvBatchingCfgDO.builder()
              .id(41L)
              .provenanceId(2L)
              .effectiveFrom(Instant.now())
              .operationType(null) // 可选
              .effectiveTo(null) // 可选
              .detailFetchBatchSize(null) // 可选
              .idsParamName(null) // 可选
              .idsJoinDelimiter(null) // 可选
              .maxIdsPerRequest(null) // 可选
              .build();

      // When: 执行转换
      BatchingConfig result = converter.toDomain(batchingDO);

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
      BatchingConfig result = converter.toDomain((RegProvBatchingCfgDO) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toDomain(RegProvRetryCfgDO) 转换测试")
  class RegProvRetryCfgDOToDomainTests {

    @Test
    @DisplayName("应该正确转换完整的重试配置 DO")
    void shouldConvertCompleteRetryCfgDO() throws Exception {
      // Given: 准备完整的 DO 对象
      ArrayNode retryHttpStatusJson = objectMapper.createArrayNode();
      retryHttpStatusJson.add(500);
      retryHttpStatusJson.add(502);
      retryHttpStatusJson.add(503);

      ArrayNode giveupHttpStatusJson = objectMapper.createArrayNode();
      giveupHttpStatusJson.add(400);
      giveupHttpStatusJson.add(401);
      giveupHttpStatusJson.add(403);

      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      RegProvRetryCfgDO retryCfgDO =
          RegProvRetryCfgDO.builder()
              .id(50L)
              .provenanceId(1L)
              .operationType("HARVEST")
              .effectiveFrom(effectiveFrom)
              .effectiveTo(effectiveTo)
              .maxRetryTimes(3)
              .backoffPolicyTypeCode("EXPONENTIAL")
              .initialDelayMillis(1000)
              .maxDelayMillis(30000)
              .expMultiplierValue(2.0)
              .jitterFactorRatio(0.2)
              .retryHttpStatusJson(retryHttpStatusJson)
              .giveupHttpStatusJson(giveupHttpStatusJson)
              .retryOnNetworkError(true)
              .circuitBreakThreshold(5)
              .circuitCooldownMillis(60000)
              .lifecycleStatusCode("ACTIVE")
              .build();

      // When: 执行转换
      RetryConfig result = converter.toDomain(retryCfgDO);

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
      assertThat(result.retryHttpStatusJson()).contains("500", "502", "503");
      assertThat(result.giveupHttpStatusJson()).contains("400", "401", "403");
      assertThat(result.retryOnNetworkError()).isTrue();
      assertThat(result.circuitBreakThreshold()).isEqualTo(5);
      assertThat(result.circuitCooldownMillis()).isEqualTo(60000);
    }

    @Test
    @DisplayName("应该正确处理 retryOnNetworkError 为 null")
    void shouldHandleNullRetryOnNetworkError() {
      // Given: retryOnNetworkError 为 null
      RegProvRetryCfgDO retryCfgDO =
          RegProvRetryCfgDO.builder()
              .id(51L)
              .provenanceId(2L)
              .effectiveFrom(Instant.now())
              .maxRetryTimes(3)
              .backoffPolicyTypeCode("FIXED")
              .retryOnNetworkError(null) // null
              .build();

      // When: 执行转换
      RetryConfig result = converter.toDomain(retryCfgDO);

      // Then: 验证 retryOnNetworkError 为 null 时转换为 false
      assertThat(result).isNotNull();
      assertThat(result.retryOnNetworkError()).isFalse();
    }

    @Test
    @DisplayName("应该正确转换 retryOnNetworkError 为 true")
    void shouldConvertRetryOnNetworkErrorToTrue() {
      // Given: retryOnNetworkError 为 Boolean.TRUE
      RegProvRetryCfgDO retryCfgDO =
          RegProvRetryCfgDO.builder()
              .id(52L)
              .provenanceId(3L)
              .effectiveFrom(Instant.now())
              .maxRetryTimes(3)
              .backoffPolicyTypeCode("FIXED")
              .retryOnNetworkError(Boolean.TRUE)
              .build();

      // When: 执行转换
      RetryConfig result = converter.toDomain(retryCfgDO);

      // Then: 验证 retryOnNetworkError 正确转换为 true
      assertThat(result.retryOnNetworkError()).isTrue();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      RetryConfig result = converter.toDomain((RegProvRetryCfgDO) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确序列化 JSON 数组字段")
    void shouldSerializeJsonArrayFields() throws Exception {
      // Given: DO 包含 JSON 数组
      ArrayNode retryStatusJson = objectMapper.createArrayNode();
      retryStatusJson.add(429);
      retryStatusJson.add(503);

      RegProvRetryCfgDO retryCfgDO =
          RegProvRetryCfgDO.builder()
              .id(53L)
              .provenanceId(4L)
              .effectiveFrom(Instant.now())
              .maxRetryTimes(3)
              .backoffPolicyTypeCode("EXP_JITTER")
              .retryHttpStatusJson(retryStatusJson)
              .build();

      // When: 执行转换
      RetryConfig result = converter.toDomain(retryCfgDO);

      // Then: 验证 JSON 数组正确序列化
      assertThat(result.retryHttpStatusJson()).isNotNull();
      assertThat(result.retryHttpStatusJson()).contains("429", "503");
    }
  }

  @Nested
  @DisplayName("toDomain(RegProvRateLimitCfgDO) 转换测试")
  class RegProvRateLimitCfgDOToDomainTests {

    @Test
    @DisplayName("应该正确转换完整的速率限制配置 DO")
    void shouldConvertCompleteRateLimitCfgDO() {
      // Given: 准备完整的 DO 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      RegProvRateLimitCfgDO rateLimitDO =
          RegProvRateLimitCfgDO.builder()
              .id(60L)
              .provenanceId(1L)
              .operationType("HARVEST")
              .effectiveFrom(effectiveFrom)
              .effectiveTo(effectiveTo)
              .maxConcurrentRequests(10)
              .perCredentialQpsLimit(3)
              .lifecycleStatusCode("ACTIVE")
              .build();

      // When: 执行转换
      RateLimitConfig result = converter.toDomain(rateLimitDO);

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
      RegProvRateLimitCfgDO rateLimitDO =
          RegProvRateLimitCfgDO.builder()
              .id(61L)
              .provenanceId(2L)
              .effectiveFrom(Instant.now())
              .operationType(null) // 可选
              .effectiveTo(null) // 可选
              .maxConcurrentRequests(null) // 可选
              .perCredentialQpsLimit(null) // 可选
              .build();

      // When: 执行转换
      RateLimitConfig result = converter.toDomain(rateLimitDO);

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
      RateLimitConfig result = converter.toDomain((RegProvRateLimitCfgDO) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("JsonNode 序列化辅助方法测试")
  class JsonNodeSerializationTests {

    @Test
    @DisplayName("应该将 JsonNode 正确序列化为 JSON 字符串")
    void shouldSerializeJsonNodeToString() throws Exception {
      // Given: 创建 JsonNode 对象
      ObjectNode jsonNode = objectMapper.createObjectNode();
      jsonNode.put("key", "value");

      // When: 调用辅助方法
      String result = converter.map(jsonNode);

      // Then: 验证序列化结果
      assertThat(result).isNotNull();
      assertThat(result).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    @DisplayName("当 JsonNode 为 null 时应该返回 null")
    void shouldReturnNullWhenJsonNodeIsNull() {
      // When: 输入 null
      String result = converter.map((JsonNode) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确序列化 ArrayNode")
    void shouldSerializeArrayNode() throws Exception {
      // Given: 创建 ArrayNode
      ArrayNode arrayNode = objectMapper.createArrayNode();
      arrayNode.add("item1");
      arrayNode.add("item2");
      arrayNode.add("item3");

      // When: 调用辅助方法
      String result = converter.map(arrayNode);

      // Then: 验证序列化结果
      assertThat(result).isNotNull();
      assertThat(result).isEqualTo("[\"item1\",\"item2\",\"item3\"]");
    }

    @Test
    @DisplayName("应该正确序列化嵌套的 JSON 结构")
    void shouldSerializeNestedJsonStructure() throws Exception {
      // Given: 创建复杂的嵌套 JSON 结构
      ObjectNode root = objectMapper.createObjectNode();
      root.put("name", "test");

      ObjectNode nested = objectMapper.createObjectNode();
      nested.put("nestedKey", "nestedValue");
      root.set("nested", nested);

      ArrayNode array = objectMapper.createArrayNode();
      array.add("a");
      array.add("b");
      root.set("array", array);

      // When: 调用辅助方法
      String result = converter.map(root);

      // Then: 验证序列化结果
      assertThat(result).isNotNull();
      assertThat(result).contains("\"name\":\"test\"");
      assertThat(result).contains("\"nested\":{\"nestedKey\":\"nestedValue\"}");
      assertThat(result).contains("\"array\":[\"a\",\"b\"]");
    }
  }
}
