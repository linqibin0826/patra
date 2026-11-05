package dev.patra.analytics.domain.kernel.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EventNameTest {

  @Test
  void from_wire_maps_all_ten_events() {
    assertThat(EventName.values()).hasSize(10);
    Arrays.stream(EventName.values())
        .forEach(name -> assertThat(EventName.fromWire(name.wire())).contains(name));
    assertThat(EventName.fromWire("article_viewed")).contains(EventName.ARTICLE_VIEWED);
    assertThat(EventName.ARTICLE_VIEWED.source()).isEqualTo(EventSource.SERVER);
    assertThat(EventName.CITATION_COPIED.source()).isEqualTo(EventSource.CLIENT);
    assertThat(EventSource.SERVER.wire()).isEqualTo("server");
  }

  @Test
  void from_wire_returns_empty_for_unknown() {
    assertThat(EventName.fromWire("article_liked")).isEmpty();
    assertThat(EventName.fromWire(null)).isEmpty();
  }
}
