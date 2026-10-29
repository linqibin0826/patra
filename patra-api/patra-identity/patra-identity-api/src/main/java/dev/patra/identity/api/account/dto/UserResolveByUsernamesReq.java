package dev.patra.identity.api.account.dto;

import java.util.List;

/// 按用户名批量解析的请求（PAP-118）。不做防御性拷贝：`null` 与含 `null` 的列表要原样到达服务端（列表为
/// `null` 时 422，`null` 元素跳过）。
///
/// @param usernames 用户名原文（`@` 后面的部分，不带 `@`），最多 100 个（按去重前的长度算）；不合用户名语法的
///     元素跳过
public record UserResolveByUsernamesReq(List<String> usernames) {

  /// 创建请求。
  ///
  /// @param usernames 用户名原文
  /// @return 请求
  public static UserResolveByUsernamesReq of(List<String> usernames) {
    return new UserResolveByUsernamesReq(usernames);
  }
}
