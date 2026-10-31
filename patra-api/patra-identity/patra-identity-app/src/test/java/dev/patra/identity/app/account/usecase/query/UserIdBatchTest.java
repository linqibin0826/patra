package dev.patra.identity.app.account.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/// 内部批量接口共用的用户 ID 列表校验：不能为 `null`、不能含 `null`、去重前不超过 100 个；去重保序。
class UserIdBatchTest {

  /// 缺列表。
  @Test
  void should_reject_null_list() {
    assertRejected(null, "REQUIRED");
  }

  /// 列表里有空值。
  @Test
  void should_reject_null_element() {
    assertRejected(Arrays.asList(1L, null), "INVALID_FORMAT");
  }

  /// 上限按去重前的长度算。
  @Test
  void should_reject_more_than_100_even_if_duplicated() {
    assertRejected(Collections.nCopies(101, 7L), "TOO_MANY");
  }

  /// 恰好 100 个可以。
  @Test
  void should_accept_exactly_100() {
    assertThat(UserIdBatch.requested(LongStream.rangeClosed(1, 100).boxed().toList())).hasSize(100);
  }

  /// 重复的只留第一次出现，保持请求顺序。
  @Test
  void should_dedupe_and_keep_request_order() {
    assertThat(UserIdBatch.requested(List.of(3L, 1L, 3L, 2L))).containsExactly(3L, 1L, 2L);
  }

  /// 断言被拒，字段 `userIds`，原因码如期。
  ///
  /// @param userIds 请求
  /// @param reason 原因码
  private static void assertRejected(List<Long> userIds, String reason) {
    assertThatThrownBy(() -> UserIdBatch.requested(userIds))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e -> {
              FieldViolation violation = e.getFieldViolations().getFirst();
              assertThat(violation.field()).isEqualTo("userIds");
              assertThat(violation.code()).isEqualTo(reason);
            });
  }
}
