package dev.patra.interaction.app.subject.usecase.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.subject.service.SubjectSources;
import dev.patra.interaction.domain.subject.exception.SubjectSourceUnavailableException;
import dev.patra.interaction.domain.subject.model.aggregate.SubjectAggregate;
import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import dev.patra.interaction.domain.subject.model.enums.SourceState;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SourceRecord;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.repository.SubjectRepository;
import dev.patra.interaction.domain.subject.port.source.SubjectSourcePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/// 每日对账：100 行一页按身份锚回查，一页失败跳过继续，结果计数。
class ReconcileSubjectsHandlerTest {

  private static final SubjectType PUB = SubjectType.PUBLICATION;
  private static final Instant T0 = Instant.parse("2026-10-23T06:30:00Z");
  private static final Instant NOW = Instant.parse("2026-10-24T06:30:00Z");
  private static final SubjectSnapshot SNAPSHOT = SubjectSnapshot.of("T", "J", "2019", null);

  private final SubjectRepository repository = mock(SubjectRepository.class);
  private final SubjectSourcePort source = mock(SubjectSourcePort.class);
  private final SubjectSourcePort journals = mock(SubjectSourcePort.class);
  private ReconcileSubjectsHandler handler;

  @BeforeEach
  void setUp() {
    when(source.type()).thenReturn(PUB);
    when(journals.type()).thenReturn(SubjectType.JOURNAL);
    handler =
        new ReconcileSubjectsHandler(
            repository,
            new SubjectSources(List.of(source, journals)),
            TransactionOperations.withoutTransaction(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /// 本地对象不进对账：连分页都不取。
  @Test
  void should_never_page_local_types() {
    handler.handle(ReconcileSubjectsCommand.of());

    verify(repository, never()).findPage(eq(SubjectType.POST), anyLong(), anyInt());
    verify(repository).findPage(SubjectType.JOURNAL, 0L, 100);
  }

  @Test
  void should_page_until_empty_and_save_each_page() {
    List<SubjectAggregate> first = subjects(1, 100);
    List<SubjectAggregate> second = subjects(101, 103);
    when(repository.findPage(PUB, 0L, 100)).thenReturn(first);
    when(repository.findPage(PUB, 100L, 100)).thenReturn(second);
    when(repository.findPage(PUB, 103L, 100)).thenReturn(List.of());
    when(source.resolveByNaturalKeys(anyCollection())).thenAnswer(inv -> echo(inv.getArgument(0)));

    ReconciliationResult result = handler.handle(ReconcileSubjectsCommand.of());

    assertThat(result.scanned()).isEqualTo(103);
    assertThat(result.failedBatches()).isZero();
    verify(source, times(2)).resolveByNaturalKeys(anyCollection());
    verify(repository, times(2)).saveReconciled(anyList());
  }

  @Test
  void should_count_transitions() {
    SubjectAggregate idChanged = subject(1, 10L, SubjectState.ACTIVE);
    SubjectAggregate deleted = subject(2, 20L, SubjectState.ACTIVE);
    SubjectAggregate revived = subject(3, null, SubjectState.UNAVAILABLE);
    SubjectAggregate unchanged = subject(4, 40L, SubjectState.ACTIVE);
    when(repository.findPage(PUB, 0L, 100))
        .thenReturn(List.of(idChanged, deleted, revived, unchanged));
    when(repository.findPage(PUB, 4L, 100)).thenReturn(List.of());
    when(source.resolveByNaturalKeys(anyCollection()))
        .thenReturn(
            Map.of(
                pmid(1), record(1, 11L, SourceState.ACTIVE),
                pmid(2), record(2, 20L, SourceState.DELETED),
                pmid(3), record(3, 30L, SourceState.ACTIVE),
                pmid(4), record(4, 40L, SourceState.ACTIVE)));

    ReconciliationResult result = handler.handle(ReconcileSubjectsCommand.of());

    assertThat(result)
        .isEqualTo(
            ReconciliationResult.builder()
                .scanned(4)
                .sourceIdChanged(2)
                .becameUnavailable(1)
                .revived(1)
                .failedBatches(0)
                .build());
  }

  @Test
  void should_skip_failed_page_and_continue() {
    List<SubjectAggregate> first = subjects(1, 100);
    List<SubjectAggregate> second = subjects(101, 150);
    when(repository.findPage(PUB, 0L, 100)).thenReturn(first);
    when(repository.findPage(PUB, 100L, 100)).thenReturn(second);
    when(repository.findPage(PUB, 150L, 100)).thenReturn(List.of());
    when(source.resolveByNaturalKeys(anyCollection()))
        .thenThrow(new SubjectSourceUnavailableException(PUB, new RuntimeException("down")))
        .thenAnswer(inv -> echo(inv.getArgument(0)));

    ReconciliationResult result = handler.handle(ReconcileSubjectsCommand.of());

    assertThat(result.failedBatches()).isEqualTo(1);
    assertThat(result.scanned()).isEqualTo(150);
    ArgumentCaptor<List<SubjectAggregate>> saved = ArgumentCaptor.captor();
    verify(repository).saveReconciled(saved.capture());
    assertThat(saved.getValue()).hasSize(50);
    verify(repository).findPage(PUB, 150L, 100);
  }

  @Test
  void should_use_last_subject_id_as_cursor() {
    when(repository.findPage(PUB, 0L, 100)).thenReturn(subjects(5, 7));
    when(repository.findPage(eq(PUB), eq(7L), eq(100))).thenReturn(List.of());
    when(source.resolveByNaturalKeys(anyCollection())).thenAnswer(inv -> echo(inv.getArgument(0)));

    handler.handle(ReconcileSubjectsCommand.of());

    verify(repository).findPage(PUB, 7L, 100);
    verify(repository, never()).findPage(PUB, 5L, 100);
  }

  /// catalog 照原样返回每个身份锚（ID = PMID × 10，ACTIVE）。
  ///
  /// @param keys 请求的身份锚
  /// @return 解析结果
  private static Map<NaturalKey, SourceRecord> echo(Collection<NaturalKey> keys) {
    return keys.stream()
        .collect(
            Collectors.toMap(
                k -> k,
                k ->
                    SourceRecord.of(
                        Long.parseLong(k.value()) * 10, k, SourceState.ACTIVE, SNAPSHOT)));
  }

  /// `subject_id` 与 PMID 从 from 到 to 的一串 ACTIVE 对象（来源 ID = PMID × 10）。
  ///
  /// @param from 起
  /// @param to 止（含）
  /// @return 对象
  private static List<SubjectAggregate> subjects(long from, long to) {
    return LongStream.rangeClosed(from, to)
        .mapToObj(i -> subject(i, i * 10, SubjectState.ACTIVE))
        .toList();
  }

  /// 一个对象：`subject_id` = PMID = n。
  ///
  /// @param n 编号
  /// @param sourceId 来源 ID
  /// @param state 状态
  /// @return 聚合
  private static SubjectAggregate subject(long n, Long sourceId, SubjectState state) {
    return SubjectAggregate.builder()
        .subjectId(SubjectId.of(n))
        .type(PUB)
        .naturalKey(pmid(n))
        .sourceId(sourceId)
        .state(state)
        .snapshot(SNAPSHOT)
        .snapshotAt(T0)
        .build();
  }

  /// PMID n 的来源记录。
  ///
  /// @param n PMID
  /// @param sourceId 来源 ID
  /// @param state 状态
  /// @return 来源记录
  private static SourceRecord record(long n, long sourceId, SourceState state) {
    return SourceRecord.of(sourceId, pmid(n), state, SNAPSHOT);
  }

  /// PMID 身份锚。
  ///
  /// @param n PMID
  /// @return 身份锚
  private static NaturalKey pmid(long n) {
    return NaturalKey.of(NaturalKeyScheme.PMID, Long.toString(n));
  }
}
