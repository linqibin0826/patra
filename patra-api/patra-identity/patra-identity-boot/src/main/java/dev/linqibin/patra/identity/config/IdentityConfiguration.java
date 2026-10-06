package dev.linqibin.patra.identity.config;

import dev.linqibin.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.password.CommonPasswordPort;
import dev.linqibin.patra.identity.infra.adapter.hashing.PasswordHashingAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// identity 的装配：把配置项变成领域层的规则对象，创建密码哈希适配器。
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityConfiguration {

  /// 登录失败限制的参数。
  ///
  /// @param properties 配置
  /// @return 参数
  @Bean
  public LoginThrottlePolicy loginThrottlePolicy(IdentityProperties properties) {
    IdentityProperties.LoginThrottle throttle = properties.loginThrottle();
    return LoginThrottlePolicy.of(
        throttle.maxFailures(), throttle.window(), throttle.lockDuration(), throttle.inFlightTtl());
  }

  /// 密码哈希：Argon2id。
  ///
  /// @param properties 配置
  /// @return 哈希端口
  @Bean
  public PasswordHashingPort passwordHashingPort(IdentityProperties properties) {
    IdentityProperties.PasswordHashing hashing = properties.passwordHashing();
    return PasswordHashingAdapter.argon2(hashing.maxConcurrent(), hashing.waitTimeout());
  }

  /// 注册时的密码规则。
  ///
  /// @param commonPasswordPort 常见密码名单
  /// @return 规则
  @Bean
  public PasswordPolicy passwordPolicy(CommonPasswordPort commonPasswordPort) {
    return new PasswordPolicy(commonPasswordPort);
  }
}
