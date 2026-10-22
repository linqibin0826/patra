package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;

/// 登录设备的端到端流程（PAP-99）：经受信转发链路来的客户端 IP 与 UA 写进登录记录，解析出设备名、端与属地；
/// 会话列表与踢设备；新设备邮件。identity 测试里没有网关，`X-Forwarded-For` 直接由测试带上，
/// 和网关转发后 identity 看到的一样（`forward-headers-strategy: framework`）。
@DisplayName("登录设备流程")
class LoginDeviceFlowIT extends IdentityFlowITSupport {

  static final String UA_CHROME_MAC =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/141.0.0.0 Safari/537.36";
  static final String DEVICE_A = "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy";
  static final String SHENZHEN_IP = "113.92.157.29";

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
