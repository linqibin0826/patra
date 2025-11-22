package com.patra.registry.domain.model.vo.provenance;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// RateLimitConfig 值对象单元测试。
///
/// 测试策略：
///
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 测试 record 的业务约束验证（正整数 ID、非 null 时间戳等）
///   - 验证字符串字段自动 trim 处理
///   - 测试可选字段的 null 处理
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
///
/// 覆盖范围：
///
/// - ✅ record 构造函数验证测试
///   - ✅ 正整数 ID 验证（id, provenanceId）
///   - ✅ 必需字段非 null 验证（effectiveFrom）
///   - ✅ 字符串 trim 处理测试（operationType）
///   - ✅ 可选字段处理（effectiveTo, maxConcurrentRequests, perCredentialQpsLimit 允许为 null）
///   - ✅ record 的 equals/hashCode/toString 测试
///   - ✅ 不变性保证
///   - ✅ 业务场景测试（不同操作类型、速率限制配置等）
///   - ✅ 边界条件处理
///
/// @author Patra Team
/// @since 2.0
@DisplayName("RateLimitConfig 单元测试")
class RateLimitConfigTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的速率限制配置")
    void shouldCreateRateLimitConfigWithAllFields() {
      // Given: 所有字段都有效
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      Integer maxConcurrentRequests = 10;
      Integer perCredentialQpsLimit = 5;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxConcurrentRequests,
              perCredentialQpsLimit);

      // Then: 验证所有字段正确赋值
      assertThat(rateLimitConfig).isNotNull();
      assertThat(rateLimitConfig.id()).isEqualTo(id);
      assertThat(rateLimitConfig.provenanceId()).isEqualTo(provenanceId);
      assertThat(rateLimitConfig.operationType()).isEqualTo(operationType);
      assertThat(rateLimitConfig.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(rateLimitConfig.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(rateLimitConfig.maxConcurrentRequests()).isEqualTo(maxConcurrentRequests);
      assertThat(rateLimitConfig.perCredentialQpsLimit()).isEqualTo(perCredentialQpsLimit);
    }

    @Test
    @DisplayName("应该成功创建仅包含必需字段的最小配置")
    void shouldCreateMinimalRateLimitConfig() {
      // Given: 只有必需字段
      Long id = 1001L;
      Long provenanceId = 2001L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              id,
              provenanceId,
              null, // operationType
              effectiveFrom,
              null, // effectiveTo
              null, // maxConcurrentRequests
              null // perCredentialQpsLimit
              );

      // Then: 验证必需字段正确赋值，可选字段为 null
      assertThat(rateLimitConfig).isNotNull();
      assertThat(rateLimitConfig.id()).isEqualTo(id);
      assertThat(rateLimitConfig.provenanceId()).isEqualTo(provenanceId);
      assertThat(rateLimitConfig.operationType()).isNull();
      assertThat(rateLimitConfig.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(rateLimitConfig.effectiveTo()).isNull();
      assertThat(rateLimitConfig.maxConcurrentRequests()).isNull();
      assertThat(rateLimitConfig.perCredentialQpsLimit()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 effectiveTo 为 null 的永久有效配置")
    void shouldCreatePermanentConfig() {
      // Given: effectiveTo 为 null（表示永久有效）
      Long id = 1001L;
      Long provenanceId = 2001L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = null;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(id, provenanceId, "HARVEST", effectiveFrom, effectiveTo, 10, 5);

      // Then: 验证 effectiveTo 为 null
      assertThat(rateLimitConfig.effectiveTo()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 null 的通用配置")
    void shouldCreateConfigWithNullOperationType() {
      // Given: operationType 为 null（表示适用于所有操作）
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = null;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(id, provenanceId, operationType, effectiveFrom, null, 10, 5);

      // Then: 验证 operationType 为 null
      assertThat(rateLimitConfig.operationType()).isNull();
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
              () -> new RateLimitConfig(id, 2001L, "HARVEST", Instant.now(), null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Rate limit config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为 0")
    void shouldThrowExceptionWhenIdIsZero() {
      // Given: id 为 0
      Long id = 0L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () -> new RateLimitConfig(id, 2001L, "HARVEST", Instant.now(), null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Rate limit config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为负数")
    void shouldThrowExceptionWhenIdIsNegative() {
      // Given: id 为负数
      Long id = -1L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () -> new RateLimitConfig(id, 2001L, "HARVEST", Instant.now(), null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Rate limit config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 id 为 1 的配置")
    void shouldCreateConfigWithIdOne() {
      // Given: id 为 1
      Long id = 1L;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(id, 2001L, "HARVEST", Instant.now(), null, null, null);

      // Then: 验证成功创建
      assertThat(rateLimitConfig.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功创建 id 为 Long.MAX_VALUE 的配置")
    void shouldCreateConfigWithMaxId() {
      // Given: id 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(id, 2001L, "HARVEST", Instant.now(), null, null, null);

      // Then: 验证成功创建
      assertThat(rateLimitConfig.id()).isEqualTo(Long.MAX_VALUE);
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
                  new RateLimitConfig(
                      1001L, provenanceId, "HARVEST", Instant.now(), null, null, null))
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
                  new RateLimitConfig(
                      1001L, provenanceId, "HARVEST", Instant.now(), null, null, null))
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
                  new RateLimitConfig(
                      1001L, provenanceId, "HARVEST", Instant.now(), null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 provenanceId 为 1 的配置")
    void shouldCreateConfigWithProvenanceIdOne() {
      // Given: provenanceId 为 1
      Long provenanceId = 1L;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, provenanceId, "HARVEST", Instant.now(), null, null, null);

      // Then: 验证成功创建
      assertThat(rateLimitConfig.provenanceId()).isEqualTo(1L);
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
              () -> new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Effective from")
          .hasMessageContaining("不能为 null");
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为当前时间的配置")
    void shouldCreateConfigWithCurrentEffectiveFrom() {
      // Given: effectiveFrom 为当前时间
      Instant effectiveFrom = Instant.now();

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null);

      // Then: 验证成功创建
      assertThat(rateLimitConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为过去时间的配置")
    void shouldCreateConfigWithPastEffectiveFrom() {
      // Given: effectiveFrom 为过去时间
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null);

      // Then: 验证成功创建
      assertThat(rateLimitConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为未来时间的配置")
    void shouldCreateConfigWithFutureEffectiveFrom() {
      // Given: effectiveFrom 为未来时间
      Instant effectiveFrom = Instant.parse("2026-01-01T00:00:00Z");

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null);

      // Then: 验证成功创建
      assertThat(rateLimitConfig.effectiveFrom()).isEqualTo(effectiveFrom);
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

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, operationType, Instant.now(), null, null, null);

      // Then: 验证 operationType 已被 trim
      assertThat(rateLimitConfig.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该处理混合空白字符的字符串")
    void shouldHandleStringWithMixedWhitespace() {
      // Given: 包含制表符、换行符等混合空白的字符串
      String operationType = "\t\n  UPDATE  \t\n";

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, operationType, Instant.now(), null, null, null);

      // Then: 验证空白字符都被 trim
      assertThat(rateLimitConfig.operationType()).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("应该保留字符串内部的空白字符")
    void shouldPreserveInternalWhitespace() {
      // Given: 字符串内部包含空白字符
      String operationType = "  BACK FILL  ";

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, operationType, Instant.now(), null, null, null);

      // Then: 验证内部空白字符被保留
      assertThat(rateLimitConfig.operationType()).isEqualTo("BACK FILL");
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

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, operationType, Instant.now(), null, null, null);

      // Then: 验证 operationType 为 null
      assertThat(rateLimitConfig.operationType()).isNull();
    }

    @Test
    @DisplayName("effectiveTo 为 null 时应保持 null（表示永久有效）")
    void effectiveToCanBeNull() {
      // Given: effectiveTo 为 null
      Instant effectiveTo = null;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, "HARVEST", Instant.now(), effectiveTo, null, null);

      // Then: 验证 effectiveTo 为 null
      assertThat(rateLimitConfig.effectiveTo()).isNull();
    }

    @Test
    @DisplayName("maxConcurrentRequests 为 null 时应允许（表示无并发限制）")
    void maxConcurrentRequestsCanBeNull() {
      // Given: maxConcurrentRequests 为 null
      Integer maxConcurrentRequests = null;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, maxConcurrentRequests, null);

      // Then: 验证 maxConcurrentRequests 为 null
      assertThat(rateLimitConfig.maxConcurrentRequests()).isNull();
    }

    @Test
    @DisplayName("perCredentialQpsLimit 为 null 时应允许（表示无凭证级限制）")
    void perCredentialQpsLimitCanBeNull() {
      // Given: perCredentialQpsLimit 为 null
      Integer perCredentialQpsLimit = null;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, perCredentialQpsLimit);

      // Then: 验证 perCredentialQpsLimit 为 null
      assertThat(rateLimitConfig.perCredentialQpsLimit()).isNull();
    }

    @Test
    @DisplayName("应该处理所有可选字段都为 null 的情况")
    void shouldHandleAllOptionalFieldsBeingNull() {
      // Given: 所有可选字段为 null
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, null, Instant.now(), null, null, null);

      // Then: 验证可选字段都为 null
      assertThat(rateLimitConfig.operationType()).isNull();
      assertThat(rateLimitConfig.effectiveTo()).isNull();
      assertThat(rateLimitConfig.maxConcurrentRequests()).isNull();
      assertThat(rateLimitConfig.perCredentialQpsLimit()).isNull();
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
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      Integer maxConcurrentRequests = 10;
      Integer perCredentialQpsLimit = 5;

      RateLimitConfig rateLimitConfig1 =
          new RateLimitConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxConcurrentRequests,
              perCredentialQpsLimit);

      RateLimitConfig rateLimitConfig2 =
          new RateLimitConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              maxConcurrentRequests,
              perCredentialQpsLimit);

      // When & Then: 应该相等
      assertThat(rateLimitConfig1).isEqualTo(rateLimitConfig2);
      assertThat(rateLimitConfig1).hasSameHashCodeAs(rateLimitConfig2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同值对象不相等）")
    void shouldImplementEqualsCorrectlyForDifferentObjects() {
      // Given: 两个不同值的配置
      RateLimitConfig rateLimitConfig1 =
          new RateLimitConfig(1001L, 2001L, "HARVEST", Instant.now(), null, 10, 5);

      RateLimitConfig rateLimitConfig2 =
          new RateLimitConfig(1002L, 2002L, "UPDATE", Instant.now(), null, 20, 10);

      // When & Then: 不应该相等
      assertThat(rateLimitConfig1).isNotEqualTo(rateLimitConfig2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      RateLimitConfig rateLimitConfig1 =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, 10, 5);

      RateLimitConfig rateLimitConfig2 =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, 10, 5);

      // When & Then: hashCode 应该相等
      assertThat(rateLimitConfig1.hashCode()).isEqualTo(rateLimitConfig2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建配置
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.parse("2025-01-01T00:00:00Z"),
              Instant.parse("2025-12-31T23:59:59Z"),
              10,
              5);

      // When: 调用 toString
      String toString = rateLimitConfig.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("RateLimitConfig");
      assertThat(toString).contains("1001");
      assertThat(toString).contains("2001");
      assertThat(toString).contains("HARVEST");
    }

    @Test
    @DisplayName("应该支持 equals 自反性")
    void shouldSupportEqualsReflexivity() {
      // Given: 创建配置
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, "HARVEST", Instant.now(), null, null, null);

      // When & Then: 对象应该等于自身
      assertThat(rateLimitConfig).isEqualTo(rateLimitConfig);
    }

    @Test
    @DisplayName("应该支持 equals 对称性")
    void shouldSupportEqualsSymmetry() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      RateLimitConfig rateLimitConfig1 =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null);

      RateLimitConfig rateLimitConfig2 =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null);

      // When & Then: 对称性（a.equals(b) == b.equals(a)）
      assertThat(rateLimitConfig1.equals(rateLimitConfig2))
          .isEqualTo(rateLimitConfig2.equals(rateLimitConfig1));
      assertThat(rateLimitConfig1).isEqualTo(rateLimitConfig2);
      assertThat(rateLimitConfig2).isEqualTo(rateLimitConfig1);
    }

    @Test
    @DisplayName("应该支持 equals 传递性")
    void shouldSupportEqualsTransitivity() {
      // Given: 三个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      RateLimitConfig rateLimitConfig1 =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null);

      RateLimitConfig rateLimitConfig2 =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null);

      RateLimitConfig rateLimitConfig3 =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null);

      // When & Then: 传递性（a.equals(b) && b.equals(c) => a.equals(c)）
      assertThat(rateLimitConfig1).isEqualTo(rateLimitConfig2);
      assertThat(rateLimitConfig2).isEqualTo(rateLimitConfig3);
      assertThat(rateLimitConfig1).isEqualTo(rateLimitConfig3);
    }

    @Test
    @DisplayName("应该正确处理与 null 的比较")
    void shouldHandleNullComparison() {
      // Given: 创建配置
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, "HARVEST", Instant.now(), null, null, null);

      // When & Then: 与 null 比较应该返回 false
      assertThat(rateLimitConfig).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确处理与不同类型对象的比较")
    void shouldHandleDifferentTypeComparison() {
      // Given: 创建配置
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, "HARVEST", Instant.now(), null, null, null);

      // When & Then: 与不同类型对象比较应该返回 false
      assertThat(rateLimitConfig).isNotEqualTo("Not a RateLimitConfig");
      assertThat(rateLimitConfig).isNotEqualTo(1001L);
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
      Instant originalEffectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      Integer originalMaxConcurrentRequests = 10;
      Integer originalPerCredentialQpsLimit = 5;

      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              originalId,
              originalProvenanceId,
              originalOperationType,
              originalEffectiveFrom,
              originalEffectiveTo,
              originalMaxConcurrentRequests,
              originalPerCredentialQpsLimit);

      // When: 获取字段值
      Long retrievedId = rateLimitConfig.id();
      Long retrievedProvenanceId = rateLimitConfig.provenanceId();
      String retrievedOperationType = rateLimitConfig.operationType();
      Instant retrievedEffectiveFrom = rateLimitConfig.effectiveFrom();
      Instant retrievedEffectiveTo = rateLimitConfig.effectiveTo();
      Integer retrievedMaxConcurrentRequests = rateLimitConfig.maxConcurrentRequests();
      Integer retrievedPerCredentialQpsLimit = rateLimitConfig.perCredentialQpsLimit();

      // Then: 字段值应该保持不变
      assertThat(retrievedId).isEqualTo(originalId);
      assertThat(retrievedProvenanceId).isEqualTo(originalProvenanceId);
      assertThat(retrievedOperationType).isEqualTo(originalOperationType);
      assertThat(retrievedEffectiveFrom).isEqualTo(originalEffectiveFrom);
      assertThat(retrievedEffectiveTo).isEqualTo(originalEffectiveTo);
      assertThat(retrievedMaxConcurrentRequests).isEqualTo(originalMaxConcurrentRequests);
      assertThat(retrievedPerCredentialQpsLimit).isEqualTo(originalPerCredentialQpsLimit);
    }

    @Test
    @DisplayName("字符串字段应该在创建后保持不变")
    void stringFieldsShouldRemainUnchangedAfterCreation() {
      // Given: 创建配置
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, "HARVEST", Instant.now(), null, 10, 5);

      // When: 多次获取字段值
      String operationType1 = rateLimitConfig.operationType();
      String operationType2 = rateLimitConfig.operationType();

      // Then: 字段值应该保持一致
      assertThat(operationType1).isEqualTo(operationType2);
      assertThat(operationType1).isSameAs(operationType2);
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

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, operationType, Instant.now(), null, 10, 5);

      // Then: 验证成功创建
      assertThat(rateLimitConfig.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 UPDATE 的配置")
    void shouldCreateConfigWithOperationTypeUpdate() {
      // Given: operationType 为 UPDATE
      String operationType = "UPDATE";

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, operationType, Instant.now(), null, 10, 5);

      // Then: 验证成功创建
      assertThat(rateLimitConfig.operationType()).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 BACKFILL 的配置")
    void shouldCreateConfigWithOperationTypeBackfill() {
      // Given: operationType 为 BACKFILL
      String operationType = "BACKFILL";

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, operationType, Instant.now(), null, 10, 5);

      // Then: 验证成功创建
      assertThat(rateLimitConfig.operationType()).isEqualTo("BACKFILL");
    }

    @Test
    @DisplayName("应该成功创建无并发限制的配置")
    void shouldCreateConfigWithoutConcurrentLimit() {
      // Given: maxConcurrentRequests 为 null（表示无并发限制）
      Integer maxConcurrentRequests = null;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, maxConcurrentRequests, 5);

      // Then: 验证无并发限制
      assertThat(rateLimitConfig.maxConcurrentRequests()).isNull();
    }

    @Test
    @DisplayName("应该成功创建有并发限制的配置")
    void shouldCreateConfigWithConcurrentLimit() {
      // Given: maxConcurrentRequests 为正数
      Integer maxConcurrentRequests = 20;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, maxConcurrentRequests, 5);

      // Then: 验证并发限制正确赋值
      assertThat(rateLimitConfig.maxConcurrentRequests()).isEqualTo(20);
    }

    @Test
    @DisplayName("应该成功创建无凭证级 QPS 限制的配置")
    void shouldCreateConfigWithoutPerCredentialQpsLimit() {
      // Given: perCredentialQpsLimit 为 null（表示无凭证级限制）
      Integer perCredentialQpsLimit = null;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, 10, perCredentialQpsLimit);

      // Then: 验证无凭证级限制
      assertThat(rateLimitConfig.perCredentialQpsLimit()).isNull();
    }

    @Test
    @DisplayName("应该成功创建有凭证级 QPS 限制的配置")
    void shouldCreateConfigWithPerCredentialQpsLimit() {
      // Given: perCredentialQpsLimit 为正数
      Integer perCredentialQpsLimit = 3;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, 10, perCredentialQpsLimit);

      // Then: 验证凭证级 QPS 限制正确赋值
      assertThat(rateLimitConfig.perCredentialQpsLimit()).isEqualTo(3);
    }

    @Test
    @DisplayName("应该成功创建完整的速率限制配置")
    void shouldCreateCompleteRateLimitConfig() {
      // Given: 完整的速率限制配置
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.parse("2025-01-01T00:00:00Z"),
              Instant.parse("2025-12-31T23:59:59Z"),
              50,
              10);

      // Then: 验证所有字段
      assertThat(rateLimitConfig.id()).isEqualTo(1001L);
      assertThat(rateLimitConfig.provenanceId()).isEqualTo(2001L);
      assertThat(rateLimitConfig.operationType()).isEqualTo("HARVEST");
      assertThat(rateLimitConfig.effectiveFrom()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
      assertThat(rateLimitConfig.effectiveTo()).isEqualTo(Instant.parse("2025-12-31T23:59:59Z"));
      assertThat(rateLimitConfig.maxConcurrentRequests()).isEqualTo(50);
      assertThat(rateLimitConfig.perCredentialQpsLimit()).isEqualTo(10);
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

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, operationType, Instant.now(), null, null, null);

      // Then: 应该成功创建
      assertThat(rateLimitConfig.operationType()).isEqualTo("A");
    }

    @Test
    @DisplayName("应该处理 operationType 为极长字符串的情况")
    void shouldHandleVeryLongOperationType() {
      // Given: operationType 为长字符串
      String operationType = "A".repeat(255);

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, operationType, Instant.now(), null, null, null);

      // Then: 应该成功创建
      assertThat(rateLimitConfig.operationType()).hasSize(255);
    }

    @Test
    @DisplayName("应该处理 trim 后相同的不同输入")
    void shouldHandleDifferentInputsWithSameTrimmedValue() {
      // Given: trim 后相同的不同输入
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      RateLimitConfig rateLimitConfig1 =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, 10, 5);

      RateLimitConfig rateLimitConfig2 =
          new RateLimitConfig(1001L, 2001L, "  HARVEST  ", effectiveFrom, null, 10, 5);

      // When & Then: trim 后应该相等
      assertThat(rateLimitConfig1).isEqualTo(rateLimitConfig2);
    }

    @Test
    @DisplayName("应该处理 maxConcurrentRequests 为 0 的情况")
    void shouldHandleZeroMaxConcurrentRequests() {
      // Given: maxConcurrentRequests 为 0
      Integer maxConcurrentRequests = 0;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, maxConcurrentRequests, null);

      // Then: 应该成功创建
      assertThat(rateLimitConfig.maxConcurrentRequests()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该处理 maxConcurrentRequests 为 Integer.MAX_VALUE 的情况")
    void shouldHandleMaxIntegerConcurrentRequests() {
      // Given: maxConcurrentRequests 为 Integer.MAX_VALUE
      Integer maxConcurrentRequests = Integer.MAX_VALUE;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, maxConcurrentRequests, null);

      // Then: 应该成功创建
      assertThat(rateLimitConfig.maxConcurrentRequests()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理 perCredentialQpsLimit 为 1 的情况")
    void shouldHandleMinimalPerCredentialQpsLimit() {
      // Given: perCredentialQpsLimit 为 1
      Integer perCredentialQpsLimit = 1;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, perCredentialQpsLimit);

      // Then: 应该成功创建
      assertThat(rateLimitConfig.perCredentialQpsLimit()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该处理 perCredentialQpsLimit 为 Integer.MAX_VALUE 的情况")
    void shouldHandleMaxIntegerPerCredentialQpsLimit() {
      // Given: perCredentialQpsLimit 为 Integer.MAX_VALUE
      Integer perCredentialQpsLimit = Integer.MAX_VALUE;

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, perCredentialQpsLimit);

      // Then: 应该成功创建
      assertThat(rateLimitConfig.perCredentialQpsLimit()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理 effectiveFrom 和 effectiveTo 相同的情况")
    void shouldHandleSameEffectiveTimes() {
      // Given: effectiveFrom 和 effectiveTo 相同
      Instant sameTime = Instant.parse("2025-01-01T00:00:00Z");

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, "HARVEST", sameTime, sameTime, 10, 5);

      // Then: 应该成功创建
      assertThat(rateLimitConfig.effectiveFrom()).isEqualTo(sameTime);
      assertThat(rateLimitConfig.effectiveTo()).isEqualTo(sameTime);
    }

    @Test
    @DisplayName("应该处理 effectiveTo 早于 effectiveFrom 的情况")
    void shouldHandleEffectiveToBeforeEffectiveFrom() {
      // Given: effectiveTo 早于 effectiveFrom（虽然业务上不合理，但 record 不阻止）
      Instant effectiveFrom = Instant.parse("2025-12-31T23:59:59Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      // When: 创建 RateLimitConfig
      RateLimitConfig rateLimitConfig =
          new RateLimitConfig(1001L, 2001L, "HARVEST", effectiveFrom, effectiveTo, 10, 5);

      // Then: 应该成功创建（不在 record 层校验业务逻辑）
      assertThat(rateLimitConfig.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(rateLimitConfig.effectiveTo()).isEqualTo(effectiveTo);
    }
  }
}
