package dev.patra.catalog.domain.mesh.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.catalog.domain.organization.exception.OrganizationNotFoundException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 机构与 MeSH 主题词未找到异常的语义特征测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("机构与 MeSH 未找到异常")
class SuggestNotFoundExceptionsTest {

  @Test
  @DisplayName("都带 NOT_FOUND 特征，消息里有 ID")
  void should_carry_not_found_trait_and_id_in_message() {
    OrganizationNotFoundException org = new OrganizationNotFoundException("0aaaaaaa1");
    assertThat(org.getErrorTraits()).containsExactly(StandardErrorTrait.NOT_FOUND);
    assertThat(org.getMessage()).contains("0aaaaaaa1");

    MeshDescriptorNotFoundException mesh = new MeshDescriptorNotFoundException("D000000");
    assertThat(mesh.getErrorTraits()).containsExactly(StandardErrorTrait.NOT_FOUND);
    assertThat(mesh.getMessage()).contains("D000000");
  }
}
