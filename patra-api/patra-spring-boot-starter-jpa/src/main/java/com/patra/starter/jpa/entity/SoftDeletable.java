package com.patra.starter.jpa.entity;

import java.time.Instant;

/// 软删除能力接口。
///
/// 实现此接口的实体支持逻辑删除而非物理删除。
/// 通常与 `@SQLRestriction("deleted_at IS NULL")` 配合使用。
///
/// @author linqibin
/// @since 0.1.0
/// @see SoftDeletableJpaEntity
public interface SoftDeletable {

  /// 获取删除时间戳。
  ///
  /// @return 删除时间，null 表示未删除
  Instant getDeletedAt();

  /// 设置删除时间戳。
  ///
  /// @param deletedAt 删除时间
  void setDeletedAt(Instant deletedAt);

  /// 执行软删除。
  ///
  /// 设置 `deletedAt` 为当前时间戳，标记记录为已删除。
  /// 调用此方法后，需要调用 `repository.save(entity)` 持久化更改。
  default void softDelete() {
    setDeletedAt(Instant.now());
  }

  /// 检查是否已被软删除。
  ///
  /// @return 已删除返回 true
  default boolean isDeleted() {
    return getDeletedAt() != null;
  }
}
