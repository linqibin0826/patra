package dev.patra.identity.domain.model.enums;

/// 登录记录的结束原因。过期不记：Redis 过期不通知 identity。
public enum LoginEndReason {
  /// 用户自己登出。
  LOGOUT,
  /// 账号被封禁，会话被删。
  BANNED,
  /// 超过每用户的会话上限，被新登录挤掉。
  REPLACED,
  /// 被本人的另一次操作撤销：验证邮箱、改密码、踢设备。
  REVOKED
}
