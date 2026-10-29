package dev.patra.identity.adapter.session.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.patra.identity.app.session.usecase.query.SessionIntrospectionService;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// SessionEndpointImpl 切片测试：查会话的 JSON 形状（PAP-106 设计第 4 节）。
@WebMvcTest
@Import(SessionEndpointImpl.class)
@AutoConfigureRestTestClient
@DisplayName("SessionEndpointImpl 切片测试")
class SessionEndpointImplIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private RestTestClient restClient;

  @MockitoBean private SessionIntrospectionService sessionIntrospectionService;

  @Test
  @DisplayName("会话在：active 为 true，用户 ID 与会话 ID 是字符串，带账号类型与客户端类型")
  void should_return_active_session() {
    when(sessionIntrospectionService.introspect("patra_user_live"))
        .thenReturn(Optional.of(CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB)));

    JsonNode json = introspect("patra_user_live");

    assertThat(json.get("active").asBoolean()).isTrue();
    assertThat(json.get("userId").isString()).isTrue();
    assertThat(json.get("userId").asString()).isEqualTo("42");
    assertThat(json.get("sessionId").asString()).isEqualTo("7001");
    assertThat(json.get("accountType").asString()).isEqualTo("USER");
    assertThat(json.get("clientType").asString()).isEqualTo("WEB");
  }

  @Test
  @DisplayName("会话不在：200，active 为 false，没有用户字段")
  void should_return_inactive_when_session_is_absent() {
    when(sessionIntrospectionService.introspect("patra_user_gone")).thenReturn(Optional.empty());

    JsonNode json = introspect("patra_user_gone");

    assertThat(json.get("active").asBoolean()).isFalse();
    assertThat(json.path("userId").isMissingNode() || json.get("userId").isNull()).isTrue();
  }

  private JsonNode introspect(String token) {
    String body =
        restClient
            .post()
            .uri("/_internal/sessions/introspect")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("token", token))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    return JSON.readTree(body);
  }
}
