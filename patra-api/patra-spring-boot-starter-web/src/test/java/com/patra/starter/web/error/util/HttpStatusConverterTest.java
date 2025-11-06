package com.patra.starter.web.error.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** HttpStatusConverter 单元测试。 */
@DisplayName("HttpStatusConverter 单元测试")
class HttpStatusConverterTest {

  @Test
  @DisplayName("应该将有效的状态码 200 转换为 HttpStatus.OK")
  void shouldConvertValidStatusCode200ToOk() {
    // Given: 有效的状态码 200
    int statusCode = 200;

    // When: 转换状态码
    HttpStatus result = HttpStatusConverter.toHttpStatus(statusCode);

    // Then: 验证转换结果
    assertThat(result).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("应该将有效的状态码 404 转换为 HttpStatus.NOT_FOUND")
  void shouldConvertValidStatusCode404ToNotFound() {
    // Given: 有效的状态码 404
    int statusCode = 404;

    // When: 转换状态码
    HttpStatus result = HttpStatusConverter.toHttpStatus(statusCode);

    // Then: 验证转换结果
    assertThat(result).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("应该将有效的状态码 500 转换为 HttpStatus.INTERNAL_SERVER_ERROR")
  void shouldConvertValidStatusCode500ToInternalServerError() {
    // Given: 有效的状态码 500
    int statusCode = 500;

    // When: 转换状态码
    HttpStatus result = HttpStatusConverter.toHttpStatus(statusCode);

    // Then: 验证转换结果
    assertThat(result).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("应该将无效的状态码转换为 HttpStatus.INTERNAL_SERVER_ERROR")
  void shouldConvertInvalidStatusCodeToInternalServerError() {
    // Given: 无效的状态码 999
    int statusCode = 999;

    // When: 转换状态码
    HttpStatus result = HttpStatusConverter.toHttpStatus(statusCode);

    // Then: 验证回退到 500
    assertThat(result).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("应该将无效的状态码 0 转换为 HttpStatus.INTERNAL_SERVER_ERROR")
  void shouldConvertInvalidStatusCodeZeroToInternalServerError() {
    // Given: 无效的状态码 0
    int statusCode = 0;

    // When: 转换状态码
    HttpStatus result = HttpStatusConverter.toHttpStatus(statusCode);

    // Then: 验证回退到 500
    assertThat(result).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("应该将负数状态码转换为 HttpStatus.INTERNAL_SERVER_ERROR")
  void shouldConvertNegativeStatusCodeToInternalServerError() {
    // Given: 负数状态码 -1
    int statusCode = -1;

    // When: 转换状态码
    HttpStatus result = HttpStatusConverter.toHttpStatus(statusCode);

    // Then: 验证回退到 500
    assertThat(result).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("应该将所有标准 HTTP 状态码正确转换")
  void shouldConvertAllStandardHttpStatusCodes() {
    // Given: 标准 HTTP 状态码
    assertThat(HttpStatusConverter.toHttpStatus(100)).isEqualTo(HttpStatus.CONTINUE);
    assertThat(HttpStatusConverter.toHttpStatus(201)).isEqualTo(HttpStatus.CREATED);
    assertThat(HttpStatusConverter.toHttpStatus(204)).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(HttpStatusConverter.toHttpStatus(301)).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
    assertThat(HttpStatusConverter.toHttpStatus(302)).isEqualTo(HttpStatus.FOUND);
    assertThat(HttpStatusConverter.toHttpStatus(400)).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(HttpStatusConverter.toHttpStatus(401)).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(HttpStatusConverter.toHttpStatus(403)).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(HttpStatusConverter.toHttpStatus(503)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  @DisplayName("应该阻止工具类实例化")
  void shouldPreventUtilityClassInstantiation() {
    // When & Then: 验证构造函数抛出异常
    assertThatThrownBy(
            () -> {
              var constructor = HttpStatusConverter.class.getDeclaredConstructor();
              constructor.setAccessible(true);
              constructor.newInstance();
            })
        .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
        .hasCauseInstanceOf(UnsupportedOperationException.class)
        .hasRootCauseMessage("工具类不能被实例化");
  }
}
