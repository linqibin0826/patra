package dev.patra.identity.adapter.session.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.session.usecase.query.SessionQueryService;
import dev.patra.identity.app.session.usecase.query.SessionView;
import dev.patra.identity.app.session.usecase.revoke.RevokeOtherSessionsCommand;
import dev.patra.identity.app.session.usecase.revoke.RevokeSessionCommand;
import dev.patra.identity.app.session.usecase.revoke.SessionsRevoked;
import dev.patra.identity.domain.session.exception.CurrentSessionRevocationException;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/// MeSessionController 切片测试：登录设备列表与踢设备的 HTTP 形状（PAP-99 设计第 11 节）。
@WebMvcTest
@Import(MeSessionController.class)
@AutoConfigureRestTestClient
@DisplayName("MeSessionController 切片测试")
class MeSessionControllerIT {

  @Autowired private RestTestClient restClient;

  @MockitoBean private CommandBus commandBus;

  @MockitoBean private SessionQueryService sessionQueryService;

  @Test
  @DisplayName("列表：字段名与设计一致，id 是字符串，空值原样为 null")
  void should_list_sessions() {
    when(sessionQueryService.list())
        .thenReturn(
            List.of(
                SessionView.builder()
                    .id(723511223344556677L)
                    .current(true)
                    .deviceName("Chrome 141 · macOS")
                    .deviceClass(DeviceClass.DESKTOP)
                    .ipAddress("113.92.157.29")
                    .ipRegion("中国 广东 深圳")
                    .privateNetwork(false)
                    .signedInAt(Instant.parse("2026-10-20T01:12:00Z"))
                    .lastActiveAt(Instant.parse("2026-10-22T08:03:00Z"))
                    .build(),
                SessionView.builder()
                    .id(723511223344556600L)
                    .current(false)
                    .deviceClass(DeviceClass.UNKNOWN)
                    .ipAddress("100.70.1.1")
                    .privateNetwork(true)
                    .signedInAt(Instant.parse("2026-10-18T13:40:00Z"))
                    .lastActiveAt(Instant.parse("2026-10-21T08:03:00Z"))
                    .build()));

    restClient
        .get()
        .uri("/me/sessions")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.sessions[0].id")
        .isEqualTo("723511223344556677")
        .jsonPath("$.sessions[0].current")
        .isEqualTo(true)
        .jsonPath("$.sessions[0].deviceName")
        .isEqualTo("Chrome 141 · macOS")
        .jsonPath("$.sessions[0].deviceClass")
        .isEqualTo("DESKTOP")
        .jsonPath("$.sessions[0].ipAddress")
        .isEqualTo("113.92.157.29")
        .jsonPath("$.sessions[0].ipRegion")
        .isEqualTo("中国 广东 深圳")
        .jsonPath("$.sessions[0].privateNetwork")
        .isEqualTo(false)
        .jsonPath("$.sessions[0].signedInAt")
        .isEqualTo("2026-10-20T01:12:00Z")
        .jsonPath("$.sessions[0].lastActiveAt")
        .isEqualTo("2026-10-22T08:03:00Z")
        .jsonPath("$.sessions[1].deviceName")
        .doesNotExist()
        .jsonPath("$.sessions[1].privateNetwork")
        .isEqualTo(true);
  }

  @Test
  @DisplayName("踢单个：会话 ID 交给命令，回撤销数")
  void should_revoke_one_session() {
    when(commandBus.handle(any(RevokeSessionCommand.class))).thenReturn(SessionsRevoked.of(1));

    restClient
        .delete()
        .uri("/me/sessions/723511223344556600")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.revokedSessions")
        .isEqualTo(1);

    ArgumentCaptor<RevokeSessionCommand> command =
        ArgumentCaptor.forClass(RevokeSessionCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().sessionId()).isEqualTo(723511223344556600L);
  }

  @Test
  @DisplayName("踢其他全部：走另一个命令，回撤销数")
  void should_revoke_other_sessions() {
    when(commandBus.handle(any(RevokeOtherSessionsCommand.class)))
        .thenReturn(SessionsRevoked.of(3));

    restClient
        .delete()
        .uri("/me/sessions/others")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.revokedSessions")
        .isEqualTo(3);
  }

  @Test
  @DisplayName("踢本机：409，traits 含 CURRENT_SESSION")
  void should_answer_409_for_current_session() {
    when(commandBus.handle(any(RevokeSessionCommand.class)))
        .thenThrow(new CurrentSessionRevocationException());

    restClient
        .delete()
        .uri("/me/sessions/1")
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("IDN-0409")
        .jsonPath("$.traits[?(@ == 'CURRENT_SESSION')]")
        .exists();
  }

  @Test
  @DisplayName("会话 ID 不是数字：400，不调命令")
  void should_reject_non_numeric_session_id() {
    restClient.delete().uri("/me/sessions/abc").exchange().expectStatus().isBadRequest();

    verifyNoInteractions(commandBus);
  }
}
