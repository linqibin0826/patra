package dev.linqibin.patra.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/// 在 starter 的映射之上加一行：查会话的非暂时失败是 0500，不是 0503。
class GatewaySecurityErrorMappingContributorTest {

  private final GatewaySecurityErrorMappingContributor contributor =
      new GatewaySecurityErrorMappingContributor(HttpStdErrors.of("GW"));

  @Test
  void should_map_lookup_failure_to_internal_error() {
    assertThat(codeOf(new SessionLookupFailedException(new IllegalStateException("corrupt"))))
        .contains("GW-0500");
  }

  @Test
  void should_keep_the_parent_mappings() {
    assertThat(codeOf(new AuthenticationServiceException("redis down"))).contains("GW-0503");
    assertThat(codeOf(new InsufficientAuthenticationException("login"))).contains("GW-0401");
    assertThat(codeOf(new AccessDeniedException("denied"))).contains("GW-0403");
    assertThat(codeOf(new IllegalStateException("other"))).isEmpty();
  }

  private Optional<String> codeOf(Throwable exception) {
    return contributor.mapException(exception).map(ErrorCodeLike::code);
  }
}
