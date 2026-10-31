package dev.patra.interaction.discussion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.support.DiscussionEventRecorder;
import dev.patra.tool.security.test.TestIdentity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.transaction.support.TransactionTemplate;

/// 进程内事件（PAP-121 的 AC）：评论写入的同一事务里发布，订阅方失败时评论与发表计数一起回滚；三种事件按契约带足字段。
/// 另外经 HTTP 验发表守卫的原因码（底座第一次被 HTTP 打到）。测试用的订阅方是共用底座导入的
/// {@link DiscussionEventRecorder}。
class DiscussionEventRollbackFlowIT extends DiscussionFlowITSupport {

  private static final long A = 1001L;
  private static final long B = 1002L;
  private static final long C = 1003L;
  private static final long D = 1004L;
  private static final long E = 1005L;
  private static final long PAPER = 100L;
  private static final long GONE_POST = 6001L;

  @Autowired private SubjectGateway subjects;
  @Autowired private TransactionTemplate transactions;
  @LocalServerPort private int port;

  @BeforeEach
  void world() {
    publication(PAPER);
    users(A, B, C, D, E);
  }

  /// AC：订阅方抛异常时整个事务回滚：评论、发表计数都没有落库（状态码随订阅方抛的异常而定，这里不关心）。
  @Test
  void should_roll_back_comment_and_quota_when_subscriber_fails() {
    EntityExchangeResult<String> result =
        post(A, "publication", PAPER, DiscussionEventRecorder.FAIL_MARKER, null);

    assertThat(status(result)).isNotEqualTo(201);
    assertThat(count("SELECT count(*) FROM discussion.comment")).isZero();
    assertThat(count("SELECT count(*) FROM subject.publish_quota WHERE user_id = ?", A)).isZero();
  }

  /// 发表、回复（@C）、编辑（新加 @D）、删除：依次收到四个事件，字段与库里一致。
  @Test
  void should_deliver_events_with_contract_fields() {
    long root = comment(A, PAPER, "根", null);
    long reply = comment(B, PAPER, "@user-1003 同意", root);
    Instant replyCreatedAt = createdAt(reply);
    EntityExchangeResult<String> edited =
        call(
            HttpMethod.PUT,
            B,
            "/comments/" + reply,
            JSON.writeValueAsString(Map.of("body", "@user-1003 @user-1004 同意")));
    assertThat(status(edited)).isEqualTo(200);
    assertThat(status(call(HttpMethod.DELETE, B, "/comments/" + reply, null))).isEqualTo(200);

    List<Object> events = DiscussionEventRecorder.EVENTS;
    assertThat(events).hasSize(4);
    CommentPublishedEvent first = (CommentPublishedEvent) events.get(0);
    assertThat(first.commentId()).isEqualTo(root);
    assertThat(first.rootCommentId()).isEqualTo(root);
    assertThat(first.parentCommentId()).isNull();
    assertThat(first.subjectType()).isEqualTo(SubjectType.PUBLICATION);
    assertThat(first.subjectOwner()).isNull();
    CommentPublishedEvent second = (CommentPublishedEvent) events.get(1);
    assertThat(second.commentId()).isEqualTo(reply);
    assertThat(second.rootCommentId()).isEqualTo(root);
    assertThat(second.parentCommentId()).isEqualTo(root);
    assertThat(second.parentAuthorId()).isEqualTo(A);
    assertThat(second.authorId()).isEqualTo(B);
    assertThat(second.mentionedUserIds()).containsExactly(C);
    assertThat(second.plainText()).isEqualTo("<@1003> 同意");
    assertThat(second.publishedAt()).isEqualTo(replyCreatedAt);
    CommentEditedEvent third = (CommentEditedEvent) events.get(2);
    assertThat(third.commentId()).isEqualTo(reply);
    assertThat(third.parentAuthorId()).isEqualTo(A);
    assertThat(third.mentionedUserIds()).containsExactly(C, D);
    assertThat(third.addedMentionedUserIds()).containsExactly(D);
    CommentDeletedEvent fourth = (CommentDeletedEvent) events.get(3);
    assertThat(fourth.commentId()).isEqualTo(reply);
    assertThat(fourth.authorId()).isEqualTo(B);
    assertThat(fourth.placeholder()).isFalse();
  }

  /// 发表守卫经 HTTP：邮箱未验证 409；10 秒内第二条 429 带 `Retry-After`；对象已删除 404。
  @Test
  void should_map_guard_rejections_over_http() throws Exception {
    IDENTITY_DATA.put(E, "ACTIVE", false, "PUBLIC");
    EntityExchangeResult<String> unverified = post(E, "publication", PAPER, "hi", null);
    assertThat(status(unverified)).isEqualTo(409);
    assertThat(traits(unverified)).contains("EMAIL_UNVERIFIED");

    comment(A, PAPER, "第一条", null);
    HttpResponse<String> second = postWithoutRetry(A, "第二条");
    assertThat(second.statusCode()).isEqualTo(429);
    assertThat(second.headers().firstValue("Retry-After")).isPresent();
    assertThat(second.body()).contains("TOO_FREQUENT");

    transactions.executeWithoutResult(
        status -> {
          SubjectSnapshot snapshot = SubjectSnapshot.of(null, "J", "2026-03", null);
          subjects.registerLocal(SubjectType.POST, GONE_POST, SubjectOwner.of(C, false), snapshot);
          subjects.updateLocal(SubjectType.POST, GONE_POST, SubjectState.UNAVAILABLE, snapshot);
        });
    EntityExchangeResult<String> gone = post(B, "post", GONE_POST, "还在吗", null);
    assertThat(status(gone)).isEqualTo(404);
    assertThat(traits(gone)).contains("SUBJECT_UNAVAILABLE");
  }

  /// 不经 `RestTestClient` 发表：它遇到 429 会按 `Retry-After` 自动重发。
  ///
  /// @param user 作者
  /// @param body 正文
  /// @return 响应
  /// @throws Exception 请求失败
  private HttpResponse<String> postWithoutRetry(long user, String body) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:" + port + "/discussions/publication/" + PAPER + "/comments"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("body", body))));
    TestIdentity.headers(user)
        .forEach((name, values) -> values.forEach(v -> request.header(name, v)));
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  /// 库里的发表时刻。
  ///
  /// @param id 评论
  /// @return 时刻
  private Instant createdAt(long id) {
    return jdbcTemplate
        .queryForObject(
            "SELECT created_at FROM discussion.comment WHERE comment_id = ?",
            OffsetDateTime.class,
            id)
        .toInstant();
  }
}
