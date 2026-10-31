package dev.patra.notification.app.mail.usecase.digest;

import java.time.Instant;

/// 建汇总投递的计数。
///
/// @param windowEnd 汇总窗口右端
/// @param subscribers 设为每日汇总的人数（手动时是列出的人里有汇总类别的）
/// @param created 新建的投递数（补跑时已建过的不算）
/// @param purged 顺带删掉的过期投递记录数
public record DigestScheduleResult(Instant windowEnd, int subscribers, int created, int purged) {

  /// 创建。
  ///
  /// @param windowEnd 窗口右端
  /// @param subscribers 人数
  /// @param created 新建数
  /// @param purged 删除数
  /// @return 计数
  public static DigestScheduleResult of(
      Instant windowEnd, int subscribers, int created, int purged) {
    return new DigestScheduleResult(windowEnd, subscribers, created, purged);
  }
}
