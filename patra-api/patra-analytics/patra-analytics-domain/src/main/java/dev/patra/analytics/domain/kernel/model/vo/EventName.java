package dev.patra.analytics.domain.kernel.model.vo;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/// 事件字典里的 10 个事件（埋点方案 §3.2）：机器值与只能由谁发。
///
/// 来源与事件名绑定：服务端事件只能由门户服务端发，浏览器事件只能由浏览器发，浏览器伪造不了浏览数。
public enum EventName {
  /// 文献详情页服务端渲染完成
  ARTICLE_VIEWED("article_viewed", EventSource.SERVER),
  /// 检索页服务端拿到结果
  SEARCH_PERFORMED("search_performed", EventSource.SERVER),
  /// PDF 开始回传
  PDF_DOWNLOADED("pdf_downloaded", EventSource.SERVER),
  /// 导出文件开始回传
  EXPORT_COMPLETED("export_completed", EventSource.SERVER),
  /// 收藏写入成功
  ARTICLE_COLLECTED("article_collected", EventSource.SERVER),
  /// 进入一个页面
  PAGE_VIEWED("page_viewed", EventSource.CLIENT),
  /// 文献卡片满足曝光规则
  ARTICLE_IMPRESSED("article_impressed", EventSource.CLIENT),
  /// 点击文献卡片
  ARTICLE_CLICKED("article_clicked", EventSource.CLIENT),
  /// 全文正文累计可见停留达到 10 秒
  FULLTEXT_READ("fulltext_read", EventSource.CLIENT),
  /// 引用对话框里点「复制」成功
  CITATION_COPIED("citation_copied", EventSource.CLIENT);

  private static final Map<String, EventName> BY_WIRE =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(EventName::wire, Function.identity()));

  private final String wire;
  private final EventSource source;

  EventName(String wire, EventSource source) {
    this.wire = wire;
    this.source = source;
  }

  /// 事件 JSON 里 `event_name` 字段的取值。
  ///
  /// @return snake_case 的机器值，如 `article_viewed`
  public String wire() {
    return wire;
  }

  /// 这个事件只能由谁发。
  ///
  /// @return 绑定的来源
  public EventSource source() {
    return source;
  }

  /// 按机器值找事件。
  ///
  /// @param wire 事件 JSON 里的 `event_name`，可为 `null`
  /// @return 对应的事件；不在字典里时为空
  public static Optional<EventName> fromWire(String wire) {
    return wire == null ? Optional.empty() : Optional.ofNullable(BY_WIRE.get(wire));
  }
}
