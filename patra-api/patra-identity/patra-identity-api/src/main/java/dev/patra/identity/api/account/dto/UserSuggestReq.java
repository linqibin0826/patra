package dev.patra.identity.api.account.dto;

/// @ 联想的请求（PAP-118）。
///
/// @param prefix `@` 后面已输入的部分（不带 `@`）：同时当用户名前缀和昵称前缀用，各自按规则规范化；
///     哪一种都不可能时结果为空
/// @param viewerUserId 访客的用户 ID，匿名时为 `null`；结果不含访客本人
/// @param limit 最多几个，1 ~ 20
public record UserSuggestReq(String prefix, Long viewerUserId, Integer limit) {

  /// 创建请求。
  ///
  /// @param prefix `@` 后面已输入的部分
  /// @param viewerUserId 访客的用户 ID，可为 `null`
  /// @param limit 最多几个
  /// @return 请求
  public static UserSuggestReq of(String prefix, Long viewerUserId, Integer limit) {
    return new UserSuggestReq(prefix, viewerUserId, limit);
  }
}
