package dev.patra.notification.app.inbox.usecase.purge;

/// 一次保留期清理删掉的行数。
///
/// @param notifications 提醒
/// @param comments 评论快照与墓碑
/// @param tombstones 经验墓碑
public record PurgeResult(int notifications, int comments, int tombstones) {

  /// 构造。
  ///
  /// @param notifications 提醒
  /// @param comments 评论快照与墓碑
  /// @param tombstones 经验墓碑
  /// @return 结果
  public static PurgeResult of(int notifications, int comments, int tombstones) {
    return new PurgeResult(notifications, comments, tombstones);
  }
}
