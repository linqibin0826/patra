package dev.linqibin.patra.catalog.domain.model.read.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.catalog.domain.model.vo.publication.EvidenceLevel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PublicationSearchFilter 紧凑构造器")
class PublicationSearchFilterTest {

  @Test
  @DisplayName("全空构建：sort 默认 LATEST，四个列表为空不可变列表")
  void emptyBuildDefaults() {
    PublicationSearchFilter f = PublicationSearchFilter.builder().build();
    assertThat(f.sort()).isEqualTo(PublicationSearchSort.LATEST);
    assertThat(f.venueIds()).isNotNull().isEmpty();
    assertThat(f.types()).isNotNull().isEmpty();
    assertThat(f.evidenceLevels()).isNotNull().isEmpty();
    assertThat(f.languages()).isNotNull().isEmpty();
    assertThat(f.keyword()).isNull();
    assertThat(f.isOpenAccess()).isNull();
  }

  @Test
  @DisplayName("列表做防御性拷贝：外部修改不影响 filter")
  void listsAreDefensivelyCopied() {
    List<String> types = new ArrayList<>(List.of("Review"));
    PublicationSearchFilter f = PublicationSearchFilter.builder().types(types).build();
    types.add("Letter");
    assertThat(f.types()).containsExactly("Review");
    assertThatThrownBy(() -> f.types().add("x")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("evidenceLevels 保留枚举值与顺序")
  void keepsEvidenceLevels() {
    PublicationSearchFilter f =
        PublicationSearchFilter.builder()
            .evidenceLevels(List.of(EvidenceLevel.UNKNOWN, EvidenceLevel.CASE_REPORT))
            .build();
    assertThat(f.evidenceLevels())
        .containsExactly(EvidenceLevel.UNKNOWN, EvidenceLevel.CASE_REPORT);
  }
}
