package dev.patra.catalog.infra.mesh.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSuggestion;
import dev.patra.catalog.infra.mesh.persistence.dao.MeshDescriptorDao;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `MeshDescriptorReadAdapter` 单元测试：LIKE 模式的拼接、投影到读模型的转换。
///
/// SQL 的行为由 `MeshDescriptorReadAdapterIT` 在真实 PostgreSQL 上验证。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshDescriptorReadAdapter 单元测试")
class MeshDescriptorReadAdapterTest {

  private final MeshDescriptorDao dao = mock(MeshDescriptorDao.class);
  private final MeshDescriptorReadAdapter adapter = new MeshDescriptorReadAdapter(dao);

  @Test
  @DisplayName("不管匹配模式，只拼前缀与词首两个模式")
  void should_build_prefix_and_word_start_patterns_regardless_of_mode() {
    adapter.suggest(SuggestQuery.of("糖尿病 50%", 10));

    verify(dao).suggest("糖尿病 50%", "糖尿病 50!%%", "% 糖尿病 50!%%", 10);
  }

  @Test
  @DisplayName("树号按空格拆成列表，matchedTerm 原样保留")
  void should_split_tree_numbers_and_keep_matched_term() {
    MeshDescriptorSuggestRow row = mock(MeshDescriptorSuggestRow.class);
    when(row.getUi()).thenReturn("D009369");
    when(row.getName()).thenReturn("Neoplasms");
    when(row.getMatchedTerm()).thenReturn("Cancer");
    when(row.getTreeNumbers()).thenReturn("C04");
    when(dao.suggest(any(), any(), any(), anyInt())).thenReturn(List.of(row));

    MeshDescriptorSuggestion suggestion = adapter.suggest(SuggestQuery.of("Cancer", 10)).getFirst();

    assertThat(suggestion.descriptor().ui()).isEqualTo("D009369");
    assertThat(suggestion.descriptor().name()).isEqualTo("Neoplasms");
    assertThat(suggestion.descriptor().treeNumbers()).containsExactly("C04");
    assertThat(suggestion.matchedTerm()).isEqualTo("Cancer");
  }

  @Test
  @DisplayName("树号为 null 时映射成空列表")
  void should_map_null_tree_numbers_to_empty_list() {
    MeshDescriptorSummaryRow row = mock(MeshDescriptorSummaryRow.class);
    when(row.getUi()).thenReturn("D003920");
    when(row.getTreeNumbers()).thenReturn(null);
    when(dao.findSummaryByUi("D003920")).thenReturn(Optional.of(row));

    assertThat(adapter.findByUi("D003920").orElseThrow().treeNumbers()).isEmpty();
  }
}
