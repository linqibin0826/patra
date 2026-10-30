package dev.patra.notification.infra.kernel.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.api.account.dto.AccountStatus;
import dev.patra.identity.api.account.dto.UserSummariesReq;
import dev.patra.identity.api.account.dto.UserSummariesResp;
import dev.patra.identity.api.account.dto.UserSummaryResp;
import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.model.read.UserCard;
import dev.patra.tool.core.error.remote.RemoteCallException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/// 名片端口：替访客取 identity 的批量用户摘要。
class UserCardAdapterTest {

  private final UserEndpoint endpoint = mock(UserEndpoint.class);
  private final UserCardAdapter adapter = new UserCardAdapter(endpoint);

  /// 访客原样透传，主页可见性由 identity 算；摘要逐字段映射。
  @Test
  void should_map_summaries_with_viewer() {
    when(endpoint.summaries(UserSummariesReq.of(List.of(7L), 2L)))
        .thenReturn(
            UserSummariesResp.of(
                List.of(
                    UserSummaryResp.builder()
                        .userId(7L)
                        .username("zhou")
                        .displayName("周敏")
                        .avatarUrl("/users/7/avatar?v=1")
                        .avatarPalette(3)
                        .accountStatus(AccountStatus.ACTIVE)
                        .profileVisible(true)
                        .build())));

    assertThat(adapter.findCards(List.of(7L), 2L))
        .containsExactly(
            Map.entry(
                7L,
                UserCard.builder()
                    .userId(7L)
                    .username("zhou")
                    .displayName("周敏")
                    .avatarUrl("/users/7/avatar?v=1")
                    .avatarPalette(3)
                    .state(AccountState.ACTIVE)
                    .profileVisible(true)
                    .build()));
  }

  /// 0 个或超过 100 个是调用方的程序错误，不发请求。
  @Test
  void should_reject_empty_or_oversized_batch() {
    assertThatThrownBy(() -> adapter.findCards(List.of(), 2L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.findCards(LongStream.rangeClosed(1, 101).boxed().toList(), 2L))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(endpoint);
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
      assertThatThrownBy(() -> adapter.findCards(List.of(7L), 2L))
          .isInstanceOf(IdentityUnavailableException.class)
          .hasCause(failure);
    }
  }
}
