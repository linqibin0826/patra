package dev.patra.catalog.infra.publication.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.kernel.port.source.FileDownloadPort;
import dev.patra.catalog.domain.kernel.port.source.FileDownloadResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// `PubmedFileFetcher` 单元测试：下载后按 NCBI 的 `.md5` 旁文件校验，不符重试。
///
/// 下载端口用假实现：按 URL 返回预设内容，记录调用次数。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PubmedFileFetcher 单元测试")
class PubmedFileFetcherTest {

  private static final URI FILE = URI.create("https://ftp.example/pubmed26n1335.xml.gz");
  private static final byte[] CONTENT = "full file content".getBytes(StandardCharsets.UTF_8);

  @TempDir Path tmp;

  private final List<URI> requests = new ArrayList<>();

  @Test
  @DisplayName("md5 相符时返回文件，只下一次")
  void should_return_file_when_md5_matches() throws Exception {
    PubmedFileFetcher fetcher = fetcher(url -> url.equals(FILE) ? CONTENT : sidecar(CONTENT));

    Path path = fetcher.fetch(FILE);

    assertThat(Files.readAllBytes(path)).isEqualTo(CONTENT);
    assertThat(requests).containsExactly(FILE, URI.create(FILE + ".md5"));
  }

  @Test
  @DisplayName("md5 不符时重试，3 次都不符则抛出")
  void should_retry_then_fail_when_md5_mismatches() {
    PubmedFileFetcher fetcher =
        fetcher(
            url -> url.equals(FILE) ? CONTENT : sidecar("other".getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(() -> fetcher.fetch(FILE)).isInstanceOf(PubmedFileFetchException.class);
    assertThat(requests.stream().filter(FILE::equals)).hasSize(3);
  }

  @Test
  @DisplayName("第一次下载被截断、第二次完整：校验发现截断后重下成功")
  void should_retry_truncated_file_even_if_gzip_unchecked() throws Exception {
    int[] fileCalls = {0};
    PubmedFileFetcher fetcher =
        fetcher(
            url -> {
              if (!url.equals(FILE)) {
                return sidecar(CONTENT);
              }
              return fileCalls[0]++ == 0 ? "full file".getBytes(StandardCharsets.UTF_8) : CONTENT;
            });

    assertThat(Files.readAllBytes(fetcher.fetch(FILE))).isEqualTo(CONTENT);
  }

  @Test
  @DisplayName(".md5 内容格式不对时抛出")
  void should_fail_on_malformed_md5_sidecar() {
    PubmedFileFetcher fetcher =
        fetcher(url -> url.equals(FILE) ? CONTENT : "garbage".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> fetcher.fetch(FILE)).isInstanceOf(PubmedFileFetchException.class);
  }

  private PubmedFileFetcher fetcher(Function<URI, byte[]> server) {
    FileDownloadPort port =
        url -> {
          requests.add(url);
          try {
            Path file = Files.createTempFile(tmp, "dl", ".tmp");
            byte[] body = server.apply(url);
            Files.write(file, body);
            return FileDownloadResult.of(file, body.length);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        };
    return new PubmedFileFetcher(port, 3);
  }

  private static byte[] sidecar(byte[] content) {
    try {
      String md5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(content));
      return ("MD5(pubmed26n1335.xml.gz)= " + md5 + "\n").getBytes(StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
