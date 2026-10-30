package dev.patra.interaction.app.post.usecase.experience;

import dev.patra.interaction.app.post.service.PostRegistry;
import dev.patra.interaction.domain.post.event.PostDeletedEvent;
import dev.patra.interaction.domain.post.exception.PostForbiddenException;
import dev.patra.interaction.domain.post.exception.PostNotFoundException;
import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.port.repository.PostRepository;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 删除投稿经验（spec 6.3）：不存在 404、不是作者 403、已删除直接返回（幂等）；不查发表资格（2026-10-27 用户定）。
/// 事务里拿作者锁、再判一次，在锁内读当前登记快照，软删并把登记行置为 UNAVAILABLE（原样传回快照）：期刊列表与汇总不再计入，讨论区不能再回复。
/// 同一事务里发布 {@link PostDeletedEvent}，提醒子域据此删掉这条经验下的全部提醒（PAP-127）。
@Component
@RequiredArgsConstructor
public class DeleteExperienceHandler implements CommandHandler<DeleteExperienceCommand, Void> {

  private final SubjectGateway gateway;
  private final PostRegistry registry;
  private final PostRepository posts;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /// 删除。
  ///
  /// @param command 命令
  /// @return 无
  @Override
  public Void handle(DeleteExperienceCommand command) {
    long userId = currentUser.require().userId();
    PostId id = PostId.of(command.postId());
    PostAggregate post = posts.find(id).orElseThrow(PostNotFoundException::notFound);
    if (!post.isAuthor(userId)) {
      throw new PostForbiddenException();
    }
    if (post.isDeleted()) {
      return null;
    }
    transactions.executeWithoutResult(
        status -> {
          posts.lockAuthor(userId);
          if (posts.find(id).map(PostAggregate::isDeleted).orElse(true)) {
            return;
          }
          SubjectSnapshot snapshot = registry.snapshotOf(id);
          Instant now = clock.instant();
          posts.markDeleted(id, now);
          gateway.updateLocal(SubjectType.POST, id.value(), SubjectState.UNAVAILABLE, snapshot);
          events.publishEvent(
              PostDeletedEvent.builder()
                  .eventId(UUID.randomUUID())
                  .postId(id.value())
                  .subjectId(registry.subjectIdOf(id))
                  .authorId(userId)
                  .deletedAt(now)
                  .build());
        });
    return null;
  }
}
