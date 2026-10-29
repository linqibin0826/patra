package dev.patra.catalog.infra.publication.source;

import dev.patra.catalog.domain.publication.exception.PubmedUpdateListingException;
import dev.patra.catalog.domain.publication.port.source.PubmedFileListingPort;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/// 读取 NCBI updatefiles 目录 HTML 索引的适配器。
///
/// 索引是 Apache 风格的目录页，每个文件一行 `<a href="pubmed26n1335.xml.gz">`，
/// 同名的 `.md5` 文件与其他文件按 href 精确匹配排除。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
public class PubmedFileListingAdapter implements PubmedFileListingPort {

  private final RestClient restClient;

  /// 创建适配器。
  ///
  /// @param restClient 访问 NCBI FTP 网页目录的客户端（无 baseUrl，调用时传完整地址）
  public PubmedFileListingAdapter(@Qualifier("pubmedFtpRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  /// 返回目录里当前最大的文件号。
  ///
  /// @param updateUrl updatefiles 目录地址
  /// @param filePrefix 文件名前缀，如 `pubmed26n`
  /// @return 最大文件号
  /// @throws PubmedUpdateListingException 读取失败或没有匹配文件时抛出
  @Override
  public int findLatestSeq(String updateUrl, String filePrefix) {
    String index;
    try {
      index = restClient.get().uri(updateUrl).retrieve().body(String.class);
    } catch (RestClientException e) {
      throw new PubmedUpdateListingException("读取 updatefiles 目录失败：" + updateUrl, e);
    }
    if (index == null) {
      throw new PubmedUpdateListingException("updatefiles 目录返回空内容：" + updateUrl);
    }

    Matcher matcher =
        Pattern.compile("href=\"" + Pattern.quote(filePrefix) + "(\\d{4,})\\.xml\\.gz\"")
            .matcher(index);
    int max = 0;
    while (matcher.find()) {
      max = Math.max(max, Integer.parseInt(matcher.group(1)));
    }
    if (max == 0) {
      throw new PubmedUpdateListingException(
          "updatefiles 目录里没有前缀为 " + filePrefix + " 的文件：" + updateUrl);
    }
    log.info("updatefiles 目录最新文件号：{}{}", filePrefix, max);
    return max;
  }
}
