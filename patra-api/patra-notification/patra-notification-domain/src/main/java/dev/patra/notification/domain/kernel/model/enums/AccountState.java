package dev.patra.notification.domain.kernel.model.enums;

/// identity 的账号状态，与 identity-api 的 `AccountStatus` 按名字一一对应。
public enum AccountState {

  /// 正常。
  ACTIVE,

  /// 已封禁。
  BANNED,

  /// 注销冷静期。
  PENDING_DELETION,

  /// 已注销。
  DELETED
}
