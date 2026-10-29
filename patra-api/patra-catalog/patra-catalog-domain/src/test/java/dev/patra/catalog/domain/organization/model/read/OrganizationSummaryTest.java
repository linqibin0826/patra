package dev.patra.catalog.domain.organization.model.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `OrganizationSummary` 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationSummary 单元测试")
class OrganizationSummaryTest {

  @Test
  @DisplayName("types 防御性拷贝，缺省为空列表")
  void should_copy_types_defensively_and_default_to_empty() {
    List<String> types = new ArrayList<>(List.of("healthcare"));
    OrganizationSummary summary =
        OrganizationSummary.builder().rorId("0aaaaaaa1").displayName("X").types(types).build();
    types.add("funder");

    assertThat(summary.types()).containsExactly("healthcare");
    assertThat(OrganizationSummary.builder().rorId("0aaaaaaa1").displayName("X").build().types())
        .isEmpty();
  }
}
