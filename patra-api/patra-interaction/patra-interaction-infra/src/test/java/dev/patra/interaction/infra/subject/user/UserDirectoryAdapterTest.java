package dev.patra.interaction.infra.subject.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.api.account.dto.AccountStatus;
import dev.patra.identity.api.account.dto.UserSummariesReq;
import dev.patra.identity.api.account.dto.UserSummariesResp;
import dev.patra.identity.api.account.dto.UserSummaryResp;
import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.user.AccountState;
import dev.patra.interaction.domain.subject.model.user.UserCard;
import dev.patra.tool.core.error.remote.RemoteCallException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/// identity 批量用户摘要的适配：字段映射、访客透传、失败一律翻成 503。
class UserDirectoryAdapterTest {

  private final UserEndpoint endpoint = mock(UserEndpoint.class);
  private final UserDirectoryAdapter adapter = new UserDirectoryAdapter(endpoint);

  /// 透传访客 ID；identity 省略的 ID 不在结果里。
  @Test
  void should_map_summaries_and_pass_viewer() {
    when(endpoint.summaries(UserSummariesReq.of(List.of(7L, 8L), 9L)))
        .thenReturn(
            UserSummariesResp.of(
                List.of(
                    UserSummaryResp.builder()
                        .userId(7L)
                        .username("chen-yu")
                        .displayName("陈宇")
                        .avatarUrl("/users/7/avatar?v=a")
                        .avatarPalette(3)
                        .accountStatus(AccountStatus.PENDING_DELETION)
                        .emailVerified(true)
                        .profileVisible(true)
                        .build())));

    Map<Long, UserCard> cards = adapter.findCards(List.of(7L, 8L), 9L);

    assertThat(cards)
        .containsExactly(
            Map.entry(
                7L,
                UserCard.builder()
                    .userId(7L)
                    .username("chen-yu")
                    .displayName("陈宇")
                    .avatarUrl("/users/7/avatar?v=a")
                    .avatarPalette(3)
                    .state(AccountState.PENDING_DELETION)
                    .emailVerified(true)
                    .profileVisible(true)
                    .build()));
  }

  /// 连不上、回错误、没有实例都是依赖不可用。
  @Test
  void should_translate_remote_failures() {
    for (RuntimeException failure :
        List.of(
            new ResourceAccessException("down"),
            new IllegalStateException("No instances available for patra-identity"),
            new RemoteCallException(
                "IDN-0503",
                503,
                "remote",
                "POST /x",
                null,
                Map.of(),
                Set.of(StandardErrorTrait.DEP_UNAVAILABLE)))) {
      doThrow(failure).when(endpoint).summaries(any());
      assertThatThrownBy(() -> adapter.findCards(List.of(7L), null))
          .isInstanceOf(UserDirectoryUnavailableException.class)
          .hasCause(failure);
    }
  }

  /// 0 个或超过 100 个是调用方的程序错误，不发请求。
  @Test
  void should_reject_empty_or_oversized_batch() {
    assertThatThrownBy(() -> adapter.findCards(List.of(), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> adapter.findCards(LongStream.rangeClosed(1, 101).boxed().toList(), null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(endpoint);
  }

  /// 匿名访客传 null。
  @Test
  void should_pass_null_viewer_for_anonymous() {
    when(endpoint.summaries(any())).thenReturn(UserSummariesResp.of(List.of()));

    assertThat(adapter.findCards(List.of(7L), null)).isEmpty();
    verify(endpoint).summaries(UserSummariesReq.of(List.of(7L), null));
  }
}
