package dev.patra.analytics.domain.insight.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.patra.analytics.domain.insight.model.enums.AlsoReadSource;
import dev.patra.analytics.domain.insight.model.vo.AlsoReadItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlsoReadRuleTest {

  @Test
  void full_co_read_has_no_backfill() {
    List<AlsoReadItem> items =
        AlsoReadRule.assemble(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), List.of(mesh(8L)));

    assertThat(items).hasSize(AlsoReadRule.DISPLAY);
    assertThat(items).allMatch(i -> i.source() == AlsoReadSource.CO_READ);
    assertThat(items).extracting(AlsoReadItem::articleId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
  }

  @Test
  void backfills_after_co_read_without_duplicates() {
    List<AlsoReadItem> items =
        AlsoReadRule.assemble(
            List.of(1L, 2L, 3L), List.of(mesh(2L), mesh(4L), venue(5L), venue(6L), venue(7L)));

    assertThat(items)
        .extracting(AlsoReadItem::articleId, AlsoReadItem::source)
        .containsExactly(
            tuple(1L, AlsoReadSource.CO_READ),
            tuple(2L, AlsoReadSource.CO_READ),
            tuple(3L, AlsoReadSource.CO_READ),
            tuple(4L, AlsoReadSource.SAME_MESH),
            tuple(5L, AlsoReadSource.SAME_VENUE),
            tuple(6L, AlsoReadSource.SAME_VENUE));
    assertThat(items.get(3).meshTerm()).isEqualTo("Sepsis");
    assertThat(items.get(4).venueName()).isEqualTo("Chest");
  }

  @Test
  void no_co_read_uses_neighbors_only() {
    assertThat(AlsoReadRule.assemble(List.of(), List.of(mesh(4L), venue(5L))))
        .extracting(AlsoReadItem::articleId)
        .containsExactly(4L, 5L);
  }

  @Test
  void empty_when_nothing() {
    assertThat(AlsoReadRule.assemble(List.of(), List.of())).isEmpty();
  }

  @Test
  void co_read_item_has_no_labels() {
    AlsoReadItem item = AlsoReadItem.coRead(1L);

    assertThat(item.source()).isEqualTo(AlsoReadSource.CO_READ);
    assertThat(item.meshTerm()).isNull();
    assertThat(item.venueName()).isNull();
  }

  private static AlsoReadItem mesh(long id) {
    return AlsoReadItem.of(id, AlsoReadSource.SAME_MESH, "Sepsis", null);
  }

  private static AlsoReadItem venue(long id) {
    return AlsoReadItem.of(id, AlsoReadSource.SAME_VENUE, null, "Chest");
  }
}
