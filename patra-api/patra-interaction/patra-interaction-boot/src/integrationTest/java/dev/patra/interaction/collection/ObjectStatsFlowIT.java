package dev.patra.interaction.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// 对象统计（PAP-133）的完整流程：收藏人数随收藏、复制、移出同事务变化；批量接口不要会话、未知 key 不报错。
class ObjectStatsFlowIT extends CollectionFlowITSupport {

  private static final long A = 5001L;
  private static final long B = 5002L;

  /// 两篇文献；A、B 都是正常、已验证、主页公开的用户。
  @BeforeEach
  void seed() {
    CATALOG_DATA.put(100L, "1", "Lancet", "2019");
    CATALOG_DATA.put(101L, "2", "BMJ", "2021-03");
    IDENTITY_DATA.put(A, "ACTIVE", true, "PUBLIC");
    IDENTITY_DATA.put(B, "ACTIVE", true, "PUBLIC");
  }

  /// AC：一人把一篇文献放进两个收藏夹，收藏人数为 1；移出文献库后为 0。
  @Test
  void should_count_collector_once_across_collections() {
    String item = collect(A, 100L);
    addToCollection(A, createCollection(A, "组会"), item);
    addToCollection(A, createCollection(A, "综述"), item);

    assertThat(collectorCount(stats("publication", "100"), 0)).isEqualTo(1);

    call(HttpMethod.DELETE, A, "/me/library/items?itemIds=" + item, null);

    assertThat(collectorCount(stats("publication", "100"), 0)).isZero();
  }

  /// 复制公开收藏夹：B 已收藏的那篇不重复计。
  @Test
  void should_count_only_new_items_when_copying_collection() {
    String paper = collect(A, 100L);
    String other = collect(A, 101L);
    String group = createCollection(A, "组会");
    addToCollection(A, group, paper, other);
    publish(A, group);
    collect(B, 100L);

    assertThat(status(call(HttpMethod.POST, B, "/collections/" + group + "/copies", null)))
        .isEqualTo(201);

    JsonNode items = stats("publication", "100", "101");
    assertThat(collectorCount(items, 0)).isEqualTo(2);
    assertThat(collectorCount(items, 1)).isEqualTo(2);
  }

  /// AC：一次返回一屏的两项统计；没人互动过、格式不对的 key 两项都是 0；按请求顺序、重复的只回一行；不要会话。
  @Test
  void should_return_zero_for_unregistered_and_invalid_keys() {
    collect(A, 101L);

    JsonNode items = stats("publication", "101", "999", "abc", "101");

    assertThat(items.valueStream().map(i -> i.get("key").asString()).toList())
        .containsExactly("101", "999", "abc");
    assertThat(collectorCount(items, 0)).isEqualTo(1);
    assertThat(items.get(0).get("discussionCount").asInt()).isZero();
    for (int i = 1; i < 3; i++) {
      assertThat(collectorCount(items, i)).isZero();
      assertThat(items.get(i).get("discussionCount").asInt()).isZero();
    }
    assertThat(items.get(0).get("collectorCount").isNumber()).isTrue();
  }

  /// 期刊类型、空 key 都 422。
  @Test
  void should_reject_bad_requests() {
    assertThat(status(statsCall("{\"type\":\"journal\",\"keys\":[\"1\"]}"))).isEqualTo(422);
    assertThat(status(statsCall("{\"type\":\"publication\",\"keys\":[]}"))).isEqualTo(422);
    assertThat(status(statsCall("{\"type\":\"publication\",\"keys\":[null]}"))).isEqualTo(422);
  }

  /// 获赞按用户 ID 取、不要会话：B 给 A 的评论点有帮助后 A 是 1；从没被点过的 B 是 0。
  @Test
  void should_expose_helpful_received_without_session() {
    EntityExchangeResult<String> comment =
        call(
            HttpMethod.POST,
            A,
            "/discussions/publication/100/comments",
            "{\"body\":\"根\",\"parentId\":null}");
    assertThat(status(comment)).as(comment.getResponseBody()).isEqualTo(201);
    String commentId = json(comment).get("commentId").asString();
    assertThat(status(call(HttpMethod.PUT, B, "/comments/" + commentId + "/helpful", null)))
        .isEqualTo(200);

    EntityExchangeResult<String> ofA =
        call(HttpMethod.GET, null, "/users/" + A + "/helpful-received", null);
    EntityExchangeResult<String> ofB =
        call(HttpMethod.GET, null, "/users/" + B + "/helpful-received", null);

    assertThat(status(ofA)).isEqualTo(200);
    assertThat(json(ofA).get("helpfulReceived").isNumber()).isTrue();
    assertThat(json(ofA).get("helpfulReceived").asLong()).isEqualTo(1L);
    assertThat(json(ofB).get("helpfulReceived").asLong()).isZero();
  }

  /// 讨论数 = 未删除的评论总数（含楼内回复）：A 发根评论、B 回复后为 2；B 删掉回复后为 1。
  @Test
  void should_count_discussion_including_replies() {
    String root = comment(A, null);
    String reply = comment(B, root);

    assertThat(stats("publication", "100").get(0).get("discussionCount").asInt()).isEqualTo(2);

    assertThat(status(call(HttpMethod.DELETE, B, "/comments/" + reply, null))).isLessThan(300);

    assertThat(stats("publication", "100").get(0).get("discussionCount").asInt()).isEqualTo(1);
  }

  /// 在文献 100 下发一条评论，回它的 ID。
  ///
  /// @param user 作者
  /// @param parentId 父评论，根评论为 `null`
  /// @return 评论 ID
  private String comment(long user, String parentId) {
    String parent = parentId == null ? "null" : "\"" + parentId + "\"";
    EntityExchangeResult<String> result =
        call(
            HttpMethod.POST,
            user,
            "/discussions/publication/100/comments",
            "{\"body\":\"内容\",\"parentId\":" + parent + "}");
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(201);
    return json(result).get("commentId").asString();
  }

  /// 匿名调批量接口，返回 `items`。
  ///
  /// @param type 对象类型
  /// @param keys key
  /// @return `items`
  private JsonNode stats(String type, String... keys) {
    EntityExchangeResult<String> result =
        statsCall(
            "{\"type\":\""
                + type
                + "\",\"keys\":[\""
                + String.join("\",\"", List.of(keys))
                + "\"]}");
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(200);
    return json(result).get("items");
  }

  /// 匿名调批量接口。
  ///
  /// @param body 请求体
  /// @return 响应
  private EntityExchangeResult<String> statsCall(String body) {
    return call(HttpMethod.POST, null, "/subjects/stats", body);
  }

  /// 第 `index` 行的收藏人数。
  ///
  /// @param items `items`
  /// @param index 行号
  /// @return 收藏人数
  private static int collectorCount(JsonNode items, int index) {
    return items.get(index).get("collectorCount").asInt();
  }
}
