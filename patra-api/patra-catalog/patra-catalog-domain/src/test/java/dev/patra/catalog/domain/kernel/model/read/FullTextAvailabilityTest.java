package dev.patra.catalog.domain.kernel.model.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `FullTextAvailability` 单元测试（工程 spec 第 7.1 节）。
@DisplayName("FullTextAvailability")
class FullTextAvailabilityTest {

  @Test
  @DisplayName("没有全文记录时两项都不可用")
  void should_be_unavailable_without_record() {
    assertThat(FullTextAvailability.of(null, false, false))
        .isEqualTo(new FullTextAvailability(false, false));
  }

  @Test
  @DisplayName("PARSED 且有 PDF 时可读、可下载")
  void should_be_readable_and_downloadable_when_parsed_with_pdf() {
    assertThat(FullTextAvailability.of(FullTextStatus.PARSED, true, false))
        .isEqualTo(new FullTextAvailability(true, true));
  }

  @Test
  @DisplayName("撤稿时都不可用")
  void should_hide_everything_when_retracted() {
    assertThat(FullTextAvailability.of(FullTextStatus.PARSED, true, true))
        .isEqualTo(new FullTextAvailability(false, false));
  }

  @Test
  @DisplayName("无正文但有 PDF 时只能下载")
  void should_allow_pdf_only_when_no_body() {
    assertThat(FullTextAvailability.of(FullTextStatus.UNAVAILABLE, true, false))
        .isEqualTo(new FullTextAvailability(false, true));
  }

  @Test
  @DisplayName("只抓到未解析时不可读，有 PDF 可下载")
  void should_not_be_readable_when_only_fetched() {
    assertThat(FullTextAvailability.of(FullTextStatus.FETCHED, false, false))
        .isEqualTo(new FullTextAvailability(false, false));
    assertThat(FullTextAvailability.of(FullTextStatus.FETCHED, true, false).pdfDownloadable())
        .isTrue();
  }
}
