package dev.patra.interaction.adapter.collection.rest.request;

/// 新建 / 编辑收藏夹的请求体。
///
/// @param name 名称
/// @param description 简介，可空
public record CollectionTextRequest(String name, String description) {}
