package dev.patra.interaction.infra.discussion.mention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.api.account.dto.AccountStatus;
import dev.patra.identity.api.account.dto.UserResolutionResp;
import dev.patra.identity.api.account.dto.UserResolveByUsernamesReq;
import dev.patra.identity.api.account.dto.UserResolveResp;
import dev.patra.identity.api.account.dto.UserSuggestReq;
import dev.patra.identity.api.account.dto.UserSuggestionsResp;
import dev.patra.identity.api.account.dto.UserSummaryResp;
import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.interaction.domain.discussion.model.read.MentionTarget;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.user.AccountState;
import dev.patra.interaction.domain.subject.model.user.UserCard;
import dev.patra.tool.core.error.remote.RemoteCallException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/// identity 的 @ 提及支撑：用户名批量解析按请求原文配回、前缀联想透传访客；失败一律翻成 503，越界不发请求。
class MentionDirectoryAdapterTest {

  private final UserEndpoint endpoint = mock(UserEndpoint.class);
  private final MentionDirectoryAdapter adapter = new MentionDirectoryAdapter(endpoint);

  /// 解析结果按请求原文作键。
  @Test
  void should_map_resolutions_by_original_text() {
    when(endpoint.resolveByUsernames(UserResolveByUsernamesReq.of(List.of("Chen-Yu", "old-name"))))
        .thenReturn(
            UserResolveResp.of(
                List.of(
                    UserResolutionResp.of("Chen-Yu", 42L, AccountStatus.ACTIVE),
                    UserResolutionResp.of("old-name", 43L, AccountStatus.BANNED))));

    assertThat(adapter.resolve(List.of("Chen-Yu", "old-name")))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "Chen-Yu", MentionTarget.of(42L, AccountState.ACTIVE),
                "old-name", MentionTarget.of(43L, AccountState.BANNED)));
  }

  /// 联想透传前缀、访客、条数，按 identity 的顺序转成名片。
  @Test
  void should_map_suggestions_and_pass_viewer() {
    when(endpoint.suggest(UserSuggestReq.of("张", 7L, 14)))
        .thenReturn(
            UserSuggestionsResp.of(
                List.of(
                    UserSummaryResp.builder()
                        .userId(42L)
                        .username("zhang-san")
                        .displayName("张三")
                        .avatarUrl("/users/42/avatar?v=a")
                        .avatarPalette(5)
                        .accountStatus(AccountStatus.ACTIVE)
                        .emailVerified(true)
                        .profileVisible(true)
                        .build())));

    assertThat(adapter.suggest("张", 7L, 14))
        .containsExactly(
            UserCard.builder()
                .userId(42L)
                .username("zhang-san")
                .displayName("张三")
                .avatarUrl("/users/42/avatar?v=a")
                .avatarPalette(5)
                .state(AccountState.ACTIVE)
                .emailVerified(true)
                .profileVisible(true)
                .build());
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
      reset(endpoint);
      doThrow(failure).when(endpoint).resolveByUsernames(any());
      doThrow(failure).when(endpoint).suggest(any());

      assertThatThrownBy(() -> adapter.resolve(List.of("bob")))
          .isInstanceOf(UserDirectoryUnavailableException.class)
          .hasCause(failure);
      assertThatThrownBy(() -> adapter.suggest("bo", 7L, 8))
          .isInstanceOf(UserDirectoryUnavailableException.class)
          .hasCause(failure);
    }
  }

  /// 0 个、超过 100 个用户名，条数不在 1 ~ 20，都是调用方的程序错误，不发请求。
  @Test
  void should_reject_out_of_range_arguments() {
    List<String> tooMany = IntStream.rangeClosed(1, 101).mapToObj(i -> "user-" + i).toList();

    assertThatThrownBy(() -> adapter.resolve(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.resolve(tooMany)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.suggest("bo", 7L, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.suggest("bo", 7L, 21))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(endpoint);
  }
}
