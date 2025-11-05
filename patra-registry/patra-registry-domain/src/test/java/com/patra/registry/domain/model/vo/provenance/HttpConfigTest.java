package com.patra.registry.domain.model.vo.provenance;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * HttpConfig 值对象单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>测试 record 的业务约束验证（正整数 ID、非空白字符串、必需字段等）
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
 *   <li>✅ 非空白字符串验证（retryAfterPolicyCode）
 *   <li>✅ 必需字段非 null 验证（effectiveFrom）
 *   <li>✅ 字符串 trim 处理测试（operationType, proxyUrlValue, retryAfterPolicyCode, idempotencyHeaderName）
 *   <li>✅ 可选字段处理（允许为 null）
 *   <li>✅ record 的 equals/hashCode/toString 测试
 *   <li>✅ 不变性保证
 *   <li>✅ 业务场景测试（不同操作类型、策略代码、TLS 配置等）
 *   <li>✅ 边界条件处理
 * </ul>
 *
 * @author Patra Team
 * @since 2.0
 */
@DisplayName("HttpConfig 单元测试")
class HttpConfigTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的 HTTP 配置")
    void shouldCreateHttpConfigWithAllFields() {
      // Given: 所有字段都有效
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      String defaultHeadersJson = "{\"User-Agent\":\"Patra/2.0\"}";
      Integer timeoutConnectMillis = 5000;
      Integer timeoutReadMillis = 10000;
      Integer timeoutTotalMillis = 30000;
      boolean tlsVerifyEnabled = true;
      String proxyUrlValue = "http://proxy:8080";
      String retryAfterPolicyCode = "RESPECT";
      Integer retryAfterCapMillis = 60000;
      String idempotencyHeaderName = "Idempotency-Key";
      Integer idempotencyTtlSeconds = 86400;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              defaultHeadersJson,
              timeoutConnectMillis,
              timeoutReadMillis,
              timeoutTotalMillis,
              tlsVerifyEnabled,
              proxyUrlValue,
              retryAfterPolicyCode,
              retryAfterCapMillis,
              idempotencyHeaderName,
              idempotencyTtlSeconds);

      // Then: 验证所有字段正确赋值
      assertThat(httpConfig).isNotNull();
      assertThat(httpConfig.id()).isEqualTo(id);
      assertThat(httpConfig.provenanceId()).isEqualTo(provenanceId);
      assertThat(httpConfig.operationType()).isEqualTo(operationType);
      assertThat(httpConfig.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(httpConfig.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(httpConfig.defaultHeadersJson()).isEqualTo(defaultHeadersJson);
      assertThat(httpConfig.timeoutConnectMillis()).isEqualTo(timeoutConnectMillis);
      assertThat(httpConfig.timeoutReadMillis()).isEqualTo(timeoutReadMillis);
      assertThat(httpConfig.timeoutTotalMillis()).isEqualTo(timeoutTotalMillis);
      assertThat(httpConfig.tlsVerifyEnabled()).isTrue();
      assertThat(httpConfig.proxyUrlValue()).isEqualTo(proxyUrlValue);
      assertThat(httpConfig.retryAfterPolicyCode()).isEqualTo(retryAfterPolicyCode);
      assertThat(httpConfig.retryAfterCapMillis()).isEqualTo(retryAfterCapMillis);
      assertThat(httpConfig.idempotencyHeaderName()).isEqualTo(idempotencyHeaderName);
      assertThat(httpConfig.idempotencyTtlSeconds()).isEqualTo(idempotencyTtlSeconds);
    }

    @Test
    @DisplayName("应该成功创建仅包含必需字段的最小配置")
    void shouldCreateMinimalHttpConfig() {
      // Given: 只有必需字段
      Long id = 1001L;
      Long provenanceId = 2001L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      String retryAfterPolicyCode = "IGNORE";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              id,
              provenanceId,
              null, // operationType
              effectiveFrom,
              null, // effectiveTo
              null, // defaultHeadersJson
              null, // timeoutConnectMillis
              null, // timeoutReadMillis
              null, // timeoutTotalMillis
              false, // tlsVerifyEnabled
              null, // proxyUrlValue
              retryAfterPolicyCode,
              null, // retryAfterCapMillis
              null, // idempotencyHeaderName
              null // idempotencyTtlSeconds
              );

      // Then: 验证必需字段正确赋值，可选字段为 null
      assertThat(httpConfig).isNotNull();
      assertThat(httpConfig.id()).isEqualTo(id);
      assertThat(httpConfig.provenanceId()).isEqualTo(provenanceId);
      assertThat(httpConfig.operationType()).isNull();
      assertThat(httpConfig.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(httpConfig.effectiveTo()).isNull();
      assertThat(httpConfig.defaultHeadersJson()).isNull();
      assertThat(httpConfig.timeoutConnectMillis()).isNull();
      assertThat(httpConfig.timeoutReadMillis()).isNull();
      assertThat(httpConfig.timeoutTotalMillis()).isNull();
      assertThat(httpConfig.tlsVerifyEnabled()).isFalse();
      assertThat(httpConfig.proxyUrlValue()).isNull();
      assertThat(httpConfig.retryAfterPolicyCode()).isEqualTo(retryAfterPolicyCode);
      assertThat(httpConfig.retryAfterCapMillis()).isNull();
      assertThat(httpConfig.idempotencyHeaderName()).isNull();
      assertThat(httpConfig.idempotencyTtlSeconds()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 effectiveTo 为 null 的永久有效配置")
    void shouldCreatePermanentConfig() {
      // Given: effectiveTo 为 null（表示永久有效）
      Long id = 1001L;
      Long provenanceId = 2001L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = null;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              id,
              provenanceId,
              "ALL",
              effectiveFrom,
              effectiveTo,
              null,
              null,
              null,
              null,
              true,
              null,
              "IGNORE",
              null,
              null,
              null);

      // Then: 验证 effectiveTo 为 null
      assertThat(httpConfig.effectiveTo()).isNull();
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
                  new HttpConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      "IGNORE",
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("HTTP config id")
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
                  new HttpConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      "IGNORE",
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("HTTP config id")
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
                  new HttpConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      "IGNORE",
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("HTTP config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 id 为 1 的配置")
    void shouldCreateConfigWithIdOne() {
      // Given: id 为 1
      Long id = 1L;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              id, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null, "IGNORE",
              null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功创建 id 为 Long.MAX_VALUE 的配置")
    void shouldCreateConfigWithMaxId() {
      // Given: id 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              id, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null, "IGNORE",
              null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.id()).isEqualTo(Long.MAX_VALUE);
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
                  new HttpConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      "IGNORE",
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
                  new HttpConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      "IGNORE",
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
                  new HttpConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      "IGNORE",
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

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              provenanceId,
              "ALL",
              Instant.now(),
              null,
              null,
              null,
              null,
              null,
              true,
              null,
              "IGNORE",
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(httpConfig.provenanceId()).isEqualTo(1L);
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
                  new HttpConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      effectiveFrom,
                      null,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      "IGNORE",
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

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", effectiveFrom, null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为过去时间的配置")
    void shouldCreateConfigWithPastEffectiveFrom() {
      // Given: effectiveFrom 为过去时间
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", effectiveFrom, null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为未来时间的配置")
    void shouldCreateConfigWithFutureEffectiveFrom() {
      // Given: effectiveFrom 为未来时间
      Instant effectiveFrom = Instant.parse("2026-01-01T00:00:00Z");

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", effectiveFrom, null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }
  }

  // ========== RetryAfterPolicyCode 验证测试 ==========

  @Nested
  @DisplayName("RetryAfterPolicyCode 非空白验证")
  class RetryAfterPolicyCodeValidationTests {

    @Test
    @DisplayName("应该抛出异常当 retryAfterPolicyCode 为 null")
    void shouldThrowExceptionWhenRetryAfterPolicyCodeIsNull() {
      // Given: retryAfterPolicyCode 为 null
      String retryAfterPolicyCode = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new HttpConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      retryAfterPolicyCode,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Retry-after policy code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 retryAfterPolicyCode 为空字符串")
    void shouldThrowExceptionWhenRetryAfterPolicyCodeIsEmpty() {
      // Given: retryAfterPolicyCode 为空字符串
      String retryAfterPolicyCode = "";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new HttpConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      retryAfterPolicyCode,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Retry-after policy code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 retryAfterPolicyCode 仅包含空白字符")
    void shouldThrowExceptionWhenRetryAfterPolicyCodeIsBlank() {
      // Given: retryAfterPolicyCode 仅包含空白字符
      String retryAfterPolicyCode = "   ";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new HttpConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      null,
                      true,
                      null,
                      retryAfterPolicyCode,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Retry-after policy code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim retryAfterPolicyCode 字段")
    void shouldTrimRetryAfterPolicyCode() {
      // Given: retryAfterPolicyCode 包含首尾空白
      String retryAfterPolicyCode = "  RESPECT  ";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              retryAfterPolicyCode, null, null, null);

      // Then: 验证 retryAfterPolicyCode 已被 trim
      assertThat(httpConfig.retryAfterPolicyCode()).isEqualTo("RESPECT");
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

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // Then: 验证 operationType 已被 trim
      assertThat(httpConfig.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该自动 trim proxyUrlValue 字段")
    void shouldTrimProxyUrlValue() {
      // Given: proxyUrlValue 包含首尾空白
      String proxyUrlValue = "  http://proxy:8080  ";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true,
              proxyUrlValue, "IGNORE", null, null, null);

      // Then: 验证 proxyUrlValue 已被 trim
      assertThat(httpConfig.proxyUrlValue()).isEqualTo("http://proxy:8080");
    }

    @Test
    @DisplayName("应该自动 trim idempotencyHeaderName 字段")
    void shouldTrimIdempotencyHeaderName() {
      // Given: idempotencyHeaderName 包含首尾空白
      String idempotencyHeaderName = "  Idempotency-Key  ";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, idempotencyHeaderName, null);

      // Then: 验证 idempotencyHeaderName 已被 trim
      assertThat(httpConfig.idempotencyHeaderName()).isEqualTo("Idempotency-Key");
    }

    @Test
    @DisplayName("应该 trim 所有字符串字段")
    void shouldTrimAllStringFields() {
      // Given: 所有字符串字段包含首尾空白
      String operationType = "  UPDATE  ";
      String proxyUrlValue = "  socks5://proxy:1080  ";
      String retryAfterPolicyCode = "  CLAMP  ";
      String idempotencyHeaderName = "  X-Request-ID  ";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              null,
              null,
              null,
              null,
              true,
              proxyUrlValue,
              retryAfterPolicyCode,
              null,
              idempotencyHeaderName,
              null);

      // Then: 验证所有字段都已被 trim
      assertThat(httpConfig.operationType()).isEqualTo("UPDATE");
      assertThat(httpConfig.proxyUrlValue()).isEqualTo("socks5://proxy:1080");
      assertThat(httpConfig.retryAfterPolicyCode()).isEqualTo("CLAMP");
      assertThat(httpConfig.idempotencyHeaderName()).isEqualTo("X-Request-ID");
    }

    @Test
    @DisplayName("应该处理混合空白字符的字符串")
    void shouldHandleStringWithMixedWhitespace() {
      // Given: 包含制表符、换行符等混合空白的字符串
      String operationType = "\t\n  BACKFILL  \t\n";
      String retryAfterPolicyCode = " \t RESPECT \n ";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null, true, null,
              retryAfterPolicyCode, null, null, null);

      // Then: 验证空白字符都被 trim
      assertThat(httpConfig.operationType()).isEqualTo("BACKFILL");
      assertThat(httpConfig.retryAfterPolicyCode()).isEqualTo("RESPECT");
    }

    @Test
    @DisplayName("应该保留字符串内部的空白字符")
    void shouldPreserveInternalWhitespace() {
      // Given: 字符串内部包含空白字符
      String idempotencyHeaderName = "  X Request ID  ";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, idempotencyHeaderName, null);

      // Then: 验证内部空白字符被保留
      assertThat(httpConfig.idempotencyHeaderName()).isEqualTo("X Request ID");
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

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // Then: 验证 operationType 为 null
      assertThat(httpConfig.operationType()).isNull();
    }

    @Test
    @DisplayName("proxyUrlValue 为 null 时应保持 null")
    void proxyUrlValueCanBeNull() {
      // Given: proxyUrlValue 为 null
      String proxyUrlValue = null;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true,
              proxyUrlValue, "IGNORE", null, null, null);

      // Then: 验证 proxyUrlValue 为 null
      assertThat(httpConfig.proxyUrlValue()).isNull();
    }

    @Test
    @DisplayName("idempotencyHeaderName 为 null 时应保持 null")
    void idempotencyHeaderNameCanBeNull() {
      // Given: idempotencyHeaderName 为 null
      String idempotencyHeaderName = null;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, idempotencyHeaderName, null);

      // Then: 验证 idempotencyHeaderName 为 null
      assertThat(httpConfig.idempotencyHeaderName()).isNull();
    }

    @Test
    @DisplayName("effectiveTo 为 null 时应保持 null（表示永久有效）")
    void effectiveToCanBeNull() {
      // Given: effectiveTo 为 null
      Instant effectiveTo = null;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), effectiveTo, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // Then: 验证 effectiveTo 为 null
      assertThat(httpConfig.effectiveTo()).isNull();
    }

    @Test
    @DisplayName("defaultHeadersJson 为 null 时应允许")
    void defaultHeadersJsonCanBeNull() {
      // Given: defaultHeadersJson 为 null
      String defaultHeadersJson = null;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "ALL",
              Instant.now(),
              null,
              defaultHeadersJson,
              null,
              null,
              null,
              true,
              null,
              "IGNORE",
              null,
              null,
              null);

      // Then: 验证 defaultHeadersJson 为 null
      assertThat(httpConfig.defaultHeadersJson()).isNull();
    }

    @Test
    @DisplayName("timeoutConnectMillis 为 null 时应允许")
    void timeoutConnectMillisCanBeNull() {
      // Given: timeoutConnectMillis 为 null
      Integer timeoutConnectMillis = null;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "ALL",
              Instant.now(),
              null,
              null,
              timeoutConnectMillis,
              null,
              null,
              true,
              null,
              "IGNORE",
              null,
              null,
              null);

      // Then: 验证 timeoutConnectMillis 为 null
      assertThat(httpConfig.timeoutConnectMillis()).isNull();
    }

    @Test
    @DisplayName("timeoutReadMillis 为 null 时应允许")
    void timeoutReadMillisCanBeNull() {
      // Given: timeoutReadMillis 为 null
      Integer timeoutReadMillis = null;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "ALL",
              Instant.now(),
              null,
              null,
              null,
              timeoutReadMillis,
              null,
              true,
              null,
              "IGNORE",
              null,
              null,
              null);

      // Then: 验证 timeoutReadMillis 为 null
      assertThat(httpConfig.timeoutReadMillis()).isNull();
    }

    @Test
    @DisplayName("timeoutTotalMillis 为 null 时应允许")
    void timeoutTotalMillisCanBeNull() {
      // Given: timeoutTotalMillis 为 null
      Integer timeoutTotalMillis = null;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "ALL",
              Instant.now(),
              null,
              null,
              null,
              null,
              timeoutTotalMillis,
              true,
              null,
              "IGNORE",
              null,
              null,
              null);

      // Then: 验证 timeoutTotalMillis 为 null
      assertThat(httpConfig.timeoutTotalMillis()).isNull();
    }

    @Test
    @DisplayName("retryAfterCapMillis 为 null 时应允许")
    void retryAfterCapMillisCanBeNull() {
      // Given: retryAfterCapMillis 为 null
      Integer retryAfterCapMillis = null;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", retryAfterCapMillis, null, null);

      // Then: 验证 retryAfterCapMillis 为 null
      assertThat(httpConfig.retryAfterCapMillis()).isNull();
    }

    @Test
    @DisplayName("idempotencyTtlSeconds 为 null 时应允许")
    void idempotencyTtlSecondsCanBeNull() {
      // Given: idempotencyTtlSeconds 为 null
      Integer idempotencyTtlSeconds = null;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, idempotencyTtlSeconds);

      // Then: 验证 idempotencyTtlSeconds 为 null
      assertThat(httpConfig.idempotencyTtlSeconds()).isNull();
    }

    @Test
    @DisplayName("应该处理所有可选字段都为 null 的情况")
    void shouldHandleAllOptionalFieldsBeingNull() {
      // Given: 所有可选字段为 null
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, null, Instant.now(), null, null, null, null, null, false, null,
              "IGNORE", null, null, null);

      // Then: 验证可选字段都为 null
      assertThat(httpConfig.operationType()).isNull();
      assertThat(httpConfig.effectiveTo()).isNull();
      assertThat(httpConfig.defaultHeadersJson()).isNull();
      assertThat(httpConfig.timeoutConnectMillis()).isNull();
      assertThat(httpConfig.timeoutReadMillis()).isNull();
      assertThat(httpConfig.timeoutTotalMillis()).isNull();
      assertThat(httpConfig.proxyUrlValue()).isNull();
      assertThat(httpConfig.retryAfterCapMillis()).isNull();
      assertThat(httpConfig.idempotencyHeaderName()).isNull();
      assertThat(httpConfig.idempotencyTtlSeconds()).isNull();
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
      String retryAfterPolicyCode = "RESPECT";

      HttpConfig httpConfig1 =
          new HttpConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              null,
              null,
              5000,
              10000,
              30000,
              true,
              "http://proxy:8080",
              retryAfterPolicyCode,
              60000,
              "Idempotency-Key",
              86400);

      HttpConfig httpConfig2 =
          new HttpConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              null,
              null,
              5000,
              10000,
              30000,
              true,
              "http://proxy:8080",
              retryAfterPolicyCode,
              60000,
              "Idempotency-Key",
              86400);

      // When & Then: 应该相等
      assertThat(httpConfig1).isEqualTo(httpConfig2);
      assertThat(httpConfig1).hasSameHashCodeAs(httpConfig2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同值对象不相等）")
    void shouldImplementEqualsCorrectlyForDifferentObjects() {
      // Given: 两个不同值的配置
      HttpConfig httpConfig1 =
          new HttpConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      HttpConfig httpConfig2 =
          new HttpConfig(
              1002L, 2002L, "UPDATE", Instant.now(), null, null, null, null, null, false, null,
              "RESPECT", null, null, null);

      // When & Then: 不应该相等
      assertThat(httpConfig1).isNotEqualTo(httpConfig2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      HttpConfig httpConfig1 =
          new HttpConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      HttpConfig httpConfig2 =
          new HttpConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // When & Then: hashCode 应该相等
      assertThat(httpConfig1.hashCode()).isEqualTo(httpConfig2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建配置
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.parse("2025-01-01T00:00:00Z"),
              null,
              "{\"User-Agent\":\"Patra/2.0\"}",
              5000,
              10000,
              30000,
              true,
              "http://proxy:8080",
              "RESPECT",
              60000,
              "Idempotency-Key",
              86400);

      // When: 调用 toString
      String toString = httpConfig.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("HttpConfig");
      assertThat(toString).contains("1001");
      assertThat(toString).contains("2001");
      assertThat(toString).contains("HARVEST");
      assertThat(toString).contains("RESPECT");
    }

    @Test
    @DisplayName("应该支持 equals 自反性")
    void shouldSupportEqualsReflexivity() {
      // Given: 创建配置
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // When & Then: 对象应该等于自身
      assertThat(httpConfig).isEqualTo(httpConfig);
    }

    @Test
    @DisplayName("应该支持 equals 对称性")
    void shouldSupportEqualsSymmetry() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      HttpConfig httpConfig1 =
          new HttpConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      HttpConfig httpConfig2 =
          new HttpConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // When & Then: 对称性（a.equals(b) == b.equals(a)）
      assertThat(httpConfig1.equals(httpConfig2)).isEqualTo(httpConfig2.equals(httpConfig1));
      assertThat(httpConfig1).isEqualTo(httpConfig2);
      assertThat(httpConfig2).isEqualTo(httpConfig1);
    }

    @Test
    @DisplayName("应该支持 equals 传递性")
    void shouldSupportEqualsTransitivity() {
      // Given: 三个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      HttpConfig httpConfig1 =
          new HttpConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      HttpConfig httpConfig2 =
          new HttpConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      HttpConfig httpConfig3 =
          new HttpConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // When & Then: 传递性（a.equals(b) && b.equals(c) => a.equals(c)）
      assertThat(httpConfig1).isEqualTo(httpConfig2);
      assertThat(httpConfig2).isEqualTo(httpConfig3);
      assertThat(httpConfig1).isEqualTo(httpConfig3);
    }

    @Test
    @DisplayName("应该正确处理与 null 的比较")
    void shouldHandleNullComparison() {
      // Given: 创建配置
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // When & Then: 与 null 比较应该返回 false
      assertThat(httpConfig).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确处理与不同类型对象的比较")
    void shouldHandleDifferentTypeComparison() {
      // Given: 创建配置
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // When & Then: 与不同类型对象比较应该返回 false
      assertThat(httpConfig).isNotEqualTo("Not a HttpConfig");
      assertThat(httpConfig).isNotEqualTo(1001L);
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
      String originalRetryAfterPolicyCode = "RESPECT";

      HttpConfig httpConfig =
          new HttpConfig(
              originalId,
              originalProvenanceId,
              originalOperationType,
              originalEffectiveFrom,
              null,
              null,
              5000,
              10000,
              30000,
              true,
              "http://proxy:8080",
              originalRetryAfterPolicyCode,
              60000,
              "Idempotency-Key",
              86400);

      // When: 获取字段值
      Long retrievedId = httpConfig.id();
      Long retrievedProvenanceId = httpConfig.provenanceId();
      String retrievedOperationType = httpConfig.operationType();
      Instant retrievedEffectiveFrom = httpConfig.effectiveFrom();
      String retrievedRetryAfterPolicyCode = httpConfig.retryAfterPolicyCode();

      // Then: 字段值应该保持不变
      assertThat(retrievedId).isEqualTo(originalId);
      assertThat(retrievedProvenanceId).isEqualTo(originalProvenanceId);
      assertThat(retrievedOperationType).isEqualTo(originalOperationType);
      assertThat(retrievedEffectiveFrom).isEqualTo(originalEffectiveFrom);
      assertThat(retrievedRetryAfterPolicyCode).isEqualTo(originalRetryAfterPolicyCode);
    }

    @Test
    @DisplayName("字符串字段应该在创建后保持不变")
    void stringFieldsShouldRemainUnchangedAfterCreation() {
      // Given: 创建配置
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              null,
              null,
              null,
              null,
              true,
              "http://proxy:8080",
              "RESPECT",
              null,
              "Idempotency-Key",
              null);

      // When: 多次获取字段值
      String operationType1 = httpConfig.operationType();
      String operationType2 = httpConfig.operationType();
      String retryAfterPolicyCode1 = httpConfig.retryAfterPolicyCode();
      String retryAfterPolicyCode2 = httpConfig.retryAfterPolicyCode();

      // Then: 字段值应该保持一致
      assertThat(operationType1).isEqualTo(operationType2);
      assertThat(retryAfterPolicyCode1).isEqualTo(retryAfterPolicyCode2);
      assertThat(operationType1).isSameAs(operationType2);
      assertThat(retryAfterPolicyCode1).isSameAs(retryAfterPolicyCode2);
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenariosTests {

    @Test
    @DisplayName("应该成功创建 operationType 为 ALL 的配置")
    void shouldCreateConfigWithOperationTypeAll() {
      // Given: operationType 为 ALL（适用于所有操作）
      String operationType = "ALL";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.operationType()).isEqualTo("ALL");
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 HARVEST 的配置")
    void shouldCreateConfigWithOperationTypeHarvest() {
      // Given: operationType 为 HARVEST
      String operationType = "HARVEST";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 UPDATE 的配置")
    void shouldCreateConfigWithOperationTypeUpdate() {
      // Given: operationType 为 UPDATE
      String operationType = "UPDATE";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.operationType()).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 BACKFILL 的配置")
    void shouldCreateConfigWithOperationTypeBackfill() {
      // Given: operationType 为 BACKFILL
      String operationType = "BACKFILL";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.operationType()).isEqualTo("BACKFILL");
    }

    @Test
    @DisplayName("应该成功创建 retryAfterPolicyCode 为 IGNORE 的配置")
    void shouldCreateConfigWithRetryAfterPolicyIgnore() {
      // Given: retryAfterPolicyCode 为 IGNORE
      String retryAfterPolicyCode = "IGNORE";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              retryAfterPolicyCode, null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.retryAfterPolicyCode()).isEqualTo("IGNORE");
    }

    @Test
    @DisplayName("应该成功创建 retryAfterPolicyCode 为 RESPECT 的配置")
    void shouldCreateConfigWithRetryAfterPolicyRespect() {
      // Given: retryAfterPolicyCode 为 RESPECT
      String retryAfterPolicyCode = "RESPECT";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              retryAfterPolicyCode, 60000, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.retryAfterPolicyCode()).isEqualTo("RESPECT");
      assertThat(httpConfig.retryAfterCapMillis()).isEqualTo(60000);
    }

    @Test
    @DisplayName("应该成功创建 retryAfterPolicyCode 为 CLAMP 的配置")
    void shouldCreateConfigWithRetryAfterPolicyClamp() {
      // Given: retryAfterPolicyCode 为 CLAMP
      String retryAfterPolicyCode = "CLAMP";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              retryAfterPolicyCode, 120000, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.retryAfterPolicyCode()).isEqualTo("CLAMP");
      assertThat(httpConfig.retryAfterCapMillis()).isEqualTo(120000);
    }

    @Test
    @DisplayName("应该成功创建 TLS 验证启用的配置")
    void shouldCreateConfigWithTlsVerificationEnabled() {
      // Given: tlsVerifyEnabled 为 true
      boolean tlsVerifyEnabled = true;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, tlsVerifyEnabled,
              null, "IGNORE", null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.tlsVerifyEnabled()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建 TLS 验证禁用的配置")
    void shouldCreateConfigWithTlsVerificationDisabled() {
      // Given: tlsVerifyEnabled 为 false（仅测试环境）
      boolean tlsVerifyEnabled = false;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, tlsVerifyEnabled,
              null, "IGNORE", null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.tlsVerifyEnabled()).isFalse();
    }

    @Test
    @DisplayName("应该成功创建包含 HTTP 代理的配置")
    void shouldCreateConfigWithHttpProxy() {
      // Given: HTTP 代理 URL
      String proxyUrlValue = "http://user:pass@proxy.example.com:8080";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true,
              proxyUrlValue, "IGNORE", null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.proxyUrlValue()).isEqualTo(proxyUrlValue);
    }

    @Test
    @DisplayName("应该成功创建包含 SOCKS5 代理的配置")
    void shouldCreateConfigWithSocks5Proxy() {
      // Given: SOCKS5 代理 URL
      String proxyUrlValue = "socks5://proxy.example.com:1080";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true,
              proxyUrlValue, "IGNORE", null, null, null);

      // Then: 验证成功创建
      assertThat(httpConfig.proxyUrlValue()).isEqualTo(proxyUrlValue);
    }

    @Test
    @DisplayName("应该成功创建包含默认请求头的配置")
    void shouldCreateConfigWithDefaultHeaders() {
      // Given: 默认请求头 JSON
      String defaultHeadersJson = "{\"User-Agent\":\"Patra/2.0\",\"Accept\":\"application/json\"}";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "ALL",
              Instant.now(),
              null,
              defaultHeadersJson,
              null,
              null,
              null,
              true,
              null,
              "IGNORE",
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(httpConfig.defaultHeadersJson()).isEqualTo(defaultHeadersJson);
    }

    @Test
    @DisplayName("应该成功创建包含所有超时配置的配置")
    void shouldCreateConfigWithAllTimeouts() {
      // Given: 所有超时配置
      Integer timeoutConnectMillis = 5000;
      Integer timeoutReadMillis = 10000;
      Integer timeoutTotalMillis = 30000;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "ALL",
              Instant.now(),
              null,
              null,
              timeoutConnectMillis,
              timeoutReadMillis,
              timeoutTotalMillis,
              true,
              null,
              "IGNORE",
              null,
              null,
              null);

      // Then: 验证所有超时配置正确赋值
      assertThat(httpConfig.timeoutConnectMillis()).isEqualTo(5000);
      assertThat(httpConfig.timeoutReadMillis()).isEqualTo(10000);
      assertThat(httpConfig.timeoutTotalMillis()).isEqualTo(30000);
    }

    @Test
    @DisplayName("应该成功创建包含幂等性配置的配置")
    void shouldCreateConfigWithIdempotency() {
      // Given: 幂等性配置
      String idempotencyHeaderName = "Idempotency-Key";
      Integer idempotencyTtlSeconds = 86400;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "ALL",
              Instant.now(),
              null,
              null,
              null,
              null,
              null,
              true,
              null,
              "IGNORE",
              null,
              idempotencyHeaderName,
              idempotencyTtlSeconds);

      // Then: 验证幂等性配置正确赋值
      assertThat(httpConfig.idempotencyHeaderName()).isEqualTo("Idempotency-Key");
      assertThat(httpConfig.idempotencyTtlSeconds()).isEqualTo(86400);
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

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, operationType, Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      // Then: 应该成功创建
      assertThat(httpConfig.operationType()).isEqualTo("A");
    }

    @Test
    @DisplayName("应该处理 retryAfterPolicyCode 为极短字符串的情况")
    void shouldHandleMinimalRetryAfterPolicyCode() {
      // Given: retryAfterPolicyCode 为单字符
      String retryAfterPolicyCode = "X";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              retryAfterPolicyCode, null, null, null);

      // Then: 应该成功创建
      assertThat(httpConfig.retryAfterPolicyCode()).isEqualTo("X");
    }

    @Test
    @DisplayName("应该处理包含特殊字符的 proxyUrlValue")
    void shouldHandleProxyUrlWithSpecialCharacters() {
      // Given: 包含特殊字符的代理 URL
      String proxyUrlValue = "http://user%40:pass%21@proxy.example.com:8080";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true,
              proxyUrlValue, "IGNORE", null, null, null);

      // Then: 应该成功创建
      assertThat(httpConfig.proxyUrlValue()).isEqualTo(proxyUrlValue);
    }

    @Test
    @DisplayName("应该处理 trim 后相同的不同输入")
    void shouldHandleDifferentInputsWithSameTrimmedValue() {
      // Given: trim 后相同的不同输入
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      HttpConfig httpConfig1 =
          new HttpConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, null, null, null, null, true, null,
              "IGNORE", null, null, null);

      HttpConfig httpConfig2 =
          new HttpConfig(
              1001L,
              2001L,
              "  HARVEST  ",
              effectiveFrom,
              null,
              null,
              null,
              null,
              null,
              true,
              null,
              "  IGNORE  ",
              null,
              null,
              null);

      // When & Then: trim 后应该相等
      assertThat(httpConfig1).isEqualTo(httpConfig2);
    }

    @Test
    @DisplayName("应该处理超时值为 0 的情况")
    void shouldHandleZeroTimeouts() {
      // Given: 超时值为 0（表示无限等待）
      Integer timeoutConnectMillis = 0;
      Integer timeoutReadMillis = 0;
      Integer timeoutTotalMillis = 0;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "ALL",
              Instant.now(),
              null,
              null,
              timeoutConnectMillis,
              timeoutReadMillis,
              timeoutTotalMillis,
              true,
              null,
              "IGNORE",
              null,
              null,
              null);

      // Then: 应该成功创建
      assertThat(httpConfig.timeoutConnectMillis()).isEqualTo(0);
      assertThat(httpConfig.timeoutReadMillis()).isEqualTo(0);
      assertThat(httpConfig.timeoutTotalMillis()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该处理超时值为最大整数的情况")
    void shouldHandleMaxIntegerTimeouts() {
      // Given: 超时值为 Integer.MAX_VALUE
      Integer timeoutConnectMillis = Integer.MAX_VALUE;
      Integer timeoutReadMillis = Integer.MAX_VALUE;
      Integer timeoutTotalMillis = Integer.MAX_VALUE;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "ALL",
              Instant.now(),
              null,
              null,
              timeoutConnectMillis,
              timeoutReadMillis,
              timeoutTotalMillis,
              true,
              null,
              "IGNORE",
              null,
              null,
              null);

      // Then: 应该成功创建
      assertThat(httpConfig.timeoutConnectMillis()).isEqualTo(Integer.MAX_VALUE);
      assertThat(httpConfig.timeoutReadMillis()).isEqualTo(Integer.MAX_VALUE);
      assertThat(httpConfig.timeoutTotalMillis()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理 retryAfterCapMillis 为 0 的情况")
    void shouldHandleZeroRetryAfterCap() {
      // Given: retryAfterCapMillis 为 0（立即重试）
      Integer retryAfterCapMillis = 0;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              "CLAMP", retryAfterCapMillis, null, null);

      // Then: 应该成功创建
      assertThat(httpConfig.retryAfterCapMillis()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该处理 idempotencyTtlSeconds 为 0 的情况")
    void shouldHandleZeroIdempotencyTtl() {
      // Given: idempotencyTtlSeconds 为 0（立即过期）
      Integer idempotencyTtlSeconds = 0;

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L, 2001L, "ALL", Instant.now(), null, null, null, null, null, true, null,
              "IGNORE", null, "Idempotency-Key", idempotencyTtlSeconds);

      // Then: 应该成功创建
      assertThat(httpConfig.idempotencyTtlSeconds()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该处理包含复杂 JSON 的 defaultHeadersJson")
    void shouldHandleComplexJsonInDefaultHeaders() {
      // Given: 复杂的 JSON 结构
      String defaultHeadersJson =
          "{\"User-Agent\":\"Patra/2.0\",\"Accept\":\"application/json\",\"X-Custom\":{\"nested\":\"value\"}}";

      // When: 创建 HttpConfig
      HttpConfig httpConfig =
          new HttpConfig(
              1001L,
              2001L,
              "ALL",
              Instant.now(),
              null,
              defaultHeadersJson,
              null,
              null,
              null,
              true,
              null,
              "IGNORE",
              null,
              null,
              null);

      // Then: 应该成功创建
      assertThat(httpConfig.defaultHeadersJson()).isEqualTo(defaultHeadersJson);
    }
  }
}
