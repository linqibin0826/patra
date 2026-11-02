package dev.patra.analytics.simulator.event;

/// 首批 10 个事件与各自的来源（埋点方案 §3.2）。
public enum SimEventName {
  /// 文献详情页服务端渲染
  ARTICLE_VIEWED("article_viewed", EventSource.SERVER),
  /// 检索
  SEARCH_PERFORMED("search_performed", EventSource.SERVER),
  /// 下载 PDF
  PDF_DOWNLOADED("pdf_downloaded", EventSource.SERVER),
  /// 导出
  EXPORT_COMPLETED("export_completed", EventSource.SERVER),
  /// 收藏
  ARTICLE_COLLECTED("article_collected", EventSource.SERVER),
  /// 进入页面
  PAGE_VIEWED("page_viewed", EventSource.CLIENT),
  /// 卡片曝光
  ARTICLE_IMPRESSED("article_impressed", EventSource.CLIENT),
  /// 点击卡片
  ARTICLE_CLICKED("article_clicked", EventSource.CLIENT),
  /// 读全文
  FULLTEXT_READ("fulltext_read", EventSource.CLIENT),
  /// 复制引用
  CITATION_COPIED("citation_copied", EventSource.CLIENT);

  private final String wire;
  private final EventSource source;

  SimEventName(String wire, EventSource source) {
    this.wire = wire;
    this.source = source;
  }

  /// 事件里的写法。
  ///
  /// @return 如 `article_viewed`
  public String wire() {
    return wire;
  }

  /// 来源。
  ///
  /// @return 来源
  public EventSource source() {
    return source;
  }

  /// 是否「查看类」事件（埋点方案 §6.8，算看过人数）。
  ///
  /// @return 是否
  public boolean isView() {
    return this == ARTICLE_VIEWED
        || this == FULLTEXT_READ
        || this == PDF_DOWNLOADED
        || this == ARTICLE_COLLECTED;
  }

  /// 是否「全文类」事件（算读过全文人数）。
  ///
  /// @return 是否
  public boolean isFulltext() {
    return this == FULLTEXT_READ || this == PDF_DOWNLOADED;
  }
}
