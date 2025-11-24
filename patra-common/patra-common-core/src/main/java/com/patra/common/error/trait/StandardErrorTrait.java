package com.patra.common.error.trait;

/// 标准错误特征的枚举实现,提供平台级别的通用语义分类。
///
/// 这些特征涵盖了大多数常见的错误场景,并被错误解析引擎用于映射到适当的 HTTP 状态码。
///
/// **使用示例**:
///
/// ```java
/// public class UserNotFoundException extends DomainException {
///     public UserNotFoundException(String userId) {
///         super("用户未找到: " + userId, StandardErrorTrait.NOT_FOUND);
///     }
/// }
/// ```
///
/// **自定义 Trait**: 如果标准 trait 不满足需求,业务模块可以实现 {@link ErrorTrait} 接口创建自定义 trait。
///
/// @author linqibin
/// @since 0.1.0
/// @see ErrorTrait
/// @see HasErrorTraits
public enum StandardErrorTrait implements ErrorTrait {

  /// 资源或实体未找到。
  ///
  /// 示例: 查询不存在的用户、文档或记录。
  NOT_FOUND,

  /// 与现有资源或业务规则冲突。
  ///
  /// 示例: 唯一键冲突、重复创建、状态机违规。
  CONFLICT,

  /// 业务规则或验证违规。
  ///
  /// 示例: 输入验证失败、业务约束检查失败。
  RULE_VIOLATION,

  /// 配额、速率限制或容量超出。
  ///
  /// 示例: API 速率限制、存储配额超出、并发数超限。
  QUOTA_EXCEEDED,

  /// 需要认证或认证失败。
  ///
  /// 示例: 缺少认证凭证、Token 过期或无效。
  UNAUTHORIZED,

  /// 已认证用户的访问被禁止。
  ///
  /// 示例: 权限不足、资源访问被拒绝。
  FORBIDDEN,

  /// 操作超时或截止时间超出。
  ///
  /// 示例: 数据库查询超时、外部服务响应超时。
  TIMEOUT,

  /// 依赖或外部服务不可用。
  ///
  /// 示例: 数据库连接失败、外部 API 不可达、消息队列宕机。
  DEP_UNAVAILABLE;

  // 注意: Enum 已经提供了 name() 方法,无需覆盖
  // ErrorTrait 接口的 name() 方法会自动由 Enum.name() 满足
}
