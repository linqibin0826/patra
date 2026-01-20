package com.patra.catalog.infra.adapter.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.port.repository.VenueRepository;
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

/// CachingVenueLookupDecorator 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("CachingVenueLookupDecorator")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class CachingVenueLookupDecoratorTest {

  @Mock private VenueRepository venueRepository;
  @Mock private VenueAggregate venueAggregate;

  private CachingVenueLookupDecorator decorator;

  private static final Long VENUE_ID = 123456L;
  private static final String NLM_ID = "101234567";
  private static final String ISSN = "1234-5678";
  private static final String ISSN_L = "1234-5679";

  @BeforeEach
  void setUp() {
    decorator = new CachingVenueLookupDecorator(venueRepository);
  }

  @Nested
  @DisplayName("缓存命中")
  class CacheHitTest {

    @Test
    @DisplayName("第二次查询应该命中缓存")
    void should_hit_cache_on_second_query() {
      // given
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // when - 第一次查询
      Optional<VenueId> result1 = decorator.findByNlmId(NLM_ID);
      // when - 第二次查询
      Optional<VenueId> result2 = decorator.findByNlmId(NLM_ID);

      // then
      assertThat(result1).isPresent();
      assertThat(result2).isPresent();
      assertThat(result1.get().value()).isEqualTo(VENUE_ID);
      // 只查询一次数据库
      verify(venueRepository, times(1)).findByNlmIds(any());
    }

    @Test
    @DisplayName("Negative Cache 应该避免重复查询不存在的标识符")
    void should_avoid_repeated_query_for_not_found_identifier() {
      // given
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of());

      // when - 多次查询
      Optional<VenueId> result1 = decorator.findByNlmId(NLM_ID);
      Optional<VenueId> result2 = decorator.findByNlmId(NLM_ID);
      Optional<VenueId> result3 = decorator.findByNlmId(NLM_ID);

      // then
      assertThat(result1).isEmpty();
      assertThat(result2).isEmpty();
      assertThat(result3).isEmpty();
      // 只查询一次数据库
      verify(venueRepository, times(1)).findByNlmIds(any());
    }
  }

  @Nested
  @DisplayName("二级索引预热")
  class SecondaryIndexWarmupTest {

    @Test
    @DisplayName("查询 NLM ID 时应该自动缓存 ISSN")
    void should_cache_issns_when_querying_by_nlm_id() {
      // given
      VenueIdentifier issnIdentifier = VenueIdentifier.forIssn(ISSN);
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of(issnIdentifier));
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // when - 先查询 NLM ID
      decorator.findByNlmId(NLM_ID);
      // when - 再查询 ISSN
      Optional<VenueId> result = decorator.findByIssn(ISSN);

      // then - ISSN 应该命中缓存
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
      // 不应该查询 findByIssns
      verify(venueRepository, times(0)).findByIssns(any());
    }
  }

  @Nested
  @DisplayName("warmup()")
  class WarmupTest {

    @Test
    @DisplayName("预热应该批量加载并缓存")
    void should_batch_load_and_cache() {
      // given
      String nlmId2 = "102345678";
      VenueAggregate venue2 = org.mockito.Mockito.mock(VenueAggregate.class);
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venue2.getId()).thenReturn(VenueId.of(999L));
      when(venue2.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID, nlmId2)))
          .thenReturn(Map.of(NLM_ID, venueAggregate, nlmId2, venue2));

      // when - 预热
      decorator.warmup(Set.of(NLM_ID, nlmId2));

      // then - 后续查询应该命中缓存
      Optional<VenueId> result1 = decorator.findByNlmId(NLM_ID);
      Optional<VenueId> result2 = decorator.findByNlmId(nlmId2);

      assertThat(result1).isPresent();
      assertThat(result2).isPresent();
      // 只有预热时调用一次
      verify(venueRepository, times(1)).findByNlmIds(any());
    }
  }

  @Nested
  @DisplayName("getStats() & clear()")
  class StatsAndClearTest {

    @Test
    @DisplayName("getStats 应该返回统计信息")
    void should_return_stats() {
      // when
      String stats = decorator.getStats();

      // then
      assertThat(stats).contains("VenueLookupCache");
      assertThat(stats).contains("nlmIdCache=0");
    }

    @Test
    @DisplayName("clear 应该清空缓存")
    void should_clear_cache() {
      // given - 先添加一些缓存
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));
      decorator.findByNlmId(NLM_ID);

      // when
      decorator.clear();

      // then - 再次查询应该重新查询数据库
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));
      decorator.findByNlmId(NLM_ID);

      verify(venueRepository, times(2)).findByNlmIds(Set.of(NLM_ID));
    }
  }

  @Nested
  @DisplayName("findByPriority()")
  class FindByPriorityTest {

    @Test
    @DisplayName("应该按优先级匹配并使用缓存")
    void should_find_by_priority_with_cache() {
      // given
      when(venueAggregate.getId()).thenReturn(VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // when
      Optional<VenueId> result = decorator.findByPriority(NLM_ID, List.of(ISSN));

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
    }
  }
}
