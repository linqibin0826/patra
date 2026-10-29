package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.infra.session.store.RedisSessionStore;
import dev.patra.identity.infra.session.store.SessionToken;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/// 登录设备的端到端流程（PAP-99）：经受信转发链路来的客户端 IP 与 UA 写进登录记录，解析出设备名、端与属地；
/// 会话列表与踢设备；新设备邮件。identity 测试里没有网关，`X-Forwarded-For` 直接由测试带上，
/// 和网关转发后 identity 看到的一样（`forward-headers-strategy: framework`）。
@DisplayName("登录设备流程")
class LoginDeviceFlowIT extends IdentityFlowITSupport {

  @Autowired RedisSessionStore sessionStore;

  static final String UA_CHROME_MAC =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/141.0.0.0 Safari/537.36";
  static final String DEVICE_A = "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy";
  static final String DEVICE_B = "Zz9_y-8xW7vU6tS5rQ4pO3nM2lK1jI0hG9fE8dC7bAa";
  static final String DEVICE_C = "Cc3_c-3cC3cC3cC3cC3cC3cC3cC3cC3cC3cC3cC3cC3";
  static final String SHENZHEN_IP = "113.92.157.29";
  static final String NEW_DEVICE_SUBJECT = "你的 Patra 账号在新设备上登录";

  @Test
  @DisplayName("注册与登录都把设备名、端、IP、属地、UA 写进登录记录")
  void should_record_device_and_region_on_register_and_login() {
    RawResponse registered =
        post(
            "/auth/register",
            Map.of(
                "email",
                "device.record@example.com",
                "username",
                "device-record",
                "password",
                PASSWORD,
                "deviceId",
                DEVICE_A),
            from(SHENZHEN_IP));
    assertThat(registered.status()).as(registered.body()).isEqualTo(201);
    long userId = userIdOf(registered);
    assertRecord(latestSessionIdOf(userId), SHENZHEN_IP, "中国 广东 深圳");

    RawResponse loggedIn = loginFrom("device.record@example.com", DEVICE_A, from(SHENZHEN_IP));
    assertThat(loggedIn.status()).as(loggedIn.body()).isEqualTo(200);
    assertRecord(latestSessionIdOf(userId), SHENZHEN_IP, "中国 广东 深圳");
  }

  @ParameterizedTest
  @ValueSource(strings = {"192.168.1.10", "100.70.1.1", "127.0.0.1"})
  @DisplayName("内网与回环地址：登录照常，属地为空")
  void should_leave_region_empty_for_private_addresses(String ip) {
    long userId =
        userIdOf(
            register("private." + ip.hashCode() + "@example.com", "p" + Math.abs(ip.hashCode())));

    RawResponse loggedIn = loginFrom("private." + ip.hashCode() + "@example.com", null, from(ip));

    assertThat(loggedIn.status()).as(loggedIn.body()).isEqualTo(200);
    Map<String, Object> row = recordOf(latestSessionIdOf(userId));
    assertThat(row.get("client_ip")).isEqualTo(ip);
    assertThat(row.get("ip_region")).isNull();
  }

  @Test
  @DisplayName("X-Forwarded-For 不是 IP：登录不出错，记录里的 IP 不是那串")
  void should_not_fail_login_on_garbage_forwarded_for() {
    long userId = userIdOf(register("garbage.ip@example.com", "garbage-ip"));

    RawResponse loggedIn = loginFrom("garbage.ip@example.com", null, from("not-an-ip"));

    assertThat(loggedIn.status()).as(loggedIn.body()).isEqualTo(200);
    assertThat(recordOf(latestSessionIdOf(userId)).get("client_ip")).isNotEqualTo("not-an-ip");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "for=\"113.92.157.29:54321\";proto=http|113.92.157.29|中国 广东 深圳",
        "for=\"[2001:4860:4860::8888]:443\";proto=http|2001:4860:4860::8888|美国"
      })
  @DisplayName("网关写的 Forwarded 带端口与方括号：记录里的 IP 不带它们，属地照常")
  void should_read_client_ip_from_gateway_forwarded_header(String testCase) {
    String[] parts = testCase.split("\\|");
    String email = "forwarded." + Math.abs(parts[1].hashCode()) + "@example.com";
    long userId = userIdOf(register(email, "fw" + Math.abs(parts[1].hashCode())));
    HttpHeaders headers = new HttpHeaders();
    headers.add("Forwarded", parts[0]);
    headers.add("X-Forwarded-For", parts[1]);
    headers.add(HttpHeaders.USER_AGENT, UA_CHROME_MAC);

    RawResponse loggedIn = loginFrom(email, null, headers);

    assertThat(loggedIn.status()).as(loggedIn.body()).isEqualTo(200);
    Map<String, Object> row = recordOf(latestSessionIdOf(userId));
    assertThat(row.get("client_ip")).isEqualTo(parts[1]);
    assertThat(row.get("ip_region")).isEqualTo(parts[2]);
  }

  @Test
  @DisplayName("新设备邮件：注册时的设备再登录不发；换一台设备登录发一封，写着设备、IP 与属地")
  void should_mail_on_login_from_new_device_only() {
    String email = "new.device@example.com";
    long userId = userIdOf(registerFrom(email, "new-device", DEVICE_A));

    assertThat(loginFrom(email, DEVICE_A, from(SHENZHEN_IP)).status()).isEqualTo(200);
    assertThat(redis.hasKey(newDeviceThrottleKey(userId, DEVICE_A))).isFalse();
    assertThat(loginFrom(email, DEVICE_B, from(SHENZHEN_IP)).status()).isEqualTo(200);
    assertThat(redis.hasKey(newDeviceThrottleKey(userId, DEVICE_B))).isTrue();

    awaitNewDeviceMails(email, 1);
    String text = partText(mailFor(email, NEW_DEVICE_SUBJECT), "text/plain");
    assertThat(text).contains("Chrome 141 · macOS").contains(SHENZHEN_IP).contains("中国 广东 深圳");
  }

  @Test
  @DisplayName("常用设备在会话绝对过期（180 天）后重新登录：不当成新设备")
  void should_not_mail_when_previous_session_of_device_expired_within_lookback() {
    String email = "expired.device@example.com";
    long userId = userIdOf(registerFrom(email, "expired-device", DEVICE_A));
    clock.advance(Duration.ofDays(180).plusMinutes(1));

    assertThat(loginFrom(email, DEVICE_A, from(SHENZHEN_IP)).status()).isEqualTo(200);
    assertThat(redis.hasKey(newDeviceThrottleKey(userId, DEVICE_A))).isFalse();
    assertThat(loginFrom(email, DEVICE_C, from(SHENZHEN_IP)).status()).isEqualTo(200);

    awaitNewDeviceMails(email, 1);
  }

  @Test
  @DisplayName("设备登出后满 180 天没再出现：再登录算新设备")
  void should_mail_when_device_unseen_for_lookback() {
    String email = "unseen.device@example.com";
    long userId = userIdOf(registerFrom(email, "unseen-device", DEVICE_A));
    RawResponse logout =
        post("/auth/logout", Map.of(), assertionFor(userId, latestSessionIdOf(userId)));
    assertThat(logout.status()).as(logout.body()).isEqualTo(204);
    clock.advance(Duration.ofDays(180).plusSeconds(1));

    assertThat(loginFrom(email, DEVICE_A, from(SHENZHEN_IP)).status()).isEqualTo(200);

    awaitNewDeviceMails(email, 1);
  }

  @Test
  @DisplayName("会话列表：两台设备，本机第一并标 current，设备名、IP、属地来自登录记录")
  void should_list_sessions_with_current_first() {
    String email = "list.sessions@example.com";
    long userId = userIdOf(registerFrom(email, "list-sessions", DEVICE_A));
    long sessionA = latestSessionIdOf(userId);
    clock.advance(Duration.ofMinutes(5));
    assertThat(loginFrom(email, DEVICE_B, from("119.29.29.29")).status()).isEqualTo(200);
    long sessionB = latestSessionIdOf(userId);

    RawResponse listed = get("/me/sessions", assertionFor(userId, sessionA));

    assertThat(listed.status()).as(listed.body()).isEqualTo(200);
    JsonNode sessions = json(listed).get("sessions");
    assertThat(sessions).hasSize(2);
    assertThat(sessions.get(0).get("id").asString()).isEqualTo(Long.toString(sessionA));
    assertThat(sessions.get(0).get("current").asBoolean()).isTrue();
    assertThat(sessions.get(0).get("ipRegion").asString()).isEqualTo("中国 广东 深圳");
    assertThat(sessions.get(1).get("id").asString()).isEqualTo(Long.toString(sessionB));
    assertThat(sessions.get(1).get("current").asBoolean()).isFalse();
    assertThat(sessions.get(1).get("deviceName").asString()).isEqualTo("Chrome 141 · macOS");
    assertThat(sessions.get(1).get("deviceClass").asString()).isEqualTo("DESKTOP");
    assertThat(sessions.get(1).get("ipAddress").asString()).isEqualTo("119.29.29.29");
    assertThat(sessions.get(1).get("ipRegion").asString()).isEqualTo("中国 北京");
    assertThat(sessions.get(1).get("privateNetwork").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("最后活跃随请求更新：网关续期距上次写入不满 60 秒不写，满 60 秒写")
  void should_reflect_renewal_in_last_active_at() {
    RawResponse registered = registerFrom("renew.session@example.com", "renew-session", DEVICE_A);
    long userId = userIdOf(registered);
    long sessionId = latestSessionIdOf(userId);
    SessionToken token = SessionToken.parse(sessionTokenOf(registered)).orElseThrow();

    clock.advance(Duration.ofSeconds(30));
    sessionStore.findAndTouch(token).orElseThrow();
    assertThat(lastActiveAtOf(userId, sessionId)).isEqualTo(T0.toString());

    clock.advance(Duration.ofSeconds(31));
    sessionStore.findAndTouch(token).orElseThrow();
    assertThat(lastActiveAtOf(userId, sessionId)).isEqualTo(T0.plusSeconds(61).toString());
  }

  @Test
  @DisplayName("踢单个：超过再认证窗口 403；再认证后踢掉，令牌失效、记录 REVOKED、回 1；再踢回 0；踢本机 409")
  void should_kick_one_device_after_reauth() {
    String email = "kick.one@example.com";
    long userId = userIdOf(registerFrom(email, "kick-one", DEVICE_A));
    long sessionA = latestSessionIdOf(userId);
    RawResponse other = loginFrom(email, DEVICE_B, from(SHENZHEN_IP));
    long sessionB = latestSessionIdOf(userId);
    SessionToken tokenB = SessionToken.parse(sessionTokenOf(other)).orElseThrow();
    clock.advance(Duration.ofMinutes(11));

    RawResponse stale = delete("/me/sessions/" + sessionB, assertionFor(userId, sessionA));
    assertThat(stale.status()).as(stale.body()).isEqualTo(403);
    assertThat(json(stale).get("traits").toString()).contains("REAUTH_REQUIRED");

    RawResponse reauth =
        post("/auth/reauth", Map.of("password", PASSWORD), assertionFor(userId, sessionA));
    assertThat(reauth.status()).as(reauth.body()).isEqualTo(200);

    RawResponse kicked = delete("/me/sessions/" + sessionB, assertionFor(userId, sessionA));
    assertThat(kicked.status()).as(kicked.body()).isEqualTo(200);
    assertThat(json(kicked).get("revokedSessions").asInt()).isEqualTo(1);
    assertThat(sessionStore.findAndTouch(tokenB)).as("被踢的令牌，网关下一个请求 401").isEmpty();
    assertThat(endReasonOf(sessionB)).isEqualTo("REVOKED");

    RawResponse again = delete("/me/sessions/" + sessionB, assertionFor(userId, sessionA));
    assertThat(json(again).get("revokedSessions").asInt()).isZero();
    RawResponse self = delete("/me/sessions/" + sessionA, assertionFor(userId, sessionA));
    assertThat(self.status()).as(self.body()).isEqualTo(409);
    assertThat(json(self).get("traits").toString()).contains("CURRENT_SESSION");
  }

  @Test
  @DisplayName("踢其他全部：三台设备只剩本机")
  void should_kick_all_other_devices() {
    String email = "kick.all@example.com";
    long userId = userIdOf(registerFrom(email, "kick-all", DEVICE_A));
    long sessionA = latestSessionIdOf(userId);
    loginFrom(email, DEVICE_B, from(SHENZHEN_IP));
    loginFrom(email, DEVICE_C, from(SHENZHEN_IP));

    RawResponse kicked = delete("/me/sessions/others", assertionFor(userId, sessionA));

    assertThat(kicked.status()).as(kicked.body()).isEqualTo(200);
    assertThat(json(kicked).get("revokedSessions").asInt()).isEqualTo(2);
    JsonNode sessions = json(get("/me/sessions", assertionFor(userId, sessionA))).get("sessions");
    assertThat(sessions).hasSize(1);
    assertThat(sessions.get(0).get("id").asString()).isEqualTo(Long.toString(sessionA));
  }

  @Test
  @DisplayName("被踢掉的设备再登录立即再提醒，不等 24 小时冷却：踢单个、踢其他全部都一样")
  void should_mail_again_right_after_device_was_kicked() {
    String email = "kicked.device@example.com";
    long userId = userIdOf(registerFrom(email, "kicked-device", DEVICE_A));
    long sessionA = latestSessionIdOf(userId);
    loginFrom(email, DEVICE_B, from(SHENZHEN_IP));
    awaitNewDeviceMails(email, 1);

    kickLatest(userId, sessionA);
    assertThat(loginFrom(email, DEVICE_B, from(SHENZHEN_IP)).status()).isEqualTo(200);
    awaitNewDeviceMails(email, 2);

    RawResponse kicked = delete("/me/sessions/others", assertionFor(userId, sessionA));
    assertThat(kicked.status()).as(kicked.body()).isEqualTo(200);
    assertThat(loginFrom(email, DEVICE_B, from(SHENZHEN_IP)).status()).isEqualTo(200);
    awaitNewDeviceMails(email, 3);
  }

  @Test
  @DisplayName("踢掉一台设备后，它更早的登出记录也不再算见过：再登录照样提醒")
  void should_mail_when_kicked_device_has_earlier_logged_out_session() {
    String email = "kicked.earlier@example.com";
    long userId = userIdOf(registerFrom(email, "kicked-earlier", DEVICE_A));
    long sessionA = latestSessionIdOf(userId);
    loginFrom(email, DEVICE_B, from(SHENZHEN_IP));
    awaitNewDeviceMails(email, 1);
    long firstB = latestSessionIdOf(userId);
    assertThat(post("/auth/logout", Map.of(), assertionFor(userId, firstB)).status())
        .isEqualTo(204);
    loginFrom(email, DEVICE_B, from(SHENZHEN_IP));

    kickLatest(userId, sessionA);
    assertThat(loginFrom(email, DEVICE_B, from(SHENZHEN_IP)).status()).isEqualTo(200);
    awaitNewDeviceMails(email, 2);
  }

  /// 踢掉最近一次登录的会话（用本机的断言）。
  ///
  /// @param userId 用户 ID
  /// @param current 本机会话 ID
  void kickLatest(long userId, long current) {
    RawResponse kicked =
        delete("/me/sessions/" + latestSessionIdOf(userId), assertionFor(userId, current));
    assertThat(kicked.status()).as(kicked.body()).isEqualTo(200);
    assertThat(json(kicked).get("revokedSessions").asInt()).isEqualTo(1);
  }

  /// 发一个 DELETE。
  ///
  /// @param uri 路径
  /// @param headers 请求头
  /// @return 响应
  RawResponse delete(String uri, HttpHeaders headers) {
    return exchange(HttpMethod.DELETE, uri, null, headers);
  }

  /// 会话列表里某个会话的 `lastActiveAt`。
  ///
  /// @param userId 用户 ID
  /// @param sessionId 会话 ID
  /// @return ISO-8601 文本
  String lastActiveAtOf(long userId, long sessionId) {
    for (JsonNode session :
        json(get("/me/sessions", assertionFor(userId, sessionId))).get("sessions")) {
      if (session.get("id").asString().equals(Long.toString(sessionId))) {
        return session.get("lastActiveAt").asString();
      }
    }
    throw new AssertionError("列表里没有会话 " + sessionId);
  }

  @Test
  @DisplayName("X-Forwarded-For 超长：注册照常 201，令牌表与登录记录里的 IP 为空")
  void should_ignore_overlong_forwarded_for_on_register() {
    String overlong = "x".repeat(60);

    RawResponse registered =
        post(
            "/auth/register",
            Map.of(
                "email",
                "overlong.ip@example.com",
                "username",
                "overlong-ip",
                "password",
                PASSWORD),
            from(overlong));

    assertThat(registered.status()).as(registered.body()).isEqualTo(201);
    long userId = userIdOf(registered);
    assertThat(recordOf(latestSessionIdOf(userId)).get("client_ip")).isNull();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT requested_ip FROM idn_email_token WHERE user_id = ?", String.class, userId))
        .containsOnlyNulls();
  }

  @Test
  @DisplayName("X-Forwarded-For 超长：找回密码照常 202，不 500")
  void should_ignore_overlong_forwarded_for_on_password_reset_request() {
    register("overlong.forgot@example.com", "overlong-forgot");

    RawResponse forgot =
        post(
            "/auth/password/forgot",
            Map.of("email", "overlong.forgot@example.com"),
            from("y".repeat(60)));

    assertThat(forgot.status()).as(forgot.body()).isEqualTo(202);
  }

  /// 带设备标识、从深圳的 IP 注册。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @param deviceId 设备标识
  /// @return 响应
  RawResponse registerFrom(String email, String username, String deviceId) {
    RawResponse response =
        post(
            "/auth/register",
            Map.of(
                "email", email, "username", username, "password", PASSWORD, "deviceId", deviceId),
            from(SHENZHEN_IP));
    assertThat(response.status()).as(response.body()).isEqualTo(201);
    return response;
  }

  /// 等到发给某个地址的新设备提醒有 `expected` 封，再确认没有多出来的。发信是异步的：
  /// 按总封数等会被上一个用例晚到的邮件凑数，所以按收件人与主题等。
  ///
  /// @param email 收件人
  /// @param expected 期望的封数
  void awaitNewDeviceMails(String email, long expected) {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (newDeviceMailsTo(email) < expected && System.nanoTime() < deadline) {
      greenMail.waitForIncomingEmail(200, greenMail.getReceivedMessages().length + 1);
    }
    assertThat(newDeviceMailsTo(email)).as("发给 %s 的新设备提醒", email).isEqualTo(expected);
  }

  /// 新设备提醒的限流键：被判为新设备时，登录请求里就同步写下它（键名规则同 `NewDeviceMailThrottleAdapter`）。
  ///
  /// @param userId 用户 ID
  /// @param deviceId 设备标识
  /// @return 键名
  static String newDeviceThrottleKey(long userId, String deviceId) {
    return "idn:new-device-mail:user:" + userId + ":" + sha256Hex(deviceId).substring(0, 32);
  }

  /// 发给某个地址的新设备提醒有几封。
  ///
  /// @param email 收件人
  /// @return 封数
  long newDeviceMailsTo(String email) {
    return Arrays.stream(greenMail.getReceivedMessages())
        .filter(mail -> recipients(mail).contains(email))
        .filter(mail -> NEW_DEVICE_SUBJECT.equals(subjectOf(mail)))
        .count();
  }

  /// 带设备标识登录。
  ///
  /// @param email 邮箱
  /// @param deviceId 设备标识，可以为 `null`
  /// @param headers 请求头
  /// @return 响应
  RawResponse loginFrom(String email, String deviceId, HttpHeaders headers) {
    Map<String, Object> body =
        deviceId == null
            ? Map.of("email", email, "password", PASSWORD)
            : Map.of("email", email, "password", PASSWORD, "deviceId", deviceId);
    return post("/auth/login", body, headers);
  }

  /// 像经过网关一样带上客户端 IP 与浏览器 UA。
  ///
  /// @param ip 客户端 IP
  /// @return 请求头
  static HttpHeaders from(String ip) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Forwarded-For", ip);
    headers.add(HttpHeaders.USER_AGENT, UA_CHROME_MAC);
    return headers;
  }

  /// 读一条登录记录的设备与来源列。
  ///
  /// @param sessionId 会话 ID
  /// @return 列名到值
  Map<String, Object> recordOf(long sessionId) {
    return jdbcTemplate.queryForMap(
        "SELECT device_id, user_agent, device_name, device_class, client_ip, ip_region"
            + " FROM idn_user_login_record WHERE id = ?",
        sessionId);
  }

  /// 断言一条记录是 Chrome / macOS 桌面端、来自给定 IP 与属地。
  ///
  /// @param sessionId 会话 ID
  /// @param ip 期望的 IP
  /// @param region 期望的属地
  void assertRecord(long sessionId, String ip, String region) {
    Map<String, Object> row = recordOf(sessionId);
    assertThat(row)
        .containsEntry("device_id", DEVICE_A)
        .containsEntry("user_agent", UA_CHROME_MAC)
        .containsEntry("device_name", "Chrome 141 · macOS")
        .containsEntry("device_class", "DESKTOP")
        .containsEntry("client_ip", ip)
        .containsEntry("ip_region", region);
  }
}
