package dev.patra.interaction.domain.collection.model.user;

/// identity 里的账号状态（与 identity 的 `AccountStatus` 按名字一一对应）。
public enum AccountState {
  /// 正常。
  ACTIVE,
  /// 已封禁。
  BANNED,
  /// 注销冷静期。
  PENDING_DELETION,
  /// 已注销：注销清理任务据此删除他的收藏与收藏夹。
  DELETED
}
