package dev.patra.interaction.config;

import static dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer.getRabbitMQContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import dev.patra.interaction.api.discussion.event.CommentDeleted;
import dev.patra.interaction.api.discussion.event.CommentEdited;
import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.post.event.PostDeleted;
import dev.patra.interaction.api.reaction.event.ExperienceOutcome;
import dev.patra.interaction.api.reaction.event.HelpfulMarked;
import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.post.event.PostDeletedEvent;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.interaction.domain.post.model.read.ExperienceOutline;
import dev.patra.interaction.domain.reaction.event.HelpfulMarkedEvent;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.port.markdown.PlainTextPort;
import dev.patra.interaction.support.DiscussionEventRecorder;
import dev.patra.interaction.support.InteractionEventsITSpringConfig;
import dev.patra.interaction.support.InteractionFlowITSupport;
import dev.patra.tool.events.resubmit.EventPublicationResubmitter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/// interaction 的集成事件（PAP-141 验收），经 HTTP、真实 PG 与真实 RabbitMQ：五种事件在事务提交后送到
/// `interaction.events`、路由键对、发出的 JSON 能读成 `patra-interaction-api` 里的 record（契约），正文是 PAP-128 的
/// 纯文本；回滚不外送；有帮助已取消不外送；broker 停着时登记行留着，恢复后补投送达、事件 ID 不变；登记表里的领域
/// 事件能经 Modulith 的序列化器原样读回（补投靠它）。
class InteractionEventsFlowIT extends InteractionFlowITSupport {

  /// 作者 A。
  private static final long A = 4001L;

  /// 读者 B。
  private static final long B = 4002L;

  /// 文献的 catalog ID。
  private static final long PAPER = 100L;

  /// 期刊的 catalog ID。
  private static final long JOURNAL = 200L;

  /// 收一条消息最多等多久。
  private static final Duration WAIT = Duration.ofSeconds(10);

  /// 断言「没有消息」时等多久。
  private static final Duration QUIET = Duration.ofSeconds(2);

  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private PlainTextPort plainText;
  @Autowired private EventPublicationResubmitter resubmitter;
  @Autowired private EventSerializer eventSerializer;

  private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

  /// 文献 100、期刊 200，A、B 两个正常账号。
  @BeforeEach
  void world() {
    CATALOG_DATA.put(PAPER, "1000100", "J", "2019");
    CATALOG_DATA.putJournal(JOURNAL, "0404402", "Journal 200", "J 200");
    for (long id : List.of(A, B)) {
      IDENTITY_DATA.put(id, "ACTIVE", true, "PUBLIC");
    }
  }

  /// 发表：提交后送达，`messageId` 是事件 ID，字段与库里一致，纯文本按 PAP-128 转好、保留 `<@ID>`；登记行发完即删。
  @Test
  void should_deliver_comment_published_after_commit() {
    long id = comment(A, "publication", PAPER, "@user-4002 看 [链接](https://x.org) 和 `code`", null);

    Received<CommentPublished> received =
        receive(CommentPublished.ROUTING_KEY, CommentPublished.class, e -> e.commentId() == id);

    CommentPublished event = received.event();
    assertThat(received.message().getMessageProperties().getMessageId())
        .isEqualTo(event.eventId().toString());
    String stored = commentColumn(id, "body", String.class);
    assertThat(event)
        .usingRecursiveComparison()
        .ignoringFields("eventId", "publishedAt")
        .isEqualTo(
            CommentPublished.builder()
                .commentId(id)
                .version(1)
                .subject(
                    SubjectContext.of(
                        commentColumn(id, "subject_id", Long.class), "PUBLICATION", null, false))
                .rootCommentId(id)
                .authorId(A)
                .authorAnonymous(false)
                .mentionedUserIds(List.of(B))
                .plainText(plainText.toPlainText(stored, Set.of(B)).value())
                .build());
    assertThat(event.plainText()).contains("<@" + B + ">").doesNotContain("](");
    assertThat(event.publishedAt()).isEqualTo(commentColumn(id, "created_at", Instant.class));
    awaitRegistryEmpty();
  }

  /// 编辑：版本 2、新的纯文本与新增的 @。
  @Test
  void should_deliver_comment_edited_with_version_two() {
    long id = comment(A, "publication", PAPER, "原文", null);
    EntityExchangeResult<String> edited =
        call(
            HttpMethod.PUT,
            A,
            "/comments/" + id,
            JSON.writeValueAsString(Map.of("body", "改成 @user-4002")));
    assertThat(status(edited)).as(edited.getResponseBody()).isEqualTo(200);

    CommentEdited event =
        receive(CommentEdited.ROUTING_KEY, CommentEdited.class, e -> e.commentId() == id).event();

    assertThat(event.version()).isEqualTo(2);
    assertThat(commentColumn(id, "version", Integer.class)).isEqualTo(2);
    assertThat(event.mentionedUserIds()).containsExactly(B);
    assertThat(event.addedMentionedUserIds()).containsExactly(B);
    assertThat(event.plainText()).isEqualTo("改成 <@" + B + ">");
    assertThat(event.subject().type()).isEqualTo("PUBLICATION");
    awaitRegistryEmpty();
  }

  /// 删除没有回复的评论：整条去掉。
  @Test
  void should_deliver_comment_deleted() {
    long id = comment(A, "publication", PAPER, "要删的", null);
    long subjectId = commentColumn(id, "subject_id", Long.class);
    assertThat(status(call(HttpMethod.DELETE, A, "/comments/" + id, null))).isEqualTo(200);

    CommentDeleted event =
        receive(CommentDeleted.ROUTING_KEY, CommentDeleted.class, e -> e.commentId() == id).event();

    assertThat(event)
        .usingRecursiveComparison()
        .ignoringFields("eventId", "deletedAt")
        .isEqualTo(
            CommentDeleted.builder()
                .commentId(id)
                .version(1)
                .subjectId(subjectId)
                .authorId(A)
                .placeholder(false)
                .build());
    awaitRegistryEmpty();
  }

  /// 经验删除：`subjectId` 就是经验下评论事件里的对象；评论事件带经验的所有者。
  @Test
  void should_deliver_post_deleted() {
    long post = experience(A, false, null);
    long reply = comment(B, "post", post, "问一句", null);
    CommentPublished published =
        receive(CommentPublished.ROUTING_KEY, CommentPublished.class, e -> e.commentId() == reply)
            .event();
    assertThat(published.subject().type()).isEqualTo("POST");
    assertThat(published.subject().ownerId()).isEqualTo(A);

    assertThat(status(call(HttpMethod.DELETE, A, "/posts/" + post, null))).isEqualTo(204);

    PostDeleted event =
        receive(PostDeleted.ROUTING_KEY, PostDeleted.class, e -> e.postId() == post).event();
    assertThat(event.subjectId()).isEqualTo(published.subject().subjectId());
    assertThat(event.authorId()).isEqualTo(A);
    awaitRegistryEmpty();
  }

  /// 点经验：带作者、点的人、经验的结果与正文的纯文本（经验不解析 @）。
  @Test
  void should_deliver_helpful_marked_on_post_with_experience_outcome() {
    long post = experience(A, true, "第二轮小修后直接接收");
    assertThat(status(call(HttpMethod.PUT, B, "/posts/" + post + "/helpful", null))).isEqualTo(200);

    HelpfulMarked event =
        receive(HelpfulMarked.ROUTING_KEY, HelpfulMarked.class, e -> e.targetId() == post).event();

    assertThat(event)
        .usingRecursiveComparison()
        .ignoringFields("eventId", "markedAt", "subject.subjectId")
        .isEqualTo(
            HelpfulMarked.builder()
                .targetType("POST")
                .targetId(post)
                .targetAuthorId(A)
                .targetAnonymous(true)
                .userId(B)
                .subject(SubjectContext.of(0L, "POST", A, true))
                .experience(ExperienceOutcome.of("MAJOR_REVISION", "IN_PROGRESS", 10, null))
                .targetPlainText("第二轮小修后直接接收")
                .build());
    awaitRegistryEmpty();
  }

  /// 有帮助已取消不外送：取消之后登记表是空的（真外送了，没有队列绑定它，会被退回、留在登记表里）。
  @Test
  void should_not_externalize_helpful_unmarked() {
    long id = comment(A, "publication", PAPER, "有帮助", null);
    assertThat(status(call(HttpMethod.PUT, B, "/comments/" + id + "/helpful", null)))
        .isEqualTo(200);
    receive(HelpfulMarked.ROUTING_KEY, HelpfulMarked.class, e -> e.targetId() == id);
    awaitRegistryEmpty();

    assertThat(status(call(HttpMethod.DELETE, B, "/comments/" + id + "/helpful", null)))
        .isEqualTo(200);

    awaitRegistryEmpty();
    await()
        .during(QUIET)
        .atMost(QUIET.plusSeconds(1))
        .untilAsserted(
            () -> assertThat(count("SELECT count(*) FROM public.event_publication")).isZero());
  }

  /// 同步订阅方抛异常让发表回滚（状态码随订阅方抛的异常而定，这里不关心）：评论没落库，也不登记、不外送。
  @Test
  void should_not_externalize_when_publish_rolls_back() {
    String body = "回滚 " + DiscussionEventRecorder.FAIL_MARKER;
    EntityExchangeResult<String> result =
        call(
            HttpMethod.POST,
            A,
            "/discussions/publication/" + PAPER + "/comments",
            JSON.writeValueAsString(Map.of("body", body)));

    assertThat(status(result)).isNotEqualTo(201);
    assertThat(count("SELECT count(*) FROM discussion.comment")).isZero();
    assertThat(count("SELECT count(*) FROM public.event_publication")).isZero();
    assertNoMessage(
        CommentPublished.ROUTING_KEY,
        CommentPublished.class,
        e -> e.plainText().contains(DiscussionEventRecorder.FAIL_MARKER));
  }

  /// broker 停着时发表照常成功、登记行留着；恢复后补投送达，事件 ID 与登记行里领域事件的相同。
  @Test
  void should_keep_event_id_when_resubmitted() throws Exception {
    long id;
    getRabbitMQContainer().execInContainer("rabbitmqctl", "stop_app");
    try {
      id = comment(A, "publication", PAPER, "broker 停着时发的", null);
      await()
          .atMost(WAIT)
          .untilAsserted(
              () ->
                  assertThat(
                          jdbcTemplate.queryForList(
                              "SELECT status FROM public.event_publication", String.class))
                      .containsExactly("FAILED"));
    } finally {
      getRabbitMQContainer().execInContainer("rabbitmqctl", "start_app");
    }
    String serialized =
        jdbcTemplate.queryForObject(
            "SELECT serialized_event FROM public.event_publication", String.class);
    UUID registered = UUID.fromString(JSON.readTree(serialized).get("eventId").asString());
    jdbcTemplate.update(
        "UPDATE public.event_publication SET publication_date = publication_date - interval '2 minutes'");

    assertThat(resubmitter.resubmit()).isEqualTo(1);

    long commentId = id;
    CommentPublished event =
        receive(
                CommentPublished.ROUTING_KEY,
                CommentPublished.class,
                e -> e.commentId() == commentId)
            .event();
    assertThat(event.eventId()).isEqualTo(registered);
    awaitRegistryEmpty();
  }

  /// 外送的五个领域事件经 Modulith 实际用的序列化器写进登记表、读回来与原值相等（补投时要读回再映射）。
  @Test
  void should_round_trip_domain_events_through_registry_serializer() {
    Instant at = Instant.parse("2026-10-31T01:02:03.456789Z");
    SubjectOwner owner = SubjectOwner.of(A, true);
    List<Object> events =
        List.of(
            CommentPublishedEvent.builder()
                .eventId(UUID.randomUUID())
                .commentId(10L)
                .version(1)
                .subjectId(SubjectId.of(11L))
                .subjectType(SubjectType.POST)
                .subjectOwner(owner)
                .rootCommentId(9L)
                .parentCommentId(9L)
                .parentAuthorId(B)
                .authorId(A)
                .authorAnonymous(true)
                .mentionedUserIds(List.of(B))
                .body("<@4002> 正文")
                .plainText("<@4002> 正文")
                .publishedAt(at)
                .build(),
            CommentEditedEvent.builder()
                .eventId(UUID.randomUUID())
                .commentId(10L)
                .version(3)
                .subjectId(SubjectId.of(11L))
                .subjectType(SubjectType.PUBLICATION)
                .rootCommentId(10L)
                .authorId(A)
                .mentionedUserIds(List.of(B))
                .addedMentionedUserIds(List.of(B))
                .body("改")
                .plainText("改")
                .editedAt(at)
                .build(),
            CommentDeletedEvent.builder()
                .eventId(UUID.randomUUID())
                .commentId(10L)
                .version(3)
                .subjectId(SubjectId.of(11L))
                .authorId(A)
                .placeholder(true)
                .deletedAt(at)
                .build(),
            PostDeletedEvent.builder()
                .eventId(UUID.randomUUID())
                .postId(500L)
                .subjectId(SubjectId.of(12L))
                .authorId(A)
                .deletedAt(at)
                .build(),
            HelpfulMarkedEvent.builder()
                .eventId(UUID.randomUUID())
                .targetType(HelpfulTargetType.POST)
                .targetId(500L)
                .targetAuthorId(A)
                .targetAnonymous(true)
                .userId(B)
                .markedAt(at)
                .subjectId(SubjectId.of(12L))
                .subjectType(SubjectType.POST)
                .subjectOwner(owner)
                .experience(
                    ExperienceOutline.of(
                        FirstDecision.MAJOR_REVISION, FinalOutcome.IN_PROGRESS, 10, null))
                .targetPlainText(null)
                .build());

    for (Object event : events) {
      assertThat(eventSerializer.deserialize(eventSerializer.serialize(event), event.getClass()))
          .as(event.getClass().getSimpleName())
          .isEqualTo(event);
    }
  }

  /// 收到的一条消息与读出来的集成事件。
  ///
  /// @param message 原始消息
  /// @param event 集成事件
  /// @param <T> 集成事件类型
  private record Received<T>(Message message, T event) {}

  /// 从测试队列收第一条路由键对、内容匹配的消息，跳过别的（共用上下文里别的测试的事件可能晚到）。契约检查：消息头
  /// `__TypeId__` 是 api 里 record 的全类名，正文能按它读回来。
  ///
  /// @param routingKey 路由键
  /// @param type 集成事件类型
  /// @param matches 内容条件
  /// @param <T> 集成事件类型
  /// @return 消息与事件
  private <T> Received<T> receive(String routingKey, Class<T> type, Predicate<T> matches) {
    long deadline = System.nanoTime() + WAIT.toNanos();
    while (System.nanoTime() < deadline) {
      Message message = rabbitTemplate.receive(InteractionEventsITSpringConfig.QUEUE, 500);
      if (message == null
          || !routingKey.equals(message.getMessageProperties().getReceivedRoutingKey())) {
        continue;
      }
      assertThat(message.getMessageProperties().getHeaders().get("__TypeId__"))
          .isEqualTo(type.getName());
      T event = JSON.readValue(message.getBody(), type);
      if (matches.test(event)) {
        return new Received<>(message, event);
      }
    }
    return fail("没有在 %s 内收到路由键为 %s 的匹配消息", WAIT, routingKey);
  }

  /// 断言一段时间内测试队列里没有路由键对、内容匹配的消息。
  ///
  /// @param routingKey 路由键
  /// @param type 集成事件类型
  /// @param matches 内容条件
  /// @param <T> 集成事件类型
  private <T> void assertNoMessage(String routingKey, Class<T> type, Predicate<T> matches) {
    long deadline = System.nanoTime() + QUIET.toNanos();
    while (System.nanoTime() < deadline) {
      Message message = rabbitTemplate.receive(InteractionEventsITSpringConfig.QUEUE, 200);
      if (message != null
          && routingKey.equals(message.getMessageProperties().getReceivedRoutingKey())) {
        assertThat(matches.test(JSON.readValue(message.getBody(), type))).isFalse();
      }
    }
  }

  /// 等登记表清空（默认完成模式 DELETE：发完即删）。
  private void awaitRegistryEmpty() {
    await()
        .atMost(WAIT)
        .untilAsserted(
            () -> assertThat(count("SELECT count(*) FROM public.event_publication")).isZero());
  }

  /// 评论表里的一列。
  ///
  /// @param id 评论
  /// @param column 列
  /// @param type 类型
  /// @param <T> 类型
  /// @return 值
  private <T> T commentColumn(long id, String column, Class<T> type) {
    return jdbcTemplate.queryForObject(
        "SELECT " + column + " FROM discussion.comment WHERE comment_id = ?", type, id);
  }

  /// 发一条评论（不受每日频率限制）。
  ///
  /// @param user 作者
  /// @param type 对象类型
  /// @param key 对象 key
  /// @param body 正文
  /// @param parentId 回复的评论，可空
  /// @return 评论 ID
  private long comment(long user, String type, long key, String body, Long parentId) {
    jdbcTemplate.update(
        "DELETE FROM subject.publish_quota WHERE user_id = ? AND kind = 'COMMENT'", user);
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("body", body);
    request.put("parentId", parentId == null ? null : parentId.toString());
    EntityExchangeResult<String> result =
        call(
            HttpMethod.POST,
            user,
            "/discussions/" + type + "/" + key + "/comments",
            JSON.writeValueAsString(request));
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(201);
    return Long.parseLong(json(result).get("commentId").asString());
  }

  /// 发一条投稿经验：首轮 10 天大修、进行中。
  ///
  /// @param user 作者
  /// @param anonymous 是否匿名
  /// @param body 正文，可空
  /// @return 帖子 ID
  private long experience(long user, boolean anonymous, String body) {
    LocalDate submitted = today.minusDays(30);
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("submittedOn", submitted.toString());
    request.put("firstDecisionOn", submitted.plusDays(10).toString());
    request.put("firstDecision", "MAJOR_REVISION");
    request.put("finalOutcome", "IN_PROGRESS");
    request.put("articleType", "ORIGINAL_RESEARCH");
    request.put("anonymous", anonymous);
    if (body != null) {
      request.put("body", body);
    }
    EntityExchangeResult<String> result =
        call(
            HttpMethod.POST,
            user,
            "/journals/" + JOURNAL + "/posts",
            JSON.writeValueAsString(request));
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(201);
    return Long.parseLong(json(result).get("postId").asString());
  }
}
