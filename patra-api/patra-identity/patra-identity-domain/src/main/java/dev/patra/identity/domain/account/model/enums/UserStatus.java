package dev.patra.identity.domain.account.model.enums;

/// 前台用户的状态。四态之间的迁移规则在 `User` 聚合里。
public enum UserStatus {
  /// 正常。
  ACTIVE,
  /// 已封禁：邮箱和密码都对时登录返回 403，会话全部撤销。
  BANNED,
  /// 注销冷静期：可以登录，但除取消注销与登出外的写操作被拒；到期由任务转成已注销。
  PENDING_DELETION,
  /// 已注销：邮箱已释放、凭据已删，登录按密码错误处理；行保留，用户名永不释放，不能回退。
  DELETED;

  /// 这个状态下是否下发头像：正常与冷静期下发（冷静期里本人还能登录），已封禁与已注销不下发。头像读取接口与
  /// 批量用户摘要共用这条规则。
  ///
  /// @return 下发时为 `true`
  public boolean servesAvatar() {
    return this == ACTIVE || this == PENDING_DELETION;
  }
}
