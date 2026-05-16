package dev.linqibin.patra.catalog.infra.adapter.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.domain.model.aggregate.VenueAggregate;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueId;
import dev.linqibin.patra.catalog.domain.port.repository.VenueRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// DefaultVenueLookupAdapter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DefaultVenueLookupAdapter")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class DefaultVenueLookupAdapterTest {

  @Mock private VenueRepository venueRepository;

  @Mock private VenueAggregate venueAggregate;

  private DefaultVenueLookupAdapter adapter;

  private static final Long VENUE_ID = 123456L;
  private static final String NLM_ID = "101234567";
  private static final String ISSN = "1234-5678";

  @BeforeEach
  void setUp() {
    adapter = new DefaultVenueLookupAdapter(venueRepository);
  }

  @Nested
  @DisplayName("findByNlmId()")
  class FindByNlmIdTest {

    @Test
    @DisplayName("空 NLM ID 应该返回 empty")
    void should_return_empty_when_nlm_id_is_null() {
      // when
      Optional<VenueId> result = adapter.findByNlmId(null);

      // then
      assertThat(result).isEmpty();
      verify(venueRepository, never()).findByNlmIds(any());
    }

    @Test
    @DisplayName("空白 NLM ID 应该返回 empty")
    void should_return_empty_when_nlm_id_is_blank() {
      // when
      Optional<VenueId> result = adapter.findByNlmId("  ");

      // then
      assertThat(result).isEmpty();
      verify(venueRepository, never()).findByNlmIds(any());
    }

    @Test
    @DisplayName("存在的 NLM ID 应该返回 VenueId")
    void should_return_venue_id_when_nlm_id_exists() {
      // given
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // when
      Optional<VenueId> result = adapter.findByNlmId(NLM_ID);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
    }

    @Test
    @DisplayName("不存在的 NLM ID 应该返回 empty")
    void should_return_empty_when_nlm_id_not_exists() {
      // given
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of());

      // when
      Optional<VenueId> result = adapter.findByNlmId(NLM_ID);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByIssn()")
  class FindByIssnTest {

    @Test
    @DisplayName("空 ISSN 应该返回 empty")
    void should_return_empty_when_issn_is_null() {
      // when
      Optional<VenueId> result = adapter.findByIssn(null);

      // then
      assertThat(result).isEmpty();
      verify(venueRepository, never()).findByIssns(any());
    }

    @Test
    @DisplayName("存在的 ISSN 应该返回 VenueId")
    void should_return_venue_id_when_issn_exists() {
      // given
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueRepository.findByIssns(Set.of(ISSN))).thenReturn(Map.of(ISSN, venueAggregate));

      // when
      Optional<VenueId> result = adapter.findByIssn(ISSN);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
    }

    @Test
    @DisplayName("不存在的 ISSN 应该返回 empty")
    void should_return_empty_when_issn_not_exists() {
      // given
      when(venueRepository.findByIssns(Set.of(ISSN))).thenReturn(Map.of());

      // when
      Optional<VenueId> result = adapter.findByIssn(ISSN);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByPriority()")
  class FindByPriorityTest {

    @Test
    @DisplayName("NLM ID 匹配时应该优先返回")
    void should_return_venue_id_by_nlm_id_first() {
      // given
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // when
      Optional<VenueId> result = adapter.findByPriority(NLM_ID, List.of(ISSN));

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
      // NLM ID 匹配成功后不应该查询 ISSN
      verify(venueRepository, never()).findByIssns(any());
    }

    @Test
    @DisplayName("NLM ID 不匹配时应该按 ISSN 顺序匹配")
    void should_return_venue_id_by_issn_when_nlm_id_not_found() {
      // given
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueRepository.findByIssns(Set.of(ISSN))).thenReturn(Map.of(ISSN, venueAggregate));

      // when
      Optional<VenueId> result = adapter.findByPriority(NLM_ID, List.of(ISSN));

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
    }

    @Test
    @DisplayName("所有标识符都不匹配时应该返回 empty")
    void should_return_empty_when_all_identifiers_not_found() {
      // given
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());
      when(venueRepository.findByIssns(any())).thenReturn(Map.of());

      // when
      Optional<VenueId> result = adapter.findByPriority(NLM_ID, List.of(ISSN));

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ISSN 列表为 null 时应该只尝试 NLM ID")
    void should_only_try_nlm_id_when_issns_is_null() {
      // given
      when(venueRepository.findByNlmIds(any())).thenReturn(Map.of());

      // when
      Optional<VenueId> result = adapter.findByPriority(NLM_ID, null);

      // then
      assertThat(result).isEmpty();
      verify(venueRepository, never()).findByIssns(any());
    }
  }
}
