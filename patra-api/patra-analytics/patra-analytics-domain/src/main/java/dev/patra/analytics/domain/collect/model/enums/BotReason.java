package dev.patra.analytics.domain.collect.model.enums;

/// 判定为爬虫的原因（埋点方案 §6.7），按检查先后排列。
public enum BotReason {
  /// UA 匹配 COUNTER-Robots 列表
  UA_LIST("ua_list"),
  /// Yauaa 把 UA 归为非人类的设备类别
  UA_CLASS("ua_class"),
  /// 同一设备 5 分钟内的文献浏览超过阈值
  RATE("rate");

  private final String wire;

  BotReason(String wire) {
    this.wire = wire;
  }

  /// 富化事件里 `bot_reason` 字段的取值。
  ///
  /// @return snake_case 的机器值
  public String wire() {
    return wire;
  }
}
