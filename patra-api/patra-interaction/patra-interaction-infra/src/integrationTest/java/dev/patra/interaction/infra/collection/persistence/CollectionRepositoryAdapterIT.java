package dev.patra.interaction.infra.collection.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.collection.port.repository.CollectionRepository;
import dev.patra.interaction.domain.collection.port.repository.LibraryRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 收藏夹的写端：建、改、重名、可见性、放进 / 移出与篇数。
class CollectionRepositoryAdapterIT extends CollectionPersistenceITSupport {

  @Autowired private CollectionRepository collections;
  @Autowired private LibraryRepository library;

  /// 新建的收藏夹是私有、空的。
  @Test
  void should_create_private_empty_collection() {
    CollectionSummary c = tx(() -> collections.create(U, name("组会"), "每周四", T0));

    assertThat(c.ownerId()).isEqualTo(U);
    assertThat(c.name()).isEqualTo("组会");
    assertThat(c.description()).isEqualTo("每周四");
    assertThat(c.visibility()).isEqualTo(CollectionVisibility.PRIVATE);
    assertThat(c.itemCount()).isZero();
    assertThat(c.createdAt()).isEqualTo(T0);
    assertThat(c.updatedAt()).isEqualTo(T0);
    assertThat(collections.countCollections(U)).isEqualTo(1);
    assertThat(collections.findOwned(V, c.collectionId())).isEmpty();
  }

  /// 重名检查可以排除自己（改名时）。
  @Test
  void should_detect_taken_name_excluding_self() {
    CollectionSummary c = tx(() -> collections.create(U, name("组会"), null, T0));

    assertThat(collections.nameTaken(U, "组会", null)).isTrue();
    assertThat(collections.nameTaken(U, "组会", c.collectionId())).isFalse();
    assertThat(collections.nameTaken(V, "组会", null)).isFalse();
    assertThat(collections.findNames(U)).isEqualTo(Set.of("组会"));
  }

  /// 改名称简介、改可见性都把更新时间写成传入的时刻。
  @Test
  void should_update_text_and_visibility() {
    CollectionSummary c = tx(() -> collections.create(U, name("组会"), "旧", T0));

    CollectionSummary renamed =
        tx(() -> collections.updateText(U, c.collectionId(), name("周会"), null, T1));
    assertThat(renamed.name()).isEqualTo("周会");
    assertThat(renamed.description()).isNull();
    assertThat(renamed.updatedAt()).isEqualTo(T1);

    CollectionSummary published =
        tx(
            () ->
                collections.updateVisibility(U, c.collectionId(), CollectionVisibility.PUBLIC, T2));
    assertThat(published.visibility()).isEqualTo(CollectionVisibility.PUBLIC);
    assertThat(published.updatedAt()).isEqualTo(T2);
  }

  /// 放进收藏夹按序写加入时间（每个递减 1 微秒），篇数与更新时间跟着变。
  @Test
  void should_preserve_order_with_microsecond_offsets() {
    CollectionSummary c = tx(() -> collections.create(U, name("组会"), null, T0));

    txRun(() -> collections.addMembers(U, c.collectionId(), List.of(S1, S2, S3), T1));

    assertThat(addedAt(c, S1)).isEqualTo(Timestamp.from(T1));
    assertThat(addedAt(c, S2)).isEqualTo(Timestamp.from(T1.minus(1, ChronoUnit.MICROS)));
    assertThat(addedAt(c, S3)).isEqualTo(Timestamp.from(T1.minus(2, ChronoUnit.MICROS)));
    CollectionSummary now = collections.findOwned(U, c.collectionId()).orElseThrow();
    assertThat(now.itemCount()).isEqualTo(3);
    assertThat(now.updatedAt()).isEqualTo(T1);
    assertThat(collections.findMembers(U, c.collectionId(), List.of(S1, S2, SubjectId.of(19L))))
        .isEqualTo(Set.of(S1, S2));
  }

  /// 复制时同一时刻建夹再放条目：更新时间就是写入的时刻，不会被换成数据库时间、早于创建时间。
  @Test
  void should_keep_explicit_updated_at_when_written_at_creation_instant() {
    CollectionSummary c = tx(() -> collections.create(U, name("组会"), null, T1));
    txRun(() -> collections.addMembers(U, c.collectionId(), List.of(S1), T1));

    CollectionSummary now = collections.findOwned(U, c.collectionId()).orElseThrow();
    assertThat(now.updatedAt()).isEqualTo(T1);
    assertThat(now.updatedAt()).isAfterOrEqualTo(now.createdAt());
  }

  /// 移出回实际移出的篇数。
  @Test
  void should_report_removed_count() {
    CollectionSummary c = tx(() -> collections.create(U, name("组会"), null, T0));
    txRun(() -> collections.addMembers(U, c.collectionId(), List.of(S1, S2), T1));

    assertThat(tx(() -> collections.removeMembers(U, c.collectionId(), List.of(S1, S3), T2)))
        .isEqualTo(1);

    CollectionSummary now = collections.findOwned(U, c.collectionId()).orElseThrow();
    assertThat(now.itemCount()).isEqualTo(1);
    assertThat(now.updatedAt()).isEqualTo(T2);
    assertThat(tx(() -> collections.removeMembers(U, c.collectionId(), List.of(S3), T2))).isZero();
    assertThat(library.countItems(U)).isZero();
  }

  /// 读加入时间。
  ///
  /// @param c 收藏夹
  /// @param subject 对象
  /// @return 加入时间
  private Timestamp addedAt(CollectionSummary c, SubjectId subject) {
    return jdbcTemplate.queryForObject(
        "SELECT added_at FROM collection.collection_item WHERE user_id = ? AND collection_id = ?"
            + " AND subject_id = ?",
        Timestamp.class,
        U,
        c.collectionId().value(),
        subject.value());
  }
}
