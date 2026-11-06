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
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/// 人机验证的装配。
///
/// 启动时缺凭据或门槛不合法直接失败。运行中刷新配置时没法这样拒绝，因为 Spring 先绑定新值、后校验，
/// 校验不通过新值也已生效：切到缺凭据的厂商时校验返回“验证不了”，其他不合法的值由 {@link #currentPolicy} 兜底，
/// 都不会让登录报 500，也不会放行。
///
/// 出站用自己建的 `RestClient`，不用容器里的 `RestClient.Builder`：它带内部调用的拦截器，身份断言不能发给第三方。
@Slf4j
@Configuration(proxyBeanMethods = false)
public class HumanVerificationConfiguration {

  /// 建连上限，包含 TLS 握手。2026-11-05 实测 identity 容器到 Cloudflare 的握手要 2.1～2.6 秒。
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(4);

  /// 单次请求的上限。JDK 客户端的这个超时从发起请求算起，包含建连，所以它才是单次尝试的总预算。
  /// 同一次实测整个请求要 2.5～3 秒。Turnstile 重试一次，最坏 10 秒，门户等 15 秒。
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

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

  /// 按配置的当前值构造参数。刷新时新值在校验前就写入了，这里兜住不合法的值，不让登录报 500：
  /// 门槛小于 1、窗口不是正数、厂商为空都按默认值生效；邮箱门槛不小于锁定门槛时按“锁定门槛减一”生效，至少为 1。
  /// 这些情况都打 ERROR。
  ///
  /// @param properties 人机验证配置
  /// @param throttle 登录失败限制配置
  /// @return 参数
  static HumanVerificationPolicy currentPolicy(
      HumanVerificationProperties properties, LoginThrottleProperties throttle) {
    HumanVerificationProvider provider = properties.getProvider();
    if (provider == null) {
      log.error("provider 为空，按默认值生效");
      provider = HumanVerificationProperties.DEFAULT_PROVIDER;
    }
    int ipThreshold = properties.getIpFailureThreshold();
    if (ipThreshold < 1) {
      log.error("ip-failure-threshold={} 小于 1，按默认值生效", ipThreshold);
      ipThreshold = HumanVerificationProperties.DEFAULT_IP_FAILURE_THRESHOLD;
    }
    Duration ipWindow = properties.getIpFailureWindow();
    if (ipWindow == null || ipWindow.isZero() || ipWindow.isNegative()) {
      log.error("ip-failure-window={} 不是正数，按默认值生效", ipWindow);
      ipWindow = HumanVerificationProperties.DEFAULT_IP_FAILURE_WINDOW;
    }
    return HumanVerificationPolicy.of(
        provider, currentEmailThreshold(properties, throttle), ipThreshold, ipWindow);
  }

  /// 邮箱门槛的当前值，兜住不合法的值。
  ///
  /// @param properties 人机验证配置
  /// @param throttle 登录失败限制配置
  /// @return 门槛，至少为 1
  private static int currentEmailThreshold(
      HumanVerificationProperties properties, LoginThrottleProperties throttle) {
    int threshold = properties.getEmailFailureThreshold();
    if (threshold < 1) {
      log.error("email-failure-threshold={} 小于 1，按默认值生效", threshold);
      threshold = HumanVerificationProperties.DEFAULT_EMAIL_FAILURE_THRESHOLD;
    }
    if (threshold >= throttle.getMaxFailures()) {
      log.error(
          "email-failure-threshold={} 不小于 login-throttle.max-failures={}，按锁定门槛减一生效",
          threshold,
          throttle.getMaxFailures());
      return Math.max(1, throttle.getMaxFailures() - 1);
    }
    return threshold;
  }

  /// 各厂商的站点标识，没配的不放进去。环境变量没设时绑定出来是空串，也算没配。
  ///
  /// @param properties 人机验证配置
  /// @return 站点标识
  static Map<HumanVerificationProvider, String> siteKeys(HumanVerificationProperties properties) {
    Map<HumanVerificationProvider, String> keys = new EnumMap<>(HumanVerificationProvider.class);
    String turnstile = properties.getTurnstile().getSiteKey();
    if (turnstile != null && !turnstile.isBlank()) {
      keys.put(HumanVerificationProvider.TURNSTILE, turnstile);
    }
    String trustTurn = properties.getTrustTurn().getSiteKey();
    if (trustTurn != null && !trustTurn.isBlank()) {
      keys.put(HumanVerificationProvider.TRUST_TURN, trustTurn);
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
        requireNotEmpty(
            properties.getTurnstile().getAllowedHostnames(), "turnstile.allowed-hostnames");
      }
      case TRUST_TURN -> {
        requireConfigured(properties.getTrustTurn().getSiteKey(), "trust-turn.site-key");
        requireConfigured(properties.getTrustTurn().getAppSecret(), "trust-turn.app-secret");
        requireNotEmpty(
            properties.getTrustTurn().getAllowedOrigins(), "trust-turn.allowed-origins");
      }
    }
  }

  /// 要求允许名单不为空。名单是空的，真实令牌通过了厂商校验也全部被适配器拒绝，运行起来才发现。
  ///
  /// @param values 名单
  /// @param name 配置项，前缀之后的部分
  private static void requireNotEmpty(Set<String> values, String name) {
    if (values == null || values.isEmpty()) {
      throw new IllegalStateException("patra.identity.human-verification." + name + " 不能为空");
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
    factory.setReadTimeout(REQUEST_TIMEOUT);
    return RestClient.builder().requestFactory(factory).build();
  }
}
