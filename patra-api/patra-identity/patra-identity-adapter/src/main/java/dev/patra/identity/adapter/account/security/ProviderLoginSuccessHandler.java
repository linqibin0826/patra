package dev.patra.identity.adapter.account.security;

import dev.patra.identity.adapter.account.rest.ClientIp;
import dev.patra.identity.adapter.account.rest.response.CurrentUserResponse;
import dev.patra.identity.adapter.account.rest.response.ProviderAuthenticatedResponse;
import dev.patra.identity.adapter.account.rest.response.ProviderLinkedResponse;
import dev.patra.identity.adapter.account.rest.response.ProviderRegistrationRequiredResponse;
import dev.patra.identity.app.account.usecase.oauth.CompleteProviderLoginCommand;
import dev.patra.identity.app.account.usecase.oauth.LinkProviderAccountCommand;
import dev.patra.identity.app.account.usecase.oauth.ProviderLoginResult;
import dev.patra.identity.domain.account.model.vo.ProviderAccount;
import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.security.authentication.CurrentUserAuthentication;
import dev.patra.tool.security.error.SecurityProblemWriter;
import dev.patra.tool.web.json.LongToStringHttpMessageConvertersCustomizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/// 平台确认了身份：转成 `ProviderAccount`，交给用例，把结果写成 JSON。Cookie 只由门户写（v0.19 决策 B）。
///
/// 这里在 `ExceptionTranslationFilter` 之前运行，抛出的业务异常到不了全局异常处理器，要自己写错误。
/// JSON 用 MVC 的响应转换器写，雪花 ID 才是字符串。
@RequiredArgsConstructor
public class ProviderLoginSuccessHandler implements AuthenticationSuccessHandler {

  /// 门户转发回调时放设备标识的请求头。
  public static final String DEVICE_ID_HEADER = "X-Patra-Device-Id";

  private final CommandBus commandBus;
  private final SecurityProblemWriter problemWriter;
  private final LongToStringHttpMessageConvertersCustomizer jsonConverters;
  private final AuthenticationConverter assertionConverter;

  /// 处理回调。
  ///
  /// @param request 请求
  /// @param response 响应
  /// @param authentication 框架的认证结果
  /// @throws IOException 写响应失败时
  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    Object body;
    try {
      body = handle(request, authentication);
    } catch (RuntimeException e) {
      problemWriter.writeProblem(request, response, e);
      return;
    }
    jsonConverters
        .jsonConverter()
        .write(body, MediaType.APPLICATION_JSON, new ServletServerHttpResponse(response));
  }

  /// 按授权的用途分流。
  ///
  /// @param request 请求
  /// @param authentication 框架的认证结果
  /// @return 响应体
  private Object handle(HttpServletRequest request, Authentication authentication) {
    if (!(authentication instanceof OAuth2AuthenticationToken token)) {
      throw new IllegalStateException("oauth2Login 给的不是 OAuth2AuthenticationToken");
    }
    ProviderAccount account = ProviderAccountMapper.toAccount(token);
    OAuth2AuthorizationRequest consumed =
        (OAuth2AuthorizationRequest)
            request.getAttribute(StoredAuthorizationRequestRepository.CONSUMED_REQUEST_ATTRIBUTE);
    return switch (ProviderAuthorizationIntent.of(consumed)) {
      case ProviderAuthorizationIntent.Login _ -> login(request, account);
      case ProviderAuthorizationIntent.Link link -> link(request, account, link);
    };
  }

  /// 登录：老用户发会话，新用户要补填。
  ///
  /// @param request 请求
  /// @param account 平台确认过的账号
  /// @return 响应体
  private Object login(HttpServletRequest request, ProviderAccount account) {
    ProviderLoginResult result =
        commandBus.handle(
            CompleteProviderLoginCommand.builder()
                .account(account)
                .deviceId(request.getHeader(DEVICE_ID_HEADER))
                .clientIp(ClientIp.from(request))
                .userAgent(request.getHeader(HttpHeaders.USER_AGENT))
                .build());
    return switch (result) {
      case ProviderLoginResult.Authenticated authenticated ->
          ProviderAuthenticatedResponse.of(
              authenticated.user().sessionToken(),
              CurrentUserResponse.from(authenticated.user().account()));
      case ProviderLoginResult.RegistrationRequired required ->
          ProviderRegistrationRequiredResponse.of(required.ticket(), required.pending());
    };
  }

  /// 绑定：核对发起人后写外部身份。当前用户现解断言：此时安全上下文里已经是框架的 `OAuth2AuthenticationToken`。
  ///
  /// @param request 请求
  /// @param account 平台确认过的账号
  /// @param link 发起人
  /// @return 响应体
  private Object link(
      HttpServletRequest request, ProviderAccount account, ProviderAuthorizationIntent.Link link) {
    CurrentUserAuthentication current =
        (CurrentUserAuthentication) assertionConverter.convert(request);
    return ProviderLinkedResponse.from(
        commandBus.handle(
            LinkProviderAccountCommand.builder()
                .account(account)
                .initiatorUserId(link.userId())
                .initiatorSessionId(link.sessionId())
                .current(current == null ? null : current.getPrincipal())
                .clientIp(ClientIp.from(request))
                .userAgent(request.getHeader(HttpHeaders.USER_AGENT))
                .build()));
  }
}
