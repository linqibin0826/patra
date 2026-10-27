package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import java.time.Instant;

/// 修改历史里的一个版本。
///
/// @param body 这一版的 Markdown 原文（提及写作 `<@用户ID>`）
/// @param at 这一版成为当前版本的时刻（最初版本是发表时刻）
public record RevisionResponse(String body, Instant at) {

  /// 从读模型转换。
  ///
  /// @param revision 版本
  /// @return 响应
  public static RevisionResponse from(RevisionRecord revision) {
    return new RevisionResponse(revision.body(), revision.at());
  }
}
