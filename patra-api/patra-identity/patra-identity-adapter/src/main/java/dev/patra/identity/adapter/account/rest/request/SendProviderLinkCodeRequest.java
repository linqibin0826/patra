package dev.patra.identity.adapter.account.rest.request;

/// 邮箱撞车时就地绑定：提交邮箱的请求体。
///
/// @param email 邮箱
public record SendProviderLinkCodeRequest(String email) {}
