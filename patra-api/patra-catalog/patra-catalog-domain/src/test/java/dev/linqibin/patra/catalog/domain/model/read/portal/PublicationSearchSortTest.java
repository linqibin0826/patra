package dev.linqibin.patra.catalog.domain.model.read.portal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PublicationSearchSort.fromCode")
class PublicationSearchSortTest {

  @Test
  @DisplayName("null → LATEST")
  void nullDefaultsToLatest() {
    assertThat(PublicationSearchSort.fromCode(null)).isEqualTo(PublicationSearchSort.LATEST);
  }

  @Test
  @DisplayName("year / YEAR → YEAR，大小写不敏感")
  void yearCaseInsensitive() {
    assertThat(PublicationSearchSort.fromCode("year")).isEqualTo(PublicationSearchSort.YEAR);
    assertThat(PublicationSearchSort.fromCode("YEAR")).isEqualTo(PublicationSearchSort.YEAR);
  }

  @Test
  @DisplayName("cited → CITED（仅 feed 使用）")
  void cited() {
    assertThat(PublicationSearchSort.fromCode("cited")).isEqualTo(PublicationSearchSort.CITED);
  }

  @Test
  @DisplayName("未知值 → LATEST")
  void unknownDefaultsToLatest() {
    assertThat(PublicationSearchSort.fromCode("relevance")).isEqualTo(PublicationSearchSort.LATEST);
  }
}
