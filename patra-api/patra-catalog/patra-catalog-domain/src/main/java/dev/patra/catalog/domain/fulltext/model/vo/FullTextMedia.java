package dev.patra.catalog.domain.fulltext.model.vo;

import java.io.Serial;
import java.io.Serializable;

/// 已下载的一张正文图片。
///
/// @param name 文件名（与 PMC 桶内一致，如 `bmjopen-16-9-g001.webp`）
/// @param width 像素宽，读不出为 `null`
/// @param height 像素高，读不出为 `null`
/// @author linqibin
/// @since 0.11.0
public record FullTextMedia(String name, Integer width, Integer height) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 创建图片记录。
  ///
  /// @param name 文件名
  /// @param width 像素宽，可空
  /// @param height 像素高，可空
  /// @return 图片记录
  public static FullTextMedia of(String name, Integer width, Integer height) {
    return new FullTextMedia(name, width, height);
  }
}
