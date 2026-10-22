package dev.patra.identity.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// identity 的配置项，前缀 `patra.identity`。启动后不变；可在线刷新的登录失败限制见
/// [LoginThrottleProperties]。
///
/// @param passwordHashing 密码哈希
/// @param session 会话策略
/// @param username 改名规则
/// @param mail 邮件：发件人、显示时区、令牌保留期、链接、重发限流
/// @param reauth 再认证
/// @param passwordReset 找回密码的限流
/// @param emailChange 改邮箱的限流
@ConfigurationProperties(prefix = "patra.identity")
public record IdentityProperties(
    @DefaultValue PasswordHashing passwordHashing,
    @DefaultValue Session session,
    @DefaultValue Username username,
    @DefaultValue Mail mail,
    @DefaultValue Reauth reauth,
    @DefaultValue PasswordReset passwordReset,
    @DefaultValue EmailChange emailChange) {

  /// 再认证。
  ///
  /// @param window 敏感操作要求这么久内输过密码
  /// @param rotationGrace 再认证换令牌的宽限期：上次密码验证距今不足这么久就不换（并发再认证）；零则每次都换
  public record Reauth(
      @DefaultValue("10m") Duration window, @DefaultValue("10s") Duration rotationGrace) {}

  /// 找回密码的限流。
  ///
  /// @param cooldown 同邮箱两次申请的最短间隔
  /// @param perEmailDaily 同邮箱每天的申请上限
  /// @param perIpHourly 同 IP 每小时的申请上限
  /// @param submitPerIpMinutely 同 IP 每分钟的提交上限
  public record PasswordReset(
      @DefaultValue("60s") Duration cooldown,
      @DefaultValue("5") int perEmailDaily,
      @DefaultValue("20") int perIpHourly,
      @DefaultValue("10") int submitPerIpMinutely) {}

  /// 改邮箱的限流。
  ///
  /// @param perAccountDaily 每账号每天的申请上限
  public record EmailChange(@DefaultValue("3") int perAccountDaily) {}

  /// 邮件。
  ///
  /// @param from 发件人，形如 `Patra <no-reply@example.com>`；随服务商定，mini 由 `mail.env` 覆盖
  /// @param zone 安全通知里时间的显示时区
  /// @param tokenRetention 过期令牌保留多久后删行
  /// @param links 邮件里站内链接的拼法
  /// @param resend 重发验证邮件的限流
  public record Mail(
      @DefaultValue("Patra <no-reply@patra.local>") String from,
      @DefaultValue("Asia/Shanghai") String zone,
      @DefaultValue("30d") Duration tokenRetention,
      @DefaultValue Links links,
      @DefaultValue Resend resend) {}

  /// 邮件里站内链接的拼法。
  ///
  /// @param baseUrl 门户的绝对地址
  /// @param verifyEmailPath 验证邮箱落地页的路径
  /// @param securitySettingsPath 安全设置页的路径
  /// @param resetPasswordPath 重设密码落地页的路径
  /// @param confirmEmailChangePath 确认改邮箱落地页的路径
  /// @param revertEmailChangePath 撤销改邮箱落地页的路径
  public record Links(
      @DefaultValue("http://linqibins-mac-mini:4000") String baseUrl,
      @DefaultValue("/verify-email") String verifyEmailPath,
      @DefaultValue("/settings/security") String securitySettingsPath,
      @DefaultValue("/reset-password") String resetPasswordPath,
      @DefaultValue("/confirm-email-change") String confirmEmailChangePath,
      @DefaultValue("/revert-email-change") String revertEmailChangePath) {}

  /// 重发验证邮件的限流。
  ///
  /// @param cooldown 两次发送的最短间隔
  /// @param perAccountHourly 每账号每小时的上限
  /// @param perAccountDaily 每账号每天的上限
  /// @param perIpHourly 每 IP 每小时的上限
  public record Resend(
      @DefaultValue("60s") Duration cooldown,
      @DefaultValue("5") int perAccountHourly,
      @DefaultValue("10") int perAccountDaily,
      @DefaultValue("30") int perIpHourly) {}

  /// 改名规则。
  ///
  /// @param changeCooldown 两次改名的最短间隔
  /// @param reservationPeriod 旧名保留多久
  public record Username(
      @DefaultValue("30d") Duration changeCooldown,
      @DefaultValue("90d") Duration reservationPeriod) {}

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
      @DefaultValue("10") int maxSessionsPerUser, Map<String, Map<String, Lifetime>> lifetime) {

    /// 拷贝有效期表；没配时是空表。
    public Session {
      lifetime = lifetime == null ? Map.of() : Map.copyOf(lifetime);
    }
  }

  /// 一种客户端的会话有效期。
  ///
  /// @param idle 不活跃过期
  /// @param absolute 绝对过期
  public record Lifetime(Duration idle, Duration absolute) {}
}
