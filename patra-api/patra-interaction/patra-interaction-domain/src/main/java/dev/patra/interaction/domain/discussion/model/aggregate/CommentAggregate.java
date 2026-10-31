package dev.patra.interaction.domain.discussion.model.aggregate;

import dev.patra.interaction.domain.discussion.exception.CommentNotFoundException;
import dev.patra.interaction.domain.discussion.exception.CommentNotOwnedException;
import dev.patra.interaction.domain.discussion.exception.ReplyTargetGoneException;
import dev.patra.interaction.domain.discussion.model.enums.CommentRemoval;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.vo.CommentEdit;
import dev.patra.interaction.domain.discussion.policy.DiscussionLimits;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/// 一条评论：根评论或回复。数据按树存，`rootId` 是所在楼的根评论（根评论是它自己）。
///
/// 不变量：`ACTIVE` 有正文、占位没有；根评论没有父评论且 `rootId == commentId`，回复反之。
@Getter
@ToString
@EqualsAndHashCode
public final class CommentAggregate {

  /// 发表时的正文版本。
  public static final int INITIAL_VERSION = 1;

  private final long commentId;
  private final SubjectId subjectId;
  private final long rootId;
  private final Long parentId;
  private final long authorId;
  private final CommentState state;
  private final String body;
  private final Set<Long> mentionedUserIds;
  private final Instant createdAt;
  private final Instant revisedAt;
  private final int version;

  /// 创建并校验不变量。
  ///
  /// @param commentId 评论 ID
  /// @param subjectId 对象
  /// @param rootId 所在楼的根评论
  /// @param parentId 回复的是哪一条，根评论为 `null`
  /// @param authorId 作者
  /// @param state 状态
  /// @param body 正文（存储形式），占位为 `null`
  /// @param mentionedUserIds 这条评论曾经 @ 到过的人（只增不减）
  /// @param createdAt 发表时刻
  /// @param revisedAt 当前版本由一次记版本的编辑产生的时刻，可空
  /// @param version 正文版本：发表为 1，每次正文确有变化加 1（集成事件靠它判新旧）
  @Builder(toBuilder = true)
  private CommentAggregate(
      long commentId,
      SubjectId subjectId,
      long rootId,
      Long parentId,
      long authorId,
      CommentState state,
      String body,
      Set<Long> mentionedUserIds,
      Instant createdAt,
      Instant revisedAt,
      int version) {
    this.commentId = commentId;
    this.subjectId = Objects.requireNonNull(subjectId, "subjectId 不能为 null");
    this.rootId = rootId;
    this.parentId = parentId;
    this.authorId = authorId;
    this.state = Objects.requireNonNull(state, "state 不能为 null");
    this.body = body;
    this.mentionedUserIds = mentionedUserIds == null ? Set.of() : Set.copyOf(mentionedUserIds);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为 null");
    this.revisedAt = revisedAt;
    this.version = version;
    if (version < INITIAL_VERSION) {
      throw new IllegalStateException("正文版本从 1 起：" + commentId);
    }
    if ((state == CommentState.ACTIVE) != (body != null)) {
      throw new IllegalStateException("正常评论必须有正文、占位不能有正文：" + commentId);
    }
    if ((parentId == null) != (rootId == commentId)) {
      throw new IllegalStateException("根评论没有父评论且根是自己，回复反之：" + commentId);
    }
  }

  /// 是否根评论。
  ///
  /// @return 没有父评论时为 `true`
  public boolean isRoot() {
    return parentId == null;
  }

  /// 要求操作者是作者。
  ///
  /// @param userId 操作者
  /// @throws CommentNotOwnedException 不是作者
  public void requireAuthor(long userId) {
    if (userId != authorId) {
      throw new CommentNotOwnedException();
    }
  }

  /// 要求这条能被回复：正常评论，或者本楼的根评论（已成占位也行，「回复这一楼」）。
  ///
  /// @throws ReplyTargetGoneException 是一条已成占位的回复
  public void requireRepliable() {
    if (state != CommentState.ACTIVE && !isRoot()) {
      throw new ReplyTargetGoneException();
    }
  }

  /// 编辑正文（spec 5.5）：发布后整 5 分钟之内原地替换、不记版本；之后把旧正文存成一版。两种都给正文版本加一。
  ///
  /// @param editorId 编辑者
  /// @param newBody 新正文（存储形式）
  /// @param newMentions 新正文 @ 到的人
  /// @param now 编辑时刻
  /// @return 编辑结果；正文没变时为空
  /// @throws CommentNotOwnedException 不是作者
  /// @throws CommentNotFoundException 已是占位
  public Optional<CommentEdit> edit(
      long editorId, String newBody, List<Long> newMentions, Instant now) {
    requireAuthor(editorId);
    if (state == CommentState.DELETED) {
      throw new CommentNotFoundException();
    }
    if (newBody.equals(body)) {
      return Optional.empty();
    }
    List<Long> added =
        newMentions.stream().filter(id -> !mentionedUserIds.contains(id)).distinct().toList();
    Set<Long> merged = new LinkedHashSet<>(mentionedUserIds);
    merged.addAll(newMentions);
    boolean withinGrace = !now.isAfter(createdAt.plus(DiscussionLimits.EDIT_GRACE));
    RevisionRecord archived =
        withinGrace ? null : RevisionRecord.of(body, revisedAt != null ? revisedAt : createdAt);
    CommentAggregate updated =
        toBuilder()
            .body(newBody)
            .mentionedUserIds(merged)
            .revisedAt(withinGrace ? revisedAt : now)
            .version(version + 1)
            .build();
    return Optional.of(CommentEdit.of(updated, archived, added));
  }

  /// 删除这条要做什么（spec 5.6）。
  ///
  /// @param actorId 操作者
  /// @param hasReplies 有没有直接回复
  /// @return 处理方式
  /// @throws CommentNotOwnedException 不是作者
  public CommentRemoval removalFor(long actorId, boolean hasReplies) {
    requireAuthor(actorId);
    if (state == CommentState.DELETED) {
      return CommentRemoval.NONE;
    }
    return hasReplies ? CommentRemoval.PLACEHOLDER : CommentRemoval.REMOVE;
  }
}
