package dev.linqibin.patra.gateway.security;

import com.nimbusds.jose.jwk.ECKey;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner;
import dev.linqibin.patra.starter.security.config.StatelessSecurityDefaults;
import dev.linqibin.patra.starter.security.error.SecurityProblemWriter;
import jakarta.servlet.DispatcherType;
import java.text.ParseException;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;

/// 网关鉴权的装配：会话存储、签名器、两条过滤器链、入站转发头的剥除、出站头过滤器。
///
/// 两条过滤器链：第一条只匹配拒绝名单、对所有人 403；第二条查会话、identity 默认需登录、其余放行。
/// starter 的默认过滤器链因为这里声明了而让位，它的写出器、错误映射、验签器照常生效。
///
/// 设计：`docs/patra/specs/2026-10-09-gateway-auth-design.md`。
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayIdentityAssertionProperties.class)
public class GatewaySecurityConfiguration {

  /// 拒绝名单：各服务的内部接口、后台接口、actuator。通配符只放第一段（服务前缀），
  /// 这三组路径在各服务里都挂在根上。
  static final String[] BLOCKED_PATHS = {"/*/_internal/**", "/*/admin/**", "/*/actuator/**"};

  /// identity 上匿名也能访问的路径：注册、登录、登出（幂等，会话已失效也要能到达）、OpenAPI 文档（Scalar 聚合要拉）。
  static final String[] IDENTITY_PUBLIC_PATHS = {
    "/patra-identity/auth/register",
    "/patra-identity/auth/login",
    "/patra-identity/auth/logout",
    "/patra-identity/v3/api-docs",
    "/patra-identity/v3/api-docs/**"
  };

  /// identity 的全部路径，公开名单之外默认需登录。
  static final String IDENTITY_PATHS = "/patra-identity/**";

  /// Redis 里的会话存储，和 identity 共用同一份契约；网关只查和续期。
  ///
  /// @param redis Redis 模板，Boot 按 `spring.data.redis.*` 装配
  /// @param clock 容器里的时钟（starter-core 提供）
  /// @return 存储
  @Bean
  public RedisSessionStore redisSessionStore(StringRedisTemplate redis, Clock clock) {
    return new RedisSessionStore(redis, clock);
  }

  /// 第一条链：拒绝名单，对任何人 403。
  ///
  /// 链里没有认证过滤器，所有人在这条链上都是匿名，撞上 `denyAll` 时框架走的是未登录入口点；
  /// 把入口点换成拒绝访问的输出，匿名和已登录就得到同一个 403，不读令牌、不查 Redis。
  ///
  /// @param http Spring Security 的构建器
  /// @param problemWriter 统一的错误写出器
  /// @return 过滤器链
  @Bean
  @Order(1)
  public SecurityFilterChain blockedPathsFilterChain(
      HttpSecurity http, SecurityProblemWriter problemWriter) {
    http.securityMatcher(BLOCKED_PATHS);
    StatelessSecurityDefaults.apply(http, problemWriter);
    http.exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(
                    (request, response, exception) ->
                        problemWriter.handle(
                            request, response, new AccessDeniedException("拒绝名单内的路径"))))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll());
    return http.build();
  }

  /// 第二条链：其余全部。从 `Authorization: Bearer` 里的会话令牌建立认证，identity 默认需登录，
  /// 公开名单和其他路由全放行。
  ///
  /// 关掉 Spring Security 默认的响应头写出器：它会给缺少的响应补 `Cache-Control: no-store`、
  /// `X-Content-Type-Options` 等，而代理透传的响应要保持下游原样（PAP-69 的契约）。
  /// 第一条链只输出网关自己的 403，保留默认。
  ///
  /// @param http Spring Security 的构建器
  /// @param problemWriter 统一的错误写出器
  /// @param sessions 会话存储
  /// @return 过滤器链
  @Bean
  @Order(2)
  public SecurityFilterChain gatewayFilterChain(
      HttpSecurity http, SecurityProblemWriter problemWriter, RedisSessionStore sessions) {
    StatelessSecurityDefaults.apply(http, problemWriter);
    http.headers(AbstractHttpConfigurer::disable);

    // 转换器给出的已经是认证完成的对象，原样返回；不经过 ProviderManager。
    // 必须用显式类型：AuthenticationFilter 的两个构造器对 lambda 有二义性。
    AuthenticationManager passThrough = authentication -> authentication;
    // 不声明成 Bean：否则 Boot 会把它再注册成全局 servlet 过滤器。
    AuthenticationFilter sessionFilter =
        new AuthenticationFilter(passThrough, new SessionTokenAuthenticationConverter(sessions));
    // 默认的成功处理器会回 302 再继续执行过滤器链，换成什么都不做。
    sessionFilter.setSuccessHandler((request, response, authentication) -> {});
    // 默认的失败处理器遇到服务类异常会原样抛出，换成统一的写出器（503 / 500）。
    sessionFilter.setFailureHandler(problemWriter);

    http.addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                    .permitAll()
                    .requestMatchers(IDENTITY_PUBLIC_PATHS)
                    .permitAll()
                    .requestMatchers(IDENTITY_PATHS)
                    .authenticated()
                    .anyRequest()
                    .permitAll());
    return http.build();
  }

  /// 入站的 `Forwarded` / `X-Forwarded-*` 在 servlet 层直接剥掉、不解释（`removeOnly`）。
  ///
  /// 网关前面没有任何反向代理，这些头从外面进来一律不合法。springdoc 的 Scalar starter 会无条件注册一个
  /// 「应用」入站转发头的 `ForwardedHeaderFilter`：客户端带 `X-Forwarded-Prefix` 就能改写网关看到的路径，
  /// 让 `StripPrefix` 剥错段。这里声明同类型的 Bean 让它让位。注册在最高优先级，先于 Security 和路由，
  /// 所以路径规则、路由、出站的 `X-Forwarded-*` 都按真实请求算。
  /// 将来网关前面放了反向代理：去掉 `removeOnly`，把 `trusted-proxies` 改成代理的地址。
  ///
  /// @return 过滤器注册
  @Bean
  public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
    ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
    filter.setRemoveOnly(true);
    FilterRegistrationBean<ForwardedHeaderFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setDispatcherTypes(
        DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  /// 出站：剥外部 `Authorization`，已登录写入现签的断言。
  ///
  /// @param signer 签名器
  /// @return 过滤器
  @Bean
  public IdentityAssertionRequestHeadersFilter identityAssertionRequestHeadersFilter(
      IdentityAssertionSigner signer) {
    return new IdentityAssertionRequestHeadersFilter(signer);
  }

  /// 安全异常到错误码的映射：starter 的表加一行查会话失败 → 0500。starter 的同类 Bean 随之让位。
  ///
  /// @param http 按网关前缀生成错误码的组
  /// @return 映射
  @Bean
  public GatewaySecurityErrorMappingContributor gatewaySecurityErrorMappingContributor(
      HttpStdErrors.Group http) {
    return new GatewaySecurityErrorMappingContributor(http);
  }

  /// 身份断言的签名器：解析私钥并自签自验一次，密钥配错在启动时就暴露。
  ///
  /// @param properties 私钥配置
  /// @param clock 容器里的时钟
  /// @param identityAssertionDecoder starter 用网关公钥建的验签器
  /// @return 签名器
  @Bean
  public IdentityAssertionSigner identityAssertionSigner(
      GatewayIdentityAssertionProperties properties,
      Clock clock,
      @Qualifier("identityAssertionDecoder") JwtDecoder identityAssertionDecoder) {
    return createSigner(properties.privateKey(), clock, identityAssertionDecoder);
  }

  /// 解析私钥、构造签名器、自检。
  ///
  /// 错误信息只出现配置项名和 `kid`，不出现密钥内容。
  ///
  /// @param privateKey 配置值，含私钥的 EC P-256 JWK JSON
  /// @param clock 时钟
  /// @param decoder 用网关公钥建的验签器
  /// @return 签名器
  /// @throws IllegalStateException 没配、不是 JWK、密钥不可用、和公钥不配对时
  static IdentityAssertionSigner createSigner(String privateKey, Clock clock, JwtDecoder decoder) {
    String property = GatewayIdentityAssertionProperties.PRIVATE_KEY_PROPERTY;
    if (privateKey == null || privateKey.isBlank()) {
      throw new IllegalStateException(
          "配置项 " + property + " 不能为空：它是网关签身份断言的私钥（含私钥的 EC P-256 JWK JSON）");
    }
    ECKey key;
    try {
      key = ECKey.parse(privateKey);
    } catch (ParseException e) {
      throw new IllegalStateException("配置项 " + property + " 不是合法的 EC JWK JSON", e);
    }
    IdentityAssertionSigner signer;
    try {
      signer = new IdentityAssertionSigner(key, clock);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("配置项 " + property + " 不可用：" + e.getMessage(), e);
    }
    CurrentUser probe = CurrentUser.of(1L, 1L, AccountType.USER, ClientType.WEB);
    try {
      decoder.decode(signer.sign(probe));
    } catch (JwtException e) {
      throw new IllegalStateException(
          "配置项 "
              + property
              + " 的私钥与 patra.security.identity-assertion.public-keys 里的公钥不配对或 kid 不一致（kid="
              + key.getKeyID()
              + "）",
          e);
    }
    log.info("身份断言签名密钥就绪，kid={}", key.getKeyID());
    return signer;
  }
}
