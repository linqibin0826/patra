package dev.linqibin.patra.starter.security.error;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/// SecurityErrorMappingContributor 单元测试：spec 第 9.2 节的映射表。
@DisplayName("SecurityErrorMappingContributor 单元测试")
class SecurityErrorMappingContributorTest {

  private final SecurityErrorMappingContributor contributor =
      new SecurityErrorMappingContributor(HttpStdErrors.of("TEST"));

  private Optional<String> codeOf(Throwable exception) {
    return contributor.mapException(exception).map(ErrorCodeLike::code);
  }

  @Test
  @DisplayName("身份头不合法映射为 0500")
  void should_map_malformed_identity_to_internal_error() {
    assertThat(codeOf(new MalformedIdentityException("身份头不完整"))).contains("TEST-0500");
  }

  @Test
  @DisplayName("其他认证服务异常映射为 0503")
  void should_map_authentication_service_exception_to_unavailable() {
    assertThat(codeOf(new AuthenticationServiceException("session store down")))
        .contains("TEST-0503");
  }

  @Test
  @DisplayName("其他认证异常映射为 0401")
  void should_map_authentication_exception_to_unauthorized() {
    assertThat(codeOf(new InsufficientAuthenticationException("full authentication required")))
        .contains("TEST-0401");
    assertThat(codeOf(new BadCredentialsException("bad credentials"))).contains("TEST-0401");
  }

  @Test
  @DisplayName("拒绝访问映射为 0403")
  void should_map_access_denied_to_forbidden() {
    assertThat(codeOf(new AccessDeniedException("denied"))).contains("TEST-0403");
  }

  @Test
  @DisplayName("别的异常不归它管")
  void should_return_empty_for_unrelated_exception() {
    assertThat(codeOf(new IllegalStateException("other"))).isEmpty();
  }
}
