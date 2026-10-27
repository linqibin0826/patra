package dev.patra.interaction.notification;

import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.support.InteractionFlowITSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/// 提醒流程测试的底座：准备文献、用户、经验，发表 / 编辑 / 删除评论，读提醒。
abstract class NotificationFlowITSupport extends InteractionFlowITSupport {

  @Autowired private SubjectGateway subjects;
  @Autowired private TransactionTemplate transactions;

  /// 放一篇正常的文献（PMID 是 `1000000 + id`）。
  ///
  /// @param id catalog ID
  protected void publication(long id) {
    CATALOG_DATA.put(id, Long.toString(1_000_000 + id), "J", "2019");
  }

  /// 放几个正常、邮箱已验证、主页公开的用户（用户名 `user-{id}`）。
  ///
  /// @param ids 用户 ID
  protected void users(long... ids) {
    for (long id : ids) {
      IDENTITY_DATA.put(id, "ACTIVE", true, "PUBLIC");
    }
  }

  /// 登记一条投稿经验（本地对象 `POST`）。
  ///
  /// @param postId 帖子 ID
  /// @param owner 所有者
  /// @param anonymous 是否匿名
  protected void registerPost(long postId, long owner, boolean anonymous) {
    transactions.executeWithoutResult(
        status ->
            subjects.registerLocal(
                SubjectType.POST, postId, SubjectOwner.of(owner, anonymous), snapshot()));
  }

  /// 把投稿经验标为已删除。
  ///
  /// @param postId 帖子 ID
  protected void deletePost(long postId) {
    transactions.executeWithoutResult(
        status ->
            subjects.updateLocal(SubjectType.POST, postId, SubjectState.UNAVAILABLE, snapshot()));
  }

  /// 发表评论或回复。先清掉这个人的发表计数，绕开 10 秒间隔。
  ///
  /// @param user 作者
  /// @param type 对象类型
  /// @param key 对象 key
  /// @param body 正文
  /// @param parentId 父评论，根评论为 `null`
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

  /// 发表，要求 201，回评论 ID。
  ///
  /// @param user 作者
  /// @param type 对象类型
  /// @param key 对象 key
  /// @param body 正文
  /// @param parentId 父评论，根评论为 `null`
  /// @return 评论 ID
  protected long comment(long user, String type, long key, String body, Long parentId) {
    EntityExchangeResult<String> result = post(user, type, key, body, parentId);
    if (status(result) != 201) {
      throw new AssertionError("发表失败：" + status(result) + " " + result.getResponseBody());
    }
    return Long.parseLong(json(result).get("commentId").asString());
  }

  /// 编辑评论，要求 200。
  ///
  /// @param user 作者
  /// @param commentId 评论
  /// @param body 新正文
  protected void edit(long user, long commentId, String body) {
    EntityExchangeResult<String> result =
        call(
            HttpMethod.PUT,
            user,
            "/comments/" + commentId,
            JSON.writeValueAsString(Map.of("body", body)));
    if (status(result) != 200) {
      throw new AssertionError("编辑失败：" + status(result) + " " + result.getResponseBody());
    }
  }

  /// 删除评论，要求 200。
  ///
  /// @param user 作者
  /// @param commentId 评论
  protected void delete(long user, long commentId) {
    EntityExchangeResult<String> result =
        call(HttpMethod.DELETE, user, "/comments/" + commentId, null);
    if (status(result) != 200) {
      throw new AssertionError("删除失败：" + status(result) + " " + result.getResponseBody());
    }
  }

  /// 读一个标签的第一页。
  ///
  /// @param user 收件人
  /// @param tab `replies` / `mentions`
  /// @return 响应体
  protected JsonNode inbox(long user, String tab) {
    EntityExchangeResult<String> result =
        call(HttpMethod.GET, user, "/me/notifications?tab=" + tab, null);
    if (status(result) != 200) {
      throw new AssertionError("读取失败：" + status(result) + " " + result.getResponseBody());
    }
    return json(result);
  }

  /// 未读数。
  ///
  /// @param user 收件人
  /// @return 响应体
  protected JsonNode unread(long user) {
    return json(call(HttpMethod.GET, user, "/me/notifications/unread-count", null));
  }

  /// 经验的快照（期刊全称、投稿月份、缩写）。
  ///
  /// @return 快照
  protected static SubjectSnapshot snapshot() {
    return SubjectSnapshot.of(null, "Journal of Tests", "2026-03", "J Tests");
  }
}
