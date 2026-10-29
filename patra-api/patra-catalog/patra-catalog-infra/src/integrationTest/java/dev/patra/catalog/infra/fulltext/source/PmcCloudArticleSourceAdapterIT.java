package dev.patra.catalog.infra.fulltext.source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.patra.catalog.domain.fulltext.exception.PmcSourceException;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleFile;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleMetadata;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/// `PmcCloudArticleSourceAdapter` WireMock 集成测试（响应结构取自 2026-10-19 对 PMC 桶的实测）。
@WireMockTest
@DisplayName("PmcCloudArticleSourceAdapter WireMock 集成测试")
class PmcCloudArticleSourceAdapterIT {

  private static final String JSON_TYPE = "binary/octet-stream";

  private PmcCloudArticleSourceAdapter adapter;

  private String baseUrl;

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
    baseUrl = wm.getHttpBaseUrl() + "/";
    RestClient restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    adapter = new PmcCloudArticleSourceAdapter(restClient, JsonMapper.builder().build(), baseUrl);
  }

  @Test
  @DisplayName("从 ListObjectsV2 的 CommonPrefixes 列出版本前缀，忽略顶层 Prefix")
  void should_list_version_prefixes_from_common_prefixes() {
    stubFor(
        get(urlPathEqualTo("/"))
            .withQueryParam("list-type", equalTo("2"))
            .withQueryParam("prefix", equalTo("PMC123."))
            .withQueryParam("delimiter", equalTo("/"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/xml")
                    .withBody(
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                          <Name>pmc-oa-opendata</Name><Prefix>PMC123.</Prefix>
                          <KeyCount>2</KeyCount><Delimiter>/</Delimiter><IsTruncated>false</IsTruncated>
                          <CommonPrefixes><Prefix>PMC123.1/</Prefix></CommonPrefixes>
                          <CommonPrefixes><Prefix>PMC123.2/</Prefix></CommonPrefixes>
                        </ListBucketResult>
                        """)));

    assertThat(adapter.listVersionPrefixes("PMC123")).containsExactly("PMC123.1", "PMC123.2");
  }

  @Test
  @DisplayName("桶里没有这篇时返回空列表")
  void should_return_empty_when_not_in_bucket() {
    stubFor(
        get(urlPathEqualTo("/"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/xml")
                    .withBody(
                        """
                        <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                          <Name>pmc-oa-opendata</Name><Prefix>PMC9.</Prefix><KeyCount>0</KeyCount>
                        </ListBucketResult>
                        """)));

    assertThat(adapter.listVersionPrefixes("PMC9")).isEmpty();
  }

  @Test
  @DisplayName("解析版本元数据，media 只保留图片（含 webp）")
  void should_parse_metadata_json() {
    stubFor(
        get(urlEqualTo("/PMC13583816.1/PMC13583816.1.json"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", JSON_TYPE)
                    .withBody(
                        """
                        {"pmcid": "PMC13583816", "version": 1, "is_pmc_openaccess": true,
                         "is_manuscript": false, "is_retracted": false, "license_code": "CC BY-NC-ND",
                         "xml_url": "s3://pmc-oa-opendata/PMC13583816.1/PMC13583816.1.xml?md5=02e7188728a46592ce9bd142b6dc76cd",
                         "pdf_url": "s3://pmc-oa-opendata/PMC13583816.1/PMC13583816.1.pdf?md5=c47862ef2aa62863c623367b42e329ce",
                         "media_urls": [
                           "s3://pmc-oa-opendata/PMC13583816.1/fig1.JPG?md5=11111111111111111111111111111111",
                           "s3://pmc-oa-opendata/PMC13583816.1/bmjopen-16-9-g001.webp?md5=3b8a803b6ef8297c8172730ff768deb9",
                           "s3://pmc-oa-opendata/PMC13583816.1/bmjopen-16-9-s001.docx?md5=18f60e1d23b4da6ddba35084fb03fde9",
                           "s3://pmc-oa-opendata/PMC13583816.1/bmjopen-2026-122934.reviewer_comments.pdf?md5=33efed7e28acd545c5c1e19a124e2736"
                         ]}
                        """)));

    PmcArticleMetadata metadata = adapter.getMetadata("PMC13583816.1");

    assertThat(metadata.pmcid()).isEqualTo("PMC13583816");
    assertThat(metadata.versionPrefix()).isEqualTo("PMC13583816.1");
    assertThat(metadata.openAccess()).isTrue();
    assertThat(metadata.manuscript()).isFalse();
    assertThat(metadata.retracted()).isFalse();
    assertThat(metadata.licenseCode()).isEqualTo("CC BY-NC-ND");
    assertThat(metadata.xml())
        .isEqualTo(PmcArticleFile.of("PMC13583816.1.xml", "02e7188728a46592ce9bd142b6dc76cd"));
    assertThat(metadata.pdf())
        .isEqualTo(PmcArticleFile.of("PMC13583816.1.pdf", "c47862ef2aa62863c623367b42e329ce"));
    assertThat(metadata.media())
        .extracting(PmcArticleFile::fileName)
        .containsExactly("fig1.JPG", "bmjopen-16-9-g001.webp");
  }

  @Test
  @DisplayName("没有 media_urls、pdf_url 键且许可为 null 时按空处理")
  void should_tolerate_missing_media_pdf_and_license() {
    stubFor(
        get(urlEqualTo("/PMC7121018.1/PMC7121018.1.json"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", JSON_TYPE)
                    .withBody(
                        """
                        {"pmcid": "PMC7121018", "is_pmc_openaccess": true, "is_manuscript": false,
                         "is_retracted": false, "license_code": null,
                         "xml_url": "s3://pmc-oa-opendata/PMC7121018.1/PMC7121018.1.xml?md5=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                        """)));

    PmcArticleMetadata metadata = adapter.getMetadata("PMC7121018.1");

    assertThat(metadata.pdf()).isNull();
    assertThat(metadata.media()).isEmpty();
    assertThat(metadata.licenseCode()).isNull();
  }

  @Test
  @DisplayName("服务端出错时抛 PmcSourceException")
  void should_throw_when_server_error() {
    stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(500)));
    stubFor(get(urlEqualTo("/PMC1.1/PMC1.1.json")).willReturn(aResponse().withStatus(503)));

    assertThatThrownBy(() -> adapter.listVersionPrefixes("PMC1"))
        .isInstanceOf(PmcSourceException.class);
    assertThatThrownBy(() -> adapter.getMetadata("PMC1.1")).isInstanceOf(PmcSourceException.class);
  }

  @Test
  @DisplayName("拼出文件的 HTTPS 地址")
  void should_build_file_uri() {
    assertThat(adapter.fileUri("PMC1.2", "a b.jpg"))
        .isEqualTo(URI.create(baseUrl + "PMC1.2/a%20b.jpg"));
  }
}
