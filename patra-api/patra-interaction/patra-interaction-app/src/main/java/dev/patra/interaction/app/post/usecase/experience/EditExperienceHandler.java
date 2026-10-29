package dev.patra.interaction.app.post.usecase.experience;

import dev.patra.interaction.app.post.service.ExperienceViews;
import dev.patra.interaction.app.post.service.PostRegistry;
import dev.patra.interaction.app.post.usecase.query.ExperienceDetailView;
import dev.patra.interaction.domain.post.exception.DuplicateSubmissionException;
import dev.patra.interaction.domain.post.exception.PostForbiddenException;
import dev.patra.interaction.domain.post.exception.PostNotFoundException;
import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.enums.EditKind;
import dev.patra.interaction.domain.post.model.vo.ExperienceDraft;
import dev.patra.interaction.domain.post.model.vo.PostEdit;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.policy.ExperienceRules;
import dev.patra.interaction.domain.post.port.repository.PostRepository;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 编辑投稿经验（spec 6.2）：
///
/// 1. 经验存在、未删除、是作者本人（404 / 404 + `SUBJECT_UNAVAILABLE` / 403），再校验字段。
/// 2. 事务外查发表资格（2026-10-27 用户定：冷静期、封禁、邮箱未验证不能编辑）；编辑不算发表，不计频率。
/// 3. 事务里：作者锁 → 再取一次（并发删除、并发编辑）→ 查重（排除自己）→ 按编辑决策写回、记版本 → 投稿月份
///    比锁内重读的内容变了，就把登记快照换成新月份（快照也在锁内读，本地查询，不占远程调用）。
@Component
@RequiredArgsConstructor
public class EditExperienceHandler
    implements CommandHandler<EditExperienceCommand, ExperienceDetailView> {

  private final SubjectGateway gateway;
  private final PostRegistry registry;
  private final PublishGuardGateway publishGuard;
  private final PostRepository posts;
  private final ExperienceViews views;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 编辑。
  ///
  /// @param command 命令
  /// @return 编辑后的详情
  @Override
  public ExperienceDetailView handle(EditExperienceCommand command) {
    long userId = currentUser.require().userId();
    PostId id = PostId.of(command.postId());
    editable(posts.find(id).orElse(null), userId);
    ExperienceDraft draft = ExperienceDraft.parse(command.input(), ExperienceRules.today(clock));
    UserCard self = publishGuard.requireEligible(userId);
    PostAggregate edited =
        transactions.execute(
            status -> {
              posts.lockAuthor(userId);
              PostAggregate post = editable(posts.find(id).orElse(null), userId);
              posts
                  .findActiveBySubmission(userId, post.subjectId(), draft.fields().submittedOn())
                  .filter(existing -> !existing.equals(id))
                  .ifPresent(
                      existing -> {
                        throw new DuplicateSubmissionException(existing);
                      });
              Instant now = clock.instant();
              PostEdit edit = post.edit(draft, now);
              if (edit.kind() == EditKind.NONE) {
                return post;
              }
              if (edit.kind() == EditKind.REVISE) {
                posts.insertRevision(id, edit.replaced());
              }
              posts.update(edit.after(), now);
              String month = draft.fields().submittedMonth();
              if (!month.equals(post.fields().submittedMonth())) {
                gateway.updateLocal(
                    SubjectType.POST, id.value(), SubjectState.ACTIVE, withMonth(id, month));
              }
              return edit.after();
            });
    return views.authored(edited, self);
  }

  /// 能被这个人编辑的经验。
  ///
  /// @param post 经验，可空
  /// @param userId 编辑者
  /// @return 经验
  /// @throws PostNotFoundException 不存在或已删除
  /// @throws PostForbiddenException 不是作者
  private static PostAggregate editable(PostAggregate post, long userId) {
    if (post == null) {
      throw PostNotFoundException.notFound();
    }
    if (post.isDeleted()) {
      throw PostNotFoundException.deleted();
    }
    if (!post.isAuthor(userId)) {
      throw new PostForbiddenException();
    }
    return post;
  }

  /// 登记快照换成新的投稿月份（期刊全称与缩写不变）。
  ///
  /// @param id 经验 ID
  /// @param month 新月份
  /// @return 快照
  private SubjectSnapshot withMonth(PostId id, String month) {
    SubjectSnapshot current = registry.snapshotOf(id);
    return SubjectSnapshot.of(current.title(), current.journal(), month, current.abbreviation());
  }
}
