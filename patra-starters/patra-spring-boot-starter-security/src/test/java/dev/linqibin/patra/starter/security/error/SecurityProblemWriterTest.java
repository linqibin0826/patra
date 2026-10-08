package dev.linqibin.patra.starter.security.error;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.starter.security.authentication.MalformedIdentityException;
import dev.linqibin.starter.core.error.config.ErrorProperties;
import dev.linqibin.starter.core.error.engine.DefaultErrorResolutionEngine;
import dev.linqibin.starter.core.error.pipeline.ErrorResolutionPipeline;
import dev.linqibin.starter.web.error.adapter.DefaultProblemDetailAdapter;
import dev.linqibin.starter.web.error.adapter.ProblemDetailAdapter;
import dev.linqibin.starter.web.error.builder.ProblemDetailBuilder;
import dev.linqibin.starter.web.error.config.WebErrorProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/// SecurityProblemWriter 单元测试。
///
/// 不起 Spring，用真实的错误解析引擎和 ProblemDetailBuilder 组装适配器。
@DisplayName("SecurityProblemWriter 单元测试")
class SecurityProblemWriterTest {

  private final JsonMapper jsonMapper =
      JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();

  private SecurityProblemWriter writer;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    ErrorProperties errorProperties = new ErrorProperties();
    errorProperties.setContextPrefix("TEST");
    DefaultErrorResolutionEngine engine =
        new DefaultErrorResolutionEngine(
            errorProperties,
            List.of(new SecurityErrorMappingContributor(HttpStdErrors.of("TEST"))));
    ProblemDetailBuilder builder =
        new ProblemDetailBuilder(
            errorProperties, new WebErrorProperties(), Optional::empty, List.of(), List.of());
    ProblemDetailAdapter adapter =
        new DefaultProblemDetailAdapter(new ErrorResolutionPipeline(engine, List.of()), builder);
    writer = new SecurityProblemWriter(adapter, jsonMapper);
    request = new MockHttpServletRequest("GET", "/probe/me");
    response = new MockHttpServletResponse();
  }

  private Map<String, Object> body() {
    return jsonMapper.readValue(
        response.getContentAsByteArray(), new TypeReference<Map<String, Object>>() {});
  }

  private String rawBody() {
    return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("未登录：401、Bearer 质询头、ProblemDetail，detail 是固定短句")
  void should_write_401_problem_when_commence() throws Exception {
    writer.commence(
        request,
        response,
        new InsufficientAuthenticationException("Full authentication is required"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(body())
        .containsEntry("status", 401)
        .containsEntry("code", "TEST-0401")
        .containsEntry("title", "TEST-0401")
        .containsEntry("detail", "Authentication required")
        .containsEntry("instance", "/probe/me")
        .containsEntry("path", "/probe/me")
        .containsKeys("type", "timestamp");
  }

  @Test
  @DisplayName("拒绝访问：403，没有质询头，不泄露异常的原始消息")
  void should_write_403_problem_when_access_denied() throws Exception {
    writer.handle(request, response, new AccessDeniedException("probe internal reason"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    assertThat(body())
        .containsEntry("status", 403)
        .containsEntry("code", "TEST-0403")
        .containsEntry("detail", "Access denied")
        .containsEntry("instance", "/probe/me");
    assertThat(rawBody()).doesNotContain("probe internal reason");
  }

  @Test
  @DisplayName("身份头不合法：500，detail 是 HTTP 状态的标准短语，不泄露头名")
  void should_write_500_problem_when_identity_malformed() throws Exception {
    writer.onAuthenticationFailure(request, response, new MalformedIdentityException("sub 不是正整数"));

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(body())
        .containsEntry("status", 500)
        .containsEntry("code", "TEST-0500")
        .containsEntry("detail", "Internal Server Error")
        .containsEntry("instance", "/probe/me");
    assertThat(rawBody()).doesNotContain("sub 不是正整数");
  }

  @Test
  @DisplayName("认证服务不可用：503")
  void should_write_503_problem_when_authentication_service_unavailable() throws Exception {
    writer.onAuthenticationFailure(
        request, response, new AuthenticationServiceException("session store down"));

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(body())
        .containsEntry("status", 503)
        .containsEntry("code", "TEST-0503")
        .containsEntry("detail", "Service Unavailable");
    assertThat(rawBody()).doesNotContain("session store down");
  }
}
