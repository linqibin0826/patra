package com.patra.registry.domain.model.vo.provenance;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// RetryConfig 值对象单元测试。
///
/// 测试策略：
///
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 测试 record 的业务约束验证（正整数 ID、非空白字符串、必需字段等）
///   - 验证字符串字段自动 trim 处理
///   - 测试可选字段的 null 处理
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
///
/// 覆盖范围：
///
/// - ✅ record 构造函数验证测试
///   - ✅ 正整数 ID 验证（id, provenanceId）
///   - ✅ 非空白字符串验证（backoffPolicyTypeCode）
///   - ✅ 必需字段非 null 验证（effectiveFrom）
///   - ✅ 字符串 trim 处理测试（operationType, backoffPolicyTypeCode）
///   - ✅ 可选字段处理（effectiveTo, maxRetryTimes 等允许为 null）
///   - ✅ record 的 equals/hashCode/toString 测试
///   - ✅ 不变性保证
///   - ✅ 业务场景测试（不同操作类型、退避策略、熔断器配置等）
///   - ✅ 边界条件处理
///
/// @author Patra Team
/// @since 2.0
@DisplayName("RetryConfig 单元测试")
class RetryConfigTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的重试配置")
    void shouldCreateRetryConfigWithAllFields() {
      // Given: 所有字段都有效
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      Integer maxRetryTimes = 3;
      String backoffPolicyTypeCode = "EXPONENTIAL";
      Integer initialDelayMillis = 1000;
      Integer maxDelayMillis = 60000;
      Double expMultiplierValue = 2.0;
      Double jitterFactorRatio = 0.1;
      String retryHttpStatusJson = "[429, 503]";
      String giveupHttpStatusJson = "[400, 401, 403]";
      boolean retryOnNetworkError = true;
      Integer circuitBreakThreshold = 5;
      Integer circuitCooldownMillis = 30000;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
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

      // Then: 验证所有字段正确赋值
      assertThat(retryConfig).isNotNull();
      assertThat(retryConfig.id()).isEqualTo(id);
      assertThat(retryConfig.provenanceId()).isEqualTo(provenanceId);
      assertThat(retryConfig.operationType()).isEqualTo(operationType);
      assertThat(retryConfig.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(retryConfig.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(retryConfig.maxRetryTimes()).isEqualTo(maxRetryTimes);
      assertThat(retryConfig.backoffPolicyTypeCode()).isEqualTo(backoffPolicyTypeCode);
      assertThat(retryConfig.initialDelayMillis()).isEqualTo(initialDelayMillis);
      assertThat(retryConfig.maxDelayMillis()).isEqualTo(maxDelayMillis);
      assertThat(retryConfig.expMultiplierValue()).isEqualTo(expMultiplierValue);
      assertThat(retryConfig.jitterFactorRatio()).isEqualTo(jitterFactorRatio);
      assertThat(retryConfig.retryHttpStatusJson()).isEqualTo(retryHttpStatusJson);
      assertThat(retryConfig.giveupHttpStatusJson()).isEqualTo(giveupHttpStatusJson);
      assertThat(retryConfig.retryOnNetworkError()).isTrue();
      assertThat(retryConfig.circuitBreakThreshold()).isEqualTo(circuitBreakThreshold);
      assertThat(retryConfig.circuitCooldownMillis()).isEqualTo(circuitCooldownMillis);
    }

    @Test
    @DisplayName("应该成功创建仅包含必需字段的最小配置")
    void shouldCreateMinimalRetryConfig() {
      // Given: 只有必需字段
      Long id = 1001L;
      Long provenanceId = 2001L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      String backoffPolicyTypeCode = "FIXED";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              id,
              provenanceId,
              null, // operationType
              effectiveFrom,
              null, // effectiveTo
              null, // maxRetryTimes
              backoffPolicyTypeCode,
              null, // initialDelayMillis
              null, // maxDelayMillis
              null, // expMultiplierValue
              null, // jitterFactorRatio
              null, // retryHttpStatusJson
              null, // giveupHttpStatusJson
              false, // retryOnNetworkError
              null, // circuitBreakThreshold
              null // circuitCooldownMillis
              );

      // Then: 验证必需字段正确赋值，可选字段为 null
      assertThat(retryConfig).isNotNull();
      assertThat(retryConfig.id()).isEqualTo(id);
      assertThat(retryConfig.provenanceId()).isEqualTo(provenanceId);
      assertThat(retryConfig.operationType()).isNull();
      assertThat(retryConfig.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(retryConfig.effectiveTo()).isNull();
      assertThat(retryConfig.maxRetryTimes()).isNull();
      assertThat(retryConfig.backoffPolicyTypeCode()).isEqualTo(backoffPolicyTypeCode);
      assertThat(retryConfig.initialDelayMillis()).isNull();
      assertThat(retryConfig.maxDelayMillis()).isNull();
      assertThat(retryConfig.expMultiplierValue()).isNull();
      assertThat(retryConfig.jitterFactorRatio()).isNull();
      assertThat(retryConfig.retryHttpStatusJson()).isNull();
      assertThat(retryConfig.giveupHttpStatusJson()).isNull();
      assertThat(retryConfig.retryOnNetworkError()).isFalse();
      assertThat(retryConfig.circuitBreakThreshold()).isNull();
      assertThat(retryConfig.circuitCooldownMillis()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 effectiveTo 为 null 的永久有效配置")
    void shouldCreatePermanentConfig() {
      // Given: effectiveTo 为 null（表示永久有效）
      Long id = 1001L;
      Long provenanceId = 2001L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              id,
              provenanceId,
              "HARVEST",
              effectiveFrom,
              effectiveTo,
              3,
              "EXPONENTIAL",
              1000,
              60000,
              2.0,
              0.1,
              null,
              null,
              true,
              null,
              null);

      // Then: 验证 effectiveTo 为 null
      assertThat(retryConfig.effectiveTo()).isNull();
    }
  }

  // ========== ID 验证测试 ==========

  @Nested
  @DisplayName("ID 正整数验证")
  class IdValidationTests {

    @Test
    @DisplayName("应该抛出异常当 id 为 null")
    void shouldThrowExceptionWhenIdIsNull() {
      // Given: id 为 null
      Long id = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new RetryConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      3,
                      "FIXED",
                      1000,
                      60000,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Retry config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为 0")
    void shouldThrowExceptionWhenIdIsZero() {
      // Given: id 为 0
      Long id = 0L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new RetryConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      3,
                      "FIXED",
                      1000,
                      60000,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Retry config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为负数")
    void shouldThrowExceptionWhenIdIsNegative() {
      // Given: id 为负数
      Long id = -1L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new RetryConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      3,
                      "FIXED",
                      1000,
                      60000,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Retry config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 id 为 1 的配置")
    void shouldCreateConfigWithIdOne() {
      // Given: id 为 1
      Long id = 1L;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              id,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
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

      // Then: 验证成功创建
      assertThat(retryConfig.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功创建 id 为 Long.MAX_VALUE 的配置")
    void shouldCreateConfigWithMaxId() {
      // Given: id 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              id,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
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

      // Then: 验证成功创建
      assertThat(retryConfig.id()).isEqualTo(Long.MAX_VALUE);
    }
  }

  @Nested
  @DisplayName("ProvenanceId 正整数验证")
  class ProvenanceIdValidationTests {

    @Test
    @DisplayName("应该抛出异常当 provenanceId 为 null")
    void shouldThrowExceptionWhenProvenanceIdIsNull() {
      // Given: provenanceId 为 null
      Long provenanceId = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new RetryConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      3,
                      "FIXED",
                      1000,
                      60000,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 provenanceId 为 0")
    void shouldThrowExceptionWhenProvenanceIdIsZero() {
      // Given: provenanceId 为 0
      Long provenanceId = 0L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new RetryConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      3,
                      "FIXED",
                      1000,
                      60000,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 provenanceId 为负数")
    void shouldThrowExceptionWhenProvenanceIdIsNegative() {
      // Given: provenanceId 为负数
      Long provenanceId = -1L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new RetryConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      3,
                      "FIXED",
                      1000,
                      60000,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 provenanceId 为 1 的配置")
    void shouldCreateConfigWithProvenanceIdOne() {
      // Given: provenanceId 为 1
      Long provenanceId = 1L;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              provenanceId,
              "HARVEST",
              Instant.now(),
              null,
              null,
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

      // Then: 验证成功创建
      assertThat(retryConfig.provenanceId()).isEqualTo(1L);
    }
  }

  // ========== EffectiveFrom 验证测试 ==========

  @Nested
  @DisplayName("EffectiveFrom 非 null 验证")
  class EffectiveFromValidationTests {

    @Test
    @DisplayName("应该抛出异常当 effectiveFrom 为 null")
    void shouldThrowExceptionWhenEffectiveFromIsNull() {
      // Given: effectiveFrom 为 null
      Instant effectiveFrom = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new RetryConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      effectiveFrom,
                      null,
                      3,
                      "FIXED",
                      1000,
                      60000,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Effective from")
          .hasMessageContaining("不能为 null");
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为当前时间的配置")
    void shouldCreateConfigWithCurrentEffectiveFrom() {
      // Given: effectiveFrom 为当前时间
      Instant effectiveFrom = Instant.now();

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
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

      // Then: 验证成功创建
      assertThat(retryConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为过去时间的配置")
    void shouldCreateConfigWithPastEffectiveFrom() {
      // Given: effectiveFrom 为过去时间
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
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

      // Then: 验证成功创建
      assertThat(retryConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为未来时间的配置")
    void shouldCreateConfigWithFutureEffectiveFrom() {
      // Given: effectiveFrom 为未来时间
      Instant effectiveFrom = Instant.parse("2026-01-01T00:00:00Z");

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
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

      // Then: 验证成功创建
      assertThat(retryConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }
  }

  // ========== BackoffPolicyTypeCode 验证测试 ==========

  @Nested
  @DisplayName("BackoffPolicyTypeCode 非空白验证")
  class BackoffPolicyTypeCodeValidationTests {

    @Test
    @DisplayName("应该抛出异常当 backoffPolicyTypeCode 为 null")
    void shouldThrowExceptionWhenBackoffPolicyTypeCodeIsNull() {
      // Given: backoffPolicyTypeCode 为 null
      String backoffPolicyTypeCode = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new RetryConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      3,
                      backoffPolicyTypeCode,
                      1000,
                      60000,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Backoff policy type code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 backoffPolicyTypeCode 为空字符串")
    void shouldThrowExceptionWhenBackoffPolicyTypeCodeIsEmpty() {
      // Given: backoffPolicyTypeCode 为空字符串
      String backoffPolicyTypeCode = "";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new RetryConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      3,
                      backoffPolicyTypeCode,
                      1000,
                      60000,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Backoff policy type code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 backoffPolicyTypeCode 仅包含空白字符")
    void shouldThrowExceptionWhenBackoffPolicyTypeCodeIsBlank() {
      // Given: backoffPolicyTypeCode 仅包含空白字符
      String backoffPolicyTypeCode = "   ";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new RetryConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      3,
                      backoffPolicyTypeCode,
                      1000,
                      60000,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Backoff policy type code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim backoffPolicyTypeCode 字段")
    void shouldTrimBackoffPolicyTypeCode() {
      // Given: backoffPolicyTypeCode 包含首尾空白
      String backoffPolicyTypeCode = "  EXPONENTIAL  ";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
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

      // Then: 验证 backoffPolicyTypeCode 已被 trim
      assertThat(retryConfig.backoffPolicyTypeCode()).isEqualTo("EXPONENTIAL");
    }
  }

  // ========== 字符串 Trim 测试 ==========

  @Nested
  @DisplayName("字符串字段 Trim 处理")
  class StringTrimTests {

    @Test
    @DisplayName("应该自动 trim operationType 字段")
    void shouldTrimOperationType() {
      // Given: operationType 包含首尾空白
      String operationType = "  HARVEST  ";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              null,
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

      // Then: 验证 operationType 已被 trim
      assertThat(retryConfig.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该 trim 所有字符串字段")
    void shouldTrimAllStringFields() {
      // Given: 所有字符串字段包含首尾空白
      String operationType = "  UPDATE  ";
      String backoffPolicyTypeCode = "  LINEAR  ";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
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

      // Then: 验证所有字段都已被 trim
      assertThat(retryConfig.operationType()).isEqualTo("UPDATE");
      assertThat(retryConfig.backoffPolicyTypeCode()).isEqualTo("LINEAR");
    }

    @Test
    @DisplayName("应该处理混合空白字符的字符串")
    void shouldHandleStringWithMixedWhitespace() {
      // Given: 包含制表符、换行符等混合空白的字符串
      String operationType = "\t\n  BACKFILL  \t\n";
      String backoffPolicyTypeCode = " \t EXPONENTIAL \n ";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
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

      // Then: 验证空白字符都被 trim
      assertThat(retryConfig.operationType()).isEqualTo("BACKFILL");
      assertThat(retryConfig.backoffPolicyTypeCode()).isEqualTo("EXPONENTIAL");
    }
  }

  // ========== Null 处理测试 ==========

  @Nested
  @DisplayName("可选字段 Null 处理")
  class NullHandlingTests {

    @Test
    @DisplayName("operationType 为 null 时应保持 null")
    void operationTypeCanBeNull() {
      // Given: operationType 为 null
      String operationType = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              null,
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

      // Then: 验证 operationType 为 null
      assertThat(retryConfig.operationType()).isNull();
    }

    @Test
    @DisplayName("effectiveTo 为 null 时应保持 null（表示永久有效）")
    void effectiveToCanBeNull() {
      // Given: effectiveTo 为 null
      Instant effectiveTo = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              effectiveTo,
              null,
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

      // Then: 验证 effectiveTo 为 null
      assertThat(retryConfig.effectiveTo()).isNull();
    }

    @Test
    @DisplayName("maxRetryTimes 为 null 时应允许（表示使用默认值）")
    void maxRetryTimesCanBeNull() {
      // Given: maxRetryTimes 为 null
      Integer maxRetryTimes = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              maxRetryTimes,
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

      // Then: 验证 maxRetryTimes 为 null
      assertThat(retryConfig.maxRetryTimes()).isNull();
    }

    @Test
    @DisplayName("initialDelayMillis 为 null 时应允许")
    void initialDelayMillisCanBeNull() {
      // Given: initialDelayMillis 为 null
      Integer initialDelayMillis = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              initialDelayMillis,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 initialDelayMillis 为 null
      assertThat(retryConfig.initialDelayMillis()).isNull();
    }

    @Test
    @DisplayName("maxDelayMillis 为 null 时应允许")
    void maxDelayMillisCanBeNull() {
      // Given: maxDelayMillis 为 null
      Integer maxDelayMillis = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              maxDelayMillis,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 maxDelayMillis 为 null
      assertThat(retryConfig.maxDelayMillis()).isNull();
    }

    @Test
    @DisplayName("expMultiplierValue 为 null 时应允许")
    void expMultiplierValueCanBeNull() {
      // Given: expMultiplierValue 为 null
      Double expMultiplierValue = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              expMultiplierValue,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 expMultiplierValue 为 null
      assertThat(retryConfig.expMultiplierValue()).isNull();
    }

    @Test
    @DisplayName("jitterFactorRatio 为 null 时应允许")
    void jitterFactorRatioCanBeNull() {
      // Given: jitterFactorRatio 为 null
      Double jitterFactorRatio = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              jitterFactorRatio,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 jitterFactorRatio 为 null
      assertThat(retryConfig.jitterFactorRatio()).isNull();
    }

    @Test
    @DisplayName("retryHttpStatusJson 为 null 时应允许")
    void retryHttpStatusJsonCanBeNull() {
      // Given: retryHttpStatusJson 为 null
      String retryHttpStatusJson = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              retryHttpStatusJson,
              null,
              false,
              null,
              null);

      // Then: 验证 retryHttpStatusJson 为 null
      assertThat(retryConfig.retryHttpStatusJson()).isNull();
    }

    @Test
    @DisplayName("giveupHttpStatusJson 为 null 时应允许")
    void giveupHttpStatusJsonCanBeNull() {
      // Given: giveupHttpStatusJson 为 null
      String giveupHttpStatusJson = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              giveupHttpStatusJson,
              false,
              null,
              null);

      // Then: 验证 giveupHttpStatusJson 为 null
      assertThat(retryConfig.giveupHttpStatusJson()).isNull();
    }

    @Test
    @DisplayName("circuitBreakThreshold 为 null 时应允许（表示禁用熔断器）")
    void circuitBreakThresholdCanBeNull() {
      // Given: circuitBreakThreshold 为 null
      Integer circuitBreakThreshold = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              circuitBreakThreshold,
              null);

      // Then: 验证 circuitBreakThreshold 为 null
      assertThat(retryConfig.circuitBreakThreshold()).isNull();
    }

    @Test
    @DisplayName("circuitCooldownMillis 为 null 时应允许")
    void circuitCooldownMillisCanBeNull() {
      // Given: circuitCooldownMillis 为 null
      Integer circuitCooldownMillis = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              circuitCooldownMillis);

      // Then: 验证 circuitCooldownMillis 为 null
      assertThat(retryConfig.circuitCooldownMillis()).isNull();
    }

    @Test
    @DisplayName("应该处理所有可选字段都为 null 的情况")
    void shouldHandleAllOptionalFieldsBeingNull() {
      // Given: 所有可选字段为 null
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              null,
              Instant.now(),
              null,
              null,
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

      // Then: 验证可选字段都为 null
      assertThat(retryConfig.operationType()).isNull();
      assertThat(retryConfig.effectiveTo()).isNull();
      assertThat(retryConfig.maxRetryTimes()).isNull();
      assertThat(retryConfig.initialDelayMillis()).isNull();
      assertThat(retryConfig.maxDelayMillis()).isNull();
      assertThat(retryConfig.expMultiplierValue()).isNull();
      assertThat(retryConfig.jitterFactorRatio()).isNull();
      assertThat(retryConfig.retryHttpStatusJson()).isNull();
      assertThat(retryConfig.giveupHttpStatusJson()).isNull();
      assertThat(retryConfig.circuitBreakThreshold()).isNull();
      assertThat(retryConfig.circuitCooldownMillis()).isNull();
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该正确实现 equals 方法（相同值对象相等）")
    void shouldImplementEqualsCorrectly() {
      // Given: 两个相同值的配置
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      String backoffPolicyTypeCode = "EXPONENTIAL";

      RetryConfig retryConfig1 =
          new RetryConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              null,
              3,
              backoffPolicyTypeCode,
              1000,
              60000,
              2.0,
              0.1,
              "[429, 503]",
              "[400, 401]",
              true,
              5,
              30000);

      RetryConfig retryConfig2 =
          new RetryConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              null,
              3,
              backoffPolicyTypeCode,
              1000,
              60000,
              2.0,
              0.1,
              "[429, 503]",
              "[400, 401]",
              true,
              5,
              30000);

      // When & Then: 应该相等
      assertThat(retryConfig1).isEqualTo(retryConfig2);
      assertThat(retryConfig1).hasSameHashCodeAs(retryConfig2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同值对象不相等）")
    void shouldImplementEqualsCorrectlyForDifferentObjects() {
      // Given: 两个不同值的配置
      RetryConfig retryConfig1 =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
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

      RetryConfig retryConfig2 =
          new RetryConfig(
              1002L,
              2002L,
              "UPDATE",
              Instant.now(),
              null,
              5,
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

      // When & Then: 不应该相等
      assertThat(retryConfig1).isNotEqualTo(retryConfig2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      RetryConfig retryConfig1 =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
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

      RetryConfig retryConfig2 =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
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

      // When & Then: hashCode 应该相等
      assertThat(retryConfig1.hashCode()).isEqualTo(retryConfig2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建配置
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.parse("2025-01-01T00:00:00Z"),
              null,
              3,
              "EXPONENTIAL",
              1000,
              60000,
              2.0,
              0.1,
              "[429, 503]",
              "[400, 401]",
              true,
              5,
              30000);

      // When: 调用 toString
      String toString = retryConfig.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("RetryConfig");
      assertThat(toString).contains("1001");
      assertThat(toString).contains("2001");
      assertThat(toString).contains("HARVEST");
      assertThat(toString).contains("EXPONENTIAL");
    }

    @Test
    @DisplayName("应该支持 equals 自反性")
    void shouldSupportEqualsReflexivity() {
      // Given: 创建配置
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
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

      // When & Then: 对象应该等于自身
      assertThat(retryConfig).isEqualTo(retryConfig);
    }

    @Test
    @DisplayName("应该支持 equals 对称性")
    void shouldSupportEqualsSymmetry() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      RetryConfig retryConfig1 =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
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

      RetryConfig retryConfig2 =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
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

      // When & Then: 对称性（a.equals(b) == b.equals(a)）
      assertThat(retryConfig1.equals(retryConfig2)).isEqualTo(retryConfig2.equals(retryConfig1));
      assertThat(retryConfig1).isEqualTo(retryConfig2);
      assertThat(retryConfig2).isEqualTo(retryConfig1);
    }

    @Test
    @DisplayName("应该支持 equals 传递性")
    void shouldSupportEqualsTransitivity() {
      // Given: 三个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      RetryConfig retryConfig1 =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
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

      RetryConfig retryConfig2 =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
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

      RetryConfig retryConfig3 =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
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

      // When & Then: 传递性（a.equals(b) && b.equals(c) => a.equals(c)）
      assertThat(retryConfig1).isEqualTo(retryConfig2);
      assertThat(retryConfig2).isEqualTo(retryConfig3);
      assertThat(retryConfig1).isEqualTo(retryConfig3);
    }

    @Test
    @DisplayName("应该正确处理与 null 的比较")
    void shouldHandleNullComparison() {
      // Given: 创建配置
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
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

      // When & Then: 与 null 比较应该返回 false
      assertThat(retryConfig).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确处理与不同类型对象的比较")
    void shouldHandleDifferentTypeComparison() {
      // Given: 创建配置
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
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

      // When & Then: 与不同类型对象比较应该返回 false
      assertThat(retryConfig).isNotEqualTo("Not a RetryConfig");
      assertThat(retryConfig).isNotEqualTo(1001L);
    }
  }

  // ========== 不变性测试 ==========

  @Nested
  @DisplayName("不变性保证")
  class ImmutabilityTests {

    @Test
    @DisplayName("Record 字段应该是不可变的")
    void recordFieldsShouldBeImmutable() {
      // Given: 创建配置
      Long originalId = 1001L;
      Long originalProvenanceId = 2001L;
      String originalOperationType = "HARVEST";
      Instant originalEffectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      String originalBackoffPolicyTypeCode = "EXPONENTIAL";

      RetryConfig retryConfig =
          new RetryConfig(
              originalId,
              originalProvenanceId,
              originalOperationType,
              originalEffectiveFrom,
              null,
              3,
              originalBackoffPolicyTypeCode,
              1000,
              60000,
              2.0,
              0.1,
              null,
              null,
              true,
              5,
              30000);

      // When: 获取字段值
      Long retrievedId = retryConfig.id();
      Long retrievedProvenanceId = retryConfig.provenanceId();
      String retrievedOperationType = retryConfig.operationType();
      Instant retrievedEffectiveFrom = retryConfig.effectiveFrom();
      String retrievedBackoffPolicyTypeCode = retryConfig.backoffPolicyTypeCode();

      // Then: 字段值应该保持不变
      assertThat(retrievedId).isEqualTo(originalId);
      assertThat(retrievedProvenanceId).isEqualTo(originalProvenanceId);
      assertThat(retrievedOperationType).isEqualTo(originalOperationType);
      assertThat(retrievedEffectiveFrom).isEqualTo(originalEffectiveFrom);
      assertThat(retrievedBackoffPolicyTypeCode).isEqualTo(originalBackoffPolicyTypeCode);
    }

    @Test
    @DisplayName("字符串字段应该在创建后保持不变")
    void stringFieldsShouldRemainUnchangedAfterCreation() {
      // Given: 创建配置
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
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

      // When: 多次获取字段值
      String operationType1 = retryConfig.operationType();
      String operationType2 = retryConfig.operationType();
      String backoffPolicyTypeCode1 = retryConfig.backoffPolicyTypeCode();
      String backoffPolicyTypeCode2 = retryConfig.backoffPolicyTypeCode();

      // Then: 字段值应该保持一致
      assertThat(operationType1).isEqualTo(operationType2);
      assertThat(backoffPolicyTypeCode1).isEqualTo(backoffPolicyTypeCode2);
      assertThat(operationType1).isSameAs(operationType2);
      assertThat(backoffPolicyTypeCode1).isSameAs(backoffPolicyTypeCode2);
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenariosTests {

    @Test
    @DisplayName("应该成功创建 operationType 为 HARVEST 的配置")
    void shouldCreateConfigWithOperationTypeHarvest() {
      // Given: operationType 为 HARVEST
      String operationType = "HARVEST";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              null,
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

      // Then: 验证成功创建
      assertThat(retryConfig.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 UPDATE 的配置")
    void shouldCreateConfigWithOperationTypeUpdate() {
      // Given: operationType 为 UPDATE
      String operationType = "UPDATE";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              null,
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

      // Then: 验证成功创建
      assertThat(retryConfig.operationType()).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 BACKFILL 的配置")
    void shouldCreateConfigWithOperationTypeBackfill() {
      // Given: operationType 为 BACKFILL
      String operationType = "BACKFILL";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              null,
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

      // Then: 验证成功创建
      assertThat(retryConfig.operationType()).isEqualTo("BACKFILL");
    }

    @Test
    @DisplayName("应该成功创建 backoffPolicyTypeCode 为 FIXED 的配置")
    void shouldCreateConfigWithBackoffPolicyFixed() {
      // Given: backoffPolicyTypeCode 为 FIXED
      String backoffPolicyTypeCode = "FIXED";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              backoffPolicyTypeCode,
              1000,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(retryConfig.backoffPolicyTypeCode()).isEqualTo("FIXED");
    }

    @Test
    @DisplayName("应该成功创建 backoffPolicyTypeCode 为 LINEAR 的配置")
    void shouldCreateConfigWithBackoffPolicyLinear() {
      // Given: backoffPolicyTypeCode 为 LINEAR
      String backoffPolicyTypeCode = "LINEAR";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              backoffPolicyTypeCode,
              1000,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(retryConfig.backoffPolicyTypeCode()).isEqualTo("LINEAR");
    }

    @Test
    @DisplayName("应该成功创建 backoffPolicyTypeCode 为 EXPONENTIAL 的配置")
    void shouldCreateConfigWithBackoffPolicyExponential() {
      // Given: backoffPolicyTypeCode 为 EXPONENTIAL
      String backoffPolicyTypeCode = "EXPONENTIAL";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              backoffPolicyTypeCode,
              1000,
              60000,
              2.0,
              0.1,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(retryConfig.backoffPolicyTypeCode()).isEqualTo("EXPONENTIAL");
      assertThat(retryConfig.expMultiplierValue()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("应该成功创建 maxRetryTimes 为 0（不重试）的配置")
    void shouldCreateConfigWithZeroMaxRetryTimes() {
      // Given: maxRetryTimes 为 0（表示不重试）
      Integer maxRetryTimes = 0;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              maxRetryTimes,
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

      // Then: 验证成功创建
      assertThat(retryConfig.maxRetryTimes()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该成功创建 maxRetryTimes 为正数的配置")
    void shouldCreateConfigWithPositiveMaxRetryTimes() {
      // Given: maxRetryTimes 为正数
      Integer maxRetryTimes = 5;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              maxRetryTimes,
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

      // Then: 验证成功创建
      assertThat(retryConfig.maxRetryTimes()).isEqualTo(5);
    }

    @Test
    @DisplayName("应该成功创建 retryOnNetworkError 为 true 的配置")
    void shouldCreateConfigWithRetryOnNetworkErrorTrue() {
      // Given: retryOnNetworkError 为 true
      boolean retryOnNetworkError = true;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              null,
              retryOnNetworkError,
              null,
              null);

      // Then: 验证成功创建
      assertThat(retryConfig.retryOnNetworkError()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建 retryOnNetworkError 为 false 的配置")
    void shouldCreateConfigWithRetryOnNetworkErrorFalse() {
      // Given: retryOnNetworkError 为 false
      boolean retryOnNetworkError = false;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              null,
              retryOnNetworkError,
              null,
              null);

      // Then: 验证成功创建
      assertThat(retryConfig.retryOnNetworkError()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建启用熔断器的配置")
    void shouldCreateConfigWithCircuitBreakerEnabled() {
      // Given: 熔断器配置
      Integer circuitBreakThreshold = 5;
      Integer circuitCooldownMillis = 30000;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              circuitBreakThreshold,
              circuitCooldownMillis);

      // Then: 验证熔断器配置正确赋值
      assertThat(retryConfig.circuitBreakThreshold()).isEqualTo(5);
      assertThat(retryConfig.circuitCooldownMillis()).isEqualTo(30000);
    }

    @Test
    @DisplayName("应该成功创建禁用熔断器的配置（circuitBreakThreshold 为 null）")
    void shouldCreateConfigWithCircuitBreakerDisabled() {
      // Given: circuitBreakThreshold 为 null（表示禁用熔断器）
      Integer circuitBreakThreshold = null;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              circuitBreakThreshold,
              null);

      // Then: 验证熔断器禁用
      assertThat(retryConfig.circuitBreakThreshold()).isNull();
    }

    @Test
    @DisplayName("应该成功创建包含 HTTP 状态码配置的配置")
    void shouldCreateConfigWithHttpStatusCodes() {
      // Given: HTTP 状态码配置
      String retryHttpStatusJson = "[429, 503, 504]";
      String giveupHttpStatusJson = "[400, 401, 403, 404]";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              retryHttpStatusJson,
              giveupHttpStatusJson,
              false,
              null,
              null);

      // Then: 验证 HTTP 状态码配置正确赋值
      assertThat(retryConfig.retryHttpStatusJson()).isEqualTo("[429, 503, 504]");
      assertThat(retryConfig.giveupHttpStatusJson()).isEqualTo("[400, 401, 403, 404]");
    }

    @Test
    @DisplayName("应该成功创建完整的指数退避配置")
    void shouldCreateCompleteExponentialBackoffConfig() {
      // Given: 完整的指数退避配置
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.parse("2025-01-01T00:00:00Z"),
              Instant.parse("2025-12-31T23:59:59Z"),
              5,
              "EXPONENTIAL",
              1000,
              60000,
              2.0,
              0.2,
              "[429, 503, 504]",
              "[400, 401, 403]",
              true,
              10,
              60000);

      // Then: 验证所有字段
      assertThat(retryConfig.maxRetryTimes()).isEqualTo(5);
      assertThat(retryConfig.backoffPolicyTypeCode()).isEqualTo("EXPONENTIAL");
      assertThat(retryConfig.initialDelayMillis()).isEqualTo(1000);
      assertThat(retryConfig.maxDelayMillis()).isEqualTo(60000);
      assertThat(retryConfig.expMultiplierValue()).isEqualTo(2.0);
      assertThat(retryConfig.jitterFactorRatio()).isEqualTo(0.2);
      assertThat(retryConfig.retryOnNetworkError()).isTrue();
      assertThat(retryConfig.circuitBreakThreshold()).isEqualTo(10);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件处理")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理 operationType 为极短字符串的情况")
    void shouldHandleMinimalOperationType() {
      // Given: operationType 为单字符
      String operationType = "A";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              null,
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

      // Then: 应该成功创建
      assertThat(retryConfig.operationType()).isEqualTo("A");
    }

    @Test
    @DisplayName("应该处理 backoffPolicyTypeCode 为极短字符串的情况")
    void shouldHandleMinimalBackoffPolicyTypeCode() {
      // Given: backoffPolicyTypeCode 为单字符
      String backoffPolicyTypeCode = "X";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
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

      // Then: 应该成功创建
      assertThat(retryConfig.backoffPolicyTypeCode()).isEqualTo("X");
    }

    @Test
    @DisplayName("应该处理 trim 后相同的不同输入")
    void shouldHandleDifferentInputsWithSameTrimmedValue() {
      // Given: trim 后相同的不同输入
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      RetryConfig retryConfig1 =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              null,
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

      RetryConfig retryConfig2 =
          new RetryConfig(
              1001L,
              2001L,
              "  HARVEST  ",
              effectiveFrom,
              null,
              null,
              "  FIXED  ",
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When & Then: trim 后应该相等
      assertThat(retryConfig1).isEqualTo(retryConfig2);
    }

    @Test
    @DisplayName("应该处理 initialDelayMillis 为 0 的情况")
    void shouldHandleZeroInitialDelay() {
      // Given: initialDelayMillis 为 0
      Integer initialDelayMillis = 0;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              initialDelayMillis,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(retryConfig.initialDelayMillis()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该处理 expMultiplierValue 为 1.0 的情况")
    void shouldHandleExpMultiplierValueOne() {
      // Given: expMultiplierValue 为 1.0
      Double expMultiplierValue = 1.0;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "EXPONENTIAL",
              1000,
              null,
              expMultiplierValue,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(retryConfig.expMultiplierValue()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("应该处理 jitterFactorRatio 为 0.0 的情况")
    void shouldHandleZeroJitterFactor() {
      // Given: jitterFactorRatio 为 0.0（无抖动）
      Double jitterFactorRatio = 0.0;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              jitterFactorRatio,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(retryConfig.jitterFactorRatio()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("应该处理 jitterFactorRatio 为 1.0 的情况")
    void shouldHandleMaxJitterFactor() {
      // Given: jitterFactorRatio 为 1.0（最大抖动）
      Double jitterFactorRatio = 1.0;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              jitterFactorRatio,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(retryConfig.jitterFactorRatio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("应该处理包含复杂 JSON 的 HTTP 状态码配置")
    void shouldHandleComplexHttpStatusJson() {
      // Given: 复杂的 JSON 配置
      String retryHttpStatusJson = "[429, 500, 502, 503, 504]";
      String giveupHttpStatusJson = "[400, 401, 403, 404, 405]";

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              retryHttpStatusJson,
              giveupHttpStatusJson,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(retryConfig.retryHttpStatusJson()).isEqualTo(retryHttpStatusJson);
      assertThat(retryConfig.giveupHttpStatusJson()).isEqualTo(giveupHttpStatusJson);
    }

    @Test
    @DisplayName("应该处理 circuitBreakThreshold 为 1 的情况")
    void shouldHandleMinimalCircuitBreakThreshold() {
      // Given: circuitBreakThreshold 为 1（第一次失败就熔断）
      Integer circuitBreakThreshold = 1;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              circuitBreakThreshold,
              5000);

      // Then: 应该成功创建
      assertThat(retryConfig.circuitBreakThreshold()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该处理 circuitCooldownMillis 为 0 的情况")
    void shouldHandleZeroCircuitCooldown() {
      // Given: circuitCooldownMillis 为 0（立即尝试）
      Integer circuitCooldownMillis = 0;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "FIXED",
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              5,
              circuitCooldownMillis);

      // Then: 应该成功创建
      assertThat(retryConfig.circuitCooldownMillis()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该处理 maxDelayMillis 为 Integer.MAX_VALUE 的情况")
    void shouldHandleMaxIntegerDelay() {
      // Given: maxDelayMillis 为 Integer.MAX_VALUE
      Integer maxDelayMillis = Integer.MAX_VALUE;

      // When: 创建 RetryConfig
      RetryConfig retryConfig =
          new RetryConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              "EXPONENTIAL",
              1000,
              maxDelayMillis,
              2.0,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(retryConfig.maxDelayMillis()).isEqualTo(Integer.MAX_VALUE);
    }
  }
}
