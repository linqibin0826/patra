package dev.patra.analytics.simulator.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/// 测试用的小文献池（3,000 篇、60 个刊加 300 篇无刊）：资源在 testFixtures 的 jar 里，拷到临时文件再给出路径。
public final class SmallArticlePoolFixture {

  private static final String RESOURCE = "/simulator/articles-small.csv";
  private static Path file;

  private SmallArticlePoolFixture() {}

  /// 小文献池文件。
  ///
  /// @return 路径（同一个 JVM 里只拷一次）
  public static synchronized Path path() {
    if (file == null) {
      try (InputStream in = SmallArticlePoolFixture.class.getResourceAsStream(RESOURCE)) {
        Path temp = Files.createTempFile("articles-small", ".csv");
        temp.toFile().deleteOnExit();
        Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        file = temp;
      } catch (IOException e) {
        throw new UncheckedIOException("拷贝小文献池失败", e);
      }
    }
    return file;
  }
}
