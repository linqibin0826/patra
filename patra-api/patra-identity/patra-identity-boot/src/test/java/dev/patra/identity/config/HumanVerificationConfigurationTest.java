package dev.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import dev.patra.identity.domain.account.policy.HumanVerificationPolicy;
import dev.patra.identity.domain.account.port.verification.HumanVerificationPort;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/// 人机验证配置：缺凭据时启动失败；运行中被刷新成不合法的值时不让登录出错。
@DisplayName("HumanVerificationConfiguration")
@ExtendWith(OutputCaptureExtension.class)
class HumanVerificationConfigurationTest {

  private static final String PREFIX = "patra.identity.human-verification.";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(PropertiesConfig.class, HumanVerificationConfiguration.class)
          .withBean(Clock.class, Clock::systemUTC)
          .withBean(JsonMapper.class, () -> JsonMapper.builder().build());

  @Test
  @DisplayName("当前厂商缺密钥：启动失败")
  void fails_startup_when_active_provider_has_no_secret() {
    runner
        .withPropertyValues(PREFIX + "turnstile.site-key=site")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("当前厂商缺 site-key：启动失败")
  void fails_startup_when_turnstile_site_key_missing() {
    runner
        .withPropertyValues(PREFIX + "turnstile.secret=value")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("邮箱门槛不小于锁定门槛：启动失败")
  void fails_startup_when_email_threshold_not_below_lock_threshold() {
    runner
        .withPropertyValues(
            PREFIX + "turnstile.site-key=site",
            PREFIX + "turnstile.secret=value",
            PREFIX + "email-failure-threshold=5")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("没启用的厂商缺凭据：照常启动，两家适配器都在")
  void starts_when_inactive_provider_has_no_credentials() {
    runner
        .withPropertyValues(PREFIX + "turnstile.site-key=site", PREFIX + "turnstile.secret=value")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(HumanVerificationPort.class)).hasSize(2);
            });
  }

  @Test
  @DisplayName("主配置不带默认密钥：线上漏配不会变成全部放行")
  void properties_have_no_default_credentials() {
    HumanVerificationProperties properties = new HumanVerificationProperties();

    assertThat(properties.getProvider()).isEqualTo(HumanVerificationProvider.TURNSTILE);
    assertThat(properties.getTurnstile().getSecret()).isNull();
    assertThat(properties.getTurnstile().getSiteKey()).isNull();
    assertThat(properties.getTrustTurn().getAppSecret()).isNull();
  }

  @Test
  @DisplayName("每次都读配置的当前值，刷新后下一次就用上")
  void policy_reads_current_values_after_change() {
    HumanVerificationProperties properties = new HumanVerificationProperties();
    LoginThrottleProperties throttle = new LoginThrottleProperties();

    assertThat(HumanVerificationConfiguration.currentPolicy(properties, throttle).provider())
        .isEqualTo(HumanVerificationProvider.TURNSTILE);

    properties.setProvider(HumanVerificationProvider.TRUST_TURN);
    properties.setIpFailureThreshold(9);
    HumanVerificationPolicy policy =
        HumanVerificationConfiguration.currentPolicy(properties, throttle);

    assertThat(policy.provider()).isEqualTo(HumanVerificationProvider.TRUST_TURN);
    assertThat(policy.ipFailureThreshold()).isEqualTo(9);
  }

  @Test
  @DisplayName("运行中邮箱门槛被刷新成不小于锁定门槛：按锁定门槛减一生效，不抛异常")
  void policy_clamps_email_threshold_after_invalid_refresh(CapturedOutput output) {
    HumanVerificationProperties properties = new HumanVerificationProperties();
    properties.setEmailFailureThreshold(9);
    LoginThrottleProperties throttle = new LoginThrottleProperties();

    HumanVerificationPolicy policy =
        HumanVerificationConfiguration.currentPolicy(properties, throttle);

    assertThat(policy.emailFailureThreshold()).isEqualTo(throttle.getMaxFailures() - 1);
    assertThat(output).contains("email-failure-threshold");
  }

  @Test
  @DisplayName("运行中门槛被刷新成 0：按默认值生效，不抛异常，登录不报 500")
  void policy_falls_back_when_thresholds_refreshed_to_zero(CapturedOutput output) {
    HumanVerificationProperties properties = new HumanVerificationProperties();
    properties.setEmailFailureThreshold(0);
    properties.setIpFailureThreshold(0);

    HumanVerificationPolicy policy =
        HumanVerificationConfiguration.currentPolicy(properties, new LoginThrottleProperties());

    assertThat(policy.emailFailureThreshold()).isEqualTo(3);
    assertThat(policy.ipFailureThreshold()).isEqualTo(5);
    assertThat(output).contains("ip-failure-threshold");
  }

  @Test
  @DisplayName("运行中窗口被刷新成 0 或空：按默认值生效，不抛异常")
  void policy_falls_back_when_window_refreshed_to_invalid() {
    HumanVerificationProperties properties = new HumanVerificationProperties();
    LoginThrottleProperties throttle = new LoginThrottleProperties();

    properties.setIpFailureWindow(Duration.ZERO);
    assertThat(HumanVerificationConfiguration.currentPolicy(properties, throttle).ipFailureWindow())
        .isEqualTo(Duration.ofMinutes(15));

    properties.setIpFailureWindow(null);
    assertThat(HumanVerificationConfiguration.currentPolicy(properties, throttle).ipFailureWindow())
        .isEqualTo(Duration.ofMinutes(15));
  }

  @Test
  @DisplayName("运行中厂商被刷新成空：回到默认的 Turnstile，不抛异常")
  void policy_falls_back_when_provider_refreshed_to_null() {
    HumanVerificationProperties properties = new HumanVerificationProperties();
    properties.setProvider(null);

    assertThat(
            HumanVerificationConfiguration.currentPolicy(properties, new LoginThrottleProperties())
                .provider())
        .isEqualTo(HumanVerificationProvider.TURNSTILE);
  }

  @Test
  @DisplayName("配置对象的 toString 不输出密钥")
  void properties_to_string_hides_credentials() {
    HumanVerificationProperties properties = new HumanVerificationProperties();
    properties.getTurnstile().setSecret("turnstile-secret-value");
    properties.getTrustTurn().setAppSecret("trustturn-secret-value");

    assertThat(properties.toString())
        .doesNotContain("turnstile-secret-value", "trustturn-secret-value");
  }

  @Test
  @DisplayName("锁定门槛是 1 时，邮箱门槛不会被算成 0")
  void policy_clamp_never_goes_below_one() {
    HumanVerificationProperties properties = new HumanVerificationProperties();
    LoginThrottleProperties throttle = new LoginThrottleProperties();
    throttle.setMaxFailures(1);

    assertThat(
            HumanVerificationConfiguration.currentPolicy(properties, throttle)
                .emailFailureThreshold())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("当前是 TRUSTTURN：启动和刷新后各打一条 WARN，写明访客信息会发给它")
  void logs_warn_when_active_provider_is_trust_turn(CapturedOutput output) {
    runner
        .withPropertyValues(
            PREFIX + "provider=trustturn",
            PREFIX + "trust-turn.site-key=site",
            PREFIX + "trust-turn.app-secret=value")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              context.getBean(HumanVerificationProviderNotice.class).warnIfTrustTurn();
              context.publishEvent(new RefreshScopeRefreshedEvent());

              assertThat(output.getOut()).containsPattern("(?s)访客.*访客");
            });
  }

  @Test
  @DisplayName("当前是 Turnstile：不打这条 WARN")
  void does_not_warn_when_active_provider_is_turnstile(CapturedOutput output) {
    runner
        .withPropertyValues(PREFIX + "turnstile.site-key=site", PREFIX + "turnstile.secret=value")
        .run(
            context -> {
              context.getBean(HumanVerificationProviderNotice.class).warnIfTrustTurn();

              assertThat(output.getOut()).doesNotContain("访客");
            });
  }

  @Test
  @DisplayName("站点标识按厂商取")
  void site_keys_by_provider() {
    HumanVerificationProperties properties = new HumanVerificationProperties();
    properties.getTurnstile().setSiteKey("a");
    properties.getTrustTurn().setSiteKey("b");

    assertThat(HumanVerificationConfiguration.siteKeys(properties))
        .isEqualTo(
            Map.of(
                HumanVerificationProvider.TURNSTILE, "a",
                HumanVerificationProvider.TRUST_TURN, "b"));
  }

  /// Nacos 里写成 `${PATRA_...:}`，环境变量没设时绑定出来是空串，不是 null。
  @Test
  @DisplayName("站点标识是空串时算没配")
  void site_keys_exclude_blank_values() {
    HumanVerificationProperties properties = new HumanVerificationProperties();
    properties.getTurnstile().setSiteKey("a");
    properties.getTrustTurn().setSiteKey("");

    assertThat(HumanVerificationConfiguration.siteKeys(properties))
        .isEqualTo(Map.of(HumanVerificationProvider.TURNSTILE, "a"));
  }

  /// 测试用的配置注册。
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties({HumanVerificationProperties.class, LoginThrottleProperties.class})
  static class PropertiesConfig {}
}
