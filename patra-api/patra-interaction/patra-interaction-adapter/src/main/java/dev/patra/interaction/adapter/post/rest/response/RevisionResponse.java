package dev.patra.interaction.adapter.post.rest.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.patra.interaction.app.post.usecase.query.RevisionVersionView;
import java.time.Instant;

/// 修改历史里的一版：结构化字段平铺在顶层，不带作者。
///
/// @param writtenAt 这一版成为当前内容的时刻
/// @param current 是否当前版本
/// @param fields 结构化字段与时长（平铺）
/// @param body 正文 Markdown，可空
public record RevisionResponse(
    Instant writtenAt,
    boolean current,
    @JsonUnwrapped ExperienceFieldsResponse fields,
    String body) {

  /// 转换。
  ///
  /// @param view 一版
  /// @return 响应
  public static RevisionResponse from(RevisionVersionView view) {
    return new RevisionResponse(
        view.writtenAt(),
        view.current(),
        ExperienceFieldsResponse.from(view.fields()),
        view.body());
  }
}
