package dev.linqibin.commons.error.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// RemoteErrorHelper 单元测试
///
/// 测试策略：
///
/// - 测试各 HTTP 状态码判断方法（基于状态码和错误码后缀）
/// - 测试错误分类方法（客户端错误、服务器错误、可重试错误）
/// - 测试错误码匹配方法
/// - 测试边界条件
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("RemoteErrorHelper 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class RemoteErrorHelperTest {

  private static final String METHOD_KEY = "GET /api/test";

  /// 创建指定状态码的异常
  private RemoteCallException exceptionWithStatus(int status) {
    return new RemoteCallException(status, "Error", METHOD_KEY, null);
  }

  /// 创建指定错误码的异常
  private RemoteCallException exceptionWithErrorCode(String errorCode) {
    return new RemoteCallException(errorCode, 500, "Error", METHOD_KEY, null, null);
  }

  /// 创建同时具有状态码和错误码的异常
  private RemoteCallException exceptionWithStatusAndCode(int status, String errorCode) {
    return new RemoteCallException(errorCode, status, "Error", METHOD_KEY, null, null);
  }

  @Nested
  @DisplayName("isNotFound 测试")
  class IsNotFoundTests {

    @Test
    @DisplayName("HTTP 404 应返回 true")
    void shouldReturnTrueForHttp404() {
      var ex = exceptionWithStatus(404);
      assertThat(RemoteErrorHelper.isNotFound(ex)).isTrue();
    }

    @Test
    @DisplayName("错误码以 -0404 结尾应返回 true")
    void shouldReturnTrueForErrorCodeEndingWith0404() {
      var ex = exceptionWithErrorCode("USER-0404");
      assertThat(RemoteErrorHelper.isNotFound(ex)).isTrue();
    }

    @Test
    @DisplayName("非 404 状态码且无匹配错误码应返回 false")
    void shouldReturnFalseForNon404() {
      var ex = exceptionWithStatus(500);
      assertThat(RemoteErrorHelper.isNotFound(ex)).isFalse();
    }
  }

  @Nested
  @DisplayName("isConflict 测试")
  class IsConflictTests {

    @Test
    @DisplayName("HTTP 409 应返回 true")
    void shouldReturnTrueForHttp409() {
      var ex = exceptionWithStatus(409);
      assertThat(RemoteErrorHelper.isConflict(ex)).isTrue();
    }

    @Test
    @DisplayName("错误码以 -0409 结尾应返回 true")
    void shouldReturnTrueForErrorCodeEndingWith0409() {
      var ex = exceptionWithErrorCode("RESOURCE-0409");
      assertThat(RemoteErrorHelper.isConflict(ex)).isTrue();
    }

    @Test
    @DisplayName("非 409 状态码应返回 false")
    void shouldReturnFalseForNon409() {
      var ex = exceptionWithStatus(404);
      assertThat(RemoteErrorHelper.isConflict(ex)).isFalse();
    }
  }

  @Nested
  @DisplayName("isUnauthorized 测试")
  class IsUnauthorizedTests {

    @Test
    @DisplayName("HTTP 401 应返回 true")
    void shouldReturnTrueForHttp401() {
      var ex = exceptionWithStatus(401);
      assertThat(RemoteErrorHelper.isUnauthorized(ex)).isTrue();
    }

    @Test
    @DisplayName("错误码以 -0401 结尾应返回 true")
    void shouldReturnTrueForErrorCodeEndingWith0401() {
      var ex = exceptionWithErrorCode("AUTH-0401");
      assertThat(RemoteErrorHelper.isUnauthorized(ex)).isTrue();
    }

    @Test
    @DisplayName("非 401 状态码应返回 false")
    void shouldReturnFalseForNon401() {
      var ex = exceptionWithStatus(403);
      assertThat(RemoteErrorHelper.isUnauthorized(ex)).isFalse();
    }
  }

  @Nested
  @DisplayName("isForbidden 测试")
  class IsForbiddenTests {

    @Test
    @DisplayName("HTTP 403 应返回 true")
    void shouldReturnTrueForHttp403() {
      var ex = exceptionWithStatus(403);
      assertThat(RemoteErrorHelper.isForbidden(ex)).isTrue();
    }

    @Test
    @DisplayName("错误码以 -0403 结尾应返回 true")
    void shouldReturnTrueForErrorCodeEndingWith0403() {
      var ex = exceptionWithErrorCode("ACCESS-0403");
      assertThat(RemoteErrorHelper.isForbidden(ex)).isTrue();
    }

    @Test
    @DisplayName("非 403 状态码应返回 false")
    void shouldReturnFalseForNon403() {
      var ex = exceptionWithStatus(401);
      assertThat(RemoteErrorHelper.isForbidden(ex)).isFalse();
    }
  }

  @Nested
  @DisplayName("isUnprocessableEntity 测试")
  class IsUnprocessableEntityTests {

    @Test
    @DisplayName("HTTP 422 应返回 true")
    void shouldReturnTrueForHttp422() {
      var ex = exceptionWithStatus(422);
      assertThat(RemoteErrorHelper.isUnprocessableEntity(ex)).isTrue();
    }

    @Test
    @DisplayName("错误码以 -0422 结尾应返回 true")
    void shouldReturnTrueForErrorCodeEndingWith0422() {
      var ex = exceptionWithErrorCode("VALIDATION-0422");
      assertThat(RemoteErrorHelper.isUnprocessableEntity(ex)).isTrue();
    }

    @Test
    @DisplayName("非 422 状态码应返回 false")
    void shouldReturnFalseForNon422() {
      var ex = exceptionWithStatus(400);
      assertThat(RemoteErrorHelper.isUnprocessableEntity(ex)).isFalse();
    }
  }

  @Nested
  @DisplayName("isTooManyRequests 测试")
  class IsTooManyRequestsTests {

    @Test
    @DisplayName("HTTP 429 应返回 true")
    void shouldReturnTrueForHttp429() {
      var ex = exceptionWithStatus(429);
      assertThat(RemoteErrorHelper.isTooManyRequests(ex)).isTrue();
    }

    @Test
    @DisplayName("错误码以 -0429 结尾应返回 true")
    void shouldReturnTrueForErrorCodeEndingWith0429() {
      var ex = exceptionWithErrorCode("RATE-0429");
      assertThat(RemoteErrorHelper.isTooManyRequests(ex)).isTrue();
    }

    @Test
    @DisplayName("非 429 状态码应返回 false")
    void shouldReturnFalseForNon429() {
      var ex = exceptionWithStatus(400);
      assertThat(RemoteErrorHelper.isTooManyRequests(ex)).isFalse();
    }
  }

  @Nested
  @DisplayName("isClientError 测试")
  class IsClientErrorTests {

    @Test
    @DisplayName("400 应返回 true")
    void shouldReturnTrueFor400() {
      var ex = exceptionWithStatus(400);
      assertThat(RemoteErrorHelper.isClientError(ex)).isTrue();
    }

    @Test
    @DisplayName("499 应返回 true")
    void shouldReturnTrueFor499() {
      var ex = exceptionWithStatus(499);
      assertThat(RemoteErrorHelper.isClientError(ex)).isTrue();
    }

    @Test
    @DisplayName("500 应返回 false")
    void shouldReturnFalseFor500() {
      var ex = exceptionWithStatus(500);
      assertThat(RemoteErrorHelper.isClientError(ex)).isFalse();
    }

    @Test
    @DisplayName("399 应返回 false")
    void shouldReturnFalseFor399() {
      var ex = exceptionWithStatus(399);
      assertThat(RemoteErrorHelper.isClientError(ex)).isFalse();
    }
  }

  @Nested
  @DisplayName("isServerError 测试")
  class IsServerErrorTests {

    @Test
    @DisplayName("500 应返回 true")
    void shouldReturnTrueFor500() {
      var ex = exceptionWithStatus(500);
      assertThat(RemoteErrorHelper.isServerError(ex)).isTrue();
    }

    @Test
    @DisplayName("503 应返回 true")
    void shouldReturnTrueFor503() {
      var ex = exceptionWithStatus(503);
      assertThat(RemoteErrorHelper.isServerError(ex)).isTrue();
    }

    @Test
    @DisplayName("599 应返回 true")
    void shouldReturnTrueFor599() {
      var ex = exceptionWithStatus(599);
      assertThat(RemoteErrorHelper.isServerError(ex)).isTrue();
    }

    @Test
    @DisplayName("499 应返回 false")
    void shouldReturnFalseFor499() {
      var ex = exceptionWithStatus(499);
      assertThat(RemoteErrorHelper.isServerError(ex)).isFalse();
    }

    @Test
    @DisplayName("600 应返回 false")
    void shouldReturnFalseFor600() {
      var ex = exceptionWithStatus(600);
      assertThat(RemoteErrorHelper.isServerError(ex)).isFalse();
    }
  }

  @Nested
  @DisplayName("isRetryable 测试")
  class IsRetryableTests {

    @Test
    @DisplayName("5xx 错误应可重试")
    void shouldReturnTrueFor5xxErrors() {
      assertThat(RemoteErrorHelper.isRetryable(exceptionWithStatus(500))).isTrue();
      assertThat(RemoteErrorHelper.isRetryable(exceptionWithStatus(502))).isTrue();
      assertThat(RemoteErrorHelper.isRetryable(exceptionWithStatus(503))).isTrue();
      assertThat(RemoteErrorHelper.isRetryable(exceptionWithStatus(504))).isTrue();
    }

    @Test
    @DisplayName("429 Too Many Requests 应可重试")
    void shouldReturnTrueFor429() {
      var ex = exceptionWithStatus(429);
      assertThat(RemoteErrorHelper.isRetryable(ex)).isTrue();
    }

    @Test
    @DisplayName("408 Request Timeout 应可重试")
    void shouldReturnTrueFor408() {
      var ex = exceptionWithStatus(408);
      assertThat(RemoteErrorHelper.isRetryable(ex)).isTrue();
    }

    @Test
    @DisplayName("普通 4xx 错误不可重试")
    void shouldReturnFalseForRegular4xxErrors() {
      assertThat(RemoteErrorHelper.isRetryable(exceptionWithStatus(400))).isFalse();
      assertThat(RemoteErrorHelper.isRetryable(exceptionWithStatus(401))).isFalse();
      assertThat(RemoteErrorHelper.isRetryable(exceptionWithStatus(403))).isFalse();
      assertThat(RemoteErrorHelper.isRetryable(exceptionWithStatus(404))).isFalse();
      assertThat(RemoteErrorHelper.isRetryable(exceptionWithStatus(422))).isFalse();
    }
  }

  @Nested
  @DisplayName("is 方法测试")
  class IsTests {

    @Test
    @DisplayName("错误码匹配应返回 true")
    void shouldReturnTrueWhenErrorCodeMatches() {
      var ex = exceptionWithErrorCode("USER-0404");
      assertThat(RemoteErrorHelper.is(ex, "USER-0404")).isTrue();
    }

    @Test
    @DisplayName("错误码不匹配应返回 false")
    void shouldReturnFalseWhenErrorCodeDoesNotMatch() {
      var ex = exceptionWithErrorCode("USER-0404");
      assertThat(RemoteErrorHelper.is(ex, "USER-0409")).isFalse();
    }

    @Test
    @DisplayName("null 错误码参数应返回 false")
    void shouldReturnFalseForNullErrorCodeParam() {
      var ex = exceptionWithErrorCode("USER-0404");
      assertThat(RemoteErrorHelper.is(ex, null)).isFalse();
    }

    @Test
    @DisplayName("异常无错误码应返回 false")
    void shouldReturnFalseWhenExceptionHasNoErrorCode() {
      var ex = exceptionWithStatus(404);
      assertThat(RemoteErrorHelper.is(ex, "USER-0404")).isFalse();
    }
  }

  @Nested
  @DisplayName("isAnyOf 方法测试")
  class IsAnyOfTests {

    @Test
    @DisplayName("错误码匹配列表中任一项应返回 true")
    void shouldReturnTrueWhenErrorCodeMatchesAny() {
      var ex = exceptionWithErrorCode("USER-0404");
      assertThat(RemoteErrorHelper.isAnyOf(ex, "USER-0409", "USER-0404", "USER-0422")).isTrue();
    }

    @Test
    @DisplayName("错误码不匹配列表中任何项应返回 false")
    void shouldReturnFalseWhenErrorCodeMatchesNone() {
      var ex = exceptionWithErrorCode("USER-0404");
      assertThat(RemoteErrorHelper.isAnyOf(ex, "USER-0409", "USER-0422")).isFalse();
    }

    @Test
    @DisplayName("空数组应返回 false")
    void shouldReturnFalseForEmptyArray() {
      var ex = exceptionWithErrorCode("USER-0404");
      assertThat(RemoteErrorHelper.isAnyOf(ex)).isFalse();
    }

    @Test
    @DisplayName("null 数组应返回 false")
    void shouldReturnFalseForNullArray() {
      var ex = exceptionWithErrorCode("USER-0404");
      assertThat(RemoteErrorHelper.isAnyOf(ex, (String[]) null)).isFalse();
    }

    @Test
    @DisplayName("异常无错误码应返回 false")
    void shouldReturnFalseWhenExceptionHasNoErrorCode() {
      var ex = exceptionWithStatus(404);
      assertThat(RemoteErrorHelper.isAnyOf(ex, "USER-0404")).isFalse();
    }
  }

  @Nested
  @DisplayName("hasErrorCode 和 hasTraceId 测试")
  class HasMethodsTests {

    @Test
    @DisplayName("hasErrorCode - 代理到异常方法")
    void hasErrorCodeShouldDelegateToException() {
      var exWithCode = exceptionWithErrorCode("USER-0404");
      var exWithoutCode = exceptionWithStatus(404);

      assertThat(RemoteErrorHelper.hasErrorCode(exWithCode)).isTrue();
      assertThat(RemoteErrorHelper.hasErrorCode(exWithoutCode)).isFalse();
    }

    @Test
    @DisplayName("hasTraceId - 代理到异常方法")
    void hasTraceIdShouldDelegateToException() {
      var exWithTraceId = new RemoteCallException(404, "Error", METHOD_KEY, "trace-123");
      var exWithoutTraceId = new RemoteCallException(404, "Error", METHOD_KEY, null);

      assertThat(RemoteErrorHelper.hasTraceId(exWithTraceId)).isTrue();
      assertThat(RemoteErrorHelper.hasTraceId(exWithoutTraceId)).isFalse();
    }
  }

  @Nested
  @DisplayName("错误码后缀优先级测试")
  class ErrorCodeSuffixPriorityTests {

    @Test
    @DisplayName("状态码非 404 但错误码以 -0404 结尾，isNotFound 应返回 true")
    void errorCodeSuffixShouldTakePrecedenceForNotFound() {
      // HTTP 500 但错误码表示 NOT_FOUND
      var ex = exceptionWithStatusAndCode(500, "USER-0404");
      assertThat(RemoteErrorHelper.isNotFound(ex)).isTrue();
    }

    @Test
    @DisplayName("状态码非 409 但错误码以 -0409 结尾，isConflict 应返回 true")
    void errorCodeSuffixShouldTakePrecedenceForConflict() {
      var ex = exceptionWithStatusAndCode(500, "RESOURCE-0409");
      assertThat(RemoteErrorHelper.isConflict(ex)).isTrue();
    }
  }
}
