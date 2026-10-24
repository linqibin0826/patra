package dev.patra.interaction.app.subject.usecase.reconcile;

import lombok.Builder;

/// 一次对账的计数。
///
/// @param scanned 核对的登记对象数（含失败批次里的）
/// @param sourceIdChanged 来源 ID 有变化的（换 ID、清空、复活时补回）
/// @param becameUnavailable 由 ACTIVE 变为 UNAVAILABLE 的
/// @param revived 由 UNAVAILABLE 变回 ACTIVE 的
/// @param failedBatches 来源不可用而跳过的批次
@Builder
public record ReconciliationResult(
    int scanned, int sourceIdChanged, int becameUnavailable, int revived, int failedBatches) {}
