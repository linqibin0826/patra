package com.patra.registry.domain.model.read.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link RateLimitConfigQuery} 的单元测试。
/// 
/// @author linqibin
/// @since 0.1.0
@DisplayName("RateLimitConfigQuery 单元测试")
class RateLimitConfigQueryTest {

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
      Integer maxConcurrentRequests = 10;
      Integer perCredentialQpsLimit = 5;

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxConcurrentRequests,
              perCredentialQpsLimit);

      // Then: 所有字段值应正确设置
      assertThat(query.id()).isEqualTo(id);
      assertThat(query.provenanceId()).isEqualTo(provenanceId);
      assertThat(query.operationType()).isEqualTo(operationType);
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(query.maxConcurrentRequests()).isEqualTo(maxConcurrentRequests);
      assertThat(query.perCredentialQpsLimit()).isEqualTo(perCredentialQpsLimit);
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
      Integer maxConcurrentRequests = null; // 可选
      Integer perCredentialQpsLimit = null; // 可选

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxConcurrentRequests,
              perCredentialQpsLimit);

      // Then: 必填字段应正确设置,可选字段为 null
      assertThat(query.id()).isEqualTo(id);
      assertThat(query.provenanceId()).isEqualTo(provenanceId);
      assertThat(query.operationType()).isNull();
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.effectiveTo()).isNull();
      assertThat(query.maxConcurrentRequests()).isNull();
      assertThat(query.perCredentialQpsLimit()).isNull();
    }

    @Test
    @DisplayName("最小必填字段应成功构造")
    void shouldConstructSuccessfullyWithMinimalRequiredFields() {
      // Given: 仅必填字段
      Long id = 999L;
      Long provenanceId = 888L;
      Instant effectiveFrom = Instant.parse("2025-06-15T12:30:00Z");

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null);

      // Then: 必填字段应正确设置
      assertThat(query.id()).isEqualTo(id);
      assertThat(query.provenanceId()).isEqualTo(provenanceId);
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.operationType()).isNull();
      assertThat(query.effectiveTo()).isNull();
      assertThat(query.maxConcurrentRequests()).isNull();
      assertThat(query.perCredentialQpsLimit()).isNull();
    }

    @Test
    @DisplayName("ID 为 Long.MAX_VALUE 时应成功构造")
    void shouldConstructSuccessfullyWithMaxLongId() {
      // Given: ID 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;
      Long provenanceId = Long.MAX_VALUE;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null);

      // Then: 应成功构造
      assertThat(query.id()).isEqualTo(Long.MAX_VALUE);
      assertThat(query.provenanceId()).isEqualTo(Long.MAX_VALUE);
    }
  }

  @Nested
  @DisplayName("ID 验证失败测试")
  class IdValidationTests {

    @Test
    @DisplayName("ID 为 null 时应抛出异常")
    void shouldThrowExceptionWhenIdIsNull() {
      // Given: ID 为 null
      Long id = null;
      Long provenanceId = 100L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When & Then: 应抛出 DomainValidationException
      assertThatThrownBy(
              () ->
                  new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("限流配置ID必须为正数");
    }

    @Test
    @DisplayName("ID 为 0 时应抛出异常")
    void shouldThrowExceptionWhenIdIsZero() {
      // Given: ID 为 0
      Long id = 0L;
      Long provenanceId = 100L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When & Then: 应抛出 DomainValidationException
      assertThatThrownBy(
              () ->
                  new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("限流配置ID必须为正数");
    }

    @Test
    @DisplayName("ID 为负数时应抛出异常")
    void shouldThrowExceptionWhenIdIsNegative() {
      // Given: ID 为负数
      Long id = -1L;
      Long provenanceId = 100L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When & Then: 应抛出 DomainValidationException
      assertThatThrownBy(
              () ->
                  new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("限流配置ID必须为正数");
    }
  }

  @Nested
  @DisplayName("provenanceId 验证失败测试")
  class ProvenanceIdValidationTests {

    @Test
    @DisplayName("provenanceId 为 null 时应抛出异常")
    void shouldThrowExceptionWhenProvenanceIdIsNull() {
      // Given: provenanceId 为 null
      Long id = 1L;
      Long provenanceId = null;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When & Then: 应抛出 DomainValidationException
      assertThatThrownBy(
              () ->
                  new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("来源ID必须为正数");
    }

    @Test
    @DisplayName("provenanceId 为 0 时应抛出异常")
    void shouldThrowExceptionWhenProvenanceIdIsZero() {
      // Given: provenanceId 为 0
      Long id = 1L;
      Long provenanceId = 0L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When & Then: 应抛出 DomainValidationException
      assertThatThrownBy(
              () ->
                  new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("来源ID必须为正数");
    }

    @Test
    @DisplayName("provenanceId 为负数时应抛出异常")
    void shouldThrowExceptionWhenProvenanceIdIsNegative() {
      // Given: provenanceId 为负数
      Long id = 1L;
      Long provenanceId = -100L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When & Then: 应抛出 DomainValidationException
      assertThatThrownBy(
              () ->
                  new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("来源ID必须为正数");
    }
  }

  @Nested
  @DisplayName("effectiveFrom 验证失败测试")
  class EffectiveFromValidationTests {

    @Test
    @DisplayName("effectiveFrom 为 null 时应抛出异常")
    void shouldThrowExceptionWhenEffectiveFromIsNull() {
      // Given: effectiveFrom 为 null
      Long id = 1L;
      Long provenanceId = 100L;
      Instant effectiveFrom = null;

      // When & Then: 应抛出 DomainValidationException
      assertThatThrownBy(
              () ->
                  new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("生效时间不能为null");
    }
  }

  @Nested
  @DisplayName("operationType Trim 逻辑测试")
  class OperationTypeTrimTests {

    @Test
    @DisplayName("operationType 前后空格应被 trim")
    void shouldTrimOperationTypeLeadingAndTrailingWhitespace() {
      // Given: operationType 包含前后空格
      Long id = 1L;
      Long provenanceId = 100L;
      String operationType = "  FETCH  ";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              id, provenanceId, operationType, effectiveFrom, null, null, null);

      // Then: operationType 应被 trim
      assertThat(query.operationType()).isEqualTo("FETCH");
    }

    @Test
    @DisplayName("operationType 仅包含空格时应被 trim 为空字符串")
    void shouldTrimOperationTypeToEmptyStringWhenOnlyWhitespace() {
      // Given: operationType 仅包含空格
      Long id = 1L;
      Long provenanceId = 100L;
      String operationType = "   ";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              id, provenanceId, operationType, effectiveFrom, null, null, null);

      // Then: operationType 应为空字符串
      assertThat(query.operationType()).isEmpty();
    }

    @Test
    @DisplayName("operationType 为 null 时应保持 null")
    void shouldKeepOperationTypeNullWhenNull() {
      // Given: operationType 为 null
      Long id = 1L;
      Long provenanceId = 100L;
      String operationType = null;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              id, provenanceId, operationType, effectiveFrom, null, null, null);

      // Then: operationType 应为 null
      assertThat(query.operationType()).isNull();
    }

    @Test
    @DisplayName("operationType 中间空格应保留")
    void shouldPreserveInnerWhitespaceInOperationType() {
      // Given: operationType 包含中间空格
      Long id = 1L;
      Long provenanceId = 100L;
      String operationType = "  FETCH DATA  ";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              id, provenanceId, operationType, effectiveFrom, null, null, null);

      // Then: 中间空格应保留
      assertThat(query.operationType()).isEqualTo("FETCH DATA");
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同字段值的两个实例应相等")
    void shouldBeEqualWhenFieldsAreIdentical() {
      // Given: 两个具有相同字段值的对象
      Long id = 1L;
      Long provenanceId = 100L;
      String operationType = "FETCH";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      Integer maxConcurrentRequests = 10;
      Integer perCredentialQpsLimit = 5;

      RateLimitConfigQuery query1 =
          new RateLimitConfigQuery(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxConcurrentRequests,
              perCredentialQpsLimit);

      RateLimitConfigQuery query2 =
          new RateLimitConfigQuery(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxConcurrentRequests,
              perCredentialQpsLimit);

      // When & Then: 两个对象应相等
      assertThat(query1).isEqualTo(query2);
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("不同字段值的两个实例应不相等")
    void shouldNotBeEqualWhenFieldsAreDifferent() {
      // Given: 两个具有不同字段值的对象
      RateLimitConfigQuery query1 =
          new RateLimitConfigQuery(
              1L, 100L, "FETCH", Instant.parse("2025-01-01T00:00:00Z"), null, 10, 5);

      RateLimitConfigQuery query2 =
          new RateLimitConfigQuery(
              2L, // 不同的 id
              100L,
              "FETCH",
              Instant.parse("2025-01-01T00:00:00Z"),
              null,
              10,
              5);

      // When & Then: 两个对象应不相等
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("与自身比较应相等")
    void shouldBeEqualToItself() {
      // Given: 一个对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              1L, 100L, "FETCH", Instant.parse("2025-01-01T00:00:00Z"), null, 10, 5);

      // When & Then: 对象应与自身相等
      assertThat(query).isEqualTo(query);
    }

    @Test
    @DisplayName("与 null 比较应不相等")
    void shouldNotBeEqualToNull() {
      // Given: 一个对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              1L, 100L, "FETCH", Instant.parse("2025-01-01T00:00:00Z"), null, 10, 5);

      // When & Then: 对象应与 null 不相等
      assertThat(query).isNotEqualTo(null);
    }

    @Test
    @DisplayName("与不同类型比较应不相等")
    void shouldNotBeEqualToDifferentType() {
      // Given: 一个对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              1L, 100L, "FETCH", Instant.parse("2025-01-01T00:00:00Z"), null, 10, 5);

      // When & Then: 对象应与不同类型不相等
      assertThat(query).isNotEqualTo("not a RateLimitConfigQuery");
    }

    @Test
    @DisplayName("hashCode 应对相等对象保持一致")
    void shouldHaveConsistentHashCodeForEqualObjects() {
      // Given: 两个相等的对象
      Long id = 1L;
      Long provenanceId = 100L;
      String operationType = "FETCH";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      Integer maxConcurrentRequests = 10;
      Integer perCredentialQpsLimit = 5;

      RateLimitConfigQuery query1 =
          new RateLimitConfigQuery(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxConcurrentRequests,
              perCredentialQpsLimit);

      RateLimitConfigQuery query2 =
          new RateLimitConfigQuery(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxConcurrentRequests,
              perCredentialQpsLimit);

      // When & Then: 多次调用 hashCode 应返回相同值
      int hashCode1 = query1.hashCode();
      int hashCode2 = query1.hashCode();
      int hashCode3 = query2.hashCode();

      assertThat(hashCode1).isEqualTo(hashCode2);
      assertThat(hashCode1).isEqualTo(hashCode3);
    }

    @Test
    @DisplayName("toString 应返回非空字符串")
    void shouldReturnNonEmptyStringForToString() {
      // Given: 一个对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              1L, 100L, "FETCH", Instant.parse("2025-01-01T00:00:00Z"), null, 10, 5);

      // When: 调用 toString
      String result = query.toString();

      // Then: 应返回非空字符串
      assertThat(result).isNotEmpty();
      assertThat(result).contains("RateLimitConfigQuery");
      assertThat(result).contains("id=1");
      assertThat(result).contains("provenanceId=100");
      assertThat(result).contains("operationType=FETCH");
    }
  }

  @Nested
  @DisplayName("组件访问器测试")
  class ComponentAccessorTests {

    @Test
    @DisplayName("所有组件访问器应返回正确的值")
    void shouldReturnCorrectValuesForAllAccessors() {
      // Given: 一个完整的对象
      Long id = 1L;
      Long provenanceId = 100L;
      String operationType = "FETCH";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      Integer maxConcurrentRequests = 10;
      Integer perCredentialQpsLimit = 5;

      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxConcurrentRequests,
              perCredentialQpsLimit);

      // When & Then: 每个访问器应返回正确的值
      assertThat(query.id()).isEqualTo(id);
      assertThat(query.provenanceId()).isEqualTo(provenanceId);
      assertThat(query.operationType()).isEqualTo(operationType);
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(query.maxConcurrentRequests()).isEqualTo(maxConcurrentRequests);
      assertThat(query.perCredentialQpsLimit()).isEqualTo(perCredentialQpsLimit);
    }

    @Test
    @DisplayName("可选字段访问器应正确返回 null")
    void shouldReturnNullForOptionalFields() {
      // Given: 可选字段为 null 的对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              1L, 100L, null, Instant.parse("2025-01-01T00:00:00Z"), null, null, null);

      // When & Then: 可选字段访问器应返回 null
      assertThat(query.operationType()).isNull();
      assertThat(query.effectiveTo()).isNull();
      assertThat(query.maxConcurrentRequests()).isNull();
      assertThat(query.perCredentialQpsLimit()).isNull();
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
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");

      RateLimitConfigQuery query =
          new RateLimitConfigQuery(1L, 100L, "FETCH", effectiveFrom, effectiveTo, 10, 5);

      // When: 获取 Instant 引用
      Instant retrievedFrom = query.effectiveFrom();
      Instant retrievedTo = query.effectiveTo();

      // Then: Instant 是不可变的,修改引用不会影响 record
      assertThat(retrievedFrom).isEqualTo(effectiveFrom);
      assertThat(retrievedTo).isEqualTo(effectiveTo);

      // Instant 是不可变类,无法修改
      // 验证返回的是相同的引用(因为 Instant 是不可变的)
      assertThat(retrievedFrom).isSameAs(effectiveFrom);
      assertThat(retrievedTo).isSameAs(effectiveTo);
    }
  }

  @Nested
  @DisplayName("边界值测试")
  class BoundaryValueTests {

    @Test
    @DisplayName("ID 为 1 (最小正数) 时应成功构造")
    void shouldConstructSuccessfullyWithMinimumPositiveId() {
      // Given: ID 为 1
      Long id = 1L;
      Long provenanceId = 1L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null);

      // Then: 应成功构造
      assertThat(query.id()).isEqualTo(1L);
      assertThat(query.provenanceId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("maxConcurrentRequests 为 0 时应成功构造")
    void shouldConstructSuccessfullyWithZeroConcurrentRequests() {
      // Given: maxConcurrentRequests 为 0 (无验证逻辑)
      Long id = 1L;
      Long provenanceId = 100L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Integer maxConcurrentRequests = 0;

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              id, provenanceId, null, effectiveFrom, null, maxConcurrentRequests, null);

      // Then: 应成功构造 (业务验证在别处)
      assertThat(query.maxConcurrentRequests()).isEqualTo(0);
    }

    @Test
    @DisplayName("perCredentialQpsLimit 为负数时应成功构造")
    void shouldConstructSuccessfullyWithNegativeQpsLimit() {
      // Given: perCredentialQpsLimit 为负数 (无验证逻辑)
      Long id = 1L;
      Long provenanceId = 100L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Integer perCredentialQpsLimit = -1;

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(
              id, provenanceId, null, effectiveFrom, null, null, perCredentialQpsLimit);

      // Then: 应成功构造 (业务验证在别处)
      assertThat(query.perCredentialQpsLimit()).isEqualTo(-1);
    }

    @Test
    @DisplayName("effectiveFrom 为 Instant.MIN 时应成功构造")
    void shouldConstructSuccessfullyWithMinInstant() {
      // Given: effectiveFrom 为 Instant.MIN
      Long id = 1L;
      Long provenanceId = 100L;
      Instant effectiveFrom = Instant.MIN;

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null);

      // Then: 应成功构造
      assertThat(query.effectiveFrom()).isEqualTo(Instant.MIN);
    }

    @Test
    @DisplayName("effectiveFrom 为 Instant.MAX 时应成功构造")
    void shouldConstructSuccessfullyWithMaxInstant() {
      // Given: effectiveFrom 为 Instant.MAX
      Long id = 1L;
      Long provenanceId = 100L;
      Instant effectiveFrom = Instant.MAX;

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, null, null, null);

      // Then: 应成功构造
      assertThat(query.effectiveFrom()).isEqualTo(Instant.MAX);
    }

    @Test
    @DisplayName("effectiveTo 早于 effectiveFrom 时应成功构造")
    void shouldConstructSuccessfullyWhenEffectiveToBeforeEffectiveFrom() {
      // Given: effectiveTo 早于 effectiveFrom (无业务验证)
      Long id = 1L;
      Long provenanceId = 100L;
      Instant effectiveFrom = Instant.parse("2025-12-31T23:59:59Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      // When: 构造对象
      RateLimitConfigQuery query =
          new RateLimitConfigQuery(id, provenanceId, null, effectiveFrom, effectiveTo, null, null);

      // Then: 应成功构造 (业务验证在别处)
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(query.effectiveTo()).isBefore(query.effectiveFrom());
    }
  }
}
