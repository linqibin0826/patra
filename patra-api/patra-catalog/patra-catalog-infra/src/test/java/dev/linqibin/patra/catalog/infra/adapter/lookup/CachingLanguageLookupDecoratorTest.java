package dev.linqibin.patra.catalog.infra.adapter.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.domain.port.lookup.LanguageLookupPort;
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

/// CachingLanguageLookupDecorator 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("CachingLanguageLookupDecorator")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class CachingLanguageLookupDecoratorTest {

  @Mock private LanguageLookupPort delegate;

  private CachingLanguageLookupDecorator decorator;

  @BeforeEach
  void setUp() {
    decorator = new CachingLanguageLookupDecorator(delegate);
  }

  @Nested
  @DisplayName("缓存命中")
  class CacheHitTest {

    @Test
    @DisplayName("第二次查询相同代码应该命中缓存")
    void should_hit_cache_on_second_query() {
      // given
      Set<String> codes = Set.of("eng");
      when(delegate.resolve(codes)).thenReturn(Map.of("eng", "en"));

      // when - 第一次查询
      decorator.resolve(codes);
      // when - 第二次查询
      Map<String, String> result = decorator.resolve(codes);

      // then
      assertThat(result).containsEntry("eng", "en");
      verify(delegate, times(1)).resolve(codes); // 只应调用一次
    }

    @Test
    @DisplayName("批量查询时应该只查询未缓存的代码")
    void should_only_query_uncached_codes() {
      // given - 先缓存 eng
      when(delegate.resolve(Set.of("eng"))).thenReturn(Map.of("eng", "en"));
      decorator.resolve(Set.of("eng"));

      // given - 现在查询 eng + chi，只有 chi 需要查询
      when(delegate.resolve(Set.of("chi"))).thenReturn(Map.of("chi", "zh"));

      // when
      Map<String, String> result = decorator.resolve(Set.of("eng", "chi"));

      // then
      assertThat(result)
          .containsEntry("eng", "en") // 来自缓存
          .containsEntry("chi", "zh"); // 来自 delegate
      verify(delegate, never()).resolve(Set.of("eng", "chi")); // 不应该查询完整集合
    }
  }

  @Nested
  @DisplayName("Negative Cache")
  class NegativeCacheTest {

    @Test
    @DisplayName("unknown 结果也应该被缓存")
    void should_cache_unknown_results() {
      // given
      Set<String> codes = Set.of("xyz");
      when(delegate.resolve(codes)).thenReturn(Map.of("xyz", LanguageLookupPort.UNKNOWN_LANGUAGE));

      // when - 两次查询
      decorator.resolve(codes);
      Map<String, String> result = decorator.resolve(codes);

      // then
      assertThat(result).containsEntry("xyz", LanguageLookupPort.UNKNOWN_LANGUAGE);
      verify(delegate, times(1)).resolve(codes); // 只应调用一次
    }
  }

  @Nested
  @DisplayName("边界情况")
  class EdgeCasesTest {

    @Test
    @DisplayName("空集合应该返回空 Map 且不调用 delegate")
    void should_return_empty_map_for_empty_input() {
      // when
      Map<String, String> result = decorator.resolve(Set.of());

      // then
      assertThat(result).isEmpty();
      verify(delegate, never()).resolve(anySet());
    }

    @Test
    @DisplayName("null 输入应该返回空 Map")
    void should_return_empty_map_for_null_input() {
      // when
      Map<String, String> result = decorator.resolve((Set<String>) null);

      // then
      assertThat(result).isEmpty();
      verify(delegate, never()).resolve(anySet());
    }

    @Test
    @DisplayName("所有代码都已缓存时不应调用 delegate")
    void should_not_call_delegate_when_all_cached() {
      // given - 先缓存
      when(delegate.resolve(Set.of("eng", "chi"))).thenReturn(Map.of("eng", "en", "chi", "zh"));
      decorator.resolve(Set.of("eng", "chi"));

      // when - 第二次查询相同代码
      Map<String, String> result = decorator.resolve(Set.of("eng", "chi"));

      // then
      assertThat(result).containsEntry("eng", "en").containsEntry("chi", "zh");
      verify(delegate, times(1)).resolve(anySet()); // 只应调用一次（第一次）
    }
  }

  @Nested
  @DisplayName("缓存统计")
  class StatsTest {

    @Test
    @DisplayName("getStats() 应该返回缓存统计信息")
    void should_return_cache_stats() {
      // given - 缓存一些数据
      when(delegate.resolve(anySet())).thenReturn(Map.of("eng", "en", "chi", "zh"));
      decorator.resolve(Set.of("eng", "chi"));

      // when
      String stats = decorator.getStats();

      // then
      assertThat(stats).contains("2"); // 至少包含缓存数量
    }

    @Test
    @DisplayName("clear() 应该清空缓存")
    void should_clear_cache() {
      // given - 先缓存
      when(delegate.resolve(Set.of("eng"))).thenReturn(Map.of("eng", "en"));
      decorator.resolve(Set.of("eng"));

      // when - 清空缓存后再次查询
      decorator.clear();
      when(delegate.resolve(Set.of("eng"))).thenReturn(Map.of("eng", "en"));
      decorator.resolve(Set.of("eng"));

      // then - delegate 应该被调用两次
      verify(delegate, times(2)).resolve(Set.of("eng"));
    }
  }
}
