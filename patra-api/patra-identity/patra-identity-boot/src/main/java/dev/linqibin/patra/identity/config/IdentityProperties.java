package dev.linqibin.patra.identity.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// identity 的配置项，前缀 `patra.identity`。
///
/// @param loginThrottle 登录失败限制
/// @param passwordHashing 密码哈希
/// @param session 会话策略
@ConfigurationProperties(prefix = "patra.identity")
public record IdentityProperties(
    @DefaultValue LoginThrottle loginThrottle,
    @DefaultValue PasswordHashing passwordHashing,
    @DefaultValue Session session) {

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

  /// 会话策略。只在 identity 配，网关不配。
  ///
  /// @param maxSessionsPerUser 每用户的会话上限，所有账号类型一个数
  /// @param lifetime 有效期，两层键分别是账号类型和客户端类型的 `code`，比如 `user.web`
  public record Session(
      @DefaultValue("10") int maxSessionsPerUser, Map<String, Map<String, Lifetime>> lifetime) {}

  /// 一种客户端的会话有效期。
  ///
  /// @param idle 不活跃过期
  /// @param absolute 绝对过期
  public record Lifetime(Duration idle, Duration absolute) {}
}
