package com.patra.catalog.infra.adapter.integration.wikidata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.vo.venue.VenueWikidataEnrichment;
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

/// WikidataEnrichmentQueryAdapter 单元测试。
///
/// **测试策略**：
///
/// - 正常委托：验证请求正确传递给 WikidataSparqlClient
/// - 空输入保护：null 和空集合不触发 Client 调用
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("WikidataEnrichmentQueryAdapter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class WikidataEnrichmentQueryAdapterTest {

  @Mock private WikidataSparqlClient wikidataSparqlClient;

  private WikidataEnrichmentQueryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new WikidataEnrichmentQueryAdapter(wikidataSparqlClient);
  }

  @Nested
  @DisplayName("正常委托测试")
  class NormalDelegationTest {

    @Test
    @DisplayName("应该正确委托给 WikidataSparqlClient 并返回富化结果")
    void shouldDelegateToSparqlClientAndReturnResult() {
      // Given
      Set<String> issnLs = Set.of("0028-0836", "0140-6736");
      Map<String, VenueWikidataEnrichment> expected =
          Map.of(
              "0028-0836",
                  VenueWikidataEnrichment.of(
                      "自然",
                      "http://commons.wikimedia.org/wiki/Special:FilePath/Nature.jpg",
                      "https://www.nature.com"),
              "0140-6736", VenueWikidataEnrichment.of("柳叶刀", null, null));
      when(wikidataSparqlClient.queryEnrichmentData(issnLs)).thenReturn(expected);

      // When
      Map<String, VenueWikidataEnrichment> result = adapter.findEnrichmentData(issnLs);

      // Then
      assertThat(result).isEqualTo(expected);
      assertThat(result.get("0028-0836").titleZh()).isEqualTo("自然");
      assertThat(result.get("0028-0836").imageUrl()).isNotNull();
      assertThat(result.get("0140-6736").titleZh()).isEqualTo("柳叶刀");
      assertThat(result.get("0140-6736").imageUrl()).isNull();
    }
  }

  @Nested
  @DisplayName("空输入保护测试")
  class EmptyInputTest {

    @Test
    @DisplayName("null 输入应返回空 Map")
    void shouldReturnEmptyMapForNullInput() {
      // When
      Map<String, VenueWikidataEnrichment> result = adapter.findEnrichmentData(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空集合应返回空 Map")
    void shouldReturnEmptyMapForEmptySet() {
      // When
      Map<String, VenueWikidataEnrichment> result = adapter.findEnrichmentData(Set.of());

      // Then
      assertThat(result).isEmpty();
    }
  }
}
