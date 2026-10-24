package dev.patra.interaction.app.collection.usecase.cleanup;

import lombok.Builder;

/// 注销清理的计数。
///
/// @param scannedUsers 核对的用户数
/// @param deletedUsers 清理掉的用户数
/// @param libraryItems 删掉的文献库条目
/// @param collections 删掉的收藏夹
/// @param collectionItems 删掉的收藏夹条目
/// @param failedBatches 因 identity 不可用跳过的批次
@Builder
public record CleanupResult(
    int scannedUsers,
    int deletedUsers,
    int libraryItems,
    int collections,
    int collectionItems,
    int failedBatches) {}
