package dev.patra.interaction.support;

import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.post.event.PostDeletedEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;

/// 测试用的讨论事件订阅方：同步收下三种事件；正文含 {@link #FAIL_MARKER} 时抛异常，模拟订阅方失败。
/// {@link #failOnDelete} 打开时，评论与投稿经验的删除事件也抛异常（有帮助的删除联动要验「与删除同事务」）。
///
/// 由 {@link InteractionFlowITSupport} 导入，所有流程测试共用一个 Spring 上下文：单独给某个测试类导入会多出一个上下文，
/// 测试类之间 WireMock 换了端口后，先建的那个上下文里记下的下游地址就失效了。
@TestConfiguration
public class DiscussionEventRecorder {

  /// 正文含这个标记时，订阅方抛异常。
  public static final String FAIL_MARKER = "!boom!";

  /// 打开时删除事件的订阅方抛异常；每例前由 {@link InteractionFlowITSupport} 复位。
  public static volatile boolean failOnDelete;

  /// 收到的事件，按顺序；每例前由 {@link InteractionFlowITSupport} 清空。
  public static final List<Object> EVENTS = new CopyOnWriteArrayList<>();

  /// 评论已发表。
  ///
  /// @param event 事件
  @EventListener
  void on(CommentPublishedEvent event) {
    if (event.body().contains(FAIL_MARKER)) {
      throw new IllegalStateException("订阅方失败");
    }
    EVENTS.add(event);
  }

  /// 评论已编辑。
  ///
  /// @param event 事件
  @EventListener
  void on(CommentEditedEvent event) {
    EVENTS.add(event);
  }

  /// 评论已删除。
  ///
  /// @param event 事件
  @EventListener
  void on(CommentDeletedEvent event) {
    if (failOnDelete) {
      throw new IllegalStateException("订阅方失败");
    }
    EVENTS.add(event);
  }

  /// 投稿经验已删除：只在 {@link #failOnDelete} 打开时抛异常，不记进 {@link #EVENTS}。
  ///
  /// @param event 事件
  @EventListener
  void on(PostDeletedEvent event) {
    if (failOnDelete) {
      throw new IllegalStateException("订阅方失败");
    }
  }
}
