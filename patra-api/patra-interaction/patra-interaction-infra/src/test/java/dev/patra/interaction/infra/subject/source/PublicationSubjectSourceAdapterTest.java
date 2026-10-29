package dev.patra.interaction.infra.subject.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.catalog.api.publication.dto.PublicationResolutionResp;
import dev.patra.catalog.api.publication.dto.PublicationResolveByIdsReq;
import dev.patra.catalog.api.publication.dto.PublicationResolveByPmidsReq;
import dev.patra.catalog.api.publication.dto.PublicationResolveResp;
import dev.patra.catalog.api.publication.dto.PublicationStateResp;
import dev.patra.catalog.api.publication.endpoint.PublicationEndpoint;
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

/// 文献来源策略：catalog 解析结果的映射与失败翻译。
class PublicationSubjectSourceAdapterTest {

  private final PublicationEndpoint endpoint = mock(PublicationEndpoint.class);
  private final PublicationSubjectSourceAdapter adapter =
      new PublicationSubjectSourceAdapter(endpoint);

  @Test
  void should_serve_publication_type() {
    assertThat(adapter.type()).isEqualTo(SubjectType.PUBLICATION);
  }

  @Test
  void should_map_active_record() {
    stubIds(found("100", 100L, "1", PublicationStateResp.ACTIVE, "2019-03"));

    assertThat(adapter.resolveByKey(100L))
        .contains(
            SourceRecord.of(
                100L,
                pmid("1"),
                SourceState.ACTIVE,
                SubjectSnapshot.of("Title 1", "Journal 1", "2019-03", null)));
  }

  @Test
  void should_map_soft_deleted_record() {
    stubIds(found("100", 100L, "1", PublicationStateResp.DELETED, "2019"));

    SourceRecord record = adapter.resolveByKey(100L).orElseThrow();

    assertThat(record.state()).isEqualTo(SourceState.DELETED);
    assertThat(record.snapshot())
        .isEqualTo(SubjectSnapshot.of("Title 1", "Journal 1", "2019", null));
  }

  @Test
  void should_treat_missing_as_empty() {
    stubIds(PublicationResolutionResp.missing("100"));

    assertThat(adapter.resolveByKey(100L)).isEmpty();
  }

  @Test
  void should_treat_null_pmid_as_empty() {
    stubIds(found("100", 100L, null, PublicationStateResp.ACTIVE, "2019"));

    assertThat(adapter.resolveByKey(100L)).isEmpty();
  }

  /// catalog 的 PMID 列没有格式约束：坏 PMID 与没有 PMID 一样跳过，不让一行坏数据中止整次对账。
  @Test
  void should_skip_malformed_pmid() {
    stubIds(found("100", 100L, "01", PublicationStateResp.ACTIVE, "2019"));
    when(endpoint.resolveByPmids(any(PublicationResolveByPmidsReq.class)))
        .thenReturn(
            PublicationResolveResp.of(
                List.of(
                    found("1", 100L, "1", PublicationStateResp.ACTIVE, "2019"),
                    found("2", 200L, "PMC2", PublicationStateResp.ACTIVE, "2019"))));

    assertThat(adapter.resolveByKey(100L)).isEmpty();
    assertThat(adapter.resolveByNaturalKeys(List.of(pmid("1"), pmid("2"))))
        .containsOnlyKeys(pmid("1"));
  }

  @Test
  void should_match_results_by_pmid_not_by_position() {
    when(endpoint.resolveByPmids(any(PublicationResolveByPmidsReq.class)))
        .thenReturn(
            PublicationResolveResp.of(
                List.of(
                    found("2", 200L, "2", PublicationStateResp.ACTIVE, "2020"),
                    PublicationResolutionResp.missing("3"),
                    found("1", 100L, "1", PublicationStateResp.DELETED, "2019"))));

    Map<NaturalKey, SourceRecord> result =
        adapter.resolveByNaturalKeys(List.of(pmid("1"), pmid("2"), pmid("3")));

    assertThat(result).containsOnlyKeys(pmid("1"), pmid("2"));
    assertThat(result.get(pmid("1")).sourceId()).isEqualTo(100L);
    assertThat(result.get(pmid("1")).state()).isEqualTo(SourceState.DELETED);
    assertThat(result.get(pmid("2")).sourceId()).isEqualTo(200L);
  }

  @Test
  void should_drop_malformed_published_date_with_warning() {
    stubIds(found("100", 100L, "1", PublicationStateResp.ACTIVE, "2019-3"));

    SubjectSnapshot snapshot = adapter.resolveByKey(100L).orElseThrow().snapshot();

    assertThat(snapshot).isEqualTo(SubjectSnapshot.of("Title 1", "Journal 1", null, null));
  }

  @ParameterizedTest
  @MethodSource("failures")
  void should_translate_failures_to_unavailable(RuntimeException failure) {
    when(endpoint.resolveByIds(any(PublicationResolveByIdsReq.class))).thenThrow(failure);
    when(endpoint.resolveByPmids(any(PublicationResolveByPmidsReq.class))).thenThrow(failure);

    assertThatThrownBy(() -> adapter.resolveByKey(100L))
        .isInstanceOf(SubjectSourceUnavailableException.class)
        .hasCause(failure);
    assertThatThrownBy(() -> adapter.resolveByNaturalKeys(List.of(pmid("1"))))
        .isInstanceOf(SubjectSourceUnavailableException.class)
        .hasCause(failure);
  }

  @Test
  void should_reject_more_than_100_natural_keys() {
    List<NaturalKey> keys = IntStream.rangeClosed(1, 101).mapToObj(i -> pmid("" + i)).toList();

    assertThatThrownBy(() -> adapter.resolveByNaturalKeys(keys))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(endpoint);
  }

  /// 三类失败：下游回了错误、连不上或超时、没有实例。
  ///
  /// @return 失败
  static Stream<RuntimeException> failures() {
    return Stream.of(
        remote(503, StandardErrorTrait.DEP_UNAVAILABLE),
        remote(422, StandardErrorTrait.RULE_VIOLATION),
        new ResourceAccessException("I/O error"),
        new IllegalStateException("No instances available for patra-catalog"));
  }

  /// 按 ID 解析的桩。
  ///
  /// @param item 结果
  private void stubIds(PublicationResolutionResp item) {
    when(endpoint.resolveByIds(PublicationResolveByIdsReq.of(List.of(100L))))
        .thenReturn(PublicationResolveResp.of(List.of(item)));
  }

  /// 存在的一行。
  ///
  /// @param key 请求键
  /// @param id 主键
  /// @param pmid PMID
  /// @param state 状态
  /// @param publishedDate 出版日期
  /// @return 结果
  private static PublicationResolutionResp found(
      String key, long id, String pmid, PublicationStateResp state, String publishedDate) {
    return PublicationResolutionResp.builder()
        .key(key)
        .exists(true)
        .state(state)
        .id(id)
        .pmid(pmid)
        .title("Title " + (pmid == null ? key : pmid))
        .journal("Journal " + (pmid == null ? key : pmid))
        .year(2019)
        .publishedDate(publishedDate)
        .build();
  }

  /// PMID 身份锚。
  ///
  /// @param value PMID
  /// @return 身份锚
  private static NaturalKey pmid(String value) {
    return NaturalKey.of(NaturalKeyScheme.PMID, value);
  }

  /// 一个带语义特征的远程调用异常。
  ///
  /// @param status HTTP 状态
  /// @param trait 特征
  /// @return 异常
  private static RemoteCallException remote(int status, StandardErrorTrait trait) {
    return new RemoteCallException(
        "CAT-0" + status, status, "remote", "POST /x", null, Map.of(), Set.of(trait));
  }
}
