package dev.patra.identity.infra.account.verification;

import dev.patra.identity.domain.account.model.enums.HumanVerificationAction;
import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import dev.patra.identity.domain.account.model.enums.HumanVerificationResult;
import dev.patra.identity.domain.account.port.verification.HumanVerificationPort;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Cloudflare Turnstile 校验。
///
/// `success` 为真之外，还要 `hostname` 在允许名单里、`action` 和这次操作一致，防止别的页面或操作拿到的令牌被挪用。
/// 超时、网络错误、5xx、`internal-error` 用同一个 `idempotency_key` 重试一次：令牌只能用一次，不带这个键，
/// 第二次校验会被判为重复。
///
/// 官方测试密钥的响应带 `metadata.result_with_testing_key`，`hostname` 固定、没有 `action`，所以带这个标记时跳过这两项
/// 核对。只有配置了测试密钥才会收到它，线上不配测试密钥。
@Slf4j
public final class TurnstileAdapter implements HumanVerificationPort {

  /// 官方文档规定的令牌长度上限。
  private static final int MAX_TOKEN_LENGTH = 2048;

  private static final String PATH = "/turnstile/v0/siteverify";

  private static final Set<String> TOKEN_REJECTED_CODES =
      Set.of("timeout-or-duplicate", "invalid-input-response");
  private static final Set<String> SECRET_ERROR_CODES =
      Set.of("invalid-input-secret", "missing-input-secret");
  private static final String RETRYABLE_CODE = "internal-error";

  private final RestClient client;
  private final JsonMapper jsonMapper;
  private final Supplier<TurnstileSettings> settings;

  /// 创建适配器。
  ///
  /// @param client 出站客户端，带超时
  /// @param jsonMapper JSON 解析
  /// @param settings 配置来源
  public TurnstileAdapter(
      RestClient client, JsonMapper jsonMapper, Supplier<TurnstileSettings> settings) {
    this.client = Objects.requireNonNull(client, "client 不能为 null");
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper 不能为 null");
    this.settings = Objects.requireNonNull(settings, "settings 不能为 null");
  }

  /// 这个实现对应的厂商。
  ///
  /// @return Turnstile
  @Override
  public HumanVerificationProvider provider() {
    return HumanVerificationProvider.TURNSTILE;
  }

  /// 核对令牌。
  ///
  /// @param token 浏览器提交的令牌
  /// @param action 这次操作
  /// @return 结果
  @Override
  public HumanVerificationResult verify(String token, HumanVerificationAction action) {
    TurnstileSettings current = settings.get();
    if (current.secret() == null || current.secret().isBlank()) {
      log.error("Turnstile 的密钥没配，人机验证不可用");
      return HumanVerificationResult.UNAVAILABLE;
    }
    if (!VerificationTokenRules.isSendable(token, MAX_TOKEN_LENGTH)) {
      return HumanVerificationResult.REJECTED;
    }
    String idempotencyKey = UUID.randomUUID().toString();
    return attempt(current, token, action, idempotencyKey)
        .or(() -> attempt(current, token, action, idempotencyKey))
        .orElse(HumanVerificationResult.UNAVAILABLE);
  }

  /// 调一次校验接口。
  ///
  /// @param current 配置
  /// @param token 令牌
  /// @param action 这次操作
  /// @param idempotencyKey 幂等键
  /// @return 结果；可以重试时为空
  private Optional<HumanVerificationResult> attempt(
      TurnstileSettings current,
      String token,
      HumanVerificationAction action,
      String idempotencyKey) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("secret", current.secret());
    form.add("response", token);
    form.add("idempotency_key", idempotencyKey);
    Reply reply;
    try {
      reply =
          client
              .post()
              .uri(current.baseUrl() + PATH)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .exchange(
                  (request, response) ->
                      new Reply(
                          response.getStatusCode(),
                          new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    } catch (RestClientException e) {
      log.warn("调 Turnstile 校验接口失败: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
    if (reply == null || reply.status().is5xxServerError()) {
      log.warn("Turnstile 校验接口返回 5xx");
      return Optional.empty();
    }
    return classify(reply.body(), current, action);
  }

  /// 解读响应。
  ///
  /// @param body 响应体
  /// @param current 配置
  /// @param action 这次操作
  /// @return 结果；可以重试时为空
  private Optional<HumanVerificationResult> classify(
      String body, TurnstileSettings current, HumanVerificationAction action) {
    JsonNode root;
    try {
      root = jsonMapper.readTree(body);
    } catch (JacksonException e) {
      log.warn("Turnstile 校验接口返回的不是 JSON");
      return Optional.of(HumanVerificationResult.UNAVAILABLE);
    }
    JsonNode success = root.path("success");
    if (!success.isBoolean()) {
      log.warn("Turnstile 校验接口的响应缺少布尔的 success");
      return Optional.of(HumanVerificationResult.UNAVAILABLE);
    }
    if (success.asBoolean()) {
      return Optional.of(classifySuccess(root, current, action));
    }
    return classifyFailure(root);
  }

  /// 解读 `success` 为真的响应：核对 `hostname` 和 `action`。
  ///
  /// @param root 响应
  /// @param current 配置
  /// @param action 这次操作
  /// @return 结果
  private static HumanVerificationResult classifySuccess(
      JsonNode root, TurnstileSettings current, HumanVerificationAction action) {
    if (root.path("metadata").path("result_with_testing_key").asBoolean(false)) {
      log.warn("Turnstile 正在使用官方测试密钥，线上不应出现这条日志");
      return HumanVerificationResult.PASSED;
    }
    boolean hostnameMatches =
        current.allowedHostnames().contains(root.path("hostname").asString(""));
    boolean actionMatches = action.wireValue().equals(root.path("action").asString(""));
    if (hostnameMatches && actionMatches) {
      return HumanVerificationResult.PASSED;
    }
    log.warn(
        "Turnstile 令牌对不上这次操作: action={}, hostnameMatches={}, actionMatches={}",
        action.wireValue(),
        hostnameMatches,
        actionMatches);
    return HumanVerificationResult.REJECTED;
  }

  /// 解读 `success` 为假的响应：按错误码分类。
  ///
  /// @param root 响应
  /// @return 结果；可以重试时为空
  private static Optional<HumanVerificationResult> classifyFailure(JsonNode root) {
    Set<String> codes = new HashSet<>();
    for (JsonNode code : root.path("error-codes")) {
      codes.add(code.asString(""));
    }
    if (codes.contains(RETRYABLE_CODE)) {
      return Optional.empty();
    }
    if (codes.stream().anyMatch(SECRET_ERROR_CODES::contains)) {
      log.error("Turnstile 的密钥不可用: {}", codes);
      return Optional.of(HumanVerificationResult.UNAVAILABLE);
    }
    if (codes.stream().anyMatch(TOKEN_REJECTED_CODES::contains)) {
      return Optional.of(HumanVerificationResult.REJECTED);
    }
    log.warn("Turnstile 校验接口返回了未处理的错误码: {}", codes);
    return Optional.of(HumanVerificationResult.UNAVAILABLE);
  }

  /// 校验接口的响应。
  ///
  /// @param status 状态码
  /// @param body 响应体
  private record Reply(HttpStatusCode status, String body) {}
}
