package dev.patra.catalog.adapter.portal.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.portal.model.enums.ExportFormat;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `ExportFileNames` 单元测试（spec 第 6.1 节文件名规则）。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ExportFileNames 单元测试")
class ExportFileNamesTest {

  private static final LocalDate DATE = LocalDate.of(2026, 10, 24);

  @Test
  @DisplayName("patra-<name>-<日期>.<扩展名>")
  void should_build_name_with_date_and_extension() {
    assertThat(ExportFileNames.of("组会", ExportFormat.RIS, DATE))
        .isEqualTo("patra-组会-2026-10-24.ris");
    assertThat(ExportFileNames.of("组会", ExportFormat.CSL_JSON, DATE))
        .isEqualTo("patra-组会-2026-10-24.json");
  }

  @Test
  @DisplayName("去掉非法字符与控制字符，空白折叠成 -")
  void should_strip_illegal_chars_and_collapse_spaces() {
    assertThat(ExportFileNames.of(" a/b  c?\t<d>|\u0007 ", ExportFormat.BIBTEX, DATE))
        .isEqualTo("patra-ab-c-d-2026-10-24.bib");
  }

  @Test
  @DisplayName("去掉后为空或没给名字：用 export")
  void should_fall_back_to_export_when_name_empty_after_strip() {
    assertThat(ExportFileNames.of("///", ExportFormat.CSV, DATE))
        .isEqualTo("patra-export-2026-10-24.csv");
    assertThat(ExportFileNames.of(null, ExportFormat.CSV, DATE))
        .isEqualTo("patra-export-2026-10-24.csv");
    assertThat(ExportFileNames.of("   ", ExportFormat.CSV, DATE))
        .isEqualTo("patra-export-2026-10-24.csv");
  }
}
