package dev.linqibin.starter.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.starter.web.error.probe.ValidationProbeController;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 参数校验失败时，响应和日志里都不能出现字段的原始值。
@WebMvcTest(controllers = ValidationProbeController.class)
@Import(ValidationProbeController.class)
@AutoConfigureRestTestClient
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("参数校验失败时不泄露字段原始值")
class ValidationFailureLeakIT {

  private static final String SECRET = "Leaky-Secret-Value-20261006";

  @Autowired private RestTestClient restClient;

  @Test
  @DisplayName("响应和日志里都没有被拒绝的原始值")
  void should_not_leak_rejected_value_into_response_or_log(CapturedOutput output) {
    String body =
        restClient
            .post()
            .uri("/probe/validation")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("email", "", "password", SECRET))
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    JsonNode json = JsonMapper.builder().build().readTree(body);
    List<String> fieldCodes = new ArrayList<>();
    json.get("errors")
        .forEach(
            error ->
                fieldCodes.add(error.get("field").asString() + ":" + error.get("code").asString()));
    assertThat(json.get("detail").asString()).isEqualTo("请求参数不合法");
    assertThat(fieldCodes).containsExactlyInAnyOrder("email:NOT_BLANK", "password:SIZE");
    assertThat(body).doesNotContain(SECRET);
    assertThat(output.getAll()).contains("参数校验失败").doesNotContain(SECRET);
  }
}
