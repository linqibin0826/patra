package dev.patra.interaction.app.post.usecase.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.post.exception.InvalidPostRequestException;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.interaction.domain.post.model.read.ExperienceOutline;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.port.read.PostReadPort;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/// 内部接口「按帖子 ID 取经验结果」的查询：去重保序、查不到的省略、批量上限。
class ExperienceOutlineQueryServiceTest {

  private static final ExperienceOutline OUTLINE =
      ExperienceOutline.of(FirstDecision.MAJOR_REVISION, FinalOutcome.ACCEPTED, 41, 96);

  private final PostReadPort posts = mock(PostReadPort.class);
  private final ExperienceOutlineQueryService query = new ExperienceOutlineQueryService(posts);

  /// 按去重后的请求顺序返回，查不到的省略。
  @Test
  void should_keep_request_order_and_omit_unknown() {
    when(posts.findOutlines(List.of(PostId.of(56L), PostId.of(55L), PostId.of(57L))))
        .thenReturn(Map.of(PostId.of(55L), OUTLINE, PostId.of(56L), OUTLINE));

    assertThat(query.outlines(List.of(56L, 55L, 56L, 57L)))
        .extracting(ExperienceOutlineView::postId)
        .containsExactly(56L, 55L);
  }

  /// 空列表：直接返回空，不查库。
  @Test
  void should_return_empty_for_empty_request() {
    assertThat(query.outlines(List.of())).isEmpty();
    verifyNoInteractions(posts);
  }

  /// 缺列表、含 null、超过 100 个：422。
  @Test
  void should_reject_null_or_over_100() {
    assertThatThrownBy(() -> query.outlines(null)).isInstanceOf(InvalidPostRequestException.class);
    assertThatThrownBy(() -> query.outlines(Arrays.asList(1L, null)))
        .isInstanceOf(InvalidPostRequestException.class);
    assertThatThrownBy(() -> query.outlines(LongStream.rangeClosed(1, 101).boxed().toList()))
        .isInstanceOf(InvalidPostRequestException.class);
    verifyNoInteractions(posts);
  }
}
