package dev.patra.identity.adapter.session.rest;

import static org.mockito.Mockito.verify;

import dev.patra.identity.app.session.usecase.logout.LogoutUserCommand;
import dev.patra.tool.cqrs.CommandBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// LogoutController 切片测试：登出请求怎么转成命令。
@WebMvcTest
@Import(LogoutController.class)
@AutoConfigureRestTestClient
@DisplayName("LogoutController 切片测试")
class LogoutControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  @Test
  @DisplayName("登出返回 204，没有响应体；命令没有字段")
  void should_logout_and_return_204() {
    restClient.post().uri("/auth/logout").exchange().expectStatus().isNoContent();

    verify(commandBus).handle(LogoutUserCommand.of());
  }
}
