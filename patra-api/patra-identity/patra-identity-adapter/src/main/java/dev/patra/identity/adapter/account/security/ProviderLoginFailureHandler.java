package dev.patra.identity.adapter.account.security;

import dev.patra.identity.domain.account.exception.ProviderAuthorizationCancelledException;
import dev.patra.identity.domain.account.exception.ProviderAuthorizationRejectedException;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.security.error.SecurityProblemWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/// 回调失败：把框架的错误码翻译成给门户的错误。平台给的描述可能带内部细节，只进 `debug` 日志。
@Slf4j
@RequiredArgsConstructor
public class ProviderLoginFailureHandler implements AuthenticationFailureHandler {

  /// 换令牌、取资料时平台或网络暂时不行：框架把连不上、响应读不了都归到这两个错误码。描述来自服务端，留着排查。
  private static final Set<String> OUTBOUND_FAILURE_CODES =
      Set.of("invalid_token_response", "invalid_user_info_response");

  /// 平台在授权响应里说自己暂时不行。这些来自回调的查询串，描述是匿名请求写的，不能进 5xx 的 ERROR 日志。
  private static final Set<String> CALLBACK_FAILURE_CODES =
      Set.of(OAuth2ErrorCodes.SERVER_ERROR, OAuth2ErrorCodes.TEMPORARILY_UNAVAILABLE);

  private final SecurityProblemWriter problemWriter;

  /// 写错误响应。
  ///
  /// @param request 请求
  /// @param response 响应
  /// @param exception 框架的认证异常
  /// @throws IOException 写响应失败时
  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    log.debug("第三方登录回调失败: {}", exception.getMessage());
    problemWriter.writeProblem(request, response, translate(exception));
  }

  /// 翻译。
  ///
  /// @param exception 框架的认证异常
  /// @return 领域异常
  private static DomainException translate(AuthenticationException exception) {
    if (exception instanceof InternalAuthenticationServiceException) {
      return new TemporarilyUnavailableException(exception);
    }
    if (exception instanceof OAuth2AuthenticationException oauth) {
      String code = oauth.getError().getErrorCode();
      if (OAuth2ErrorCodes.ACCESS_DENIED.equals(code)) {
        return new ProviderAuthorizationCancelledException();
      }
      if (OUTBOUND_FAILURE_CODES.contains(code)) {
        return new TemporarilyUnavailableException(exception);
      }
      if (CALLBACK_FAILURE_CODES.contains(code)) {
        return new TemporarilyUnavailableException();
      }
    }
    return new ProviderAuthorizationRejectedException();
  }
}
