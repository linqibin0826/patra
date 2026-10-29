package dev.patra.catalog.adapter.fulltext.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.app.fulltext.usecase.query.PortalFullTextFileQueryService;
import dev.patra.catalog.domain.fulltext.exception.FullTextFileNotFoundException;
import dev.patra.catalog.domain.fulltext.exception.FullTextNotAvailableException;
import dev.patra.catalog.domain.fulltext.model.read.FullTextFileRef;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFile;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFileInfo;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// `PortalFullTextFileController` REST 切片测试：PDF 与图片的下发。
@WebMvcTest(controllers = PortalFullTextFileController.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import(PortalFullTextFileController.class)
@AutoConfigureRestTestClient
@TestPropertySource(properties = "patra.web.error.context-prefix=CATALOG")
@DisplayName("PortalFullTextFileController REST 切片测试")
class PortalFullTextFileControllerIT {

  private static final byte[] PDF = "%PDF-1.7 fake".getBytes(StandardCharsets.UTF_8);
  private static final FullTextFileRef PDF_REF =
      FullTextFileRef.builder()
          .objectKey("pmc/PMC1/PMC1.1/PMC1.1.pdf")
          .fileName("PMC1.pdf")
          .contentType("application/pdf")
          .size(PDF.length)
          .etag("c47862ef2aa62863c623367b42e329ce")
          .build();

  @Autowired private RestTestClient restClient;

  @MockitoBean private PortalFullTextFileQueryService queryService;

  @Test
  @DisplayName("PDF 以附件下发，五个响应头齐全")
  void should_stream_pdf_with_headers() {
    when(queryService.getPdf(7L)).thenReturn(PDF_REF);
    when(queryService.open(PDF_REF)).thenReturn(stored(PDF, "application/pdf"));

    byte[] body =
        restClient
            .get()
            .uri("/portal/publications/7/fulltext/pdf")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_PDF)
            .expectHeader()
            .contentLength(PDF.length)
            .expectHeader()
            .valueEquals(HttpHeaders.ETAG, "\"c47862ef2aa62863c623367b42e329ce\"")
            .expectHeader()
            .valueEquals(HttpHeaders.CACHE_CONTROL, "max-age=86400, public")
            .expectHeader()
            .valueEquals(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"PMC1.pdf\"")
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

    assertThat(body).isEqualTo(PDF);
  }

  @Test
  @DisplayName("PMC 没给 md5 时照常下发，不带 ETag、不做条件请求")
  void should_stream_pdf_without_etag_when_md5_missing() {
    FullTextFileRef noMd5 =
        FullTextFileRef.builder()
            .objectKey(PDF_REF.objectKey())
            .fileName("PMC1.pdf")
            .contentType("application/pdf")
            .size(PDF.length)
            .build();
    when(queryService.getPdf(9L)).thenReturn(noMd5);
    when(queryService.open(noMd5)).thenReturn(stored(PDF, "application/pdf"));

    restClient
        .get()
        .uri("/portal/publications/9/fulltext/pdf")
        .header(HttpHeaders.IF_NONE_MATCH, "\"anything\"")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .doesNotExist(HttpHeaders.ETAG);
  }

  @Test
  @DisplayName("If-None-Match 命中时返回 304、不打开对象")
  void should_return_304_when_etag_matches() {
    when(queryService.getPdf(7L)).thenReturn(PDF_REF);

    restClient
        .get()
        .uri("/portal/publications/7/fulltext/pdf")
        .header(HttpHeaders.IF_NONE_MATCH, "\"c47862ef2aa62863c623367b42e329ce\"")
        .exchange()
        .expectStatus()
        .isNotModified();

    verify(queryService, never()).open(any());
  }

  @Test
  @DisplayName("弱 ETag 与 ETag 列表同样命中 304")
  void should_return_304_for_weak_and_list_if_none_match() {
    when(queryService.getPdf(7L)).thenReturn(PDF_REF);

    restClient
        .get()
        .uri("/portal/publications/7/fulltext/pdf")
        .header(HttpHeaders.IF_NONE_MATCH, "W/\"c47862ef2aa62863c623367b42e329ce\"")
        .exchange()
        .expectStatus()
        .isNotModified();
    restClient
        .get()
        .uri("/portal/publications/7/fulltext/pdf")
        .header(HttpHeaders.IF_NONE_MATCH, "\"x\", \"c47862ef2aa62863c623367b42e329ce\"")
        .exchange()
        .expectStatus()
        .isNotModified();
  }

  @Test
  @DisplayName("不可下载时在开流前返回 404 problem+json，不打开对象")
  void should_return_404_before_streaming_when_pdf_missing() {
    when(queryService.getPdf(8L)).thenThrow(new FullTextNotAvailableException(8L));

    restClient
        .get()
        .uri("/portal/publications/8/fulltext/pdf")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CATALOG-0404");

    verify(queryService, never()).open(any());
  }

  @Test
  @DisplayName("对象在存储里不存在时返回 404")
  void should_return_404_when_object_missing_in_storage() {
    when(queryService.getPdf(7L)).thenReturn(PDF_REF);
    when(queryService.open(PDF_REF))
        .thenThrow(new FullTextFileNotFoundException(PDF_REF.objectKey(), null));

    restClient
        .get()
        .uri("/portal/publications/7/fulltext/pdf")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  @DisplayName("图片以存储里的内容类型下发，长期缓存")
  void should_stream_figure_with_immutable_cache() {
    FullTextFileRef ref =
        FullTextFileRef.builder()
            .objectKey("pmc/PMC1/PMC1.1/g001.webp")
            .fileName("g001.webp")
            .contentType("image/webp")
            .size(3)
            .etag("abc")
            .build();
    when(queryService.getFigure(7L, "g001.webp")).thenReturn(ref);
    when(queryService.open(ref)).thenReturn(stored(new byte[] {1, 2, 3}, "image/webp"));

    restClient
        .get()
        .uri("/portal/publications/7/fulltext/figures/g001.webp")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType("image/webp")
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "max-age=604800, public, immutable")
        .expectHeader()
        .valueEquals(HttpHeaders.ETAG, "\"abc\"");
  }

  @Test
  @DisplayName("非法文件名返回 404 而不是 422，不查询")
  void should_return_404_for_illegal_file_name() {
    restClient
        .get()
        .uri("/portal/publications/7/fulltext/figures/{name}", "a b.jpg")
        .exchange()
        .expectStatus()
        .isNotFound();
    restClient
        .get()
        .uri("/portal/publications/7/fulltext/figures/..%2Fsecret")
        .exchange()
        .expectStatus()
        .isNotFound();

    verifyNoInteractions(queryService);
  }

  private static StoredFile stored(byte[] bytes, String contentType) {
    return StoredFile.of(
        new ByteArrayInputStream(bytes), StoredFileInfo.of(bytes.length, contentType, "e"));
  }
}
