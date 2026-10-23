package dev.patra.identity.adapter.rest.auth.request;

/// 找回密码申请的请求体。
///
/// @param email 邮箱
public record ForgotPasswordRequest(String email) {}
