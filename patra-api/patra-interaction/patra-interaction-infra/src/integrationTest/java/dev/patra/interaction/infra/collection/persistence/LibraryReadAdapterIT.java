package dev.patra.interaction.infra.collection.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.enums.LibraryView;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.model.read.ItemRef;
import dev.patra.interaction.domain.collection.model.read.LibraryCounts;
import dev.patra.interaction.domain.collection.model.read.LibraryEntry;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.collection.port.read.LibraryReadPort;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 文献库与收藏夹的读端：计数、按加入时间分页、全量、归属、公开列表、注销清理的用户游标。
class LibraryReadAdapterIT extends CollectionPersistenceITSupport {

  @Autowired private LibraryReadPort reads;
  @Autowired private LibraryRepository library;
  @Autowired private CollectionRepository collections;

  /// 全部与未分类的计数。
  @Test
  void should_count_total_and_unfiled() {
    CollectionSummary c = seedThreeWithS1InCollection();

    assertThat(reads.countLibrary(U)).isEqualTo(LibraryCounts.of(3, 2));
    assertThat(reads.countView(U, LibraryView.ALL)).isEqualTo(3L);
    assertThat(reads.countView(U, LibraryView.UNFILED)).isEqualTo(2L);
    assertThat(reads.countLibrary(V)).isEqualTo(LibraryCounts.of(0, 0));
    assertThat(c.itemCount()).isZero();
  }

  /// 文献库按收藏时间倒序分页。
  @Test
  void should_page_library_by_collected_time_desc() {
    seedThreeWithS1InCollection();

    assertThat(reads.pageLibrary(U, LibraryView.ALL, 0, 2))
        .containsExactly(ItemRef.of(S2, T2), ItemRef.of(S3, T1));
    assertThat(reads.pageLibrary(U, LibraryView.ALL, 2, 2)).containsExactly(ItemRef.of(S1, T0));
    assertThat(reads.allLibrary(U, LibraryView.ALL))
        .extracting(ItemRef::subjectId)
        .containsExactly(S2, S3, S1);
  }

  /// 未分类只含不在任何收藏夹里的。
  @Test
  void should_page_unfiled_only() {
    seedThreeWithS1InCollection();

    assertThat(reads.pageLibrary(U, LibraryView.UNFILED, 0, 10))
        .extracting(ItemRef::subjectId)
        .containsExactly(S2, S3);
    assertThat(reads.allLibrary(U, LibraryView.UNFILED))
        .extracting(ItemRef::subjectId)
        .containsExactly(S2, S3);
  }

  /// 收藏夹按加入该夹的时间倒序。
  @Test
  void should_page_collection_by_added_time_desc() {
    CollectionSummary c = tx(() -> collections.create(U, name("组会"), null, T0));
    txRun(
        () -> {
          library.insertItems(U, List.of(S1, S2, S3), T0);
          collections.addMembers(U, c.collectionId(), List.of(S3, S1), T2);
          collections.addMembers(U, c.collectionId(), List.of(S2), T1);
        });

    assertThat(reads.pageCollection(U, c.collectionId(), 0, 2))
        .containsExactly(ItemRef.of(S3, T2), ItemRef.of(S1, T2.minus(1, ChronoUnit.MICROS)));
    assertThat(reads.allCollection(U, c.collectionId()))
        .extracting(ItemRef::subjectId)
        .containsExactly(S3, S1, S2);
  }

  /// 条目与归属按对象聚合；没有归属的不在 map 里。
  @Test
  void should_map_entries_and_memberships() {
    CollectionSummary a = tx(() -> collections.create(U, name("A"), null, T0));
    CollectionSummary b = tx(() -> collections.create(U, name("B"), null, T0));
    txRun(
        () -> {
          library.insertItem(U, S1, T0);
          library.insertItem(U, S2, T1);
          library.updateNote(U, S1, "备注");
          collections.addMembers(U, a.collectionId(), List.of(S1), T1);
          collections.addMembers(U, b.collectionId(), List.of(S1), T2);
        });

    Map<SubjectId, LibraryEntry> entries = reads.findEntries(U, List.of(S1, S2, S3));
    assertThat(entries).hasSize(2);
    assertThat(entries.get(S1)).isEqualTo(LibraryEntry.of(S1, "备注", T0));
    Map<SubjectId, List<CollectionId>> memberships = reads.findMemberships(U, List.of(S1, S2));
    assertThat(memberships).containsOnlyKeys(S1);
    assertThat(memberships.get(S1)).containsExactlyInAnyOrder(a.collectionId(), b.collectionId());
  }

  /// 我的收藏夹与公开收藏夹都按更新时间倒序；按 ID 取不限所有者。
  @Test
  void should_list_collections_by_update_desc() {
    CollectionSummary old = tx(() -> collections.create(U, name("旧"), null, T0));
    CollectionSummary fresh = tx(() -> collections.create(U, name("新"), null, T1));
    txRun(
        () ->
            collections.updateVisibility(
                U, old.collectionId(), CollectionVisibility.PUBLIC, T0.plusSeconds(1)));

    assertThat(reads.findCollections(U))
        .extracting(CollectionSummary::name)
        .containsExactly("新", "旧");
    assertThat(reads.findPublicCollections(U))
        .extracting(CollectionSummary::name)
        .containsExactly("旧");
    assertThat(reads.findCollection(fresh.collectionId()))
        .get()
        .extracting(CollectionSummary::ownerId)
        .isEqualTo(U);
    assertThat(reads.findCollection(CollectionId.of(999L))).isEmpty();
  }

  /// 有数据的用户：两表并集、升序、可续。
  @Test
  void should_iterate_owners_from_both_tables() {
    long w = 1003L;
    txRun(
        () -> {
          library.insertItem(U, S1, T0);
          collections.create(V, name("空夹"), null, T0);
          library.insertItem(w, S1, T0);
          collections.create(w, name("夹"), null, T0);
        });

    assertThat(reads.findOwnersAfter(0L, 2)).containsExactly(U, V);
    assertThat(reads.findOwnersAfter(V, 2)).containsExactly(w);
    assertThat(reads.findOwnersAfter(w, 2)).isEmpty();
  }

  /// S1@T0、S3@T1、S2@T2 三条，S1 在一个收藏夹里。
  ///
  /// @return 收藏夹（篇数是建时的 0）
  private CollectionSummary seedThreeWithS1InCollection() {
    CollectionSummary c = tx(() -> collections.create(U, name("组会"), null, T0));
    txRun(
        () -> {
          library.insertItem(U, S1, T0);
          library.insertItem(U, S3, T1);
          library.insertItem(U, S2, T2);
          collections.addMembers(U, c.collectionId(), List.of(S1), T2);
        });
    return c;
  }
}
