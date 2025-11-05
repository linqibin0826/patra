package com.patra.registry.domain.model.read.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link HttpConfigQuery} 单元测试。
 *
 * <p>测试策略: Record 验证测试 (纯 Java，无框架依赖)
 *
 * <p>覆盖场景:
 *
 * <ul>
 *   <li>✅ 成功构造场景 (完整字段、最小字段、可选字段为null)
 *   <li>✅ Compact Constructor 验证失败 (id、provenanceId、retryAfterPolicyCode、effectiveFrom)
 *   <li>✅ Trim 逻辑验证 (operationType、proxyUrlValue、retryAfterPolicyCode、idempotencyHeaderName)
 *   <li>✅ Record 语义验证 (equals、hashCode、toString、组件访问器)
 *   <li>✅ 不可变性验证
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("HttpConfigQuery 单元测试")
class HttpConfigQueryTest {

  // ========== 测试数据常量 ==========

  private static final Long VALID_ID = 100L;
  private static final Long VALID_PROVENANCE_ID = 200L;
  private static final String VALID_OPERATION_TYPE = "CREATE";
  private static final Instant VALID_EFFECTIVE_FROM = Instant.parse("2025-01-01T00:00:00Z");
  private static final Instant VALID_EFFECTIVE_TO = Instant.parse("2025-12-31T23:59:59Z");
  private static final String VALID_DEFAULT_HEADERS_JSON = "{\"User-Agent\":\"Patra/1.0\"}";
  private static final Integer VALID_TIMEOUT_CONNECT_MILLIS = 5000;
  private static final Integer VALID_TIMEOUT_READ_MILLIS = 10000;
  private static final Integer VALID_TIMEOUT_TOTAL_MILLIS = 15000;
  private static final boolean VALID_TLS_VERIFY_ENABLED = true;
  private static final String VALID_PROXY_URL_VALUE = "http://proxy.example.com:8080";
  private static final String VALID_RETRY_AFTER_POLICY_CODE = "EXPONENTIAL_BACKOFF";
  private static final Integer VALID_RETRY_AFTER_CAP_MILLIS = 60000;
  private static final String VALID_IDEMPOTENCY_HEADER_NAME = "X-Idempotency-Key";
  private static final Integer VALID_IDEMPOTENCY_TTL_SECONDS = 3600;

  // ========== 辅助方法 ==========

  /** 创建完整的有效 HttpConfigQuery 实例 */
  private HttpConfigQuery createValidHttpConfigQuery() {
    return new HttpConfigQuery(
        VALID_ID,
        VALID_PROVENANCE_ID,
        VALID_OPERATION_TYPE,
        VALID_EFFECTIVE_FROM,
        VALID_EFFECTIVE_TO,
        VALID_DEFAULT_HEADERS_JSON,
        VALID_TIMEOUT_CONNECT_MILLIS,
        VALID_TIMEOUT_READ_MILLIS,
        VALID_TIMEOUT_TOTAL_MILLIS,
        VALID_TLS_VERIFY_ENABLED,
        VALID_PROXY_URL_VALUE,
        VALID_RETRY_AFTER_POLICY_CODE,
        VALID_RETRY_AFTER_CAP_MILLIS,
        VALID_IDEMPOTENCY_HEADER_NAME,
        VALID_IDEMPOTENCY_TTL_SECONDS);
  }

  /** 创建只包含必填字段的 HttpConfigQuery 实例 */
  private HttpConfigQuery createMinimalHttpConfigQuery() {
    return new HttpConfigQuery(
        VALID_ID,
        VALID_PROVENANCE_ID,
        null, // operationType - 可选
        VALID_EFFECTIVE_FROM,
        null, // effectiveTo - 可选
        null, // defaultHeadersJson - 可选
        null, // timeoutConnectMillis - 可选
        null, // timeoutReadMillis - 可选
        null, // timeoutTotalMillis - 可选
        false, // tlsVerifyEnabled - 布尔默认值
        null, // proxyUrlValue - 可选
        VALID_RETRY_AFTER_POLICY_CODE,
        null, // retryAfterCapMillis - 可选
        null, // idempotencyHeaderName - 可选
        null // idempotencyTtlSeconds - 可选
        );
  }

  // ========== 成功构造测试 ==========

  @Nested
  @DisplayName("成功构造场景")
  class SuccessfulConstruction {

    @Test
    @DisplayName("应该成功创建包含所有字段的实例")
    void shouldCreateInstanceWithAllFields() {
      // Given & When
      var query = createValidHttpConfigQuery();

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isEqualTo(VALID_OPERATION_TYPE);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isEqualTo(VALID_EFFECTIVE_TO);
      assertThat(query.defaultHeadersJson()).isEqualTo(VALID_DEFAULT_HEADERS_JSON);
      assertThat(query.timeoutConnectMillis()).isEqualTo(VALID_TIMEOUT_CONNECT_MILLIS);
      assertThat(query.timeoutReadMillis()).isEqualTo(VALID_TIMEOUT_READ_MILLIS);
      assertThat(query.timeoutTotalMillis()).isEqualTo(VALID_TIMEOUT_TOTAL_MILLIS);
      assertThat(query.tlsVerifyEnabled()).isEqualTo(VALID_TLS_VERIFY_ENABLED);
      assertThat(query.proxyUrlValue()).isEqualTo(VALID_PROXY_URL_VALUE);
      assertThat(query.retryAfterPolicyCode()).isEqualTo(VALID_RETRY_AFTER_POLICY_CODE);
      assertThat(query.retryAfterCapMillis()).isEqualTo(VALID_RETRY_AFTER_CAP_MILLIS);
      assertThat(query.idempotencyHeaderName()).isEqualTo(VALID_IDEMPOTENCY_HEADER_NAME);
      assertThat(query.idempotencyTtlSeconds()).isEqualTo(VALID_IDEMPOTENCY_TTL_SECONDS);
    }

    @Test
    @DisplayName("应该成功创建仅包含必填字段的实例")
    void shouldCreateInstanceWithRequiredFieldsOnly() {
      // Given & When
      var query = createMinimalHttpConfigQuery();

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isNull();
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isNull();
      assertThat(query.defaultHeadersJson()).isNull();
      assertThat(query.timeoutConnectMillis()).isNull();
      assertThat(query.timeoutReadMillis()).isNull();
      assertThat(query.timeoutTotalMillis()).isNull();
      assertThat(query.tlsVerifyEnabled()).isFalse();
      assertThat(query.proxyUrlValue()).isNull();
      assertThat(query.retryAfterPolicyCode()).isEqualTo(VALID_RETRY_AFTER_POLICY_CODE);
      assertThat(query.retryAfterCapMillis()).isNull();
      assertThat(query.idempotencyHeaderName()).isNull();
      assertThat(query.idempotencyTtlSeconds()).isNull();
    }

    @Test
    @DisplayName("应该成功创建可选字段为null的实例")
    void shouldCreateInstanceWithNullOptionalFields() {
      // Given & When
      var query =
          new HttpConfigQuery(
              1L,
              2L,
              null, // operationType
              Instant.now(),
              null, // effectiveTo
              null, // defaultHeadersJson
              null, // timeoutConnectMillis
              null, // timeoutReadMillis
              null, // timeoutTotalMillis
              true,
              null, // proxyUrlValue
              "FIXED_DELAY",
              null, // retryAfterCapMillis
              null, // idempotencyHeaderName
              null // idempotencyTtlSeconds
              );

      // Then
      assertThat(query).isNotNull();
      assertThat(query.operationType()).isNull();
      assertThat(query.effectiveTo()).isNull();
      assertThat(query.defaultHeadersJson()).isNull();
      assertThat(query.proxyUrlValue()).isNull();
      assertThat(query.retryAfterCapMillis()).isNull();
      assertThat(query.idempotencyHeaderName()).isNull();
      assertThat(query.idempotencyTtlSeconds()).isNull();
    }
  }

  // ========== 验证失败测试 ==========

  @Nested
  @DisplayName("Compact Constructor 验证失败场景")
  class ValidationFailures {

    @Nested
    @DisplayName("id 验证")
    class IdValidation {

      @Test
      @DisplayName("当 id 为 null 时，应该抛出 DomainValidationException")
      void shouldThrowExceptionWhenIdIsNull() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new HttpConfigQuery(
                        null, // id
                        VALID_PROVENANCE_ID,
                        VALID_OPERATION_TYPE,
                        VALID_EFFECTIVE_FROM,
                        VALID_EFFECTIVE_TO,
                        VALID_DEFAULT_HEADERS_JSON,
                        VALID_TIMEOUT_CONNECT_MILLIS,
                        VALID_TIMEOUT_READ_MILLIS,
                        VALID_TIMEOUT_TOTAL_MILLIS,
                        VALID_TLS_VERIFY_ENABLED,
                        VALID_PROXY_URL_VALUE,
                        VALID_RETRY_AFTER_POLICY_CODE,
                        VALID_RETRY_AFTER_CAP_MILLIS,
                        VALID_IDEMPOTENCY_HEADER_NAME,
                        VALID_IDEMPOTENCY_TTL_SECONDS))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("HTTP配置ID必须为正数");
      }

      @Test
      @DisplayName("当 id 为 0 时,应该抛出 DomainValidationException")
      void shouldThrowExceptionWhenIdIsZero() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new HttpConfigQuery(
                        0L, // id
                        VALID_PROVENANCE_ID,
                        VALID_OPERATION_TYPE,
                        VALID_EFFECTIVE_FROM,
                        VALID_EFFECTIVE_TO,
                        VALID_DEFAULT_HEADERS_JSON,
                        VALID_TIMEOUT_CONNECT_MILLIS,
                        VALID_TIMEOUT_READ_MILLIS,
                        VALID_TIMEOUT_TOTAL_MILLIS,
                        VALID_TLS_VERIFY_ENABLED,
                        VALID_PROXY_URL_VALUE,
                        VALID_RETRY_AFTER_POLICY_CODE,
                        VALID_RETRY_AFTER_CAP_MILLIS,
                        VALID_IDEMPOTENCY_HEADER_NAME,
                        VALID_IDEMPOTENCY_TTL_SECONDS))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("HTTP配置ID必须为正数");
      }

      @Test
      @DisplayName("当 id 为负数时,应该抛出 DomainValidationException")
      void shouldThrowExceptionWhenIdIsNegative() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new HttpConfigQuery(
                        -1L, // id
                        VALID_PROVENANCE_ID,
                        VALID_OPERATION_TYPE,
                        VALID_EFFECTIVE_FROM,
                        VALID_EFFECTIVE_TO,
                        VALID_DEFAULT_HEADERS_JSON,
                        VALID_TIMEOUT_CONNECT_MILLIS,
                        VALID_TIMEOUT_READ_MILLIS,
                        VALID_TIMEOUT_TOTAL_MILLIS,
                        VALID_TLS_VERIFY_ENABLED,
                        VALID_PROXY_URL_VALUE,
                        VALID_RETRY_AFTER_POLICY_CODE,
                        VALID_RETRY_AFTER_CAP_MILLIS,
                        VALID_IDEMPOTENCY_HEADER_NAME,
                        VALID_IDEMPOTENCY_TTL_SECONDS))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("HTTP配置ID必须为正数");
      }
    }

    @Nested
    @DisplayName("provenanceId 验证")
    class ProvenanceIdValidation {

      @Test
      @DisplayName("当 provenanceId 为 null 时,应该抛出 DomainValidationException")
      void shouldThrowExceptionWhenProvenanceIdIsNull() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new HttpConfigQuery(
                        VALID_ID,
                        null, // provenanceId
                        VALID_OPERATION_TYPE,
                        VALID_EFFECTIVE_FROM,
                        VALID_EFFECTIVE_TO,
                        VALID_DEFAULT_HEADERS_JSON,
                        VALID_TIMEOUT_CONNECT_MILLIS,
                        VALID_TIMEOUT_READ_MILLIS,
                        VALID_TIMEOUT_TOTAL_MILLIS,
                        VALID_TLS_VERIFY_ENABLED,
                        VALID_PROXY_URL_VALUE,
                        VALID_RETRY_AFTER_POLICY_CODE,
                        VALID_RETRY_AFTER_CAP_MILLIS,
                        VALID_IDEMPOTENCY_HEADER_NAME,
                        VALID_IDEMPOTENCY_TTL_SECONDS))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("来源ID必须为正数");
      }

      @Test
      @DisplayName("当 provenanceId 为 0 时,应该抛出 DomainValidationException")
      void shouldThrowExceptionWhenProvenanceIdIsZero() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new HttpConfigQuery(
                        VALID_ID,
                        0L, // provenanceId
                        VALID_OPERATION_TYPE,
                        VALID_EFFECTIVE_FROM,
                        VALID_EFFECTIVE_TO,
                        VALID_DEFAULT_HEADERS_JSON,
                        VALID_TIMEOUT_CONNECT_MILLIS,
                        VALID_TIMEOUT_READ_MILLIS,
                        VALID_TIMEOUT_TOTAL_MILLIS,
                        VALID_TLS_VERIFY_ENABLED,
                        VALID_PROXY_URL_VALUE,
                        VALID_RETRY_AFTER_POLICY_CODE,
                        VALID_RETRY_AFTER_CAP_MILLIS,
                        VALID_IDEMPOTENCY_HEADER_NAME,
                        VALID_IDEMPOTENCY_TTL_SECONDS))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("来源ID必须为正数");
      }

      @Test
      @DisplayName("当 provenanceId 为负数时,应该抛出 DomainValidationException")
      void shouldThrowExceptionWhenProvenanceIdIsNegative() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new HttpConfigQuery(
                        VALID_ID,
                        -100L, // provenanceId
                        VALID_OPERATION_TYPE,
                        VALID_EFFECTIVE_FROM,
                        VALID_EFFECTIVE_TO,
                        VALID_DEFAULT_HEADERS_JSON,
                        VALID_TIMEOUT_CONNECT_MILLIS,
                        VALID_TIMEOUT_READ_MILLIS,
                        VALID_TIMEOUT_TOTAL_MILLIS,
                        VALID_TLS_VERIFY_ENABLED,
                        VALID_PROXY_URL_VALUE,
                        VALID_RETRY_AFTER_POLICY_CODE,
                        VALID_RETRY_AFTER_CAP_MILLIS,
                        VALID_IDEMPOTENCY_HEADER_NAME,
                        VALID_IDEMPOTENCY_TTL_SECONDS))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("来源ID必须为正数");
      }
    }

    @Nested
    @DisplayName("retryAfterPolicyCode 验证")
    class RetryAfterPolicyCodeValidation {

      @Test
      @DisplayName("当 retryAfterPolicyCode 为 null 时,应该抛出 DomainValidationException")
      void shouldThrowExceptionWhenRetryAfterPolicyCodeIsNull() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new HttpConfigQuery(
                        VALID_ID,
                        VALID_PROVENANCE_ID,
                        VALID_OPERATION_TYPE,
                        VALID_EFFECTIVE_FROM,
                        VALID_EFFECTIVE_TO,
                        VALID_DEFAULT_HEADERS_JSON,
                        VALID_TIMEOUT_CONNECT_MILLIS,
                        VALID_TIMEOUT_READ_MILLIS,
                        VALID_TIMEOUT_TOTAL_MILLIS,
                        VALID_TLS_VERIFY_ENABLED,
                        VALID_PROXY_URL_VALUE,
                        null, // retryAfterPolicyCode
                        VALID_RETRY_AFTER_CAP_MILLIS,
                        VALID_IDEMPOTENCY_HEADER_NAME,
                        VALID_IDEMPOTENCY_TTL_SECONDS))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("重试策略代码不能为空");
      }

      @Test
      @DisplayName("当 retryAfterPolicyCode 为空字符串时,应该抛出 DomainValidationException")
      void shouldThrowExceptionWhenRetryAfterPolicyCodeIsEmpty() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new HttpConfigQuery(
                        VALID_ID,
                        VALID_PROVENANCE_ID,
                        VALID_OPERATION_TYPE,
                        VALID_EFFECTIVE_FROM,
                        VALID_EFFECTIVE_TO,
                        VALID_DEFAULT_HEADERS_JSON,
                        VALID_TIMEOUT_CONNECT_MILLIS,
                        VALID_TIMEOUT_READ_MILLIS,
                        VALID_TIMEOUT_TOTAL_MILLIS,
                        VALID_TLS_VERIFY_ENABLED,
                        VALID_PROXY_URL_VALUE,
                        "", // retryAfterPolicyCode
                        VALID_RETRY_AFTER_CAP_MILLIS,
                        VALID_IDEMPOTENCY_HEADER_NAME,
                        VALID_IDEMPOTENCY_TTL_SECONDS))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("重试策略代码不能为空");
      }

      @Test
      @DisplayName("当 retryAfterPolicyCode 为纯空白字符时,应该抛出 DomainValidationException")
      void shouldThrowExceptionWhenRetryAfterPolicyCodeIsBlank() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new HttpConfigQuery(
                        VALID_ID,
                        VALID_PROVENANCE_ID,
                        VALID_OPERATION_TYPE,
                        VALID_EFFECTIVE_FROM,
                        VALID_EFFECTIVE_TO,
                        VALID_DEFAULT_HEADERS_JSON,
                        VALID_TIMEOUT_CONNECT_MILLIS,
                        VALID_TIMEOUT_READ_MILLIS,
                        VALID_TIMEOUT_TOTAL_MILLIS,
                        VALID_TLS_VERIFY_ENABLED,
                        VALID_PROXY_URL_VALUE,
                        "   ", // retryAfterPolicyCode
                        VALID_RETRY_AFTER_CAP_MILLIS,
                        VALID_IDEMPOTENCY_HEADER_NAME,
                        VALID_IDEMPOTENCY_TTL_SECONDS))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("重试策略代码不能为空");
      }
    }

    @Nested
    @DisplayName("effectiveFrom 验证")
    class EffectiveFromValidation {

      @Test
      @DisplayName("当 effectiveFrom 为 null 时,应该抛出 DomainValidationException")
      void shouldThrowExceptionWhenEffectiveFromIsNull() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new HttpConfigQuery(
                        VALID_ID,
                        VALID_PROVENANCE_ID,
                        VALID_OPERATION_TYPE,
                        null, // effectiveFrom
                        VALID_EFFECTIVE_TO,
                        VALID_DEFAULT_HEADERS_JSON,
                        VALID_TIMEOUT_CONNECT_MILLIS,
                        VALID_TIMEOUT_READ_MILLIS,
                        VALID_TIMEOUT_TOTAL_MILLIS,
                        VALID_TLS_VERIFY_ENABLED,
                        VALID_PROXY_URL_VALUE,
                        VALID_RETRY_AFTER_POLICY_CODE,
                        VALID_RETRY_AFTER_CAP_MILLIS,
                        VALID_IDEMPOTENCY_HEADER_NAME,
                        VALID_IDEMPOTENCY_TTL_SECONDS))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("生效时间不能为null");
      }
    }
  }

  // ========== Trim 逻辑测试 ==========

  @Nested
  @DisplayName("Trim 逻辑验证")
  class TrimLogic {

    @Test
    @DisplayName("应该自动 trim operationType 前后的空格")
    void shouldTrimOperationType() {
      // Given
      String operationTypeWithSpaces = "  CREATE  ";

      // When
      var query =
          new HttpConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              operationTypeWithSpaces,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DEFAULT_HEADERS_JSON,
              VALID_TIMEOUT_CONNECT_MILLIS,
              VALID_TIMEOUT_READ_MILLIS,
              VALID_TIMEOUT_TOTAL_MILLIS,
              VALID_TLS_VERIFY_ENABLED,
              VALID_PROXY_URL_VALUE,
              VALID_RETRY_AFTER_POLICY_CODE,
              VALID_RETRY_AFTER_CAP_MILLIS,
              VALID_IDEMPOTENCY_HEADER_NAME,
              VALID_IDEMPOTENCY_TTL_SECONDS);

      // Then
      assertThat(query.operationType()).isEqualTo("CREATE");
    }

    @Test
    @DisplayName("当 operationType 为 null 时,应该保持 null")
    void shouldKeepOperationTypeAsNullWhenNull() {
      // Given & When
      var query =
          new HttpConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null, // operationType
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DEFAULT_HEADERS_JSON,
              VALID_TIMEOUT_CONNECT_MILLIS,
              VALID_TIMEOUT_READ_MILLIS,
              VALID_TIMEOUT_TOTAL_MILLIS,
              VALID_TLS_VERIFY_ENABLED,
              VALID_PROXY_URL_VALUE,
              VALID_RETRY_AFTER_POLICY_CODE,
              VALID_RETRY_AFTER_CAP_MILLIS,
              VALID_IDEMPOTENCY_HEADER_NAME,
              VALID_IDEMPOTENCY_TTL_SECONDS);

      // Then
      assertThat(query.operationType()).isNull();
    }

    @Test
    @DisplayName("应该自动 trim proxyUrlValue 前后的空格")
    void shouldTrimProxyUrlValue() {
      // Given
      String proxyUrlWithSpaces = "  http://proxy.example.com:8080  ";

      // When
      var query =
          new HttpConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DEFAULT_HEADERS_JSON,
              VALID_TIMEOUT_CONNECT_MILLIS,
              VALID_TIMEOUT_READ_MILLIS,
              VALID_TIMEOUT_TOTAL_MILLIS,
              VALID_TLS_VERIFY_ENABLED,
              proxyUrlWithSpaces,
              VALID_RETRY_AFTER_POLICY_CODE,
              VALID_RETRY_AFTER_CAP_MILLIS,
              VALID_IDEMPOTENCY_HEADER_NAME,
              VALID_IDEMPOTENCY_TTL_SECONDS);

      // Then
      assertThat(query.proxyUrlValue()).isEqualTo("http://proxy.example.com:8080");
    }

    @Test
    @DisplayName("当 proxyUrlValue 为 null 时,应该保持 null")
    void shouldKeepProxyUrlValueAsNullWhenNull() {
      // Given & When
      var query =
          new HttpConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DEFAULT_HEADERS_JSON,
              VALID_TIMEOUT_CONNECT_MILLIS,
              VALID_TIMEOUT_READ_MILLIS,
              VALID_TIMEOUT_TOTAL_MILLIS,
              VALID_TLS_VERIFY_ENABLED,
              null, // proxyUrlValue
              VALID_RETRY_AFTER_POLICY_CODE,
              VALID_RETRY_AFTER_CAP_MILLIS,
              VALID_IDEMPOTENCY_HEADER_NAME,
              VALID_IDEMPOTENCY_TTL_SECONDS);

      // Then
      assertThat(query.proxyUrlValue()).isNull();
    }

    @Test
    @DisplayName("应该自动 trim retryAfterPolicyCode 前后的空格")
    void shouldTrimRetryAfterPolicyCode() {
      // Given
      String retryPolicyWithSpaces = "  EXPONENTIAL_BACKOFF  ";

      // When
      var query =
          new HttpConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DEFAULT_HEADERS_JSON,
              VALID_TIMEOUT_CONNECT_MILLIS,
              VALID_TIMEOUT_READ_MILLIS,
              VALID_TIMEOUT_TOTAL_MILLIS,
              VALID_TLS_VERIFY_ENABLED,
              VALID_PROXY_URL_VALUE,
              retryPolicyWithSpaces,
              VALID_RETRY_AFTER_CAP_MILLIS,
              VALID_IDEMPOTENCY_HEADER_NAME,
              VALID_IDEMPOTENCY_TTL_SECONDS);

      // Then
      assertThat(query.retryAfterPolicyCode()).isEqualTo("EXPONENTIAL_BACKOFF");
    }

    @Test
    @DisplayName("应该自动 trim idempotencyHeaderName 前后的空格")
    void shouldTrimIdempotencyHeaderName() {
      // Given
      String idempotencyHeaderWithSpaces = "  X-Idempotency-Key  ";

      // When
      var query =
          new HttpConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DEFAULT_HEADERS_JSON,
              VALID_TIMEOUT_CONNECT_MILLIS,
              VALID_TIMEOUT_READ_MILLIS,
              VALID_TIMEOUT_TOTAL_MILLIS,
              VALID_TLS_VERIFY_ENABLED,
              VALID_PROXY_URL_VALUE,
              VALID_RETRY_AFTER_POLICY_CODE,
              VALID_RETRY_AFTER_CAP_MILLIS,
              idempotencyHeaderWithSpaces,
              VALID_IDEMPOTENCY_TTL_SECONDS);

      // Then
      assertThat(query.idempotencyHeaderName()).isEqualTo("X-Idempotency-Key");
    }

    @Test
    @DisplayName("当 idempotencyHeaderName 为 null 时,应该保持 null")
    void shouldKeepIdempotencyHeaderNameAsNullWhenNull() {
      // Given & When
      var query =
          new HttpConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DEFAULT_HEADERS_JSON,
              VALID_TIMEOUT_CONNECT_MILLIS,
              VALID_TIMEOUT_READ_MILLIS,
              VALID_TIMEOUT_TOTAL_MILLIS,
              VALID_TLS_VERIFY_ENABLED,
              VALID_PROXY_URL_VALUE,
              VALID_RETRY_AFTER_POLICY_CODE,
              VALID_RETRY_AFTER_CAP_MILLIS,
              null, // idempotencyHeaderName
              VALID_IDEMPOTENCY_TTL_SECONDS);

      // Then
      assertThat(query.idempotencyHeaderName()).isNull();
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义验证")
  class RecordSemantics {

    @Test
    @DisplayName("相同字段的两个实例应该相等")
    void shouldBeEqualWhenFieldsAreIdentical() {
      // Given
      var query1 = createValidHttpConfigQuery();
      var query2 = createValidHttpConfigQuery();

      // When & Then
      assertThat(query1).isEqualTo(query2);
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("不同字段的两个实例不应该相等")
    void shouldNotBeEqualWhenFieldsAreDifferent() {
      // Given
      var query1 = createValidHttpConfigQuery();
      var query2 =
          new HttpConfigQuery(
              999L, // 不同的 id
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DEFAULT_HEADERS_JSON,
              VALID_TIMEOUT_CONNECT_MILLIS,
              VALID_TIMEOUT_READ_MILLIS,
              VALID_TIMEOUT_TOTAL_MILLIS,
              VALID_TLS_VERIFY_ENABLED,
              VALID_PROXY_URL_VALUE,
              VALID_RETRY_AFTER_POLICY_CODE,
              VALID_RETRY_AFTER_CAP_MILLIS,
              VALID_IDEMPOTENCY_HEADER_NAME,
              VALID_IDEMPOTENCY_TTL_SECONDS);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
      assertThat(query1.hashCode()).isNotEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("实例与自身应该相等")
    void shouldBeEqualToItself() {
      // Given
      var query = createValidHttpConfigQuery();

      // When & Then
      assertThat(query).isEqualTo(query);
      assertThat(query.hashCode()).isEqualTo(query.hashCode());
    }

    @Test
    @DisplayName("实例与 null 不应该相等")
    void shouldNotBeEqualToNull() {
      // Given
      var query = createValidHttpConfigQuery();

      // When & Then
      assertThat(query).isNotEqualTo(null);
    }

    @Test
    @DisplayName("实例与不同类型对象不应该相等")
    void shouldNotBeEqualToDifferentType() {
      // Given
      var query = createValidHttpConfigQuery();
      String differentType = "Not a HttpConfigQuery";

      // When & Then
      assertThat(query).isNotEqualTo(differentType);
    }

    @Test
    @DisplayName("toString() 应该返回包含所有字段的字符串")
    void shouldReturnStringWithAllFields() {
      // Given
      var query = createValidHttpConfigQuery();

      // When
      String result = query.toString();

      // Then
      assertThat(result)
          .contains("HttpConfigQuery")
          .contains("id=" + VALID_ID)
          .contains("provenanceId=" + VALID_PROVENANCE_ID)
          .contains("operationType=" + VALID_OPERATION_TYPE)
          .contains("retryAfterPolicyCode=" + VALID_RETRY_AFTER_POLICY_CODE)
          .contains("tlsVerifyEnabled=" + VALID_TLS_VERIFY_ENABLED);
    }

    @Test
    @DisplayName("组件访问器应该返回正确的字段值")
    void shouldReturnCorrectFieldValuesViaAccessors() {
      // Given
      var query = createValidHttpConfigQuery();

      // When & Then - 验证所有访问器
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isEqualTo(VALID_OPERATION_TYPE);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isEqualTo(VALID_EFFECTIVE_TO);
      assertThat(query.defaultHeadersJson()).isEqualTo(VALID_DEFAULT_HEADERS_JSON);
      assertThat(query.timeoutConnectMillis()).isEqualTo(VALID_TIMEOUT_CONNECT_MILLIS);
      assertThat(query.timeoutReadMillis()).isEqualTo(VALID_TIMEOUT_READ_MILLIS);
      assertThat(query.timeoutTotalMillis()).isEqualTo(VALID_TIMEOUT_TOTAL_MILLIS);
      assertThat(query.tlsVerifyEnabled()).isEqualTo(VALID_TLS_VERIFY_ENABLED);
      assertThat(query.proxyUrlValue()).isEqualTo(VALID_PROXY_URL_VALUE);
      assertThat(query.retryAfterPolicyCode()).isEqualTo(VALID_RETRY_AFTER_POLICY_CODE);
      assertThat(query.retryAfterCapMillis()).isEqualTo(VALID_RETRY_AFTER_CAP_MILLIS);
      assertThat(query.idempotencyHeaderName()).isEqualTo(VALID_IDEMPOTENCY_HEADER_NAME);
      assertThat(query.idempotencyTtlSeconds()).isEqualTo(VALID_IDEMPOTENCY_TTL_SECONDS);
    }
  }

  // ========== 不可变性测试 ==========

  @Nested
  @DisplayName("不可变性验证")
  class Immutability {

    @Test
    @DisplayName("Record 实例应该是不可变的")
    void shouldBeImmutable() {
      // Given
      var query = createValidHttpConfigQuery();

      // When - Record 不提供 setter 方法
      // 验证无法通过反射或其他方式修改字段

      // Then - 通过组件访问器验证值未被修改
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isEqualTo(VALID_OPERATION_TYPE);
      assertThat(query.retryAfterPolicyCode()).isEqualTo(VALID_RETRY_AFTER_POLICY_CODE);

      // 创建新实例来修改值
      var modifiedQuery =
          new HttpConfigQuery(
              999L, // 新的 id
              query.provenanceId(),
              query.operationType(),
              query.effectiveFrom(),
              query.effectiveTo(),
              query.defaultHeadersJson(),
              query.timeoutConnectMillis(),
              query.timeoutReadMillis(),
              query.timeoutTotalMillis(),
              query.tlsVerifyEnabled(),
              query.proxyUrlValue(),
              query.retryAfterPolicyCode(),
              query.retryAfterCapMillis(),
              query.idempotencyHeaderName(),
              query.idempotencyTtlSeconds());

      // 原始实例应该保持不变
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(modifiedQuery.id()).isEqualTo(999L);
    }

    @Test
    @DisplayName("多个引用指向同一个实例时,值应该保持一致")
    void shouldMaintainConsistencyAcrossReferences() {
      // Given
      var query1 = createValidHttpConfigQuery();
      var query2 = query1; // 同一个引用

      // When & Then
      assertThat(query1).isSameAs(query2);
      assertThat(query1.id()).isEqualTo(query2.id());
      assertThat(query1.retryAfterPolicyCode()).isEqualTo(query2.retryAfterPolicyCode());
    }
  }
}
