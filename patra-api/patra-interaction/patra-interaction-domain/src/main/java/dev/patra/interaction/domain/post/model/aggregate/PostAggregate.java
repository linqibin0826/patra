package dev.patra.interaction.domain.post.model.aggregate;

import dev.patra.interaction.domain.post.model.enums.EditKind;
import dev.patra.interaction.domain.post.model.enums.PostStatus;
import dev.patra.interaction.domain.post.model.enums.PostType;
import dev.patra.interaction.domain.post.model.vo.ExperienceDraft;
import dev.patra.interaction.domain.post.model.vo.ExperienceFields;
import dev.patra.interaction.domain.post.model.vo.PostBody;
import dev.patra.interaction.domain.post.model.vo.PostEdit;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.model.vo.PostRevision;
import dev.patra.interaction.domain.post.policy.ExperienceRules;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 一条帖子（本版只有投稿经验）：基表一行加经验扩展表一行。作者、挂载对象、是否匿名发布后都不变。
///
/// @param postId 帖子 ID
/// @param type 帖子类型
/// @param subjectId 挂在哪个对象下（期刊在登记表的本地 ID）
/// @param authorId 作者
/// @param anonymous 发布时是否匿名
/// @param fields 结构化字段
/// @param body 正文，可空
/// @param status 状态
/// @param createdAt 发布时刻
/// @param revisedAt 最近一次记了版本的编辑时刻；没有修改记录为 `null`
@Builder(toBuilder = true)
public record PostAggregate(
    PostId postId,
    PostType type,
    SubjectId subjectId,
    long authorId,
    boolean anonymous,
    ExperienceFields fields,
    PostBody body,
    PostStatus status,
    Instant createdAt,
    Instant revisedAt) {

  /// 发布一条投稿经验。
  ///
  /// @param postId 帖子 ID
  /// @param journal 期刊的本地 ID
  /// @param authorId 作者
  /// @param anonymous 是否匿名
  /// @param draft 解析好的内容
  /// @param now 发布时刻
  /// @return 帖子
  public static PostAggregate publish(
      PostId postId,
      SubjectId journal,
      long authorId,
      boolean anonymous,
      ExperienceDraft draft,
      Instant now) {
    return PostAggregate.builder()
        .postId(postId)
        .type(PostType.SUBMISSION_EXPERIENCE)
        .subjectId(journal)
        .authorId(authorId)
        .anonymous(anonymous)
        .fields(draft.fields())
        .body(draft.body())
        .status(PostStatus.ACTIVE)
        .createdAt(now)
        .build();
  }

  /// 是否这个人写的。
  ///
  /// @param userId 用户
  /// @return 是作者时为 `true`
  public boolean isAuthor(long userId) {
    return authorId == userId;
  }

  /// 是否已删除。
  ///
  /// @return 已删除时为 `true`
  public boolean isDeleted() {
    return status == PostStatus.DELETED;
  }

  /// 是否有修改记录（界面上的「已编辑」）。
  ///
  /// @return 有记录版本时为 `true`
  public boolean edited() {
    return revisedAt != null;
  }

  /// 当前内容成为当前版本的时刻：没有修改记录时是发布时刻。
  ///
  /// @return 时刻
  public Instant contentWrittenAt() {
    return revisedAt != null ? revisedAt : createdAt;
  }

  /// 编辑：内容没变什么都不做；发布后 5 分钟内覆盖、不记版本；之后把被替换的旧内容记成一版。
  ///
  /// @param draft 新内容
  /// @param now 编辑时刻
  /// @return 决定
  /// @throws IllegalStateException 已删除（调用方应先判）
  public PostEdit edit(ExperienceDraft draft, Instant now) {
    if (isDeleted()) {
      throw new IllegalStateException("已删除的帖子不能编辑：" + postId);
    }
    if (Objects.equals(fields, draft.fields()) && Objects.equals(body, draft.body())) {
      return PostEdit.of(EditKind.NONE, this, null);
    }
    PostAggregate.PostAggregateBuilder after =
        toBuilder().fields(draft.fields()).body(draft.body());
    if (now.isBefore(createdAt.plus(ExperienceRules.EDIT_GRACE))) {
      return PostEdit.of(EditKind.OVERWRITE, after.build(), null);
    }
    return PostEdit.of(
        EditKind.REVISE,
        after.revisedAt(now).build(),
        PostRevision.of(fields, body, contentWrittenAt(), now));
  }
}
