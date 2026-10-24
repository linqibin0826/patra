package dev.patra.interaction.app.collection.usecase.copy;

import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import lombok.Builder;

/// 复制收藏夹的结果。
///
/// @param collectionId 新收藏夹
/// @param name 实际名称（重名时加了序号）
/// @param addedToLibrary 新收进文献库的篇数
/// @param alreadyInLibrary 原本就在文献库里的篇数
/// @param skippedUnavailable 因已失效没有复制的篇数
@Builder
public record CopyResult(
    CollectionId collectionId,
    String name,
    int addedToLibrary,
    int alreadyInLibrary,
    int skippedUnavailable) {}
