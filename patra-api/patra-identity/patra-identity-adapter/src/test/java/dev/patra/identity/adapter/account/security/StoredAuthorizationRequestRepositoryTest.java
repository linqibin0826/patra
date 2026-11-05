package dev.patra.identity.adapter.account.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.port.oauth.AuthorizationRequestStorePort;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import tools.jackson.databind.json.JsonMapper;

/// StoredAuthorizationRequestRepository 单元测试：JSON 往返、取出即删、取出的请求留给成功处理器。
@DisplayName("StoredAuthorizationRequestRepository 单元测试")
class StoredAuthorizationRequestRepositoryTest {

  private static final OAuth2AuthorizationRequest REQUEST =
      OAuth2AuthorizationRequest.authorizationCode()
          .authorizationUri("https://provider.test/authorize")
          .clientId("client-1")
          .redirectUri("https://patra.test/api/auth/oauth/google/callback")
          .scope("openid", "email")
          .state("state-1")
          .additionalParameters(Map.of("nonce", "nonce-hash"))
          .attributes(
              attrs -> {
                attrs.put("registration_id", "google");
                attrs.put("nonce", "nonce-raw");
                attrs.put("code_verifier", "verifier");
              })
          .authorizationRequestUri("https://provider.test/authorize?state=state-1")
          .build();

  private final InMemoryStore store = new InMemoryStore();
  private final StoredAuthorizationRequestRepository repository =
      new StoredAuthorizationRequestRepository(store, JsonMapper.builder().build());

  private static MockHttpServletRequest callback(String state) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("state", state);
    return request;
  }

  @Test
  @DisplayName("存进去取出来，换令牌要用的字段都还在，有效期 10 分钟")
  void should_round_trip_through_store() {
    repository.saveAuthorizationRequest(
        REQUEST, new MockHttpServletRequest(), new MockHttpServletResponse());

    OAuth2AuthorizationRequest loaded =
        repository.removeAuthorizationRequest(callback("state-1"), new MockHttpServletResponse());

    assertThat(store.lastTtl).isEqualTo(Duration.ofMinutes(10));
    assertThat(loaded.getState()).isEqualTo("state-1");
    assertThat(loaded.getClientId()).isEqualTo("client-1");
    assertThat(loaded.getRedirectUri()).isEqualTo(REQUEST.getRedirectUri());
    assertThat(loaded.getScopes()).containsExactlyInAnyOrder("openid", "email");
    assertThat(loaded.getAdditionalParameters()).containsEntry("nonce", "nonce-hash");
    assertThat(loaded.<String>getAttribute("code_verifier")).isEqualTo("verifier");
    assertThat(loaded.<String>getAttribute("registration_id")).isEqualTo("google");
    assertThat(loaded.getAuthorizationRequestUri()).isEqualTo(REQUEST.getAuthorizationRequestUri());
  }

  @Test
  @DisplayName("取出即删：第二次拿不到")
  void should_remove_on_first_read() {
    repository.saveAuthorizationRequest(
        REQUEST, new MockHttpServletRequest(), new MockHttpServletResponse());
    repository.removeAuthorizationRequest(callback("state-1"), new MockHttpServletResponse());

    assertThat(
            repository.removeAuthorizationRequest(
                callback("state-1"), new MockHttpServletResponse()))
        .isNull();
  }

  @Test
  @DisplayName("取出的请求放进请求属性，成功处理器据此知道用途")
  void should_expose_consumed_request_as_attribute() {
    repository.saveAuthorizationRequest(
        REQUEST, new MockHttpServletRequest(), new MockHttpServletResponse());
    MockHttpServletRequest request = callback("state-1");

    repository.removeAuthorizationRequest(request, new MockHttpServletResponse());

    assertThat(
            request.getAttribute(StoredAuthorizationRequestRepository.CONSUMED_REQUEST_ATTRIBUTE))
        .isInstanceOf(OAuth2AuthorizationRequest.class);
  }

  @Test
  @DisplayName("没带 state：不查存储，返回空")
  void should_return_null_without_state() {
    assertThat(
            repository.removeAuthorizationRequest(
                new MockHttpServletRequest(), new MockHttpServletResponse()))
        .isNull();
  }

  @Test
  @DisplayName("Redis 暂时不可用：转成框架认识的认证异常，才能走到失败处理器")
  void should_wrap_store_failure() {
    store.failure = new TemporarilyUnavailableException();

    assertThatThrownBy(
            () ->
                repository.removeAuthorizationRequest(
                    callback("state-1"), new MockHttpServletResponse()))
        .isInstanceOf(InternalAuthenticationServiceException.class);
  }

  /// 内存里的存储。
  private static final class InMemoryStore implements AuthorizationRequestStorePort {

    private final Map<String, String> values = new HashMap<>();
    private Duration lastTtl;
    private RuntimeException failure;

    @Override
    public void save(String state, String payload, Duration ttl) {
      values.put(state, payload);
      lastTtl = ttl;
    }

    @Override
    public Optional<String> find(String state) {
      return Optional.ofNullable(values.get(state));
    }

    @Override
    public Optional<String> consume(String state) {
      if (failure != null) {
        throw failure;
      }
      return Optional.ofNullable(values.remove(state));
    }
  }
}
