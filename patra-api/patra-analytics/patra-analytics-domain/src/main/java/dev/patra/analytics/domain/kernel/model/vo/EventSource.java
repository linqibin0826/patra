package dev.patra.analytics.domain.kernel.model.vo;

/// 事件的来源：门户服务端发的，还是浏览器发的（埋点方案 §3.2）。
public enum EventSource {
  /// 门户服务端在处理请求时发出
  SERVER("server"),
  /// 浏览器执行门户脚本发出
  CLIENT("client");

  private final String wire;

  EventSource(String wire) {
    this.wire = wire;
  }

  /// 事件 JSON 里 `source` 字段的取值。
  ///
  /// @return `server` 或 `client`
  public String wire() {
    return wire;
  }
}
