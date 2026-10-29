package dev.patra.catalog.domain.portal.model.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class VenueBrowseFilterTest {

  @Test
  void givenNullSort_whenBuild_thenDefaultsToImpactFactor() {
    var filter = VenueBrowseFilter.builder().sort(null).build();
    assertThat(filter.sort()).isEqualTo(VenueBrowseSort.IMPACT_FACTOR);
  }

  @Test
  void givenNullSubjects_whenBuild_thenEmptyList() {
    var filter = VenueBrowseFilter.builder().subjects(null).build();
    assertThat(filter.subjects()).isNotNull().isEmpty();
  }

  @Test
  void givenNullJcrQuartiles_whenBuild_thenEmptyList() {
    var filter = VenueBrowseFilter.builder().jcrQuartiles(null).build();
    assertThat(filter.jcrQuartiles()).isNotNull().isEmpty();
  }

  @Test
  void givenNullCasQuartiles_whenBuild_thenEmptyList() {
    var filter = VenueBrowseFilter.builder().casQuartiles(null).build();
    assertThat(filter.casQuartiles()).isNotNull().isEmpty();
  }

  @Test
  void givenNullCountryCodes_whenBuild_thenEmptyList() {
    var filter = VenueBrowseFilter.builder().countryCodes(null).build();
    assertThat(filter.countryCodes()).isNotNull().isEmpty();
  }

  @Test
  void givenMutableSubjects_whenBuild_thenDefensivelyCopied() {
    var source = new ArrayList<>(List.of("Medicine"));
    var filter = VenueBrowseFilter.builder().subjects(source).build();
    source.add("Biology");
    assertThat(filter.subjects()).containsExactly("Medicine");
  }

  @Test
  void givenMutableJcrQuartiles_whenBuild_thenDefensivelyCopied() {
    var source = new ArrayList<>(List.of("Q1"));
    var filter = VenueBrowseFilter.builder().jcrQuartiles(source).build();
    source.add("Q2");
    assertThat(filter.jcrQuartiles()).containsExactly("Q1");
  }

  @Test
  void givenMutableCasQuartiles_whenBuild_thenDefensivelyCopied() {
    var source = new ArrayList<>(List.of("Q1"));
    var filter = VenueBrowseFilter.builder().casQuartiles(source).build();
    source.add("Q3");
    assertThat(filter.casQuartiles()).containsExactly("Q1");
  }

  @Test
  void givenMutableCountryCodes_whenBuild_thenDefensivelyCopied() {
    var source = new ArrayList<>(List.of("CN"));
    var filter = VenueBrowseFilter.builder().countryCodes(source).build();
    source.add("US");
    assertThat(filter.countryCodes()).containsExactly("CN");
  }

  @Test
  void givenListWithNullElement_whenBuild_thenThrows() {
    var withNull = Arrays.asList("Medicine", null);
    assertThatThrownBy(() -> VenueBrowseFilter.builder().subjects(withNull).build())
        .isInstanceOf(NullPointerException.class);
  }
}
