package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.adapter.account.rest.response.LoginMethodsResponse;
import dev.patra.identity.adapter.account.rest.response.ProviderLinkStartedResponse;
import dev.patra.identity.adapter.account.security.ProviderLinkAuthorizationStarter;
import dev.patra.identity.app.account.usecase.oauth.PrepareProviderLinkCommand;
import dev.patra.identity.app.account.usecase.oauth.UnlinkProviderAccountCommand;
import dev.patra.identity.app.account.usecase.query.LoginMethodQueryService;
import dev.patra.identity.domain.account.exception.UnknownIdentityProviderException;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/// 设置页的「登录方式」：列表、发起绑定、解绑。绑定的回调走 `oauth2Login` 的过滤器链。
@Tag(name = "Me", description = "当前用户的账号设置")
@RestController
@RequestMapping("/me/login-methods")
@RequiredArgsConstructor
public class MeLoginMethodController {

  private final CommandBus commandBus;
  private final LoginMethodQueryService loginMethodQueryService;
  private final ProviderLinkAuthorizationStarter linkAuthorizationStarter;

  /// 当前用户的登录方式。
  ///
  /// @return 有没有密码和绑定的第三方账号
  @GetMapping
  public LoginMethodsResponse list() {
    return LoginMethodsResponse.from(loginMethodQueryService.current());
  }

  /// 发起绑定：要求再认证、邮箱已验证、这一家没绑过。返回平台的授权地址。
  ///
  /// @param provider 平台的小写名
  /// @param http 原始请求
  /// @param httpResponse 原始响应
  /// @return 授权地址
  @PostMapping("/{provider}/link")
  public ProviderLinkStartedResponse link(
      @PathVariable String provider, HttpServletRequest http, HttpServletResponse httpResponse) {
    IdentityProvider target = providerOf(provider);
    CurrentUser initiator = commandBus.handle(PrepareProviderLinkCommand.of(target));
    return ProviderLinkStartedResponse.of(
        linkAuthorizationStarter.start(target, initiator, http, httpResponse));
  }

  /// 解绑：成功或本来就没绑都是 204；409 最后一种登录方式，403 要再认证。
  ///
  /// @param provider 平台的小写名
  /// @param http 原始请求，取客户端 IP
  /// @param userAgent `User-Agent` 头，可以没有；进安全通知的设备
  @DeleteMapping("/{provider}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unlink(
      @PathVariable String provider,
      HttpServletRequest http,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    commandBus.handle(
        UnlinkProviderAccountCommand.of(providerOf(provider), ClientIp.from(http), userAgent));
  }

  /// 路径里的平台名。
  ///
  /// @param raw 路径里的值
  /// @return 平台
  /// @throws UnknownIdentityProviderException 不认识
  private static IdentityProvider providerOf(String raw) {
    return IdentityProvider.fromRegistrationId(raw)
        .orElseThrow(UnknownIdentityProviderException::new);
  }
}
