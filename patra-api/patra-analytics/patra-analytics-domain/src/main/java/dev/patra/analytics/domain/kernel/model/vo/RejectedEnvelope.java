package dev.patra.analytics.domain.kernel.model.vo;

import dev.patra.analytics.domain.kernel.model.enums.RejectionReason;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 一条不合格的事件（写进 dlq 前的样子，埋点方案 §5）：采集预检不合格，或入库时被 ClickHouse 拒绝。
///
/// @param reason 原因
/// @param detail 校验器给出的第一条错误
/// @param eventName 原事件里的 `event_name`；读不出为 `null`
/// @param rawJson 原事件的 JSON 文本（写出时删 IP 与 UA、截断）
/// @param receivedAt 采集端点收到的时刻（入库被拒时为转 dlq 的时刻）
@Builder
public record RejectedEnvelope(
    RejectionReason reason, String detail, String eventName, String rawJson, Instant receivedAt) {

  /// 除事件名外都必填。
  public RejectedEnvelope {
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(detail, "detail");
    Objects.requireNonNull(rawJson, "rawJson");
    Objects.requireNonNull(receivedAt, "receivedAt");
  }
}
