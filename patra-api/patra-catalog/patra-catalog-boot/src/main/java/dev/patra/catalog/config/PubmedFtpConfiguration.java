package dev.patra.catalog.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/// NCBI FTP 网页目录的 HTTP 客户端配置。
///
/// 只用来读 updatefiles 目录索引（几十 KB 的 HTML），文件下载仍走 `FileDownloadPort`。
///
/// @author linqibin
/// @since 0.11.0
@Configuration
public class PubmedFtpConfiguration {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  /// 读 NCBI 目录索引的 RestClient，不设 baseUrl，调用时传完整地址。
  ///
  /// @return RestClient
  @Bean
  public RestClient pubmedFtpRestClient() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
