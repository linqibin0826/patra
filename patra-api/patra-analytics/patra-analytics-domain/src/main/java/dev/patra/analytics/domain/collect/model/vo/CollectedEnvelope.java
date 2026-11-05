package dev.patra.analytics.domain.collect.model.vo;

import dev.patra.analytics.domain.kernel.model.vo.EventName;
import dev.patra.analytics.domain.kernel.model.vo.EventSource;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 通过校验的一条采集信封（埋点方案 §4.2）。`properties` 只在 schema 校验时看，以 JSON 文本原样带过，写出时不改变
/// 字段顺序与数字写法；阅读历史要用的 `article_id` 在校验时另外解析出来。
///
/// @param eventId 事件 ID（小写 UUID），去重的依据
/// @param eventName 事件名
/// @param schemaVersion 版本原文，如 `1-0-0`
/// @param source 来源
/// @param createdAt 产生方时钟的发生时刻
/// @param sentAt 产生方发出这一批的时刻
/// @param deviceId 设备 ID
/// @param sessionId 会话 ID
/// @param userId 登录用户 ID；未登录为 `null`
/// @param pagePath 页面路径；服务端事件可为 `null`
/// @param pageType 页面类型
/// @param referrerHost 来源网页主机名；可为 `null`
/// @param clientIp 访客 IP，只用来查地区与判爬虫，不写出
/// @param userAgent 原始 UA，只用来解析与判爬虫，不写出
/// @param synthetic 是否模拟器造的
/// @param propertiesJson 事件属性的 JSON 文本
/// @param articleId 属性里的 `article_id`；没有或超出 `long` 时为 `null`
@Builder
public record CollectedEnvelope(
    String eventId,
    EventName eventName,
    String schemaVersion,
    EventSource source,
    Instant createdAt,
    Instant sentAt,
    String deviceId,
    String sessionId,
    String userId,
    String pagePath,
    String pageType,
    String referrerHost,
    String clientIp,
    String userAgent,
    boolean synthetic,
    String propertiesJson,
    Long articleId) {

  /// 必填字段不能为空（可空字段见参数说明）。
  public CollectedEnvelope {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(eventName, "eventName");
    Objects.requireNonNull(schemaVersion, "schemaVersion");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(sentAt, "sentAt");
    Objects.requireNonNull(deviceId, "deviceId");
    Objects.requireNonNull(sessionId, "sessionId");
    Objects.requireNonNull(pageType, "pageType");
    Objects.requireNonNull(clientIp, "clientIp");
    Objects.requireNonNull(userAgent, "userAgent");
    Objects.requireNonNull(propertiesJson, "propertiesJson");
  }
}
