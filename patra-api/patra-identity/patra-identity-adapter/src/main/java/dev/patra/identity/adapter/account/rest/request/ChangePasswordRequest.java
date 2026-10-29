package dev.patra.identity.adapter.account.rest.request;

/// 改密码的请求体。
///
/// @param currentPassword 当前密码
/// @param newPassword 新密码
public record ChangePasswordRequest(String currentPassword, String newPassword) {

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
  }
}
