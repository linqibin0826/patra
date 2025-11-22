package com.patra.starter.core.error.model;

import com.patra.common.error.codes.ErrorCodeLike;

/// 异常解析结果,组合业务错误码和 HTTP 状态码。
/// 
/// 该记录类封装了错误解析引擎的输出,提供统一的错误表示格式。
/// 
/// @param errorCode 解析后的业务错误码(永不为 `null`)
/// @param httpStatus 解析后的 HTTP 状态码(范围: 100–599)
/// @author Patra Team
/// @since 2.0
public record ErrorResolution(ErrorCodeLike errorCode, int httpStatus) {

  /// 验证构造函数参数。
/// 
/// @param errorCode 解析后的错误码(永不为 `null`)
/// @param httpStatus HTTP 状态码,范围必须在 100–599 之间
/// @throws IllegalArgumentException 如果错误码为 null 或 HTTP 状态码不在有效范围内
  public ErrorResolution {
    if (errorCode == null) {
      throw new IllegalArgumentException("错误码不能为 null");
    }
    if (httpStatus < 100 || httpStatus > 599) {
      throw new IllegalArgumentException("HTTP 状态码必须在 100 到 599 之间,实际值: " + httpStatus);
    }
  }
}
