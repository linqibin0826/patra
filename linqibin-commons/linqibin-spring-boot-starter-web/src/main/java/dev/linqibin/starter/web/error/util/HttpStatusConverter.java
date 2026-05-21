package dev.linqibin.starter.web.error.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/// 将整数状态码转换为 {@link HttpStatus} 的工具类,提供回退处理。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public final class HttpStatusConverter {

  /// 私有构造函数,防止实例化工具类。
  private HttpStatusConverter() {
    throw new UnsupportedOperationException("工具类不能被实例化");
  }

  /// 将整数 HTTP 状态码转换为 {@link HttpStatus},无效时默认为 500。
  ///
  /// @param statusCode HTTP 状态码(整数)
  /// @return 对应的 {@link HttpStatus},如果无效则返回 `INTERNAL_SERVER_ERROR`
  public static HttpStatus toHttpStatus(int statusCode) {
    try {
      return HttpStatus.valueOf(statusCode);
    } catch (IllegalArgumentException e) {
      log.warn("遇到无效的 HTTP 状态码 [{}],默认为 500 Internal Server Error", statusCode);
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }
  }
}
