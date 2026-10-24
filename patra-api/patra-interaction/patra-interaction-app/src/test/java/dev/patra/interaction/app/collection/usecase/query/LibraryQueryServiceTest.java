package dev.patra.interaction.app.collection.usecase.query;

import static dev.patra.interaction.app.collection.Fixtures.collection;
import static dev.patra.interaction.app.collection.Fixtures.view;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.collection.TestUsers;
import dev.patra.interaction.app.collection.service.SubjectViews;
import dev.patra.interaction.domain.collection.exception.CollectionNotFoundException;
import dev.patra.interaction.domain.collection.exception.ExportTooLargeException;
import dev.patra.interaction.domain.collection.exception.InvalidCollectionRequestException;
import dev.patra.interaction.domain.collection.exception.LibraryItemNotFoundException;
import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.enums.LibraryView;
import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.collection.model.read.LibraryCounts;
import dev.patra.interaction.domain.collection.model.read.LibraryEntry;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 我的文献库的查询：分页走 SQL 或内存排序、条目视图、我的状态、导出 key。
class LibraryQueryServiceTest {

  private static final long U = 1001L;
  private static final Instant T = Instant.parse("2026-10-24T00:00:00Z");
  private static final SubjectId S1 = SubjectId.of(11L);
  private static final SubjectId S2 = SubjectId.of(12L);
  private static final SubjectId S3 = SubjectId.of(13L);
  private static final CollectionId A = CollectionId.of(500L);
  private static final CollectionId B = CollectionId.of(501L);

  private final LibraryReadPort reads = mock(LibraryReadPort.class);
  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private LibraryQueryService service;
  private Map<SubjectId, SubjectView> views;

  @BeforeEach
  void setUp() {
    service =
        new LibraryQueryService(reads, subjects, new SubjectViews(subjects), TestUsers.signedIn(U));
    views =
        Map.of(
            S1, view(11L, SubjectState.ACTIVE, "101", "lancet", "2019"),
            S2, view(12L, SubjectState.ACTIVE, "102", "BMJ", "2021"),
            S3, view(13L, SubjectState.UNAVAILABLE, null, "Cell", "2020"));
    when(subjects.findByIds(anyCollection()))
        .thenAnswer(
            inv -> {
              Collection<SubjectId> ids = inv.getArgument(0);
              return ids.stream()
                  .filter(views::containsKey)
                  .collect(Collectors.toMap(Function.identity(), views::get));
            });
  }

  /// 概览：两个计数与全部收藏夹。
  @Test
  void should_return_overview() {
    when(reads.countLibrary(U)).thenReturn(LibraryCounts.of(3, 1));
    when(reads.findCollections(U))
        .thenReturn(List.of(collection(500L, U, CollectionVisibility.PRIVATE, 2)));

    LibraryOverviewView overview = service.overview();

    assertThat(overview.total()).isEqualTo(3);
    assertThat(overview.unfiled()).isEqualTo(1);
    assertThat(overview.collections()).hasSize(1);
  }

  /// 按加入时间：SQL 分页，只对本页取视图、条目与归属。
  @Test
  void should_page_added_sort_via_sql_only_for_page_ids() {
    when(reads.pageLibrary(U, LibraryView.ALL, 50, 50)).thenReturn(List.of(ItemRef.of(S1, T)));
    when(reads.countView(U, LibraryView.ALL)).thenReturn(51L);
    when(reads.findEntries(U, List.of(S1))).thenReturn(Map.of(S1, LibraryEntry.of(S1, "备注", T)));
    when(reads.findMemberships(U, List.of(S1))).thenReturn(Map.of(S1, List.of(A, B)));

    PageView<LibraryItemView> page = service.libraryItems(null, null, 2, 50);

    assertThat(page.total()).isEqualTo(51L);
    assertThat(page.totalPages()).isEqualTo(2);
    LibraryItemView item = page.items().getFirst();
    assertThat(item.itemId()).isEqualTo(S1);
    assertThat(item.type()).isEqualTo(SubjectType.PUBLICATION);
    assertThat(item.key()).isEqualTo("101");
    assertThat(item.note()).isEqualTo("备注");
    assertThat(item.collectedAt()).isEqualTo(T);
    assertThat(item.addedAt()).isNull();
    assertThat(item.collectionIds()).containsExactly(A, B);
    verify(reads, never()).allLibrary(anyLong(), any());
  }

  /// 按期刊：取全量，在内存里排好再切页。
  @Test
  void should_sort_in_memory_for_journal() {
    when(reads.allLibrary(U, LibraryView.ALL))
        .thenReturn(List.of(ItemRef.of(S1, T), ItemRef.of(S2, T), ItemRef.of(S3, T)));

    PageView<LibraryItemView> page = service.libraryItems("all", "journal", 2, 1);

    assertThat(page.total()).isEqualTo(3L);
    assertThat(page.items()).extracting(LibraryItemView::itemId).containsExactly(S3);
    verify(reads, never()).pageLibrary(anyLong(), any(), anyInt(), anyInt());
  }

  /// 已失效才带快照标题；整行消失时没有 key。
  @Test
  void should_show_snapshot_title_only_when_unavailable() {
    when(reads.pageLibrary(U, LibraryView.ALL, 0, 50))
        .thenReturn(List.of(ItemRef.of(S1, T), ItemRef.of(S3, T)));

    List<LibraryItemView> items = service.libraryItems(null, null, 1, 50).items();

    assertThat(items.get(0).title()).isNull();
    assertThat(items.get(0).state()).isEqualTo(SubjectState.ACTIVE);
    assertThat(items.get(1).title()).isEqualTo("Title 13");
    assertThat(items.get(1).key()).isNull();
    assertThat(items.get(1).state()).isEqualTo(SubjectState.UNAVAILABLE);
  }

  /// 每页 1 ~ 100、页码 ≥ 1。
  @Test
  void should_reject_bad_paging() {
    assertThatThrownBy(() -> service.libraryItems(null, null, 0, 50))
        .isInstanceOf(InvalidCollectionRequestException.class);
    assertThatThrownBy(() -> service.libraryItems(null, null, 1, 101))
        .isInstanceOf(InvalidCollectionRequestException.class);
  }

  /// 页码大到 offset 溢出 int：回空页，不查 SQL、不抛异常。
  @Test
  void should_return_empty_page_for_huge_page_number() {
    when(reads.countView(U, LibraryView.ALL)).thenReturn(3L);

    PageView<LibraryItemView> added = service.libraryItems(null, null, 30_000_000, 100);
    PageView<LibraryItemView> journal = service.libraryItems(null, "journal", 30_000_000, 100);

    assertThat(added.items()).isEmpty();
    assertThat(added.total()).isEqualTo(3L);
    assertThat(journal.items()).isEmpty();
    verify(reads, never()).pageLibrary(anyLong(), any(), anyInt(), anyInt());
  }

  /// 自己的收藏夹：条目带加入该夹的时间；别人的 404。
  @Test
  void should_page_own_collection_with_added_time() {
    when(reads.findCollection(A))
        .thenReturn(Optional.of(collection(500L, U, CollectionVisibility.PRIVATE, 1)));
    when(reads.pageCollection(U, A, 0, 50)).thenReturn(List.of(ItemRef.of(S1, T.plusSeconds(9))));
    when(reads.findEntries(U, List.of(S1))).thenReturn(Map.of(S1, LibraryEntry.of(S1, null, T)));

    PageView<LibraryItemView> page = service.collectionItems(500L, null, 1, 50);

    assertThat(page.total()).isEqualTo(1L);
    assertThat(page.items().getFirst().addedAt()).isEqualTo(T.plusSeconds(9));
    assertThat(page.items().getFirst().collectedAt()).isEqualTo(T);
    when(reads.findCollection(A))
        .thenReturn(Optional.of(collection(500L, 7L, CollectionVisibility.PUBLIC, 1)));
    assertThatThrownBy(() -> service.collectionItems(500L, null, 1, 50))
        .isInstanceOf(CollectionNotFoundException.class);
  }

  /// 单条：不在库里 404。
  @Test
  void should_404_single_item_not_in_library() {
    when(reads.findEntries(U, List.of(S1))).thenReturn(Map.of());

    assertThatThrownBy(() -> service.item(S1)).isInstanceOf(LibraryItemNotFoundException.class);
  }

  /// 每个请求的 key 一行，按去重后的顺序；不合法、未登记的都是未收藏。
  @Test
  void should_report_status_for_every_requested_key() {
    when(subjects.findIdsByKeys(SubjectType.PUBLICATION, List.of("101", "x", "102")))
        .thenReturn(Map.of("101", S1, "102", S2));
    when(reads.findEntries(U, List.of(S1, S2))).thenReturn(Map.of(S1, LibraryEntry.of(S1, "n", T)));
    when(reads.findMemberships(U, List.of(S1, S2))).thenReturn(Map.of(S1, List.of(A, B)));

    List<ItemStatusView> status = service.status("publication", List.of("101", "x", "102", "101"));

    assertThat(status).extracting(ItemStatusView::key).containsExactly("101", "x", "102");
    assertThat(status.get(0))
        .isEqualTo(
            ItemStatusView.builder()
                .key("101")
                .collected(true)
                .itemId(S1)
                .collectedAt(T)
                .collectionIds(List.of(A, B))
                .hasNote(true)
                .build());
    assertThat(status.get(1).collected()).isFalse();
    assertThat(status.get(1).collectionIds()).isEmpty();
    assertThat(status.get(2).collected()).isFalse();
  }

  /// 101 个 key 是请求错误。
  @Test
  void should_reject_101_status_keys() {
    List<String> keys = LongStream.rangeClosed(1, 101).mapToObj(Long::toString).toList();

    assertThatThrownBy(() -> service.status("publication", keys))
        .isInstanceOf(InvalidCollectionRequestException.class);
  }

  /// 导出 key 跳过失效的，顺序与同一排序下的列表一致。
  @Test
  void should_skip_unavailable_in_export_keys_and_keep_list_order() {
    when(reads.allLibrary(U, LibraryView.ALL))
        .thenReturn(List.of(ItemRef.of(S1, T), ItemRef.of(S2, T), ItemRef.of(S3, T)));

    ExportKeysView keys = service.libraryKeys(null, "journal");

    assertThat(keys.keys()).containsExactly("102", "101");
    assertThat(keys.skippedUnavailable()).isEqualTo(1);
  }

  /// 可导出的超过 5,000 条。
  @Test
  void should_reject_export_over_5000() {
    List<ItemRef> many =
        LongStream.rangeClosed(1, 5001).mapToObj(i -> ItemRef.of(SubjectId.of(i), T)).toList();
    when(reads.allLibrary(U, LibraryView.ALL)).thenReturn(many);
    when(subjects.findByIds(anyCollection()))
        .thenAnswer(
            inv -> {
              Collection<SubjectId> ids = inv.getArgument(0);
              return ids.stream()
                  .collect(
                      Collectors.toMap(
                          Function.identity(),
                          id ->
                              view(
                                  id.value(),
                                  SubjectState.ACTIVE,
                                  Long.toString(id.value()),
                                  "J",
                                  null)));
            });

    assertThatThrownBy(() -> service.libraryKeys(null, null))
        .isInstanceOf(ExportTooLargeException.class);
  }
}
