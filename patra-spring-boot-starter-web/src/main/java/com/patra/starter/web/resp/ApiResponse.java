package com.patra.starter.web.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Getter;

/**
 * Standard API response envelope providing a consistent success/failure contract with an associated
 * timestamp and optional payload.
 *
 * @param <T> type of the optional response body
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

  /** Create a successful response with the supplied payload. */
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, ResultCode.OK.getCode(), ResultCode.OK.getMessage(), data);
  }

  /**
   * Create a business failure response using the provided result code.
   *
   * @param code logical result code
   * @param message optional override for the default message
   */
  public static <T> ApiResponse<T> failure(ResultCode code, String message) {
    return new ApiResponse<>(
        false, code.getCode(), message == null ? code.getMessage() : message, null);
  }

  /**
   * Create an error response referencing the raw HTTP status code.
   *
   * @param code HTTP status code
   * @param message human-readable error description
   * @return response marked as unsuccessful
   */
  public static <T> ApiResponse<T> error(int code, String message) {
    return new ApiResponse<>(false, code, message, null);
  }
}
