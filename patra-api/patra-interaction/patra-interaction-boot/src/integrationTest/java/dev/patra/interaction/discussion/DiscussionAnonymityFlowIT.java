package dev.patra.interaction.discussion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/// 匿名经验下的讨论（PAP-121 的 AC）：匿名所有者的用户 ID 不出现在任何对他人的响应里，他的评论显示「匿名作者」并带
/// 「作者」标记，回复他的标「回复 匿名作者」；他本人看得到自己的名片；@ 联想不列他。非匿名所有者在联想里排第一。
class DiscussionAnonymityFlowIT extends DiscussionFlowITSupport {

  private static final long OWNER = 770001L;
  private static final long VISIBLE_OWNER = 770002L;
  private static final long B = 1002L;
  private static final long C = 1003L;
  private static final long ANONYMOUS_POST = 5001L;
  private static final long OPEN_POST = 5002L;

  @Autowired private SubjectGateway subjects;
  @Autowired private TransactionTemplate transactions;

  @BeforeEach
  void world() {
    users(OWNER, VISIBLE_OWNER, B, C);
    transactions.executeWithoutResult(
        status -> {
          subjects.registerLocal(
              SubjectType.POST, ANONYMOUS_POST, SubjectOwner.of(OWNER, true), snapshot());
          subjects.registerLocal(
              SubjectType.POST, OPEN_POST, SubjectOwner.of(VISIBLE_OWNER, false), snapshot());
        });
  }

  /// B 发根评论、匿名所有者回复 B、C 回复他：别人与匿名访客取到的每个响应里都没有他的用户 ID。
  @Test
  void should_never_expose_anonymous_owner_to_others() {
    long root = postComment(B, "审稿多久？", null);
    long ownerReply = postComment(OWNER, "六周", root);
    EntityExchangeResult<String> cReply = post(C, "post", ANONYMOUS_POST, "谢谢", ownerReply);
    long cId = idOf(json(cReply), "commentId");

    String threads = "/discussions/post/" + ANONYMOUS_POST + "/threads";
    List<String> bodies =
        List.of(
            cReply.getResponseBody(),
            body(B, threads),
            body(C, threads),
            body(null, threads),
            body(C, "/discussions/post/" + ANONYMOUS_POST + "/comments/" + cId + "/thread"),
            body(null, "/comments/" + cId + "/conversation"),
            body(B, "/discussions/post/" + ANONYMOUS_POST + "/mention-suggestions"),
            body(B, "/discussions/post/" + ANONYMOUS_POST + "/mention-suggestions?q=user-100"),
            body(null, "/comments/" + ownerReply + "/revisions"));
    assertThat(bodies).allSatisfy(json -> assertThat(json).doesNotContain(Long.toString(OWNER)));

    JsonNode replies = get(B, threads).get("items").get(0).get("replies");
    JsonNode owners = replies.get(0);
    assertThat(owners.get("anonymous").asBoolean()).isTrue();
    assertThat(owners.get("isOwner").asBoolean()).isTrue();
    assertThat(owners.get("author").isNull()).isTrue();
    assertThat(replies.get(1).get("replyTo").get("anonymous").asBoolean()).isTrue();
    assertThat(
            get(B, "/discussions/post/" + ANONYMOUS_POST + "/mention-suggestions")
                .get("items")
                .valueStream()
                .map(item -> idOf(item, "userId")))
        .containsExactly(C);
  }

  /// 带查询词时，identity 的全站联想（`OTHER`）照常可能列出所有者的真实账号：那是全站搜索，与这条匿名经验无关；只在
  /// 他自己的匿名经验下把他剔掉，「缺席」反而会暴露谁是匿名作者。他永远不会作为本讨论的参与者出现。
  @Test
  void should_list_anonymous_owner_only_as_a_global_search_result() {
    long root = postComment(B, "审稿多久？", null);
    postComment(OWNER, "六周", root);

    JsonNode items =
        get(B, "/discussions/post/" + ANONYMOUS_POST + "/mention-suggestions?q=user").get("items");

    assertThat(items.valueStream().filter(item -> idOf(item, "userId") == OWNER))
        .allSatisfy(item -> assertThat(item.get("group").asString()).isEqualTo("OTHER"));
    assertThat(
            items
                .valueStream()
                .filter(item -> "PARTICIPANT".equals(item.get("group").asString()))
                .map(item -> idOf(item, "userId")))
        .doesNotContain(OWNER);
  }

  /// 匿名所有者本人看自己的评论：「这条是我发的」，作者是他自己的名片。
  @Test
  void should_show_owner_their_own_anonymous_comments() {
    postComment(OWNER, "补充一下", null);

    JsonNode own =
        get(OWNER, "/discussions/post/" + ANONYMOUS_POST + "/threads")
            .get("items")
            .get(0)
            .get("root");

    assertThat(own.get("mine").asBoolean()).isTrue();
    assertThat(own.get("anonymous").asBoolean()).isTrue();
    assertThat(idOf(own.get("author"), "userId")).isEqualTo(OWNER);
  }

  /// 非匿名所有者没评论过，也排在联想的第一位。
  @Test
  void should_list_non_anonymous_owner_first_in_suggestions() {
    post(C, "post", OPEN_POST, "有个问题", null);

    JsonNode items = get(B, "/discussions/post/" + OPEN_POST + "/mention-suggestions").get("items");

    assertThat(items.valueStream().map(item -> idOf(item, "userId")))
        .containsExactly(VISIBLE_OWNER, C);
    assertThat(items.get(0).get("group").asString()).isEqualTo("PARTICIPANT");
  }

  /// 在匿名经验下发表，回评论 ID。
  ///
  /// @param user 作者
  /// @param body 正文
  /// @param parentId 父评论，根评论为 `null`
  /// @return 评论 ID
  private long postComment(long user, String body, Long parentId) {
    EntityExchangeResult<String> result = post(user, "post", ANONYMOUS_POST, body, parentId);
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(201);
    return idOf(json(result), "commentId");
  }

  /// 取一个接口的原始响应体（200）。
  ///
  /// @param viewer 访客，匿名为 `null`
  /// @param path 路径
  /// @return 响应体
  private String body(Long viewer, String path) {
    EntityExchangeResult<String> result = call(HttpMethod.GET, viewer, path, null);
    assertThat(status(result)).as(path + " " + result.getResponseBody()).isEqualTo(200);
    return result.getResponseBody();
  }

  /// 经验的快照（期刊全称、投稿月份）。
  ///
  /// @return 快照
  private static SubjectSnapshot snapshot() {
    return SubjectSnapshot.of(null, "Journal of Tests", "2026-03", "J Tests");
  }
}
