package dev.patra.notification.preference;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.support.NotificationFlowITSupport;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

/// 免登录退订的整条链路（PAP-142 AC 2～5）：落地页读接口不改东西；确认与一键退订按类生效、重复调用结果相同；
/// 链接无效一律 404 + `UNSUBSCRIBE_LINK_INVALID`；读接口回查 identity，写接口不依赖它。
class UnsubscribeFlowIT extends NotificationFlowITSupport {

  private static final long OWNER = 7L;
  private static final String PREFERENCES = "/me/notification-preferences";

  private String token;

  /// 这个人把「回复我的」设成每日汇总，「@ 我的」保持离站逐条；令牌从库里取。
  @BeforeEach
  void givenOwnerPreferences() {
    call(HttpMethod.PUT, OWNER, PREFERENCES + "/REPLY", "{\"mode\":\"DIGEST\"}");
    token =
        jdbcTemplate.queryForObject(
            "SELECT unsubscribe_token FROM preference.email_preference WHERE user_id = ?",
            String.class,
            OWNER);
    stubRecipient(OWNER, "chen.yu@example.com", "ACTIVE");
  }

  /// 打开落地页两次：200、脱敏邮箱、两个判断；档位与更新时间都不变。
  @Test
  void reading_link_does_not_change_anything() {
    OffsetDateTime before = updatedAt();

    EntityExchangeResult<String> first = read(token, "DIGEST");
    EntityExchangeResult<String> second = read(token, "DIGEST");

    assertThat(status(first)).isEqualTo(200);
    assertThat(status(second)).isEqualTo(200);
    JsonNode body = json(first);
    assertThat(body.get("maskedEmail").asString()).isEqualTo("c***@example.com");
    assertThat(body.get("category").asString()).isEqualTo("DIGEST");
    assertThat(body.get("reply").asString()).isEqualTo("DIGEST");
    assertThat(body.get("mention").asString()).isEqualTo("INSTANT");
    assertThat(body.get("alreadyUnsubscribed").asBoolean()).isFalse();
    assertThat(body.get("othersEnabled").asBoolean()).isTrue();
    assertThat(modes()).isEqualTo("DIGEST/INSTANT");
    assertThat(updatedAt()).isEqualTo(before);
  }

  /// 不登录的一键退订：表单体同邮箱服务商；两次都 200，第二次 `turnedOff` 为空，结果相同。设置页同步显示为关。
  @Test
  void one_click_works_anonymously_and_is_idempotent() {
    String uri = "/unsubscribe/one-click?token=" + token + "&category=MENTION";

    EntityExchangeResult<String> first = form(uri, "List-Unsubscribe=One-Click");
    EntityExchangeResult<String> again = form(uri, "List-Unsubscribe=One-Click");

    assertThat(status(first)).isEqualTo(200);
    assertThat(first.getResponseHeaders().getLocation()).isNull();
    assertThat(json(first).get("turnedOff").toString()).isEqualTo("[\"MENTION\"]");
    assertThat(status(again)).isEqualTo(200);
    assertThat(json(again).get("turnedOff").toString()).isEqualTo("[]");
    assertThat(json(again).get("mention").asString()).isEqualTo("OFF");
    assertThat(modes()).isEqualTo("DIGEST/OFF");
    assertThat(json(call(HttpMethod.GET, OWNER, PREFERENCES, null)).get("mention").asString())
        .isEqualTo("OFF");
  }

  /// 令牌与类别只认地址里的：表单体里夹带同名参数（被改写的转发、伪造的请求体）既不能顶替、也不会让退订失败。
  @Test
  void one_click_ignores_parameters_in_body() {
    String other = (token.charAt(0) == 'A' ? "B" : "A") + token.substring(1);

    EntityExchangeResult<String> result =
        form(
            "/unsubscribe/one-click?token=" + token + "&category=MENTION",
            "token=" + other + "&category=ALL&List-Unsubscribe=One-Click");

    assertThat(status(result)).isEqualTo(200);
    assertThat(json(result).get("category").asString()).isEqualTo("MENTION");
    assertThat(modes()).isEqualTo("DIGEST/OFF");
  }

  /// 邮箱服务商也可能用 multipart 发这个请求体，同样生效。
  @Test
  void one_click_accepts_multipart_body() {
    EntityExchangeResult<String> result =
        restClient
            .post()
            .uri("/unsubscribe/one-click?token=" + token + "&category=REPLY")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(multipartOneClick())
            .exchange()
            .expectBody(String.class)
            .returnResult();

    assertThat(status(result)).isEqualTo(200);
    assertThat(modes()).isEqualTo("OFF/INSTANT");
  }

  /// 打开时这一类已经是关：`alreadyUnsubscribed` 为真，落地页出「已经退订过了」。
  @Test
  void reading_link_reports_already_unsubscribed() {
    form("/unsubscribe/one-click?token=" + token + "&category=DIGEST", null);

    JsonNode body = json(read(token, "DIGEST"));

    assertThat(body.get("alreadyUnsubscribed").asBoolean()).isTrue();
    assertThat(body.get("reply").asString()).isEqualTo("OFF");
    assertThat(body.get("othersEnabled").asBoolean()).isTrue();
  }

  /// 后端不检查请求体：不带体的 POST 同样生效。
  @Test
  void one_click_without_body_also_works() {
    EntityExchangeResult<String> result =
        form("/unsubscribe/one-click?token=" + token + "&category=REPLY", null);

    assertThat(status(result)).isEqualTo(200);
    assertThat(modes()).isEqualTo("OFF/INSTANT");
  }

  /// 落地页确认退订「回复我的」：只关回复（不看它原来是每日汇总）。
  @Test
  void confirm_reply_turns_off_only_reply() {
    JsonNode body = json(confirm("REPLY"));

    assertThat(body.get("category").asString()).isEqualTo("REPLY");
    assertThat(body.get("turnedOff").toString()).isEqualTo("[\"REPLY\"]");
    assertThat(body.get("othersEnabled").asBoolean()).isTrue();
    assertThat(modes()).isEqualTo("OFF/INSTANT");
  }

  /// 每日汇总的退订只关在汇总的类别。
  @Test
  void confirm_digest_turns_off_only_digest_categories() {
    JsonNode body = json(confirm("DIGEST"));

    assertThat(body.get("turnedOff").toString()).isEqualTo("[\"REPLY\"]");
    assertThat(body.get("reply").asString()).isEqualTo("OFF");
    assertThat(body.get("mention").asString()).isEqualTo("INSTANT");
    assertThat(modes()).isEqualTo("OFF/INSTANT");
  }

  /// 关闭全部提醒邮件。
  @Test
  void confirm_all_turns_off_everything() {
    JsonNode body = json(confirm("ALL"));

    assertThat(body.get("turnedOff").toString()).isEqualTo("[\"REPLY\",\"MENTION\"]");
    assertThat(body.get("othersEnabled").asBoolean()).isFalse();
    assertThat(modes()).isEqualTo("OFF/OFF");
  }

  /// 各种无效链接：一律 404 + `UNSUBSCRIBE_LINK_INVALID`，档位不动。
  @Test
  void invalid_links_are_404_with_trait() {
    String wrong = (token.charAt(0) == 'A' ? "B" : "A") + token.substring(1);
    assertInvalid(call(HttpMethod.GET, null, "/unsubscribe?category=REPLY", null));
    assertInvalid(read(wrong, "REPLY"));
    assertInvalid(read(token.substring(1), "REPLY"));
    assertInvalid(read(token, "MEN"));
    assertInvalid(read(token, "ALL"));
    assertInvalid(form("/unsubscribe/one-click?token=" + token + "&category=ALL", null));
    assertInvalid(form("/unsubscribe/one-click?token=" + wrong + "&category=REPLY", null));
    assertInvalid(
        call(
            HttpMethod.POST,
            null,
            "/unsubscribe",
            "{\"token\":\"" + wrong + "\",\"category\":\"REPLY\"}"));
    assertThat(modes()).isEqualTo("DIGEST/INSTANT");
  }

  /// 账号已注销：打开落地页就是链接无效。
  @Test
  void deleted_account_link_is_invalid_on_read() {
    identity.resetAll();
    stubRecipient(OWNER, null, "DELETED");

    assertInvalid(read(token, "REPLY"));
  }

  /// identity 挂了：读接口 503；写接口照常退订（不调 identity）。
  @Test
  void read_is_503_when_identity_down_but_write_still_works() {
    identity.resetAll();
    identityDown();

    EntityExchangeResult<String> read = read(token, "REPLY");
    EntityExchangeResult<String> write =
        form("/unsubscribe/one-click?token=" + token + "&category=REPLY", null);

    assertThat(status(read)).isEqualTo(503);
    assertThat(status(write)).isEqualTo(200);
    assertThat(modes()).isEqualTo("OFF/INSTANT");
  }

  /// 一键退订的地址只认 POST：确认页由门户出，不走这里。
  @Test
  void get_on_one_click_is_405() {
    EntityExchangeResult<String> result =
        call(
            HttpMethod.GET,
            null,
            "/unsubscribe/one-click?token=" + token + "&category=REPLY",
            null);

    assertThat(status(result)).isEqualTo(405);
    assertThat(modes()).isEqualTo("DIGEST/INSTANT");
  }

  /// 落地页读接口（匿名）。
  ///
  /// @param linkToken 令牌
  /// @param category 类别
  /// @return 响应
  private EntityExchangeResult<String> read(String linkToken, String category) {
    return call(
        HttpMethod.GET, null, "/unsubscribe?token=" + linkToken + "&category=" + category, null);
  }

  /// 落地页上点按钮（匿名）。
  ///
  /// @param category 类别
  /// @return 响应
  private EntityExchangeResult<String> confirm(String category) {
    EntityExchangeResult<String> result =
        call(
            HttpMethod.POST,
            null,
            "/unsubscribe",
            "{\"token\":\"" + token + "\",\"category\":\"" + category + "\"}");
    assertThat(status(result)).isEqualTo(200);
    return result;
  }

  /// 邮箱服务商发的 multipart 请求体：只有一个字段 `List-Unsubscribe=One-Click`。
  ///
  /// @return 表单
  private static MultiValueMap<String, String> multipartOneClick() {
    MultiValueMap<String, String> parts = new LinkedMultiValueMap<>();
    parts.add("List-Unsubscribe", "One-Click");
    return parts;
  }

  /// 断言是「链接无效」。
  ///
  /// @param result 响应
  private static void assertInvalid(EntityExchangeResult<String> result) {
    assertThat(status(result)).isEqualTo(404);
    JsonNode body = json(result);
    assertThat(body.get("code").asString()).isEqualTo("NOTIFICATION-0404");
    assertThat(body.get("traits").toString()).contains("UNSUBSCRIBE_LINK_INVALID");
  }

  /// 两类档位，形如 `DIGEST/INSTANT`。
  ///
  /// @return 档位
  private String modes() {
    return jdbcTemplate.queryForObject(
        "SELECT reply_mode || '/' || mention_mode FROM preference.email_preference"
            + " WHERE user_id = ?",
        String.class,
        OWNER);
  }

  /// 更新时间。
  ///
  /// @return 更新时间
  private OffsetDateTime updatedAt() {
    return jdbcTemplate.queryForObject(
        "SELECT updated_at FROM preference.email_preference WHERE user_id = ?",
        OffsetDateTime.class,
        OWNER);
  }
}
