package dev.patra.catalog.integration.fulltext;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.patra.catalog.app.fulltext.usecase.fetch.FullTextFetchRunStats;
import dev.patra.catalog.app.fulltext.usecase.fetch.PmcFullTextFetchCommand;
import dev.patra.catalog.app.fulltext.usecase.parse.FullTextParseRunStats;
import dev.patra.catalog.app.fulltext.usecase.parse.JatsFullTextParseCommand;
import dev.patra.catalog.integration.config.CatalogE2EMinIOContainerInitializer;
import dev.patra.catalog.integration.config.CatalogE2EPostgreSQLContainerInitializer;
import dev.patra.tool.cqrs.CommandBus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/// 全文全链 E2E：真实 PG + MinIO，PMC 由 WireMock 模拟。
///
/// 抓取（下载、校验 md5、读图片宽高、写三张表）→ 解析（正文入库、has_full_text）→
/// 门户读端点（全文、PDF、图片，关闭 OSIV 后经 MVC 下发真实 MinIO 流）；
/// 另覆盖"重抓失败不抹掉已能阅读的全文"与"只有 PDF、PMC 没给 md5"。
@SpringBootTest(
    properties = {
      "spring.cloud.nacos.discovery.enabled=false",
      // e2e 配置默认排除了对象存储自动配置，这里要真实 MinIO
      "spring.autoconfigure.exclude="
    })
@AutoConfigureMockMvc
@ContextConfiguration(
    initializers = {
      CatalogE2EPostgreSQLContainerInitializer.class,
      CatalogE2EMinIOContainerInitializer.class
    })
@ActiveProfiles("e2e-test")
@DisplayName("全文抓取、解析与读端点 E2E")
class FullTextPipelineE2E {

  private static final long PUB_READABLE = 9_900_001L;
  private static final long PUB_PDF_ONLY = 9_900_002L;
  private static final String XML_TYPE = "application/xml";
  private static final String JSON_TYPE = "binary/octet-stream";
  private static final byte[] PDF = "%PDF-1.7 e2e".getBytes(StandardCharsets.UTF_8);

  @RegisterExtension
  static WireMockExtension pmc =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
          .build();

  @DynamicPropertySource
  static void pmcBaseUrl(DynamicPropertyRegistry registry) {
    registry.add("patra.catalog.fulltext.pmc.base-url", () -> pmc.baseUrl() + "/");
  }

  @Autowired private CommandBus commandBus;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws Exception {
    List<String> tables =
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public'"
                + " AND tablename LIKE 'cat_publication%'",
            String.class);
    jdbc.execute("TRUNCATE " + String.join(", ", tables));
    publication(PUB_READABLE, "PMC99001");
    publication(PUB_PDF_ONLY, "PMC99002");

    byte[] xml1 = resource("PMC99001.1.xml");
    byte[] xml2 = resource("PMC99002.1.xml");
    byte[] fig = resource("fig1.webp");
    stubVersions("PMC99001");
    stubVersions("PMC99002");
    stubJson(
        "PMC99001.1",
        """
        {"pmcid": "PMC99001", "is_pmc_openaccess": true, "is_manuscript": false, "is_retracted": false,
         "license_code": "CC BY",
         "xml_url": "s3://pmc-oa-opendata/PMC99001.1/PMC99001.1.xml?md5=%s",
         "pdf_url": "s3://pmc-oa-opendata/PMC99001.1/PMC99001.1.pdf?md5=%s",
         "media_urls": ["s3://pmc-oa-opendata/PMC99001.1/fig1.webp?md5=%s"]}
        """
            .formatted(md5(xml1), md5(PDF), md5(fig)));
    stubJson(
        "PMC99002.1",
        """
        {"pmcid": "PMC99002", "is_pmc_openaccess": true, "is_manuscript": false, "is_retracted": false,
         "license_code": null,
         "xml_url": "s3://pmc-oa-opendata/PMC99002.1/PMC99002.1.xml?md5=%s",
         "pdf_url": "s3://pmc-oa-opendata/PMC99002.1/PMC99002.1.pdf"}
        """
            .formatted(md5(xml2)));
    stubFile("/PMC99001.1/PMC99001.1.xml", xml1, XML_TYPE);
    stubFile("/PMC99001.1/PMC99001.1.pdf", PDF, "application/pdf");
    stubFile("/PMC99001.1/fig1.webp", fig, "image/webp");
    stubFile("/PMC99002.1/PMC99002.1.xml", xml2, XML_TYPE);
    stubFile("/PMC99002.1/PMC99002.1.pdf", PDF, "application/pdf");
  }

  @Test
  @DisplayName("抓取 → 解析 → 全文、PDF、图片可读；重抓失败不抹掉已能阅读的全文")
  void should_fetch_parse_and_serve_full_text() throws Exception {
    FullTextFetchRunStats fetched = commandBus.handle(new PmcFullTextFetchCommand(List.of()));
    assertThat(fetched.fetched()).isEqualTo(2);
    Map<String, Object> record =
        jdbc.queryForMap(
            "SELECT status, pmc_version, object_prefix, media::text AS media, pdf_size, pdf_md5"
                + " FROM cat_publication_fulltext WHERE publication_id = ?",
            PUB_READABLE);
    assertThat(record)
        .containsEntry("status", "FETCHED")
        .containsEntry("pmc_version", "PMC99001.1");
    assertThat((String) record.get("media")).contains("\"width\": 3").contains("\"height\": 2");
    assertThat(
            jdbc.queryForObject(
                "SELECT is_oa FROM cat_publication WHERE id = ?", Boolean.class, PUB_READABLE))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cat_publication_oa_location WHERE publication_id = ?"
                    + " AND location_type = 'pubmed_central'",
                Integer.class,
                PUB_READABLE))
        .isEqualTo(1);

    FullTextParseRunStats parsed = commandBus.handle(new JatsFullTextParseCommand());
    assertThat(parsed.parsed()).isEqualTo(1);
    assertThat(parsed.noBody()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT has_full_text FROM cat_publication_metadata WHERE publication_id = ?",
                Boolean.class,
                PUB_READABLE))
        .isTrue();

    mockMvc
        .perform(MockMvcRequestBuilders.get("/portal/publications/{id}/fulltext", PUB_READABLE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.license.url").value("https://creativecommons.org/licenses/by/4.0/"))
        .andExpect(jsonPath("$.body.sections[0].blocks[0].kind").value("paragraph"))
        .andExpect(jsonPath("$.body.sections[0].blocks[1].width").value(3))
        .andExpect(jsonPath("$.body.references[0].id").value("R1"));
    byte[] pdf =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/portal/publications/{id}/fulltext/pdf", PUB_READABLE))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ETAG, "\"" + md5(PDF) + "\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(md5(pdf)).isEqualTo(md5(PDF));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                "/portal/publications/{id}/fulltext/figures/fig1.webp", PUB_READABLE))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/webp"));

    // 只有 PDF、PMC 没给 md5：全文接口 200 不带正文，PDF 照常下发且不带 ETag
    mockMvc
        .perform(MockMvcRequestBuilders.get("/portal/publications/{id}/fulltext", PUB_PDF_ONLY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").doesNotExist());
    mockMvc
        .perform(MockMvcRequestBuilders.get("/portal/publications/{id}/fulltext/pdf", PUB_PDF_ONLY))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist(HttpHeaders.ETAG));

    // 手动重抓遇到 PMC 503：已能阅读的全文保持可读
    pmc.stubFor(
        WireMock.get(urlPathEqualTo("/"))
            .withQueryParam("prefix", equalTo("PMC99001."))
            .willReturn(aResponse().withStatus(503)));
    FullTextFetchRunStats refetch =
        commandBus.handle(new PmcFullTextFetchCommand(List.of(PUB_READABLE)));
    assertThat(refetch.failed()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM cat_publication_fulltext WHERE publication_id = ?",
                String.class,
                PUB_READABLE))
        .isEqualTo("PARSED");
    mockMvc
        .perform(MockMvcRequestBuilders.get("/portal/publications/{id}/fulltext", PUB_READABLE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body.sections[0].id").value("s1"));
  }

  private void publication(long id, String pmcid) {
    jdbc.update(
        "INSERT INTO cat_publication (id, provenance_code, pmid, venue_instance_id, title, publication_year)"
            + " VALUES (?, 'PUBMED', ?, 1, 'T', 2025)",
        id,
        String.valueOf(id));
    jdbc.update(
        "INSERT INTO cat_publication_identifier (id, publication_id, type, identifier_value, source)"
            + " VALUES (?, ?, 'pmc', ?, 'PubMed')",
        id + 10,
        id,
        pmcid);
    jdbc.update(
        "INSERT INTO cat_publication_metadata (id, publication_id, has_full_text) VALUES (?, ?, false)",
        id + 20,
        id);
  }

  private static void stubVersions(String pmcid) {
    pmc.stubFor(
        WireMock.get(urlPathEqualTo("/"))
            .withQueryParam("prefix", equalTo(pmcid + "."))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", XML_TYPE)
                    .withBody(
                        "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Prefix>"
                            + pmcid
                            + ".</Prefix><CommonPrefixes><Prefix>"
                            + pmcid
                            + ".1/</Prefix></CommonPrefixes></ListBucketResult>")));
  }

  private static void stubJson(String prefix, String json) {
    pmc.stubFor(
        WireMock.get(urlEqualTo("/" + prefix + "/" + prefix + ".json"))
            .willReturn(aResponse().withHeader("Content-Type", JSON_TYPE).withBody(json)));
  }

  private static void stubFile(String path, byte[] body, String type) {
    pmc.stubFor(
        WireMock.get(urlEqualTo(path))
            .willReturn(aResponse().withHeader("Content-Type", type).withBody(body)));
  }

  private byte[] resource(String name) throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/fulltext/" + name)) {
      if (in == null) {
        throw new IllegalStateException("测试资源不存在：" + name);
      }
      return in.readAllBytes();
    }
  }

  private static String md5(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
  }
}
