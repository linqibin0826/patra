package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.adapter.account.rest.response.MovedUserResponse;
import dev.patra.identity.adapter.account.rest.response.PublicUserResponse;
import dev.patra.identity.app.account.usecase.query.UserQueryService;
import dev.patra.identity.domain.account.model.read.PublicUserLookup;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 公开身份卡：按用户名读，网关上对匿名开放。找到 200；保留期内的旧名 301 到当前用户名，
/// `Location` 是 identity 自己的相对路径，JSON 体里的 `username` 才是门户要用的；不存在或不可见 404。
/// 301 带 `Cache-Control: no-store`：跳转最多 90 天、原主 30 天后就可能改回，不能让中间缓存记住它。
@Tag(name = "Users", description = "公开身份卡")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class PublicUserController {

  private final UserQueryService userQueryService;

  /// 按用户名读公开身份卡。
  ///
  /// @param username 路径里的用户名，大小写不敏感
  /// @return 200 身份卡，或 301 带新名（不缓存）
  @GetMapping("/{username}")
  public ResponseEntity<Object> byUsername(@PathVariable String username) {
    return switch (userQueryService.publicIdentity(username)) {
      case PublicUserLookup.Found found -> ResponseEntity.ok(PublicUserResponse.from(found.view()));
      case PublicUserLookup.Moved moved ->
          ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
              .location(URI.create("/users/" + moved.current().value()))
              .cacheControl(CacheControl.noStore())
              .body(MovedUserResponse.of(moved.current().value()));
    };
  }
}
