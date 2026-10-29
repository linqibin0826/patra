package dev.patra.catalog.adapter.fulltext.rest;

import dev.patra.catalog.adapter.fulltext.rest.converter.PortalFullTextApiConverter;
import dev.patra.catalog.adapter.fulltext.rest.response.PortalFullTextResponse;
import dev.patra.catalog.app.fulltext.usecase.query.PortalPublicationFullTextQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// Portal C 端门户文献全文控制器（正文结构、许可与署名）。
///
/// @author linqibin
/// @since 0.11.0
@Tag(name = "Portal", description = "Portal C 端门户专用接口")
@Validated
@RestController
@RequestMapping("/portal/publications")
@RequiredArgsConstructor
public class PortalFullTextController {

  private final PortalPublicationFullTextQueryService portalPublicationFullTextQueryService;
  private final PortalFullTextApiConverter portalFullTextApiConverter;

  /// 查询文献全文（正文结构、许可与署名）。只有 PDF 可下载时返回 200、正文为空；都不满足时 404。
  ///
  /// @param id 文献 ID（必须为正整数）
  /// @return 全文
  @GetMapping("/{id}/fulltext")
  public PortalFullTextResponse getFullText(@PathVariable @Positive long id) {
    return portalFullTextApiConverter.toFullTextResponse(
        portalPublicationFullTextQueryService.getForPortal(id));
  }
}
