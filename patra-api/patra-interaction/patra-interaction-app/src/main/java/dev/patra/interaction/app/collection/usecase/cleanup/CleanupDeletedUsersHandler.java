package dev.patra.interaction.app.collection.usecase.cleanup;

import dev.patra.interaction.domain.collection.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.collection.model.read.DeletedUserData;
import dev.patra.interaction.domain.collection.model.user.AccountState;
import dev.patra.interaction.domain.collection.model.user.UserCard;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.collection.port.user.UserDirectoryPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 注销清理（spec §5.10）：按用户 ID 升序每批 {@value #BATCH} 个有数据的用户（文献库条目与收藏夹两表的并集），
/// 调一次 identity（访客为空）；只有 `DELETED` 的人，每人一个事务：锁住他再删三张表里他的全部行。identity 省略的不删
/// （省略不等于已注销），记 WARN；identity 不可用跳过这一批、计数，继续下一批，次日重跑。
///
/// 不加任务级锁：调度中心「单机串行」保证同一时间只有一个实例在跑。
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupDeletedUsersHandler
    implements CommandHandler<CleanupDeletedUsersCommand, CleanupResult> {

  /// 一批的人数，等于 identity 批量接口的上限。
  static final int BATCH = UserDirectoryPort.MAX_USERS;

  private final LibraryReadPort reads;
  private final LibraryRepository library;
  private final UserDirectoryPort users;
  private final TransactionOperations transactions;

  /// 清理。
  ///
  /// @param command 命令
  /// @return 计数
  @Override
  public CleanupResult handle(CleanupDeletedUsersCommand command) {
    int scanned = 0;
    int deletedUsers = 0;
    int libraryItems = 0;
    int collections = 0;
    int collectionItems = 0;
    int failedBatches = 0;
    long cursor = 0L;
    List<Long> batch;
    while (!(batch = reads.findOwnersAfter(cursor, BATCH)).isEmpty()) {
      long after = cursor;
      cursor = batch.getLast();
      scanned += batch.size();
      Map<Long, UserCard> cards;
      try {
        cards = users.findCards(batch, null);
      } catch (UserDirectoryUnavailableException ex) {
        failedBatches++;
        log.warn("注销清理跳过一批: afterUserId={}, users={}", after, batch.size());
        continue;
      }
      for (Long userId : batch) {
        UserCard card = cards.get(userId);
        if (card == null) {
          log.warn("注销清理: identity 不认识这个用户，不删: userId={}", userId);
          continue;
        }
        if (card.state() != AccountState.DELETED) {
          continue;
        }
        DeletedUserData deleted =
            transactions.execute(
                status -> {
                  library.lockOwner(userId);
                  return library.deleteAllOf(userId);
                });
        deletedUsers++;
        libraryItems += deleted.libraryItems();
        collections += deleted.collections();
        collectionItems += deleted.collectionItems();
      }
    }
    log.info(
        "注销清理完成: scannedUsers={}, deletedUsers={}, libraryItems={}, collections={},"
            + " collectionItems={}, failedBatches={}",
        scanned,
        deletedUsers,
        libraryItems,
        collections,
        collectionItems,
        failedBatches);
    return CleanupResult.builder()
        .scannedUsers(scanned)
        .deletedUsers(deletedUsers)
        .libraryItems(libraryItems)
        .collections(collections)
        .collectionItems(collectionItems)
        .failedBatches(failedBatches)
        .build();
  }
}
