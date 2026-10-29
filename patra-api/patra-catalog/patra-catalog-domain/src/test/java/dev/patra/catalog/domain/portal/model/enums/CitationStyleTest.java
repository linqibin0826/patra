package dev.patra.catalog.domain.portal.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `CitationStyle` 单元测试：接口代码与枚举互转。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("CitationStyle 单元测试")
class CitationStyleTest {

  @Test
  @DisplayName("按接口代码找到样式；未知代码抛出")
  void should_resolve_code_and_reject_unknown() {
    assertThat(CitationStyle.fromCode("gb-t-7714-2015")).isEqualTo(CitationStyle.GB_T_7714_2015);
    assertThat(CitationStyle.fromCode("vancouver")).isEqualTo(CitationStyle.VANCOUVER);
    assertThat(CitationStyle.fromCode("apa-7")).isEqualTo(CitationStyle.APA_7);
    assertThat(CitationStyle.fromCode("ama-11")).isEqualTo(CitationStyle.AMA_11);
    assertThatThrownBy(() -> CitationStyle.fromCode("mla"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
