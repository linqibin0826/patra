package dev.patra.interaction.support;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// 扮演 identity 的用户内部接口，各子域的流程测试共用；要扮演别的 identity 接口时在这里扩展。
///
/// 一张内存用户表，`profileVisible` 按 identity 的主页规则替访客算：账号不正常或邮箱未验证 → 只有本人；
/// 否则按主页可见性 PUBLIC / SIGNED_IN / SELF。表里没有的 ID 从结果里省略。
///
/// 按请求路径分派：`…/summaries` 批量摘要；`…/resolve/by-usernames` 按用户名解析（当前名不分大小写，再查保留期内
/// 的旧名，回显请求原文）；`…/suggest` 前缀联想（正常、对访客可见、不是访客本人，用户名或昵称以前缀开头）。用户名
/// 默认 `user-{id}`，`rename` 后旧名进保留期，`expireFormerNames` 让旧名过期。
public final class FakeIdentity implements ResponseDefinitionTransformerV2 {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Map<Long, User> users = new ConcurrentHashMap<>();
  private final Map<Long, String> usernames = new ConcurrentHashMap<>();
  private final Map<String, Long> reservedNames = new ConcurrentHashMap<>();
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

  /// 改用户名：旧名进 90 天保留期，期间仍解析到他。
  ///
  /// @param id 用户 ID
  /// @param newUsername 新用户名
  public void rename(long id, String newUsername) {
    reservedNames.put(usernameOf(id), id);
    usernames.put(id, newUsername);
  }

  /// 这个人的旧名都过了保留期。
  ///
  /// @param id 用户 ID
  public void expireFormerNames(long id) {
    reservedNames.values().removeIf(owner -> owner == id);
  }

  /// 改账号状态。
  ///
  /// @param id 用户 ID
  /// @param status 账号状态
  public void setStatus(long id, String status) {
    User user = users.get(id);
    users.put(id, new User(id, status, user.emailVerified(), user.profile()));
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
    usernames.clear();
    reservedNames.clear();
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
    String url = serveEvent.getRequest().getUrl();
    if (url.endsWith("/resolve/by-usernames")) {
      return ResponseDefinition.okForJson(Map.of("users", resolve(body)));
    }
    if (url.endsWith("/suggest")) {
      return ResponseDefinition.okForJson(Map.of("users", suggest(body)));
    }
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

  /// 按用户名解析：当前名不分大小写，再查保留期内的旧名；回显请求原文，原文相同的只出一行。
  ///
  /// @param body 请求体
  /// @return 解析结果
  private List<Map<String, Object>> resolve(JsonNode body) {
    List<Map<String, Object>> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (JsonNode node : body.get("usernames")) {
      String original = node.asString();
      String name = original.toLowerCase(Locale.ROOT);
      Long owner =
          users.keySet().stream()
              .filter(id -> usernameOf(id).equals(name))
              .findFirst()
              .orElse(reservedNames.get(name));
      if (owner != null && seen.add(original)) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("username", original);
        row.put("userId", Long.toString(owner));
        row.put("accountStatus", users.get(owner).status());
        result.add(row);
      }
    }
    return result;
  }

  /// 前缀联想：正常、对访客可见、不是访客本人，用户名或昵称以前缀开头，按用户名排序。
  ///
  /// @param body 请求体
  /// @return 摘要
  private List<Map<String, Object>> suggest(JsonNode body) {
    String prefix = body.get("prefix").asString().toLowerCase(Locale.ROOT);
    long viewer = body.get("viewerUserId").asLong();
    int limit = body.get("limit").asInt();
    return users.values().stream()
        .filter(user -> user.id() != viewer && "ACTIVE".equals(user.status()))
        .map(user -> summary(user, viewer))
        .filter(summary -> Boolean.TRUE.equals(summary.get("profileVisible")))
        .filter(
            summary ->
                ((String) summary.get("username")).startsWith(prefix)
                    || ((String) summary.get("displayName"))
                        .toLowerCase(Locale.ROOT)
                        .startsWith(prefix))
        .sorted(Comparator.comparing(summary -> (String) summary.get("username")))
        .limit(limit)
        .toList();
  }

  /// 当前用户名。
  ///
  /// @param id 用户 ID
  /// @return 用户名
  private String usernameOf(long id) {
    return usernames.getOrDefault(id, "user-" + id);
  }

  /// 一个用户的摘要。
  ///
  /// @param user 用户
  /// @param viewer 访客
  /// @return 摘要
  private Map<String, Object> summary(User user, Long viewer) {
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
    summary.put("username", deleted ? null : usernameOf(user.id()));
    summary.put("displayName", deleted ? null : "用户" + user.id());
    summary.put("avatarUrl", deleted ? null : "/users/" + user.id() + "/avatar?v=abc");
    summary.put("avatarPalette", 3);
    summary.put("accountStatus", user.status());
    summary.put("emailVerified", !deleted && user.emailVerified());
    summary.put("profileVisible", visible);
    return summary;
  }
}
