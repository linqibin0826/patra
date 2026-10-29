package dev.patra.identity.adapter.account.rest.request;

/// 改用户名的请求体。
///
/// @param username 新用户名
public record ChangeUsernameRequest(String username) {}
