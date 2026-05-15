package dev.linqibin.patra.catalog.infra.adapter.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
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

/// BatchLanguageLookupAdapter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("BatchLanguageLookupAdapter")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class BatchLanguageLookupAdapterTest {

  @Mock private DefaultLanguageLookupAdapter defaultAdapter;

  private BatchLanguageLookupAdapter batchAdapter;

  @BeforeEach
  void setUp() {
    batchAdapter = new BatchLanguageLookupAdapter(defaultAdapter);
  }

  @Nested
  @DisplayName("基本功能")
  class BasicFunctionalityTest {

    @Test
    @DisplayName("应该委托给内部装饰器解析语言代码")
    void should_delegate_to_caching_decorator() {
      // given
      Set<String> codes = Set.of("eng", "chi");
      when(defaultAdapter.resolve(codes)).thenReturn(Map.of("eng", "en", "chi", "zh"));

      // when
      Map<String, String> result = batchAdapter.resolve(codes);

      // then
      assertThat(result).containsEntry("eng", "en").containsEntry("chi", "zh");
    }

    @Test
    @DisplayName("应该支持单个代码解析")
    void should_support_single_code_resolution() {
      // given
      when(defaultAdapter.resolve(Set.of("eng"))).thenReturn(Map.of("eng", "en"));

      // when
      String result = batchAdapter.resolve("eng");

      // then
      assertThat(result).isEqualTo("en");
    }
  }

  @Nested
  @DisplayName("缓存行为")
  class CachingBehaviorTest {

    @Test
    @DisplayName("第二次查询相同代码应该命中缓存")
    void should_hit_cache_on_second_query() {
      // given
      Set<String> codes = Set.of("eng");
      when(defaultAdapter.resolve(codes)).thenReturn(Map.of("eng", "en"));

      // when - 两次查询
      batchAdapter.resolve(codes);
      Map<String, String> result = batchAdapter.resolve(codes);

      // then
      assertThat(result).containsEntry("eng", "en");
      verify(defaultAdapter, times(1)).resolve(codes); // 只调用一次
    }

    @Test
    @DisplayName("批量查询时应该只查询未缓存的代码")
    void should_only_query_uncached_codes() {
      // given - 先缓存 eng
      when(defaultAdapter.resolve(Set.of("eng"))).thenReturn(Map.of("eng", "en"));
      batchAdapter.resolve(Set.of("eng"));

      // given - 现在查询 eng + chi
      when(defaultAdapter.resolve(Set.of("chi"))).thenReturn(Map.of("chi", "zh"));

      // when
      Map<String, String> result = batchAdapter.resolve(Set.of("eng", "chi"));

      // then
      assertThat(result).containsEntry("eng", "en").containsEntry("chi", "zh");
    }
  }

  @Nested
  @DisplayName("统计功能")
  class StatsTest {

    @Test
    @DisplayName("getStats() 应该返回缓存统计信息")
    void should_return_cache_stats() {
      // given
      when(defaultAdapter.resolve(anySet())).thenReturn(Map.of("eng", "en"));
      batchAdapter.resolve(Set.of("eng"));

      // when
      String stats = batchAdapter.getStats();

      // then
      assertThat(stats).isNotEmpty();
      assertThat(stats).contains("1"); // 至少包含缓存数量
    }
  }
}
