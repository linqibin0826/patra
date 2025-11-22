package com.patra.common.error.trait;

/// 异常的语义分类特征,用于在不同异常类型间实现一致的错误处理。
///
/// 这些特征被错误解析算法用于将异常映射到适当的 HTTP 状态码和错误响应。
///
/// @author linqibin
/// @since 0.1.0
public enum ErrorTrait {

  /// 资源或实体未找到
  NOT_FOUND,

  /// 与现有资源或业务规则冲突
  CONFLICT,

  /// 业务规则或验证违规
  RULE_VIOLATION,

  /// 配额、速率限制或容量超出
  QUOTA_EXCEEDED,

  /// 需要认证或认证失败
  UNAUTHORIZED,

  /// 已认证用户的访问被禁止
  FORBIDDEN,

  /// 操作超时或截止时间超出
  TIMEOUT,

  /// 依赖或外部服务不可用
  DEP_UNAVAILABLE
}
