package dev.patra.interaction.app.discussion.usecase.comment;

import dev.patra.interaction.app.discussion.service.CommentViews;
import dev.patra.interaction.app.discussion.service.DiscussionSubjects;
import dev.patra.interaction.app.discussion.service.MentionDraft;
import dev.patra.interaction.app.discussion.service.MentionResolution;
import dev.patra.interaction.app.discussion.service.ResolvedBody;
import dev.patra.interaction.app.discussion.usecase.query.CommentView;
import dev.patra.interaction.domain.discussion.exception.CommentNotFoundException;
import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.discussion.model.vo.CommentEdit;
import dev.patra.interaction.domain.discussion.policy.CommentText;
import dev.patra.interaction.domain.discussion.policy.MentionSyntax;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.interaction.domain.subject.port.markdown.PlainTextPort;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 编辑自己的评论（spec 5.5、6.2「编辑的顺序」）：
///
/// 1. 本地校验正文与 @ 候选；预读：不存在或占位 404、不是作者 403；对象已失效 404；
/// 2. 事务外：发表资格（冷静期、封禁、邮箱未验证不能改；不计频率）→ 解析 @，当前版本里已有的提及承接；
/// 3. 事务：锁行重读 → 正文没变什么都不做；超过 5 分钟先存旧版本 → 改正文与正文版本 → 发布编辑结果产生的
///    `CommentEditedEvent`（带事件 ID、新版本与事务外转好的纯文本，PAP-141）。
@Component
@RequiredArgsConstructor
public class EditCommentHandler implements CommandHandler<EditCommentCommand, CommentView> {

  private final DiscussionSubjects subjects;
  private final PublishGuardGateway guard;
  private final MentionResolution mentions;
  private final CommentRepository comments;
  private final DiscussionReadPort reads;
  private final CommentViews views;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final PlainTextPort plainText;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /// 编辑。
  ///
  /// @param command 命令
  /// @return 改后的完整条目
  @Override
  public CommentView handle(EditCommentCommand command) {
    long editor = currentUser.require().userId();
    MentionDraft draft = mentions.draft(CommentText.parse(command.body()));
    long id = command.commentId();
    CommentAggregate current = comments.find(id).orElseThrow(CommentNotFoundException::new);
    current.requireAuthor(editor);
    if (current.getState() == CommentState.DELETED) {
      throw new CommentNotFoundException();
    }
    SubjectView subject = subjects.requireActive(current.getSubjectId());
    UserCard self = guard.requireEligible(editor);
    ResolvedBody resolved =
        mentions.resolve(draft, Set.copyOf(MentionSyntax.mentionedIds(current.getBody())));
    String plain =
        plainText.toPlainText(resolved.body(), Set.copyOf(resolved.mentionedUserIds())).value();
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    CommentRecord record =
        transactions.execute(
            status -> {
              CommentAggregate locked =
                  comments.findForEdit(id).orElseThrow(CommentNotFoundException::new);
              Optional<CommentEdit> edit =
                  locked.edit(editor, resolved.body(), resolved.mentionedUserIds(), now);
              if (edit.isPresent()) {
                if (edit.get().archived() != null) {
                  comments.appendRevision(id, edit.get().archived());
                }
                comments.updateBody(edit.get().updated());
              }
              CommentRecord after = reads.findById(id).orElseThrow();
              edit.ifPresent(
                  e ->
                      events.publishEvent(
                          e.editedEvent(
                              subject,
                              after.activeParentAuthorId(),
                              resolved.mentionedUserIds(),
                              plain,
                              now)));
              return after;
            });
    return views
        .assemble(List.of(record), subject, editor, Map.of(editor, self), Set.of())
        .getFirst();
  }
}
