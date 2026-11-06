package dev.patra.identity.config;

import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;
import lombok.Data;
import lombok.ToString;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// 人机验证，前缀 `patra.identity.human-verification`。可在线刷新，切换厂商不用重启。
///
/// 这里不带默认密钥和 `site-key`：如果默认放官方测试密钥，线上一旦漏配就会全部放行。缺凭据时启动失败，
/// 见 {@link HumanVerificationConfiguration}。密钥走环境变量，不进仓库。
@Data
@Validated
@ConfigurationProperties(prefix = "patra.identity.human-verification")
public class HumanVerificationProperties {

  /// 默认厂商。
  static final HumanVerificationProvider DEFAULT_PROVIDER = HumanVerificationProvider.TURNSTILE;

  /// 邮箱门槛的默认值。
  static final int DEFAULT_EMAIL_FAILURE_THRESHOLD = 3;

  /// IP 门槛的默认值。
  static final int DEFAULT_IP_FAILURE_THRESHOLD = 5;

  /// IP 窗口的默认值。
  static final Duration DEFAULT_IP_FAILURE_WINDOW = Duration.ofMinutes(15);

  /// 当前厂商。没有“关闭”这个值。
  @NotNull private volatile HumanVerificationProvider provider = DEFAULT_PROVIDER;

  /// 同一邮箱失败多少次后要验证，必须小于 `login-throttle.max-failures`。
  @Min(1)
  private volatile int emailFailureThreshold = DEFAULT_EMAIL_FAILURE_THRESHOLD;

  /// 同一 IP 失败多少次后要验证。
  @Min(1)
  private volatile int ipFailureThreshold = DEFAULT_IP_FAILURE_THRESHOLD;

  /// IP 失败计数的窗口，从第一次失败算起。
  @NotNull
  @DurationMin(nanos = 1)
  private volatile Duration ipFailureWindow = DEFAULT_IP_FAILURE_WINDOW;

  /// Turnstile 的配置。
  @Valid private final Turnstile turnstile = new Turnstile();

  /// TRUSTTURN 的配置。
  @Valid private final TrustTurn trustTurn = new TrustTurn();

  /// Turnstile 的配置。
  @Data
  public static class Turnstile {

    /// 接口地址。
    @NotBlank private volatile String baseUrl = "https://challenges.cloudflare.com";

    /// 站点的公开标识。
    private volatile String siteKey;

    /// 服务端密钥。
    @ToString.Exclude private volatile String secret;

    /// 允许的站点域名。
    private volatile Set<String> allowedHostnames = Set.of();
  }

  /// TRUSTTURN 的配置。
  @Data
  public static class TrustTurn {

    /// 接口地址。
    @NotBlank private volatile String baseUrl = "https://api.trustturn.cc";

    /// 站点的公开标识。
    private volatile String siteKey;

    /// 签名密钥。
    @ToString.Exclude private volatile String appSecret;

    /// 允许的来源。
    private volatile Set<String> allowedOrigins = Set.of();
  }
}
