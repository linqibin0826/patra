package com.patra.starter.web.resp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 测试 {@link ApiResponse} 的工厂方法和字段值 */
@DisplayName("ApiResponse 单元测试")
class ApiResponseTest {

  @Test
  @DisplayName("ok() 应该创建成功响应并包含数据")
  void ok_shouldCreateSuccessResponseWithData() {
    // Arrange
    String testData = "test-data";
    Instant beforeCreate = Instant.now();

    // Act
    ApiResponse<String> response = ApiResponse.ok(testData);

    // Assert
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getCode()).isEqualTo(ResultCode.OK.getCode());
    assertThat(response.getMessage()).isEqualTo(ResultCode.OK.getMessage());
    assertThat(response.getData()).isEqualTo(testData);
    assertThat(response.getTimestamp()).isAfterOrEqualTo(beforeCreate);
    assertThat(response.getTimestamp()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  @DisplayName("ok() 使用 null 数据应该创建成功响应")
  void ok_withNullData_shouldCreateSuccessResponse() {
    // Act
    ApiResponse<Object> response = ApiResponse.ok(null);

    // Assert
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getCode()).isEqualTo(0);
    assertThat(response.getMessage()).isEqualTo("OK");
    assertThat(response.getData()).isNull();
  }

  @Test
  @DisplayName("failure() 使用默认消息应该创建失败响应")
  void failure_withDefaultMessage_shouldCreateFailureResponse() {
    // Arrange
    ResultCode code = ResultCode.BAD_REQUEST;

    // Act
    ApiResponse<Object> response = ApiResponse.failure(code, null);

    // Assert
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getCode()).isEqualTo(code.getCode());
    assertThat(response.getMessage()).isEqualTo(code.getMessage());
    assertThat(response.getData()).isNull();
    assertThat(response.getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("failure() 使用自定义消息应该覆盖默认消息")
  void failure_withCustomMessage_shouldOverrideDefaultMessage() {
    // Arrange
    ResultCode code = ResultCode.VALIDATION_ERROR;
    String customMessage = "自定义验证错误消息";

    // Act
    ApiResponse<Object> response = ApiResponse.failure(code, customMessage);

    // Assert
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getCode()).isEqualTo(code.getCode());
    assertThat(response.getMessage()).isEqualTo(customMessage);
    assertThat(response.getData()).isNull();
  }

  @Test
  @DisplayName("failure() 使用各种 ResultCode 应该正确映射代码和消息")
  void failure_withVariousResultCodes_shouldMapCorrectly() {
    // Test multiple ResultCode values
    ApiResponse<Object> notFound = ApiResponse.failure(ResultCode.NOT_FOUND, null);
    assertThat(notFound.getCode()).isEqualTo(1404);
    assertThat(notFound.getMessage()).isEqualTo("Not Found");

    ApiResponse<Object> unauthorized = ApiResponse.failure(ResultCode.UNAUTHORIZED, null);
    assertThat(unauthorized.getCode()).isEqualTo(2401);
    assertThat(unauthorized.getMessage()).isEqualTo("Unauthorized");

    ApiResponse<Object> internalError = ApiResponse.failure(ResultCode.INTERNAL_ERROR, null);
    assertThat(internalError.getCode()).isEqualTo(5500);
    assertThat(internalError.getMessage()).isEqualTo("Internal Server Error");
  }

  @Test
  @DisplayName("error() 应该创建错误响应并使用提供的 HTTP 代码")
  void error_shouldCreateErrorResponseWithHttpCode() {
    // Arrange
    int httpCode = 500;
    String errorMessage = "Internal server error occurred";

    // Act
    ApiResponse<Object> response = ApiResponse.error(httpCode, errorMessage);

    // Assert
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getCode()).isEqualTo(httpCode);
    assertThat(response.getMessage()).isEqualTo(errorMessage);
    assertThat(response.getData()).isNull();
    assertThat(response.getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("error() 使用各种 HTTP 状态码应该正确创建响应")
  void error_withVariousHttpCodes_shouldCreateResponses() {
    ApiResponse<Object> badRequest = ApiResponse.error(400, "Bad Request");
    assertThat(badRequest.getCode()).isEqualTo(400);
    assertThat(badRequest.isSuccess()).isFalse();

    ApiResponse<Object> notFound = ApiResponse.error(404, "Not Found");
    assertThat(notFound.getCode()).isEqualTo(404);
    assertThat(notFound.isSuccess()).isFalse();

    ApiResponse<Object> serverError = ApiResponse.error(503, "Service Unavailable");
    assertThat(serverError.getCode()).isEqualTo(503);
    assertThat(serverError.isSuccess()).isFalse();
  }

  @Test
  @DisplayName("timestamp 应该在每次创建响应时生成")
  void timestamp_shouldBeGeneratedOnEachCreation() throws InterruptedException {
    // Act
    ApiResponse<String> first = ApiResponse.ok("first");
    Thread.sleep(10); // 确保时间戳不同
    ApiResponse<String> second = ApiResponse.ok("second");

    // Assert
    assertThat(second.getTimestamp()).isAfter(first.getTimestamp());
  }

  @Test
  @DisplayName("不同工厂方法创建的响应应该都有 timestamp")
  void allFactoryMethods_shouldGenerateTimestamp() {
    ApiResponse<String> okResponse = ApiResponse.ok("data");
    ApiResponse<Object> failureResponse = ApiResponse.failure(ResultCode.BAD_REQUEST, null);
    ApiResponse<Object> errorResponse = ApiResponse.error(500, "error");

    assertThat(okResponse.getTimestamp()).isNotNull();
    assertThat(failureResponse.getTimestamp()).isNotNull();
    assertThat(errorResponse.getTimestamp()).isNotNull();
  }
}
