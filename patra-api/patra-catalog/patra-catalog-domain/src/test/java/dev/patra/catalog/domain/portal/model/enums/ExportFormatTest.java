package dev.patra.catalog.domain.portal.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `ExportFormat` 单元测试：接口代码、Content-Type 与扩展名（spec 第 6.1 节）。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ExportFormat 单元测试")
class ExportFormatTest {

  @Test
  @DisplayName("按接口代码找到格式，带 Content-Type 与扩展名；未知代码抛出")
  void should_resolve_code_with_content_type_and_extension() {
    assertThat(ExportFormat.fromCode("ris").contentType())
        .isEqualTo("application/x-research-info-systems; charset=UTF-8");
    assertThat(ExportFormat.fromCode("bibtex").extension()).isEqualTo("bib");
    assertThat(ExportFormat.fromCode("csv").contentType()).isEqualTo("text/csv; charset=UTF-8");
    assertThat(ExportFormat.fromCode("csl-json").contentType())
        .isEqualTo("application/vnd.citationstyles.csl+json");
    assertThat(ExportFormat.fromCode("csl-json").extension()).isEqualTo("json");
    assertThatThrownBy(() -> ExportFormat.fromCode("docx"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
