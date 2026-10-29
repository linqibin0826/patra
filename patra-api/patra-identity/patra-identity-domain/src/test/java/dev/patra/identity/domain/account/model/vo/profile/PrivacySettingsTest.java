package dev.patra.identity.domain.account.model.vo.profile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PrivacySettings 单元测试：默认值。
@DisplayName("PrivacySettings 单元测试")
class PrivacySettingsTest {

  @Test
  void should_default_to_public_public_off() {
    PrivacySettings defaults = PrivacySettings.defaults();

    assertThat(defaults.profile()).isEqualTo(ProfileVisibility.PUBLIC);
    assertThat(defaults.professional()).isEqualTo(ProfessionalVisibility.PUBLIC);
    assertThat(defaults.searchIndexing()).isFalse();
  }
}
