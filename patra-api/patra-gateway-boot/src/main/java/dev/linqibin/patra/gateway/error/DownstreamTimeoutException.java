package dev.linqibin.patra.gateway.error;

import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.commons.error.codes.ErrorCodeLike;

/// 下游在读超时内没有给出响应头。对外统一 504，文案固定，原始异常只留在原因链里。
public class DownstreamTimeoutException extends ApplicationException {

  /// 构造。
  ///
  /// @param errorCode 网关前缀下的 504 错误码
  /// @param cause 代理失败的原始异常
  public DownstreamTimeoutException(ErrorCodeLike errorCode, Throwable cause) {
    super(errorCode, "下游服务响应超时", cause);
  }
}
