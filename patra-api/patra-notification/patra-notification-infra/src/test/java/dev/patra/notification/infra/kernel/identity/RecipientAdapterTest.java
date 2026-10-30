package dev.patra.notification.infra.kernel.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.api.account.dto.AccountStatus;
import dev.patra.identity.api.account.dto.UserRecipientResp;
import dev.patra.identity.api.account.dto.UserRecipientsReq;
import dev.patra.identity.api.account.dto.UserRecipientsResp;
import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.AccountState;
import dev.patra.notification.domain.kernel.model.read.Recipient;
import dev.patra.tool.core.error.remote.RemoteCallException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/// identity 批量收件信息的适配：字段与状态映射、单个查询走批量、失败一律翻成 503、批量大小是调用方的程序错误。
class RecipientAdapterTest {

  private final UserEndpoint endpoint = mock(UserEndpoint.class);
  private final RecipientAdapter adapter = new RecipientAdapter(endpoint);

  /// 六个字段与状态按名字映射；identity 省略的 ID 不在结果里。
  @Test
  void should_map_recipients() {
    when(endpoint.recipients(UserRecipientsReq.of(List.of(7L, 8L))))
        .thenReturn(
            UserRecipientsResp.of(
                List.of(
                    UserRecipientResp.builder()
                        .userId(7L)
                        .email("chen@example.com")
                        .emailVerified(true)
                        .accountStatus(AccountStatus.PENDING_DELETION)
                        .username("chen-yu")
                        .displayName("陈宇")
                        .build())));

    Map<Long, Recipient> recipients = adapter.findAll(List.of(7L, 8L));

    assertThat(recipients)
        .containsExactly(
            Map.entry(
                7L,
                Recipient.builder()
                    .userId(7L)
                    .email("chen@example.com")
                    .emailVerified(true)
                    .state(AccountState.PENDING_DELETION)
                    .username("chen-yu")
                    .displayName("陈宇")
                    .build()));
  }

  /// 取一个人走批量接口；identity 不认识时为空。
  @Test
  void find_one_goes_through_batch() {
    when(endpoint.recipients(UserRecipientsReq.of(List.of(42L))))
        .thenReturn(UserRecipientsResp.of(List.of()));

    assertThat(adapter.find(42L)).isEmpty();
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
      doThrow(failure).when(endpoint).recipients(any());
      assertThatThrownBy(() -> adapter.findAll(List.of(7L)))
          .isInstanceOf(IdentityUnavailableException.class)
          .hasCause(failure);
    }
  }

  /// 0 个或超过 100 个是调用方的程序错误，不发请求。
  @Test
  void should_reject_empty_or_oversized_batch() {
    assertThatThrownBy(() -> adapter.findAll(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.findAll(LongStream.rangeClosed(1, 101).boxed().toList()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(endpoint);
  }
}
