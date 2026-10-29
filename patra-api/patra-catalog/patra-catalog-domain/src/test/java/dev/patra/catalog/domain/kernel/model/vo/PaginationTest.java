package dev.patra.catalog.domain.kernel.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// `Pagination` 单元测试：首页 / 尾页的推导（RIS、CSV 用）。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("Pagination 单元测试")
class PaginationTest {

  @ParameterizedTest(name = "{0} → {1} / {2}")
  @CsvSource(
      value = {
        "1451-61|1451|1461",
        "S1-S10|S1|S10",
        "1451-61; discussion 1462-3|1451|1461",
        "e0123456|e0123456|",
        "123|123|",
        "e10-e15|e10|e15",
        "99-101|99|101"
      },
      delimiter = '|')
  @DisplayName("从 MedlinePgn 推导首页与尾页，尾页短时按首页补齐高位")
  void should_derive_first_range_from_medline_pgn(String pgn, String first, String last) {
    assertThat(Pagination.of(pgn, null, null).firstRange()).contains(PageRange.of(first, last));
  }

  @Test
  @DisplayName("有 StartPage 时以它为准")
  void should_prefer_start_and_end_page() {
    assertThat(Pagination.of("1451-61", "1451", "1461").firstRange())
        .contains(PageRange.of("1451", "1461"));
    assertThat(Pagination.of(null, "e12", null).firstRange()).contains(PageRange.of("e12", null));
  }

  @Test
  @DisplayName("什么都没有时为空")
  void should_be_empty_without_any_page() {
    assertThat(Pagination.of(null, null, null).firstRange()).isEmpty();
    assertThat(Pagination.of("  ", null, null).isEmpty()).isTrue();
  }
}
