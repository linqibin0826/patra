package dev.patra.interaction.app.subject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.subject.exception.InvalidSubjectKeyException;
import dev.patra.interaction.domain.subject.exception.SubjectCapabilityNotSupportedException;
import dev.patra.interaction.domain.subject.exception.SubjectErrorTrait;
import dev.patra.interaction.domain.subject.exception.SubjectNotFoundException;
import dev.patra.interaction.domain.subject.exception.SubjectSourceUnavailableException;
import dev.patra.interaction.domain.subject.model.aggregate.SubjectAggregate;
import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import dev.patra.interaction.domain.subject.model.enums.SourceState;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SourceRecord;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.repository.SubjectRepository;
import dev.patra.interaction.domain.subject.port.source.SubjectSourcePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/// 首次互动解析：先查本地，未命中才调来源；不可用、不存在、软删都不写库。
class SubjectGatewayImplTest {

  private static final SubjectType PUB = SubjectType.PUBLICATION;
  private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");
  private static final NaturalKey PMID_1 = NaturalKey.of(NaturalKeyScheme.PMID, "1");
  private static final SubjectSnapshot SNAPSHOT = SubjectSnapshot.of("T", "J", "2019", null);

  private final SubjectRepository repository = mock(SubjectRepository.class);
  private final SubjectSourcePort source = mock(SubjectSourcePort.class);
  private final SubjectSourcePort journals = mock(SubjectSourcePort.class);
  private final List<String> events = new ArrayList<>();
  private SubjectGatewayImpl gateway;

  @BeforeEach
  void setUp() {
    when(source.type()).thenReturn(PUB);
    when(journals.type()).thenReturn(SubjectType.JOURNAL);
    gateway =
        new SubjectGatewayImpl(
            repository,
            new SubjectSources(List.of(source, journals)),
            new RecordingTransactions(events),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void should_reject_unsupported_capability() {
    SubjectType unsupported = mock(SubjectType.class);
    when(unsupported.supports(SubjectCapability.COLLECT)).thenReturn(false);

    assertThatThrownBy(
            () -> gateway.resolveForInteraction(unsupported, "100", SubjectCapability.COLLECT))
        .isInstanceOf(SubjectCapabilityNotSupportedException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void should_reject_invalid_key_without_touching_storage() {
    assertThatThrownBy(() -> gateway.resolveForInteraction(PUB, "abc", SubjectCapability.COLLECT))
        .isInstanceOf(InvalidSubjectKeyException.class);
    verifyNoInteractions(repository);
    verify(source, never()).resolveByKey(anyLong());
  }

  @Test
  void should_return_local_active_subject_without_calling_source() {
    when(repository.findBySourceId(PUB, 100L))
        .thenReturn(Optional.of(subject(SubjectState.ACTIVE)));

    assertThat(gateway.resolveForInteraction(PUB, "100", SubjectCapability.COLLECT))
        .isEqualTo(SubjectId.of(7L));
    verify(source, never()).resolveByKey(anyLong());
  }

  @Test
  void should_reject_local_unavailable_subject() {
    when(repository.findBySourceId(PUB, 100L))
        .thenReturn(Optional.of(subject(SubjectState.UNAVAILABLE)));

    assertThatThrownBy(() -> gateway.resolveForInteraction(PUB, "100", SubjectCapability.COLLECT))
        .isInstanceOfSatisfying(
            SubjectNotFoundException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.SUBJECT_UNAVAILABLE));
    verify(source, never()).resolveByKey(anyLong());
  }

  /// 本地对象没有来源可以解析：本地查不到就是不存在，不调任何来源。
  @Test
  void should_reject_unregistered_local_object_without_any_source() {
    when(repository.findBySourceId(SubjectType.POST, 7L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> gateway.resolveForInteraction(SubjectType.POST, "7", SubjectCapability.DISCUSS))
        .isInstanceOfSatisfying(
            SubjectNotFoundException.class,
            ex ->
                assertThat(ex.getErrorTraits())
                    .doesNotContain(SubjectErrorTrait.SUBJECT_UNAVAILABLE));
    verify(source, never()).resolveByKey(anyLong());
    verify(journals, never()).resolveByKey(anyLong());
  }

  @Test
  void should_propagate_source_unavailable_without_writing() {
    SubjectSourceUnavailableException down =
        new SubjectSourceUnavailableException(PUB, new RuntimeException("down"));
    when(repository.findBySourceId(PUB, 100L)).thenReturn(Optional.empty());
    when(source.resolveByKey(100L)).thenThrow(down);

    assertThatThrownBy(() -> gateway.resolveForInteraction(PUB, "100", SubjectCapability.COLLECT))
        .isSameAs(down);
    verify(repository, never()).upsertResolved(any(), any(), any());
  }

  @Test
  void should_reject_missing_without_writing() {
    when(repository.findBySourceId(PUB, 100L)).thenReturn(Optional.empty());
    when(source.resolveByKey(100L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> gateway.resolveForInteraction(PUB, "100", SubjectCapability.COLLECT))
        .isInstanceOfSatisfying(
            SubjectNotFoundException.class,
            ex ->
                assertThat(ex.getErrorTraits())
                    .doesNotContain(SubjectErrorTrait.SUBJECT_UNAVAILABLE));
    verify(repository, never()).upsertResolved(any(), any(), any());
  }

  @Test
  void should_reject_soft_deleted_without_writing() {
    when(repository.findBySourceId(PUB, 100L)).thenReturn(Optional.empty());
    when(source.resolveByKey(100L))
        .thenReturn(Optional.of(SourceRecord.of(100L, PMID_1, SourceState.DELETED, SNAPSHOT)));

    assertThatThrownBy(() -> gateway.resolveForInteraction(PUB, "100", SubjectCapability.COLLECT))
        .isInstanceOfSatisfying(
            SubjectNotFoundException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.SUBJECT_UNAVAILABLE));
    verify(repository, never()).upsertResolved(any(), any(), any());
  }

  @Test
  void should_upsert_resolved_record_inside_transaction() {
    SourceRecord record = SourceRecord.of(100L, PMID_1, SourceState.ACTIVE, SNAPSHOT);
    when(repository.findBySourceId(PUB, 100L)).thenReturn(Optional.empty());
    when(source.resolveByKey(100L))
        .thenAnswer(
            inv -> {
              events.add("source");
              return Optional.of(record);
            });
    when(repository.upsertResolved(PUB, record, NOW))
        .thenAnswer(
            inv -> {
              events.add("upsert");
              return subject(SubjectState.ACTIVE);
            });

    assertThat(gateway.resolveForInteraction(PUB, "100", SubjectCapability.COLLECT))
        .isEqualTo(SubjectId.of(7L));
    assertThat(events).containsExactly("source", "tx-begin", "upsert", "tx-end");
  }

  /// 登记本地对象：交给仓储，快照时间取时钟。
  @Test
  void should_register_local_subject_at_clock_now() {
    SubjectOwner owner = SubjectOwner.of(9L, true);
    when(repository.insertLocal(SubjectType.POST, 7L, owner, SNAPSHOT, NOW))
        .thenReturn(post(SubjectState.ACTIVE));

    assertThat(gateway.registerLocal(SubjectType.POST, 7L, owner, SNAPSHOT))
        .isEqualTo(SubjectId.of(8L));
  }

  /// 改本地对象的状态与快照。
  @Test
  void should_update_local_subject() {
    when(repository.updateLocal(SubjectType.POST, 7L, SubjectState.UNAVAILABLE, SNAPSHOT, NOW))
        .thenReturn(true);

    gateway.updateLocal(SubjectType.POST, 7L, SubjectState.UNAVAILABLE, SNAPSHOT);

    verify(repository).updateLocal(SubjectType.POST, 7L, SubjectState.UNAVAILABLE, SNAPSHOT, NOW);
  }

  /// 外部对象只能经来源登记，不能当本地对象用。
  @Test
  void should_reject_external_type_for_local_methods() {
    assertThatThrownBy(() -> gateway.registerLocal(PUB, 7L, SubjectOwner.of(9L, false), SNAPSHOT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> gateway.updateLocal(SubjectType.JOURNAL, 7L, SubjectState.UNAVAILABLE, SNAPSHOT))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  /// 快照、状态、所有者都不能为空：传 null 会写进一行 JSON null 快照，之后批量读到它整页失败。
  @Test
  void should_reject_null_arguments_for_local_methods() {
    SubjectOwner owner = SubjectOwner.of(9L, true);

    assertThatThrownBy(() -> gateway.registerLocal(SubjectType.POST, 7L, owner, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> gateway.registerLocal(SubjectType.POST, 7L, null, SNAPSHOT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> gateway.updateLocal(SubjectType.POST, 7L, SubjectState.UNAVAILABLE, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> gateway.updateLocal(SubjectType.POST, 7L, null, SNAPSHOT))
        .isInstanceOf(NullPointerException.class);
    verifyNoInteractions(repository);
  }

  /// 改一个没登记过的本地对象是程序错误。
  @Test
  void should_fail_when_updating_unregistered_local() {
    when(repository.updateLocal(SubjectType.POST, 7L, SubjectState.UNAVAILABLE, SNAPSHOT, NOW))
        .thenReturn(false);

    assertThatThrownBy(
            () -> gateway.updateLocal(SubjectType.POST, 7L, SubjectState.UNAVAILABLE, SNAPSHOT))
        .isInstanceOf(IllegalStateException.class);
  }

  /// 帖子 7 的登记行。
  ///
  /// @param state 状态
  /// @return 聚合
  private static SubjectAggregate post(SubjectState state) {
    return SubjectAggregate.builder()
        .subjectId(SubjectId.of(8L))
        .type(SubjectType.POST)
        .naturalKey(NaturalKey.of(NaturalKeyScheme.POST, "7"))
        .sourceId(7L)
        .state(state)
        .snapshot(SNAPSHOT)
        .snapshotAt(NOW)
        .owner(SubjectOwner.of(9L, true))
        .build();
  }

  /// 一个已登记对象。
  ///
  /// @param state 状态
  /// @return 聚合
  private static SubjectAggregate subject(SubjectState state) {
    return SubjectAggregate.builder()
        .subjectId(SubjectId.of(7L))
        .type(PUB)
        .naturalKey(PMID_1)
        .sourceId(100L)
        .state(state)
        .snapshot(SNAPSHOT)
        .snapshotAt(NOW)
        .build();
  }

  /// 记下事务边界的替身。
  ///
  /// @param events 事件记录
  private record RecordingTransactions(List<String> events) implements TransactionOperations {

    /// 在「事务」里执行回调并记下边界。
    ///
    /// @param action 回调
    /// @param <T> 返回类型
    /// @return 回调结果
    @Override
    public <T> @Nullable T execute(TransactionCallback<T> action) {
      events.add("tx-begin");
      TransactionStatus status = new SimpleTransactionStatus();
      T result = action.doInTransaction(status);
      events.add("tx-end");
      return result;
    }

    /// 不返回结果的版本。
    ///
    /// @param action 回调
    @Override
    public void executeWithoutResult(Consumer<TransactionStatus> action) {
      execute(
          status -> {
            action.accept(status);
            return null;
          });
    }
  }
}
