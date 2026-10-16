package dev.patra.identity.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// 登录失败限制，前缀 `patra.identity.login-throttle`。可在线刷新（release spec v0.9 决策 D）。
///
/// Nacos 配置变更后由 Spring Cloud 原地重新绑定；使用方每次登录时经
/// `IdentityConfiguration#loginThrottlePolicy` 重新读取，不缓存。
///
/// 不合法的值让启动失败；刷新时新值在校验前已经写入，校验失败只会让刷新报错、健康检查转为 DOWN，
/// 不会保留旧值，所以改动前要在 Nacos 文件的评审里把关。
@Data
@Validated
@ConfigurationProperties(prefix = "patra.identity.login-throttle")
public class LoginThrottleProperties {

  /// 计数窗口内允许的失败次数，到达即上锁。
  @Min(1)
  private volatile int maxFailures = 5;

  /// 计数窗口。
  @NotNull
  @DurationMin(nanos = 1)
  private volatile Duration window = Duration.ofMinutes(15);

  /// 锁定时长。
  @NotNull
  @DurationMin(nanos = 1)
  private volatile Duration lockDuration = Duration.ofMinutes(15);

  /// 在途登记的过期时间。
  @NotNull
  @DurationMin(nanos = 1)
  private volatile Duration inFlightTtl = Duration.ofSeconds(30);
}
