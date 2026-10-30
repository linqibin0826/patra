package dev.patra.catalog.domain.publication.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.kernel.model.enums.ProvenanceCode;
import dev.patra.catalog.domain.kernel.model.vo.VenueId;
import dev.patra.catalog.domain.publication.model.vo.PublicationAuthorSnapshot;
import dev.patra.catalog.domain.publication.model.vo.VenueInstanceId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// PublicationAggregate 聚合根单元测试。
///
/// @author linqibin
/// @since 0.6.0
@DisplayName("PublicationAggregate 单元测试")
class PublicationAggregateTest {

  @Nested
  @DisplayName("作者快照")
  class AuthorSnapshotTests {

    @Test
    @DisplayName("挂载作者快照应防御性拷贝并可读取")
    void attachAuthors_shouldDefensiveCopyAndExpose() {
      PublicationAggregate agg = createMinimalAggregate();
      var authors = new ArrayList<PublicationAuthorSnapshot>();
      authors.add(
          PublicationAuthorSnapshot.builder()
              .order(1)
              .lastName("Smith")
              .displayName("Smith")
              .firstAuthor(true)
              .build());

      agg.attachAuthors(authors);
      authors.clear();

      assertThat(agg.getAuthors()).hasSize(1);
      assertThat(agg.getAuthors().getFirst().displayName()).isEqualTo("Smith");
      assertThat(agg.hasAuthors()).isTrue();
    }

    @Test
    @DisplayName("挂载 null 应归一化为空列表")
    void attachAuthors_null_shouldBecomeEmpty() {
      PublicationAggregate agg = createMinimalAggregate();

      agg.attachAuthors(null);

      assertThat(agg.getAuthors()).isEmpty();
    }

    @Test
    @DisplayName("二次挂载应整体替换而非追加")
    void attachAuthors_twice_shouldReplaceNotAppend() {
      PublicationAggregate agg = createMinimalAggregate();
      agg.attachAuthors(
          List.of(
              PublicationAuthorSnapshot.builder()
                  .order(1)
                  .lastName("Smith")
                  .displayName("Smith")
                  .firstAuthor(true)
                  .build()));

      agg.attachAuthors(
          List.of(
              PublicationAuthorSnapshot.builder()
                  .order(1)
                  .lastName("Jones")
                  .displayName("Jones")
                  .firstAuthor(true)
                  .build()));

      assertThat(agg.getAuthors()).hasSize(1);
      assertThat(agg.getAuthors().getFirst().displayName()).isEqualTo("Jones");
    }

    @Test
    @DisplayName("新建聚合根的作者列表应为空")
    void newAggregate_shouldHaveEmptyAuthors() {
      assertThat(createMinimalAggregate().getAuthors()).isEmpty();
      assertThat(createMinimalAggregate().hasAuthors()).isFalse();
    }
  }

  /// 构造仅含必填字段的最小文献聚合根。
  ///
  /// @return 文献聚合根
  private static PublicationAggregate createMinimalAggregate() {
    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        "12345678",
        null,
        VenueId.of(1L),
        VenueInstanceId.of(1L),
        "A Minimal Publication",
        null,
        null,
        null,
        null,
        2024,
        true,
        0,
        null);
  }
}
