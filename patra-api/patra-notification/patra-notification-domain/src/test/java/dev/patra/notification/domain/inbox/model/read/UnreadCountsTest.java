package dev.patra.notification.domain.inbox.model.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// 三个未读数：铃铛只数回复和 @（PAP-140），ETag 覆盖三个。
class UnreadCountsTest {

  /// `total` 不含有帮助；ETag 是带引号的三段。
  @Test
  void should_count_bell_without_helpful_and_tag_all_three() {
    UnreadCounts counts = UnreadCounts.of(2, 1, 4);

    assertThat(counts.total()).isEqualTo(3);
    assertThat(counts.etag()).isEqualTo("\"2-1-4\"");
  }
}
