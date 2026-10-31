package dev.patra.notification.adapter.inbox.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.api.reaction.event.ExperienceOutcome;
import dev.patra.interaction.api.reaction.event.HelpfulMarked;
import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.notification.app.inbox.usecase.ingest.IngestOutcome;
import dev.patra.notification.app.inbox.usecase.ingest.RecordHelpfulMarkedCommand;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// 信箱监听只做「集成事件 → 命令」的映射。
class InboxEventListenerTest {

  private final CommandBus commandBus = mock(CommandBus.class);
  private final InboxEventListener listener = new InboxEventListener(commandBus);

  /// 有帮助已点：点的人取 `userId`，所在对象与所有者取 `subject`，经验结果不进命令（列表读时现查）。
  @Test
  void should_map_helpful_marked_to_command() {
    UUID eventId = UUID.randomUUID();
    Instant markedAt = Instant.parse("2026-10-31T06:00:00Z");
    when(commandBus.handle(any(RecordHelpfulMarkedCommand.class)))
        .thenReturn(IngestOutcome.APPLIED);

    listener.on(
        HelpfulMarked.builder()
            .eventId(eventId)
            .targetType("POST")
            .targetId(55L)
            .targetAuthorId(101L)
            .targetAnonymous(true)
            .userId(11L)
            .markedAt(markedAt)
            .subject(SubjectContext.of(41L, "POST", 101L, true))
            .experience(new ExperienceOutcome("MAJOR_REVISION", "ACCEPTED", 41, 96))
            .targetPlainText("经验正文")
            .build());

    ArgumentCaptor<RecordHelpfulMarkedCommand> command =
        ArgumentCaptor.forClass(RecordHelpfulMarkedCommand.class);
    verify(commandBus).handle(command.capture());
    assertThat(command.getValue())
        .isEqualTo(
            RecordHelpfulMarkedCommand.builder()
                .eventId(eventId)
                .targetType("POST")
                .targetId(55L)
                .targetAuthorId(101L)
                .actorId(11L)
                .markedAt(markedAt)
                .subjectId(41L)
                .subjectOwnerId(101L)
                .subjectOwnerAnonymous(true)
                .targetPlainText("经验正文")
                .build());
  }
}
