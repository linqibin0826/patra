package dev.patra.catalog.domain.mesh.model.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `MeshDescriptorSummary` 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshDescriptorSummary 单元测试")
class MeshDescriptorSummaryTest {

  @Test
  @DisplayName("treeNumbers 防御性拷贝，缺省为空列表")
  void should_copy_tree_numbers_and_default_to_empty() {
    assertThat(MeshDescriptorSummary.of("D003920", "Diabetes Mellitus", null).treeNumbers())
        .isEmpty();

    List<String> numbers = new ArrayList<>(List.of("C19.246"));
    MeshDescriptorSummary summary =
        MeshDescriptorSummary.of("D003920", "Diabetes Mellitus", numbers);
    numbers.clear();

    assertThat(summary.treeNumbers()).containsExactly("C19.246");
  }
}
