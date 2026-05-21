package dev.linqibin.patra.starter.provenance.common.config;

import java.util.Map;

/// HTTP 客户端配置记录
///
/// 定义访问 Provenance 数据源 API 时的 HTTP 客户端行为,包括默认请求头和各类超时设置。
/// 该配置会被应用到 Spring RestClient。
///
/// @param defaultHeaders 不可变的 HTTP 请求头映射,会附加到每个请求中
/// @param timeoutConnectMillis 连接建立超时时间(毫秒)
/// @param timeoutReadMillis 套接字读取超时时间(毫秒)
/// @param timeoutTotalMillis 请求总超时时间(毫秒)
/// @author linqibin
/// @since 0.1.0
public record HttpConfig(
    Map<String, String> defaultHeaders,
    Integer timeoutConnectMillis,
    Integer timeoutReadMillis,
    Integer timeoutTotalMillis) {

  /// 规范构造器,强制执行 HTTP 配置的验证规则。
  ///
  /// 验证规则:
  ///
  /// - defaultHeaders 为 null 时自动填充空 Map
  /// - defaultHeaders 自动转换为不可变 Map
  public HttpConfig {
    defaultHeaders = defaultHeaders == null ? Map.of() : Map.copyOf(defaultHeaders);
  }
}
