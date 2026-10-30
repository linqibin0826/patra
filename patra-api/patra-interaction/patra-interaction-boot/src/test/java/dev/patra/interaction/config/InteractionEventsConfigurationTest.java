package dev.patra.interaction.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.api.discussion.event.CommentDeleted;
import dev.patra.interaction.api.discussion.event.CommentEdited;
import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.post.event.PostDeleted;
import dev.patra.interaction.api.reaction.event.HelpfulMarked;
import dev.patra.interaction.api.subject.event.InteractionExchange;
import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.post.event.PostDeletedEvent;
import dev.patra.interaction.domain.reaction.event.HelpfulMarkedEvent;
import dev.patra.interaction.domain.reaction.event.HelpfulUnmarkedEvent;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/// 外送配置（PAP-141）：只选中五种领域事件，都发到 `interaction.events`、路由键为事件类型，映射成 api 里的 record；
/// 有帮助已取消不外送（没有队列订阅，发了会被退回、一直补投）。
class InteractionEventsConfigurationTest {

  private static final Instant AT = Instant.parse("2026-10-31T01:00:00Z");

  private final InteractionEventsConfiguration configuration = new InteractionEventsConfiguration();
  private final EventExternalizationConfiguration externalization =
      configuration.eventExternalizationConfiguration();

  /// 五种领域事件 → 路由键与集成事件类型。
  @Test
  void should_externalize_exactly_five_domain_events() {
    Map<Object, Expected> samples =
        Map.of(
            published(), new Expected(CommentPublished.ROUTING_KEY, CommentPublished.class),
            edited(), new Expected(CommentEdited.ROUTING_KEY, CommentEdited.class),
            deleted(), new Expected(CommentDeleted.ROUTING_KEY, CommentDeleted.class),
            postDeleted(), new Expected(PostDeleted.ROUTING_KEY, PostDeleted.class),
            marked(), new Expected(HelpfulMarked.ROUTING_KEY, HelpfulMarked.class));

    samples.forEach(
        (event, expected) -> {
          assertThat(externalization.supports(event)).as(event.getClass().getName()).isTrue();
          assertThat(externalization.determineTarget(event))
              .isEqualTo(RoutingTarget.forTarget(InteractionExchange.NAME).andKey(expected.key()));
          assertThat(externalization.map(event)).isInstanceOf(expected.type());
        });
    assertThat(CommentPublished.ROUTING_KEY).isEqualTo("comment.published");
    assertThat(CommentEdited.ROUTING_KEY).isEqualTo("comment.edited");
    assertThat(CommentDeleted.ROUTING_KEY).isEqualTo("comment.deleted");
    assertThat(PostDeleted.ROUTING_KEY).isEqualTo("post.deleted");
    assertThat(HelpfulMarked.ROUTING_KEY).isEqualTo("helpful.marked");
  }

  /// 有帮助已取消与无关对象不外送。
  @Test
  void should_not_externalize_unmarked_or_unrelated_objects() {
    HelpfulUnmarkedEvent unmarked =
        HelpfulUnmarkedEvent.builder()
            .eventId(UUID.randomUUID())
            .targetType(HelpfulTargetType.POST)
            .targetId(1L)
            .userId(2L)
            .unmarkedAt(AT)
            .build();

    assertThat(externalization.supports(unmarked)).isFalse();
    assertThat(externalization.supports("x")).isFalse();
  }

  /// 交换机：`interaction.events`，topic，持久化。
  @Test
  void should_declare_durable_topic_exchange() {
    TopicExchange exchange = configuration.interactionEventsExchange();

    assertThat(exchange.getName()).isEqualTo("interaction.events");
    assertThat(exchange.isDurable()).isTrue();
    assertThat(exchange.isAutoDelete()).isFalse();
  }

  /// 期望的路由键与集成事件类型。
  ///
  /// @param key 路由键
  /// @param type 集成事件类型
  private record Expected(String key, Class<?> type) {}

  /// 样例：评论已发表。
  ///
  /// @return 事件
  private static CommentPublishedEvent published() {
    return CommentPublishedEvent.builder()
        .eventId(UUID.randomUUID())
        .commentId(1L)
        .version(1)
        .subjectId(SubjectId.of(2L))
        .subjectType(SubjectType.PUBLICATION)
        .rootCommentId(1L)
        .authorId(3L)
        .body("x")
        .plainText("x")
        .publishedAt(AT)
        .build();
  }

  /// 样例：评论已编辑。
  ///
  /// @return 事件
  private static CommentEditedEvent edited() {
    return CommentEditedEvent.builder()
        .eventId(UUID.randomUUID())
        .commentId(1L)
        .version(2)
        .subjectId(SubjectId.of(2L))
        .subjectType(SubjectType.PUBLICATION)
        .rootCommentId(1L)
        .authorId(3L)
        .body("y")
        .plainText("y")
        .editedAt(AT)
        .build();
  }

  /// 样例：评论已删除。
  ///
  /// @return 事件
  private static CommentDeletedEvent deleted() {
    return CommentDeletedEvent.builder()
        .eventId(UUID.randomUUID())
        .commentId(1L)
        .version(2)
        .subjectId(SubjectId.of(2L))
        .authorId(3L)
        .deletedAt(AT)
        .build();
  }

  /// 样例：投稿经验已删除。
  ///
  /// @return 事件
  private static PostDeletedEvent postDeleted() {
    return PostDeletedEvent.builder()
        .eventId(UUID.randomUUID())
        .postId(5L)
        .subjectId(SubjectId.of(6L))
        .authorId(3L)
        .deletedAt(AT)
        .build();
  }

  /// 样例：有帮助已点。
  ///
  /// @return 事件
  private static HelpfulMarkedEvent marked() {
    return HelpfulMarkedEvent.builder()
        .eventId(UUID.randomUUID())
        .targetType(HelpfulTargetType.COMMENT)
        .targetId(1L)
        .targetAuthorId(3L)
        .userId(4L)
        .markedAt(AT)
        .subjectId(SubjectId.of(2L))
        .subjectType(SubjectType.PUBLICATION)
        .build();
  }
}
