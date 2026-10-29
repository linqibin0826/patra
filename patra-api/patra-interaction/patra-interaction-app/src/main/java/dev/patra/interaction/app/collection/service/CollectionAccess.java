package dev.patra.interaction.app.collection.service;

import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 公开收藏夹的可见性（spec §5.4）：所有者本人总能读（私有、公开都行），不经 identity 判定，identity 只用来拼他的
/// 摘要、不可用时摘要为空；别人只能读公开的，且 identity 判定所有者
/// 的主页对这位访客可见（identity 省略所有者也算不可见）。不可见一律 404，不区分原因；identity 不可用是 503。
@Component
@RequiredArgsConstructor
public class CollectionAccess {

  private final LibraryReadPort reads;
  private final UserDirectoryPort users;

  /// 打开一个收藏夹。
  ///
  /// @param id 收藏夹
  /// @param viewer 访客的用户 ID，匿名为空
  /// @return 可读的收藏夹与所有者摘要
  /// @throws CollectionNotFoundException 不存在或对他不可见
  public ReadableCollection open(CollectionId id, Optional<Long> viewer) {
    CollectionSummary collection =
        reads.findCollection(id).orElseThrow(CollectionNotFoundException::new);
    long ownerId = collection.ownerId();
    boolean viewerIsOwner = viewer.isPresent() && viewer.get() == ownerId;
    if (!viewerIsOwner && !collection.isPublic()) {
      throw new CollectionNotFoundException();
    }
    if (viewerIsOwner) {
      return ReadableCollection.of(collection, ownerCardIfAvailable(ownerId), true);
    }
    UserCard owner = users.findCards(List.of(ownerId), viewer.orElse(null)).get(ownerId);
    if (owner == null || !owner.profileVisible()) {
      throw new CollectionNotFoundException();
    }
    return ReadableCollection.of(collection, owner, false);
  }

  /// 本人看自己的收藏夹时的所有者摘要：只用来拼昵称、头像，不判可见性；identity 不可用或省略时为 `null`，不让请求失败。
  ///
  /// @param ownerId 所有者
  /// @return 摘要，可空
  private UserCard ownerCardIfAvailable(long ownerId) {
    try {
      return users.findCards(List.of(ownerId), ownerId).get(ownerId);
    } catch (UserDirectoryUnavailableException ex) {
      return null;
    }
  }
}
