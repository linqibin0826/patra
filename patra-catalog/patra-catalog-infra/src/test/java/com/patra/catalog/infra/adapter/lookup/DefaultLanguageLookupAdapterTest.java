package com.patra.catalog.infra.adapter.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.enums.DictionaryType;
import com.patra.catalog.domain.model.vo.common.SourceStandard;
import com.patra.catalog.domain.port.lookup.LanguageLookupPort;
import com.patra.catalog.domain.port.registry.DictionaryResolverPort;
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

/// DefaultLanguageLookupAdapter 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DefaultLanguageLookupAdapter")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class DefaultLanguageLookupAdapterTest {

  @Mock private DictionaryResolverPort dictionaryResolverPort;

  private DefaultLanguageLookupAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new DefaultLanguageLookupAdapter(dictionaryResolverPort);
  }

  @Nested
  @DisplayName("resolve(Set)")
  class ResolveBatchTest {

    @Test
    @DisplayName("空集合应该返回空 Map")
    void should_return_empty_map_when_input_is_empty() {
      // when
      Map<String, String> result = adapter.resolve(Set.of());

      // then
      assertThat(result).isEmpty();
      verify(dictionaryResolverPort, never()).resolve(any(), any(), any());
    }

    @Test
    @DisplayName("null 输入应该返回空 Map")
    void should_return_empty_map_when_input_is_null() {
      // when
      Map<String, String> result = adapter.resolve((Set<String>) null);

      // then
      assertThat(result).isEmpty();
      verify(dictionaryResolverPort, never()).resolve(any(), any(), any());
    }

    @Test
    @DisplayName("Registry 解析成功时应该返回解析结果")
    void should_return_resolved_languages_from_registry() {
      // given
      Set<String> codes = Set.of("eng", "chi", "jpn");
      when(dictionaryResolverPort.resolve(
              eq(DictionaryType.LANGUAGE), eq(SourceStandard.ISO_639_3), eq(codes)))
          .thenReturn(Map.of("eng", "en", "chi", "zh", "jpn", "ja"));

      // when
      Map<String, String> result = adapter.resolve(codes);

      // then
      assertThat(result)
          .containsEntry("eng", "en")
          .containsEntry("chi", "zh")
          .containsEntry("jpn", "ja");
    }

    @Test
    @DisplayName("Registry 部分解析成功时应该使用 fallback 补充")
    void should_use_fallback_when_registry_partially_resolves() {
      // given
      Set<String> codes = Set.of("eng", "chi");
      // Registry 只解析了 eng
      when(dictionaryResolverPort.resolve(
              eq(DictionaryType.LANGUAGE), eq(SourceStandard.ISO_639_3), eq(codes)))
          .thenReturn(Map.of("eng", "en"));

      // when
      Map<String, String> result = adapter.resolve(codes);

      // then
      assertThat(result)
          .containsEntry("eng", "en") // Registry 结果
          .containsEntry("chi", "zh"); // Fallback 结果
    }

    @Test
    @DisplayName("Registry 返回空时应该完全使用 fallback")
    void should_use_fallback_when_registry_returns_empty() {
      // given
      Set<String> codes = Set.of("eng", "chi");
      when(dictionaryResolverPort.resolve(any(), any(), any())).thenReturn(Map.of());

      // when
      Map<String, String> result = adapter.resolve(codes);

      // then
      assertThat(result).containsEntry("eng", "en").containsEntry("chi", "zh");
    }

    @Test
    @DisplayName("完全未知的语言代码应该返回 unknown")
    void should_return_unknown_for_completely_unknown_codes() {
      // given
      Set<String> codes = Set.of("xyz", "abc");
      when(dictionaryResolverPort.resolve(any(), any(), any())).thenReturn(Map.of());

      // when
      Map<String, String> result = adapter.resolve(codes);

      // then
      assertThat(result)
          .containsEntry("xyz", LanguageLookupPort.UNKNOWN_LANGUAGE)
          .containsEntry("abc", LanguageLookupPort.UNKNOWN_LANGUAGE);
    }

    @Test
    @DisplayName("混合情况：Registry + fallback + unknown")
    void should_handle_mixed_scenario() {
      // given
      Set<String> codes = Set.of("eng", "fre", "xyz");
      // Registry 解析 eng
      when(dictionaryResolverPort.resolve(
              eq(DictionaryType.LANGUAGE), eq(SourceStandard.ISO_639_3), eq(codes)))
          .thenReturn(Map.of("eng", "en"));

      // when
      Map<String, String> result = adapter.resolve(codes);

      // then
      assertThat(result)
          .containsEntry("eng", "en") // Registry 结果
          .containsEntry("fre", "fr") // Fallback 结果
          .containsEntry("xyz", LanguageLookupPort.UNKNOWN_LANGUAGE); // 未知
    }
  }

  @Nested
  @DisplayName("resolve(String)")
  class ResolveSingleTest {

    @Test
    @DisplayName("空字符串应该返回 unknown")
    void should_return_unknown_when_code_is_blank() {
      // when
      String result = adapter.resolve("  ");

      // then
      assertThat(result).isEqualTo(LanguageLookupPort.UNKNOWN_LANGUAGE);
      verify(dictionaryResolverPort, never()).resolve(any(), any(), any());
    }

    @Test
    @DisplayName("null 应该返回 unknown")
    void should_return_unknown_when_code_is_null() {
      // when
      String result = adapter.resolve((String) null);

      // then
      assertThat(result).isEqualTo(LanguageLookupPort.UNKNOWN_LANGUAGE);
      verify(dictionaryResolverPort, never()).resolve(any(), any(), any());
    }

    @Test
    @DisplayName("有效代码应该返回解析结果")
    void should_return_resolved_language() {
      // given
      when(dictionaryResolverPort.resolve(
              eq(DictionaryType.LANGUAGE), eq(SourceStandard.ISO_639_3), eq(Set.of("eng"))))
          .thenReturn(Map.of("eng", "en"));

      // when
      String result = adapter.resolve("eng");

      // then
      assertThat(result).isEqualTo("en");
    }
  }

  @Nested
  @DisplayName("常用语言 fallback 映射")
  class FallbackMappingTest {

    @Test
    @DisplayName("常用语言应该有正确的 fallback 映射")
    void should_have_correct_fallback_for_common_languages() {
      // given - Registry 返回空，强制使用 fallback
      when(dictionaryResolverPort.resolve(any(), any(), any())).thenReturn(Map.of());

      // when & then
      assertThat(adapter.resolve("eng")).isEqualTo("en");
      assertThat(adapter.resolve("chi")).isEqualTo("zh");
      assertThat(adapter.resolve("jpn")).isEqualTo("ja");
      assertThat(adapter.resolve("fre")).isEqualTo("fr");
      assertThat(adapter.resolve("ger")).isEqualTo("de");
      assertThat(adapter.resolve("spa")).isEqualTo("es");
      assertThat(adapter.resolve("ita")).isEqualTo("it");
      assertThat(adapter.resolve("por")).isEqualTo("pt");
      assertThat(adapter.resolve("rus")).isEqualTo("ru");
      assertThat(adapter.resolve("kor")).isEqualTo("ko");
      assertThat(adapter.resolve("ara")).isEqualTo("ar");
      assertThat(adapter.resolve("hin")).isEqualTo("hi");
    }
  }
}
