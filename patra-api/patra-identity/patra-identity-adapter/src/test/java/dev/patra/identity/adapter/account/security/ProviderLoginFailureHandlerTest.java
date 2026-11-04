package dev.patra.identity.adapter.account.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.patra.identity.domain.account.exception.ProviderAuthorizationCancelledException;
import dev.patra.identity.domain.account.exception.ProviderAuthorizationRejectedException;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.tool.security.error.SecurityProblemWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/// ProviderLoginFailureHandler 单元测试：框架的错误码翻译成给门户的错误。
@DisplayName("ProviderLoginFailureHandler 单元测试")
class ProviderLoginFailureHandlerTest {

  private final SecurityProblemWriter writer = mock(SecurityProblemWriter.class);
  private final ProviderLoginFailureHandler handler = new ProviderLoginFailureHandler(writer);

  private RuntimeException translated(AuthenticationException exception) throws Exception {
    handler.onAuthenticationFailure(
        new MockHttpServletRequest(), new MockHttpServletResponse(), exception);
    ArgumentCaptor<RuntimeException> written = ArgumentCaptor.forClass(RuntimeException.class);
    verify(writer).writeProblem(any(), any(), written.capture());
    return written.getValue();
  }

  @Test
  @DisplayName("用户拒绝：取消")
  void should_translate_access_denied() throws Exception {
    assertThat(translated(new OAuth2AuthenticationException("access_denied")))
        .isInstanceOf(ProviderAuthorizationCancelledException.class);
  }

  @Test
  @DisplayName("连不上平台：503")
  void should_translate_unavailable_provider() throws Exception {
    assertThat(translated(new OAuth2AuthenticationException("invalid_token_response")))
        .isInstanceOf(TemporarilyUnavailableException.class);
  }

  @Test
  @DisplayName("Redis 不可用：503")
  void should_translate_store_failure() throws Exception {
    assertThat(translated(new InternalAuthenticationServiceException("redis")))
        .isInstanceOf(TemporarilyUnavailableException.class);
  }

  @Test
  @DisplayName("其余协议错误：拒绝，不区分原因")
  void should_translate_other_codes_to_rejected() throws Exception {
    assertThat(translated(new OAuth2AuthenticationException("invalid_id_token")))
        .isInstanceOf(ProviderAuthorizationRejectedException.class);
  }
}
