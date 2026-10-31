package dev.patra.notification.domain.mail.model.vo;

import java.util.ArrayList;
import java.util.List;

/// 一个收件人排队行到点时的处理结果（spec 5.1）：丢弃哪些、合成哪几批；两者之外的行原样留着。
///
/// @param dropped 丢弃的提醒 ID（已读、已删除、出现之后上过站）
/// @param batches 合成逐条邮件的批，回复在前、@ 在后
public record FlushPlan(List<Long> dropped, List<ReminderBatch> batches) {

  /// 防御性拷贝。
  public FlushPlan {
    dropped = List.copyOf(dropped);
    batches = List.copyOf(batches);
  }

  /// 构造。
  ///
  /// @param dropped 丢弃的提醒 ID
  /// @param batches 合成的批
  /// @return 结果
  public static FlushPlan of(List<Long> dropped, List<ReminderBatch> batches) {
    return new FlushPlan(dropped, batches);
  }

  /// 要从排队表里删掉的行：丢弃的加上进了批的。
  ///
  /// @return 提醒 ID
  public List<Long> consumed() {
    List<Long> ids = new ArrayList<>(dropped);
    batches.forEach(batch -> ids.addAll(batch.notificationIds()));
    return List.copyOf(ids);
  }
}
