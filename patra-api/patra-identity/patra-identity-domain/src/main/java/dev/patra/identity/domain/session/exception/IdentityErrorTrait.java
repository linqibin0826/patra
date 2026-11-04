package dev.patra.identity.domain.session.exception;

import dev.patra.tool.core.error.trait.ErrorTrait;

/// identity 自己的语义特征：随一个标准特征一起放进异常。错误引擎映射状态码时跳过它，
/// 响应的 `traits[]` 里带上它，客户端把它当「原因码」读。一个异常只放一个标准特征，
/// 状态码才不取决于集合的遍历顺序。
public enum IdentityErrorTrait implements ErrorTrait {
  /// 账号正在注销冷静期，写操作被拒（409）。
  PENDING_DELETION,

  /// 距上次改名不足冷却期，改名被拒（409）。
  USERNAME_CHANGE_COOLDOWN,

  /// 邮箱已经验证过了，重发验证邮件被拒（409）。
  EMAIL_ALREADY_VERIFIED,

  /// 最近一次输密码已超过再认证窗口，敏感操作被拒（403）；门户弹密码框后重试。
  REAUTH_REQUIRED,

  /// 已有一个待确认的改邮箱申请（409）。
  EMAIL_CHANGE_PENDING,

  /// 再认证或改密码时当前密码不正确（401）；和「会话失效」的 401 区分开，门户不把它当登出。
  PASSWORD_MISMATCH,

  /// 在登录设备列表里踢的是本机（409）；本机走退出登录（PAP-99）。
  CURRENT_SESSION,

  /// 用户在第三方平台上点了拒绝（422）。
  OAUTH_CANCELLED,

  /// 第三方授权没通过协议校验：`state`、`id_token`、授权码（422）。
  OAUTH_REJECTED,

  /// 绑定第三方账号要求邮箱已验证（409）。
  EMAIL_NOT_VERIFIED,

  /// 绑定回调时的用户或会话与发起时不一致（403）。
  LINK_SESSION_MISMATCH,

  /// 这个第三方账号已绑给别的用户（409）。
  PROVIDER_ACCOUNT_TAKEN,

  /// 当前账号已绑了这一家（409）。
  PROVIDER_ALREADY_LINKED,

  /// 没有密码且只剩这一种登录方式，不能解绑（409）。
  LAST_LOGIN_METHOD
}
