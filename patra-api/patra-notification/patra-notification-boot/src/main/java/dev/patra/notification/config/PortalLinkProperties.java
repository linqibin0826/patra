package dev.patra.notification.config;

import java.net.URI;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// 提醒邮件里门户链接的拼法（spec 第 10 节）：门户地址在 Nacos `patra-notification.yaml`，取环境变量
/// `PATRA_PORTAL_BASE_URL`（与 identity 同一个，线上由 `.env.common` 给 `https://patra.linqibin.dev`）。
/// 域名只来自配置，不来自请求的 `Host` 头。
///
/// @param baseUrl 门户地址，必须是绝对的 http(s) 地址
@ConfigurationProperties("patra.notification.mail.links")
public record PortalLinkProperties(String baseUrl) {

  /// 缺失或不是绝对的 http(s) 地址时启动失败：邮件里的链接与一键退订地址都靠它，不能等到发信时才发现。
  public PortalLinkProperties {
    Objects.requireNonNull(baseUrl, "patra.notification.mail.links.base-url 不能为空");
    URI uri;
    try {
      uri = URI.create(baseUrl.strip());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("门户地址不合法: " + baseUrl, ex);
    }
    if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
        || uri.getHost() == null) {
      throw new IllegalArgumentException("门户地址必须是绝对的 http(s) 地址: " + baseUrl);
    }
  }
}
