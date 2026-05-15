package dev.linqibin.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.catalog.domain.model.enums.VenueType;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueId;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// VenueAggregate 封面对象键字段单元测试。
///
/// 验证 `imageObjectKey` 字段的 null-safe 幂等富化语义。
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@DisplayName("VenueAggregate imageObjectKey 字段测试")
class VenueAggregateImageKeyTest {

  @Nested
  @DisplayName("enrichImageObjectKey 幂等语义")
  class EnrichImageObjectKeyTests {

    @Test
    @DisplayName("restore 后传入非 null 值应更新字段")
    void shouldUpdateWhenPreviousIsNull() {
      VenueAggregate aggregate =
          VenueAggregate.restore(VenueId.of(1L), VenueType.JOURNAL, "Nature", null, 0L);
      assertThat(aggregate.getImageObjectKey()).isNull();

      aggregate.enrichImageObjectKey("catalog/venue-cover/1.jpg");

      assertThat(aggregate.getImageObjectKey()).isEqualTo("catalog/venue-cover/1.jpg");
    }

    @Test
    @DisplayName("传入新值应覆盖已有值")
    void shouldOverrideWhenPreviousExists() {
      VenueAggregate aggregate =
          VenueAggregate.restore(
              VenueId.of(1L), VenueType.JOURNAL, "Nature", "catalog/venue-cover/old.jpg", 0L);

      aggregate.enrichImageObjectKey("catalog/venue-cover/new.jpg");

      assertThat(aggregate.getImageObjectKey()).isEqualTo("catalog/venue-cover/new.jpg");
    }

    @Test
    @DisplayName("传入 null 不应清空已有值")
    void shouldIgnoreWhenInputIsNull() {
      VenueAggregate aggregate =
          VenueAggregate.restore(
              VenueId.of(1L), VenueType.JOURNAL, "Nature", "catalog/venue-cover/1.jpg", 0L);
      assertThat(aggregate.getImageObjectKey()).isEqualTo("catalog/venue-cover/1.jpg");

      aggregate.enrichImageObjectKey(null);

      assertThat(aggregate.getImageObjectKey()).isEqualTo("catalog/venue-cover/1.jpg");
    }
  }
}
