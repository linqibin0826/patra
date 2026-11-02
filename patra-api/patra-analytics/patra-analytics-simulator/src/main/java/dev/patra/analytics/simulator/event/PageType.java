package dev.patra.analytics.simulator.event;

/// 模拟器会走到的页面类型（`page_type` 的取值子集）。
public enum PageType {
  /// 首页
  HOME("home"),
  /// 检索
  SEARCH("search"),
  /// 文献详情
  ARTICLE("article"),
  /// 我的文献库
  LIBRARY("library");

  private final String wire;

  PageType(String wire) {
    this.wire = wire;
  }

  /// 事件里的写法。
  ///
  /// @return 如 `article`
  public String wire() {
    return wire;
  }
}
