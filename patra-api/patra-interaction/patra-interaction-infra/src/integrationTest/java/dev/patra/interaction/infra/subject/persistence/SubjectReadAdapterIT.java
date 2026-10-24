package dev.patra.interaction.infra.subject.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.subject.model.aggregate.SubjectAggregate;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.source.SourceRecord;
import dev.patra.interaction.domain.subject.model.source.SourceState;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.repository.SubjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 登记表读端口：只查本地，不合法的 key 跳过，视图带快照与状态。
class SubjectReadAdapterIT extends SubjectRegistryITSupport {

  private static final SubjectType PUB = SubjectType.PUBLICATION;
  private static final Instant NOW = Instant.parse("2026-10-24T06:30:00Z");

  @Autowired private SubjectReadPort readPort;
  @Autowired private SubjectRepository repository;

  @Test
  void should_find_ids_by_registered_keys() {
    SubjectAggregate a = upsert(active(100L, "1"));
    SubjectAggregate b = upsert(active(200L, "2"));

    Map<String, SubjectId> ids = readPort.findIdsByKeys(PUB, List.of("100", "200", "300"));

    assertThat(ids)
        .containsOnly(Map.entry("100", a.getSubjectId()), Map.entry("200", b.getSubjectId()));
  }

  @Test
  void should_skip_invalid_keys() {
    SubjectAggregate a = upsert(active(100L, "1"));

    assertThat(readPort.findIdsByKeys(PUB, List.of("abc", "0", "100")))
        .containsOnly(Map.entry("100", a.getSubjectId()));
  }

  @Test
  void should_return_views_with_snapshot_and_state() {
    SubjectAggregate year = upsert(record(100L, "1", "2019"));
    SubjectAggregate month = upsert(record(200L, "2", "2019-03"));
    SubjectAggregate day = upsert(record(300L, "3", "2019-03-15"));
    transactions.executeWithoutResult(
        s -> repository.saveReconciled(List.of(day.reconcile(Optional.empty(), NOW))));

    Map<SubjectId, SubjectView> views =
        readPort.findByIds(
            List.of(
                year.getSubjectId(), month.getSubjectId(), day.getSubjectId(), SubjectId.of(1L)));

    assertThat(views).hasSize(3);
    assertThat(views.get(year.getSubjectId()))
        .isEqualTo(
            SubjectView.builder()
                .subjectId(year.getSubjectId())
                .type(PUB)
                .key("100")
                .state(SubjectState.ACTIVE)
                .snapshot(SubjectSnapshot.of("Title 1", "Journal 1", "2019"))
                .build());
    assertThat(views.get(month.getSubjectId()).snapshot().publishedDate()).isEqualTo("2019-03");
    assertThat(views.get(day.getSubjectId()).key()).isNull();
    assertThat(views.get(day.getSubjectId()).state()).isEqualTo(SubjectState.UNAVAILABLE);
    assertThat(views.get(day.getSubjectId()).snapshot().publishedDate()).isEqualTo("2019-03-15");
  }

  @Test
  void should_reject_oversized_batches() {
    List<String> keys = LongStream.rangeClosed(1, 101).mapToObj(Long::toString).toList();
    List<SubjectId> ids = LongStream.rangeClosed(1, 5001).mapToObj(SubjectId::of).toList();

    assertThatThrownBy(() -> readPort.findIdsByKeys(PUB, keys))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> readPort.findByIds(ids)).isInstanceOf(IllegalArgumentException.class);
  }

  /// 带指定出版日期的来源记录。
  ///
  /// @param sourceId 来源 ID
  /// @param pmid PMID
  /// @param publishedDate 出版日期
  /// @return 来源记录
  private static SourceRecord record(long sourceId, String pmid, String publishedDate) {
    return SourceRecord.of(
        sourceId,
        pmid(pmid),
        SourceState.ACTIVE,
        SubjectSnapshot.of("Title " + pmid, "Journal " + pmid, publishedDate));
  }

  /// 在事务里 upsert。
  ///
  /// @param record 来源记录
  /// @return 写入后的聚合
  private SubjectAggregate upsert(SourceRecord record) {
    return transactions.execute(s -> repository.upsertResolved(PUB, record, NOW));
  }
}
