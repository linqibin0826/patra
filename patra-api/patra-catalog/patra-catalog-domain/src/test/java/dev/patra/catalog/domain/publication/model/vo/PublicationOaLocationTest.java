package dev.patra.catalog.domain.publication.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.publication.model.enums.OaLocationType;
import dev.patra.catalog.domain.publication.model.enums.OaStatus;
import dev.patra.catalog.domain.publication.model.enums.VersionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PublicationOaLocation` 单元测试。
@DisplayName("PublicationOaLocation")
class PublicationOaLocationTest {

  @Test
  @DisplayName("PMC 位置带许可、PMC 新域名与证据来源，正式版为 PUBLISHED、手稿为 ACCEPTED")
  void should_build_pmc_location_with_license_and_version() {
    PublicationOaLocation published = PublicationOaLocation.ofPmc("PMC13583816", "CC BY", false);
    PublicationOaLocation manuscript = PublicationOaLocation.ofPmc("PMC8000000", null, true);

    assertThat(published.oaStatus()).isEqualTo(OaStatus.GREEN);
    assertThat(published.locationType()).isEqualTo(OaLocationType.PUBMED_CENTRAL);
    assertThat(published.url()).isEqualTo("https://pmc.ncbi.nlm.nih.gov/articles/PMC13583816/");
    assertThat(published.hostDomain()).isEqualTo("pmc.ncbi.nlm.nih.gov");
    assertThat(published.repositoryName()).isEqualTo("PubMed Central");
    assertThat(published.repositoryId()).isEqualTo("PMC13583816");
    assertThat(published.pmcid()).isEqualTo("PMC13583816");
    assertThat(published.license()).isEqualTo("CC BY");
    assertThat(published.versionType()).isEqualTo(VersionType.PUBLISHED);
    assertThat(published.evidenceSource()).isEqualTo("PMC");
    assertThat(published.isBest()).isTrue();
    assertThat(published.isActive()).isTrue();
    assertThat(manuscript.versionType()).isEqualTo(VersionType.ACCEPTED);
    assertThat(manuscript.license()).isNull();
  }
}
