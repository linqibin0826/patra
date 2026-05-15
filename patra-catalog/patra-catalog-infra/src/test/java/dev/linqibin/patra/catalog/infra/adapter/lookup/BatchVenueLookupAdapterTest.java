package dev.linqibin.patra.catalog.infra.adapter.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
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

/// BatchVenueLookupAdapter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("BatchVenueLookupAdapter")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class BatchVenueLookupAdapterTest {

  @Mock private VenueRepository venueRepository;
  @Mock private VenueAggregate venueAggregate;

  private BatchVenueLookupAdapter batchAdapter;

  private static final Long VENUE_ID = 123456L;
  private static final String NLM_ID = "101234567";
  private static final String ISSN = "1234-5678";

  @BeforeEach
  void setUp() {
    batchAdapter = new BatchVenueLookupAdapter(venueRepository);
  }

  @Nested
  @DisplayName("委托功能")
  class DelegationTest {

    @Test
    @DisplayName("findByNlmId 应该委托给 CachingDecorator")
    void should_delegate_find_by_nlm_id() {
      // given
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // when
      Optional<VenueId> result = batchAdapter.findByNlmId(NLM_ID);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
    }

    @Test
    @DisplayName("findByIssn 应该委托给 CachingDecorator")
    void should_delegate_find_by_issn() {
      // given
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByIssns(Set.of(ISSN))).thenReturn(Map.of(ISSN, venueAggregate));

      // when
      Optional<VenueId> result = batchAdapter.findByIssn(ISSN);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
    }

    @Test
    @DisplayName("findByPriority 应该委托给 CachingDecorator")
    void should_delegate_find_by_priority() {
      // given
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // when
      Optional<VenueId> result = batchAdapter.findByPriority(NLM_ID, List.of(ISSN));

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
    }
  }

  @Nested
  @DisplayName("缓存功能")
  class CachingTest {

    @Test
    @DisplayName("重复查询应该命中缓存")
    void should_hit_cache_on_repeated_query() {
      // given
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // when - 多次查询
      batchAdapter.findByNlmId(NLM_ID);
      batchAdapter.findByNlmId(NLM_ID);
      batchAdapter.findByNlmId(NLM_ID);

      // then - 只查询一次数据库
      verify(venueRepository, times(1)).findByNlmIds(any());
    }
  }

  @Nested
  @DisplayName("预热功能")
  class WarmupTest {

    @Test
    @DisplayName("warmup 应该批量加载缓存")
    void should_batch_load_on_warmup() {
      // given
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // when
      batchAdapter.warmup(Set.of(NLM_ID));

      // then - 后续查询应该命中缓存
      batchAdapter.findByNlmId(NLM_ID);
      verify(venueRepository, times(1)).findByNlmIds(any());
    }
  }

  @Nested
  @DisplayName("统计功能")
  class StatsTest {

    @Test
    @DisplayName("getStats 应该返回缓存统计信息")
    void should_return_cache_stats() {
      // when
      String stats = batchAdapter.getStats();

      // then
      assertThat(stats).contains("VenueLookupCache");
    }
  }
}
