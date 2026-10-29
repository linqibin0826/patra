package dev.patra.identity.adapter.account.rest.request;

/// `PUT /me/privacy` 的请求体，三项都必填。取值收成字符串，由应用层报 `INVALID_FORMAT`（交给 Jackson
/// 反序列化枚举会回 400 且没有字段原因码）。
///
/// @param profileVisibility 主页可见性：`PUBLIC` / `SIGNED_IN` / `SELF`
/// @param professionalVisibility 专业背景可见性：`PUBLIC` / `SELF`
/// @param searchIndexing 是否允许搜索引擎收录
public record UpdatePrivacyRequest(
    String profileVisibility, String professionalVisibility, Boolean searchIndexing) {}
