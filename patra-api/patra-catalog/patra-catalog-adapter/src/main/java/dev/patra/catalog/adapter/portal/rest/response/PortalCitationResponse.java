package dev.patra.catalog.adapter.portal.rest.response;

import java.util.List;

/// 复制引用响应（spec 第 7.1 节）：序号与正文分开，门户自己拼 `label + " " + text`。
///
/// @param style 样式代码
/// @param entries 引用条目；编号样式按请求顺序，APA 按作者字母序
/// @author linqibin
/// @since 0.1.0
public record PortalCitationResponse(String style, List<Entry> entries) {

  /// 紧凑构造器：防御性拷贝。
  public PortalCitationResponse {
    entries = entries != null ? List.copyOf(entries) : List.of();
  }

  /// 创建响应。
  ///
  /// @param style 样式代码
  /// @param entries 引用条目
  /// @return 响应
  public static PortalCitationResponse of(String style, List<Entry> entries) {
    return new PortalCitationResponse(style, entries);
  }

  /// 一条引用。
  ///
  /// @param label 序号原样（`[1]`、`1.`）；APA 为 null
  /// @param text 不含序号的纯文本条目
  public record Entry(String label, String text) {

    /// 创建条目。
    ///
    /// @param label 序号，可空
    /// @param text 正文
    /// @return 条目
    public static Entry of(String label, String text) {
      return new Entry(label, text);
    }
  }
}
