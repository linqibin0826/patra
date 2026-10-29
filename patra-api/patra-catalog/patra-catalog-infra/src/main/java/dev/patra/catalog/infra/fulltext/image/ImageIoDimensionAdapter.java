package dev.patra.catalog.infra.fulltext.image;

import dev.patra.catalog.domain.fulltext.model.vo.ImageDimensions;
import dev.patra.catalog.domain.fulltext.port.image.ImageDimensionPort;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 用 ImageIO 读图片宽高，只读文件头。
///
/// JDK 自带 jpg / png / gif 的 reader；WebP 由运行时依赖 TwelveMonkeys `imageio-webp` 经 SPI 注册（构造时重新扫描）。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
public class ImageIoDimensionAdapter implements ImageDimensionPort {

  /// 创建适配器并重新扫描 ImageIO 插件。
  ///
  /// ImageIO 的插件注册表在首次使用时按当前线程的类加载器扫描；Spring Boot 可执行 jar 里插件在嵌套 jar 中，
  /// 首次使用若发生在类加载器不对的线程上，WebP 插件就注册不上（宽高静默变空）。在 Spring 创建 bean 时
  /// 主动扫描一次（TwelveMonkeys 文档的推荐做法）。
  public ImageIoDimensionAdapter() {
    ImageIO.scanForPlugins();
  }

  /// 读取图片宽高。
  ///
  /// @param file 图片文件
  /// @return 宽高；读不出时为空
  @Override
  public Optional<ImageDimensions> read(Path file) {
    try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
      if (in == null) {
        return Optional.empty();
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        return Optional.empty();
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        return width > 0 && height > 0
            ? Optional.of(ImageDimensions.of(width, height))
            : Optional.empty();
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      log.debug("读取图片尺寸失败：{}，{}", file, e.toString());
      return Optional.empty();
    }
  }
}
