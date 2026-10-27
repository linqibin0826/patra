package dev.patra.interaction.support;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 扮演 identity 的批量用户摘要接口，各子域的流程测试共用；要扮演别的 identity 接口时在这里扩展。
///
/// 一张内存用户表，`profileVisible` 按 identity 的主页规则替访客算：账号不正常或邮箱未验证 → 只有本人；
/// 否则按主页可见性 PUBLIC / SIGNED_IN / SELF。表里没有的 ID 从结果里省略。
public final class FakeIdentity implements ResponseDefinitionTransformerV2 {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Map<Long, User> users = new ConcurrentHashMap<>();
  private volatile boolean down;

  /// 一个用户。
  ///
  /// @param id 用户 ID
  /// @param status 账号状态
  /// @param emailVerified 邮箱是否已验证
  /// @param profile 主页可见性
  record User(long id, String status, boolean emailVerified, String profile) {}

  /// 放一个用户。
  ///
  /// @param id 用户 ID
  /// @param status 账号状态
  /// @param emailVerified 邮箱是否已验证
  /// @param profile 主页可见性
  public void put(long id, String status, boolean emailVerified, String profile) {
    users.put(id, new User(id, status, emailVerified, profile));
  }

  /// 删掉一个用户（identity 不认识他）。
  ///
  /// @param id 用户 ID
  public void remove(long id) {
    users.remove(id);
  }

  /// 让接口回 503。
  ///
  /// @param down 是否不可用
  public void down(boolean down) {
    this.down = down;
  }

  /// 清空。
  public void clear() {
    users.clear();
    down = false;
  }

  /// 按请求体回摘要。
  ///
  /// @param serveEvent 请求
  /// @return 响应
  @Override
  public ResponseDefinition transform(ServeEvent serveEvent) {
    if (down) {
      return ResponseDefinitionBuilder.responseDefinition()
          .withStatus(503)
          .withHeader("Content-Type", "application/problem+json")
          .withBody(
              "{\"type\":\"about:blank\",\"title\":\"Service Unavailable\",\"status\":503,"
                  + "\"code\":\"IDN-0503\",\"traits\":[\"DEP_UNAVAILABLE\"]}")
          .build();
    }
    JsonNode body = JSON.readTree(serveEvent.getRequest().getBodyAsString());
    JsonNode viewerNode = body.get("viewerUserId");
    Long viewer = viewerNode == null || viewerNode.isNull() ? null : viewerNode.asLong();
    List<Map<String, Object>> result = new ArrayList<>();
    for (JsonNode idNode : body.get("userIds")) {
      User user = users.get(idNode.asLong());
      if (user != null) {
        result.add(summary(user, viewer));
      }
    }
    return ResponseDefinition.okForJson(Map.of("users", result));
  }

  /// 扩展名。
  ///
  /// @return 名字
  @Override
  public String getName() {
    return "fake-identity";
  }

  /// 一个用户的摘要。
  ///
  /// @param user 用户
  /// @param viewer 访客
  /// @return 摘要
  private static Map<String, Object> summary(User user, Long viewer) {
    boolean deleted = "DELETED".equals(user.status());
    boolean self = viewer != null && viewer == user.id();
    boolean normal = "ACTIVE".equals(user.status()) && user.emailVerified();
    boolean visible =
        !deleted
            && (self
                || normal
                    && switch (user.profile()) {
                      case "PUBLIC" -> true;
                      case "SIGNED_IN" -> viewer != null;
                      default -> false;
                    });
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("userId", Long.toString(user.id()));
    summary.put("username", deleted ? null : "user-" + user.id());
    summary.put("displayName", deleted ? null : "用户" + user.id());
    summary.put("avatarUrl", deleted ? null : "/users/" + user.id() + "/avatar?v=abc");
    summary.put("avatarPalette", 3);
    summary.put("accountStatus", user.status());
    summary.put("emailVerified", !deleted && user.emailVerified());
    summary.put("profileVisible", visible);
    return summary;
  }
}
