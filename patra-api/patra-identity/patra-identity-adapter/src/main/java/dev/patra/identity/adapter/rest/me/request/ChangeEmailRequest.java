package dev.patra.identity.adapter.rest.me.request;

/// 改邮箱申请的请求体。
///
/// @param newEmail 新邮箱
public record ChangeEmailRequest(String newEmail) {}
