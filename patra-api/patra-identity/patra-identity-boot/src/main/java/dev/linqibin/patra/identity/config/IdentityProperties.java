package dev.linqibin.patra.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// identity 的配置项，前缀 `patra.identity`。
///
/// @param loginThrottle 登录失败限制
/// @param passwordHashing 密码哈希
@ConfigurationProperties(prefix = "patra.identity")
public record IdentityProperties(
    @DefaultValue LoginThrottle loginThrottle, @DefaultValue PasswordHashing passwordHashing) {

  /// 登录失败限制。
  ///
  /// @param maxFailures 计数窗口内允许的失败次数
  /// @param window 计数窗口
  /// @param lockDuration 锁定时长
  /// @param inFlightTtl 在途登记的过期时间
  public record LoginThrottle(
      @DefaultValue("5") int maxFailures,
      @DefaultValue("15m") Duration window,
      @DefaultValue("15m") Duration lockDuration,
      @DefaultValue("30s") Duration inFlightTtl) {}

  /// 密码哈希。
  ///
  /// @param maxConcurrent 同时进行的计算上限
  /// @param waitTimeout 排队等待的最长时间
  public record PasswordHashing(
      @DefaultValue("4") int maxConcurrent, @DefaultValue("3s") Duration waitTimeout) {}
}
