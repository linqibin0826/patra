package dev.patra.interaction.app.post.usecase.query;

import dev.patra.interaction.domain.post.model.vo.ExperienceFields;
import java.time.Instant;

/// 修改历史里的一版（不带作者，免得匿名经验经历史泄露作者）。门户逐版对比，列出「字段：旧值 → 新值」与正文增删。
///
/// @param writtenAt 这一版成为当前内容的时刻
/// @param current 是否当前版本
/// @param fields 结构化字段
/// @param body 正文，可空
public record RevisionVersionView(
    Instant writtenAt, boolean current, ExperienceFields fields, String body) {

  /// 创建。
  ///
  /// @param writtenAt 成为当前内容的时刻
  /// @param current 是否当前版本
  /// @param fields 结构化字段
  /// @param body 正文
  /// @return 一版
  public static RevisionVersionView of(
      Instant writtenAt, boolean current, ExperienceFields fields, String body) {
    return new RevisionVersionView(writtenAt, current, fields, body);
  }
}
