package dev.patra.interaction.app.collection.usecase.query;

import dev.patra.interaction.app.collection.service.SubjectViews;
import dev.patra.interaction.domain.collection.exception.ExportTooLargeException;
import dev.patra.interaction.domain.collection.exception.InvalidCollectionRequestException;
import dev.patra.interaction.domain.collection.model.enums.LibrarySort;
import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.collection.policy.CollectionLimits;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/// 分页与导出 key 的公共算法（spec §5.6、§5.9）：`ADDED` 走 SQL 分页，其余排序取全量、取快照、在内存里排好再切页。
/// 两个查询服务各持有一个。
final class ItemPaging {

  /// 每页最多条数。
  static final int MAX_PAGE_SIZE = 100;

  private final SubjectViews subjectViews;

  /// 创建。
  ///
  /// @param subjectViews 分块取视图
  ItemPaging(SubjectViews subjectViews) {
    this.subjectViews = subjectViews;
  }

  /// 一页。
  ///
  /// @param sort 排序
  /// @param page 页码，从 1 开始
  /// @param size 每页条数
  /// @param sqlPage `ADDED` 时按 offset 取一页
  /// @param sqlTotal `ADDED` 时的总条数
  /// @param all 其余排序时取全量
  /// @return 一页
  OrderedPage page(
      LibrarySort sort,
      int page,
      int size,
      IntFunction<List<ItemRef>> sqlPage,
      Supplier<Long> sqlTotal,
      Supplier<List<ItemRef>> all) {
    requirePaging(page, size);
    long start = (long) (page - 1) * size;
    if (start >= CollectionLimits.LIBRARY_MAX_ITEMS) {
      // 任何视图都不超过文献库上限：再往后必然是空页，也避免 offset 溢出 int
      long total = sort == LibrarySort.ADDED ? sqlTotal.get() : all.get().size();
      return new OrderedPage(List.of(), total, Map.of());
    }
    int offset = (int) start;
    if (sort == LibrarySort.ADDED) {
      List<ItemRef> refs = sqlPage.apply(offset);
      return new OrderedPage(refs, sqlTotal.get(), subjectViews.fetch(ids(refs)));
    }
    List<ItemRef> everything = all.get();
    Map<SubjectId, SubjectView> views = subjectViews.fetch(ids(everything));
    List<ItemRef> ordered = LibraryOrdering.order(everything, sort, views);
    List<ItemRef> refs =
        offset >= ordered.size()
            ? List.of()
            : ordered.subList(offset, Math.min(ordered.size(), offset + size));
    return new OrderedPage(refs, ordered.size(), views);
  }

  /// 可导出的 key：正常且有 key 的，顺序与同一排序下的列表一致；超过 5,000 拒绝。
  ///
  /// @param everything 范围里的全部引用
  /// @param sort 排序
  /// @return key 与跳过条数
  ExportKeysView keys(List<ItemRef> everything, LibrarySort sort) {
    Map<SubjectId, SubjectView> views = subjectViews.fetch(ids(everything));
    List<String> keys =
        LibraryOrdering.order(everything, sort, views).stream()
            .map(ref -> views.get(ref.subjectId()))
            .filter(v -> v != null && v.state() == SubjectState.ACTIVE && v.key() != null)
            .map(SubjectView::key)
            .toList();
    if (keys.size() > CollectionLimits.MAX_EXPORT) {
      throw new ExportTooLargeException(keys.size());
    }
    return ExportKeysView.of(keys, everything.size() - keys.size());
  }

  /// 页码 ≥ 1、每页 1 ~ 100。
  ///
  /// @param page 页码
  /// @param size 每页条数
  private static void requirePaging(int page, int size) {
    if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidCollectionRequestException("page 从 1 开始，size 在 1 ~ 100");
    }
  }

  /// 取 ID。
  ///
  /// @param refs 引用
  /// @return ID
  private static List<SubjectId> ids(List<ItemRef> refs) {
    return refs.stream().map(ItemRef::subjectId).toList();
  }
}
