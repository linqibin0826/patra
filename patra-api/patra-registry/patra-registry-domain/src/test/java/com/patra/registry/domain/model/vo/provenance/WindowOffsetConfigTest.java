package com.patra.registry.domain.model.vo.provenance;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * WindowOffsetConfig 值对象单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>测试 record 的业务约束验证（正整数 ID、非空白字符串、必需字段等）
 *   <li>验证字符串字段自动 trim 处理
 *   <li>测试可选字段的 null 处理
 *   <li>验证 DATE/COMPOSITE 偏移量类型的特殊业务规则
 *   <li>遵循 Given-When-Then 结构
 *   <li>使用 AssertJ 流畅断言
 * </ul>
 *
 * <p>覆盖范围：
 *
 * <ul>
 *   <li>✅ record 构造函数验证测试
 *   <li>✅ 正整数 ID 验证（id, provenanceId）
 *   <li>✅ 非空白字符串验证（windowModeCode, windowSizeUnitCode, offsetTypeCode）
 *   <li>✅ 必需字段非 null 验证（effectiveFrom）
 *   <li>✅ DATE/COMPOSITE 偏移量类型字段键验证
 *   <li>✅ 字符串 trim 处理测试
 *   <li>✅ 可选字段处理
 *   <li>✅ record 的 equals/hashCode/toString 测试
 *   <li>✅ 不变性保证
 *   <li>✅ 业务场景测试（不同窗口模式、偏移量类型等）
 *   <li>✅ 边界条件处理
 * </ul>
 *
 * @author Patra Team
 * @since 2.0
 */
@DisplayName("WindowOffsetConfig 单元测试")
class WindowOffsetConfigTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的窗口偏移配置")
    void shouldCreateWindowOffsetConfigWithAllFields() {
      // Given: 所有字段都有效
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      String windowModeCode = "SLIDING";
      Integer windowSizeValue = 3600;
      String windowSizeUnitCode = "SECOND";
      String calendarAlignTo = "HOUR";
      Integer lookbackValue = 300;
      String lookbackUnitCode = "SECOND";
      Integer overlapValue = 60;
      String overlapUnitCode = "SECOND";
      Integer watermarkLagSeconds = 120;
      String offsetTypeCode = "DATE";
      String offsetFieldKey = "created_at";
      String offsetDateFormat = "ISO_INSTANT";
      String windowDateFieldKey = "updated_at";
      Integer maxIdsPerWindow = 1000;
      Integer maxWindowSpanSeconds = 7200;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              windowModeCode,
              windowSizeValue,
              windowSizeUnitCode,
              calendarAlignTo,
              lookbackValue,
              lookbackUnitCode,
              overlapValue,
              overlapUnitCode,
              watermarkLagSeconds,
              offsetTypeCode,
              offsetFieldKey,
              offsetDateFormat,
              windowDateFieldKey,
              maxIdsPerWindow,
              maxWindowSpanSeconds);

      // Then: 验证所有字段正确赋值
      assertThat(config).isNotNull();
      assertThat(config.id()).isEqualTo(id);
      assertThat(config.provenanceId()).isEqualTo(provenanceId);
      assertThat(config.operationType()).isEqualTo(operationType);
      assertThat(config.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(config.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(config.windowModeCode()).isEqualTo(windowModeCode);
      assertThat(config.windowSizeValue()).isEqualTo(windowSizeValue);
      assertThat(config.windowSizeUnitCode()).isEqualTo(windowSizeUnitCode);
      assertThat(config.calendarAlignTo()).isEqualTo(calendarAlignTo);
      assertThat(config.lookbackValue()).isEqualTo(lookbackValue);
      assertThat(config.lookbackUnitCode()).isEqualTo(lookbackUnitCode);
      assertThat(config.overlapValue()).isEqualTo(overlapValue);
      assertThat(config.overlapUnitCode()).isEqualTo(overlapUnitCode);
      assertThat(config.watermarkLagSeconds()).isEqualTo(watermarkLagSeconds);
      assertThat(config.offsetTypeCode()).isEqualTo(offsetTypeCode);
      assertThat(config.offsetFieldKey()).isEqualTo(offsetFieldKey);
      assertThat(config.offsetDateFormat()).isEqualTo(offsetDateFormat);
      assertThat(config.windowDateFieldKey()).isEqualTo(windowDateFieldKey);
      assertThat(config.maxIdsPerWindow()).isEqualTo(maxIdsPerWindow);
      assertThat(config.maxWindowSpanSeconds()).isEqualTo(maxWindowSpanSeconds);
    }

    @Test
    @DisplayName("应该成功创建仅包含必需字段的最小配置")
    void shouldCreateMinimalWindowOffsetConfig() {
      // Given: 只有必需字段
      Long id = 1001L;
      Long provenanceId = 2001L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      String windowModeCode = "SLIDING";
      String windowSizeUnitCode = "HOUR";
      String offsetTypeCode = "ID";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              id,
              provenanceId,
              null, // operationType
              effectiveFrom,
              null, // effectiveTo
              windowModeCode,
              null, // windowSizeValue
              windowSizeUnitCode,
              null, // calendarAlignTo
              null, // lookbackValue
              null, // lookbackUnitCode
              null, // overlapValue
              null, // overlapUnitCode
              null, // watermarkLagSeconds
              offsetTypeCode,
              null, // offsetFieldKey
              null, // offsetDateFormat
              null, // windowDateFieldKey
              null, // maxIdsPerWindow
              null); // maxWindowSpanSeconds

      // Then: 验证必需字段正确赋值，可选字段为 null
      assertThat(config).isNotNull();
      assertThat(config.id()).isEqualTo(id);
      assertThat(config.provenanceId()).isEqualTo(provenanceId);
      assertThat(config.operationType()).isNull();
      assertThat(config.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(config.effectiveTo()).isNull();
      assertThat(config.windowModeCode()).isEqualTo(windowModeCode);
      assertThat(config.windowSizeValue()).isNull();
      assertThat(config.windowSizeUnitCode()).isEqualTo(windowSizeUnitCode);
      assertThat(config.calendarAlignTo()).isNull();
      assertThat(config.lookbackValue()).isNull();
      assertThat(config.lookbackUnitCode()).isNull();
      assertThat(config.overlapValue()).isNull();
      assertThat(config.overlapUnitCode()).isNull();
      assertThat(config.watermarkLagSeconds()).isNull();
      assertThat(config.offsetTypeCode()).isEqualTo(offsetTypeCode);
      assertThat(config.offsetFieldKey()).isNull();
      assertThat(config.offsetDateFormat()).isNull();
      assertThat(config.windowDateFieldKey()).isNull();
      assertThat(config.maxIdsPerWindow()).isNull();
      assertThat(config.maxWindowSpanSeconds()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 effectiveTo 为 null 的永久有效配置")
    void shouldCreatePermanentConfig() {
      // Given: effectiveTo 为 null（表示永久有效）
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = null;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              effectiveTo,
              "SLIDING",
              3600,
              "SECOND",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证 effectiveTo 为 null
      assertThat(config.effectiveTo()).isNull();
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
                  new WindowOffsetConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Window offset config id")
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
                  new WindowOffsetConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Window offset config id")
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
                  new WindowOffsetConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Window offset config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 id 为 1 的配置")
    void shouldCreateConfigWithIdOne() {
      // Given: id 为 1
      Long id = 1L;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              id,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功创建 id 为 Long.MAX_VALUE 的配置")
    void shouldCreateConfigWithMaxId() {
      // Given: id 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              id,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.id()).isEqualTo(Long.MAX_VALUE);
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
                  new WindowOffsetConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
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
                  new WindowOffsetConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
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
                  new WindowOffsetConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
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

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              provenanceId,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.provenanceId()).isEqualTo(1L);
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
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      effectiveFrom,
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
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

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为过去时间的配置")
    void shouldCreateConfigWithPastEffectiveFrom() {
      // Given: effectiveFrom 为过去时间
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为未来时间的配置")
    void shouldCreateConfigWithFutureEffectiveFrom() {
      // Given: effectiveFrom 为未来时间
      Instant effectiveFrom = Instant.parse("2026-01-01T00:00:00Z");

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.effectiveFrom()).isEqualTo(effectiveFrom);
    }
  }

  // ========== WindowModeCode 验证测试 ==========

  @Nested
  @DisplayName("WindowModeCode 非空白验证")
  class WindowModeCodeValidationTests {

    @Test
    @DisplayName("应该抛出异常当 windowModeCode 为 null")
    void shouldThrowExceptionWhenWindowModeCodeIsNull() {
      // Given: windowModeCode 为 null
      String windowModeCode = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      windowModeCode,
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Window mode code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 windowModeCode 为空字符串")
    void shouldThrowExceptionWhenWindowModeCodeIsEmpty() {
      // Given: windowModeCode 为空字符串
      String windowModeCode = "";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      windowModeCode,
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Window mode code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 windowModeCode 仅包含空白字符")
    void shouldThrowExceptionWhenWindowModeCodeIsBlank() {
      // Given: windowModeCode 仅包含空白字符
      String windowModeCode = "   ";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      windowModeCode,
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Window mode code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim windowModeCode 字段")
    void shouldTrimWindowModeCode() {
      // Given: windowModeCode 包含首尾空白
      String windowModeCode = "  SLIDING  ";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              windowModeCode,
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证 windowModeCode 已被 trim
      assertThat(config.windowModeCode()).isEqualTo("SLIDING");
    }
  }

  // ========== WindowSizeUnitCode 验证测试 ==========

  @Nested
  @DisplayName("WindowSizeUnitCode 非空白验证")
  class WindowSizeUnitCodeValidationTests {

    @Test
    @DisplayName("应该抛出异常当 windowSizeUnitCode 为 null")
    void shouldThrowExceptionWhenWindowSizeUnitCodeIsNull() {
      // Given: windowSizeUnitCode 为 null
      String windowSizeUnitCode = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      windowSizeUnitCode,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Window size unit code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 windowSizeUnitCode 为空字符串")
    void shouldThrowExceptionWhenWindowSizeUnitCodeIsEmpty() {
      // Given: windowSizeUnitCode 为空字符串
      String windowSizeUnitCode = "";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      windowSizeUnitCode,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Window size unit code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 windowSizeUnitCode 仅包含空白字符")
    void shouldThrowExceptionWhenWindowSizeUnitCodeIsBlank() {
      // Given: windowSizeUnitCode 仅包含空白字符
      String windowSizeUnitCode = "   ";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      windowSizeUnitCode,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "ID",
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Window size unit code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim windowSizeUnitCode 字段")
    void shouldTrimWindowSizeUnitCode() {
      // Given: windowSizeUnitCode 包含首尾空白
      String windowSizeUnitCode = "  HOUR  ";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              windowSizeUnitCode,
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证 windowSizeUnitCode 已被 trim
      assertThat(config.windowSizeUnitCode()).isEqualTo("HOUR");
    }
  }

  // ========== OffsetTypeCode 验证测试 ==========

  @Nested
  @DisplayName("OffsetTypeCode 非空白验证")
  class OffsetTypeCodeValidationTests {

    @Test
    @DisplayName("应该抛出异常当 offsetTypeCode 为 null")
    void shouldThrowExceptionWhenOffsetTypeCodeIsNull() {
      // Given: offsetTypeCode 为 null
      String offsetTypeCode = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      offsetTypeCode,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Offset type code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 offsetTypeCode 为空字符串")
    void shouldThrowExceptionWhenOffsetTypeCodeIsEmpty() {
      // Given: offsetTypeCode 为空字符串
      String offsetTypeCode = "";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      offsetTypeCode,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Offset type code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 offsetTypeCode 仅包含空白字符")
    void shouldThrowExceptionWhenOffsetTypeCodeIsBlank() {
      // Given: offsetTypeCode 仅包含空白字符
      String offsetTypeCode = "   ";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      offsetTypeCode,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Offset type code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim offsetTypeCode 字段")
    void shouldTrimOffsetTypeCode() {
      // Given: offsetTypeCode 包含首尾空白
      String offsetTypeCode = "  DATE  ";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              offsetTypeCode,
              "created_at", // 提供 offsetFieldKey 以满足 DATE 类型要求
              null,
              null,
              null,
              null);

      // Then: 验证 offsetTypeCode 已被 trim
      assertThat(config.offsetTypeCode()).isEqualTo("DATE");
    }
  }

  // ========== DATE/COMPOSITE 偏移量字段键验证测试 ==========

  @Nested
  @DisplayName("DATE/COMPOSITE 偏移量字段键验证")
  class DateOffsetKeysValidationTests {

    @Test
    @DisplayName("应该抛出异常当 offsetTypeCode 为 DATE 且没有提供任何字段键")
    void shouldThrowExceptionWhenDateOffsetTypeWithoutKeys() {
      // Given: offsetTypeCode 为 DATE，但 offsetFieldKey 和 windowDateFieldKey 都为 null
      String offsetTypeCode = "DATE";
      String offsetFieldKey = null;
      String windowDateFieldKey = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      offsetTypeCode,
                      offsetFieldKey,
                      null,
                      windowDateFieldKey,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("DATE/COMPOSITE offset requires at least one std_key");
    }

    @Test
    @DisplayName("应该抛出异常当 offsetTypeCode 为 COMPOSITE 且没有提供任何字段键")
    void shouldThrowExceptionWhenCompositeOffsetTypeWithoutKeys() {
      // Given: offsetTypeCode 为 COMPOSITE，但 offsetFieldKey 和 windowDateFieldKey 都为 null
      String offsetTypeCode = "COMPOSITE";
      String offsetFieldKey = null;
      String windowDateFieldKey = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      offsetTypeCode,
                      offsetFieldKey,
                      null,
                      windowDateFieldKey,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("DATE/COMPOSITE offset requires at least one std_key");
    }

    @Test
    @DisplayName("应该成功创建 DATE 类型且仅提供 offsetFieldKey 的配置")
    void shouldCreateDateOffsetTypeWithOffsetFieldKeyOnly() {
      // Given: offsetTypeCode 为 DATE，提供 offsetFieldKey
      String offsetTypeCode = "DATE";
      String offsetFieldKey = "created_at";
      String windowDateFieldKey = null;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              offsetTypeCode,
              offsetFieldKey,
              null,
              windowDateFieldKey,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.offsetTypeCode()).isEqualTo("DATE");
      assertThat(config.offsetFieldKey()).isEqualTo("created_at");
      assertThat(config.windowDateFieldKey()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 DATE 类型且仅提供 windowDateFieldKey 的配置")
    void shouldCreateDateOffsetTypeWithWindowDateFieldKeyOnly() {
      // Given: offsetTypeCode 为 DATE，提供 windowDateFieldKey
      String offsetTypeCode = "DATE";
      String offsetFieldKey = null;
      String windowDateFieldKey = "updated_at";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              offsetTypeCode,
              offsetFieldKey,
              null,
              windowDateFieldKey,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.offsetTypeCode()).isEqualTo("DATE");
      assertThat(config.offsetFieldKey()).isNull();
      assertThat(config.windowDateFieldKey()).isEqualTo("updated_at");
    }

    @Test
    @DisplayName("应该成功创建 DATE 类型且同时提供两个字段键的配置")
    void shouldCreateDateOffsetTypeWithBothKeys() {
      // Given: offsetTypeCode 为 DATE，同时提供两个字段键
      String offsetTypeCode = "DATE";
      String offsetFieldKey = "created_at";
      String windowDateFieldKey = "updated_at";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              offsetTypeCode,
              offsetFieldKey,
              null,
              windowDateFieldKey,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.offsetTypeCode()).isEqualTo("DATE");
      assertThat(config.offsetFieldKey()).isEqualTo("created_at");
      assertThat(config.windowDateFieldKey()).isEqualTo("updated_at");
    }

    @Test
    @DisplayName("应该成功创建 COMPOSITE 类型且提供 offsetFieldKey 的配置")
    void shouldCreateCompositeOffsetTypeWithOffsetFieldKey() {
      // Given: offsetTypeCode 为 COMPOSITE，提供 offsetFieldKey
      String offsetTypeCode = "COMPOSITE";
      String offsetFieldKey = "composite_key";
      String windowDateFieldKey = null;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              offsetTypeCode,
              offsetFieldKey,
              null,
              windowDateFieldKey,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.offsetTypeCode()).isEqualTo("COMPOSITE");
      assertThat(config.offsetFieldKey()).isEqualTo("composite_key");
    }

    @Test
    @DisplayName("应该成功创建 ID 类型且不需要提供字段键的配置")
    void shouldCreateIdOffsetTypeWithoutKeys() {
      // Given: offsetTypeCode 为 ID，不需要提供字段键
      String offsetTypeCode = "ID";
      String offsetFieldKey = null;
      String windowDateFieldKey = null;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              offsetTypeCode,
              offsetFieldKey,
              null,
              windowDateFieldKey,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.offsetTypeCode()).isEqualTo("ID");
      assertThat(config.offsetFieldKey()).isNull();
      assertThat(config.windowDateFieldKey()).isNull();
    }

    @Test
    @DisplayName("应该抛出异常当 DATE 类型提供空白字符串的字段键")
    void shouldThrowExceptionWhenDateOffsetTypeWithBlankKeys() {
      // Given: offsetTypeCode 为 DATE，但字段键为空白字符串
      String offsetTypeCode = "DATE";
      String offsetFieldKey = "   ";
      String windowDateFieldKey = "   ";

      // When & Then: 创建配置应该失败（空白字符串会被标准化为 null）
      assertThatThrownBy(
              () ->
                  new WindowOffsetConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "SLIDING",
                      null,
                      "HOUR",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      offsetTypeCode,
                      offsetFieldKey,
                      null,
                      windowDateFieldKey,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("DATE/COMPOSITE offset requires at least one std_key");
    }
  }

  // ========== 字符串 Trim 处理测试 ==========

  @Nested
  @DisplayName("字符串字段 Trim 处理")
  class StringTrimTests {

    @Test
    @DisplayName("应该自动 trim operationType 字段")
    void shouldTrimOperationType() {
      // Given: operationType 包含首尾空白
      String operationType = "  HARVEST  ";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证 operationType 已被 trim
      assertThat(config.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该 trim 所有字符串字段")
    void shouldTrimAllStringFields() {
      // Given: 所有字符串字段包含首尾空白
      String operationType = "  UPDATE  ";
      String windowModeCode = "  CALENDAR  ";
      String windowSizeUnitCode = "  DAY  ";
      String calendarAlignTo = "  WEEK  ";
      String lookbackUnitCode = "  HOUR  ";
      String overlapUnitCode = "  MINUTE  ";
      String offsetTypeCode = "  DATE  ";
      String offsetFieldKey = "  created_at  ";
      String offsetDateFormat = "  ISO_INSTANT  ";
      String windowDateFieldKey = "  updated_at  ";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              windowModeCode,
              null,
              windowSizeUnitCode,
              calendarAlignTo,
              null,
              lookbackUnitCode,
              null,
              overlapUnitCode,
              null,
              offsetTypeCode,
              offsetFieldKey,
              offsetDateFormat,
              windowDateFieldKey,
              null,
              null);

      // Then: 验证所有字段都已被 trim
      assertThat(config.operationType()).isEqualTo("UPDATE");
      assertThat(config.windowModeCode()).isEqualTo("CALENDAR");
      assertThat(config.windowSizeUnitCode()).isEqualTo("DAY");
      assertThat(config.calendarAlignTo()).isEqualTo("WEEK");
      assertThat(config.lookbackUnitCode()).isEqualTo("HOUR");
      assertThat(config.overlapUnitCode()).isEqualTo("MINUTE");
      assertThat(config.offsetTypeCode()).isEqualTo("DATE");
      assertThat(config.offsetFieldKey()).isEqualTo("created_at");
      assertThat(config.offsetDateFormat()).isEqualTo("ISO_INSTANT");
      assertThat(config.windowDateFieldKey()).isEqualTo("updated_at");
    }

    @Test
    @DisplayName("应该处理混合空白字符的字符串")
    void shouldHandleStringWithMixedWhitespace() {
      // Given: 包含制表符、换行符等混合空白的字符串
      String operationType = "\t\n  BACKFILL  \t\n";
      String windowModeCode = " \t SLIDING \n ";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              windowModeCode,
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证空白字符都被 trim
      assertThat(config.operationType()).isEqualTo("BACKFILL");
      assertThat(config.windowModeCode()).isEqualTo("SLIDING");
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

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证 operationType 为 null
      assertThat(config.operationType()).isNull();
    }

    @Test
    @DisplayName("应该处理所有可选字段都为 null 的情况")
    void shouldHandleAllOptionalFieldsBeingNull() {
      // Given: 所有可选字段为 null
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              null, // operationType
              Instant.now(),
              null, // effectiveTo
              "SLIDING",
              null, // windowSizeValue
              "HOUR",
              null, // calendarAlignTo
              null, // lookbackValue
              null, // lookbackUnitCode
              null, // overlapValue
              null, // overlapUnitCode
              null, // watermarkLagSeconds
              "ID",
              null, // offsetFieldKey
              null, // offsetDateFormat
              null, // windowDateFieldKey
              null, // maxIdsPerWindow
              null); // maxWindowSpanSeconds

      // Then: 验证可选字段都为 null
      assertThat(config.operationType()).isNull();
      assertThat(config.effectiveTo()).isNull();
      assertThat(config.windowSizeValue()).isNull();
      assertThat(config.calendarAlignTo()).isNull();
      assertThat(config.lookbackValue()).isNull();
      assertThat(config.lookbackUnitCode()).isNull();
      assertThat(config.overlapValue()).isNull();
      assertThat(config.overlapUnitCode()).isNull();
      assertThat(config.watermarkLagSeconds()).isNull();
      assertThat(config.offsetFieldKey()).isNull();
      assertThat(config.offsetDateFormat()).isNull();
      assertThat(config.windowDateFieldKey()).isNull();
      assertThat(config.maxIdsPerWindow()).isNull();
      assertThat(config.maxWindowSpanSeconds()).isNull();
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
      String windowModeCode = "SLIDING";
      String windowSizeUnitCode = "HOUR";
      String offsetTypeCode = "DATE";
      String offsetFieldKey = "created_at";

      WindowOffsetConfig config1 =
          new WindowOffsetConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              null,
              windowModeCode,
              3600,
              windowSizeUnitCode,
              null,
              null,
              null,
              null,
              null,
              null,
              offsetTypeCode,
              offsetFieldKey,
              "ISO_INSTANT",
              null,
              1000,
              7200);

      WindowOffsetConfig config2 =
          new WindowOffsetConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              null,
              windowModeCode,
              3600,
              windowSizeUnitCode,
              null,
              null,
              null,
              null,
              null,
              null,
              offsetTypeCode,
              offsetFieldKey,
              "ISO_INSTANT",
              null,
              1000,
              7200);

      // When & Then: 应该相等
      assertThat(config1).isEqualTo(config2);
      assertThat(config1).hasSameHashCodeAs(config2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同值对象不相等）")
    void shouldImplementEqualsCorrectlyForDifferentObjects() {
      // Given: 两个不同值的配置
      WindowOffsetConfig config1 =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      WindowOffsetConfig config2 =
          new WindowOffsetConfig(
              1002L,
              2002L,
              "UPDATE",
              Instant.now(),
              null,
              "CALENDAR",
              null,
              "DAY",
              null,
              null,
              null,
              null,
              null,
              null,
              "DATE",
              "created_at",
              null,
              null,
              null,
              null);

      // When & Then: 不应该相等
      assertThat(config1).isNotEqualTo(config2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      WindowOffsetConfig config1 =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      WindowOffsetConfig config2 =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // When & Then: hashCode 应该相等
      assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建配置
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.parse("2025-01-01T00:00:00Z"),
              null,
              "SLIDING",
              3600,
              "SECOND",
              null,
              300,
              "SECOND",
              60,
              "SECOND",
              120,
              "DATE",
              "created_at",
              "ISO_INSTANT",
              "updated_at",
              1000,
              7200);

      // When: 调用 toString
      String toString = config.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("WindowOffsetConfig");
      assertThat(toString).contains("1001");
      assertThat(toString).contains("2001");
      assertThat(toString).contains("HARVEST");
      assertThat(toString).contains("SLIDING");
      assertThat(toString).contains("DATE");
    }

    @Test
    @DisplayName("应该支持 equals 自反性")
    void shouldSupportEqualsReflexivity() {
      // Given: 创建配置
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // When & Then: 对象应该等于自身
      assertThat(config).isEqualTo(config);
    }

    @Test
    @DisplayName("应该正确处理与 null 的比较")
    void shouldHandleNullComparison() {
      // Given: 创建配置
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // When & Then: 与 null 比较应该返回 false
      assertThat(config).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确处理与不同类型对象的比较")
    void shouldHandleDifferentTypeComparison() {
      // Given: 创建配置
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // When & Then: 与不同类型对象比较应该返回 false
      assertThat(config).isNotEqualTo("Not a WindowOffsetConfig");
      assertThat(config).isNotEqualTo(1001L);
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
      String originalWindowModeCode = "SLIDING";
      String originalWindowSizeUnitCode = "HOUR";
      String originalOffsetTypeCode = "DATE";
      String originalOffsetFieldKey = "created_at";

      WindowOffsetConfig config =
          new WindowOffsetConfig(
              originalId,
              originalProvenanceId,
              originalOperationType,
              originalEffectiveFrom,
              null,
              originalWindowModeCode,
              3600,
              originalWindowSizeUnitCode,
              null,
              null,
              null,
              null,
              null,
              null,
              originalOffsetTypeCode,
              originalOffsetFieldKey,
              null,
              null,
              null,
              null);

      // When: 获取字段值
      Long retrievedId = config.id();
      Long retrievedProvenanceId = config.provenanceId();
      String retrievedOperationType = config.operationType();
      Instant retrievedEffectiveFrom = config.effectiveFrom();
      String retrievedWindowModeCode = config.windowModeCode();
      String retrievedWindowSizeUnitCode = config.windowSizeUnitCode();
      String retrievedOffsetTypeCode = config.offsetTypeCode();
      String retrievedOffsetFieldKey = config.offsetFieldKey();

      // Then: 字段值应该保持不变
      assertThat(retrievedId).isEqualTo(originalId);
      assertThat(retrievedProvenanceId).isEqualTo(originalProvenanceId);
      assertThat(retrievedOperationType).isEqualTo(originalOperationType);
      assertThat(retrievedEffectiveFrom).isEqualTo(originalEffectiveFrom);
      assertThat(retrievedWindowModeCode).isEqualTo(originalWindowModeCode);
      assertThat(retrievedWindowSizeUnitCode).isEqualTo(originalWindowSizeUnitCode);
      assertThat(retrievedOffsetTypeCode).isEqualTo(originalOffsetTypeCode);
      assertThat(retrievedOffsetFieldKey).isEqualTo(originalOffsetFieldKey);
    }

    @Test
    @DisplayName("字符串字段应该在创建后保持不变")
    void stringFieldsShouldRemainUnchangedAfterCreation() {
      // Given: 创建配置
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "DATE",
              "created_at",
              null,
              null,
              null,
              null);

      // When: 多次获取字段值
      String operationType1 = config.operationType();
      String operationType2 = config.operationType();
      String windowModeCode1 = config.windowModeCode();
      String windowModeCode2 = config.windowModeCode();

      // Then: 字段值应该保持一致
      assertThat(operationType1).isEqualTo(operationType2);
      assertThat(windowModeCode1).isEqualTo(windowModeCode2);
      assertThat(operationType1).isSameAs(operationType2);
      assertThat(windowModeCode1).isSameAs(windowModeCode2);
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenariosTests {

    @Test
    @DisplayName("应该成功创建 SLIDING 窗口模式的配置")
    void shouldCreateSlidingWindowModeConfig() {
      // Given: windowModeCode 为 SLIDING
      String windowModeCode = "SLIDING";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              windowModeCode,
              3600,
              "SECOND",
              null, // calendarAlignTo 在 SLIDING 模式下为 null
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.windowModeCode()).isEqualTo("SLIDING");
      assertThat(config.calendarAlignTo()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 CALENDAR 窗口模式的配置")
    void shouldCreateCalendarWindowModeConfig() {
      // Given: windowModeCode 为 CALENDAR，提供 calendarAlignTo
      String windowModeCode = "CALENDAR";
      String calendarAlignTo = "HOUR";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              windowModeCode,
              1,
              "HOUR",
              calendarAlignTo,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.windowModeCode()).isEqualTo("CALENDAR");
      assertThat(config.calendarAlignTo()).isEqualTo("HOUR");
    }

    @Test
    @DisplayName("应该成功创建包含回溯配置的窗口")
    void shouldCreateConfigWithLookback() {
      // Given: 回溯配置
      Integer lookbackValue = 300;
      String lookbackUnitCode = "SECOND";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              3600,
              "SECOND",
              null,
              lookbackValue,
              lookbackUnitCode,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证回溯配置正确赋值
      assertThat(config.lookbackValue()).isEqualTo(300);
      assertThat(config.lookbackUnitCode()).isEqualTo("SECOND");
    }

    @Test
    @DisplayName("应该成功创建包含重叠配置的窗口")
    void shouldCreateConfigWithOverlap() {
      // Given: 重叠配置
      Integer overlapValue = 60;
      String overlapUnitCode = "SECOND";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              3600,
              "SECOND",
              null,
              null,
              null,
              overlapValue,
              overlapUnitCode,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 验证重叠配置正确赋值
      assertThat(config.overlapValue()).isEqualTo(60);
      assertThat(config.overlapUnitCode()).isEqualTo("SECOND");
    }

    @Test
    @DisplayName("应该成功创建包含水位线配置的窗口")
    void shouldCreateConfigWithWatermark() {
      // Given: 水位线配置
      Integer watermarkLagSeconds = 120;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              3600,
              "SECOND",
              null,
              null,
              null,
              null,
              null,
              watermarkLagSeconds,
              "DATE",
              "created_at",
              null,
              null,
              null,
              null);

      // Then: 验证水位线配置正确赋值
      assertThat(config.watermarkLagSeconds()).isEqualTo(120);
    }

    @Test
    @DisplayName("应该成功创建 ID 偏移量类型的配置")
    void shouldCreateIdOffsetTypeConfig() {
      // Given: offsetTypeCode 为 ID
      String offsetTypeCode = "ID";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              offsetTypeCode,
              null,
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(config.offsetTypeCode()).isEqualTo("ID");
    }

    @Test
    @DisplayName("应该成功创建包含窗口分割配置的窗口")
    void shouldCreateConfigWithWindowSplitting() {
      // Given: 窗口分割配置
      Integer maxIdsPerWindow = 1000;
      Integer maxWindowSpanSeconds = 7200;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              3600,
              "SECOND",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              maxIdsPerWindow,
              maxWindowSpanSeconds);

      // Then: 验证窗口分割配置正确赋值
      assertThat(config.maxIdsPerWindow()).isEqualTo(1000);
      assertThat(config.maxWindowSpanSeconds()).isEqualTo(7200);
    }

    @Test
    @DisplayName("应该成功创建完整的窗口偏移配置")
    void shouldCreateCompleteWindowOffsetConfig() {
      // Given: 完整的窗口偏移配置
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.parse("2025-01-01T00:00:00Z"),
              Instant.parse("2025-12-31T23:59:59Z"),
              "SLIDING",
              3600,
              "SECOND",
              null,
              300,
              "SECOND",
              60,
              "SECOND",
              120,
              "DATE",
              "created_at",
              "ISO_INSTANT",
              "updated_at",
              1000,
              7200);

      // Then: 验证所有字段
      assertThat(config.id()).isEqualTo(1001L);
      assertThat(config.provenanceId()).isEqualTo(2001L);
      assertThat(config.operationType()).isEqualTo("HARVEST");
      assertThat(config.windowModeCode()).isEqualTo("SLIDING");
      assertThat(config.windowSizeValue()).isEqualTo(3600);
      assertThat(config.windowSizeUnitCode()).isEqualTo("SECOND");
      assertThat(config.lookbackValue()).isEqualTo(300);
      assertThat(config.overlapValue()).isEqualTo(60);
      assertThat(config.watermarkLagSeconds()).isEqualTo(120);
      assertThat(config.offsetTypeCode()).isEqualTo("DATE");
      assertThat(config.offsetFieldKey()).isEqualTo("created_at");
      assertThat(config.offsetDateFormat()).isEqualTo("ISO_INSTANT");
      assertThat(config.windowDateFieldKey()).isEqualTo("updated_at");
      assertThat(config.maxIdsPerWindow()).isEqualTo(1000);
      assertThat(config.maxWindowSpanSeconds()).isEqualTo(7200);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件处理")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理 trim 后相同的不同输入")
    void shouldHandleDifferentInputsWithSameTrimmedValue() {
      // Given: trim 后相同的不同输入
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      WindowOffsetConfig config1 =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              effectiveFrom,
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      WindowOffsetConfig config2 =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "  HARVEST  ",
              effectiveFrom,
              null,
              "  SLIDING  ",
              null,
              "  HOUR  ",
              null,
              null,
              null,
              null,
              null,
              null,
              "  ID  ",
              null,
              null,
              null,
              null,
              null);

      // When & Then: trim 后应该相等
      assertThat(config1).isEqualTo(config2);
    }

    @Test
    @DisplayName("应该处理 windowSizeValue 为 0 的情况")
    void shouldHandleZeroWindowSize() {
      // Given: windowSizeValue 为 0
      Integer windowSizeValue = 0;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              windowSizeValue,
              "SECOND",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 应该成功创建
      assertThat(config.windowSizeValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该处理 watermarkLagSeconds 为 0 的情况")
    void shouldHandleZeroWatermark() {
      // Given: watermarkLagSeconds 为 0（无延迟容忍）
      Integer watermarkLagSeconds = 0;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              watermarkLagSeconds,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 应该成功创建
      assertThat(config.watermarkLagSeconds()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该处理 maxWindowSpanSeconds 为 Integer.MAX_VALUE 的情况")
    void shouldHandleMaxIntegerWindowSpan() {
      // Given: maxWindowSpanSeconds 为 Integer.MAX_VALUE
      Integer maxWindowSpanSeconds = Integer.MAX_VALUE;

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              maxWindowSpanSeconds);

      // Then: 应该成功创建
      assertThat(config.maxWindowSpanSeconds()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理 operationType 为极短字符串的情况")
    void shouldHandleMinimalOperationType() {
      // Given: operationType 为单字符
      String operationType = "A";

      // When: 创建 WindowOffsetConfig
      WindowOffsetConfig config =
          new WindowOffsetConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              "SLIDING",
              null,
              "HOUR",
              null,
              null,
              null,
              null,
              null,
              null,
              "ID",
              null,
              null,
              null,
              null,
              null);

      // Then: 应该成功创建
      assertThat(config.operationType()).isEqualTo("A");
    }
  }
}
