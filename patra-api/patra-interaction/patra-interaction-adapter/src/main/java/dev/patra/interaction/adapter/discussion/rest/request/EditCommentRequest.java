package dev.patra.interaction.adapter.discussion.rest.request;

/// 编辑评论的请求体。
///
/// @param body 新正文原文（Markdown，@ 写成 `@用户名`）
public record EditCommentRequest(String body) {}
