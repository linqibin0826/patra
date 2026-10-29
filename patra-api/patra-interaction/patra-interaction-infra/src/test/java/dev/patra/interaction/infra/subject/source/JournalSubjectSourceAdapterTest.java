package dev.patra.interaction.infra.subject.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.api.venue.dto.VenueResolutionResp;
import dev.patra.catalog.api.venue.dto.VenueResolveByIdsReq;
import dev.patra.catalog.api.venue.dto.VenueResolveByNlmIdsReq;
import dev.patra.catalog.api.venue.dto.VenueResolveResp;
import dev.patra.catalog.api.venue.dto.VenueStateResp;
import dev.patra.catalog.api.venue.endpoint.VenueEndpoint;
import dev.patra.interaction.domain.subject.exception.SubjectSourceUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.NaturalKeyScheme;
import dev.patra.interaction.domain.subject.model.enums.SourceState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.NaturalKey;
import dev.patra.interaction.domain.subject.model.vo.SourceRecord;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.tool.core.error.remote.RemoteCallException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.ResourceAccessException;

/// 期刊来源策略：首次登记的锚取主 NLM，对账按请求的 key 对回；快照取刊名全称与 MEDLINE 缩写；失败翻译。
class JournalSubjectSourceAdapterTest {

  private static final String TITLE = "Schweizerische medizinische Wochenschrift";

  private final VenueEndpoint endpoint = mock(VenueEndpoint.class);
  private final JournalSubjectSourceAdapter adapter = new JournalSubjectSourceAdapter(endpoint);

  @Test
  void should_serve_journal_type() {
    assertThat(adapter.type()).isEqualTo(SubjectType.JOURNAL);
  }

  /// 首次登记：锚是返回的主 NLM。
  @Test
  void should_anchor_first_resolution_on_primary_nlm() {
    stubIds(found("100", 100L, "0404401", VenueStateResp.ACTIVE));

    assertThat(adapter.resolveByKey(100L))
        .contains(
            SourceRecord.of(
                100L,
                nlm("0404401"),
                SourceState.ACTIVE,
                SubjectSnapshot.of(TITLE, null, null, "Swiss Med Wkly")));
  }

  /// 对账：请求的若是副 NLM，返回的 `nlmId` 是主 NLM；锚仍是请求的 key，不跟着变。
  @Test
  void should_anchor_reconciliation_on_requested_key_not_primary_nlm() {
    when(endpoint.resolveByNlmIds(VenueResolveByNlmIdsReq.of(List.of("0404401"))))
        .thenReturn(
            VenueResolveResp.of(
                List.of(found("0404401", 200L, "100970884", VenueStateResp.ACTIVE))));

    Map<NaturalKey, SourceRecord> result = adapter.resolveByNaturalKeys(List.of(nlm("0404401")));

    assertThat(result).containsOnlyKeys(nlm("0404401"));
    assertThat(result.get(nlm("0404401")).naturalKey()).isEqualTo(nlm("0404401"));
    assertThat(result.get(nlm("0404401")).sourceId()).isEqualTo(200L);
  }

  /// 不存在的行没有记录；按 ID 解析时主 NLM 为空就没有锚，也没有记录。
  @Test
  void should_drop_missing_rows_and_rows_without_anchor() {
    stubIds(found("100", 100L, null, VenueStateResp.ACTIVE));
    when(endpoint.resolveByNlmIds(any(VenueResolveByNlmIdsReq.class)))
        .thenReturn(
            VenueResolveResp.of(
                List.of(
                    VenueResolutionResp.missing("9999999"),
                    found("2985213R", 300L, "2985213R", VenueStateResp.ACTIVE))));

    assertThat(adapter.resolveByKey(100L)).isEmpty();
    assertThat(adapter.resolveByNaturalKeys(List.of(nlm("9999999"), nlm("2985213R"))))
        .containsOnlyKeys(nlm("2985213R"));
  }

  /// 软删的期刊照样返回，状态是 DELETED，快照照样有刊名。
  @Test
  void should_map_soft_deleted_state() {
    stubIds(found("100", 100L, "0404401", VenueStateResp.DELETED));

    SourceRecord record = adapter.resolveByKey(100L).orElseThrow();

    assertThat(record.state()).isEqualTo(SourceState.DELETED);
    assertThat(record.snapshot().title()).isEqualTo(TITLE);
  }

  @ParameterizedTest
  @MethodSource("failures")
  void should_translate_failures_to_source_unavailable(RuntimeException failure) {
    when(endpoint.resolveByIds(any(VenueResolveByIdsReq.class))).thenThrow(failure);
    when(endpoint.resolveByNlmIds(any(VenueResolveByNlmIdsReq.class))).thenThrow(failure);

    assertThatThrownBy(() -> adapter.resolveByKey(100L))
        .isInstanceOfSatisfying(
            SubjectSourceUnavailableException.class,
            ex -> assertThat(ex.getType()).isEqualTo(SubjectType.JOURNAL))
        .hasCause(failure);
    assertThatThrownBy(() -> adapter.resolveByNaturalKeys(List.of(nlm("0404401"))))
        .isInstanceOf(SubjectSourceUnavailableException.class)
        .hasCause(failure);
  }

  @Test
  void should_reject_batches_outside_1_to_100() {
    List<NaturalKey> keys = IntStream.rangeClosed(1, 101).mapToObj(i -> nlm("" + i)).toList();

    assertThatThrownBy(() -> adapter.resolveByNaturalKeys(keys))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.resolveByNaturalKeys(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(endpoint);
  }

  /// 三类失败：下游回了错误、连不上或超时、没有实例。
  ///
  /// @return 失败
  static Stream<RuntimeException> failures() {
    return Stream.of(
        new RemoteCallException(
            "CAT-0503",
            503,
            "remote",
            "POST /x",
            null,
            Map.of(),
            Set.of(StandardErrorTrait.DEP_UNAVAILABLE)),
        new ResourceAccessException("I/O error"),
        new IllegalStateException("No instances available for patra-catalog"));
  }

  /// 按 ID 解析的桩。
  ///
  /// @param item 结果
  private void stubIds(VenueResolutionResp item) {
    when(endpoint.resolveByIds(VenueResolveByIdsReq.of(List.of(100L))))
        .thenReturn(VenueResolveResp.of(List.of(item)));
  }

  /// 存在的一行。
  ///
  /// @param key 请求键
  /// @param id 期刊 ID
  /// @param nlmId 主 NLM
  /// @param state 状态
  /// @return 结果
  private static VenueResolutionResp found(
      String key, long id, String nlmId, VenueStateResp state) {
    return VenueResolutionResp.builder()
        .key(key)
        .exists(true)
        .state(state)
        .id(id)
        .nlmId(nlmId)
        .title(TITLE)
        .abbreviatedTitle("Swiss Med Wkly")
        .build();
  }

  /// NLM 身份锚。
  ///
  /// @param value NLM ID
  /// @return 身份锚
  private static NaturalKey nlm(String value) {
    return NaturalKey.of(NaturalKeyScheme.NLM, value);
  }
}
