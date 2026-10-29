package dev.patra.catalog.infra.publication.batch;

import dev.patra.catalog.domain.kernel.port.source.FileDownloadPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/// 下载 PubMed 文件并按 NCBI 提供的 `.md5` 旁文件校验，不符就删掉重下。
///
/// 旁文件内容形如 `MD5(pubmed26n1335.xml.gz)= 35b5eddf88f6772fc99fc00cfec173c9`。校验先于解压与解析，
/// 截断的文件不会进入解析（mini 到 NCBI 的网络传大文件会截断）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public final class PubmedFileFetcher {

  /// `MD5(文件名)= 32 位十六进制`。
  private static final Pattern MD5_LINE = Pattern.compile("^MD5\\((.+)\\)= ([0-9a-f]{32})\\s*$");

  private final FileDownloadPort downloadPort;
  private final int maxAttempts;

  /// 创建抓取器。
  ///
  /// @param downloadPort 文件下载端口
  /// @param maxAttempts 最多尝试次数（≥ 1）
  public PubmedFileFetcher(FileDownloadPort downloadPort, int maxAttempts) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts 必须 ≥ 1：" + maxAttempts);
    }
    this.downloadPort = downloadPort;
    this.maxAttempts = maxAttempts;
  }

  /// 下载并校验。
  ///
  /// @param fileUrl 文件地址（`.xml.gz`）
  /// @return 校验通过的本地临时文件，由调用方删除
  /// @throws PubmedFileFetchException 尝试 `maxAttempts` 次仍失败时
  public Path fetch(URI fileUrl) {
    RuntimeException last = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      Path file = null;
      try {
        file = downloadPort.download(fileUrl).filePath();
        String expected = expectedMd5(URI.create(fileUrl + ".md5"));
        String actual = md5Of(file);
        if (expected.equals(actual)) {
          return file;
        }
        last = new IllegalStateException("md5 不符：期望 " + expected + "，实际 " + actual);
      } catch (RuntimeException e) {
        last = e;
      }
      log.warn(
          "PubMed 文件下载或校验失败，第 {}/{} 次：{}，{}", attempt, maxAttempts, fileUrl, last.getMessage());
      deleteQuietly(file);
    }
    throw new PubmedFileFetchException("PubMed 文件下载或校验失败 " + maxAttempts + " 次：" + fileUrl, last);
  }

  /// 下载并解析 `.md5` 旁文件。
  ///
  /// @param md5Url 旁文件地址
  /// @return 期望的 md5（小写十六进制）
  private String expectedMd5(URI md5Url) {
    Path sidecar = downloadPort.download(md5Url).filePath();
    try {
      String content = Files.readString(sidecar, StandardCharsets.US_ASCII).trim();
      Matcher m = MD5_LINE.matcher(content);
      if (!m.matches()) {
        throw new IllegalStateException("md5 旁文件格式不对：" + content);
      }
      return m.group(2);
    } catch (IOException e) {
      throw new IllegalStateException("读取 md5 旁文件失败：" + md5Url, e);
    } finally {
      deleteQuietly(sidecar);
    }
  }

  /// 计算文件的 md5。
  ///
  /// @param file 文件
  /// @return md5（小写十六进制）
  private static String md5Of(Path file) {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
        in.transferTo(OutputStream.nullOutputStream());
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("计算 md5 失败：" + file, e);
    }
  }

  /// 删除文件，失败只记日志。
  ///
  /// @param file 文件，可空
  private static void deleteQuietly(Path file) {
    if (file == null) {
      return;
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("删除临时文件失败：{}", file, e);
    }
  }
}
