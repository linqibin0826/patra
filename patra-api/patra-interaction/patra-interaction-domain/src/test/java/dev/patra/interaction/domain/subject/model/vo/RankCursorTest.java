package dev.patra.interaction.domain.subject.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 「最有帮助」的三元组游标：`{有帮助数}.{微秒}.{ID}`，格式不对返回空，由各子域转成自己的 422。
class RankCursorTest {

  private static final Instant T0 = Instant.parse("2026-10-27T01:00:00.123456Z");

  /// 格式化再解析回来不丢微秒。
  @Test
  void should_round_trip_with_microsecond_precision() {
    RankCursor cursor = RankCursor.of(3, T0, 42L);

    assertThat(cursor.format()).isEqualTo("3.1793062800123456.42");
    assertThat(RankCursor.parse(cursor.format())).contains(cursor);
    assertThat(RankCursor.parse("0.0.1")).contains(RankCursor.of(0, Instant.EPOCH, 1L));
  }

  /// 段数不对、不是数字、ID 为 0、数字溢出：都是空。
  @Test
  void should_return_empty_for_malformed() {
    for (String raw :
        List.of(
            "1.2",
            "a.b.c",
            "1.2.0",
            "-1.2.3",
            "99999999999.1.1",
            "1.99999999999999999999.1",
            "1.2.99999999999999999999")) {
      assertThat(RankCursor.parse(raw)).as(raw).isEmpty();
    }
  }
}
