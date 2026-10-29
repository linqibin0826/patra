package dev.patra.catalog.adapter.fulltext.rest;

import dev.patra.catalog.app.fulltext.usecase.query.PortalFullTextFileQueryService;
import dev.patra.catalog.domain.fulltext.exception.FullTextNotAvailableException;
import dev.patra.catalog.domain.fulltext.model.read.FullTextFileRef;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFile;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/// 门户全文的二进制下发：PDF 与正文图片（工程 spec 第 7.4、7.5 节）。
///
/// 用 `InputStreamResource` 同步写回（边读边写、写完关流），不用 `StreamingResponseBody`：
/// 后者走异步分派，受 Tomcat 默认 30 秒异步超时限制。**返回响应前完成全部判定并打开对象**——
/// 首字节写出后再出错已无法返回 404。
///
/// @author linqibin
/// @since 0.11.0
@Validated
@RestController
@RequestMapping("/portal/publications")
@RequiredArgsConstructor
public class PortalFullTextFileController {

  private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

  private static final CacheControl PDF_CACHE =
      CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

  private static final CacheControl FIGURE_CACHE =
      CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable();

  private final PortalFullTextFileQueryService queryService;

  /// 下载 PDF（附件，文件名 `{PMCID}.pdf`）。
  ///
  /// @param id 文献 ID
  /// @param request 用于条件请求
  /// @return PDF，ETag 命中时为 304
  @GetMapping("/{id}/fulltext/pdf")
  public ResponseEntity<Resource> getPdf(@PathVariable @Positive long id, WebRequest request) {
    FullTextFileRef ref = queryService.getPdf(id);
    if (notModified(ref, request)) {
      return null;
    }
    return respond(
        ref,
        MediaType.APPLICATION_PDF,
        PDF_CACHE,
        ContentDisposition.attachment().filename(ref.fileName()).build());
  }

  /// 取正文图片（门户经同源 Route Handler 转发）。
  ///
  /// 文件名只允许 `[A-Za-z0-9._-]+`，在方法里手工校验返回 404：控制器开了 `@Validated`，
  /// 路径变量上的约束注解会在进方法前变成 422。
  ///
  /// @param id 文献 ID
  /// @param fileName 文件名
  /// @param request 用于条件请求
  /// @return 图片，ETag 命中时为 304
  @GetMapping("/{id}/fulltext/figures/{fileName}")
  public ResponseEntity<Resource> getFigure(
      @PathVariable @Positive long id, @PathVariable String fileName, WebRequest request) {
    if (!FILE_NAME.matcher(fileName).matches()) {
      throw new FullTextNotAvailableException(id);
    }
    FullTextFileRef ref = queryService.getFigure(id, fileName);
    if (notModified(ref, request)) {
      return null;
    }
    return respond(ref, MediaType.parseMediaType(ref.contentType()), FIGURE_CACHE, null);
  }

  /// 打开对象后组装响应（对象不存在时 `open` 抛 NOT_FOUND，仍在写出之前）。
  private ResponseEntity<Resource> respond(
      FullTextFileRef ref, MediaType type, CacheControl cache, ContentDisposition disposition) {
    StoredFile file = queryService.open(ref);
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.ok()
            .contentType(type)
            .contentLength(file.info() != null ? file.info().size() : ref.size())
            .cacheControl(cache);
    if (ref.etag() != null) {
      builder.eTag(quoted(ref.etag()));
    }
    if (disposition != null) {
      builder.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
    }
    return builder.body(new InputStreamResource(file.content()));
  }

  /// 条件请求：有 ETag 时按 `If-None-Match` 判断（命中返回 304）；PMC 没给 md5 时不做条件请求。
  private static boolean notModified(FullTextFileRef ref, WebRequest request) {
    return ref.etag() != null && request.checkNotModified(quoted(ref.etag()));
  }

  /// ETag 加引号（存储与数据库里都不带引号）。
  private static String quoted(String etag) {
    return etag.startsWith("\"") ? etag : "\"" + etag + "\"";
  }
}
