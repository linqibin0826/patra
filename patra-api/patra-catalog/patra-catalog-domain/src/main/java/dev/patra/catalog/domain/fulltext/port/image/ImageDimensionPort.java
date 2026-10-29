package dev.patra.catalog.domain.fulltext.port.image;

import dev.patra.catalog.domain.fulltext.model.vo.ImageDimensions;
import java.nio.file.Path;
import java.util.Optional;

/// 读取图片像素宽高的端口（只读文件头，不解码整张图）。
///
/// @author linqibin
/// @since 0.11.0
public interface ImageDimensionPort {

  /// 读取图片宽高。
  ///
  /// @param file 图片文件
  /// @return 宽高；格式不认识、文件损坏或读出非正数时为空，不抛异常
  Optional<ImageDimensions> read(Path file);
}
