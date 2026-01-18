package com.patra.catalog.infra.adapter.batch.publication.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.port.repository.VenueRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// VenueCache 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueCache")
@ExtendWith(MockitoExtension.class)
class VenueCacheTest {

  @Mock private VenueRepository venueRepository;

  @Mock private VenueAggregate venueAggregate;

  private VenueCache venueCache;

  private static final String NLM_ID = "101234567";
  private static final String ISSN = "1234-5678";
  private static final Long VENUE_ID = 1L;

  @BeforeEach
  void setUp() {
    venueCache = new VenueCache(venueRepository);
  }

  @Nested
  @DisplayName("findByNlmId()")
  class FindByNlmIdTest {

    @Test
    @DisplayName("缓存命中时应直接返回，不查询数据库")
    void should_return_cached_value_without_querying_database() {
      // given - 预热缓存
      when(venueAggregate.getId())
          .thenReturn(com.patra.catalog.domain.model.vo.venue.VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // 第一次查询会访问数据库
      venueCache.findByNlmId(NLM_ID);

      // when - 第二次查询
      Optional<VenueId> result = venueCache.findByNlmId(NLM_ID);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
      // 只查询了一次数据库
      verify(venueRepository, times(1)).findByNlmIds(anyCollection());
    }

    @Test
    @DisplayName("缓存未命中时应查询数据库并缓存结果")
    void should_query_database_and_cache_result_on_cache_miss() {
      // given
      when(venueAggregate.getId())
          .thenReturn(com.patra.catalog.domain.model.vo.venue.VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // when
      Optional<VenueId> result = venueCache.findByNlmId(NLM_ID);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
      verify(venueRepository).findByNlmIds(Set.of(NLM_ID));
    }

    @Test
    @DisplayName("未找到时应缓存 not found 状态，避免重复查询")
    void should_cache_not_found_status_to_avoid_repeated_queries() {
      // given
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of());

      // 第一次查询
      venueCache.findByNlmId(NLM_ID);

      // when - 第二次查询
      Optional<VenueId> result = venueCache.findByNlmId(NLM_ID);

      // then
      assertThat(result).isEmpty();
      // 只查询了一次数据库
      verify(venueRepository, times(1)).findByNlmIds(anyCollection());
    }

    @Test
    @DisplayName("null 或空白 NLM ID 应直接返回 empty")
    void should_return_empty_for_null_or_blank_nlm_id() {
      assertThat(venueCache.findByNlmId(null)).isEmpty();
      assertThat(venueCache.findByNlmId("")).isEmpty();
      assertThat(venueCache.findByNlmId("  ")).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByIssn()")
  class FindByIssnTest {

    @Test
    @DisplayName("缓存命中时应直接返回，不查询数据库")
    void should_return_cached_value_without_querying_database() {
      // given
      when(venueAggregate.getId())
          .thenReturn(com.patra.catalog.domain.model.vo.venue.VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByIssns(Set.of(ISSN))).thenReturn(Map.of(ISSN, venueAggregate));

      venueCache.findByIssn(ISSN);

      // when
      Optional<VenueId> result = venueCache.findByIssn(ISSN);

      // then
      assertThat(result).isPresent();
      verify(venueRepository, times(1)).findByIssns(anyCollection());
    }

    @Test
    @DisplayName("null 或空白 ISSN 应直接返回 empty")
    void should_return_empty_for_null_or_blank_issn() {
      assertThat(venueCache.findByIssn(null)).isEmpty();
      assertThat(venueCache.findByIssn("")).isEmpty();
      assertThat(venueCache.findByIssn("  ")).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByPriority()")
  class FindByPriorityTest {

    @Test
    @DisplayName("NLM ID 匹配成功时应直接返回，不尝试 ISSN")
    void should_return_venue_when_nlm_id_matches() {
      // given
      when(venueAggregate.getId())
          .thenReturn(com.patra.catalog.domain.model.vo.venue.VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      // when
      Optional<VenueId> result = venueCache.findByPriority(NLM_ID, List.of(ISSN));

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
      // 不应查询 ISSN
      verify(venueRepository).findByNlmIds(anyCollection());
    }

    @Test
    @DisplayName("NLM ID 未匹配时应尝试 ISSN")
    void should_try_issn_when_nlm_id_not_matched() {
      // given
      when(venueAggregate.getId())
          .thenReturn(com.patra.catalog.domain.model.vo.venue.VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of());
      when(venueRepository.findByIssns(Set.of(ISSN))).thenReturn(Map.of(ISSN, venueAggregate));

      // when
      Optional<VenueId> result = venueCache.findByPriority(NLM_ID, List.of(ISSN));

      // then
      assertThat(result).isPresent();
      assertThat(result.get().value()).isEqualTo(VENUE_ID);
    }

    @Test
    @DisplayName("所有标识符都未匹配时应返回 empty")
    void should_return_empty_when_all_identifiers_not_matched() {
      // given
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of());
      when(venueRepository.findByIssns(Set.of(ISSN))).thenReturn(Map.of());

      // when
      Optional<VenueId> result = venueCache.findByPriority(NLM_ID, List.of(ISSN));

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("clear()")
  class ClearTest {

    @Test
    @DisplayName("清空后应重新查询数据库")
    void should_query_database_again_after_clear() {
      // given
      when(venueAggregate.getId())
          .thenReturn(com.patra.catalog.domain.model.vo.venue.VenueId.of(VENUE_ID));
      when(venueAggregate.getIdentifiers()).thenReturn(List.of());
      when(venueRepository.findByNlmIds(Set.of(NLM_ID))).thenReturn(Map.of(NLM_ID, venueAggregate));

      venueCache.findByNlmId(NLM_ID);

      // when
      venueCache.clear();
      venueCache.findByNlmId(NLM_ID);

      // then - 应查询两次数据库
      verify(venueRepository, times(2)).findByNlmIds(anyCollection());
    }
  }
}
