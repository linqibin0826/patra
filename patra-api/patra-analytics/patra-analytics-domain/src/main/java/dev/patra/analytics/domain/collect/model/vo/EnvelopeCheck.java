package dev.patra.analytics.domain.collect.model.vo;

import dev.patra.analytics.domain.kernel.model.enums.RejectionReason;
import java.util.Objects;

/// 一条采集信封的校验结果。
public sealed interface EnvelopeCheck permits EnvelopeCheck.Valid, EnvelopeCheck.Invalid {

  /// 合格。
  ///
  /// @param event 解析好的事件
  record Valid(CollectedEnvelope event) implements EnvelopeCheck {

    /// 事件必填。
    public Valid {
      Objects.requireNonNull(event, "event");
    }
  }

  /// 不合格。
  ///
  /// @param reason 原因
  /// @param detail 第一条错误（JSON 指针 + 说明）
  /// @param eventName 原事件里的 `event_name`；读不出为 `null`
  record Invalid(RejectionReason reason, String detail, String eventName) implements EnvelopeCheck {

    /// 原因与说明必填。
    public Invalid {
      Objects.requireNonNull(reason, "reason");
      Objects.requireNonNull(detail, "detail");
    }
  }
}
