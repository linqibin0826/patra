package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.app.account.usecase.query.AvatarQueryService;
import dev.patra.identity.app.account.usecase.query.AvatarRef;
import dev.patra.identity.domain.account.model.vo.avatar.StoredAvatar;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/// 公开的头像读取，网关上 `/users/**` 公开。地址带版本，内容永远不变，所以按不可变长期缓存下发；
/// 版本、尺寸、用户 ID 任一对不上一律 404（不用 Bean Validation：`@Validated` 下会变 422）。
///
/// 返回 `InputStreamResource` 而不是 `StreamingResponseBody`：后者走异步分派，有 Tomcat 30 秒超时，
/// 所有检查都在写响应之前做完，404 照样能回（同 catalog 的全文文件下发）。
@Tag(name = "Users", description = "公开头像")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class PublicAvatarController {

  private static final MediaType WEBP = MediaType.parseMediaType("image/webp");

  /// 一年、公开、不可变。
  private static final String IMMUTABLE = "public, max-age=31536000, immutable";

  private final AvatarQueryService avatarQueryService;

  /// 读头像。
  ///
  /// @param userId 路径里的用户 ID
  /// @param size `512` 或 `128`
  /// @param version 当前头像的哈希
  /// @param request 用于条件请求
  /// @return 200 WebP 字节流；`If-None-Match` 命中时 304
  @GetMapping("/{userId}/avatar")
  public ResponseEntity<Resource> avatar(
      @PathVariable String userId,
      @RequestParam(required = false) String size,
      @RequestParam(name = "v", required = false) String version,
      WebRequest request) {
    AvatarRef ref = avatarQueryService.locate(userId, size, version);
    if (request.checkNotModified(ref.etag())) {
      return null;
    }
    StoredAvatar stored = avatarQueryService.open(ref);
    return ResponseEntity.ok()
        .contentType(WEBP)
        .contentLength(stored.length())
        .eTag(ref.etag())
        .header(HttpHeaders.CACHE_CONTROL, IMMUTABLE)
        .body(new InputStreamResource(stored.content()));
  }
}
