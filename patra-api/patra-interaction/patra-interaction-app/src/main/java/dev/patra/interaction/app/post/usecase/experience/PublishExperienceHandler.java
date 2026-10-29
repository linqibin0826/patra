package dev.patra.interaction.app.post.usecase.experience;

import dev.patra.interaction.app.post.service.ExperienceViews;
import dev.patra.interaction.app.post.usecase.query.ExperienceDetailView;
import dev.patra.interaction.domain.post.exception.DuplicateSubmissionException;
import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.vo.ExperienceDraft;
import dev.patra.interaction.domain.post.policy.ExperienceRules;
import dev.patra.interaction.domain.post.port.repository.PostRepository;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 写投稿经验（spec 6.1）：
///
/// 1. 先校验字段（表单错误不必等 catalog、identity）。
/// 2. 事务外：解析期刊（首次挂经验时登记期刊）、查发表资格（邮箱已验证、账号正常）。
/// 3. 事务里：发表守卫计一笔（每天 10 条）→ 作者锁 → 同刊同投稿日期查重 → 写两张表 → 登记成 `POST` 对象（快照：
///    期刊全称、投稿月份、期刊缩写，给提醒的上下文行用）。底座的异常不 catch，直接传播。
@Component
@RequiredArgsConstructor
public class PublishExperienceHandler
    implements CommandHandler<PublishExperienceCommand, ExperienceDetailView> {

  private final SubjectGateway gateway;
  private final SubjectReadPort subjects;
  private final PublishGuardGateway publishGuard;
  private final PostRepository posts;
  private final ExperienceViews views;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 写入。
  ///
  /// @param command 命令
  /// @return 新经验的详情
  @Override
  public ExperienceDetailView handle(PublishExperienceCommand command) {
    long userId = currentUser.require().userId();
    ExperienceDraft draft = ExperienceDraft.parse(command.input(), ExperienceRules.today(clock));
    SubjectId journal =
        gateway.resolveForInteraction(
            SubjectType.JOURNAL, command.journalKey(), SubjectCapability.SUBMISSION_EXPERIENCE);
    UserCard self = publishGuard.requireEligible(userId);
    SubjectView journalView = subjects.findByIds(List.of(journal)).get(journal);
    PostAggregate post =
        transactions.execute(
            status -> {
              publishGuard.admit(userId, PublishKind.POST);
              posts.lockAuthor(userId);
              posts
                  .findActiveBySubmission(userId, journal, draft.fields().submittedOn())
                  .ifPresent(
                      existing -> {
                        throw new DuplicateSubmissionException(existing);
                      });
              // 截到 PG 的微秒精度：201 响应与之后读回来的值一致
              Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
              PostAggregate created =
                  PostAggregate.publish(
                      posts.nextId(), journal, userId, command.anonymous(), draft, now);
              posts.insert(created);
              gateway.registerLocal(
                  SubjectType.POST,
                  created.postId().value(),
                  SubjectOwner.of(userId, command.anonymous()),
                  SubjectSnapshot.of(
                      null,
                      journalView.snapshot().title(),
                      draft.fields().submittedMonth(),
                      journalView.snapshot().abbreviation()));
              return created;
            });
    return views.authored(post, self);
  }
}
