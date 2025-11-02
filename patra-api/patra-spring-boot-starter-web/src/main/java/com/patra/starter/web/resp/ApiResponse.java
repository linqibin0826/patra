package com.patra.starter.web.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Getter;

/**
 * 标准 API 响应信封，提供一致的成功/失败契约，包含关联的时间戳和可选的载荷。
 *
 * @param <T> 可选响应体的类型
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private final boolean success;

  private final int code;

  private final String message;

  private final T data;

  private final Instant timestamp = Instant.now();

  private ApiResponse(boolean success, int code, String message, T data) {
    this.success = success;
    this.code = code;
    this.message = message;
    this.data = data;
  }

  /** 使用提供的数据创建成功响应。 */
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, ResultCode.OK.getCode(), ResultCode.OK.getMessage(), data);
  }

  /**
   * 使用提供的结果代码创建业务失败响应。
   *
   * @param code 逻辑结果代码
   * @param message 默认消息的可选覆盖
   */
  public static <T> ApiResponse<T> failure(ResultCode code, String message) {
    return new ApiResponse<>(
        false, code.getCode(), message == null ? code.getMessage() : message, null);
  }

  /**
   * 创建引用原始 HTTP 状态代码的错误响应。
   *
   * @param code HTTP 状态代码
   * @param message 人类可读的错误描述
   * @return 标记为失败的响应
   */
  public static <T> ApiResponse<T> error(int code, String message) {
    return new ApiResponse<>(false, code, message, null);
  }
}
