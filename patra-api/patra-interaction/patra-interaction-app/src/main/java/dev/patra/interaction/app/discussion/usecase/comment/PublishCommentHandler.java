package dev.patra.interaction.app.discussion.usecase.comment;

import dev.patra.interaction.app.discussion.service.CommentViews;
import dev.patra.interaction.app.discussion.service.DiscussionSubjects;
import dev.patra.interaction.app.discussion.service.MentionDraft;
import dev.patra.interaction.app.discussion.service.MentionResolution;
import dev.patra.interaction.app.discussion.service.ResolvedBody;
import dev.patra.interaction.app.discussion.usecase.query.CommentView;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.discussion.exception.InvalidDiscussionRequestException;
import dev.patra.interaction.domain.discussion.exception.ReplyTargetGoneException;
import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.discussion.model.vo.NewComment;
import dev.patra.interaction.domain.discussion.policy.CommentText;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 发表根评论与回复（spec 6.2「发表的顺序」）：
///
/// 1. 本地校验（类型、能力、key、父评论 ID、正文、@ 候选），任何远程调用之前；
/// 2. 事务外：发表资格（identity）→ 解析对象（文献首次评论时登记）→ 预读父评论 → 解析 @（identity）；
/// 3. 事务：发表守卫计数 → 回复时拿楼锁、重读父评论判能否回复 → 插入 → 楼内回复数（回复时）与对象评论数 +1 →
///    发布 {@link CommentPublishedEvent}；
/// 4. 拼响应：自己的名片用资格那一步的。
@Component
@RequiredArgsConstructor
public class PublishCommentHandler implements CommandHandler<PublishCommentCommand, CommentView> {

  private final DiscussionSubjects subjects;
  private final SubjectGateway gateway;
  private final PublishGuardGateway guard;
  private final MentionResolution mentions;
  private final CommentRepository comments;
  private final DiscussionReadPort reads;
  private final CommentViews views;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /// 发表。
  ///
  /// @param command 命令
  /// @return 新评论的完整条目
  @Override
  public CommentView handle(PublishCommentCommand command) {
    long author = currentUser.require().userId();
    SubjectType type = subjects.requireDiscussable(command.type(), command.key());
    if (command.parentId() != null && command.parentId() <= 0) {
      throw new InvalidDiscussionRequestException("回复的评论不合法");
    }
    MentionDraft draft = mentions.draft(CommentText.parse(command.body()));
    UserCard self = guard.requireEligible(author);
    SubjectId subjectId =
        gateway.resolveForInteraction(type, command.key(), SubjectCapability.DISCUSS);
    SubjectView subject = subjects.requireActive(subjectId);
    CommentAggregate parent = command.parentId() == null ? null : preReadParent(command, subjectId);
    ResolvedBody resolved = mentions.resolve(draft, Set.of());
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Long id =
        transactions.execute(
            status -> {
              guard.admit(author, PublishKind.COMMENT);
              CommentAggregate locked = parent == null ? null : lockParent(parent);
              long newId =
                  comments.insert(
                      NewComment.builder()
                          .subjectId(subjectId)
                          .rootId(locked == null ? null : locked.getRootId())
                          .parentId(locked == null ? null : locked.getCommentId())
                          .authorId(author)
                          .body(resolved.body())
                          .mentionedUserIds(resolved.mentionedUserIds())
                          .createdAt(now)
                          .build());
              if (locked != null) {
                comments.recordReply(locked.getRootId(), now);
              }
              comments.incrementCommentCount(subjectId);
              events.publishEvent(published(newId, subject, locked, author, resolved, now));
              return newId;
            });
    CommentRecord record = reads.findById(id).orElseThrow();
    return views
        .assemble(List.of(record), subject, author, Map.of(author, self), Set.of())
        .getFirst();
  }

  /// 事务外预读父评论：不存在 409，不在这个对象下 422。
  ///
  /// @param command 命令
  /// @param subjectId 对象
  /// @return 父评论
  private CommentAggregate preReadParent(PublishCommentCommand command, SubjectId subjectId) {
    CommentAggregate parent =
        comments.find(command.parentId()).orElseThrow(ReplyTargetGoneException::new);
    if (!parent.getSubjectId().equals(subjectId)) {
      throw new InvalidDiscussionRequestException("回复的评论不在这个讨论里");
    }
    return parent;
  }

  /// 拿楼锁、重读父评论，判能不能回复（锁内这份决定写什么）。
  ///
  /// @param parent 预读的父评论
  /// @return 锁内读到的父评论
  private CommentAggregate lockParent(CommentAggregate parent) {
    comments.lockThread(parent.getRootId());
    CommentAggregate locked =
        comments.find(parent.getCommentId()).orElseThrow(ReplyTargetGoneException::new);
    locked.requireRepliable();
    return locked;
  }

  /// 「评论已发表」事件。
  ///
  /// @param commentId 新评论
  /// @param subject 对象
  /// @param parent 锁内读到的父评论，根评论为 `null`
  /// @param author 作者
  /// @param resolved 正文与提及
  /// @param now 发表时刻
  /// @return 事件
  private static CommentPublishedEvent published(
      long commentId,
      SubjectView subject,
      CommentAggregate parent,
      long author,
      ResolvedBody resolved,
      Instant now) {
    SubjectOwner owner = subject.owner();
    return CommentPublishedEvent.builder()
        .commentId(commentId)
        .subjectId(subject.subjectId())
        .subjectType(subject.type())
        .subjectOwner(owner)
        .rootCommentId(parent == null ? commentId : parent.getRootId())
        .parentCommentId(parent == null ? null : parent.getCommentId())
        .parentAuthorId(
            parent != null && parent.getState() == CommentState.ACTIVE
                ? parent.getAuthorId()
                : null)
        .authorId(author)
        .authorAnonymous(owner != null && owner.anonymous() && owner.userId() == author)
        .mentionedUserIds(resolved.mentionedUserIds())
        .body(resolved.body())
        .publishedAt(now)
        .build();
  }
}
