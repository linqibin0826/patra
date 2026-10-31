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
import dev.patra.tool.core.error.remote.RemoteCallException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/// 经 identity 批量用户摘要取账号状态（注销清理用）：没有访客；状态按名字映射；失败一律翻成 503。
class AccountStateAdapterTest {

  private final UserEndpoint endpoint = mock(UserEndpoint.class);
  private final AccountStateAdapter adapter = new AccountStateAdapter(endpoint);

  /// 后台任务没有访客，传 null；identity 省略的 ID 不在结果里。
  @Test
  void should_map_states_without_viewer() {
    when(endpoint.summaries(UserSummariesReq.of(List.of(7L, 8L), null)))
        .thenReturn(
            UserSummariesResp.of(
                List.of(
                    UserSummaryResp.builder()
                        .userId(7L)
                        .accountStatus(AccountStatus.DELETED)
                        .build())));

    assertThat(adapter.findStates(List.of(7L, 8L)))
        .containsExactly(Map.entry(7L, AccountState.DELETED));
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
      assertThatThrownBy(() -> adapter.findStates(List.of(7L)))
          .isInstanceOf(IdentityUnavailableException.class)
          .hasCause(failure);
    }
  }

  /// 0 个或超过 100 个是调用方的程序错误，不发请求。
  @Test
  void should_reject_empty_or_oversized_batch() {
    assertThatThrownBy(() -> adapter.findStates(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.findStates(LongStream.rangeClosed(1, 101).boxed().toList()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(endpoint);
  }
}
