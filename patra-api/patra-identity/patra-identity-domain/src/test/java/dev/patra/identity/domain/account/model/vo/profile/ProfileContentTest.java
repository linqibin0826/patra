package dev.patra.identity.domain.account.model.vo.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ProfileContent 单元测试：列表拷贝、`null` 视为空。
@DisplayName("ProfileContent 单元测试")
class ProfileContentTest {

  @Test
  void should_copy_lists_and_treat_null_as_empty() {
    List<ProfileLink> links = new ArrayList<>(List.of(ProfileLink.of(null, "https://a.org")));

    ProfileContent content = ProfileContent.builder().links(links).build();
    links.clear();

    assertThat(content.links()).hasSize(1);
    assertThat(content.researchInterests()).isEmpty();
    assertThatThrownBy(() -> content.links().add(ProfileLink.of(null, "https://b.org")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
