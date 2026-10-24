package dev.patra.interaction.app.collection.usecase.library;

import dev.patra.interaction.app.collection.service.Batches;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 从文献库移除：锁本人后删（仓储连带移出收藏夹、减篇数）。不在库里的忽略。
@Component
@RequiredArgsConstructor
public class RemoveItemsHandler implements CommandHandler<RemoveItemsCommand, Void> {

  private final LibraryRepository library;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 移除。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  public Void handle(RemoveItemsCommand command) {
    long userId = currentUser.require().userId();
    List<SubjectId> ids = Batches.requireItemIds(command.itemIds());
    transactions.executeWithoutResult(
        status -> {
          library.lockOwner(userId);
          library.deleteItems(userId, ids, clock.instant());
        });
    return null;
  }
}
