package dev.patra.interaction.adapter.collection.rest.request;

/// 改备注的请求体。
///
/// @param note 备注；`null` 或空白为删除
public record NoteRequest(String note) {}
