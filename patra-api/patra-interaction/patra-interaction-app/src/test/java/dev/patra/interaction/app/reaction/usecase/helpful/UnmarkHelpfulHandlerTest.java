package dev.patra.interaction.app.reaction.usecase.helpful;

import static dev.patra.interaction.app.reaction.ReactionFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.reaction.event.HelpfulUnmarkedEvent;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulMark;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/// 取消有帮助（spec §4.2）：只要登录，不查资格与目标；目标锁里真删掉才减。
class UnmarkHelpfulHandlerTest {

  private static final long ME = 8L;
  private static final HelpfulTarget TARGET = HelpfulTarget.of(HelpfulTargetType.POST, 500L);

  private final HelpfulRepository helpful = mock(HelpfulRepository.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final List<String> events = new ArrayList<>();
  private UnmarkHelpfulHandler handler;

  @BeforeEach
  void setUp() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(ME, 1L, AccountType.USER, ClientType.WEB));
    doAnswer(invocation -> events.add("lock")).when(helpful).lockTarget(TARGET);
    doAnswer(invocation -> events.add("target" + invocation.getArgument(1, Integer.class)))
        .when(helpful)
        .adjustTargetCount(any(), anyInt());
    doAnswer(
            invocation ->
                events.add(
                    "received" + invocation.getArgument(0) + ":" + invocation.getArgument(1)))
        .when(helpful)
        .adjustReceived(anyLong(), anyLong());
    doAnswer(
            invocation -> {
              events.add("count");
              return 2;
            })
        .when(helpful)
        .countOf(TARGET);
    doAnswer(invocation -> events.add("publish")).when(publisher).publishEvent(any(Object.class));
    handler =
        new UnmarkHelpfulHandler(
            helpful,
            currentUser,
            new RecordingTransactions(events),
            publisher,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void should_unmark_and_decrement_credited_user() {
    givenDeleted(Optional.of(HelpfulMark.of(7L)));

    HelpfulResult result = handler.handle(UnmarkHelpfulCommand.of(HelpfulTargetType.POST, 500L));

    assertThat(result).isEqualTo(HelpfulResult.of(false, 2));
    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN,
            "lock",
            "delete",
            "target-1",
            "received7:-1",
            "publish",
            "count",
            RecordingTransactions.END);
  }

  @Test
  void should_not_touch_received_for_uncredited_mark() {
    givenDeleted(Optional.of(HelpfulMark.of(null)));

    handler.handle(UnmarkHelpfulCommand.of(HelpfulTargetType.POST, 500L));

    verify(helpful).adjustTargetCount(TARGET, -1);
    verify(helpful, never()).adjustReceived(anyLong(), anyLong());
  }

  /// 没点过（含目标已删、明细随之删掉）：什么都不改，照样返回。
  @Test
  void should_be_noop_when_not_marked() {
    givenDeleted(Optional.empty());

    HelpfulResult result = handler.handle(UnmarkHelpfulCommand.of(HelpfulTargetType.POST, 500L));

    assertThat(result).isEqualTo(HelpfulResult.of(false, 2));
    verify(helpful, never()).adjustTargetCount(any(), anyInt());
    verify(helpful, never()).adjustReceived(anyLong(), anyLong());
    verify(publisher, never()).publishEvent(any(Object.class));
  }

  /// 真删掉了才发「有帮助已取消」（PAP-141，只在服务内发布、不外送）：谁取消了哪个目标、什么时候。
  @Test
  void should_publish_unmarked_event_after_delete() {
    givenDeleted(Optional.of(HelpfulMark.of(7L)));

    handler.handle(UnmarkHelpfulCommand.of(HelpfulTargetType.POST, 500L));

    ArgumentCaptor<HelpfulUnmarkedEvent> captor =
        ArgumentCaptor.forClass(HelpfulUnmarkedEvent.class);
    verify(publisher).publishEvent(captor.capture());
    HelpfulUnmarkedEvent event = captor.getValue();
    assertThat(event.eventId()).isNotNull();
    assertThat(event)
        .usingRecursiveComparison()
        .ignoringFields("eventId")
        .isEqualTo(
            HelpfulUnmarkedEvent.builder()
                .eventId(UUID.randomUUID())
                .targetType(HelpfulTargetType.POST)
                .targetId(500L)
                .userId(ME)
                .unmarkedAt(NOW)
                .build());
  }

  /// 桩删除结果，删的时候记一笔。
  ///
  /// @param mark 删除结果
  private void givenDeleted(Optional<HelpfulMark> mark) {
    when(helpful.delete(ME, TARGET))
        .thenAnswer(
            invocation -> {
              events.add("delete");
              return mark;
            });
  }
}
