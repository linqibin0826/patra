package dev.patra.notification.infra.kernel.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.api.post.dto.ExperienceOutlineResp;
import dev.patra.interaction.api.post.dto.ExperienceOutlinesReq;
import dev.patra.interaction.api.post.dto.ExperienceOutlinesResp;
import dev.patra.interaction.api.post.endpoint.PostEndpoint;
import dev.patra.interaction.api.subject.dto.SubjectViewResp;
import dev.patra.interaction.api.subject.dto.SubjectViewsReq;
import dev.patra.interaction.api.subject.dto.SubjectViewsResp;
import dev.patra.interaction.api.subject.endpoint.SubjectEndpoint;
import dev.patra.notification.domain.kernel.exception.InteractionUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import dev.patra.notification.domain.kernel.model.read.ExperienceOutline;
import dev.patra.notification.domain.kernel.model.read.SubjectView;
import dev.patra.tool.core.error.remote.RemoteCallException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/// 对象视图端口：组合 interaction 的两个内部接口（对象视图 + 经验结果）。
class SubjectViewAdapterTest {

  private final SubjectEndpoint subjects = mock(SubjectEndpoint.class);
  private final PostEndpoint posts = mock(PostEndpoint.class);
  private final SubjectViewAdapter adapter = new SubjectViewAdapter(subjects, posts);

  /// 经验的结果按它的 key（帖子 ID）只查一次，合进视图；文献没有结果。
  @Test
  void should_merge_post_outlines_by_key() {
    when(subjects.views(SubjectViewsReq.of(List.of(40L, 41L))))
        .thenReturn(SubjectViewsResp.of(List.of(publication(40L), post(41L, "55"))));
    when(posts.outlines(ExperienceOutlinesReq.of(List.of(55L))))
        .thenReturn(
            ExperienceOutlinesResp.of(
                List.of(
                    ExperienceOutlineResp.builder()
                        .postId(55L)
                        .firstDecision("MAJOR_REVISION")
                        .finalOutcome("ACCEPTED")
                        .firstRoundDays(41)
                        .totalDays(96)
                        .build())));

    Map<Long, SubjectView> views = adapter.findViews(List.of(40L, 41L));

    assertThat(views.get(40L).type()).isEqualTo(SubjectType.PUBLICATION);
    assertThat(views.get(40L).title()).isEqualTo("文献标题");
    assertThat(views.get(40L).experience()).isNull();
    assertThat(views.get(41L))
        .isEqualTo(
            SubjectView.builder()
                .subjectId(41L)
                .type(SubjectType.POST)
                .key("55")
                .available(true)
                .journal("The Lancet")
                .abbreviation("Lancet")
                .publishedDate("2026-08")
                .ownerId(9L)
                .ownerAnonymous(false)
                .experience(ExperienceOutline.of("MAJOR_REVISION", "ACCEPTED", 41, 96))
                .build());
  }

  /// 没有经验时不调结果接口。
  @Test
  void should_not_call_outlines_without_posts() {
    when(subjects.views(any())).thenReturn(SubjectViewsResp.of(List.of(publication(40L))));

    adapter.findViews(List.of(40L));

    verifyNoInteractions(posts);
  }

  /// 不认识的对象类型（如期刊）不进结果，不抛异常。
  @Test
  void should_skip_unknown_subject_type() {
    SubjectViewResp journal =
        SubjectViewResp.builder().subjectId(42L).type("JOURNAL").key("7").available(true).build();
    when(subjects.views(any())).thenReturn(SubjectViewsResp.of(List.of(journal)));

    assertThat(adapter.findViews(List.of(42L))).isEmpty();
  }

  /// 经验的 key 不是数字（来源整行消失时为空）：视图照给，不查结果。
  @Test
  void should_keep_post_without_numeric_key() {
    when(subjects.views(any())).thenReturn(SubjectViewsResp.of(List.of(post(41L, null))));

    assertThat(adapter.findViews(List.of(41L)).get(41L).experience()).isNull();
    verifyNoInteractions(posts);
  }

  /// 0 个或超过 100 个是调用方的程序错误，不发请求。
  @Test
  void should_reject_empty_or_oversized_batch() {
    assertThatThrownBy(() -> adapter.findViews(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.findViews(LongStream.rangeClosed(1, 101).boxed().toList()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(subjects, posts);
  }

  /// 两个接口任一个连不上、回错误、没有实例，都是依赖不可用。
  @Test
  void should_translate_remote_failures() {
    RuntimeException remote =
        new RemoteCallException(
            "INTERACTION-0503",
            503,
            "remote",
            "POST /x",
            null,
            Map.of(),
            Set.of(StandardErrorTrait.DEP_UNAVAILABLE));
    for (RuntimeException failure :
        List.of(
            new ResourceAccessException("down"),
            new IllegalStateException("No instances available for patra-interaction"),
            remote)) {
      doThrow(failure).when(subjects).views(any());
      assertThatThrownBy(() -> adapter.findViews(List.of(40L)))
          .isInstanceOf(InteractionUnavailableException.class)
          .hasCause(failure);
    }

    reset(subjects);
    when(subjects.views(any())).thenReturn(SubjectViewsResp.of(List.of(post(41L, "55"))));
    doThrow(remote).when(posts).outlines(any());
    assertThatThrownBy(() -> adapter.findViews(List.of(41L)))
        .isInstanceOf(InteractionUnavailableException.class)
        .hasCause(remote);
    verify(posts).outlines(ExperienceOutlinesReq.of(List.of(55L)));
  }

  /// 一个文献的视图。
  ///
  /// @param subjectId 对象
  /// @return 视图
  private static SubjectViewResp publication(long subjectId) {
    return SubjectViewResp.builder()
        .subjectId(subjectId)
        .type("PUBLICATION")
        .key("18")
        .available(true)
        .title("文献标题")
        .journal("Nature")
        .publishedDate("2019-03")
        .build();
  }

  /// 一条投稿经验的视图。
  ///
  /// @param subjectId 对象
  /// @param key 帖子 ID
  /// @return 视图
  private static SubjectViewResp post(long subjectId, String key) {
    return SubjectViewResp.builder()
        .subjectId(subjectId)
        .type("POST")
        .key(key)
        .available(true)
        .journal("The Lancet")
        .abbreviation("Lancet")
        .publishedDate("2026-08")
        .ownerId(9L)
        .ownerAnonymous(false)
        .build();
  }
}
