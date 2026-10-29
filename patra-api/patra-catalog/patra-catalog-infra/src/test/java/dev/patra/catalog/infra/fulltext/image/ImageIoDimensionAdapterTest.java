package dev.patra.catalog.infra.fulltext.image;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.fulltext.model.vo.ImageDimensions;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// `ImageIoDimensionAdapter` 单元测试（夹具由 `magick -size 3x2` 生成）。
@DisplayName("ImageIoDimensionAdapter")
class ImageIoDimensionAdapterTest {

  private final ImageIoDimensionAdapter adapter = new ImageIoDimensionAdapter();

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"sample-3x2.jpg", "sample-3x2.png", "sample-3x2.webp"})
  @DisplayName("读出 jpg、png、webp 的像素宽高")
  void should_read_dimensions_of_jpg_png_and_webp(String fileName) throws Exception {
    assertThat(adapter.read(resource(fileName))).contains(ImageDimensions.of(3, 2));
  }

  @Test
  @DisplayName("创建后 ImageIO 能找到 WebP 的 reader")
  void should_register_webp_reader_after_construction() {
    new ImageIoDimensionAdapter();

    assertThat(ImageIO.getImageReadersByFormatName("webp").hasNext()).isTrue();
  }

  @Test
  @DisplayName("截断的文件返回空")
  void should_return_empty_for_broken_file() throws Exception {
    assertThat(adapter.read(resource("broken.webp"))).isEmpty();
  }

  @Test
  @DisplayName("不是图片的文件返回空")
  void should_return_empty_for_non_image(@TempDir Path dir) throws Exception {
    Path text = Files.writeString(dir.resolve("a.txt"), "not an image");

    assertThat(adapter.read(text)).isEmpty();
  }

  private Path resource(String fileName) throws URISyntaxException {
    return Path.of(getClass().getResource("/image/" + fileName).toURI());
  }
}
