package dev.patra.interaction.collection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.app.subject.usecase.cleanup.CleanupDeletedUsersCommand;
import dev.patra.interaction.app.subject.usecase.reconcile.ReconcileSubjectsCommand;
import dev.patra.tool.cqrs.CommandBus;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// 收藏与收藏夹的完整流程（PAP-113 的全部 AC）：经真实路由、真实验签、真实 PG。
class CollectionFlowIT extends CollectionFlowITSupport {

  private static final long A = 1001L;
  private static final long B = 1002L;

  @Autowired private CommandBus commandBus;

  /// 三篇文献；A、B 都是正常、已验证、主页公开的用户。
  @BeforeEach
  void seed() {
    CATALOG_DATA.put(100L, "1", "Lancet", "2019");
    CATALOG_DATA.put(200L, "2", "BMJ", "2021-03");
    CATALOG_DATA.put(300L, "3", "Cell", "2020");
    IDENTITY_DATA.put(A, "ACTIVE", true, "PUBLIC");
    IDENTITY_DATA.put(B, "ACTIVE", true, "PUBLIC");
  }

  /// AC：重复收藏不产生重复条目，重复取消不报错。
  @Test
  void should_collect_idempotently_and_remove_idempotently() {
    EntityExchangeResult<String> first =
        call(HttpMethod.PUT, A, "/me/library/publication/100", null);
    EntityExchangeResult<String> second =
        call(HttpMethod.PUT, A, "/me/library/publication/100", null);

    assertThat(status(first)).isEqualTo(200);
    assertThat(json(first).get("created").asBoolean()).isTrue();
    assertThat(json(second).get("created").asBoolean()).isFalse();
    assertThat(json(first).get("item").get("itemId").isString()).isTrue();
    String itemId = json(first).get("item").get("itemId").asString();
    assertThat(json(second).get("item").get("itemId").asString()).isEqualTo(itemId);
    assertThat(json(first).get("item").get("key").asString()).isEqualTo("100");
    assertThat(count("SELECT count(*) FROM collection.library_item")).isEqualTo(1L);

    assertThat(status(call(HttpMethod.DELETE, A, "/me/library/items?itemIds=" + itemId, null)))
        .isEqualTo(204);
    assertThat(status(call(HttpMethod.DELETE, A, "/me/library/items?itemIds=" + itemId, null)))
        .isEqualTo(204);
    assertThat(json(call(HttpMethod.GET, A, "/me/library", null)).get("total").asInt()).isZero();
  }

  /// AC：删收藏夹不删条目；删条目时同时移出所有收藏夹。
  @Test
  void should_keep_items_when_collection_deleted_and_remove_from_all_on_uncollect() {
    String i100 = collect(A, 100);
    String i200 = collect(A, 200);
    collect(A, 300);
    String group = createCollection(A, "组会");
    String review = createCollection(A, "综述");
    addToCollection(A, group, i100, i200);
    addToCollection(A, review, i100);

    assertThat(status(call(HttpMethod.DELETE, A, "/me/collections/" + review, null)))
        .isEqualTo(204);
    JsonNode overview = json(call(HttpMethod.GET, A, "/me/library", null));
    assertThat(overview.get("total").asInt()).isEqualTo(3);
    assertThat(overview.get("unfiled").asInt()).isEqualTo(1);
    assertThat(overview.get("collections")).hasSize(1);

    call(HttpMethod.DELETE, A, "/me/library/items?itemIds=" + i100, null);

    assertThat(
            json(call(HttpMethod.GET, A, "/me/library", null))
                .get("collections")
                .get(0)
                .get("itemCount")
                .asInt())
        .isEqualTo(1);
    JsonNode items = json(call(HttpMethod.GET, A, "/me/collections/" + group + "/items", null));
    assertThat(items.get("total").asInt()).isEqualTo(1);
    assertThat(items.get("items").get(0).get("itemId").asString()).isEqualTo(i200);
    assertThat(items.get("items").get(0).get("addedAt").isNull()).isFalse();
    assertThat(
            count(
                "SELECT count(*) FROM collection.collection_item WHERE subject_id = ?",
                Long.parseLong(i100)))
        .isZero();
  }

  /// AC：邮箱未验证的账号设为公开被拒；账号异常、identity 不可用各有语义。
  @Test
  void should_gate_publishing_by_identity_state() {
    String group = createCollection(A, "组会");

    IDENTITY_DATA.put(A, "ACTIVE", false, "PUBLIC");
    assertRejected(publish(A, group), 409, "EMAIL_UNVERIFIED");
    IDENTITY_DATA.put(A, "BANNED", false, "PUBLIC");
    assertRejected(publish(A, group), 409, "ACCOUNT_RESTRICTED");
    IDENTITY_DATA.put(A, "PENDING_DELETION", true, "PUBLIC");
    assertRejected(publish(A, group), 409, "ACCOUNT_RESTRICTED");
    IDENTITY_DATA.put(A, "ACTIVE", true, "PUBLIC");
    IDENTITY_DATA.down(true);
    assertThat(status(publish(A, group))).isEqualTo(503);
    IDENTITY_DATA.down(false);

    EntityExchangeResult<String> ok = publish(A, group);
    assertThat(status(ok)).isEqualTo(200);
    assertThat(json(ok).get("visibility").asString()).isEqualTo("PUBLIC");
  }

  /// AC：所有者主页不可见时公开收藏夹对访客 404（覆盖 identity 返回的各种状态）；本人能读私有夹。
  @Test
  void should_404_public_collection_when_owner_profile_hidden() {
    String group = createCollection(A, "组会");
    String secret = createCollection(A, "私房");
    publish(A, group);
    String page = "/collections/" + group;

    JsonNode header = json(call(HttpMethod.GET, null, page, null));
    assertThat(header.get("owner").get("username").asString()).isEqualTo("user-1001");
    assertThat(header.get("owner").get("deleted").asBoolean()).isFalse();
    assertThat(header.get("viewerIsOwner").asBoolean()).isFalse();
    assertThat(
            json(call(HttpMethod.GET, null, "/users/" + A + "/collections", null))
                .get("collections"))
        .hasSize(1);

    IDENTITY_DATA.put(A, "ACTIVE", true, "SIGNED_IN");
    assertThat(status(call(HttpMethod.GET, null, page, null))).isEqualTo(404);
    assertThat(status(call(HttpMethod.GET, B, page, null))).isEqualTo(200);

    IDENTITY_DATA.put(A, "ACTIVE", true, "SELF");
    assertThat(status(call(HttpMethod.GET, B, page, null))).isEqualTo(404);
    assertThat(status(call(HttpMethod.GET, B, page + "/items", null))).isEqualTo(404);
    assertThat(status(call(HttpMethod.GET, B, page + "/keys", null))).isEqualTo(404);
    assertThat(
            json(call(HttpMethod.GET, B, "/users/" + A + "/collections", null)).get("collections"))
        .isEmpty();
    assertThat(json(call(HttpMethod.GET, A, page, null)).get("viewerIsOwner").asBoolean()).isTrue();

    for (String state : List.of("BANNED", "PENDING_DELETION", "DELETED")) {
      IDENTITY_DATA.put(A, state, true, "PUBLIC");
      assertThat(status(call(HttpMethod.GET, B, page, null))).as(state).isEqualTo(404);
    }
    IDENTITY_DATA.put(A, "ACTIVE", false, "PUBLIC");
    assertThat(status(call(HttpMethod.GET, B, page, null))).isEqualTo(404);
    IDENTITY_DATA.remove(A);
    assertThat(status(call(HttpMethod.GET, B, page, null))).isEqualTo(404);
    IDENTITY_DATA.down(true);
    assertThat(status(call(HttpMethod.GET, B, page, null))).isEqualTo(503);
    EntityExchangeResult<String> ownDuringOutage = call(HttpMethod.GET, A, page, null);
    assertThat(status(ownDuringOutage)).isEqualTo(200);
    assertThat(json(ownDuringOutage).get("owner").isNull()).isTrue();
    assertThat(json(ownDuringOutage).get("viewerIsOwner").asBoolean()).isTrue();
    assertThat(status(call(HttpMethod.GET, A, page + "/items", null))).isEqualTo(200);
    IDENTITY_DATA.down(false);
    IDENTITY_DATA.put(A, "ACTIVE", true, "PUBLIC");

    assertThat(status(call(HttpMethod.GET, B, "/collections/" + secret, null))).isEqualTo(404);
    assertThat(
            json(call(HttpMethod.GET, A, "/collections/" + secret, null))
                .get("visibility")
                .asString())
        .isEqualTo("PRIVATE");
  }

  /// 复制别人的公开收藏夹：重名加序号、已在库里的不重复、失效的跳过、备注不复制。
  @Test
  void should_copy_public_collection_with_counts() {
    String i100 = collect(A, 100);
    String i200 = collect(A, 200);
    String i300 = collect(A, 300);
    call(HttpMethod.PUT, A, "/me/library/items/" + i100 + "/note", "{\"note\":\"A 的备注\"}");
    String group = createCollection(A, "组会");
    addToCollection(A, group, i100, i200, i300);
    publish(A, group);
    CATALOG_DATA.softDelete(300L);
    commandBus.handle(ReconcileSubjectsCommand.of());
    EntityExchangeResult<String> again =
        call(HttpMethod.PUT, A, "/me/library/publication/300", null);
    assertThat(status(again)).isEqualTo(200);
    assertThat(json(again).get("created").asBoolean()).isFalse();
    assertThat(json(again).get("item").get("state").asString()).isEqualTo("UNAVAILABLE");
    collect(B, 200);
    createCollection(B, "组会");

    EntityExchangeResult<String> copied =
        call(HttpMethod.POST, B, "/collections/" + group + "/copies", null);

    assertThat(status(copied)).isEqualTo(201);
    JsonNode result = json(copied);
    assertThat(result.get("name").asString()).isEqualTo("组会 (2)");
    assertThat(result.get("addedToLibrary").asInt()).isEqualTo(1);
    assertThat(result.get("alreadyInLibrary").asInt()).isEqualTo(1);
    assertThat(result.get("skippedUnavailable").asInt()).isEqualTo(1);
    JsonNode items =
        json(
            call(
                HttpMethod.GET,
                B,
                "/me/collections/" + result.get("collectionId").asString() + "/items",
                null));
    assertThat(items.get("total").asInt()).isEqualTo(2);
    List<String> order = new ArrayList<>();
    items.get("items").forEach(i -> order.add(i.get("itemId").asString()));
    assertThat(order).containsExactly(i100, i200);
    assertThat(items.get("items").get(0).get("note").isNull()).isTrue();
  }

  /// AC：注销清理任务删除 DELETED 用户的全部收藏与收藏夹，不影响别人复制的收藏夹。
  @Test
  void should_clean_up_deleted_user_without_touching_copies() {
    String i100 = collect(A, 100);
    String group = createCollection(A, "组会");
    addToCollection(A, group, i100);
    publish(A, group);
    String copy =
        json(call(HttpMethod.POST, B, "/collections/" + group + "/copies", null))
            .get("collectionId")
            .asString();
    IDENTITY_DATA.put(A, "DELETED", false, "PUBLIC");

    commandBus.handle(CleanupDeletedUsersCommand.of());

    for (String table : List.of("library_item", "collection", "collection_item")) {
      assertThat(count("SELECT count(*) FROM collection." + table + " WHERE user_id = ?", A))
          .as(table)
          .isZero();
    }
    assertThat(
            count(
                "SELECT count(*) FROM collection.collection WHERE collection_id = ?",
                Long.parseLong(copy)))
        .isEqualTo(1L);
    assertThat(count("SELECT count(*) FROM collection.collection_item WHERE user_id = ?", B))
        .isEqualTo(1L);
    assertThat(count("SELECT count(*) FROM collection.library_item WHERE user_id = ?", B))
        .isEqualTo(1L);
  }

  /// AC：「我的状态」一次请求完成 100 个对象的判定；101 个 422。
  @Test
  void should_report_status_for_100_keys_in_one_request() {
    String i100 = collect(A, 100);
    call(HttpMethod.PUT, A, "/me/library/items/" + i100 + "/note", "{\"note\":\"讲\"}");
    addToCollection(A, createCollection(A, "组会"), i100);
    addToCollection(A, createCollection(A, "综述"), i100);
    List<String> keys = new ArrayList<>(List.of("100", "x", "200"));
    IntStream.range(0, 97).forEach(i -> keys.add(Integer.toString(9000 + i)));

    EntityExchangeResult<String> result =
        call(HttpMethod.POST, A, "/me/library/status", statusBody(keys));

    assertThat(status(result)).isEqualTo(200);
    JsonNode items = json(result).get("items");
    assertThat(items).hasSize(100);
    assertThat(items.get(0).get("collected").asBoolean()).isTrue();
    assertThat(items.get(0).get("itemId").asString()).isEqualTo(i100);
    assertThat(items.get(0).get("collectionIds")).hasSize(2);
    assertThat(items.get(0).get("hasNote").asBoolean()).isTrue();
    assertThat(items.get(1).get("collected").asBoolean()).isFalse();
    assertThat(items.get(2).get("collected").asBoolean()).isFalse();

    keys.add("9999");
    assertThat(status(call(HttpMethod.POST, A, "/me/library/status", statusBody(keys))))
        .isEqualTo(422);
  }

  /// AC：触达上限时返回明确错误。
  @Test
  void should_return_clear_errors_at_limits() {
    collect(A, 100);
    jdbcTemplate.update(
        "INSERT INTO collection.library_item (user_id, subject_id, collected_at)"
            + " SELECT ?, g, now() FROM generate_series(1, 19999) g",
        A);
    assertRejected(
        call(HttpMethod.PUT, A, "/me/library/publication/200", null), 409, "LIBRARY_FULL");

    jdbcTemplate.update(
        "INSERT INTO collection.collection (collection_id, user_id, name, visibility)"
            + " SELECT g, ?, 'c' || g, 'PRIVATE' FROM generate_series(1, 200) g",
        B);
    assertRejected(
        call(HttpMethod.POST, B, "/me/collections", "{\"name\":\"新的\"}"),
        409,
        "COLLECTION_LIMIT_REACHED");

    long c = 1003L;
    createCollection(c, "组会");
    EntityExchangeResult<String> taken =
        call(HttpMethod.POST, c, "/me/collections", "{\"name\":\" 组会 \"}");
    assertRejected(taken, 409, "COLLECTION_NAME_TAKEN");
    assertThat(json(taken).get("errors").get(0).get("field").asString()).isEqualTo("name");
    assertThat(json(taken).get("errors").get(0).get("code").asString()).isEqualTo("TAKEN");
    EntityExchangeResult<String> tooLong =
        call(HttpMethod.POST, c, "/me/collections", "{\"name\":\"" + "字".repeat(101) + "\"}");
    assertThat(status(tooLong)).isEqualTo(422);
    assertThat(json(tooLong).get("errors").get(0).get("code").asString()).isEqualTo("TOO_LONG");
  }

  /// catalog 里整行消失的条目：没有 key、显示快照标题，仍能按 itemId 改备注、移除，不进导出 key。
  @Test
  void should_manage_vanished_item_by_item_id() {
    String i100 = collect(A, 100);
    collect(A, 200);
    CATALOG_DATA.remove(100L);
    commandBus.handle(ReconcileSubjectsCommand.of());

    JsonNode items = json(call(HttpMethod.GET, A, "/me/library/items", null)).get("items");
    JsonNode vanished = items.get(1);
    assertThat(vanished.get("itemId").asString()).isEqualTo(i100);
    assertThat(vanished.get("key").isNull()).isTrue();
    assertThat(vanished.get("state").asString()).isEqualTo("UNAVAILABLE");
    assertThat(vanished.get("title").asString()).isEqualTo("Title 1");
    assertThat(items.get(0).get("title").isNull()).isTrue();

    EntityExchangeResult<String> noted =
        call(HttpMethod.PUT, A, "/me/library/items/" + i100 + "/note", "{\"note\":\"还在\"}");
    assertThat(status(noted)).isEqualTo(200);
    assertThat(json(noted).get("note").asString()).isEqualTo("还在");
    JsonNode keys = json(call(HttpMethod.GET, A, "/me/library/keys", null));
    assertThat(keys.get("keys")).hasSize(1);
    assertThat(keys.get("keys").get(0).asString()).isEqualTo("200");
    assertThat(keys.get("skippedUnavailable").asInt()).isEqualTo(1);
    assertThat(status(call(HttpMethod.PUT, A, "/me/library/publication/100", null))).isEqualTo(404);
    assertThat(status(call(HttpMethod.DELETE, A, "/me/library/items?itemIds=" + i100, null)))
        .isEqualTo(204);
  }

  /// 我的接口要登录。
  @Test
  void should_require_login_for_my_endpoints() {
    assertThat(status(call(HttpMethod.GET, null, "/me/library", null))).isEqualTo(401);
    assertThat(status(call(HttpMethod.PUT, null, "/me/library/publication/100", null)))
        .isEqualTo(401);
  }

  /// 参数越界与未知取值是 422；页码不是数字是 400。
  @Test
  void should_reject_bad_query_params() {
    assertThat(status(call(HttpMethod.GET, A, "/me/library/items?size=101", null))).isEqualTo(422);
    assertThat(status(call(HttpMethod.GET, A, "/me/library/items?page=0", null))).isEqualTo(422);
    assertThat(status(call(HttpMethod.GET, A, "/me/library/items?sort=title", null)))
        .isEqualTo(422);
    assertThat(status(call(HttpMethod.GET, A, "/me/library/items?page=x", null))).isEqualTo(400);
    assertThat(status(call(HttpMethod.DELETE, A, "/me/library/items", null))).isEqualTo(422);
    assertThat(status(call(HttpMethod.PUT, A, "/me/library/journal/100", null))).isEqualTo(422);
    assertThat(status(call(HttpMethod.PUT, A, "/me/library/publication/abc", null))).isEqualTo(422);
  }

  /// 断言被拒的状态码与原因码。
  ///
  /// @param result 响应
  /// @param status 状态码
  /// @param reason 原因码
  private static void assertRejected(
      EntityExchangeResult<String> result, int status, String reason) {
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(status);
    List<String> traits = new ArrayList<>();
    json(result).get("traits").forEach(t -> traits.add(t.asString()));
    assertThat(traits).contains(reason);
  }

  /// 「我的状态」请求体。
  ///
  /// @param keys key
  /// @return JSON
  private static String statusBody(List<String> keys) {
    return "{\"type\":\"publication\",\"keys\":[\"" + String.join("\",\"", keys) + "\"]}";
  }
}
