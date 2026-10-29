package dev.patra.identity.api.account.dto;

/// 账号状态，与 identity 领域里的用户状态按名字一一对应。
public enum AccountStatus {
  /// 正常。
  ACTIVE,
  /// 已封禁。
  BANNED,
  /// 注销冷静期。
  PENDING_DELETION,
  /// 已注销：调用方据此清理该用户的数据（interaction 的注销清理任务）。
  DELETED
}
