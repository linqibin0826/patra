package dev.patra.analytics.infra.insight.support;

import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.event;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.infra.ingest.support.ClickHouseITFixture;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.node.ObjectNode;

/// insight 集成测试的事件构造：一个「人」的一个会话里读了哪些文献、什么时候读的。
public final class InsightEvents {

  private final List<ObjectNode> rows = new ArrayList<>();

  /// 开始造一批事件。
  ///
  /// @return 构造器
  public static InsightEvents batch() {
    return new InsightEvents();
  }

  /// 一个真人会话（有一条浏览器的页面浏览）里，某人在某时刻读了一篇。
  ///
  /// @param userId 登录用户 ID（「人」）
  /// @param eventName 事件名
  /// @param articleId 文献 ID
  /// @param at 事件时间（UTC）
  /// @return 本构造器
  public InsightEvents read(long userId, String eventName, long articleId, Instant at) {
    return session(
        userId, UUID.randomUUID().toString(), false, false, true, eventName, articleId, at);
  }

  /// 在指定会话里造一条事件，并按需加一条同会话的浏览器页面浏览。
  ///
  /// @param userId 用户
  /// @param sessionId 会话
  /// @param bot 这条事件是否标为爬虫
  /// @param synthetic 是否合成
  /// @param withClient 是否同时造一条浏览器的页面浏览
  /// @param eventName 事件名
  /// @param articleId 文献
  /// @param at 时间
  /// @return 本构造器
  public InsightEvents session(
      long userId,
      String sessionId,
      boolean bot,
      boolean synthetic,
      boolean withClient,
      String eventName,
      long articleId,
      Instant at) {
    ObjectNode read =
        event(
            node -> {
              node.put("event_name", eventName);
              node.put("user_id", String.valueOf(userId));
              node.put("session_id", sessionId);
              node.put("is_bot", bot);
              node.put("is_synthetic", synthetic);
              node.put("derived_at", at.toString());
              node.putObject("properties").put("article_id", String.valueOf(articleId));
            });
    rows.add(read);
    if (withClient) {
      ObjectNode view = ClickHouseITFixture.pageViewIn(read);
      view.put("is_synthetic", synthetic);
      view.put("derived_at", at.toString());
      rows.add(view);
    }
    return this;
  }

  /// 原样加一行事件。
  ///
  /// @param row 富化事件
  /// @return 本构造器
  public InsightEvents raw(ObjectNode row) {
    rows.add(row);
    return this;
  }

  /// 写进 `events`（一次写入、一个新令牌）。
  ///
  /// @param client 客户端
  public void insertInto(Client client) {
    ClickHouseITFixture.insert(client, "it-" + UUID.randomUUID(), rows);
  }
}
