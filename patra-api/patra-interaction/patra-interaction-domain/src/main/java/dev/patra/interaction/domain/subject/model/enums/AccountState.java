package dev.patra.interaction.domain.subject.model.enums;

/// identity 里的账号状态（与 identity 的 `AccountStatus` 按名字一一对应）。
public enum AccountState {
  /// 正常。
  ACTIVE,
  /// 已封禁。
  BANNED,
  /// 注销冷静期。
  PENDING_DELETION,
  /// 已注销：注销清理据此删除他在各子域登记的个人数据（收藏、发表计数等）。
  DELETED
}
