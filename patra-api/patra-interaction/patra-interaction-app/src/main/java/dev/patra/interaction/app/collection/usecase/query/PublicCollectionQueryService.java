package dev.patra.interaction.app.collection.usecase.query;

import dev.patra.interaction.app.collection.service.CollectionAccess;
import dev.patra.interaction.app.collection.service.ReadableCollection;
import dev.patra.interaction.app.collection.service.SubjectViews;
import dev.patra.interaction.domain.collection.model.enums.LibrarySort;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/// 别人能看的收藏夹：公开收藏夹页（头部、条目、导出 key）与主页的公开收藏夹列表。登录可选，访客 ID 透传给 identity。
@Component
public class PublicCollectionQueryService {

  private final CollectionAccess access;
  private final LibraryReadPort reads;
  private final UserDirectoryPort users;
  private final ItemPaging paging;
  private final CurrentUserPort currentUser;

  /// 创建。
  ///
  /// @param access 可见性判定
  /// @param reads 读端口
  /// @param users identity
  /// @param subjectViews 分块取视图
  /// @param currentUser 当前用户
  public PublicCollectionQueryService(
      CollectionAccess access,
      LibraryReadPort reads,
      UserDirectoryPort users,
      SubjectViews subjectViews,
      CurrentUserPort currentUser) {
    this.access = access;
    this.reads = reads;
    this.users = users;
    this.paging = new ItemPaging(subjectViews);
    this.currentUser = currentUser;
  }

  /// 收藏夹头部。
  ///
  /// @param collectionId 收藏夹
  /// @return 头部
  public PublicCollectionView collection(long collectionId) {
    ReadableCollection readable = open(collectionId);
    return PublicCollectionView.of(
        readable.collection(), readable.owner(), readable.viewerIsOwner());
  }

  /// 条目的一页（不带备注与归属）。
  ///
  /// @param collectionId 收藏夹
  /// @param sort 排序，可空
  /// @param page 页码
  /// @param size 每页条数
  /// @return 一页
  public PageView<PublicItemView> items(long collectionId, String sort, int page, int size) {
    CollectionSummary collection = open(collectionId).collection();
    long ownerId = collection.ownerId();
    CollectionId id = collection.collectionId();
    OrderedPage ordered =
        paging.page(
            LibrarySort.fromParam(sort),
            page,
            size,
            offset -> reads.pageCollection(ownerId, id, offset, size),
            () -> (long) collection.itemCount(),
            () -> reads.allCollection(ownerId, id));
    List<PublicItemView> items =
        ordered.refs().stream()
            .map(
                ref -> {
                  SubjectView view = ordered.views().get(ref.subjectId());
                  PublicItemView.PublicItemViewBuilder builder =
                      PublicItemView.builder().itemId(ref.subjectId()).addedAt(ref.at());
                  return view == null
                      ? builder
                          .type(SubjectType.PUBLICATION)
                          .state(SubjectState.UNAVAILABLE)
                          .build()
                      : builder
                          .type(view.type())
                          .key(view.key())
                          .state(view.state())
                          .title(SubjectFields.unavailableTitle(view))
                          .build();
                })
            .toList();
    return PageView.of(items, page, size, ordered.total());
  }

  /// 可导出的 key（对这位访客可见的收藏夹）。
  ///
  /// @param collectionId 收藏夹
  /// @param sort 排序，可空
  /// @return key
  public ExportKeysView keys(long collectionId, String sort) {
    CollectionSummary collection = open(collectionId).collection();
    return paging.keys(
        reads.allCollection(collection.ownerId(), collection.collectionId()),
        LibrarySort.fromParam(sort));
  }

  /// 主页的公开收藏夹：本人直接回自己的公开收藏夹；别人只有 identity 判定主页对他可见时才回，否则空列表。
  ///
  /// @param userId 主页的主人
  /// @return 公开收藏夹，按更新时间倒序
  public List<CollectionSummary> userCollections(long userId) {
    Optional<Long> viewer = viewer();
    if (viewer.isPresent() && viewer.get() == userId) {
      return reads.findPublicCollections(userId);
    }
    UserCard owner = users.findCards(List.of(userId), viewer.orElse(null)).get(userId);
    if (owner == null || !owner.profileVisible()) {
      return List.of();
    }
    return reads.findPublicCollections(userId);
  }

  /// 判可见并打开。
  ///
  /// @param collectionId 收藏夹
  /// @return 可读收藏夹
  private ReadableCollection open(long collectionId) {
    return access.open(CollectionId.of(collectionId), viewer());
  }

  /// 访客的用户 ID，匿名为空。
  ///
  /// @return 用户 ID
  private Optional<Long> viewer() {
    return currentUser.current().map(CurrentUser::userId);
  }
}
