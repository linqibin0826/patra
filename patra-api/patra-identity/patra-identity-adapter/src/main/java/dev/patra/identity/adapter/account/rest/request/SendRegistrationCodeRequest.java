package dev.patra.identity.adapter.account.rest.request;

/// 第三方首次注册：提交用户名和邮箱的请求体。
///
/// @param username 用户名
/// @param email 邮箱
public record SendRegistrationCodeRequest(String username, String email) {}
