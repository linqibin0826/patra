package com.patra.common.error.codes;

/// 统一错误处理系统使用的结构化业务错误码契约。
///
/// 实现必须提供全局唯一标识符,以支持错误解析、映射和客户端处理。
///
/// @author linqibin
/// @since 0.1.0
public interface ErrorCodeLike {

  /// 返回规范的错误码字符串。
  ///
  /// 遵循共享命名模式(例如,`REG-0404` 或 `ING-1201`), 使响应对人类可读且易于编程解析。
  String code();

  /// 返回与此代码关联的 HTTP 状态码(100–599)。
  ///
  /// 用于呈现 HTTP 响应;其他传输协议可能忽略或覆盖此映射。
  int httpStatus();
}
