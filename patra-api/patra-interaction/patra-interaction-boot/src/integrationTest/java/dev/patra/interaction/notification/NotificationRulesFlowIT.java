package dev.patra.interaction.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/// 提醒规则的端到端流程（AC 1）：评论接口 → 事件 → 提醒表 → 提醒接口。
class NotificationRulesFlowIT extends NotificationFlowITSupport {

  private static final long A = 1001L;
  private static final long B = 1002L;
  private static final long C = 1003L;
  private static final long OWNER = 770001L;
  private static final long PAPER = 100L;
  private static final long POST = 5001L;

  @BeforeEach
  void world() {
    publication(PAPER);
    users(A, B, C, OWNER);
  }

  /// 自己回复自己、@ 自己：没有提醒。
  @Test
  void should_not_notify_when_replying_to_self() {
    long root = comment(A, "publication", PAPER, "根", null);
    comment(A, "publication", PAPER, "补充 @user-1001", root);

    assertThat(unread(A).get("total").asInt()).isZero();
  }

  /// B 回复 A 并 @A、@C：A 只有一条「回复我的」，C 有一条「@ 我的」。
  @Test
  void should_send_single_reply_when_replied_and_mentioned() {
    long root = comment(A, "publication", PAPER, "根", null);
    comment(B, "publication", PAPER, "@user-1001 @user-1003 同意", root);

    JsonNode replies = inbox(A, "replies").get("items");
    assertThat(replies).hasSize(1);
    assertThat(replies.get(0).get("type").asString()).isEqualTo("COMMENT_REPLY");
    assertThat(inbox(A, "mentions").get("items")).isEmpty();
    assertThat(inbox(C, "mentions").get("items")).hasSize(1);
  }

  /// 编辑后只提醒新增的 @；删掉再加回同一人不再提醒。
  @Test
  void should_notify_only_added_mentions_after_edit() {
    long root = comment(B, "publication", PAPER, "请教 @user-1001", null);
    edit(B, root, "请教 @user-1001 @user-1003");
    edit(B, root, "请教 @user-1001");
    edit(B, root, "请教 @user-1001 @user-1003 再问");

    assertThat(inbox(A, "mentions").get("items")).hasSize(1);
    assertThat(inbox(C, "mentions").get("items")).hasSize(1);
  }

  /// 经验下的根评论提醒作者；楼里回复别人不提醒作者。
  @Test
  void should_notify_post_owner_on_root_comment_only() {
    registerPost(POST, OWNER, false);
    long root = comment(B, "post", POST, "有个问题", null);
    comment(C, "post", POST, "我也想知道", root);

    JsonNode owner = inbox(OWNER, "replies").get("items");
    assertThat(owner).hasSize(1);
    assertThat(owner.get(0).get("type").asString()).isEqualTo("POST_COMMENT");
    JsonNode b = inbox(B, "replies").get("items");
    assertThat(b).hasSize(1);
    assertThat(b.get(0).get("type").asString()).isEqualTo("COMMENT_REPLY");
  }

  /// 评论删除后：它引起的提醒随之删除，列表里没有、未读数减回去。
  @Test
  void should_remove_notification_when_comment_deleted() {
    long root = comment(A, "publication", PAPER, "根", null);
    long reply = comment(B, "publication", PAPER, "回复", root);
    assertThat(unread(A).get("replies").asInt()).isEqualTo(1);

    delete(B, reply);

    assertThat(inbox(A, "replies").get("items")).isEmpty();
    assertThat(unread(A).get("replies").asInt()).isZero();
  }

  /// 摘录是纯文本：Markdown 标记去掉，链接取文字，提及换成用户名。
  @Test
  void should_render_plain_excerpt() {
    long root = comment(A, "publication", PAPER, "根", null);
    comment(B, "publication", PAPER, "**同意**，见 [这里](https://x.org) @user-1003", root);

    JsonNode item = inbox(A, "replies").get("items").get(0);
    assertThat(item.get("excerpt").asString()).isEqualTo("同意，见 这里 @user-1003");
    assertThat(item.get("excerptTruncated").asBoolean()).isFalse();
    assertThat(item.get("subject").get("type").asString()).isEqualTo("publication");
    assertThat(item.get("subject").get("key").asString()).isEqualTo("100");
    assertThat(item.get("actor").get("username").asString()).isEqualTo("user-1002");
  }

  /// 摘录按 PAP-128 的规则转：引用块加中文引号，图片给原文，换行在站内压成一行。
  @Test
  void should_render_excerpt_by_plain_text_rules() {
    long root = comment(A, "publication", PAPER, "根", null);
    comment(B, "publication", PAPER, "> 原文\n\n看 ![图](u.png)\n第二行", root);

    JsonNode item = inbox(A, "replies").get("items").get(0);
    assertThat(item.get("excerpt").asString()).isEqualTo("“原文” 看 ![图](u.png) 第二行");
    assertThat(item.get("excerptTruncated").asBoolean()).isFalse();
  }
}
