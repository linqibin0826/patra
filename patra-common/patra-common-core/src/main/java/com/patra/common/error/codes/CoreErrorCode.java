package com.patra.common.error.codes;

import lombok.RequiredArgsConstructor;

/// 核心模块通用错误码。
///
/// 错误码格式: `CORE-{系列号}`
///
/// 系列号说明:
/// - 05xx: 服务器内部错误
///
/// @author linqibin
/// @since 0.1.0
@RequiredArgsConstructor
public enum CoreErrorCode implements ErrorCodeLike {

  /// CommandHandler 未找到。
  ///
  /// 当 CommandBus 无法找到与 Command 类型匹配的 Handler 时发生。
  /// 通常表示配置问题：忘记创建 Handler、Handler 未注册到 Spring 容器、
  /// 或 Command 类型泛型参数配置错误。
  COMMAND_HANDLER_NOT_FOUND("CORE-0500", 500);

  private final String code;
  private final int httpStatus;

  @Override
  public String code() {
    return code;
  }

  @Override
  public int httpStatus() {
    return httpStatus;
  }
}
