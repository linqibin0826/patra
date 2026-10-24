package dev.patra.interaction.app.collection.usecase.query;

import static dev.patra.interaction.app.collection.Fixtures.view;
import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.collection.model.enums.LibrarySort;
import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// 三种排序：加入时间倒序；出版日期降精度字典序倒序、缺的排后；期刊不分大小写升序、缺的排后。同值都按加入时间。
class LibraryOrderingTest {

  private static final Instant T = Instant.parse("2026-10-24T00:00:00Z");

  /// 加入时间倒序，同时间按 ID 倒序。
  @Test
  void should_sort_added_desc_then_id_desc() {
    List<ItemRef> refs = List.of(ref(1, 0), ref(2, 5), ref(3, 5));

    assertThat(ids(LibraryOrdering.order(refs, LibrarySort.ADDED, Map.of())))
        .containsExactly(3L, 2L, 1L);
  }

  /// 「年、月、日依次降序，只到年的排在同年有月份的之后」，没有日期的最后。
  @Test
  void should_sort_published_desc_with_coarse_dates_after_finer_and_nulls_last() {
    Map<SubjectId, SubjectView> views = new HashMap<>();
    views.put(SubjectId.of(1), view(1, SubjectState.ACTIVE, "1", "J", "2019"));
    views.put(SubjectId.of(2), view(2, SubjectState.ACTIVE, "2", "J", "2019-03"));
    views.put(SubjectId.of(3), view(3, SubjectState.ACTIVE, "3", "J", "2020"));
    views.put(SubjectId.of(4), view(4, SubjectState.ACTIVE, "4", "J", null));
    views.put(SubjectId.of(5), view(5, SubjectState.ACTIVE, "5", "J", "2019-03-15"));
    List<ItemRef> refs = List.of(ref(1, 1), ref(2, 2), ref(3, 3), ref(4, 4), ref(5, 5));

    assertThat(ids(LibraryOrdering.order(refs, LibrarySort.PUBLISHED, views)))
        .containsExactly(3L, 5L, 2L, 1L, 4L);
  }

  /// 期刊不分大小写升序，没有期刊的（含查不到视图的）最后。
  @Test
  void should_sort_journal_case_insensitively_nulls_last() {
    Map<SubjectId, SubjectView> views = new HashMap<>();
    views.put(SubjectId.of(1), view(1, SubjectState.ACTIVE, "1", "lancet", null));
    views.put(SubjectId.of(2), view(2, SubjectState.ACTIVE, "2", "BMJ", null));
    views.put(SubjectId.of(3), view(3, SubjectState.ACTIVE, "3", null, null));
    views.put(SubjectId.of(4), view(4, SubjectState.ACTIVE, "4", "Cell", null));
    List<ItemRef> refs = List.of(ref(1, 1), ref(2, 2), ref(3, 3), ref(4, 4), ref(5, 5));

    assertThat(ids(LibraryOrdering.order(refs, LibrarySort.JOURNAL, views)))
        .containsExactly(2L, 4L, 1L, 5L, 3L);
  }

  /// 同一期刊按加入时间倒序。
  @Test
  void should_break_ties_by_added_time() {
    Map<SubjectId, SubjectView> views = new HashMap<>();
    views.put(SubjectId.of(1), view(1, SubjectState.ACTIVE, "1", "BMJ", "2019"));
    views.put(SubjectId.of(2), view(2, SubjectState.ACTIVE, "2", "BMJ", "2019"));
    List<ItemRef> refs = List.of(ref(1, 1), ref(2, 9));

    assertThat(ids(LibraryOrdering.order(refs, LibrarySort.JOURNAL, views)))
        .containsExactly(2L, 1L);
    assertThat(ids(LibraryOrdering.order(refs, LibrarySort.PUBLISHED, views)))
        .containsExactly(2L, 1L);
  }

  /// 引用。
  ///
  /// @param id 对象 ID
  /// @param seconds 加入时间（秒偏移）
  /// @return 引用
  private static ItemRef ref(long id, long seconds) {
    return ItemRef.of(SubjectId.of(id), T.plusSeconds(seconds));
  }

  /// 取 ID。
  ///
  /// @param refs 引用
  /// @return ID
  private static List<Long> ids(List<ItemRef> refs) {
    return refs.stream().map(r -> r.subjectId().value()).toList();
  }
}
