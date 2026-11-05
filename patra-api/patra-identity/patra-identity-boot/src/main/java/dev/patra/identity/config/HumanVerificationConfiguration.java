package dev.patra.identity.config;

import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import dev.patra.identity.domain.account.policy.HumanVerificationPolicy;
import dev.patra.identity.infra.account.verification.TrustTurnAdapter;
import dev.patra.identity.infra.account.verification.TrustTurnSettings;
import dev.patra.identity.infra.account.verification.TurnstileAdapter;
import dev.patra.identity.infra.account.verification.TurnstileSettings;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/// 人机验证的装配。
///
/// 启动时缺凭据或门槛不合法直接失败。刷新配置不走这里：切到缺凭据的厂商时校验返回“验证不了”，
/// 邮箱门槛被改成不小于锁定门槛时按“锁定门槛减一”生效，都不会让登录报 500，也不会放行。
///
/// 出站用自己建的 `RestClient`，不用容器里的 `RestClient.Builder`：它带内部调用的拦截器，身份断言不能发给第三方。
@Slf4j
@Configuration(proxyBeanMethods = false)
public class HumanVerificationConfiguration {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  /// 人机验证参数的来源：每次调用都按配置的当前值构造。
  ///
  /// @param properties 人机验证配置
  /// @param throttle 登录失败限制配置
  /// @return 参数来源
  @Bean
  public Supplier<HumanVerificationPolicy> humanVerificationPolicy(
      HumanVerificationProperties properties, LoginThrottleProperties throttle) {
    requireValidForStartup(properties, throttle);
    return () -> currentPolicy(properties, throttle);
  }

  /// 各厂商站点标识的来源。
  ///
  /// @param properties 人机验证配置
  /// @return 站点标识的来源
  @Bean
  public Supplier<Map<HumanVerificationProvider, String>> humanVerificationSiteKeys(
      HumanVerificationProperties properties) {
    return () -> siteKeys(properties);
  }

  /// Turnstile 适配器。
  ///
  /// @param properties 人机验证配置
  /// @param jsonMapper JSON 解析
  /// @return 适配器
  @Bean
  public TurnstileAdapter turnstileAdapter(
      HumanVerificationProperties properties, JsonMapper jsonMapper) {
    HumanVerificationProperties.Turnstile turnstile = properties.getTurnstile();
    return new TurnstileAdapter(
        outboundClient(),
        jsonMapper,
        () ->
            TurnstileSettings.of(
                turnstile.getBaseUrl(), turnstile.getSecret(), turnstile.getAllowedHostnames()));
  }

  /// TRUSTTURN 适配器。
  ///
  /// @param properties 人机验证配置
  /// @param jsonMapper JSON 序列化
  /// @param clock 时钟
  /// @return 适配器
  @Bean
  public TrustTurnAdapter trustTurnAdapter(
      HumanVerificationProperties properties, JsonMapper jsonMapper, Clock clock) {
    HumanVerificationProperties.TrustTurn trustTurn = properties.getTrustTurn();
    return new TrustTurnAdapter(
        outboundClient(),
        jsonMapper,
        () ->
            TrustTurnSettings.of(
                trustTurn.getBaseUrl(),
                trustTurn.getSiteKey(),
                trustTurn.getAppSecret(),
                trustTurn.getAllowedOrigins()),
        clock);
  }

  /// 当前厂商是 TRUSTTURN 时的提醒。
  ///
  /// @param properties 人机验证配置
  /// @return 提醒
  @Bean
  public HumanVerificationProviderNotice humanVerificationProviderNotice(
      HumanVerificationProperties properties) {
    return new HumanVerificationProviderNotice(properties);
  }

  /// 按配置的当前值构造参数。邮箱门槛不小于锁定门槛时按“锁定门槛减一”生效并打 ERROR，至少为 1。
  ///
  /// @param properties 人机验证配置
  /// @param throttle 登录失败限制配置
  /// @return 参数
  static HumanVerificationPolicy currentPolicy(
      HumanVerificationProperties properties, LoginThrottleProperties throttle) {
    int emailThreshold = properties.getEmailFailureThreshold();
    if (emailThreshold >= throttle.getMaxFailures()) {
      log.error(
          "email-failure-threshold={} 不小于 login-throttle.max-failures={}，按锁定门槛减一生效",
          emailThreshold,
          throttle.getMaxFailures());
      emailThreshold = Math.max(1, throttle.getMaxFailures() - 1);
    }
    return HumanVerificationPolicy.of(
        properties.getProvider(),
        emailThreshold,
        properties.getIpFailureThreshold(),
        properties.getIpFailureWindow());
  }

  /// 各厂商的站点标识，没配的不放进去。
  ///
  /// @param properties 人机验证配置
  /// @return 站点标识
  static Map<HumanVerificationProvider, String> siteKeys(HumanVerificationProperties properties) {
    Map<HumanVerificationProvider, String> keys = new EnumMap<>(HumanVerificationProvider.class);
    if (properties.getTurnstile().getSiteKey() != null) {
      keys.put(HumanVerificationProvider.TURNSTILE, properties.getTurnstile().getSiteKey());
    }
    if (properties.getTrustTurn().getSiteKey() != null) {
      keys.put(HumanVerificationProvider.TRUST_TURN, properties.getTrustTurn().getSiteKey());
    }
    return Map.copyOf(keys);
  }

  /// 启动时校验：当前厂商的凭据要配全，邮箱门槛要小于锁定门槛。
  ///
  /// @param properties 人机验证配置
  /// @param throttle 登录失败限制配置
  private static void requireValidForStartup(
      HumanVerificationProperties properties, LoginThrottleProperties throttle) {
    if (properties.getEmailFailureThreshold() >= throttle.getMaxFailures()) {
      throw new IllegalStateException(
          "patra.identity.human-verification.email-failure-threshold 必须小于"
              + " patra.identity.login-throttle.max-failures");
    }
    switch (properties.getProvider()) {
      case TURNSTILE -> {
        requireConfigured(properties.getTurnstile().getSiteKey(), "turnstile.site-key");
        requireConfigured(properties.getTurnstile().getSecret(), "turnstile.secret");
      }
      case TRUST_TURN -> {
        requireConfigured(properties.getTrustTurn().getSiteKey(), "trust-turn.site-key");
        requireConfigured(properties.getTrustTurn().getAppSecret(), "trust-turn.app-secret");
      }
    }
  }

  /// 要求一个配置项有值。错误信息只点名配置项，不带值。
  ///
  /// @param value 配置值
  /// @param name 配置项，前缀之后的部分
  private static void requireConfigured(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("patra.identity.human-verification." + name + " 没有配置");
    }
  }

  /// 出站客户端：带超时，代理跟随 JVM 默认设置。
  ///
  /// @return 客户端
  private static RestClient outboundClient() {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .proxy(ProxySelector.getDefault())
                .build());
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().requestFactory(factory).build();
  }
}
