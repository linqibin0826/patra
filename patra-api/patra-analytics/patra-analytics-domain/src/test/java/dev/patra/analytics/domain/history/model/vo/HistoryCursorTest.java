package dev.patra.analytics.domain.history.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.analytics.domain.history.exception.InvalidHistoryCursorException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HistoryCursorTest {

  @Test
  void encode_decode_round_trip() {
    HistoryCursor cursor = HistoryCursor.of(Instant.parse("2026-11-02T07:42:10.177Z"), 40000001L);

    String encoded = cursor.encode();

    assertThat(encoded).doesNotContain("=").doesNotContain("+").doesNotContain("/");
    assertThat(HistoryCursor.decode(encoded)).isEqualTo(cursor);
  }

  @ParameterizedTest
  @ValueSource(strings = {"!!", "bWlzc2luZw", ""})
  void garbage_cursor_is_rejected(String garbage) {
    assertThatThrownBy(() -> HistoryCursor.decode(garbage))
        .isInstanceOf(InvalidHistoryCursorException.class);
  }
}
