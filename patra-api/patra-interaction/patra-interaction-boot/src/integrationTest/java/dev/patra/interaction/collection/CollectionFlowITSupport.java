package dev.patra.interaction.collection;

import dev.patra.interaction.support.InteractionFlowITSupport;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// 收藏与收藏夹完整流程的底座：在共用底座之上加收藏、建夹、放入、设公开四个辅助方法。
abstract class CollectionFlowITSupport extends InteractionFlowITSupport {

  /// 收藏一篇，回 `itemId`。
  ///
  /// @param user 用户
  /// @param key catalog ID
  /// @return 条目 ID
  protected String collect(long user, long key) {
    return json(call(HttpMethod.PUT, user, "/me/library/publication/" + key, null))
        .get("item")
        .get("itemId")
        .asString();
  }

  /// 建收藏夹，回 `collectionId`。
  ///
  /// @param user 用户
  /// @param name 名称
  /// @return 收藏夹 ID
  protected String createCollection(long user, String name) {
    return json(call(
            HttpMethod.POST,
            user,
            "/me/collections",
            "{\"name\":\"" + name + "\",\"description\":\"每周四\"}"))
        .get("collectionId")
        .asString();
  }

  /// 放进收藏夹。
  ///
  /// @param user 用户
  /// @param collectionId 收藏夹
  /// @param itemIds 条目
  /// @return 响应
  protected JsonNode addToCollection(long user, String collectionId, String... itemIds) {
    String ids = String.join("\",\"", itemIds);
    return json(
        call(
            HttpMethod.POST,
            user,
            "/me/collections/" + collectionId + "/items",
            "{\"itemIds\":[\"" + ids + "\"]}"));
  }

  /// 设为公开。
  ///
  /// @param user 用户
  /// @param collectionId 收藏夹
  /// @return 响应
  protected EntityExchangeResult<String> publish(long user, String collectionId) {
    return call(
        HttpMethod.PUT,
        user,
        "/me/collections/" + collectionId + "/visibility",
        "{\"visibility\":\"PUBLIC\"}");
  }
}
