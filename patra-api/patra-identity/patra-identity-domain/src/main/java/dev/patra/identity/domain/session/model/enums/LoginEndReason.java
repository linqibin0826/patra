package dev.patra.identity.domain.session.model.enums;

/// 登录记录的结束原因：这台设备为什么下线了。过期不记：Redis 过期不通知 identity。
public enum LoginEndReason {
  /// 用户自己登出。
  LOGOUT,
  /// 账号被封禁，会话被删。
  BANNED,
  /// 超过每用户的会话上限，被新登录挤掉。
  REPLACED,
  /// 本人在登录设备列表里踢掉。新设备判定不把这样下线的设备算作「见过」。
  REVOKED,
  /// 验证邮箱后，其他会话被撤销。
  EMAIL_VERIFIED,
  /// 改密码后，其他会话被撤销。
  PASSWORD_CHANGED,
  /// 重设密码后，全部会话被撤销。
  PASSWORD_RESET,
  /// 撤销改邮箱后，全部会话被撤销。
  EMAIL_REVERTED,
  /// 申请注销，全部会话被撤销。
  DELETION_REQUESTED,
  /// 注销到期执行，冷静期内登录的会话被撤销。
  DELETION_COMPLETED
}
