package dev.patra.identity.adapter.account.rest.request;

/// 发起注销的请求体。
///
/// @param username 输入的用户名，用来确认
public record RequestAccountDeletionRequest(String username) {}
