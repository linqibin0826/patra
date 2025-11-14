package com.patra.registry.domain.model.vo.provenance;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BatchingConfig 值对象单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>测试 record 的业务约束验证（正整数 ID、非空 effectiveFrom 等）
 *   <li>验证字符串字段自动 trim 处理
 *   <li>测试可选字段的 null 处理
 *   <li>遵循 Given-When-Then 结构
 *   <li>使用 AssertJ 流畅断言
 * </ul>
 *
 * <p>覆盖范围：
 *
 * <ul>
 *   <li>✅ record 构造函数验证测试
 *   <li>✅ 正整数 ID 验证（id, provenanceId）
 *   <li>✅ 必需字段非 null 验证（effectiveFrom）
 *   <li>✅ 字符串 trim 处理测试（operationType, idsParamName, idsJoinDelimiter）
 *   <li>✅ 可选字段处理（effectiveTo, detailFetchBatchSize 等允许为 null）
 *   <li>✅ record 的 equals/hashCode/toString 测试
 *   <li>✅ 不变性保证
 *   <li>✅ 业务场景测试（批处理配置、ID 参数定制、硬上限保护等）
 *   <li>✅ 边界条件处理
 * </ul>
 *
 * @author Patra Team
 * @since 2.0
 */
@DisplayName("BatchingConfig 单元测试")
class BatchingConfigTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的批处理配置")
    void shouldCreateBatchingConfigWithAllFields() {
      // Given: 所有字段都有效
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      Integer detailFetchBatchSize = 100;
      String idsParamName = "ids";
      String idsJoinDelimiter = ",";
      Integer maxIdsPerRequest = 500;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              detailFetchBatchSize,
              idsParamName,
              idsJoinDelimiter,
              maxIdsPerRequest);

      // Then: 验证所有字段正确赋值
      assertThat(batchingConfig).isNotNull();
      assertThat(batchingConfig.id()).isEqualTo(id);
      assertThat(batchingConfig.provenanceId()).isEqualTo(provenanceId);
      assertThat(batchingConfig.operationType()).isEqualTo(operationType);
      assertThat(batchingConfig.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(batchingConfig.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(batchingConfig.detailFetchBatchSize()).isEqualTo(detailFetchBatchSize);
      assertThat(batchingConfig.idsParamName()).isEqualTo(idsParamName);
      assertThat(batchingConfig.idsJoinDelimiter()).isEqualTo(idsJoinDelimiter);
      assertThat(batchingConfig.maxIdsPerRequest()).isEqualTo(maxIdsPerRequest);
    }

    @Test
    @DisplayName("应该成功创建仅包含必需字段的最小配置")
    void shouldCreateMinimalBatchingConfig() {
      // Given: 只有必需字段
      Long id = 1001L;
      Long provenanceId = 2001L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              id,
              provenanceId,
              null, // operationType
              effectiveFrom,
              null, // effectiveTo
              null, // detailFetchBatchSize
              null, // idsParamName
              null, // idsJoinDelimiter
              null // maxIdsPerRequest
              );

      // Then: 验证必需字段正确赋值，可选字段为 null
      assertThat(batchingConfig).isNotNull();
      assertThat(batchingConfig.id()).isEqualTo(id);
      assertThat(batchingConfig.provenanceId()).isEqualTo(provenanceId);
      assertThat(batchingConfig.operationType()).isNull();
      assertThat(batchingConfig.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(batchingConfig.effectiveTo()).isNull();
      assertThat(batchingConfig.detailFetchBatchSize()).isNull();
      assertThat(batchingConfig.idsParamName()).isNull();
      assertThat(batchingConfig.idsJoinDelimiter()).isNull();
      assertThat(batchingConfig.maxIdsPerRequest()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 effectiveTo 为 null 的永久有效配置")
    void shouldCreatePermanentConfig() {
      // Given: effectiveTo 为 null（表示永久有效）
      Long id = 1001L;
      Long provenanceId = 2001L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = null;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              id, provenanceId, "HARVEST", effectiveFrom, effectiveTo, 100, "ids", ",", 500);

      // Then: 验证 effectiveTo 为 null
      assertThat(batchingConfig.effectiveTo()).isNull();
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
                  new BatchingConfig(
                      id, 2001L, "HARVEST", Instant.now(), null, null, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Batching config id")
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
                  new BatchingConfig(
                      id, 2001L, "HARVEST", Instant.now(), null, null, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Batching config id")
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
                  new BatchingConfig(
                      id, 2001L, "HARVEST", Instant.now(), null, null, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Batching config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 id 为 1 的配置")
    void shouldCreateConfigWithIdOne() {
      // Given: id 为 1
      Long id = 1L;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(id, 2001L, "HARVEST", Instant.now(), null, null, null, null, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功创建 id 为 Long.MAX_VALUE 的配置")
    void shouldCreateConfigWithMaxId() {
      // Given: id 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(id, 2001L, "HARVEST", Instant.now(), null, null, null, null, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.id()).isEqualTo(Long.MAX_VALUE);
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
                  new BatchingConfig(
                      1001L, provenanceId, "HARVEST", Instant.now(), null, null, null, null, null))
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
                  new BatchingConfig(
                      1001L, provenanceId, "HARVEST", Instant.now(), null, null, null, null, null))
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
                  new BatchingConfig(
                      1001L, provenanceId, "HARVEST", Instant.now(), null, null, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 provenanceId 为 1 的配置")
    void shouldCreateConfigWithProvenanceIdOne() {
      // Given: provenanceId 为 1
      Long provenanceId = 1L;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, provenanceId, "HARVEST", Instant.now(), null, null, null, null, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.provenanceId()).isEqualTo(1L);
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
                  new BatchingConfig(
                      1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Effective from")
          .hasMessageContaining("不能为 null");
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为当前时间的配置")
    void shouldCreateConfigWithCurrentEffectiveFrom() {
      // Given: effectiveFrom 为当前时间
      Instant effectiveFrom = Instant.now();

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为过去时间的配置")
    void shouldCreateConfigWithPastEffectiveFrom() {
      // Given: effectiveFrom 为过去时间
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为未来时间的配置")
    void shouldCreateConfigWithFutureEffectiveFrom() {
      // Given: effectiveFrom 为未来时间
      Instant effectiveFrom = Instant.parse("2026-01-01T00:00:00Z");

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.effectiveFrom()).isEqualTo(effectiveFrom);
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

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null);

      // Then: 验证 operationType 已被 trim
      assertThat(batchingConfig.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该自动 trim idsParamName 字段")
    void shouldTrimIdsParamName() {
      // Given: idsParamName 包含首尾空白
      String idsParamName = "  ids  ";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, idsParamName, null, null);

      // Then: 验证 idsParamName 已被 trim
      assertThat(batchingConfig.idsParamName()).isEqualTo("ids");
    }

    @Test
    @DisplayName("应该自动 trim idsJoinDelimiter 字段")
    void shouldTrimIdsJoinDelimiter() {
      // Given: idsJoinDelimiter 包含首尾空白
      String idsJoinDelimiter = "  ,  ";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, idsJoinDelimiter, null);

      // Then: 验证 idsJoinDelimiter 已被 trim
      assertThat(batchingConfig.idsJoinDelimiter()).isEqualTo(",");
    }

    @Test
    @DisplayName("应该 trim 所有字符串字段")
    void shouldTrimAllStringFields() {
      // Given: 所有字符串字段包含首尾空白
      String operationType = "  UPDATE  ";
      String idsParamName = "  article_ids  ";
      String idsJoinDelimiter = "  +  ";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              null,
              idsParamName,
              idsJoinDelimiter,
              null);

      // Then: 验证所有字段都已被 trim
      assertThat(batchingConfig.operationType()).isEqualTo("UPDATE");
      assertThat(batchingConfig.idsParamName()).isEqualTo("article_ids");
      assertThat(batchingConfig.idsJoinDelimiter()).isEqualTo("+");
    }

    @Test
    @DisplayName("应该处理混合空白字符的字符串")
    void shouldHandleStringWithMixedWhitespace() {
      // Given: 包含制表符、换行符等混合空白的字符串
      String operationType = "\t\n  BACKFILL  \t\n";
      String idsParamName = " \t ids \n ";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, idsParamName, null, null);

      // Then: 验证空白字符都被 trim
      assertThat(batchingConfig.operationType()).isEqualTo("BACKFILL");
      assertThat(batchingConfig.idsParamName()).isEqualTo("ids");
    }

    @Test
    @DisplayName("应该保留字符串内部的空白字符")
    void shouldPreserveInternalWhitespace() {
      // Given: 字符串内部包含空白字符
      String idsParamName = "  article ids  ";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, idsParamName, null, null);

      // Then: 验证内部空白字符被保留
      assertThat(batchingConfig.idsParamName()).isEqualTo("article ids");
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

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null);

      // Then: 验证 operationType 为 null
      assertThat(batchingConfig.operationType()).isNull();
    }

    @Test
    @DisplayName("effectiveTo 为 null 时应保持 null（表示永久有效）")
    void effectiveToCanBeNull() {
      // Given: effectiveTo 为 null
      Instant effectiveTo = null;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), effectiveTo, null, null, null, null);

      // Then: 验证 effectiveTo 为 null
      assertThat(batchingConfig.effectiveTo()).isNull();
    }

    @Test
    @DisplayName("detailFetchBatchSize 为 null 时应允许（表示使用默认值）")
    void detailFetchBatchSizeCanBeNull() {
      // Given: detailFetchBatchSize 为 null
      Integer detailFetchBatchSize = null;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, detailFetchBatchSize, null, null, null);

      // Then: 验证 detailFetchBatchSize 为 null
      assertThat(batchingConfig.detailFetchBatchSize()).isNull();
    }

    @Test
    @DisplayName("idsParamName 为 null 时应允许")
    void idsParamNameCanBeNull() {
      // Given: idsParamName 为 null
      String idsParamName = null;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, idsParamName, null, null);

      // Then: 验证 idsParamName 为 null
      assertThat(batchingConfig.idsParamName()).isNull();
    }

    @Test
    @DisplayName("idsJoinDelimiter 为 null 时应允许")
    void idsJoinDelimiterCanBeNull() {
      // Given: idsJoinDelimiter 为 null
      String idsJoinDelimiter = null;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, idsJoinDelimiter, null);

      // Then: 验证 idsJoinDelimiter 为 null
      assertThat(batchingConfig.idsJoinDelimiter()).isNull();
    }

    @Test
    @DisplayName("maxIdsPerRequest 为 null 时应允许")
    void maxIdsPerRequestCanBeNull() {
      // Given: maxIdsPerRequest 为 null
      Integer maxIdsPerRequest = null;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, null, maxIdsPerRequest);

      // Then: 验证 maxIdsPerRequest 为 null
      assertThat(batchingConfig.maxIdsPerRequest()).isNull();
    }

    @Test
    @DisplayName("应该处理所有可选字段都为 null 的情况")
    void shouldHandleAllOptionalFieldsBeingNull() {
      // Given: 所有可选字段为 null
      BatchingConfig batchingConfig =
          new BatchingConfig(1001L, 2001L, null, Instant.now(), null, null, null, null, null);

      // Then: 验证可选字段都为 null
      assertThat(batchingConfig.operationType()).isNull();
      assertThat(batchingConfig.effectiveTo()).isNull();
      assertThat(batchingConfig.detailFetchBatchSize()).isNull();
      assertThat(batchingConfig.idsParamName()).isNull();
      assertThat(batchingConfig.idsJoinDelimiter()).isNull();
      assertThat(batchingConfig.maxIdsPerRequest()).isNull();
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

      BatchingConfig batchingConfig1 =
          new BatchingConfig(
              id, provenanceId, operationType, effectiveFrom, null, 100, "ids", ",", 500);

      BatchingConfig batchingConfig2 =
          new BatchingConfig(
              id, provenanceId, operationType, effectiveFrom, null, 100, "ids", ",", 500);

      // When & Then: 应该相等
      assertThat(batchingConfig1).isEqualTo(batchingConfig2);
      assertThat(batchingConfig1).hasSameHashCodeAs(batchingConfig2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同值对象不相等）")
    void shouldImplementEqualsCorrectlyForDifferentObjects() {
      // Given: 两个不同值的配置
      BatchingConfig batchingConfig1 =
          new BatchingConfig(1001L, 2001L, "HARVEST", Instant.now(), null, 100, "ids", ",", 500);

      BatchingConfig batchingConfig2 =
          new BatchingConfig(1002L, 2002L, "UPDATE", Instant.now(), null, 200, "pmids", "+", 1000);

      // When & Then: 不应该相等
      assertThat(batchingConfig1).isNotEqualTo(batchingConfig2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      BatchingConfig batchingConfig1 =
          new BatchingConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, 100, "ids", ",", 500);

      BatchingConfig batchingConfig2 =
          new BatchingConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, 100, "ids", ",", 500);

      // When & Then: hashCode 应该相等
      assertThat(batchingConfig1.hashCode()).isEqualTo(batchingConfig2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建配置
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.parse("2025-01-01T00:00:00Z"),
              null,
              100,
              "ids",
              ",",
              500);

      // When: 调用 toString
      String toString = batchingConfig.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("BatchingConfig");
      assertThat(toString).contains("1001");
      assertThat(toString).contains("2001");
      assertThat(toString).contains("HARVEST");
    }

    @Test
    @DisplayName("应该支持 equals 自反性")
    void shouldSupportEqualsReflexivity() {
      // Given: 创建配置
      BatchingConfig batchingConfig =
          new BatchingConfig(1001L, 2001L, "HARVEST", Instant.now(), null, null, null, null, null);

      // When & Then: 对象应该等于自身
      assertThat(batchingConfig).isEqualTo(batchingConfig);
    }

    @Test
    @DisplayName("应该支持 equals 对称性")
    void shouldSupportEqualsSymmetry() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      BatchingConfig batchingConfig1 =
          new BatchingConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null);

      BatchingConfig batchingConfig2 =
          new BatchingConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null);

      // When & Then: 对称性（a.equals(b) == b.equals(a)）
      assertThat(batchingConfig1.equals(batchingConfig2))
          .isEqualTo(batchingConfig2.equals(batchingConfig1));
      assertThat(batchingConfig1).isEqualTo(batchingConfig2);
      assertThat(batchingConfig2).isEqualTo(batchingConfig1);
    }

    @Test
    @DisplayName("应该支持 equals 传递性")
    void shouldSupportEqualsTransitivity() {
      // Given: 三个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      BatchingConfig batchingConfig1 =
          new BatchingConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null);

      BatchingConfig batchingConfig2 =
          new BatchingConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null);

      BatchingConfig batchingConfig3 =
          new BatchingConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null);

      // When & Then: 传递性（a.equals(b) && b.equals(c) => a.equals(c)）
      assertThat(batchingConfig1).isEqualTo(batchingConfig2);
      assertThat(batchingConfig2).isEqualTo(batchingConfig3);
      assertThat(batchingConfig1).isEqualTo(batchingConfig3);
    }

    @Test
    @DisplayName("应该正确处理与 null 的比较")
    void shouldHandleNullComparison() {
      // Given: 创建配置
      BatchingConfig batchingConfig =
          new BatchingConfig(1001L, 2001L, "HARVEST", Instant.now(), null, null, null, null, null);

      // When & Then: 与 null 比较应该返回 false
      assertThat(batchingConfig).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确处理与不同类型对象的比较")
    void shouldHandleDifferentTypeComparison() {
      // Given: 创建配置
      BatchingConfig batchingConfig =
          new BatchingConfig(1001L, 2001L, "HARVEST", Instant.now(), null, null, null, null, null);

      // When & Then: 与不同类型对象比较应该返回 false
      assertThat(batchingConfig).isNotEqualTo("Not a BatchingConfig");
      assertThat(batchingConfig).isNotEqualTo(1001L);
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
      Integer originalDetailFetchBatchSize = 100;

      BatchingConfig batchingConfig =
          new BatchingConfig(
              originalId,
              originalProvenanceId,
              originalOperationType,
              originalEffectiveFrom,
              null,
              originalDetailFetchBatchSize,
              "ids",
              ",",
              500);

      // When: 获取字段值
      Long retrievedId = batchingConfig.id();
      Long retrievedProvenanceId = batchingConfig.provenanceId();
      String retrievedOperationType = batchingConfig.operationType();
      Instant retrievedEffectiveFrom = batchingConfig.effectiveFrom();
      Integer retrievedDetailFetchBatchSize = batchingConfig.detailFetchBatchSize();

      // Then: 字段值应该保持不变
      assertThat(retrievedId).isEqualTo(originalId);
      assertThat(retrievedProvenanceId).isEqualTo(originalProvenanceId);
      assertThat(retrievedOperationType).isEqualTo(originalOperationType);
      assertThat(retrievedEffectiveFrom).isEqualTo(originalEffectiveFrom);
      assertThat(retrievedDetailFetchBatchSize).isEqualTo(originalDetailFetchBatchSize);
    }

    @Test
    @DisplayName("字符串字段应该在创建后保持不变")
    void stringFieldsShouldRemainUnchangedAfterCreation() {
      // Given: 创建配置
      BatchingConfig batchingConfig =
          new BatchingConfig(1001L, 2001L, "HARVEST", Instant.now(), null, 100, "ids", ",", 500);

      // When: 多次获取字段值
      String operationType1 = batchingConfig.operationType();
      String operationType2 = batchingConfig.operationType();
      String idsParamName1 = batchingConfig.idsParamName();
      String idsParamName2 = batchingConfig.idsParamName();

      // Then: 字段值应该保持一致
      assertThat(operationType1).isEqualTo(operationType2);
      assertThat(idsParamName1).isEqualTo(idsParamName2);
      assertThat(operationType1).isSameAs(operationType2);
      assertThat(idsParamName1).isSameAs(idsParamName2);
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

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 UPDATE 的配置")
    void shouldCreateConfigWithOperationTypeUpdate() {
      // Given: operationType 为 UPDATE
      String operationType = "UPDATE";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.operationType()).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 BACKFILL 的配置")
    void shouldCreateConfigWithOperationTypeBackfill() {
      // Given: operationType 为 BACKFILL
      String operationType = "BACKFILL";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.operationType()).isEqualTo("BACKFILL");
    }

    @Test
    @DisplayName("应该成功创建包含批处理大小的配置")
    void shouldCreateConfigWithDetailFetchBatchSize() {
      // Given: detailFetchBatchSize 为 100
      Integer detailFetchBatchSize = 100;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, detailFetchBatchSize, null, null, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.detailFetchBatchSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("应该成功创建包含 ID 参数名称的配置")
    void shouldCreateConfigWithIdsParamName() {
      // Given: idsParamName 为 "ids"
      String idsParamName = "ids";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, idsParamName, null, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.idsParamName()).isEqualTo("ids");
    }

    @Test
    @DisplayName("应该成功创建包含 ID 连接分隔符的配置（逗号）")
    void shouldCreateConfigWithIdsJoinDelimiterComma() {
      // Given: idsJoinDelimiter 为逗号
      String idsJoinDelimiter = ",";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, idsJoinDelimiter, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.idsJoinDelimiter()).isEqualTo(",");
    }

    @Test
    @DisplayName("应该成功创建包含 ID 连接分隔符的配置（加号）")
    void shouldCreateConfigWithIdsJoinDelimiterPlus() {
      // Given: idsJoinDelimiter 为加号
      String idsJoinDelimiter = "+";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, idsJoinDelimiter, null);

      // Then: 验证成功创建
      assertThat(batchingConfig.idsJoinDelimiter()).isEqualTo("+");
    }

    @Test
    @DisplayName("应该成功创建包含最大 ID 数量限制的配置")
    void shouldCreateConfigWithMaxIdsPerRequest() {
      // Given: maxIdsPerRequest 为 500
      Integer maxIdsPerRequest = 500;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, null, maxIdsPerRequest);

      // Then: 验证成功创建
      assertThat(batchingConfig.maxIdsPerRequest()).isEqualTo(500);
    }

    @Test
    @DisplayName("应该成功创建完整的批处理配置（PubMed 风格）")
    void shouldCreateCompletePubMedStyleConfig() {
      // Given: PubMed 风格的批处理配置
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.parse("2025-01-01T00:00:00Z"),
              Instant.parse("2025-12-31T23:59:59Z"),
              100,
              "id",
              ",",
              500);

      // Then: 验证所有字段
      assertThat(batchingConfig.detailFetchBatchSize()).isEqualTo(100);
      assertThat(batchingConfig.idsParamName()).isEqualTo("id");
      assertThat(batchingConfig.idsJoinDelimiter()).isEqualTo(",");
      assertThat(batchingConfig.maxIdsPerRequest()).isEqualTo(500);
    }

    @Test
    @DisplayName("应该成功创建完整的批处理配置（EPMC 风格）")
    void shouldCreateCompleteEpmcStyleConfig() {
      // Given: EPMC 风格的批处理配置
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1002L,
              2002L,
              "UPDATE",
              Instant.parse("2025-01-01T00:00:00Z"),
              null,
              50,
              "pmids",
              "+",
              200);

      // Then: 验证所有字段
      assertThat(batchingConfig.detailFetchBatchSize()).isEqualTo(50);
      assertThat(batchingConfig.idsParamName()).isEqualTo("pmids");
      assertThat(batchingConfig.idsJoinDelimiter()).isEqualTo("+");
      assertThat(batchingConfig.maxIdsPerRequest()).isEqualTo(200);
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

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null);

      // Then: 应该成功创建
      assertThat(batchingConfig.operationType()).isEqualTo("A");
    }

    @Test
    @DisplayName("应该处理 trim 后相同的不同输入")
    void shouldHandleDifferentInputsWithSameTrimmedValue() {
      // Given: trim 后相同的不同输入
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      BatchingConfig batchingConfig1 =
          new BatchingConfig(1001L, 2001L, "HARVEST", effectiveFrom, null, 100, "ids", ",", 500);

      BatchingConfig batchingConfig2 =
          new BatchingConfig(
              1001L, 2001L, "  HARVEST  ", effectiveFrom, null, 100, "  ids  ", "  ,  ", 500);

      // When & Then: trim 后应该相等
      assertThat(batchingConfig1).isEqualTo(batchingConfig2);
    }

    @Test
    @DisplayName("应该处理 detailFetchBatchSize 为 1 的情况（最小批次）")
    void shouldHandleMinimalBatchSize() {
      // Given: detailFetchBatchSize 为 1
      Integer detailFetchBatchSize = 1;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, detailFetchBatchSize, null, null, null);

      // Then: 应该成功创建
      assertThat(batchingConfig.detailFetchBatchSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该处理 maxIdsPerRequest 为 1 的情况（严格限制）")
    void shouldHandleMinimalMaxIds() {
      // Given: maxIdsPerRequest 为 1
      Integer maxIdsPerRequest = 1;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, null, maxIdsPerRequest);

      // Then: 应该成功创建
      assertThat(batchingConfig.maxIdsPerRequest()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该处理 maxIdsPerRequest 为 Integer.MAX_VALUE 的情况")
    void shouldHandleMaxIntegerIds() {
      // Given: maxIdsPerRequest 为 Integer.MAX_VALUE
      Integer maxIdsPerRequest = Integer.MAX_VALUE;

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, null, maxIdsPerRequest);

      // Then: 应该成功创建
      assertThat(batchingConfig.maxIdsPerRequest()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理 idsParamName 为单字符的情况")
    void shouldHandleMinimalIdsParamName() {
      // Given: idsParamName 为单字符
      String idsParamName = "i";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, idsParamName, null, null);

      // Then: 应该成功创建
      assertThat(batchingConfig.idsParamName()).isEqualTo("i");
    }

    @Test
    @DisplayName("应该处理 idsJoinDelimiter 为单字符的情况")
    void shouldHandleMinimalIdsJoinDelimiter() {
      // Given: idsJoinDelimiter 为单字符
      String idsJoinDelimiter = "|";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, idsJoinDelimiter, null);

      // Then: 应该成功创建
      assertThat(batchingConfig.idsJoinDelimiter()).isEqualTo("|");
    }

    @Test
    @DisplayName("应该处理 idsJoinDelimiter 为多字符的情况")
    void shouldHandleMultiCharIdsJoinDelimiter() {
      // Given: idsJoinDelimiter 为多字符
      String idsJoinDelimiter = "::";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, idsJoinDelimiter, null);

      // Then: 应该成功创建
      assertThat(batchingConfig.idsJoinDelimiter()).isEqualTo("::");
    }

    @Test
    @DisplayName("应该处理 idsParamName 包含下划线的情况")
    void shouldHandleIdsParamNameWithUnderscore() {
      // Given: idsParamName 包含下划线
      String idsParamName = "article_ids";

      // When: 创建 BatchingConfig
      BatchingConfig batchingConfig =
          new BatchingConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, idsParamName, null, null);

      // Then: 应该成功创建
      assertThat(batchingConfig.idsParamName()).isEqualTo("article_ids");
    }
  }
}
