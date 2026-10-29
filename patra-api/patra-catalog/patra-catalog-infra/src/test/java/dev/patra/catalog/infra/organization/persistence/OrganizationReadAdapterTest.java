package dev.patra.catalog.infra.organization.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.organization.model.read.OrganizationSuggestion;
import dev.patra.catalog.domain.organization.model.read.OrganizationSummary;
import dev.patra.catalog.infra.organization.persistence.dao.OrganizationDao;
import dev.patra.catalog.infra.organization.persistence.dao.OrganizationNameDao;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `OrganizationReadAdapter` 单元测试：LIKE 模式的拼接与转义、投影到读模型的转换。
///
/// SQL 的行为由 `OrganizationReadAdapterIT` 在真实 PostgreSQL 上验证。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationReadAdapter 单元测试")
class OrganizationReadAdapterTest {

  private final OrganizationNameDao nameDao = mock(OrganizationNameDao.class);
  private final OrganizationDao organizationDao = mock(OrganizationDao.class);
  private final OrganizationReadAdapter adapter =
      new OrganizationReadAdapter(nameDao, organizationDao);

  @Test
  @DisplayName("子串模式：三个模式都转义 ! % _")
  void should_build_substring_patterns_with_escaped_metacharacters() {
    // 含汉字才走 SUBSTRING 模式
    adapter.suggest(SuggestQuery.of("协和%_a!", 10));

    verify(nameDao).suggestBySubstring("协和%_a!", "%协和!%!_a!!%", "协和!%!_a!!%", "% 协和!%!_a!!%", 10);
  }

  @Test
  @DisplayName("2 码点的中日韩查询走短子串查询（规划器不能选 trigram 索引），不调普通子串查询")
  void should_use_short_substring_query_for_two_code_point_cjk_query() {
    adapter.suggest(SuggestQuery.of("协和", 10));

    verify(nameDao).suggestByShortSubstring("协和", "%协和%", "协和%", "% 协和%", 10);
    verify(nameDao, never()).suggestBySubstring(any(), any(), any(), any(), anyInt());
  }

  @Test
  @DisplayName("拉丁文字走词首模式，不调子串查询")
  void should_use_word_prefix_query_for_latin_text() {
    adapter.suggest(SuggestQuery.of("Mayo", 5));

    verify(nameDao).suggestByWordPrefix("Mayo", "Mayo%", "% Mayo%", 5);
    verify(nameDao, never()).suggestBySubstring(any(), any(), any(), any(), anyInt());
  }

  @Test
  @DisplayName("types 按空格拆成列表，可空字段原样保留")
  void should_split_types_and_keep_nullable_fields() {
    OrganizationSuggestRow row = mock(OrganizationSuggestRow.class);
    when(row.getRorId()).thenReturn("0aaaaaaa1");
    when(row.getDisplayName()).thenReturn("Peking Union Medical College Hospital");
    when(row.getMatchedName()).thenReturn("PUMCH");
    when(row.getTypes()).thenReturn("funder healthcare");
    when(nameDao.suggestByWordPrefix(any(), any(), any(), anyInt())).thenReturn(List.of(row));

    OrganizationSuggestion suggestion = adapter.suggest(SuggestQuery.of("PUMCH", 10)).getFirst();

    assertThat(suggestion.matchedName()).isEqualTo("PUMCH");
    assertThat(suggestion.organization().rorId()).isEqualTo("0aaaaaaa1");
    assertThat(suggestion.organization().types()).containsExactly("funder", "healthcare");
    assertThat(suggestion.organization().zhName()).isNull();
    assertThat(suggestion.organization().countryCode()).isNull();
  }

  @Test
  @DisplayName("types 为空串时映射成空列表")
  void should_map_empty_types_string_to_empty_list() {
    OrganizationSummaryRow row = mock(OrganizationSummaryRow.class);
    when(row.getRorId()).thenReturn("0aaaaaaa1");
    when(row.getTypes()).thenReturn("");
    when(organizationDao.findSummaryByRorId("0aaaaaaa1")).thenReturn(Optional.of(row));

    Optional<OrganizationSummary> summary = adapter.findByRorId("0aaaaaaa1");

    assertThat(summary).isPresent();
    assertThat(summary.get().types()).isEmpty();
  }
}
