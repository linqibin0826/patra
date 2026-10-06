package dev.linqibin.patra.identity.adapter.rest.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.identity.app.usecase.ban.BanUserCommand;
import dev.linqibin.patra.identity.app.usecase.ban.UnbanUserCommand;
import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// AdminUserController 切片测试。
@WebMvcTest
@Import(AdminUserController.class)
@AutoConfigureRestTestClient
@DisplayName("AdminUserController 切片测试")
class AdminUserControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  @Test
  @DisplayName("封禁返回 204")
  void should_ban_and_return_204() {
    restClient.post().uri("/admin/users/42/ban").exchange().expectStatus().isNoContent();

    verify(commandBus).handle(BanUserCommand.of(42L));
  }

  @Test
  @DisplayName("解封返回 204")
  void should_unban_and_return_204() {
    restClient.post().uri("/admin/users/42/unban").exchange().expectStatus().isNoContent();

    verify(commandBus).handle(UnbanUserCommand.of(42L));
  }

  @Test
  @DisplayName("用户不存在返回 404")
  void should_render_not_found() {
    when(commandBus.handle(any(BanUserCommand.class))).thenThrow(new UserNotFoundException());

    restClient
        .post()
        .uri("/admin/users/404/ban")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0404")
        .jsonPath("$.detail")
        .isEqualTo("用户不存在");
  }
}
