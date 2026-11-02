package dev.patra.analytics.simulator.event;

/// 事件来源（埋点方案 §3.2）：门户服务端或浏览器。
public enum EventSource {
  /// 门户服务端
  SERVER("server"),
  /// 浏览器
  CLIENT("client");

  private final String wire;

  EventSource(String wire) {
    this.wire = wire;
  }

  /// 事件里的写法。
  ///
  /// @return `server` / `client`
  public String wire() {
    return wire;
  }
}
