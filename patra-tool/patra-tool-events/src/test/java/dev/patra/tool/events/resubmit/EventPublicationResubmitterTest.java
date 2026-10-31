package dev.patra.tool.events.resubmit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.tool.test.clock.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.Staleness;

@DisplayName("事件发布补投")
@ExtendWith(OutputCaptureExtension.class)
class EventPublicationResubmitterTest {

  private static final Instant NOW = Instant.parse("2026-10-30T00:10:00Z");

  private final IncompleteEventPublications publications = mock(IncompleteEventPublications.class);

  private final EventPublicationRegistry registry = mock(EventPublicationRegistry.class);

  private final EventPublicationResubmitter resubmitter =
      new EventPublicationResubmitter(
          publications, registry, MutableClock.startingAt(NOW), Duration.ofMinutes(1));

  @Test
  @DisplayName("只补投发布时间早于 min-age 的登记行")
  void should_only_resubmit_publications_older_than_min_age() {
    AtomicReference<Predicate<EventPublication>> filter = new AtomicReference<>();
    doAnswer(
            invocation -> {
              filter.set(invocation.getArgument(0));
              return null;
            })
        .when(publications)
        .resubmitIncompletePublications(ArgumentMatchers.<Predicate<EventPublication>>any());

    resubmitter.resubmit();

    assertThat(filter.get().test(publishedAt("2026-10-30T00:08:00Z"))).isTrue();
    assertThat(filter.get().test(publishedAt("2026-10-30T00:09:30Z"))).isFalse();
  }

  @Test
  @DisplayName("返回补投的条数")
  void should_return_number_of_resubmitted() {
    List<EventPublication> candidates =
        List.of(
            publishedAt("2026-10-30T00:01:00Z"),
            publishedAt("2026-10-30T00:02:00Z"),
            publishedAt("2026-10-30T00:03:00Z"),
            publishedAt("2026-10-30T00:09:59Z"));
    doAnswer(
            invocation -> {
              Predicate<EventPublication> filter = invocation.getArgument(0);
              candidates.forEach(filter::test);
              return null;
            })
        .when(publications)
        .resubmitIncompletePublications(ArgumentMatchers.<Predicate<EventPublication>>any());

    assertThat(resubmitter.resubmit()).isEqualTo(3);
  }

  @Test
  @DisplayName("尝试 10 次以上还没发出去的登记行打 WARN，带事件 ID 与尝试次数")
  void should_warn_about_publications_stuck_for_ten_attempts(CapturedOutput output) {
    UUID stuck = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID fresh = UUID.fromString("00000000-0000-0000-0000-000000000009");
    List<EventPublication> candidates = List.of(attempted(stuck, 10), attempted(fresh, 9));
    doAnswer(
            invocation -> {
              Predicate<EventPublication> filter = invocation.getArgument(0);
              candidates.forEach(filter::test);
              return null;
            })
        .when(publications)
        .resubmitIncompletePublications(ArgumentMatchers.<Predicate<EventPublication>>any());

    resubmitter.resubmit();

    assertThat(output.getOut())
        .contains("WARN")
        .contains(stuck.toString())
        .contains("尝试 10 次")
        .doesNotContain(fresh.toString());
  }

  @Test
  @DisplayName("正在发送（PROCESSING、10 分钟内有动静）或已被领走（RESUBMITTED）的行不算补投；PROCESSING 超过 10 分钟当作卡住重投")
  void should_skip_publications_still_in_flight() {
    AtomicReference<Predicate<EventPublication>> filter = new AtomicReference<>();
    doAnswer(
            invocation -> {
              filter.set(invocation.getArgument(0));
              return null;
            })
        .when(publications)
        .resubmitIncompletePublications(ArgumentMatchers.<Predicate<EventPublication>>any());

    resubmitter.resubmit();

    assertThat(filter.get().test(withStatus(Status.PROCESSING, "2026-10-30T00:08:00Z"))).isFalse();
    assertThat(filter.get().test(withStatus(Status.PROCESSING, "2026-10-29T23:59:00Z"))).isTrue();
    assertThat(filter.get().test(withStatus(Status.FAILED, "2026-10-30T00:08:00Z"))).isTrue();
    // 已被领走、还没解卡的 RESUBMITTED 行 Modulith 不会再派发，不能算进补投数
    assertThat(filter.get().test(withStatus(Status.RESUBMITTED, "2026-10-30T00:08:00Z"))).isFalse();
  }

  @Test
  @DisplayName("先让 Modulith 把 10 分钟没动静的 RESUBMITTED 行改回 FAILED，再补投；只监控 RESUBMITTED")
  void should_reset_stuck_resubmissions_first() {
    resubmitter.resubmit();

    ArgumentCaptor<Staleness> staleness = ArgumentCaptor.forClass(Staleness.class);
    InOrder order = inOrder(registry, publications);
    order.verify(registry).markStalePublicationsFailed(staleness.capture());
    order
        .verify(publications)
        .resubmitIncompletePublications(ArgumentMatchers.<Predicate<EventPublication>>any());
    assertThat(staleness.getValue().getStaleness(Status.RESUBMITTED))
        .isEqualTo(Duration.ofMinutes(10));
    assertThat(staleness.getValue().isMonitored(Status.PROCESSING)).isFalse();
    assertThat(staleness.getValue().isMonitored(Status.PUBLISHED)).isFalse();
  }

  /// 已尝试若干次、早于 min-age 的登记行。
  ///
  /// @param id 登记行 ID
  /// @param attempts 尝试次数
  /// @return 登记行
  private static EventPublication attempted(UUID id, int attempts) {
    EventPublication publication = publishedAt("2026-10-30T00:00:00Z");
    when(publication.getIdentifier()).thenReturn(id);
    when(publication.getCompletionAttempts()).thenReturn(attempts);
    // 打日志不能读事件内容：反序列化失败会让整轮补投中断
    when(publication.getEvent()).thenThrow(new IllegalStateException("事件反序列化失败"));
    return publication;
  }

  /// 一小时前发布、处于指定状态、上次补投在指定时间的登记行。
  ///
  /// @param status 状态
  /// @param lastResubmission 上次补投时间
  /// @return 登记行
  private static EventPublication withStatus(Status status, String lastResubmission) {
    EventPublication publication = publishedAt("2026-10-29T23:10:00Z");
    when(publication.getStatus()).thenReturn(status);
    when(publication.getLastResubmissionDate()).thenReturn(Instant.parse(lastResubmission));
    return publication;
  }

  /// 发布于指定时间的登记行。
  ///
  /// @param instant 发布时间
  /// @return 登记行
  private static EventPublication publishedAt(String instant) {
    EventPublication publication = mock(EventPublication.class);
    when(publication.getPublicationDate()).thenReturn(Instant.parse(instant));
    return publication;
  }
}
