package dev.patra.notification.preference;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.support.NotificationFlowITSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// 偏好读写的整条链路：需登录；新用户是默认值且不落库；按类单独保存，刷新后保持（PAP-142 AC 1、5）。
class PreferenceFlowIT extends NotificationFlowITSupport {

  private static final long ME = 7L;
  private static final String PREFERENCES = "/me/notification-preferences";

  /// 不带身份断言读、写偏好：401。
  @Test
  void anonymous_read_and_write_are_rejected() {
    assertThat(status(call(HttpMethod.GET, null, PREFERENCES, null))).isEqualTo(401);
    assertThat(status(call(HttpMethod.PUT, null, PREFERENCES + "/REPLY", "{\"mode\":\"OFF\"}")))
        .isEqualTo(401);
    assertThat(rows()).isZero();
  }

  /// 新用户两类都是离站时逐条，读一次不落库。
  @Test
  void new_user_gets_defaults_without_row() {
    EntityExchangeResult<String> result = call(HttpMethod.GET, ME, PREFERENCES, null);

    assertThat(status(result)).isEqualTo(200);
    assertThat(json(result).get("reply").asString()).isEqualTo("INSTANT");
    assertThat(json(result).get("mention").asString()).isEqualTo("INSTANT");
    assertThat(json(result).has("helpful")).isFalse();
    assertThat(rows()).isZero();
  }

  /// 改一类立即保存，返回两类档位；再读一致；库里一行、带 43 位令牌。
  @Test
  void put_saves_one_category_and_get_reflects_it() {
    EntityExchangeResult<String> saved =
        call(HttpMethod.PUT, ME, PREFERENCES + "/REPLY", "{\"mode\":\"DIGEST\"}");

    assertThat(status(saved)).isEqualTo(200);
    assertThat(json(saved).get("reply").asString()).isEqualTo("DIGEST");
    assertThat(json(saved).get("mention").asString()).isEqualTo("INSTANT");
    JsonNode reloaded = json(call(HttpMethod.GET, ME, PREFERENCES, null));
    assertThat(reloaded.get("reply").asString()).isEqualTo("DIGEST");
    assertThat(rows()).isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT unsubscribe_token FROM preference.email_preference WHERE user_id = ?",
                String.class,
                ME))
        .matches("^[A-Za-z0-9_-]{43}$");
  }

  /// 改一类不动另一类。
  @Test
  void put_one_category_keeps_the_other() {
    call(HttpMethod.PUT, ME, PREFERENCES + "/MENTION", "{\"mode\":\"OFF\"}");

    JsonNode result =
        json(call(HttpMethod.PUT, ME, PREFERENCES + "/REPLY", "{\"mode\":\"DIGEST\"}"));

    assertThat(result.get("reply").asString()).isEqualTo("DIGEST");
    assertThat(result.get("mention").asString()).isEqualTo("OFF");
  }

  /// 改「有帮助」、写错类别或档位：422。
  @Test
  void put_helpful_or_bad_mode_is_422() {
    for (String[] request :
        new String[][] {
          {"/HELPFUL", "{\"mode\":\"OFF\"}"},
          {"/reply", "{\"mode\":\"OFF\"}"},
          {"/REPLY", "{\"mode\":\"WEEKLY\"}"},
          {"/REPLY", "{}"}
        }) {
      EntityExchangeResult<String> result =
          call(HttpMethod.PUT, ME, PREFERENCES + request[0], request[1]);
      assertThat(status(result)).as(request[0] + " " + request[1]).isEqualTo(422);
      assertThat(json(result).get("code").asString()).isEqualTo("NOTIFICATION-0422");
    }
    assertThat(rows()).isZero();
  }

  /// 偏好表行数。
  ///
  /// @return 行数
  private long rows() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM preference.email_preference", Long.class);
  }
}
