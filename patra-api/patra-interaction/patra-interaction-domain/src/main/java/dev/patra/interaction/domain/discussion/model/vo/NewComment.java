package dev.patra.interaction.domain.discussion.model.vo;

import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 要插入的一条新评论（ID 由仓储生成）。
///
/// @param subjectId 对象
/// @param rootId 所在楼的根评论；根评论为 `null`（就是它自己）
/// @param parentId 回复的是哪一条；根评论为 `null`
/// @param authorId 作者
/// @param body 正文（存储形式）
/// @param mentionedUserIds 正文 @ 到的人
/// @param createdAt 发表时刻（已截到微秒）
@Builder
public record NewComment(
    SubjectId subjectId,
    Long rootId,
    Long parentId,
    long authorId,
    String body,
    List<Long> mentionedUserIds,
    Instant createdAt) {

  /// 防御性拷贝，`null` 视为空。
  public NewComment {
    mentionedUserIds = mentionedUserIds == null ? List.of() : List.copyOf(mentionedUserIds);
  }
}
