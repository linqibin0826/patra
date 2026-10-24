package dev.patra.interaction.app.collection.usecase.query;

import dev.patra.interaction.app.collection.service.Batches;
import dev.patra.interaction.app.collection.service.SubjectViews;
import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.exception.InvalidCollectionRequestException;
import dev.patra.interaction.domain.collection.exception.LibraryItemNotFoundException;
import dev.patra.interaction.domain.collection.model.enums.LibrarySort;
import dev.patra.interaction.domain.collection.model.enums.LibraryView;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.collection.model.read.LibraryCounts;
import dev.patra.interaction.domain.collection.model.read.LibraryEntry;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.policy.CollectionLimits;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/// 当前用户自己的文献库与收藏夹的查询（都要登录）。
@Component
public class LibraryQueryService {

  private final LibraryReadPort reads;
  private final SubjectReadPort subjects;
  private final ItemPaging paging;
  private final CurrentUserPort currentUser;

  /// 创建。
  ///
  /// @param reads 读端口
  /// @param subjects 登记表读端口
  /// @param subjectViews 分块取视图
  /// @param currentUser 当前用户
  public LibraryQueryService(
      LibraryReadPort reads,
      SubjectReadPort subjects,
      SubjectViews subjectViews,
      CurrentUserPort currentUser) {
    this.reads = reads;
    this.subjects = subjects;
    this.paging = new ItemPaging(subjectViews);
    this.currentUser = currentUser;
  }

  /// 概览。
  ///
  /// @return 两个计数与全部收藏夹
  public LibraryOverviewView overview() {
    long userId = currentUser.require().userId();
    LibraryCounts counts = reads.countLibrary(userId);
    return LibraryOverviewView.of(counts.total(), counts.unfiled(), reads.findCollections(userId));
  }

  /// 文献库的一页。
  ///
  /// @param view `all` / `unfiled`，可空
  /// @param sort `added` / `published` / `journal`，可空
  /// @param page 页码
  /// @param size 每页条数
  /// @return 一页
  public PageView<LibraryItemView> libraryItems(String view, String sort, int page, int size) {
    long userId = currentUser.require().userId();
    LibraryView v = LibraryView.fromParam(view);
    OrderedPage ordered =
        paging.page(
            LibrarySort.fromParam(sort),
            page,
            size,
            offset -> reads.pageLibrary(userId, v, offset, size),
            () -> reads.countView(userId, v),
            () -> reads.allLibrary(userId, v));
    return PageView.of(items(userId, ordered, false), page, size, ordered.total());
  }

  /// 自己的一个收藏夹的一页（条目带加入该夹的时间）。
  ///
  /// @param collectionId 收藏夹
  /// @param sort 排序，可空
  /// @param page 页码
  /// @param size 每页条数
  /// @return 一页
  public PageView<LibraryItemView> collectionItems(
      long collectionId, String sort, int page, int size) {
    long userId = currentUser.require().userId();
    CollectionSummary collection = owned(userId, collectionId);
    CollectionId id = collection.collectionId();
    OrderedPage ordered =
        paging.page(
            LibrarySort.fromParam(sort),
            page,
            size,
            offset -> reads.pageCollection(userId, id, offset, size),
            () -> (long) collection.itemCount(),
            () -> reads.allCollection(userId, id));
    return PageView.of(items(userId, ordered, true), page, size, ordered.total());
  }

  /// 文献库里的一条（收藏、改备注之后回给门户）。
  ///
  /// @param itemId 条目
  /// @return 条目
  /// @throws LibraryItemNotFoundException 不在库里
  public LibraryItemView item(SubjectId itemId) {
    long userId = currentUser.require().userId();
    LibraryEntry entry = reads.findEntries(userId, List.of(itemId)).get(itemId);
    if (entry == null) {
      throw new LibraryItemNotFoundException();
    }
    OrderedPage single =
        new OrderedPage(
            List.of(ItemRef.of(itemId, entry.collectedAt())),
            1,
            subjects.findByIds(List.of(itemId)));
    return items(userId, single, false).getFirst();
  }

  /// 批量「我的状态」：每个去重后的 key 一行，按请求顺序。
  ///
  /// @param type 对外类型名
  /// @param keys 1 ~ 100 个 key
  /// @return 状态
  public List<ItemStatusView> status(String type, List<String> keys) {
    long userId = currentUser.require().userId();
    if (keys == null || keys.isEmpty() || keys.size() > CollectionLimits.MAX_BATCH) {
      throw new InvalidCollectionRequestException(
          "一次 1 ~ " + CollectionLimits.MAX_BATCH + " 个 key");
    }
    SubjectType subjectType = Batches.subjectType(type);
    List<String> unique = List.copyOf(new LinkedHashSet<>(keys));
    Map<String, SubjectId> ids = subjects.findIdsByKeys(subjectType, unique);
    List<SubjectId> known = unique.stream().filter(ids::containsKey).map(ids::get).toList();
    Map<SubjectId, LibraryEntry> entries =
        known.isEmpty() ? Map.of() : reads.findEntries(userId, known);
    Map<SubjectId, List<CollectionId>> memberships =
        known.isEmpty() ? Map.of() : reads.findMemberships(userId, known);
    List<ItemStatusView> result = new ArrayList<>(unique.size());
    for (String key : unique) {
      SubjectId id = ids.get(key);
      LibraryEntry entry = id == null ? null : entries.get(id);
      if (entry == null) {
        result.add(ItemStatusView.builder().key(key).collected(false).build());
      } else {
        result.add(
            ItemStatusView.builder()
                .key(key)
                .collected(true)
                .itemId(id)
                .collectedAt(entry.collectedAt())
                .collectionIds(memberships.getOrDefault(id, List.of()))
                .hasNote(entry.note() != null)
                .build());
      }
    }
    return result;
  }

  /// 文献库（全部 / 未分类）可导出的 key。
  ///
  /// @param view 视图，可空
  /// @param sort 排序，可空
  /// @return key
  public ExportKeysView libraryKeys(String view, String sort) {
    long userId = currentUser.require().userId();
    return paging.keys(
        reads.allLibrary(userId, LibraryView.fromParam(view)), LibrarySort.fromParam(sort));
  }

  /// 自己的一个收藏夹可导出的 key。
  ///
  /// @param collectionId 收藏夹
  /// @param sort 排序，可空
  /// @return key
  public ExportKeysView collectionKeys(long collectionId, String sort) {
    long userId = currentUser.require().userId();
    CollectionSummary collection = owned(userId, collectionId);
    return paging.keys(
        reads.allCollection(userId, collection.collectionId()), LibrarySort.fromParam(sort));
  }

  /// 自己的收藏夹，不是自己的 404。
  ///
  /// @param userId 用户
  /// @param collectionId 收藏夹
  /// @return 收藏夹
  private CollectionSummary owned(long userId, long collectionId) {
    return reads
        .findCollection(CollectionId.of(collectionId))
        .filter(c -> c.ownerId() == userId)
        .orElseThrow(CollectionNotFoundException::new);
  }

  /// 组装条目视图。
  ///
  /// @param userId 用户
  /// @param page 一页引用与视图
  /// @param inCollection 是否收藏夹视图（带加入该夹的时间）
  /// @return 条目
  private List<LibraryItemView> items(long userId, OrderedPage page, boolean inCollection) {
    List<SubjectId> ids = page.ids();
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<SubjectId, LibraryEntry> entries = reads.findEntries(userId, ids);
    Map<SubjectId, List<CollectionId>> memberships = reads.findMemberships(userId, ids);
    return page.refs().stream()
        .map(
            ref -> {
              LibraryEntry entry = entries.get(ref.subjectId());
              return withSubject(LibraryItemView.builder(), ref.subjectId(), page.views())
                  .note(entry == null ? null : entry.note())
                  .collectedAt(entry == null ? null : entry.collectedAt())
                  .addedAt(inCollection ? ref.at() : null)
                  .collectionIds(memberships.getOrDefault(ref.subjectId(), List.of()))
                  .build();
            })
        .toList();
  }

  /// 从登记对象视图填类型、key、状态与失效标题；查不到视图（登记行不会被删，理论上没有）按失效处理。
  ///
  /// @param builder 构建器
  /// @param id 对象
  /// @param views 视图
  /// @return 同一个构建器
  private static LibraryItemView.LibraryItemViewBuilder withSubject(
      LibraryItemView.LibraryItemViewBuilder builder,
      SubjectId id,
      Map<SubjectId, SubjectView> views) {
    SubjectView view = views.get(id);
    builder.itemId(id);
    if (view == null) {
      return builder.type(SubjectType.PUBLICATION).state(SubjectState.UNAVAILABLE);
    }
    return builder
        .type(view.type())
        .key(view.key())
        .state(view.state())
        .title(SubjectFields.unavailableTitle(view));
  }
}
