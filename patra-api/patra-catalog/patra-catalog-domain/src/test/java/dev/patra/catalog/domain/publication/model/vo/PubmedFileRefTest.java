package dev.patra.catalog.domain.publication.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PubmedFileRef` 单元测试。
@DisplayName("PubmedFileRef")
class PubmedFileRefTest {

  @Test
  @DisplayName("按前缀与四位文件号生成文件名与下载地址")
  void should_build_file_name_and_url() {
    PubmedFileRef ref = PubmedFileRef.of(PubmedFileKind.UPDATE, "pubmed26n", 1335);

    assertThat(ref.fileName()).isEqualTo("pubmed26n1335.xml.gz");
    assertThat(ref.downloadUrl("https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles"))
        .isEqualTo("https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/pubmed26n1335.xml.gz");
    assertThat(ref.downloadUrl("https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/"))
        .isEqualTo("https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/pubmed26n1335.xml.gz");
  }

  @Test
  @DisplayName("文件号小于 1 时拒绝")
  void should_reject_seq_below_one() {
    assertThatThrownBy(() -> PubmedFileRef.of(PubmedFileKind.UPDATE, "pubmed26n", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("前缀为空时拒绝")
  void should_reject_blank_prefix() {
    assertThatThrownBy(() -> PubmedFileRef.of(PubmedFileKind.UPDATE, " ", 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
