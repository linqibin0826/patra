package dev.patra.identity.domain.account.model.vo;

/// 一个账号的邮箱和用户名。设置新密码时交给 `PasswordPolicy`，用来判断密码是不是和它们相同或相近。
///
/// 两项都可以是还没校验过的原始输入：注册时邮箱、用户名和密码一起校验，这时它们可能为 `null` 或不合法。
///
/// @param email 邮箱，可以为 `null`
/// @param username 用户名，可以为 `null`
public record UserIdentifiers(String email, String username) {

  /// 创建。
  ///
  /// @param email 邮箱，可以为 `null`
  /// @param username 用户名，可以为 `null`
  /// @return 邮箱和用户名
  public static UserIdentifiers of(String email, String username) {
    return new UserIdentifiers(email, username);
  }

  /// 还不知道是哪个账号（重设密码时，查到令牌之前）。两项都为 `null`，不参与比较。
  ///
  /// @return 两项都为空的邮箱和用户名
  public static UserIdentifiers unknown() {
    return new UserIdentifiers(null, null);
  }
}
