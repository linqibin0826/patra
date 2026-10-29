package dev.patra.catalog.infra.publication.source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.patra.catalog.domain.publication.exception.PubmedUpdateListingException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/// `PubmedFileListingAdapter` WireMock 集成测试。
@WireMockTest
@DisplayName("PubmedFileListingAdapter WireMock 集成测试")
class PubmedFileListingAdapterIT {

  private static final String INDEX_PATH = "/pubmed/updatefiles/";

  private PubmedFileListingAdapter adapter;

  private String updateUrl;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wm) {
    // WireMock 默认开 h2c，JDK HttpClient 必须固定 HTTP/1.1
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofSeconds(3));
    adapter = new PubmedFileListingAdapter(RestClient.builder().requestFactory(factory).build());
    updateUrl = wm.getHttpBaseUrl() + INDEX_PATH;
  }

  @Test
  @DisplayName("应从目录索引里取最大的文件号")
  void should_return_max_seq_from_index() {
    stubFor(
        get(urlEqualTo(INDEX_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/html")
                    .withBody(
                        """
                        <html><body><pre>
                        <a href="README.txt">README.txt</a>
                        <a href="pubmed26n1335.xml.gz">pubmed26n1335.xml.gz</a> 2026-01-30 14:02
                        <a href="pubmed26n1335.xml.gz.md5">pubmed26n1335.xml.gz.md5</a>
                        <a href="pubmed26n1687.xml.gz">pubmed26n1687.xml.gz</a> 2026-10-18 14:03
                        <a href="pubmed26n1687.xml.gz.md5">pubmed26n1687.xml.gz.md5</a>
                        <a href="pubmed25n1999.xml.gz">pubmed25n1999.xml.gz</a>
                        </pre></body></html>
                        """)));

    assertThat(adapter.findLatestSeq(updateUrl, "pubmed26n")).isEqualTo(1687);
  }

  @Test
  @DisplayName("索引里没有匹配前缀的文件时抛异常")
  void should_throw_when_no_matching_file() {
    stubFor(
        get(urlEqualTo(INDEX_PATH))
            .willReturn(aResponse().withBody("<a href=\"README.txt\">README.txt</a>")));

    assertThatThrownBy(() -> adapter.findLatestSeq(updateUrl, "pubmed26n"))
        .isInstanceOf(PubmedUpdateListingException.class);
  }

  @Test
  @DisplayName("索引返回 500 时抛异常而不是返回 0")
  void should_throw_when_index_returns_500() {
    stubFor(get(urlEqualTo(INDEX_PATH)).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> adapter.findLatestSeq(updateUrl, "pubmed26n"))
        .isInstanceOf(PubmedUpdateListingException.class);
  }
}
