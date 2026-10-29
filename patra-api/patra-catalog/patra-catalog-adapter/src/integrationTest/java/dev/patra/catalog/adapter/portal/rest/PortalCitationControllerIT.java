package dev.patra.catalog.adapter.portal.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.patra.catalog.adapter.config.CatalogAdapterITWebMvcConfig;
import dev.patra.catalog.app.portal.usecase.query.PortalCitationQueryService;
import dev.patra.catalog.app.portal.usecase.query.PortalPublicationExportService;
import dev.patra.catalog.domain.portal.model.enums.CitationStyle;
import dev.patra.catalog.domain.portal.model.enums.ExportFormat;
import dev.patra.catalog.domain.portal.model.read.CitationEntry;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/// `PortalCitationController` REST 切片测试：导出（流式，用 MockMvc 异步分派读响应体）与复制引用。
///
/// `RestTestClient` 读不到 `StreamingResponseBody`，所以这里只用 MockMvc。
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest(controllers = PortalCitationController.class)
@ContextConfiguration(classes = CatalogAdapterITWebMvcConfig.class)
@Import({PortalCitationController.class, PortalCitationControllerIT.FixedClockConfig.class})
@TestPropertySource(properties = "patra.web.error.context-prefix=CATALOG")
@DisplayName("PortalCitationController REST 切片测试")
class PortalCitationControllerIT {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PortalPublicationExportService exportService;
  @MockitoBean private PortalCitationQueryService citationService;

  /// 固定时钟：UTC 10-23 17:00 = 上海 10-24 01:00，用来确认文件名日期取上海当天。
  @TestConfiguration
  static class FixedClockConfig {

    /// 固定时钟。
    ///
    /// @return 时钟
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-23T17:00:00Z"), ZoneOffset.UTC);
    }
  }

  // ===== POST /portal/publications/export =====

  @Test
  @DisplayName("导出 RIS：流式写出，Content-Type、Content-Disposition（UTF-8 文件名、上海日期）、正文")
  void should_stream_ris_with_headers() throws Exception {
    doAnswer(
            inv -> {
              OutputStream out = inv.getArgument(2);
              out.write("TY  - JOUR\r\nER  - \r\n".getBytes(StandardCharsets.UTF_8));
              return null;
            })
        .when(exportService)
        .export(eq(List.of(38L, 7L)), eq(ExportFormat.RIS), any(OutputStream.class));

    MvcResult started =
        mockMvc
            .perform(
                post("/portal/publications/export")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ids\":[\"38\",7],\"format\":\"ris\",\"name\":\"组会\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(started))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Content-Type", startsWith("application/x-research-info-systems")))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    containsString("filename*=UTF-8''patra-%E7%BB%84%E4%BC%9A-2026-10-24.ris")))
        .andExpect(header().string("Content-Disposition", startsWith("attachment")))
        .andExpect(content().bytes("TY  - JOUR\r\nER  - \r\n".getBytes(StandardCharsets.UTF_8)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidExportBodies")
  @DisplayName("导出：ids 空 / 缺失 / 超 5,000 / 非正、format 未知或缺失、name 超 100 字 → 422，不触达服务")
  void should_reject_invalid_export_requests_with_422(String body) throws Exception {
    mockMvc
        .perform(
            post("/portal/publications/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().is(422));

    verifyNoInteractions(exportService);
  }

  static List<String> invalidExportBodies() {
    String ids5001 =
        LongStream.rangeClosed(1, 5001).mapToObj(Long::toString).collect(Collectors.joining(","));
    return List.of(
        "{\"ids\":[],\"format\":\"ris\"}",
        "{\"format\":\"ris\"}",
        "{\"ids\":[" + ids5001 + "],\"format\":\"ris\"}",
        "{\"ids\":[0],\"format\":\"ris\"}",
        "{\"ids\":[1],\"format\":\"docx\"}",
        "{\"ids\":[1]}",
        "{\"ids\":[1],\"format\":\"ris\",\"name\":\"" + "名".repeat(101) + "\"}");
  }

  // ===== POST /portal/publications/citations =====

  @Test
  @DisplayName("复制引用：返回样式代码与 label / text 条目，APA 的 label 为 null")
  void should_return_citation_entries() throws Exception {
    when(citationService.format(List.of(2L, 1L), CitationStyle.APA_7))
        .thenReturn(
            List.of(
                CitationEntry.of(null, "Anker, S. D. (2021)."),
                CitationEntry.of(null, "Wang, L. (2022).")));

    mockMvc
        .perform(
            post("/portal/publications/citations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"2\",1],\"style\":\"apa-7\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.style").value("apa-7"))
        .andExpect(jsonPath("$.entries.length()").value(2))
        .andExpect(jsonPath("$.entries[0].label").isEmpty())
        .andExpect(jsonPath("$.entries[0].text").value("Anker, S. D. (2021)."));
    verify(citationService).format(List.of(2L, 1L), CitationStyle.APA_7);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidCitationBodies")
  @DisplayName("复制引用：ids 空 / 超 100、style 未知或缺失 → 422，不触达服务")
  void should_reject_invalid_citation_requests_with_422(String body) throws Exception {
    mockMvc
        .perform(
            post("/portal/publications/citations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().is(422));

    verifyNoInteractions(citationService);
  }

  static List<String> invalidCitationBodies() {
    String ids101 =
        LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.joining(","));
    return List.of(
        "{\"ids\":[],\"style\":\"apa-7\"}",
        "{\"ids\":[" + ids101 + "],\"style\":\"apa-7\"}",
        "{\"ids\":[1],\"style\":\"mla\"}",
        "{\"ids\":[1]}");
  }

  @Test
  @DisplayName("全部不存在：entries 为空数组")
  void should_return_empty_entries_when_all_missing() throws Exception {
    when(citationService.format(List.of(9L), CitationStyle.VANCOUVER)).thenReturn(List.of());

    MvcResult result =
        mockMvc
            .perform(
                post("/portal/publications/citations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ids\":[9],\"style\":\"vancouver\"}"))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getContentAsString()).contains("\"entries\":[]");
  }
}
