package com.patra.registry.domain.model.read.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RetryConfigQuery} 的单元测试。
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("RetryConfigQuery 单元测试")
class RetryConfigQueryTest {

  @Nested
  @DisplayName("成功构造测试")
  class ConstructionSuccessTests {

    @Test
    @DisplayName("所有字段有效时应成功构造")
    void shouldConstructSuccessfullyWithAllFieldsValid() {
      // Given: 准备所有有效字段
      Long id = 1L;
      Long provenanceId = 100L;
      String operationType = "FETCH";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      Integer maxRetryTimes = 3;
      String backoffPolicyTypeCode = "EXPONENTIAL";
      Integer initialDelayMillis = 1000;
      Integer maxDelayMillis = 30000;
      Double expMultiplierValue = 2.0;
      Double jitterFactorRatio = 0.1;
      String retryHttpStatusJson = "[500, 502, 503]";
      String giveupHttpStatusJson = "[400, 401, 403]";
      boolean retryOnNetworkError = true;
      Integer circuitBreakThreshold = 5;
      Integer circuitCooldownMillis = 60000;

      // When: 构造对象
      RetryConfigQuery query =
          new RetryConfigQuery(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxRetryTimes,
              backoffPolicyTypeCode,
              initialDelayMillis,
              maxDelayMillis,
              expMultiplierValue,
              jitterFactorRatio,
              retryHttpStatusJson,
              giveupHttpStatusJson,
              retryOnNetworkError,
              circuitBreakThreshold,
              circuitCooldownMillis);

      // Then: 所有字段值应正确设置
      assertThat(query.id()).isEqualTo(id);
      assertThat(query.provenanceId()).isEqualTo(provenanceId);
      assertThat(query.operationType()).isEqualTo(operationType);
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(query.maxRetryTimes()).isEqualTo(maxRetryTimes);
      assertThat(query.backoffPolicyTypeCode()).isEqualTo(backoffPolicyTypeCode);
      assertThat(query.initialDelayMillis()).isEqualTo(initialDelayMillis);
      assertThat(query.maxDelayMillis()).isEqualTo(maxDelayMillis);
      assertThat(query.expMultiplierValue()).isEqualTo(expMultiplierValue);
      assertThat(query.jitterFactorRatio()).isEqualTo(jitterFactorRatio);
      assertThat(query.retryHttpStatusJson()).isEqualTo(retryHttpStatusJson);
      assertThat(query.giveupHttpStatusJson()).isEqualTo(giveupHttpStatusJson);
      assertThat(query.retryOnNetworkError()).isTrue();
      assertThat(query.circuitBreakThreshold()).isEqualTo(circuitBreakThreshold);
      assertThat(query.circuitCooldownMillis()).isEqualTo(circuitCooldownMillis);
    }

    @Test
    @DisplayName("可选字段为 null 时应成功构造")
    void shouldConstructSuccessfullyWithOptionalFieldsNull() {
      // Given: 必填字段有效,可选字段为 null
      Long id = 1L;
      Long provenanceId = 100L;
      String operationType = null; // 可选
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = null; // 可选
      Integer maxRetryTimes = null; // 可选
      String backoffPolicyTypeCode = "FIXED";
      Integer initialDelayMillis = null; // 可选
      Integer maxDelayMillis = null; // 可选
      Double expMultiplierValue = null; // 可选
      Double jitterFactorRatio = null; // 可选
      String retryHttpStatusJson = null; // 可选
      String giveupHttpStatusJson = null; // 可选
      boolean retryOnNetworkError = false;
      Integer circuitBreakThreshold = null; // 可选
      Integer circuitCooldownMillis = null; // 可选

      // When: 构造对象
      RetryConfigQuery query =
          new RetryConfigQuery(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxRetryTimes,
              backoffPolicyTypeCode,
              initialDelayMillis,
              maxDelayMillis,
              expMultiplierValue,
              jitterFactorRatio,
              retryHttpStatusJson,
              giveupHttpStatusJson,
              retryOnNetworkError,
              circuitBreakThreshold,
              circuitCooldownMillis);

      // Then: 必填字段应正确设置,可选字段为 null
      assertThat(query.id()).isEqualTo(id);
      assertThat(query.provenanceId()).isEqualTo(provenanceId);
      assertThat(query.operationType()).isNull();
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.effectiveTo()).isNull();
      assertThat(query.maxRetryTimes()).isNull();
      assertThat(query.backoffPolicyTypeCode()).isEqualTo(backoffPolicyTypeCode);
      assertThat(query.initialDelayMillis()).isNull();
      assertThat(query.maxDelayMillis()).isNull();
      assertThat(query.expMultiplierValue()).isNull();
      assertThat(query.jitterFactorRatio()).isNull();
      assertThat(query.retryHttpStatusJson()).isNull();
      assertThat(query.giveupHttpStatusJson()).isNull();
      assertThat(query.retryOnNetworkError()).isFalse();
      assertThat(query.circuitBreakThreshold()).isNull();
      assertThat(query.circuitCooldownMillis()).isNull();
    }

    @Test
    @DisplayName("最小必填字段时应成功构造")
    void shouldConstructSuccessfullyWithMinimalRequiredFields() {
      // Given: 仅必填字段
      Long id = 999L;
      Long provenanceId = 888L;
      Instant effectiveFrom = Instant.parse("2025-06-01T12:00:00Z");
      String backoffPolicyTypeCode = "LINEAR";

      // When: 构造对象
      RetryConfigQuery query =
          new RetryConfigQuery(
              id,
              provenanceId,
              null,
              effectiveFrom,
              null,
              null,
              backoffPolicyTypeCode,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 必填字段应正确设置
      assertThat(query.id()).isEqualTo(id);
      assertThat(query.provenanceId()).isEqualTo(provenanceId);
      assertThat(query.backoffPolicyTypeCode()).isEqualTo(backoffPolicyTypeCode);
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.retryOnNetworkError()).isFalse();
    }
  }

  @Nested
  @DisplayName("验证失败测试")
  class ValidationFailureTests {

    @Nested
    @DisplayName("id 验证")
    class IdValidationTests {

      @Test
      @DisplayName("id 为 null 时应抛出异常")
      void shouldThrowExceptionWhenIdIsNull() {
        // Given: id 为 null
        Long id = null;

        // When & Then: 应抛出 DomainValidationException
        assertThatThrownBy(
                () ->
                    new RetryConfigQuery(
                        id,
                        100L,
                        "FETCH",
                        Instant.now(),
                        null,
                        3,
                        "EXPONENTIAL",
                        1000,
                        30000,
                        2.0,
                        0.1,
                        null,
                        null,
                        true,
                        5,
                        60000))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("重试配置ID必须为正数");
      }

      @Test
      @DisplayName("id 为 0 时应抛出异常")
      void shouldThrowExceptionWhenIdIsZero() {
        // Given: id 为 0
        Long id = 0L;

        // When & Then: 应抛出 DomainValidationException
        assertThatThrownBy(
                () ->
                    new RetryConfigQuery(
                        id,
                        100L,
                        "FETCH",
                        Instant.now(),
                        null,
                        3,
                        "EXPONENTIAL",
                        1000,
                        30000,
                        2.0,
                        0.1,
                        null,
                        null,
                        true,
                        5,
                        60000))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("重试配置ID必须为正数");
      }

      @Test
      @DisplayName("id 为负数时应抛出异常")
      void shouldThrowExceptionWhenIdIsNegative() {
        // Given: id 为负数
        Long id = -1L;

        // When & Then: 应抛出 DomainValidationException
        assertThatThrownBy(
                () ->
                    new RetryConfigQuery(
                        id,
                        100L,
                        "FETCH",
                        Instant.now(),
                        null,
                        3,
                        "EXPONENTIAL",
                        1000,
                        30000,
                        2.0,
                        0.1,
                        null,
                        null,
                        true,
                        5,
                        60000))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("重试配置ID必须为正数");
      }
    }

    @Nested
    @DisplayName("provenanceId 验证")
    class ProvenanceIdValidationTests {

      @Test
      @DisplayName("provenanceId 为 null 时应抛出异常")
      void shouldThrowExceptionWhenProvenanceIdIsNull() {
        // Given: provenanceId 为 null
        Long provenanceId = null;

        // When & Then: 应抛出 DomainValidationException
        assertThatThrownBy(
                () ->
                    new RetryConfigQuery(
                        1L,
                        provenanceId,
                        "FETCH",
                        Instant.now(),
                        null,
                        3,
                        "EXPONENTIAL",
                        1000,
                        30000,
                        2.0,
                        0.1,
                        null,
                        null,
                        true,
                        5,
                        60000))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("来源ID必须为正数");
      }

      @Test
      @DisplayName("provenanceId 为 0 时应抛出异常")
      void shouldThrowExceptionWhenProvenanceIdIsZero() {
        // Given: provenanceId 为 0
        Long provenanceId = 0L;

        // When & Then: 应抛出 DomainValidationException
        assertThatThrownBy(
                () ->
                    new RetryConfigQuery(
                        1L,
                        provenanceId,
                        "FETCH",
                        Instant.now(),
                        null,
                        3,
                        "EXPONENTIAL",
                        1000,
                        30000,
                        2.0,
                        0.1,
                        null,
                        null,
                        true,
                        5,
                        60000))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("来源ID必须为正数");
      }

      @Test
      @DisplayName("provenanceId 为负数时应抛出异常")
      void shouldThrowExceptionWhenProvenanceIdIsNegative() {
        // Given: provenanceId 为负数
        Long provenanceId = -100L;

        // When & Then: 应抛出 DomainValidationException
        assertThatThrownBy(
                () ->
                    new RetryConfigQuery(
                        1L,
                        provenanceId,
                        "FETCH",
                        Instant.now(),
                        null,
                        3,
                        "EXPONENTIAL",
                        1000,
                        30000,
                        2.0,
                        0.1,
                        null,
                        null,
                        true,
                        5,
                        60000))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("来源ID必须为正数");
      }
    }

    @Nested
    @DisplayName("backoffPolicyTypeCode 验证")
    class BackoffPolicyTypeCodeValidationTests {

      @Test
      @DisplayName("backoffPolicyTypeCode 为 null 时应抛出异常")
      void shouldThrowExceptionWhenBackoffPolicyTypeCodeIsNull() {
        // Given: backoffPolicyTypeCode 为 null
        String backoffPolicyTypeCode = null;

        // When & Then: 应抛出 DomainValidationException
        assertThatThrownBy(
                () ->
                    new RetryConfigQuery(
                        1L,
                        100L,
                        "FETCH",
                        Instant.now(),
                        null,
                        3,
                        backoffPolicyTypeCode,
                        1000,
                        30000,
                        2.0,
                        0.1,
                        null,
                        null,
                        true,
                        5,
                        60000))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("退避策略类型代码不能为空");
      }

      @Test
      @DisplayName("backoffPolicyTypeCode 为空字符串时应抛出异常")
      void shouldThrowExceptionWhenBackoffPolicyTypeCodeIsEmpty() {
        // Given: backoffPolicyTypeCode 为空字符串
        String backoffPolicyTypeCode = "";

        // When & Then: 应抛出 DomainValidationException
        assertThatThrownBy(
                () ->
                    new RetryConfigQuery(
                        1L,
                        100L,
                        "FETCH",
                        Instant.now(),
                        null,
                        3,
                        backoffPolicyTypeCode,
                        1000,
                        30000,
                        2.0,
                        0.1,
                        null,
                        null,
                        true,
                        5,
                        60000))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("退避策略类型代码不能为空");
      }

      @Test
      @DisplayName("backoffPolicyTypeCode 为空白字符串时应抛出异常")
      void shouldThrowExceptionWhenBackoffPolicyTypeCodeIsBlank() {
        // Given: backoffPolicyTypeCode 为空白字符串
        String backoffPolicyTypeCode = "   ";

        // When & Then: 应抛出 DomainValidationException
        assertThatThrownBy(
                () ->
                    new RetryConfigQuery(
                        1L,
                        100L,
                        "FETCH",
                        Instant.now(),
                        null,
                        3,
                        backoffPolicyTypeCode,
                        1000,
                        30000,
                        2.0,
                        0.1,
                        null,
                        null,
                        true,
                        5,
                        60000))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("退避策略类型代码不能为空");
      }
    }

    @Nested
    @DisplayName("effectiveFrom 验证")
    class EffectiveFromValidationTests {

      @Test
      @DisplayName("effectiveFrom 为 null 时应抛出异常")
      void shouldThrowExceptionWhenEffectiveFromIsNull() {
        // Given: effectiveFrom 为 null
        Instant effectiveFrom = null;

        // When & Then: 应抛出 DomainValidationException
        assertThatThrownBy(
                () ->
                    new RetryConfigQuery(
                        1L,
                        100L,
                        "FETCH",
                        effectiveFrom,
                        null,
                        3,
                        "EXPONENTIAL",
                        1000,
                        30000,
                        2.0,
                        0.1,
                        null,
                        null,
                        true,
                        5,
                        60000))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("生效时间不能为null");
      }
    }
  }

  @Nested
  @DisplayName("Trim 逻辑测试")
  class TrimLogicTests {

    @Test
    @DisplayName("operationType 前后空格应被 trim")
    void shouldTrimOperationTypeWhitespace() {
      // Given: operationType 有前后空格
      String operationTypeWithSpaces = "  FETCH  ";

      // When: 构造对象
      RetryConfigQuery query =
          new RetryConfigQuery(
              1L,
              100L,
              operationTypeWithSpaces,
              Instant.now(),
              null,
              3,
              "EXPONENTIAL",
              1000,
              30000,
              2.0,
              0.1,
              null,
              null,
              true,
              5,
              60000);

      // Then: operationType 应被 trim
      assertThat(query.operationType()).isEqualTo("FETCH");
    }

    @Test
    @DisplayName("operationType 为 null 时不应抛出异常")
    void shouldHandleNullOperationTypeGracefully() {
      // Given: operationType 为 null
      String operationType = null;

      // When: 构造对象
      RetryConfigQuery query =
          new RetryConfigQuery(
              1L,
              100L,
              operationType,
              Instant.now(),
              null,
              3,
              "EXPONENTIAL",
              1000,
              30000,
              2.0,
              0.1,
              null,
              null,
              true,
              5,
              60000);

      // Then: operationType 应为 null
      assertThat(query.operationType()).isNull();
    }

    @Test
    @DisplayName("backoffPolicyTypeCode 前后空格应被 trim")
    void shouldTrimBackoffPolicyTypeCodeWhitespace() {
      // Given: backoffPolicyTypeCode 有前后空格
      String backoffPolicyTypeCodeWithSpaces = "  EXPONENTIAL  ";

      // When: 构造对象
      RetryConfigQuery query =
          new RetryConfigQuery(
              1L,
              100L,
              "FETCH",
              Instant.now(),
              null,
              3,
              backoffPolicyTypeCodeWithSpaces,
              1000,
              30000,
              2.0,
              0.1,
              null,
              null,
              true,
              5,
              60000);

      // Then: backoffPolicyTypeCode 应被 trim
      assertThat(query.backoffPolicyTypeCode()).isEqualTo("EXPONENTIAL");
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同字段值的两个对象应相等")
    void shouldBeEqualWithSameFieldValues() {
      // Given: 两个具有相同字段值的对象
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");

      RetryConfigQuery query1 =
          new RetryConfigQuery(
              1L,
              100L,
              "FETCH",
              effectiveFrom,
              effectiveTo,
              3,
              "EXPONENTIAL",
              1000,
              30000,
              2.0,
              0.1,
              "[500]",
              "[400]",
              true,
              5,
              60000);

      RetryConfigQuery query2 =
          new RetryConfigQuery(
              1L,
              100L,
              "FETCH",
              effectiveFrom,
              effectiveTo,
              3,
              "EXPONENTIAL",
              1000,
              30000,
              2.0,
              0.1,
              "[500]",
              "[400]",
              true,
              5,
              60000);

      // Then: 两个对象应相等
      assertThat(query1).isEqualTo(query2);
    }

    @Test
    @DisplayName("不同字段值的两个对象应不相等")
    void shouldNotBeEqualWithDifferentFieldValues() {
      // Given: 两个具有不同字段值的对象
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      RetryConfigQuery query1 =
          new RetryConfigQuery(
              1L,
              100L,
              "FETCH",
              effectiveFrom,
              null,
              3,
              "EXPONENTIAL",
              1000,
              30000,
              2.0,
              0.1,
              null,
              null,
              true,
              5,
              60000);

      RetryConfigQuery query2 =
          new RetryConfigQuery(
              2L, // 不同的 id
              100L,
              "FETCH",
              effectiveFrom,
              null,
              3,
              "EXPONENTIAL",
              1000,
              30000,
              2.0,
              0.1,
              null,
              null,
              true,
              5,
              60000);

      // Then: 两个对象应不相等
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("相同对象的 hashCode 应相等")
    void shouldHaveSameHashCodeForEqualObjects() {
      // Given: 两个具有相同字段值的对象
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      RetryConfigQuery query1 =
          new RetryConfigQuery(
              1L,
              100L,
              "FETCH",
              effectiveFrom,
              null,
              3,
              "EXPONENTIAL",
              1000,
              30000,
              2.0,
              0.1,
              null,
              null,
              true,
              5,
              60000);

      RetryConfigQuery query2 =
          new RetryConfigQuery(
              1L,
              100L,
              "FETCH",
              effectiveFrom,
              null,
              3,
              "EXPONENTIAL",
              1000,
              30000,
              2.0,
              0.1,
              null,
              null,
              true,
              5,
              60000);

      // Then: hashCode 应相等
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("toString 应返回有意义的字符串表示")
    void shouldReturnMeaningfulToString() {
      // Given: 一个对象
      RetryConfigQuery query =
          new RetryConfigQuery(
              1L,
              100L,
              "FETCH",
              Instant.parse("2025-01-01T00:00:00Z"),
              null,
              3,
              "EXPONENTIAL",
              1000,
              30000,
              2.0,
              0.1,
              null,
              null,
              true,
              5,
              60000);

      // When: 调用 toString
      String toString = query.toString();

      // Then: 应包含所有字段信息
      assertThat(toString).contains("RetryConfigQuery");
      assertThat(toString).contains("id=1");
      assertThat(toString).contains("provenanceId=100");
      assertThat(toString).contains("operationType=FETCH");
      assertThat(toString).contains("backoffPolicyTypeCode=EXPONENTIAL");
      assertThat(toString).contains("maxRetryTimes=3");
      assertThat(toString).contains("retryOnNetworkError=true");
    }

    @Test
    @DisplayName("组件访问器应正确返回字段值")
    void shouldAccessComponentsCorrectly() {
      // Given: 一个对象
      Long expectedId = 123L;
      Long expectedProvenanceId = 456L;
      String expectedOperationType = "PARSE";
      Instant expectedEffectiveFrom = Instant.parse("2025-06-15T10:30:00Z");
      Integer expectedMaxRetryTimes = 5;
      String expectedBackoffPolicyTypeCode = "LINEAR";

      RetryConfigQuery query =
          new RetryConfigQuery(
              expectedId,
              expectedProvenanceId,
              expectedOperationType,
              expectedEffectiveFrom,
              null,
              expectedMaxRetryTimes,
              expectedBackoffPolicyTypeCode,
              500,
              10000,
              1.5,
              0.2,
              null,
              null,
              false,
              3,
              30000);

      // Then: 组件访问器应返回正确的值
      assertThat(query.id()).isEqualTo(expectedId);
      assertThat(query.provenanceId()).isEqualTo(expectedProvenanceId);
      assertThat(query.operationType()).isEqualTo(expectedOperationType);
      assertThat(query.effectiveFrom()).isEqualTo(expectedEffectiveFrom);
      assertThat(query.maxRetryTimes()).isEqualTo(expectedMaxRetryTimes);
      assertThat(query.backoffPolicyTypeCode()).isEqualTo(expectedBackoffPolicyTypeCode);
    }
  }

  @Nested
  @DisplayName("不可变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("Record 应是不可变的")
    void shouldBeImmutable() {
      // Given: 一个对象
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      RetryConfigQuery query =
          new RetryConfigQuery(
              1L,
              100L,
              "FETCH",
              effectiveFrom,
              null,
              3,
              "EXPONENTIAL",
              1000,
              30000,
              2.0,
              0.1,
              null,
              null,
              true,
              5,
              60000);

      // Then: Record 的所有字段应是 final 的(通过 record 语义保证)
      // Record 本身是 final class,所有组件都是 final field
      assertThat(query).isNotNull();
      assertThat(query.getClass().isRecord()).isTrue();

      // 验证字段值不会因外部引用改变而改变
      Instant originalEffectiveFrom = query.effectiveFrom();
      assertThat(query.effectiveFrom()).isEqualTo(originalEffectiveFrom);
    }

    @Test
    @DisplayName("构造后修改参数不应影响 Record 内部状态")
    void shouldNotBeAffectedByParameterModification() {
      // Given: 可变的参数
      String operationType = "  FETCH  ";
      String backoffPolicyTypeCode = "  EXPONENTIAL  ";

      // When: 构造对象后修改参数
      RetryConfigQuery query =
          new RetryConfigQuery(
              1L,
              100L,
              operationType,
              Instant.now(),
              null,
              3,
              backoffPolicyTypeCode,
              1000,
              30000,
              2.0,
              0.1,
              null,
              null,
              true,
              5,
              60000);

      // 修改原始参数(虽然 String 是不可变的,这里主要验证 trim 后的独立性)
      operationType = "MODIFIED";
      backoffPolicyTypeCode = "MODIFIED";

      // Then: Record 内部状态不应受影响
      assertThat(query.operationType()).isEqualTo("FETCH");
      assertThat(query.backoffPolicyTypeCode()).isEqualTo("EXPONENTIAL");
    }
  }
}
