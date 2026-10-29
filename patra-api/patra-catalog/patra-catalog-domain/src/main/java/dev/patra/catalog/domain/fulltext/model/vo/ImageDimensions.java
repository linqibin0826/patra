package dev.patra.catalog.domain.fulltext.model.vo;

/// 图片的像素宽高。
///
/// @param width 宽
/// @param height 高
/// @author linqibin
/// @since 0.11.0
public record ImageDimensions(int width, int height) {

  /// 创建宽高。
  ///
  /// @param width 宽
  /// @param height 高
  /// @return 宽高
  public static ImageDimensions of(int width, int height) {
    return new ImageDimensions(width, height);
  }
}
