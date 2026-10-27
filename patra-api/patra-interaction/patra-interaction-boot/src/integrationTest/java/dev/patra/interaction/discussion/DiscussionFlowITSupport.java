package dev.patra.interaction.discussion;

import dev.patra.interaction.support.InteractionFlowITSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// 讨论流程测试的底座：在共用底座之上加准备文献与用户、发表、读列表的辅助方法。
abstract class DiscussionFlowITSupport extends InteractionFlowITSupport {

  /// 放一篇正常的文献（PMID 是 `1000000 + id`）。
  ///
  /// @param id catalog ID
  protected void publication(long id) {
    CATALOG_DATA.put(id, Long.toString(1_000_000 + id), "J", "2019");
  }

  /// 放几个正常、邮箱已验证、主页公开的用户。
  ///
  /// @param ids 用户 ID
  protected void users(long... ids) {
    for (long id : ids) {
      IDENTITY_DATA.put(id, "ACTIVE", true, "PUBLIC");
    }
  }

  /// 在对象下发表一条评论或回复。先清掉这个人的发表计数：流程测试里同一人要连发多条，绕开 10 秒间隔。
  ///
  /// @param user 作者
  /// @param type 对象类型
  /// @param key 对象 key
  /// @param body 正文
  /// @param parentId 回复的是哪一条，根评论为 `null`
  /// @return 响应
  protected EntityExchangeResult<String> post(
      long user, String type, long key, String body, Long parentId) {
    jdbcTemplate.update("DELETE FROM subject.publish_quota WHERE user_id = ?", user);
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("body", body);
    request.put("parentId", parentId == null ? null : parentId.toString());
    return call(
        HttpMethod.POST,
        user,
        "/discussions/" + type + "/" + key + "/comments",
        JSON.writeValueAsString(request));
  }

  /// 在文献下发表，回新评论的 ID。
  ///
  /// @param user 作者
  /// @param key 文献的 catalog ID
  /// @param body 正文
  /// @param parentId 父评论，根评论为 `null`
  /// @return 评论 ID
  protected long comment(long user, long key, String body, Long parentId) {
    EntityExchangeResult<String> result = post(user, "publication", key, body, parentId);
    if (status(result) != 201) {
      throw new AssertionError("发表失败：" + status(result) + " " + result.getResponseBody());
    }
    return idOf(json(result), "commentId");
  }

  /// 读一个对象的讨论列表。
  ///
  /// @param viewer 访客，匿名为 `null`
  /// @param path `/discussions/…/threads?…`
  /// @return 响应体
  protected JsonNode get(Long viewer, String path) {
    EntityExchangeResult<String> result = call(HttpMethod.GET, viewer, path, null);
    if (status(result) != 200) {
      throw new AssertionError("读取失败：" + status(result) + " " + result.getResponseBody());
    }
    return json(result);
  }

  /// 取一个 ID 字段（JSON 里是字符串）。
  ///
  /// @param node 节点
  /// @param field 字段名
  /// @return ID
  protected static long idOf(JsonNode node, String field) {
    return Long.parseLong(node.get(field).asString());
  }

  /// 一组评论的 ID。
  ///
  /// @param comments 评论数组
  /// @return ID
  protected static List<Long> ids(JsonNode comments) {
    return comments.valueStream().map(c -> idOf(c, "commentId")).toList();
  }

  /// 响应里的原因码。
  ///
  /// @param result 响应
  /// @return 原因码
  protected static List<String> traits(EntityExchangeResult<String> result) {
    return json(result).get("traits").valueStream().map(JsonNode::asString).toList();
  }
}
