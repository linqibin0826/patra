package dev.patra.identity.config;

import dev.patra.identity.adapter.account.security.ProviderLinkAuthorizationStarter;
import dev.patra.identity.adapter.account.security.ProviderLoginFailureHandler;
import dev.patra.identity.adapter.account.security.ProviderLoginSuccessHandler;
import dev.patra.identity.adapter.account.security.StoredAuthorizationRequestRepository;
import dev.patra.identity.domain.account.port.oauth.AuthorizationRequestStorePort;
import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.security.authentication.IdentityAssertionAuthenticationConverter;
import dev.patra.tool.security.autoconfigure.AdditionalSecurityFilterChain;
import dev.patra.tool.security.autoconfigure.StatelessSecurityDefaults;
import dev.patra.tool.security.error.SecurityProblemWriter;
import dev.patra.tool.web.json.LongToStringHttpMessageConvertersCustomizer;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.json.JsonMapper;

/// 第三方登录的过滤器链（PAP-180）：只收发起和回调两组路径，其余仍走 starter 的默认断言链。
///
/// 路径和官方 `oauth2Login` 的默认值一致。发起路径只收配好了的平台：不认识或没配的不进这条链，由 MVC 回 404，
/// 进了框架的重定向过滤器只会是 500；框架的默认登录页 `/login` 也不在匹配范围里。
@Configuration(proxyBeanMethods = false)
public class IdentityOAuth2SecurityConfiguration {

  /// 回调路径。
  private static final String CALLBACK_PATHS = "/login/oauth2/code/*";

  /// 第三方登录的过滤器链。返回类型必须是附加链：默认链的条件按它判断，要留着。
  ///
  /// @param http Spring Security 的构建器
  /// @param problemWriter 统一的错误写出器
  /// @param registrations 配好了的平台
  /// @param repository 授权请求仓库
  /// @param resolver 授权请求解析器
  /// @param tokenClient 换令牌的客户端
  /// @param userService 取资料的服务
  /// @param authorizedClients 什么都不存的已授权客户端仓库
  /// @param successHandler 成功处理器
  /// @param failureHandler 失败处理器
  /// @return 过滤器链
  @Bean
  @Order(1)
  public AdditionalSecurityFilterChain identityOAuth2FilterChain(
      HttpSecurity http,
      SecurityProblemWriter problemWriter,
      ConfiguredClientRegistrations registrations,
      StoredAuthorizationRequestRepository repository,
      OAuth2AuthorizationRequestResolver resolver,
      OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenClient,
      OAuth2UserService<OAuth2UserRequest, OAuth2User> userService,
      OAuth2AuthorizedClientRepository authorizedClients,
      ProviderLoginSuccessHandler successHandler,
      ProviderLoginFailureHandler failureHandler) {
    String[] paths =
        Stream.concat(
                registrations.registrationIds().stream().map(id -> "/oauth2/authorization/" + id),
                Stream.of(CALLBACK_PATHS))
            .toArray(String[]::new);
    http.securityMatcher(paths);
    StatelessSecurityDefaults.apply(http, problemWriter);
    http.oauth2Login(
            login ->
                login
                    .authorizationEndpoint(
                        endpoint ->
                            endpoint
                                .authorizationRequestRepository(repository)
                                .authorizationRequestResolver(resolver))
                    .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenClient))
                    .userInfoEndpoint(endpoint -> endpoint.userService(userService))
                    .authorizedClientRepository(authorizedClients)
                    .successHandler(successHandler)
                    .failureHandler(failureHandler))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
    return new AdditionalSecurityFilterChain(http.build());
  }

  /// 授权请求仓库。
  ///
  /// @param store 存储
  /// @param jsonMapper JSON 映射器
  /// @return 仓库
  @Bean
  public StoredAuthorizationRequestRepository storedAuthorizationRequestRepository(
      AuthorizationRequestStorePort store, JsonMapper jsonMapper) {
    return new StoredAuthorizationRequestRepository(store, jsonMapper);
  }

  /// 成功处理器。
  ///
  /// @param commandBus 命令总线
  /// @param problemWriter 统一的错误写出器
  /// @param jsonConverters MVC 的 JSON 转换器
  /// @param identityAssertionDecoder 身份断言的验签器：绑定回调要现解断言
  /// @return 处理器
  @Bean
  public ProviderLoginSuccessHandler providerLoginSuccessHandler(
      CommandBus commandBus,
      SecurityProblemWriter problemWriter,
      LongToStringHttpMessageConvertersCustomizer jsonConverters,
      @Qualifier("identityAssertionDecoder") JwtDecoder identityAssertionDecoder) {
    return new ProviderLoginSuccessHandler(
        commandBus,
        problemWriter,
        jsonConverters,
        new IdentityAssertionAuthenticationConverter(identityAssertionDecoder));
  }

  /// 发起绑定用的组件。
  ///
  /// @param registrations 配好了的平台
  /// @param resolver 授权请求解析器
  /// @param repository 授权请求仓库
  /// @return 组件
  @Bean
  public ProviderLinkAuthorizationStarter providerLinkAuthorizationStarter(
      ConfiguredClientRegistrations registrations,
      OAuth2AuthorizationRequestResolver resolver,
      StoredAuthorizationRequestRepository repository) {
    return new ProviderLinkAuthorizationStarter(registrations, resolver, repository);
  }

  /// 失败处理器。
  ///
  /// @param problemWriter 统一的错误写出器
  /// @return 处理器
  @Bean
  public ProviderLoginFailureHandler providerLoginFailureHandler(
      SecurityProblemWriter problemWriter) {
    return new ProviderLoginFailureHandler(problemWriter);
  }
}
