package dev.patra.notification.domain.mail.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.mail.model.vo.DigestSlots;
import org.junit.jupiter.api.Test;

/// 每日汇总一封最多列 10 条，两节怎么分（spec 5.2）。
class DigestSlotPolicyTest {

  /// 9 条回复、5 条 @：回复先拿 ceil(10 × 9 ÷ 14) = 7 条。
  @Test
  void both_sections_split_by_share() {
    assertThat(DigestSlotPolicy.allocate(9, 9, 5, 5)).isEqualTo(DigestSlots.of(7, 3));
  }

  /// 回复占绝大多数时，也给 @ 留 1 条。
  @Test
  void small_section_keeps_at_least_one() {
    assertThat(DigestSlotPolicy.allocate(13, 10, 1, 1)).isEqualTo(DigestSlots.of(9, 1));
  }

  /// 回复的份额向上取整。
  @Test
  void reply_share_rounds_up() {
    assertThat(DigestSlotPolicy.allocate(1, 1, 13, 10)).isEqualTo(DigestSlots.of(1, 9));
  }

  /// 一节能列的不够名额，余下的给另一节。
  @Test
  void short_section_gives_slots_to_other() {
    assertThat(DigestSlotPolicy.allocate(12, 10, 6, 1)).isEqualTo(DigestSlots.of(9, 1));
    assertThat(DigestSlotPolicy.allocate(6, 2, 6, 10)).isEqualTo(DigestSlots.of(2, 8));
  }

  /// 只有一节时最多列 10 条。
  @Test
  void single_section_takes_up_to_ten() {
    assertThat(DigestSlotPolicy.allocate(0, 0, 14, 10)).isEqualTo(DigestSlots.of(0, 10));
    assertThat(DigestSlotPolicy.allocate(3, 3, 0, 0)).isEqualTo(DigestSlots.of(3, 0));
  }

  /// 总数不超过 10 条时全部列出。
  @Test
  void ten_or_fewer_lists_everything() {
    assertThat(DigestSlotPolicy.allocate(4, 4, 6, 6)).isEqualTo(DigestSlots.of(4, 6));
  }
}
