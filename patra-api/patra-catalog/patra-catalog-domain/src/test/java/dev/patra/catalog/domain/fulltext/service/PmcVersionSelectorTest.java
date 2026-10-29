package dev.patra.catalog.domain.fulltext.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleFile;
import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleMetadata;
import dev.patra.catalog.domain.fulltext.model.vo.PmcVersionSelection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PmcVersionSelector` 单元测试。
@DisplayName("PmcVersionSelector")
class PmcVersionSelectorTest {

  @Test
  @DisplayName("有正式版时优先正式版，即使手稿版本号更大")
  void should_prefer_non_manuscript() {
    PmcVersionSelection selection =
        PmcVersionSelector.select(
            List.of(version(1, true, false, false), version(2, true, true, false)));

    assertThat(selection.selected().versionPrefix()).isEqualTo("PMC1.1");
    assertThat(selection.unavailableReason()).isNull();
  }

  @Test
  @DisplayName("同等条件下取版本号最大的")
  void should_pick_highest_version_among_equals() {
    PmcVersionSelection selection =
        PmcVersionSelector.select(
            List.of(
                version(2, true, false, false),
                version(10, true, false, false),
                version(3, true, false, false)));

    assertThat(selection.selected().versionPrefix()).isEqualTo("PMC1.10");
  }

  @Test
  @DisplayName("没有任何版本时为 NOT_IN_BUCKET")
  void should_report_not_in_bucket() {
    PmcVersionSelection selection = PmcVersionSelector.select(List.of());

    assertThat(selection.selected()).isNull();
    assertThat(selection.unavailableReason()).isEqualTo(FullTextUnavailableReason.NOT_IN_BUCKET);
  }

  @Test
  @DisplayName("只有 TDM 手稿（非开放获取）时为 NOT_OPEN_ACCESS")
  void should_report_not_open_access_when_only_manuscript_tdm() {
    PmcVersionSelection selection =
        PmcVersionSelector.select(List.of(version(1, false, true, false)));

    assertThat(selection.unavailableReason()).isEqualTo(FullTextUnavailableReason.NOT_OPEN_ACCESS);
  }

  @Test
  @DisplayName("选中的版本撤稿时为 RETRACTED")
  void should_report_retracted() {
    PmcVersionSelection selection =
        PmcVersionSelector.select(List.of(version(1, true, false, true)));

    assertThat(selection.selected()).isNull();
    assertThat(selection.unavailableReason()).isEqualTo(FullTextUnavailableReason.RETRACTED);
  }

  private static PmcArticleMetadata version(
      int v, boolean openAccess, boolean manuscript, boolean retracted) {
    return PmcArticleMetadata.builder()
        .pmcid("PMC1")
        .versionPrefix("PMC1." + v)
        .openAccess(openAccess)
        .manuscript(manuscript)
        .retracted(retracted)
        .xml(PmcArticleFile.of("PMC1." + v + ".xml", "x"))
        .build();
  }
}
