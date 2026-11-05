package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.config.HumanVerificationFixture;
import dev.patra.identity.config.HumanVerificationProperties;
import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/// 人机验证的流程测试：真实的 Redis 和数据库，第三方由 WireMock 扮演。这里不自动带令牌，每个请求带什么都写明。
@DisplayName("人机验证流程")
class HumanVerificationFlowIT extends IdentityFlowITSupport {

  private static final String WRONG_PASSWORD = "Wrong-Horse-99";
  private static final String REQUIRED = "VERIFICATION_REQUIRED";
  private static final String FAILED = "VERIFICATION_FAILED";

  @Autowired private HumanVerificationProperties properties;

  /// 不自动带令牌。
  ///
  /// @return `false`
  @Override
  boolean autoVerification() {
    return false;
  }

  /// 每个用例从默认的桩开始。
  @BeforeEach
  void resetVerification() {
    HumanVerificationFixture.reset();
  }

  @Test
  @DisplayName("撞库：同一 IP 换 5 个邮箱失败后，第 6 个邮箱不带令牌被要求验证")
  void credential_stuffing_requires_verification_after_five_failures_from_one_ip() {
    HttpHeaders ip = fromIp(uniqueIp());
    for (int i = 0; i < 5; i++) {
      assertThat(attempt(uniqueEmail(), WRONG_PASSWORD, null, ip).status()).isEqualTo(401);
    }

    assertRequires(attempt(uniqueEmail(), WRONG_PASSWORD, null, ip), REQUIRED);
  }

  @Test
  @DisplayName("爆破：同一邮箱换 3 个 IP 失败后，第 4 次不带令牌被要求验证")
  void brute_force_requires_verification_after_three_failures_on_one_email() {
    String email = uniqueEmail();
    for (int i = 0; i < 3; i++) {
      assertThat(attempt(email, WRONG_PASSWORD, null, fromIp(uniqueIp())).status()).isEqualTo(401);
    }

    assertRequires(attempt(email, WRONG_PASSWORD, null, fromIp(uniqueIp())), REQUIRED);
  }

  @Test
  @DisplayName("第 5 次失败锁定；已锁定时不要求验证，也不调第三方")
  void locks_on_fifth_failure_and_locked_account_skips_verification() {
    String email = uniqueEmail();
    for (int i = 0; i < 3; i++) {
      assertThat(attempt(email, WRONG_PASSWORD, null, fromIp(uniqueIp())).status()).isEqualTo(401);
    }
    assertThat(
            attempt(email, WRONG_PASSWORD, HumanVerificationFixture.LOGIN, fromIp(uniqueIp()))
                .status())
        .isEqualTo(401);
    assertThat(
            attempt(email, WRONG_PASSWORD, HumanVerificationFixture.LOGIN, fromIp(uniqueIp()))
                .status())
        .isEqualTo(429);
    int callsBefore = HumanVerificationFixture.turnstileCalls();

    assertThat(attempt(email, WRONG_PASSWORD, null, fromIp(uniqueIp())).status()).isEqualTo(429);

    assertThat(HumanVerificationFixture.turnstileCalls()).isEqualTo(callsBefore);
  }

  @Test
  @DisplayName("不泄露：存在和不存在的邮箱，每一步的响应都相同")
  void unknown_and_existing_email_get_identical_responses() {
    String existing = uniqueEmail();
    registerUser(existing);

    List<String> forExisting = responsesOfFourFailures(existing);
    List<String> forUnknown = responsesOfFourFailures(uniqueEmail());

    assertThat(forExisting).isEqualTo(forUnknown);
    assertThat(forExisting).containsExactly("401", "401", "401", "422:" + REQUIRED);
  }

  @Test
  @DisplayName("登录成功不清 IP 计数：攻击者用自己的账号登录一次也清不掉")
  void successful_login_does_not_clear_ip_counter() {
    String email = uniqueEmail();
    registerUser(email);
    HttpHeaders ip = fromIp(uniqueIp());
    for (int i = 0; i < 5; i++) {
      attempt(uniqueEmail(), WRONG_PASSWORD, null, ip);
    }

    assertThat(attempt(email, PASSWORD, HumanVerificationFixture.LOGIN, ip).status())
        .isEqualTo(200);

    assertRequires(attempt(uniqueEmail(), WRONG_PASSWORD, null, ip), REQUIRED);
  }

  @Test
  @DisplayName("校验失败不占失败限制名额")
  void failed_verification_does_not_consume_failure_quota() {
    String email = uniqueEmail();
    for (int i = 0; i < 3; i++) {
      attempt(email, WRONG_PASSWORD, null, fromIp(uniqueIp()));
    }

    for (int i = 0; i < 3; i++) {
      assertRequires(
          attempt(email, WRONG_PASSWORD, HumanVerificationFixture.REJECTED, fromIp(uniqueIp())),
          FAILED);
    }

    assertThat(redis.opsForValue().get(loginThrottleKeys(email).getFirst())).isEqualTo("3");
  }

  @Test
  @DisplayName("校验失败时，密码对也登录不了；换一个通过的令牌就能登录")
  void failed_verification_blocks_correct_password() {
    String email = uniqueEmail();
    registerUser(email);
    for (int i = 0; i < 3; i++) {
      attempt(email, WRONG_PASSWORD, null, fromIp(uniqueIp()));
    }

    assertRequires(
        attempt(email, PASSWORD, HumanVerificationFixture.REJECTED, fromIp(uniqueIp())), FAILED);

    assertThat(
            attempt(email, PASSWORD, HumanVerificationFixture.LOGIN, fromIp(uniqueIp())).status())
        .isEqualTo(200);
  }

  @Test
  @DisplayName("注册和找回密码不带令牌被拒绝")
  void register_and_forgot_without_token_are_rejected() {
    assertRequires(
        post(
            "/auth/register",
            Map.of("email", uniqueEmail(), "username", uniqueUsername(), "password", PASSWORD),
            noHeaders()),
        REQUIRED);
    assertRequires(
        post("/auth/password/forgot", Map.of("email", uniqueEmail()), noHeaders()), REQUIRED);
  }

  @Test
  @DisplayName("别的操作拿到的令牌不能挪用")
  void token_for_another_action_is_rejected() {
    assertRequires(
        post(
            "/auth/password/forgot",
            Map.of("email", uniqueEmail(), "verificationToken", HumanVerificationFixture.REGISTER),
            noHeaders()),
        FAILED);
  }

  @Test
  @DisplayName("第三方不可用：三个接口都 503，不放行")
  void third_party_down_returns_503_on_all_three_endpoints() {
    String email = uniqueEmail();
    for (int i = 0; i < 3; i++) {
      attempt(email, WRONG_PASSWORD, null, fromIp(uniqueIp()));
    }
    HumanVerificationFixture.stubUnavailable();

    assertUnavailable(attempt(email, PASSWORD, HumanVerificationFixture.LOGIN, noHeaders()));
    assertUnavailable(
        post(
            "/auth/register",
            Map.of(
                "email",
                uniqueEmail(),
                "username",
                uniqueUsername(),
                "password",
                PASSWORD,
                "verificationToken",
                HumanVerificationFixture.REGISTER),
            noHeaders()));
    assertUnavailable(
        post(
            "/auth/password/forgot",
            Map.of(
                "email",
                uniqueEmail(),
                "verificationToken",
                HumanVerificationFixture.PASSWORD_RESET),
            noHeaders()));
  }

  @Test
  @DisplayName("切换厂商：之后用另一家校验，查询接口返回新厂商")
  void switching_provider_uses_other_adapter_and_query_returns_it() {
    assertThat(json(get("/auth/human-verification", noHeaders())).get("provider").asString())
        .isEqualTo("turnstile");
    properties.setProvider(HumanVerificationProvider.TRUST_TURN);
    try {
      RawResponse current = get("/auth/human-verification", noHeaders());
      assertThat(json(current).get("provider").asString()).isEqualTo("trustturn");
      assertThat(json(current).get("siteKey").asString()).isEqualTo("test-trustturn-site");

      registerUser(uniqueEmail());

      assertThat(HumanVerificationFixture.trustTurnCalls()).isEqualTo(1);
      assertThat(HumanVerificationFixture.turnstileCalls()).isZero();
    } finally {
      properties.setProvider(HumanVerificationProvider.TURNSTILE);
    }
  }

  @Test
  @DisplayName("拿不到 IP：只按邮箱触发，不把这些请求算到同一个键")
  void request_without_valid_ip_uses_email_trigger_only() {
    HttpHeaders invalid = fromIp("not-an-ip");
    for (int i = 0; i < 6; i++) {
      assertThat(attempt(uniqueEmail(), WRONG_PASSWORD, null, invalid).status()).isEqualTo(401);
    }

    String email = uniqueEmail();
    for (int i = 0; i < 3; i++) {
      assertThat(attempt(email, WRONG_PASSWORD, null, invalid).status()).isEqualTo(401);
    }
    assertRequires(attempt(email, WRONG_PASSWORD, null, invalid), REQUIRED);
  }

  /// 同一个邮箱连续失败四次，每次换 IP，记下每一步的结果。
  ///
  /// @param email 邮箱
  /// @return 状态码和原因码
  private List<String> responsesOfFourFailures(String email) {
    List<String> responses = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      RawResponse response = attempt(email, WRONG_PASSWORD, null, fromIp(uniqueIp()));
      responses.add(
          response.status() == 422
              ? "422:" + json(response).get("errors").get(0).get("code").asString()
              : String.valueOf(response.status()));
    }
    return responses;
  }

  /// 发一次登录。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @param token 令牌，可以没有
  /// @param headers 请求头
  /// @return 响应
  private RawResponse attempt(String email, String password, String token, HttpHeaders headers) {
    return post(
        "/auth/login",
        token == null
            ? Map.of("email", email, "password", password)
            : Map.of("email", email, "password", password, "verificationToken", token),
        headers);
  }

  /// 带令牌注册，断言 201。
  ///
  /// @param email 邮箱
  private void registerUser(String email) {
    RawResponse response =
        post(
            "/auth/register",
            Map.of(
                "email",
                email,
                "username",
                uniqueUsername(),
                "password",
                PASSWORD,
                "verificationToken",
                HumanVerificationFixture.REGISTER),
            noHeaders());
    assertThat(response.status()).as(response.body()).isEqualTo(201);
  }

  /// 断言 422，字段错误是给定的原因码。
  ///
  /// @param response 响应
  /// @param code 原因码
  private static void assertRequires(RawResponse response, String code) {
    assertThat(response.status()).as(response.body()).isEqualTo(422);
    assertThat(json(response).get("errors").get(0).get("field").asString())
        .isEqualTo("verificationToken");
    assertThat(json(response).get("errors").get(0).get("code").asString()).isEqualTo(code);
  }

  /// 断言 503，`traits` 带原因码。
  ///
  /// @param response 响应
  private static void assertUnavailable(RawResponse response) {
    assertThat(response.status()).as(response.body()).isEqualTo(503);
    assertThat(response.body()).contains("VERIFICATION_UNAVAILABLE");
  }

  /// 像经门户和网关来的请求，带给定的来源 IP。
  ///
  /// @param ip IP
  /// @return 请求头
  private static HttpHeaders fromIp(String ip) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Forwarded-For", ip);
    return headers;
  }

  /// 不重复的公网 IP。
  ///
  /// @return IP
  private static String uniqueIp() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    return "113." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
  }

  /// 不重复的邮箱。
  ///
  /// @return 邮箱
  private static String uniqueEmail() {
    return "chen." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
  }

  /// 不重复的用户名。
  ///
  /// @return 用户名
  private static String uniqueUsername() {
    return "chen-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
