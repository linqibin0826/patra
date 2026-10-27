package dev.patra.interaction.adapter.discussion.rest.request;

/// 发表评论或回复的请求体。
///
/// @param body 正文原文（Markdown，@ 写成 `@用户名`）
/// @param parentId 回复的是哪一条；为空是根评论
public record PublishCommentRequest(String body, Long parentId) {}
