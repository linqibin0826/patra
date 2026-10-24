package dev.patra.interaction.app.collection.usecase.library;

import dev.patra.interaction.app.collection.service.Batches;
import dev.patra.interaction.domain.collection.exception.LibraryFullException;
import dev.patra.interaction.domain.collection.policy.CollectionLimits;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 收藏（spec §6.1）：
///
/// 1. 本地登记表查得到、本人库里也已有 → 直接返回（对象已失效也不报错，不经 subject 解析）。
/// 2. 否则经 `SubjectGateway` 解析（事务外：未登记时会同步调 catalog，不能占着连接）。
/// 3. 事务：锁本人 → 已有就返回 → 满 20,000 拒绝 → 插入。
@Component
@RequiredArgsConstructor
public class CollectItemHandler implements CommandHandler<CollectItemCommand, CollectResult> {

  private final LibraryRepository library;
  private final SubjectGateway gateway;
  private final SubjectReadPort subjects;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 收藏。
  ///
  /// @param command 命令
  /// @return 结果
  @Override
  public CollectResult handle(CollectItemCommand command) {
    long userId = currentUser.require().userId();
    SubjectType type = Batches.subjectType(command.type());
    SubjectId known = subjects.findIdsByKeys(type, List.of(command.key())).get(command.key());
    if (known != null && library.findItem(userId, known).isPresent()) {
      return CollectResult.of(false, known);
    }
    SubjectId subjectId =
        gateway.resolveForInteraction(type, command.key(), SubjectCapability.COLLECT);
    return transactions.execute(
        status -> {
          library.lockOwner(userId);
          if (library.findItem(userId, subjectId).isPresent()) {
            return CollectResult.of(false, subjectId);
          }
          if (library.countItems(userId) >= CollectionLimits.LIBRARY_MAX_ITEMS) {
            throw new LibraryFullException();
          }
          library.insertItem(userId, subjectId, clock.instant());
          return CollectResult.of(true, subjectId);
        });
  }
}
