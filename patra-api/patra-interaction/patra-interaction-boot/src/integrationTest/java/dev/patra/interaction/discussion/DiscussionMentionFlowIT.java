package dev.patra.interaction.discussion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// @ 的解析（PAP-121 的 AC）：当前名与保留期内旧名换成标记；非正常账号、解析不到的保持纯文本；超过 10 人 422；代码与
/// 网址里不动、伪造的标记被转义；编辑原文换回当前用户名，原样保存提及不丢；主页不可见的人不给用户名。
class DiscussionMentionFlowIT extends DiscussionFlowITSupport {

  private static final long A = 1001L;
  private static final long B = 1002L;
  private static final long C = 1003L;
  private static final long D = 1004L;
  private static final long E = 1005L;
  private static final long PAPER = 100L;

  @BeforeEach
  void world() {
    publication(PAPER);
    users(A, B, C, D, E);
  }

  /// 当前名、保留期内的旧名换成标记；封禁、不存在、旧名已过期的保持纯文本。
  @Test
  void should_resolve_current_and_reserved_names_for_active_users_only() {
    IDENTITY_DATA.rename(C, "c-new");
    IDENTITY_DATA.setStatus(D, "BANNED");
    IDENTITY_DATA.rename(E, "e-new");
    IDENTITY_DATA.expireFormerNames(E);

    JsonNode comment =
        json(
            post(
                A,
                "publication",
                PAPER,
                "@user-1002 @user-1003 @user-1004 @nobody @user-1005",
                null));

    assertThat(comment.get("body").asString())
        .isEqualTo("<@1002> <@1003> @user-1004 @nobody @user-1005");
    assertThat(comment.get("mentions").valueStream().map(m -> idOf(m, "userId")))
        .containsExactly(B, C);
    assertThat(comment.get("mentions").get(1).get("username").asString()).isEqualTo("c-new");
  }

  /// 11 个不同的正常账号：422。
  @Test
  void should_reject_more_than_ten_people_with_422() {
    LongStream.rangeClosed(2001, 2011).forEach(id -> users(id));
    String body =
        LongStream.rangeClosed(2001, 2011)
            .mapToObj(id -> "@user-" + id)
            .collect(Collectors.joining(" "));

    EntityExchangeResult<String> result = post(A, "publication", PAPER, body, null);

    assertThat(status(result)).isEqualTo(422);
    assertThat(json(result).get("errors").get(0).get("code").asString())
        .isEqualTo("TOO_MANY_MENTIONS");
  }

  /// 代码与网址里的 @ 不动；客户端发来的标记被转义成字面文字。
  @Test
  void should_leave_code_and_urls_alone_and_escape_forged_tokens() {
    JsonNode comment =
        json(post(A, "publication", PAPER, "`@user-1002` https://x.org/@user-1002 <@99>", null));

    assertThat(comment.get("body").asString())
        .isEqualTo("`@user-1002` https://x.org/@user-1002 \\<@99>");
    assertThat(comment.get("mentions")).isEmpty();
  }

  /// 编辑原文换回当前用户名；被 @ 的人之后被封禁，原样保存提及不丢；注销后写成「@已注销用户」。
  @Test
  void should_give_source_with_current_usernames_and_keep_mentions_on_save() {
    long id = comment(A, PAPER, "@user-1002 和 @user-1004 你好", null);
    IDENTITY_DATA.rename(B, "b-new");

    String source = sourceOf(id);
    assertThat(source).isEqualTo("@b-new 和 @user-1004 你好");

    IDENTITY_DATA.setStatus(D, "BANNED");
    EntityExchangeResult<String> saved =
        call(HttpMethod.PUT, A, "/comments/" + id, JSON.writeValueAsString(Map.of("body", source)));
    assertThat(status(saved)).isEqualTo(200);
    assertThat(json(saved).get("body").asString()).isEqualTo("<@1002> 和 <@1004> 你好");

    IDENTITY_DATA.setStatus(D, "DELETED");
    assertThat(sourceOf(id)).isEqualTo("@b-new 和 @已注销用户 你好");
  }

  /// 主页对访客不可见的人：提及里没有用户名，只有昵称。
  @Test
  void should_hide_username_of_invisible_mentions() {
    IDENTITY_DATA.put(B, "ACTIVE", true, "SELF");
    comment(A, PAPER, "@user-1002 看看", null);

    JsonNode mention =
        get(C, "/discussions/publication/" + PAPER + "/threads")
            .get("items")
            .get(0)
            .get("root")
            .get("mentions")
            .get(0);

    assertThat(mention.get("username").isNull()).isTrue();
    assertThat(mention.get("displayName").asString()).isEqualTo("用户1002");
  }

  /// 取编辑原文。
  ///
  /// @param id 评论
  /// @return 原文
  private String sourceOf(long id) {
    EntityExchangeResult<String> result =
        call(HttpMethod.GET, A, "/comments/" + id + "/source", null);
    assertThat(status(result)).isEqualTo(200);
    return json(result).get("body").asString();
  }
}
