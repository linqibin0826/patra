package dev.patra.identity.config;

import dev.patra.identity.adapter.account.security.DiscardingAuthorizedClientRepository;
import dev.patra.identity.adapter.account.security.ProviderErrorAwareTokenResponseConverter;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.converter.ClaimTypeConverter;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/// 第三方登录的客户端装配：注册信息与三条出站路径（换令牌、取资料、取公钥）。
///
/// 出站都是自己建的 `RestClient` / `RestTemplate`，故意不用容器里的 `RestClient.Builder`：
/// 它带内部调用的拦截器，身份断言不能发给第三方。框架默认的客户端没有超时，平台卡住时会一直占着请求线程。
@Configuration(proxyBeanMethods = false)
public class IdentityOAuth2ClientConfiguration {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  /// 注册信息。没配客户端 ID 或密钥的平台不注册：入口 404，identity 照常启动，不会因为一家还没建好应用就卡住。
  ///
  /// 不用 Boot 的 `spring.security.oauth2.client.registration` 属性：它要求客户端 ID 非空，一家没配整个应用就起不来；
  /// 它的 `issuer-uri` 还会让启动时联网读发现文档。这里按同样的字段自己建 `ClientRegistration`，`issuerUri`
  /// 只被 `OidcIdTokenValidator` 拿来比较 `iss`。
  ///
  /// @param identity identity 的配置
  /// @return 注册信息仓库
  @Bean
  public ConfiguredClientRegistrations clientRegistrationRepository(IdentityProperties identity) {
    return ConfiguredClientRegistrations.of(identity.oauth().providers());
  }

  /// 解析授权请求：框架的默认实现。框架的过滤器和发起绑定共用。
  ///
  /// @param registrations 注册信息
  /// @return 解析器
  @Bean
  public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
      ClientRegistrationRepository registrations) {
    return new DefaultOAuth2AuthorizationRequestResolver(
        registrations,
        OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
  }

  /// 换令牌的客户端。消息转换器和错误处理器都是框架要求的，和它的默认值一样，只多了超时，
  /// 以及认出平台用 2xx 回的拒绝（GitHub）。
  ///
  /// @return 客户端
  @Bean
  public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
      accessTokenResponseClient() {
    OAuth2AccessTokenResponseHttpMessageConverter tokenResponseConverter =
        new OAuth2AccessTokenResponseHttpMessageConverter();
    tokenResponseConverter.setAccessTokenResponseConverter(
        new ProviderErrorAwareTokenResponseConverter());
    RestClientAuthorizationCodeTokenResponseClient client =
        new RestClientAuthorizationCodeTokenResponseClient();
    client.setRestClient(
        RestClient.builder()
            .requestFactory(outboundRequestFactory())
            .configureMessageConverters(
                converters -> {
                  converters.addCustomConverter(new FormHttpMessageConverter());
                  converters.addCustomConverter(tokenResponseConverter);
                })
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build());
    return client;
  }

  /// 取资料的服务（GitHub）。OIDC 平台不配用户信息地址，框架的 `OidcUserService` 不会发请求。
  ///
  /// @return 服务
  @Bean
  public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
    RestTemplate restTemplate = new RestTemplate(outboundRequestFactory());
    restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
    DefaultOAuth2UserService service = new DefaultOAuth2UserService();
    service.setRestOperations(restTemplate);
    return service;
  }

  /// 验 `id_token` 的解码器工厂。和框架的 `OidcIdTokenDecoderFactory` 做一样的事，只是取公钥带超时：
  /// 框架的工厂在 7.1 没有设置 HTTP 客户端的口子。
  ///
  /// @return 工厂
  @Bean
  public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
    RestOperations restOperations = new RestTemplate(outboundRequestFactory());
    Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();
    return registration ->
        decoders.computeIfAbsent(
            registration.getRegistrationId(), id -> idTokenDecoder(registration, restOperations));
  }

  /// 什么都不存的已授权客户端仓库。声明成 Bean，Boot 留存令牌的默认实现随之让位。
  ///
  /// @return 仓库
  @Bean
  public OAuth2AuthorizedClientRepository authorizedClientRepository() {
    return new DiscardingAuthorizedClientRepository();
  }

  /// 建一个平台的解码器：公钥按 `jwk-set-uri` 懒加载，第一次验签才联网。
  ///
  /// @param registration 注册信息
  /// @param restOperations 取公钥的客户端
  /// @return 解码器
  private static JwtDecoder idTokenDecoder(
      ClientRegistration registration, RestOperations restOperations) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
            .restOperations(restOperations)
            .build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(), new OidcIdTokenValidator(registration)));
    decoder.setClaimSetConverter(
        new ClaimTypeConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverters()));
    return decoder;
  }

  /// 出站请求工厂：带超时，代理跟随 JVM 默认设置。
  ///
  /// @return 工厂
  private static ClientHttpRequestFactory outboundRequestFactory() {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .proxy(ProxySelector.getDefault())
                .build());
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }
}
