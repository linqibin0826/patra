package dev.patra.interaction.app.collection.service;

import dev.patra.interaction.domain.collection.model.read.DeletedUserData;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.port.gateway.UserDataCleanupGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// collection 登记的注销清理动作：已注销用户的文献库条目、收藏夹与收藏夹条目。别人复制过的收藏夹是别人的行，
/// 不在他名下，天然不受影响。
@Component
@RequiredArgsConstructor
public class CollectionUserDataCleanupGatewayImpl implements UserDataCleanupGateway {

  private final LibraryReadPort reads;
  private final LibraryRepository library;

  /// 子域名。
  ///
  /// @return `collection`
  @Override
  public String subdomain() {
    return "collection";
  }

  /// 有收藏数据的用户：文献库条目与收藏夹两表的并集（只有收藏夹没有条目的人也要扫到）。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return reads.findOwnersAfter(afterUserId, limit);
  }

  /// 先拿用户锁（和他可能残留的请求串行），再删三张表里他的全部行。
  ///
  /// @param userId 已注销的用户
  /// @return 三张表删掉的行数之和
  @Override
  public int deleteAllOf(long userId) {
    library.lockOwner(userId);
    DeletedUserData deleted = library.deleteAllOf(userId);
    return deleted.libraryItems() + deleted.collections() + deleted.collectionItems();
  }
}
