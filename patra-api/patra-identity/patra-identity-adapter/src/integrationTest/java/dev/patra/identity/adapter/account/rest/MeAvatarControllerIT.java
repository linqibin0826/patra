package dev.patra.identity.adapter.account.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.patra.identity.app.account.usecase.avatar.ChangeAvatarCommand;
import dev.patra.identity.app.account.usecase.avatar.RemoveAvatarCommand;
import dev.patra.identity.domain.account.exception.InvalidAvatarException;
import dev.patra.identity.domain.account.exception.UserPendingDeletionException;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRejection;
import dev.patra.tool.cqrs.CommandBus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// MeAvatarController 切片测试：上传的映射、响应与错误，删除的状态码。
@WebMvcTest
@Import(MeAvatarController.class)
@AutoConfigureRestTestClient
class MeAvatarControllerIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final byte[] FILE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2};

  @Autowired private RestTestClient restClient;
  @Autowired private MockMvc mockMvc;

  @MockitoBean private CommandBus commandBus;

  @Test
  void should_upload_and_return_avatar_url() {
    when(commandBus.handle(any(ChangeAvatarCommand.class)))
        .thenReturn(Avatar.of(42L, "0123456789abcdef"));

    JsonNode json = upload(FILE, 200);

    assertThat(json.get("avatarUrl").asString()).isEqualTo("/users/42/avatar?v=0123456789abcdef");
    ArgumentCaptor<ChangeAvatarCommand> command =
        ArgumentCaptor.forClass(ChangeAvatarCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().content()).containsExactly(FILE);
  }

  @Test
  void should_pass_empty_content_when_file_part_missing() {
    when(commandBus.handle(any(ChangeAvatarCommand.class)))
        .thenThrow(new InvalidAvatarException(AvatarRejection.REQUIRED));
    JsonNode json =
        post(
            multipart("/me/avatar")
                .file(new MockMultipartFile("other", "x.txt", "text/plain", new byte[] {1})),
            422);

    assertThat(json.get("errors").get(0).get("field").asString()).isEqualTo("file");
    assertThat(json.get("errors").get(0).get("code").asString()).isEqualTo("REQUIRED");
    ArgumentCaptor<ChangeAvatarCommand> command =
        ArgumentCaptor.forClass(ChangeAvatarCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue().content()).isEmpty();
  }

  @Test
  void should_return_422_with_rejection_code() {
    when(commandBus.handle(any(ChangeAvatarCommand.class)))
        .thenThrow(new InvalidAvatarException(AvatarRejection.TOO_SMALL));

    JsonNode json = upload(FILE, 422);

    assertThat(json.get("code").asString()).isEqualTo("IDN-0422");
    assertThat(json.get("errors").get(0).get("code").asString()).isEqualTo("TOO_SMALL");
  }

  @Test
  void should_return_409_when_pending_deletion() {
    when(commandBus.handle(any(ChangeAvatarCommand.class)))
        .thenThrow(new UserPendingDeletionException());

    JsonNode json = upload(FILE, 409);

    assertThat(json.get("traits").toString()).contains("PENDING_DELETION");
  }

  @Test
  void should_remove_with_204() {
    restClient.delete().uri("/me/avatar").exchange().expectStatus().isNoContent();

    verify(commandBus).handle(RemoveAvatarCommand.of());
  }

  /// 以 `file` part 上传。RestTestClient 走 MockMvc 时不拆 multipart，上传用 MockMvc 直接发。
  ///
  /// @param bytes 文件字节
  /// @param status 期望状态码
  /// @return 响应体
  private JsonNode upload(byte[] bytes, int status) {
    return post(
        multipart("/me/avatar").file(new MockMultipartFile("file", "a.jpg", "image/jpeg", bytes)),
        status);
  }

  /// 发 multipart POST。
  ///
  /// @param request 请求
  /// @param status 期望状态码
  /// @return 响应体
  private JsonNode post(MockMultipartHttpServletRequestBuilder request, int status) {
    try {
      String response =
          mockMvc
              .perform(request)
              .andExpect(status().is(status))
              .andReturn()
              .getResponse()
              .getContentAsString(StandardCharsets.UTF_8);
      return JSON.readTree(response.isEmpty() ? "{}" : response);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
