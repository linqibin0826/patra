package com.patra.catalog.infra.adapter.integration.wikidata;

import static org.assertj.core.api.Assertions.assertThat;
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

/// WikidataChineseTitleQueryAdapter 单元测试。
///
/// **测试策略**：
///
/// - 正常委托：验证请求正确传递给 WikidataSparqlClient
/// - 异常隔离：验证 WikidataSparqlClient 异常不传播
/// - 空输入保护：null 和空集合
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("WikidataChineseTitleQueryAdapter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class WikidataChineseTitleQueryAdapterTest {

  @Mock private WikidataSparqlClient wikidataSparqlClient;

  private WikidataChineseTitleQueryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new WikidataChineseTitleQueryAdapter(wikidataSparqlClient);
  }

  @Nested
  @DisplayName("正常委托测试")
  class NormalDelegationTest {

    @Test
    @DisplayName("应该正确委托给 WikidataSparqlClient 并返回结果")
    void shouldDelegateToSparqlClientAndReturnResult() {
      // Given
      Set<String> issnLs = Set.of("0028-0836", "0140-6736");
      Map<String, String> expected = Map.of("0028-0836", "自然", "0140-6736", "柳叶刀");
      when(wikidataSparqlClient.queryChineseTitles(issnLs)).thenReturn(expected);

      // When
      Map<String, String> result = adapter.findChineseTitles(issnLs);

      // Then
      assertThat(result).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("异常隔离测试")
  class ErrorIsolationTest {

    @Test
    @DisplayName("WikidataSparqlClient 异常应被捕获并返回空 Map")
    void shouldReturnEmptyMapWhenClientThrowsException() {
      // Given
      Set<String> issnLs = Set.of("0028-0836");
      when(wikidataSparqlClient.queryChineseTitles(issnLs))
          .thenThrow(new RuntimeException("Connection timeout"));

      // When
      Map<String, String> result = adapter.findChineseTitles(issnLs);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("空输入保护测试")
  class EmptyInputTest {

    @Test
    @DisplayName("null 输入应返回空 Map")
    void shouldReturnEmptyMapForNullInput() {
      // When
      Map<String, String> result = adapter.findChineseTitles(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空集合应返回空 Map")
    void shouldReturnEmptyMapForEmptySet() {
      // When
      Map<String, String> result = adapter.findChineseTitles(Set.of());

      // Then
      assertThat(result).isEmpty();
    }
  }
}
