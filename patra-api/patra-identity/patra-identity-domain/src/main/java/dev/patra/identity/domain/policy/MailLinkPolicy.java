package dev.patra.identity.domain.policy;

import dev.patra.identity.domain.model.vo.EmailTokenSecret;
import java.net.URI;
import java.util.Objects;

/// 邮件里站内链接的拼法：门户地址来自配置，永远不从请求的 Host 头取；令牌只放在 URL 片段里，
/// 片段不随 HTTP 请求发出，邮件网关预取、访问日志、Referer 都拿不到它。
///
/// @param baseUrl 门户的绝对地址（`http` / `https`），末尾不带 `/`
/// @param verifyEmailPath 验证邮箱落地页的路径，以 `/` 开头
/// @param securitySettingsPath 安全设置页的路径，以 `/` 开头
public record MailLinkPolicy(String baseUrl, String verifyEmailPath, String securitySettingsPath) {

  /// 校验门户地址是 `http` / `https` 的绝对地址并去掉末尾的 `/`；两个路径必须以 `/` 开头。
  public MailLinkPolicy {
    baseUrl = normalizeBaseUrl(baseUrl);
    requirePath(verifyEmailPath, "verifyEmailPath");
    requirePath(securitySettingsPath, "securitySettingsPath");
  }

  /// 创建规则。
  ///
  /// @param baseUrl 门户的绝对地址
  /// @param verifyEmailPath 验证邮箱落地页的路径
  /// @param securitySettingsPath 安全设置页的路径
  /// @return 规则
  public static MailLinkPolicy of(
      String baseUrl, String verifyEmailPath, String securitySettingsPath) {
    return new MailLinkPolicy(baseUrl, verifyEmailPath, securitySettingsPath);
  }

  /// 验证邮箱的链接：`{baseUrl}{verifyEmailPath}#token={令牌}`。
  ///
  /// @param secret 令牌原文
  /// @return 链接
  public String verifyEmailUrl(EmailTokenSecret secret) {
    Objects.requireNonNull(secret, "secret 不能为 null");
    return baseUrl + verifyEmailPath + "#token=" + secret.value();
  }

  /// 安全设置页的链接，安全通知邮件的「不是我怎么办」指向它。
  ///
  /// @return 链接
  public String securitySettingsUrl() {
    return baseUrl + securitySettingsPath;
  }

  /// 校验并规范化门户地址。
  ///
  /// @param raw 配置值
  /// @return 去掉末尾 `/` 之后的地址
  private static String normalizeBaseUrl(String raw) {
    Objects.requireNonNull(raw, "baseUrl 不能为 null");
    URI uri = URI.create(raw.strip());
    String scheme = uri.getScheme();
    if (scheme == null
        || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
        || uri.getHost() == null) {
      throw new IllegalArgumentException("baseUrl 必须是 http 或 https 的绝对地址，实际值: " + raw);
    }
    String normalized = raw.strip();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  /// 校验路径以 `/` 开头。
  ///
  /// @param path 路径
  /// @param name 参数名，进报错信息
  private static void requirePath(String path, String name) {
    Objects.requireNonNull(path, name + " 不能为 null");
    if (!path.startsWith("/")) {
      throw new IllegalArgumentException(name + " 必须以 / 开头，实际值: " + path);
    }
  }
}
