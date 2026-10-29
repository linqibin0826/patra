package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import jakarta.servlet.http.HttpServletRequest;

/// 请求的客户端 IP：`getRemoteAddr()` 按 {@link RequestOrigin} 的规则规范化，不是合法 IP 字面量就为 `null`。
///
/// 经受信转发链路，`getRemoteAddr()` 是门户转来的浏览器 IP（PAP-99）。它不是可信输入：门户可能转来
/// 浏览器谎报的值，tailnet 上直连 identity 也可伪造。所有用例共用这一个规范化之后的值（入库、限流键、
/// 安全通知），超长或非字面量的值不会让写库失败，也不能用来绕过按 IP 的限流。
public final class ClientIp {

  /// 只有静态方法。
  private ClientIp() {}

  /// 规范化之后的客户端 IP。
  ///
  /// @param request 原始请求
  /// @return IP 文本；不合法时为 `null`
  public static String from(HttpServletRequest request) {
    return RequestOrigin.of(request.getRemoteAddr(), null).ip();
  }
}
