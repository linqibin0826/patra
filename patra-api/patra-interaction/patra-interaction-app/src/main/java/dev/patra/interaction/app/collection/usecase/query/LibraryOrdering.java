package dev.patra.interaction.app.collection.usecase.query;

import dev.patra.interaction.domain.collection.model.enums.LibrarySort;
import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/// 文献库与收藏夹列表的排序（spec §5.6）。排序数据来自登记表快照：
///
/// - `ADDED`：加入时间倒序，同时间按对象 ID 倒序（与 SQL 分页的顺序一致）。
/// - `PUBLISHED`：快照 `publishedDate` 字典序倒序——降精度 ISO 字符串这样排即「年、月、日依次降序，只到年的排在同年
///   有月份的之后」；没有日期的排最后；同值按 `ADDED`。
/// - `JOURNAL`：快照 `journal` 不分大小写升序；没有期刊的排最后；同值按 `ADDED`。
///
/// 查不到视图的按空快照处理。
public final class LibraryOrdering {

  private static final Comparator<ItemRef> ADDED =
      Comparator.comparing(ItemRef::at)
          .thenComparing(r -> r.subjectId().value(), Comparator.naturalOrder())
          .reversed();

  /// 只有静态方法。
  private LibraryOrdering() {}

  /// 排序。
  ///
  /// @param refs 引用
  /// @param sort 排序
  /// @param views 对象 → 视图（`ADDED` 不用）
  /// @return 排好的新列表
  public static List<ItemRef> order(
      List<ItemRef> refs, LibrarySort sort, Map<SubjectId, SubjectView> views) {
    Comparator<ItemRef> comparator =
        switch (sort) {
          case ADDED -> ADDED;
          case PUBLISHED ->
              byKey(views, SubjectSnapshot::publishedDate, Comparator.<String>reverseOrder());
          case JOURNAL ->
              byKey(
                  views,
                  s -> s.journal() == null ? null : s.journal().toLowerCase(Locale.ROOT),
                  Comparator.<String>naturalOrder());
        };
    return refs.stream().sorted(comparator).toList();
  }

  /// 按快照里的一个键排，空值在后，同值按加入时间。
  ///
  /// @param views 视图
  /// @param key 取键
  /// @param order 键的顺序
  /// @return 比较器
  private static Comparator<ItemRef> byKey(
      Map<SubjectId, SubjectView> views,
      Function<SubjectSnapshot, String> key,
      Comparator<String> order) {
    Function<ItemRef, String> extract =
        ref -> {
          SubjectView view = views.get(ref.subjectId());
          return view == null || view.snapshot() == null ? null : key.apply(view.snapshot());
        };
    return Comparator.comparing(extract, Comparator.nullsLast(order)).thenComparing(ADDED);
  }
}
