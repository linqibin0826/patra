package dev.patra.identity.infra.account.verification;

import dev.patra.identity.domain.account.model.enums.HumanVerificationAction;
import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import dev.patra.identity.domain.account.model.enums.HumanVerificationResult;
import dev.patra.identity.domain.account.port.verification.HumanVerificationPort;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// TRUSTTURN 校验。运营方和数据处理方式都没有公开说明，所以请求只带站点标识、票据和期望的动作，不传任何用户数据。
///
/// `success` 为真之外，还要 `site_key` 相同、`action` 一致、`origin` 在允许名单里。`risk` 文档没说明取值，只打 DEBUG。
/// 不重试：文档没有提供幂等机制，票据可能已被消费。
///
/// 文档只列了错误码，没给错误响应的示例，这里依次读 `error`、`code` 字段，读不到算验证不了。
@Slf4j
public final class TrustTurnAdapter implements HumanVerificationPort {

  /// 令牌长度上限。文档只说至少 64 个字符，这里和 Turnstile 取同一个上限，挡掉明显不是票据的输入。
  private static final int MAX_TOKEN_LENGTH = 2048;

  private static final int NONCE_BYTES = 18;

  private static final Set<String> TICKET_REJECTED_CODES =
      Set.of(
          "invalid_ticket",
          "action_mismatch",
          "challenge_binding",
          "replayed_token",
          "expired_token",
          "session_binding",
          "environment_risk",
          "site_domain_not_allowed");

  private final RestClient client;
  private final JsonMapper jsonMapper;
  private final Supplier<TrustTurnSettings> settings;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  /// 创建适配器。
  ///
  /// @param client 出站客户端，带超时
  /// @param jsonMapper JSON 序列化
  /// @param settings 配置来源
  /// @param clock 时钟，用于请求时间戳
  public TrustTurnAdapter(
      RestClient client, JsonMapper jsonMapper, Supplier<TrustTurnSettings> settings, Clock clock) {
    this.client = Objects.requireNonNull(client, "client 不能为 null");
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper 不能为 null");
    this.settings = Objects.requireNonNull(settings, "settings 不能为 null");
    this.clock = Objects.requireNonNull(clock, "clock 不能为 null");
  }

  /// 这个实现对应的厂商。
  ///
  /// @return TRUSTTURN
  @Override
  public HumanVerificationProvider provider() {
    return HumanVerificationProvider.TRUST_TURN;
  }

  /// 核对票据。
  ///
  /// @param token 浏览器提交的票据
  /// @param action 这次操作
  /// @return 结果
  @Override
  public HumanVerificationResult verify(String token, HumanVerificationAction action) {
    TrustTurnSettings current = settings.get();
    if (isBlank(current.appSecret()) || isBlank(current.siteKey())) {
      log.error("TRUSTTURN 的 site-key 或 app-secret 没配，人机验证不可用");
      return HumanVerificationResult.UNAVAILABLE;
    }
    if (!VerificationTokenRules.isSendable(token, MAX_TOKEN_LENGTH)) {
      return HumanVerificationResult.REJECTED;
    }
    Reply reply;
    try {
      reply = consume(current, token, action);
    } catch (RestClientException e) {
      log.warn("调 TRUSTTURN 校验接口失败: {}", e.getClass().getSimpleName());
      return HumanVerificationResult.UNAVAILABLE;
    }
    if (reply == null || reply.status().is5xxServerError()) {
      log.warn("TRUSTTURN 校验接口返回 5xx");
      return HumanVerificationResult.UNAVAILABLE;
    }
    return classify(reply.body(), current, action);
  }

  /// 发一次消费票据的请求。签名和发送用同一份请求体字节。
  ///
  /// @param current 配置
  /// @param token 票据
  /// @param action 这次操作
  /// @return 响应
  private Reply consume(TrustTurnSettings current, String token, HumanVerificationAction action) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("site_key", current.siteKey());
    fields.put("ticket", token);
    fields.put("expected_action", action.wireValue());
    byte[] body = jsonMapper.writeValueAsBytes(fields);
    long timestamp = clock.millis();
    String nonce = newNonce();
    return client
        .post()
        .uri(current.baseUrl() + TrustTurnRequestSigner.PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-TrustTurn-Timestamp", String.valueOf(timestamp))
        .header("X-TrustTurn-Nonce", nonce)
        .header(
            "X-TrustTurn-Signature",
            TrustTurnRequestSigner.sign(current.appSecret(), timestamp, nonce, body))
        .body(body)
        .exchange(
            (request, response) ->
                new Reply(
                    response.getStatusCode(),
                    new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
  }

  /// 解读响应。
  ///
  /// @param body 响应体
  /// @param current 配置
  /// @param action 这次操作
  /// @return 结果
  private HumanVerificationResult classify(
      String body, TrustTurnSettings current, HumanVerificationAction action) {
    JsonNode root;
    try {
      root = jsonMapper.readTree(body);
    } catch (JacksonException e) {
      log.warn("TRUSTTURN 校验接口返回的不是 JSON");
      return HumanVerificationResult.UNAVAILABLE;
    }
    JsonNode success = root.path("success");
    if (success.isBoolean() && success.asBoolean()) {
      return classifySuccess(root, current, action);
    }
    if (root.has("success") && !success.isBoolean()) {
      log.warn("TRUSTTURN 校验接口的 success 不是布尔值");
      return HumanVerificationResult.UNAVAILABLE;
    }
    return classifyFailure(root);
  }

  /// 解读 `success` 为真的响应：核对 `site_key`、`action` 和 `origin`。
  ///
  /// @param root 响应
  /// @param current 配置
  /// @param action 这次操作
  /// @return 结果
  private static HumanVerificationResult classifySuccess(
      JsonNode root, TrustTurnSettings current, HumanVerificationAction action) {
    log.debug("TRUSTTURN 风险判断: {}", root.path("risk"));
    boolean siteKeyMatches = current.siteKey().equals(root.path("site_key").asString(""));
    boolean actionMatches = action.wireValue().equals(root.path("action").asString(""));
    boolean originAllowed = current.allowedOrigins().contains(root.path("origin").asString(""));
    if (siteKeyMatches && actionMatches && originAllowed) {
      return HumanVerificationResult.PASSED;
    }
    log.warn(
        "TRUSTTURN 票据对不上这次操作: action={}, siteKeyMatches={}, actionMatches={}, originAllowed={}",
        action.wireValue(),
        siteKeyMatches,
        actionMatches,
        originAllowed);
    return HumanVerificationResult.REJECTED;
  }

  /// 解读失败的响应：按错误码分类。
  ///
  /// @param root 响应
  /// @return 结果
  private static HumanVerificationResult classifyFailure(JsonNode root) {
    String code = root.path("error").asString("");
    if (code.isEmpty()) {
      code = root.path("code").asString("");
    }
    if (TICKET_REJECTED_CODES.contains(code)) {
      return HumanVerificationResult.REJECTED;
    }
    log.error("TRUSTTURN 校验不可用: code={}", code);
    return HumanVerificationResult.UNAVAILABLE;
  }

  /// 生成 nonce：18 字节随机数的 base64url。
  ///
  /// @return nonce
  private String newNonce() {
    byte[] bytes = new byte[NONCE_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /// 判断配置值是否缺失。
  ///
  /// @param value 配置值
  /// @return 缺失时为 `true`
  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /// 校验接口的响应。
  ///
  /// @param status 状态码
  /// @param body 响应体
  private record Reply(HttpStatusCode status, String body) {}
}
