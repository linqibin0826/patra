package dev.patra.interaction.adapter.collection.rest.request;

/// 设可见性的请求体。
///
/// @param visibility `PRIVATE` / `PUBLIC`
public record VisibilityRequest(String visibility) {}
