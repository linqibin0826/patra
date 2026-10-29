package dev.patra.catalog.adapter.kernel.rest.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `PortalSuggestRequest` 单元测试：紧凑构造器的归一化与按码点的长度自检。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PortalSuggestRequest 单元测试")
class PortalSuggestRequestTest {

  @Test
  @DisplayName("q 做 NFKC、去首尾空白、折叠空白；limit 缺省 10")
  void should_normalize_q_and_default_limit() {
    PortalSuggestRequest req =
        new PortalSuggestRequest(Character.toString(0x3000) + "ＰＵＭＣＨ  协和 ", null);

    assertThat(req.q()).isEqualTo("PUMCH 协和");
    assertThat(req.limitOrDefault()).isEqualTo(10);
    assertThat(new PortalSuggestRequest("Mayo", 5).limitOrDefault()).isEqualTo(5);
    assertThat(new PortalSuggestRequest(null, 5).q()).isNull();
  }

  @Test
  @DisplayName("长度自检按码点算 2 ~ 100，q 为空交给 @NotBlank 报")
  void should_validate_q_length_by_code_points() {
    assertThat(new PortalSuggestRequest("协和", null).isQLengthValid()).isTrue();
    assertThat(new PortalSuggestRequest("a".repeat(100), null).isQLengthValid()).isTrue();
    assertThat(new PortalSuggestRequest("协", null).isQLengthValid()).isFalse();
    assertThat(new PortalSuggestRequest("a".repeat(101), null).isQLengthValid()).isFalse();
    assertThat(new PortalSuggestRequest(null, null).isQLengthValid()).isTrue();
  }
}
