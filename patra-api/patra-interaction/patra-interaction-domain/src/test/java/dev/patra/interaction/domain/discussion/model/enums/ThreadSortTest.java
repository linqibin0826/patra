package dev.patra.interaction.domain.discussion.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.discussion.exception.InvalidDiscussionRequestException;
import org.junit.jupiter.api.Test;

/// 根评论排序的请求参数：默认正序，只认小写的 `asc`、`latest`、`helpful`。
class ThreadSortTest {

  /// 不传为正序。
  @Test
  void should_default_to_ascending() {
    assertThat(ThreadSort.fromParam(null)).isEqualTo(ThreadSort.ASC);
    assertThat(ThreadSort.fromParam("")).isEqualTo(ThreadSort.ASC);
  }

  /// 三个合法值。
  @Test
  void should_parse_known_values() {
    assertThat(ThreadSort.fromParam("asc")).isEqualTo(ThreadSort.ASC);
    assertThat(ThreadSort.fromParam("latest")).isEqualTo(ThreadSort.LATEST);
    assertThat(ThreadSort.fromParam("helpful")).isEqualTo(ThreadSort.HELPFUL);
  }

  /// 大写与未知值都拒绝。
  @Test
  void should_reject_unknown_values() {
    assertThatThrownBy(() -> ThreadSort.fromParam("LATEST"))
        .isInstanceOf(InvalidDiscussionRequestException.class);
    assertThatThrownBy(() -> ThreadSort.fromParam("hot"))
        .isInstanceOf(InvalidDiscussionRequestException.class);
  }
}
