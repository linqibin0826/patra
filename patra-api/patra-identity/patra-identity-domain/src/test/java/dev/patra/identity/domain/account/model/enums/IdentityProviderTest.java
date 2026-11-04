package dev.patra.identity.domain.account.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// IdentityProvider 单元测试：注册 ID 的转换与主体的规范化。
@DisplayName("IdentityProvider 单元测试")
class IdentityProviderTest {

  @Test
  @DisplayName("注册 ID 是小写名，只认三家")
  void should_convert_registration_id() {
    assertThat(IdentityProvider.GITHUB.registrationId()).isEqualTo("github");
    assertThat(IdentityProvider.GITHUB.displayName()).isEqualTo("GitHub");
    assertThat(IdentityProvider.fromRegistrationId("orcid")).contains(IdentityProvider.ORCID);
    assertThat(IdentityProvider.fromRegistrationId("ORCID")).isEmpty();
    assertThat(IdentityProvider.fromRegistrationId("wechat")).isEmpty();
    assertThat(IdentityProvider.fromRegistrationId(null)).isEmpty();
  }

  @Test
  @DisplayName("ORCID 只存裸 iD：带地址前缀的去掉，末位可以是 X")
  void should_normalize_orcid_subject_to_bare_id() {
    assertThat(IdentityProvider.ORCID.normalizeSubject("0000-0002-1825-0097"))
        .contains("0000-0002-1825-0097");
    assertThat(IdentityProvider.ORCID.normalizeSubject("https://orcid.org/0000-0002-1694-233X"))
        .contains("0000-0002-1694-233X");
    assertThat(
            IdentityProvider.ORCID.normalizeSubject(
                "https://sandbox.orcid.org/0000-0002-1825-0097"))
        .contains("0000-0002-1825-0097");
  }

  @Test
  @DisplayName("ORCID 形状不对时为空，不能当主体存")
  void should_reject_malformed_orcid_subject() {
    assertThat(IdentityProvider.ORCID.normalizeSubject("abc")).isEmpty();
    assertThat(IdentityProvider.ORCID.normalizeSubject("0000-0002-1825-009")).isEmpty();
    assertThat(IdentityProvider.ORCID.normalizeSubject(null)).isEmpty();
  }

  @Test
  @DisplayName("Google、GitHub 的主体原样保留，空白的为空")
  void should_keep_other_subjects_as_is() {
    assertThat(IdentityProvider.GOOGLE.normalizeSubject("1098765")).contains("1098765");
    assertThat(IdentityProvider.GITHUB.normalizeSubject("583231")).contains("583231");
    assertThat(IdentityProvider.GITHUB.normalizeSubject(" ")).isEmpty();
    assertThat(IdentityProvider.GOOGLE.normalizeSubject(null)).isEmpty();
  }
}
