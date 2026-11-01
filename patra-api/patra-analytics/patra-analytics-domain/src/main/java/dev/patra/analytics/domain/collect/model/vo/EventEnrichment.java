package dev.patra.analytics.domain.collect.model.vo;

import dev.patra.analytics.domain.collect.model.enums.BotReason;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 采集端点给一条事件补上的字段（埋点方案 §4.3）。
///
/// @param receivedAt 采集端点收到这一批的时刻
/// @param derivedAt 校正后的发生时刻
/// @param geoLocation 地区
/// @param userAgent UA 解析结果
/// @param botReason 判为爬虫的原因；不是爬虫为 `null`
@Builder
public record EventEnrichment(
    Instant receivedAt,
    Instant derivedAt,
    GeoLocation geoLocation,
    UserAgentInfo userAgent,
    BotReason botReason) {

  /// 除爬虫原因外都必填。
  public EventEnrichment {
    Objects.requireNonNull(receivedAt, "receivedAt");
    Objects.requireNonNull(derivedAt, "derivedAt");
    Objects.requireNonNull(geoLocation, "geoLocation");
    Objects.requireNonNull(userAgent, "userAgent");
  }

  /// 是否判为爬虫。
  ///
  /// @return 有爬虫原因时为 `true`
  public boolean bot() {
    return botReason != null;
  }
}
