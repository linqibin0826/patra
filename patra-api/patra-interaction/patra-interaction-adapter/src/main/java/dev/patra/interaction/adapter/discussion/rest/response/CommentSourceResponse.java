package dev.patra.interaction.adapter.discussion.rest.response;

/// 编辑原文：提及换回了 `@当前用户名`，已注销的写成 `@已注销用户`。
///
/// @param body 编辑原文
public record CommentSourceResponse(String body) {}
