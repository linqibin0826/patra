package com.patra.starter.web.resp;

import lombok.Getter;

/// 共享 API 响应辅助类使用的代表 Web 层结果代码。
@Getter
public enum ResultCode {
  OK(0, "OK"),
  BAD_REQUEST(1400, "Bad Request"),
  VALIDATION_ERROR(1401, "Validation Failed"),
  UNAUTHORIZED(2401, "Unauthorized"),
  FORBIDDEN(2403, "Forbidden"),
  NOT_FOUND(1404, "Not Found"),
  METHOD_NOT_ALLOWED(1405, "Method Not Allowed"),
  CONFLICT(3409, "Conflict"),
  TOO_MANY_REQUESTS(1429, "Too Many Requests"),
  INTERNAL_ERROR(5500, "Internal Server Error"),
  SERVICE_UNAVAILABLE(5503, "Service Unavailable");

  /// 暴露给客户端的数字代码。
  private final int code;

  /// 与代码关联的人类可读描述。
  private final String message;

  ResultCode(int code, String message) {
    this.code = code;
    this.message = message;
  }
}
