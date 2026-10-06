package dev.linqibin.patra.identity.domain.model.enums;

/// 前台用户的状态。
public enum UserStatus {
  /// 正常。
  ACTIVE,
  /// 已封禁：邮箱和密码都对时登录返回 403。
  BANNED
}
