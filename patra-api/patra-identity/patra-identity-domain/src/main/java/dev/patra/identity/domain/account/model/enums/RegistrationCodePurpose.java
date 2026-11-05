package dev.patra.identity.domain.account.model.enums;

/// 第三方首次登录的验证码是拿来做什么的。两种不能混用：注册用途的码绑不了账号，绑定用途的码也建不了账号。
public enum RegistrationCodePurpose {
  /// 建新账号。
  REGISTER,
  /// 绑定到邮箱对应的已有账号。
  LINK
}
