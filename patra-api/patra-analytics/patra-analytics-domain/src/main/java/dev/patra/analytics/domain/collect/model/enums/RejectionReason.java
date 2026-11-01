package dev.patra.analytics.domain.collect.model.enums;

/// 事件进 dlq 的原因（埋点方案 §5），按预检顺序排列。
public enum RejectionReason {
  /// 不符合 schema（缺字段、类型不对、多余字段等）
  SCHEMA_INVALID("schema_invalid"),
  /// 事件名不在事件字典里
  UNKNOWN_EVENT("unknown_event"),
  /// 来源与事件名的绑定不对
  SOURCE_MISMATCH("source_mismatch"),
  /// 版本的 MODEL 与当前不同
  VERSION_REJECTED("version_rejected");

  private final String wire;

  RejectionReason(String wire) {
    this.wire = wire;
  }

  /// dlq 记录里 `reason_code` 字段的取值。
  ///
  /// @return snake_case 的机器值
  public String wire() {
    return wire;
  }
}
