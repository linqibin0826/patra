package dev.patra.catalog.adapter.portal.rest;

import dev.patra.catalog.adapter.portal.rest.request.PortalCitationRequest;
import dev.patra.catalog.adapter.portal.rest.request.PortalPublicationExportRequest;
import dev.patra.catalog.adapter.portal.rest.response.PortalCitationResponse;
import dev.patra.catalog.app.portal.usecase.query.PortalCitationQueryService;
import dev.patra.catalog.app.portal.usecase.query.PortalPublicationExportService;
import dev.patra.catalog.domain.portal.model.enums.CitationStyle;
import dev.patra.catalog.domain.portal.model.enums.ExportFormat;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/// Portal 引用导出与复制引用接口（spec 第 6、7 节）。
///
/// @author linqibin
/// @since 0.1.0
@RestController
@RequestMapping("/portal/publications")
@RequiredArgsConstructor
@Tag(name = "Portal Citation", description = "门户引用导出与复制引用")
public class PortalCitationController {

  /// 导出文件名里的日期按北京时间取当天。
  private static final ZoneId FILE_DATE_ZONE = ZoneId.of("Asia/Shanghai");

  private final PortalPublicationExportService exportService;
  private final PortalCitationQueryService citationService;
  private final Clock clock;

  /// 导出：流式写出文件，不存在与软删的文献跳过。
  ///
  /// @param request 导出请求
  /// @return 附件响应，正文在响应线程里逐批写出
  @PostMapping("/export")
  public ResponseEntity<StreamingResponseBody> export(
      @Valid @RequestBody PortalPublicationExportRequest request) {
    ExportFormat format = ExportFormat.fromCode(request.format());
    String fileName =
        ExportFileNames.of(request.name(), format, LocalDate.now(clock.withZone(FILE_DATE_ZONE)));
    StreamingResponseBody body = out -> exportService.export(request.ids(), format, out);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentType(MediaType.parseMediaType(format.contentType()))
        .body(body);
  }

  /// 复制引用。
  ///
  /// @param request 引用请求
  /// @return 样式代码与引用条目
  @PostMapping("/citations")
  public PortalCitationResponse citations(@Valid @RequestBody PortalCitationRequest request) {
    CitationStyle style = CitationStyle.fromCode(request.style());
    return PortalCitationResponse.of(
        style.code(),
        citationService.format(request.ids(), style).stream()
            .map(e -> PortalCitationResponse.Entry.of(e.label(), e.text()))
            .toList());
  }
}
