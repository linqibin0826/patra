package dev.patra.notification.domain.inbox.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.inbox.model.enums.HelpfulTargetType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/// 有帮助合并条目用到的几个值对象：目标、对象所有者、经验正文。
class HelpfulValuesTest {

  private static final Instant T0 = Instant.parse("2026-10-31T06:00:00Z");

  /// 点评论时目标 ID 就是评论 ID；点经验时没有评论。
  @Test
  void target_exposes_comment_id_only_for_comments() {
    assertThat(HelpfulTarget.of(HelpfulTargetType.COMMENT, 500L, 40L).commentId()).isEqualTo(500L);
    assertThat(HelpfulTarget.of(HelpfulTargetType.POST, 55L, 41L).commentId()).isNull();
  }

  /// 只有「匿名经验的作者本人」被藏起来：不匿名、别人、文献（没有所有者）都不藏。
  @Test
  void owner_hides_only_the_anonymous_owner_himself() {
    assertThat(SubjectOwner.of(11L, true).hides(11L)).isTrue();
    assertThat(SubjectOwner.of(11L, true).hides(12L)).isFalse();
    assertThat(SubjectOwner.of(11L, false).hides(11L)).isFalse();
    assertThat(SubjectOwner.of(null, false).hides(11L)).isFalse();
  }

  /// 更晚的点击带来的正文替换旧的。
  @Test
  void post_text_later_mark_replaces_text() {
    PostText text = PostText.of("旧", T0);

    assertThat(text.advance("新", T0.plusSeconds(60)))
        .isEqualTo(PostText.of("新", T0.plusSeconds(60)));
  }

  /// 更早或同一时刻的点击不动正文。
  @Test
  void post_text_earlier_mark_keeps_text() {
    PostText text = PostText.of("旧", T0);

    assertThat(text.advance("更旧", T0.minusSeconds(60))).isSameAs(text);
    assertThat(text.advance("同刻", T0)).isSameAs(text);
  }

  /// 更晚的点击带来空正文（作者删了正文）也要替换：「最晚者为准」包括空值。
  @Test
  void post_text_null_text_from_later_mark_replaces_older_text() {
    PostText text = PostText.of("旧", T0);

    assertThat(text.advance(null, T0.plusSeconds(60)).value()).isNull();
  }
}
