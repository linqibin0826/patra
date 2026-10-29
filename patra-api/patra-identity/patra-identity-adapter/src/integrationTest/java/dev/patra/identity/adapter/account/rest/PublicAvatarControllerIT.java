package dev.patra.identity.adapter.account.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.account.usecase.query.AvatarQueryService;
import dev.patra.identity.app.account.usecase.query.AvatarRef;
import dev.patra.identity.domain.account.exception.AvatarNotFoundException;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarSize;
import dev.patra.identity.domain.account.model.vo.avatar.StoredAvatar;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/// PublicAvatarController 切片测试：响应头、条件请求、404。
@WebMvcTest
@Import(PublicAvatarController.class)
@AutoConfigureRestTestClient
class PublicAvatarControllerIT {

  private static final Avatar AVATAR = Avatar.of(42L, "0123456789abcdef");
  private static final AvatarRef REF = AvatarRef.of(AVATAR, AvatarSize.SMALL);
  private static final String URI = "/users/42/avatar?size=128&v=0123456789abcdef";

  @Autowired private RestTestClient restClient;

  @MockitoBean private AvatarQueryService avatarQueryService;

  /// 默认：命中，对象是三个字节。
  @BeforeEach
  void stubHit() {
    when(avatarQueryService.locate("42", "128", "0123456789abcdef")).thenReturn(REF);
    when(avatarQueryService.open(REF))
        .thenAnswer(
            invocation -> StoredAvatar.of(new ByteArrayInputStream(new byte[] {7, 8, 9}), 3));
  }

  @Test
  void should_stream_with_immutable_cache_headers() {
    EntityExchangeResult<byte[]> result =
        restClient
            .get()
            .uri(URI)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(byte[].class)
            .returnResult();

    HttpHeaders headers = result.getResponseHeaders();
    assertThat(headers.getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("image/webp");
    assertThat(headers.getContentLength()).isEqualTo(3);
    assertThat(headers.getETag()).isEqualTo("\"0123456789abcdef-128\"");
    assertThat(headers.getFirst(HttpHeaders.CACHE_CONTROL))
        .isEqualTo("public, max-age=31536000, immutable");
    assertThat(result.getResponseBody()).containsExactly(7, 8, 9);
  }

  @Test
  void should_answer_304_without_opening_object() {
    restClient
        .get()
        .uri(URI)
        .header(HttpHeaders.IF_NONE_MATCH, "\"0123456789abcdef-128\"")
        .exchange()
        .expectStatus()
        .isNotModified();

    verify(avatarQueryService, never()).open(any());
  }

  @Test
  void should_404_when_not_located() {
    when(avatarQueryService.locate("42", "abc", "0123456789abcdef"))
        .thenThrow(new AvatarNotFoundException());

    restClient
        .get()
        .uri("/users/42/avatar?size=abc&v=0123456789abcdef")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void should_404_for_username_path() {
    when(avatarQueryService.locate("chen-yu", "128", "0123456789abcdef"))
        .thenThrow(new AvatarNotFoundException());

    restClient
        .get()
        .uri("/users/chen-yu/avatar?size=128&v=0123456789abcdef")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void should_404_when_object_missing() {
    when(avatarQueryService.open(REF)).thenThrow(new AvatarNotFoundException());

    restClient.get().uri(URI).exchange().expectStatus().isNotFound();
  }
}
