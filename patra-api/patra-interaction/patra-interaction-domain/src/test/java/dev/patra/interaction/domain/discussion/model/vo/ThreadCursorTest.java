package dev.patra.interaction.domain.discussion.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.discussion.exception.InvalidDiscussionRequestException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 翻页游标：`{自 EPOCH 的微秒}.{评论 ID}`，往返不丢精度；不合法的游标是 422。
class ThreadCursorTest {

  /// 格式化后再解析得到同一个游标。
  @Test
  void should_round_trip_micros_and_id() {
    ThreadCursor cursor = ThreadCursor.of(Instant.parse("2026-10-27T08:00:00.123456Z"), 42L);

    assertThat(ThreadCursor.parse(cursor.format())).contains(cursor);
    assertThat(cursor.format()).endsWith(".42");
  }

  /// 没有游标就是从头开始。
  @Test
  void should_be_empty_for_null_or_blank() {
    assertThat(ThreadCursor.parse(null)).isEmpty();
    assertThat(ThreadCursor.parse("  ")).isEmpty();
  }

  /// 格式不对、ID 不是规范正整数、数字溢出都拒绝。
  @Test
  void should_reject_malformed() {
    for (String raw :
        List.of(
            "abc",
            "1",
            ".5",
            "1.0",
            "-1.5",
            "1.05",
            "99999999999999999999.1",
            "1.99999999999999999999")) {
      assertThatThrownBy(() -> ThreadCursor.parse(raw))
          .as(raw)
          .isInstanceOf(InvalidDiscussionRequestException.class);
    }
  }
}
