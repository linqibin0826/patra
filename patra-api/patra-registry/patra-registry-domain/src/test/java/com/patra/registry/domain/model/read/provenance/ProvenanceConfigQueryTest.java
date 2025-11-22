package com.patra.registry.domain.model.read.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link ProvenanceConfigQuery} 的单元测试。
/// 
/// @author linqibin
/// @since 0.1.0
@DisplayName("ProvenanceConfigQuery 单元测试")
class ProvenanceConfigQueryTest {

  @Nested
  @DisplayName("成功构造测试")
  class ConstructionSuccessTests {

    @Test
    @DisplayName("所有字段有效时应成功构造")
    void shouldConstructSuccessfullyWithAllFieldsValid() {
      // Given: 准备所有有效字段
      ProvenanceQuery provenance = createValidProvenanceQuery();
      WindowOffsetQuery windowOffset = createValidWindowOffsetQuery();
      PaginationConfigQuery pagination = createValidPaginationConfigQuery();
      HttpConfigQuery http = createValidHttpConfigQuery();
      BatchingConfigQuery batching = createValidBatchingConfigQuery();
      RetryConfigQuery retry = createValidRetryConfigQuery();
      RateLimitConfigQuery rateLimit = createValidRateLimitConfigQuery();

      // When: 构造对象
      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(
              provenance, windowOffset, pagination, http, batching, retry, rateLimit);

      // Then: 所有字段值应正确设置
      assertThat(query.provenance()).isSameAs(provenance);
      assertThat(query.windowOffset()).isSameAs(windowOffset);
      assertThat(query.pagination()).isSameAs(pagination);
      assertThat(query.http()).isSameAs(http);
      assertThat(query.batching()).isSameAs(batching);
      assertThat(query.retry()).isSameAs(retry);
      assertThat(query.rateLimit()).isSameAs(rateLimit);
    }

    @Test
    @DisplayName("可选字段为 null 时应成功构造")
    void shouldConstructSuccessfullyWithOptionalFieldsNull() {
      // Given: 仅必填字段
      ProvenanceQuery provenance = createValidProvenanceQuery();

      // When: 可选字段为 null
      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(provenance, null, null, null, null, null, null);

      // Then: 应成功构造
      assertThat(query.provenance()).isSameAs(provenance);
      assertThat(query.windowOffset()).isNull();
      assertThat(query.pagination()).isNull();
      assertThat(query.http()).isNull();
      assertThat(query.batching()).isNull();
      assertThat(query.retry()).isNull();
      assertThat(query.rateLimit()).isNull();
    }

    @Test
    @DisplayName("最小必填字段时应成功构造")
    void shouldConstructSuccessfullyWithMinimalFields() {
      // Given: 仅必填字段
      ProvenanceQuery provenance = createValidProvenanceQuery();

      // When: 构造对象
      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(provenance, null, null, null, null, null, null);

      // Then: 必填字段应存在
      assertThat(query.provenance()).isNotNull();
    }

    @Test
    @DisplayName("部分可选字段有值时应成功构造")
    void shouldConstructSuccessfullyWithPartialOptionalFields() {
      // Given: 部分可选字段有值
      ProvenanceQuery provenance = createValidProvenanceQuery();
      PaginationConfigQuery pagination = createValidPaginationConfigQuery();
      RetryConfigQuery retry = createValidRetryConfigQuery();

      // When: 构造对象
      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(provenance, null, pagination, null, null, retry, null);

      // Then: 字段值应正确设置
      assertThat(query.provenance()).isSameAs(provenance);
      assertThat(query.windowOffset()).isNull();
      assertThat(query.pagination()).isSameAs(pagination);
      assertThat(query.http()).isNull();
      assertThat(query.batching()).isNull();
      assertThat(query.retry()).isSameAs(retry);
      assertThat(query.rateLimit()).isNull();
    }
  }

  @Nested
  @DisplayName("provenance 验证失败测试")
  class ProvenanceValidationTests {

    @Test
    @DisplayName("provenance 为 null 时应抛出 DomainValidationException")
    void shouldThrowExceptionWhenProvenanceIsNull() {
      // Given: provenance 为 null
      ProvenanceQuery provenance = null;

      // When & Then: 应抛出异常
      assertThatThrownBy(
              () -> new ProvenanceConfigQuery(provenance, null, null, null, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("来源信息不能为null");
    }

    @Test
    @DisplayName("provenance 为 null 且其他字段有值时应抛出异常")
    void shouldThrowExceptionWhenProvenanceIsNullRegardlessOfOtherFields() {
      // Given: provenance 为 null，其他字段有值
      ProvenanceQuery provenance = null;
      PaginationConfigQuery pagination = createValidPaginationConfigQuery();
      HttpConfigQuery http = createValidHttpConfigQuery();

      // When & Then: 应抛出异常
      assertThatThrownBy(
              () -> new ProvenanceConfigQuery(provenance, null, pagination, http, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("来源信息不能为null");
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同值的两个对象应相等")
    void shouldBeEqualWhenSameValues() {
      // Given: 相同值的两个对象
      ProvenanceQuery provenance = createValidProvenanceQuery();
      PaginationConfigQuery pagination = createValidPaginationConfigQuery();

      ProvenanceConfigQuery query1 =
          new ProvenanceConfigQuery(provenance, null, pagination, null, null, null, null);
      ProvenanceConfigQuery query2 =
          new ProvenanceConfigQuery(provenance, null, pagination, null, null, null, null);

      // When & Then: 应相等
      assertThat(query1).isEqualTo(query2);
    }

    @Test
    @DisplayName("不同值的两个对象应不相等")
    void shouldNotBeEqualWhenDifferentValues() {
      // Given: 不同值的两个对象
      ProvenanceQuery provenance1 = createValidProvenanceQuery();
      ProvenanceQuery provenance2 =
          new ProvenanceQuery(
              2L, "EPMC", "Europe PMC", "https://epmc.org", "UTC", null, true, "ACTIVE");

      ProvenanceConfigQuery query1 =
          new ProvenanceConfigQuery(provenance1, null, null, null, null, null, null);
      ProvenanceConfigQuery query2 =
          new ProvenanceConfigQuery(provenance2, null, null, null, null, null, null);

      // When & Then: 应不相等
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("对象应等于自身")
    void shouldBeEqualToSelf() {
      // Given: 对象
      ProvenanceQuery provenance = createValidProvenanceQuery();
      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(provenance, null, null, null, null, null, null);

      // When & Then: 应等于自身
      assertThat(query).isEqualTo(query);
    }

    @Test
    @DisplayName("对象不应等于 null")
    void shouldNotBeEqualToNull() {
      // Given: 对象
      ProvenanceQuery provenance = createValidProvenanceQuery();
      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(provenance, null, null, null, null, null, null);

      // When & Then: 不应等于 null
      assertThat(query).isNotEqualTo(null);
    }

    @Test
    @DisplayName("对象不应等于不同类型")
    void shouldNotBeEqualToDifferentType() {
      // Given: 对象
      ProvenanceQuery provenance = createValidProvenanceQuery();
      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(provenance, null, null, null, null, null, null);

      // When & Then: 不应等于不同类型
      assertThat(query).isNotEqualTo("not a ProvenanceConfigQuery");
    }

    @Test
    @DisplayName("相同值的对象应有相同的 hashCode")
    void shouldHaveSameHashCodeWhenEqual() {
      // Given: 相同值的两个对象
      ProvenanceQuery provenance = createValidProvenanceQuery();

      ProvenanceConfigQuery query1 =
          new ProvenanceConfigQuery(provenance, null, null, null, null, null, null);
      ProvenanceConfigQuery query2 =
          new ProvenanceConfigQuery(provenance, null, null, null, null, null, null);

      // When & Then: hashCode 应相同
      assertThat(query1).hasSameHashCodeAs(query2);
    }
  }

  @Nested
  @DisplayName("组件访问器测试")
  class ComponentAccessorTests {

    @Test
    @DisplayName("所有字段访问器应返回正确值")
    void shouldReturnCorrectValuesFromAccessors() {
      // Given: 所有字段有值
      ProvenanceQuery provenance = createValidProvenanceQuery();
      WindowOffsetQuery windowOffset = createValidWindowOffsetQuery();
      PaginationConfigQuery pagination = createValidPaginationConfigQuery();
      HttpConfigQuery http = createValidHttpConfigQuery();
      BatchingConfigQuery batching = createValidBatchingConfigQuery();
      RetryConfigQuery retry = createValidRetryConfigQuery();
      RateLimitConfigQuery rateLimit = createValidRateLimitConfigQuery();

      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(
              provenance, windowOffset, pagination, http, batching, retry, rateLimit);

      // When & Then: 访问器应返回正确值
      assertThat(query.provenance()).isSameAs(provenance);
      assertThat(query.windowOffset()).isSameAs(windowOffset);
      assertThat(query.pagination()).isSameAs(pagination);
      assertThat(query.http()).isSameAs(http);
      assertThat(query.batching()).isSameAs(batching);
      assertThat(query.retry()).isSameAs(retry);
      assertThat(query.rateLimit()).isSameAs(rateLimit);
    }

    @Test
    @DisplayName("可选字段访问器应返回 null")
    void shouldReturnNullFromOptionalAccessors() {
      // Given: 仅必填字段
      ProvenanceQuery provenance = createValidProvenanceQuery();
      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(provenance, null, null, null, null, null, null);

      // When & Then: 可选字段访问器应返回 null
      assertThat(query.windowOffset()).isNull();
      assertThat(query.pagination()).isNull();
      assertThat(query.http()).isNull();
      assertThat(query.batching()).isNull();
      assertThat(query.retry()).isNull();
      assertThat(query.rateLimit()).isNull();
    }
  }

  @Nested
  @DisplayName("不可变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("Record 应保证引用不可变性")
    void shouldGuaranteeReferenceImmutability() {
      // Given: 对象
      ProvenanceQuery provenance = createValidProvenanceQuery();
      ProvenanceConfigQuery query =
          new ProvenanceConfigQuery(provenance, null, null, null, null, null, null);

      ProvenanceQuery returnedProvenance = query.provenance();

      // When & Then: 返回的引用应与原引用相同
      assertThat(returnedProvenance).isSameAs(provenance);
    }
  }

  // ==================== 测试辅助方法 ====================

  private static ProvenanceQuery createValidProvenanceQuery() {
    return new ProvenanceQuery(
        1L, "PUBMED", "PubMed", "https://pubmed.ncbi.nlm.nih.gov", "UTC", null, true, "ACTIVE");
  }

  private static WindowOffsetQuery createValidWindowOffsetQuery() {
    return new WindowOffsetQuery(
        1L,
        1L,
        "FETCH",
        Instant.parse("2025-01-01T00:00:00Z"),
        Instant.parse("2025-12-31T23:59:59Z"),
        "TUMBLING",
        7,
        "DAYS",
        "START_OF_DAY",
        1,
        "HOURS",
        30,
        "MINUTES",
        300,
        "DATE_FIELD",
        "lastModifiedDate",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "publicationDate",
        10000,
        86400);
  }

  private static PaginationConfigQuery createValidPaginationConfigQuery() {
    return new PaginationConfigQuery(
        1L,
        1L,
        "SEARCH",
        Instant.parse("2025-01-01T00:00:00Z"),
        Instant.parse("2025-12-31T23:59:59Z"),
        "OFFSET",
        100,
        10,
        "publicationDate",
        1);
  }

  private static HttpConfigQuery createValidHttpConfigQuery() {
    return new HttpConfigQuery(
        1L,
        1L,
        "CREATE",
        Instant.parse("2025-01-01T00:00:00Z"),
        Instant.parse("2025-12-31T23:59:59Z"),
        "{\"User-Agent\":\"Patra/1.0\"}",
        5000,
        10000,
        15000,
        true,
        "http://proxy.example.com:8080",
        "EXPONENTIAL_BACKOFF",
        60000,
        "X-Idempotency-Key",
        3600);
  }

  private static BatchingConfigQuery createValidBatchingConfigQuery() {
    return new BatchingConfigQuery(
        1L,
        1L,
        "FETCH_DETAILS",
        Instant.parse("2025-01-01T00:00:00Z"),
        Instant.parse("2025-12-31T23:59:59Z"),
        50,
        "ids",
        ",",
        100);
  }

  private static RetryConfigQuery createValidRetryConfigQuery() {
    return new RetryConfigQuery(
        1L,
        1L,
        "FETCH",
        Instant.parse("2025-01-01T00:00:00Z"),
        Instant.parse("2025-12-31T23:59:59Z"),
        3,
        "EXPONENTIAL",
        1000,
        30000,
        2.0,
        0.1,
        "[500, 502, 503]",
        "[400, 401, 403]",
        true,
        5,
        60000);
  }

  private static RateLimitConfigQuery createValidRateLimitConfigQuery() {
    return new RateLimitConfigQuery(
        1L,
        1L,
        "FETCH",
        Instant.parse("2025-01-01T00:00:00Z"),
        Instant.parse("2025-12-31T23:59:59Z"),
        10,
        5);
  }
}
