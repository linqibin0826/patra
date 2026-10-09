package dev.linqibin.patra.gateway.error;

import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.commons.error.codes.ErrorCodeLike;

/// 网关到不了下游：连接被拒、连接超时、域名解析失败、找不到实例。对外统一 503，
/// 文案固定，原始异常只留在原因链里——它的消息带着下游实例的地址和内部路径，不能回给调用方。
public class DownstreamUnavailableException extends ApplicationException {

  /// 构造。
  ///
  /// @param errorCode 网关前缀下的 503 错误码
  /// @param cause 代理失败的原始异常
  public DownstreamUnavailableException(ErrorCodeLike errorCode, Throwable cause) {
    super(errorCode, "下游服务暂时不可用", cause);
  }
}
