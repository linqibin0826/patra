package dev.patra.notification.domain.inbox.model.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.inbox.exception.InvalidInboxRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/// 列表游标：排序时间（微秒）与提醒 ID 两段，对门户不透明。
class InboxCursorTest {

  /// 编码后再解析，得到同一个游标。
  @Test
  void should_round_trip() {
    InboxCursor cursor = InboxCursor.of(Instant.parse("2026-10-31T02:15:30.123456Z"), 1849L);

    assertThat(InboxCursor.parse(cursor.encode())).isEqualTo(cursor);
  }

  /// 第一页不带游标。
  @Test
  void should_return_null_for_blank() {
    assertThat(InboxCursor.parse(null)).isNull();
    assertThat(InboxCursor.parse("")).isNull();
  }

  /// 不是 Base64、段数不对、数字不对、ID 不是正数、时刻早于 1970（库里放不下的极端值）：422。
  @Test
  void should_reject_garbage() {
    for (String raw :
        new String[] {
          "abc!",
          encode("x_y"),
          encode("1_-5"),
          encode("1"),
          encode("1_2_3"),
          encode("-1_5"),
          encode("-9223372036854775807_1")
        }) {
      assertThatThrownBy(() -> InboxCursor.parse(raw))
          .as(raw)
          .isInstanceOf(InvalidInboxRequestException.class);
    }
  }

  /// 测试用：按游标的编码方式编一段原文。
  ///
  /// @param raw 原文
  /// @return Base64URL
  private static String encode(String raw) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
  }
}
